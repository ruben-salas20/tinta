package dev.rubensalas.tinta.data.sync

import androidx.room.withTransaction
import dev.rubensalas.tinta.data.api.ApiException
import dev.rubensalas.tinta.data.api.Cliente
import dev.rubensalas.tinta.data.api.json
import dev.rubensalas.tinta.data.local.ArticuloE
import dev.rubensalas.tinta.data.local.AvisoE
import dev.rubensalas.tinta.data.local.BaseLocal
import dev.rubensalas.tinta.data.local.BloqueE
import dev.rubensalas.tinta.data.local.CanalE
import dev.rubensalas.tinta.data.local.EtiquetaE
import dev.rubensalas.tinta.data.local.IdiomaE
import dev.rubensalas.tinta.data.local.MedioE
import dev.rubensalas.tinta.data.local.PublicacionE
import dev.rubensalas.tinta.data.local.RamaE
import dev.rubensalas.tinta.data.local.SeccionE
import dev.rubensalas.tinta.data.local.SerieE
import dev.rubensalas.tinta.data.local.VarianteBloqueE
import dev.rubensalas.tinta.data.local.VarianteSeccionE
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.io.File

/** Cómo fue una sincronización, para la pantalla. */
data class ResultadoSync(val enviadas: Int, val descartadas: Int, val pendientes: Int)

/**
 * Sube la cola y baja lo nuevo (docs/api.md, «Sincronización»).
 *
 * Un guardado de un bloque creado en el móvil no sabe su `rev_base` hasta que
 * el servicio confirma la creación. Por eso la cola se envía por tramos: hasta
 * ese guardado, luego se baja, y el guardado sale con la rev que ya se conoce.
 */
class Sincronizador(
    private val base: BaseLocal,
    private val cliente: Cliente,
    private val revGuardada: suspend () -> Long?,
    private val guardarRev: suspend (Long) -> Unit,
) {
    private val dao = base.dao()
    private val cola = Cola(dao)
    private val local = Local(dao)

    suspend fun sincronizar(): ResultadoSync {
        subirMedios()
        var enviadas = 0
        var descartadas = 0
        var bajado = false
        var tramos = 0
        while (tramos++ < MAX_TRAMOS) {
            val tramo = prepararTramo()
            if (tramo.isEmpty()) {
                if (dao.cola().isEmpty()) break
                // El primero espera una creación que aún no se conoce: bajar y volver a mirar.
                bajar()
                bajado = true
                if (prepararTramo().isEmpty()) {
                    descartarHuerfano()
                    descartadas++
                }
                continue
            }
            val r = cliente.subir(tramo.map { it.second })
            var reintentar = false
            for ((i, res) in r.resultados.withIndex()) {
                val (seq, enviada) = tramo[i]
                when {
                    (res["ok"] as? JsonPrimitive)?.booleanOrNull == true -> {
                        enviadas++
                        confirmar(seq, enviada, res)
                    }
                    (res["reintentar"] as? JsonPrimitive)?.booleanOrNull == true -> reintentar = true
                    else -> {
                        descartadas++
                        dao.quitarOperacion(seq)
                        val error = (res["error"] as? JsonPrimitive)?.contentOrNull ?: "sin motivo"
                        dao.avisar(AvisoE(texto = "El servicio no aceptó un cambio (${enviada.op}): $error", creado = System.currentTimeMillis()))
                    }
                }
            }
            bajar()
            bajado = true
            if (reintentar) throw ApiException(500, "El servicio tuvo un error: se reintentará")
        }
        if (!bajado) bajar()
        return ResultadoSync(enviadas, descartadas, dao.cola().size)
    }

    /** Las operaciones que ya se pueden enviar, en orden, con su `rev_base` resuelta. */
    private suspend fun prepararTramo(): List<Pair<Long, JsonObject>> {
        val tramo = mutableListOf<Pair<Long, JsonObject>>()
        for ((seq, o) in cola.pendientes()) {
            var op = o
            if (op.op == "guardar" && op.revBase == null) {
                val rev = cola.revBase(op.texto("variante_bloque_id")!!) ?: break
                op = op.con("rev_base", JsonPrimitive(rev))
                dao.cambiarOperacion(seq, op.toString())
            }
            tramo += seq to op
        }
        return tramo
    }

    /** Un guardado cuyo bloque el servicio nunca recibió: no se puede enviar. */
    private suspend fun descartarHuerfano() {
        val (seq, o) = cola.pendientes().firstOrNull() ?: return
        dao.quitarOperacion(seq)
        dao.avisar(AvisoE(texto = "Se descartó un cambio de un bloque que no llegó al servicio (${o.op})", creado = System.currentTimeMillis()))
    }

    /**
     * Quita de la cola una operación aplicada. Si mientras viajaba se fusionó
     * otro guardado en ella, se queda, ya con la rev nueva como base.
     */
    private suspend fun confirmar(seq: Long, enviada: JsonObject, res: JsonObject) {
        val ahora = dao.cola().firstOrNull { it.seq == seq } ?: return
        val actual = json.parseToJsonElement(ahora.json).jsonObject
        if (actual == enviada) {
            dao.quitarOperacion(seq)
            return
        }
        val conflicto = (res["conflicto"] as? JsonPrimitive)?.booleanOrNull == true
        val rev = (res["rev"] as? JsonPrimitive)?.longOrNull
        when {
            actual.op == "guardar" && rev != null && !conflicto -> dao.cambiarOperacion(seq, actual.con("rev_base", JsonPrimitive(rev)).toString())
            // Una creación ya aplicada con texto fusionado después: sigue como guardado.
            actual.op in setOf("crear_bloque", "fork_bloque") -> {
                dao.quitarOperacion(seq)
                cola.encolar(Op.guardar(actual.id("variante_bloque"), actual.texto("contenido").orEmpty(), null))
            }
            else -> Unit
        }
    }

    private suspend fun subirMedios() {
        for (m in dao.mediosPendientes()) {
            val f = File(m.ruta)
            if (!f.exists()) {
                dao.quitarMedioPendiente(m.id)
                continue
            }
            cliente.subirMedio(m.id, f, m.nombre, m.tipo)
            dao.quitarMedioPendiente(m.id)
        }
    }

    /** Baja lo nuevo y vuelve a aplicar encima lo que aún no se envió. */
    suspend fun bajar() {
        val b = cliente.bajar(revGuardada())
        base.withTransaction {
            dao.series(filas(b.tablas["series"], SerieE.serializer()))
            dao.etiquetas(filas(b.tablas["etiquetas"], EtiquetaE.serializer()))
            dao.articulos(filas(b.tablas["articulos"], ArticuloE.serializer()))
            dao.idiomas(filas(b.tablas["idiomas"], IdiomaE.serializer()))
            dao.ramas(filas(b.tablas["ramas"], RamaE.serializer()))
            dao.secciones(filas(b.tablas["secciones"], SeccionE.serializer()))
            dao.variantesSeccion(filas(b.tablas["variantes_seccion"], VarianteSeccionE.serializer()))
            dao.bloques(filas(b.tablas["bloques"], BloqueE.serializer()))
            dao.variantesBloque(filas(b.tablas["variantes_bloque"], VarianteBloqueE.serializer()))
            dao.medios(filas(b.tablas["medios"], MedioE.serializer()))
            dao.canales(filas(b.tablas["canales"], CanalE.serializer()))
            dao.publicaciones(filas(b.tablas["publicaciones"], PublicacionE.serializer()))
            for ((_, o) in cola.pendientes()) local.aplicar(o)
        }
        guardarRev(b.rev)
    }

    private fun <T> filas(a: JsonArray?, s: KSerializer<T>): List<T> =
        a?.let { json.decodeFromJsonElement(ListSerializer(s), it) } ?: emptyList()

    private companion object {
        const val MAX_TRAMOS = 6
    }
}
