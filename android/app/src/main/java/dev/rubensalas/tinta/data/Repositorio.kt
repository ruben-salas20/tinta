package dev.rubensalas.tinta.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dev.rubensalas.tinta.data.api.Cliente
import dev.rubensalas.tinta.data.api.EntradaHistorial
import dev.rubensalas.tinta.data.api.json
import dev.rubensalas.tinta.data.local.BaseLocal
import dev.rubensalas.tinta.data.local.MedioE
import dev.rubensalas.tinta.data.local.MedioPendienteE
import dev.rubensalas.tinta.data.local.Parte
import dev.rubensalas.tinta.data.local.VarianteBloqueE
import dev.rubensalas.tinta.data.sync.Cola
import dev.rubensalas.tinta.data.sync.Local
import dev.rubensalas.tinta.data.sync.Op
import dev.rubensalas.tinta.data.sync.id
import dev.rubensalas.tinta.data.texto.Critic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.time.Instant

/**
 * Lo que usan las pantallas. Cada cambio se aplica al momento en la copia
 * local, entra en la cola y pide una sincronización: funciona igual sin red.
 */
class Repositorio(
    private val context: Context,
    base: BaseLocal,
    private val cliente: Cliente,
    scope: CoroutineScope,
    private val pedirSync: () -> Unit,
) {
    private val dao = base.dao()
    private val local = Local(dao)
    private val cola = Cola(dao)

    val datos: StateFlow<Datos> = combinar(
        dao.series(), dao.etiquetas(), dao.articulos(), dao.idiomas(), dao.ramas(), dao.secciones(),
        dao.variantesSeccion(), dao.bloques(), dao.variantesBloque(), dao.medios(), dao.canales(),
        dao.publicaciones(), dao.pendientes(),
    ).stateIn(scope, SharingStarted.Eagerly, Datos())

    val avisos = dao.avisos()

    @Suppress("UNCHECKED_CAST")
    private fun combinar(vararg f: Flow<Any>): Flow<Datos> = combine(f.toList()) { v ->
        Datos(
            v[0] as List<Nothing>, v[1] as List<Nothing>, v[2] as List<Nothing>, v[3] as List<Nothing>, v[4] as List<Nothing>,
            v[5] as List<Nothing>, v[6] as List<Nothing>, v[7] as List<Nothing>, v[8] as List<Nothing>, v[9] as List<Nothing>,
            v[10] as List<Nothing>, v[11] as List<Nothing>, v[12] as Int,
        )
    }

    private suspend fun hacer(op: JsonObject) {
        local.aplicar(op)
        cola.encolar(op)
        pedirSync()
    }

    // --- Artículos

    /** Crea un artículo y devuelve su id. */
    suspend fun crearArticulo(titulo: String): String {
        val op = Op.crearArticulo(titulo.trim().ifEmpty { "Sin título" })
        hacer(op)
        return op.id("articulo")
    }

    suspend fun cambiarEstado(articuloId: String, estado: String) =
        hacer(Op.cambiarArticulo(articuloId, buildJsonObject { put("estado", estado) }))

    /** Lo quita en todos sus idiomas y ramas, con sus publicaciones sin publicar. */
    suspend fun borrarArticulo(id: String) = hacer(Op.borrarArticulo(id))

    suspend fun restaurarArticulo(id: String) = hacer(Op.restaurarArticulo(id))

    suspend fun cambiarTitulo(idiomaId: String, titulo: String) =
        hacer(Op.cambiarIdioma(idiomaId, buildJsonObject { put("titulo", titulo) }))

    suspend fun cambiarTituloSeccion(vsId: String, titulo: String) = hacer(Op.cambiarVarianteSeccion(vsId, titulo))

    suspend fun guardar(varianteId: String, contenido: String) {
        val v = dao.varianteBloque(varianteId) ?: return
        if (v.contenido == contenido) return
        hacer(Op.guardar(varianteId, contenido, v.rev.takeIf { it >= 0 }))
    }

    /** Bloque nuevo detrás de [despuesDe] (o al final). Devuelve su id. */
    suspend fun nuevoBloque(vsId: String, despuesDe: String?, tipo: String = "parrafo", contenido: String = ""): String {
        val op = Op.crearBloque(vsId, tipo, contenido, despuesDe, null)
        hacer(op)
        return op.id("bloque")
    }

    /** Sección nueva detrás de [despuesDe] (o al final), con un párrafo vacío. Devuelve el id de ese párrafo. */
    suspend fun nuevaSeccion(ramaId: String, despuesDe: String?, titulo: String): String {
        val op = Op.crearSeccion(ramaId, titulo, despuesDe, null)
        hacer(op)
        return op.id("bloque")
    }

    /** Falla en silencio si es la única sección de la rama, como en el servicio. */
    suspend fun borrarSeccion(id: String) = hacer(Op.borrarSeccion(id))

    suspend fun moverSeccion(id: String, antes: String?, despues: String?) =
        hacer(Op.cambiarSeccion(id, buildJsonObject { put("mover", buildJsonObject { antes?.let { put("antes", it) }; despues?.let { put("despues", it) } }) }))

    suspend fun moverBloque(id: String, antes: String?, despues: String?) =
        hacer(Op.cambiarBloque(id, buildJsonObject { put("mover", buildJsonObject { antes?.let { put("antes", it) }; despues?.let { put("despues", it) } }) }))

    suspend fun cambiarTipoBloque(id: String, tipo: String) = hacer(Op.cambiarBloque(id, buildJsonObject { put("tipo", tipo) }))

    /** Otra variante de la sección, copiada de [desde] (o de la activa). Devuelve su id. */
    suspend fun forkSeccion(seccionId: String, desde: String? = null, activar: Boolean = true): String {
        val op = Op.forkSeccion(seccionId, desde, activar)
        hacer(op)
        return op.id("variante_seccion")
    }

    suspend fun borrarVarianteSeccion(id: String) = hacer(Op.borrarVarianteSeccion(id))

    // --- Ramas e idiomas

    /** Copia la rama con lo activo y devuelve el id de la nueva. */
    suspend fun forkRama(ramaId: String, nombre: String): String {
        val op = Op.forkRama(ramaId, nombre.trim().ifEmpty { "otra" })
        hacer(op)
        return op.id("rama")
    }

    suspend fun renombrarRama(id: String, nombre: String) = hacer(Op.renombrarRama(id, nombre))

    /** La principal no se puede borrar: antes hay que elegir otra. */
    suspend fun borrarRama(id: String) = hacer(Op.borrarRama(id))

    suspend fun hacerPrincipal(idiomaId: String, ramaId: String) =
        hacer(Op.cambiarIdioma(idiomaId, buildJsonObject { put("rama_principal_id", ramaId) }))

    /** Añade la versión en [idioma]. Con [copiarDe], parte de la rama principal de ese idioma. Devuelve su id. */
    suspend fun crearIdioma(articuloId: String, idioma: String, copiarDe: String?): String {
        val op = Op.crearIdioma(articuloId, idioma, copiarDe)
        hacer(op)
        return op.id("idioma")
    }

    suspend fun borrarIdioma(id: String) = hacer(Op.borrarIdioma(id))

    suspend fun cambiarDescripcion(idiomaId: String, descripcion: String) =
        hacer(Op.cambiarIdioma(idiomaId, buildJsonObject { put("descripcion", descripcion) }))

    suspend fun marcarTraducidoIa(idiomaId: String, si: Boolean) =
        hacer(Op.cambiarIdioma(idiomaId, buildJsonObject { put("traducido_ia", si) }))

    // --- Etiquetas y series

    /** Sustituye todas las etiquetas del artículo. Las nuevas se crean solas. */
    suspend fun cambiarEtiquetas(articuloId: String, nombres: List<String>) =
        hacer(Op.cambiarArticulo(articuloId, buildJsonObject { put("etiquetas", JsonArray(nombres.map(::JsonPrimitive))) }))

    /** [serieId] null quita el artículo de su serie. */
    suspend fun cambiarSerie(articuloId: String, serieId: String?, parte: Long?) =
        hacer(Op.cambiarArticulo(articuloId, buildJsonObject {
            put("serie_id", serieId?.let(::JsonPrimitive) ?: JsonNull)
            put("serie_parte", if (serieId == null) JsonNull else parte?.let(::JsonPrimitive) ?: JsonNull)
        }))

    /** Devuelve el id de la serie nueva. */
    suspend fun crearSerie(nombre: String): String {
        val op = Op.crearSerie(nombre.trim())
        hacer(op)
        return op.id("serie")
    }

    suspend fun renombrarSerie(id: String, nombre: String) = hacer(Op.renombrarSerie(id, nombre))

    suspend fun borrarSerie(id: String) = hacer(Op.borrarSerie(id))

    suspend fun borrarBloque(id: String) = hacer(Op.borrarBloque(id))

    /** Otra variante del bloque, copiada de la activa. Devuelve su id. */
    suspend fun forkBloque(bloqueId: String, contenido: String? = null, activar: Boolean = true): String {
        val op = Op.forkBloque(bloqueId, contenido, activar)
        hacer(op)
        return op.id("variante_bloque")
    }

    suspend fun activarVariante(bloqueId: String, varianteId: String) =
        hacer(Op.cambiarBloque(bloqueId, buildJsonObject { put("variante_activa_id", varianteId) }))

    suspend fun activarVarianteSeccion(seccionId: String, vsId: String) =
        hacer(dev.rubensalas.tinta.data.sync.Op.cambiarSeccion(seccionId, buildJsonObject { put("variante_activa_id", vsId) }))

    suspend fun borrarVariante(varianteId: String) = hacer(Op.borrarVarianteBloque(varianteId))

    /** Acepta o descarta una marca. Se resuelve aquí y viaja como guardado: así no depende de la red. */
    suspend fun resolver(v: VarianteBloqueE, indice: Int, decision: Critic.Decision) {
        val actual = dao.varianteBloque(v.id) ?: return
        val nuevo = Critic.resolver(actual.contenido, indice, decision) ?: return
        guardar(v.id, nuevo)
    }

    /**
     * Resuelve un conflicto. La redacción elegida queda activa y la otra, como
     * variante normal: ninguna se pierde. La del móvil (origen «conflicto») pasa
     * a ser tuya copiándola, porque solo editar cambia el origen.
     */
    suspend fun resolverConflicto(bloqueId: String, conflicto: VarianteBloqueE, quedarseConMovil: Boolean) {
        forkBloque(bloqueId, conflicto.contenido, activar = quedarseConMovil)
        borrarVariante(conflicto.id)
    }

    suspend fun historial(varianteId: String): List<EntradaHistorial> = cliente.historial(varianteId)

    suspend fun restaurar(varianteId: String, entrada: Long) {
        cliente.restaurar(varianteId, entrada)
        pedirSync()
    }

    /** Copia una imagen de la galería a la app y la deja lista para subir. Devuelve su id. */
    suspend fun guardarImagen(uri: Uri): String {
        val id = nuevoId()
        val cr = context.contentResolver
        val tipo = cr.getType(uri) ?: "image/jpeg"
        val nombre = cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: "imagen.jpg"
        val dir = File(context.filesDir, "medios").apply { mkdirs() }
        val f = File(dir, id)
        cr.openInputStream(uri)!!.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
        dao.medios(listOf(MedioE(id, "", nombre, tipo, f.length(), Instant.now().toString(), rev = -1)))
        dao.medioPendiente(MedioPendienteE(id, f.path, nombre, tipo))
        pedirSync()
        return id
    }

    /** El archivo local de un medio aún sin subir, o null para pedirlo al servicio. */
    fun archivoLocal(medioId: String): File? = File(context.filesDir, "medios/$medioId").takeIf { it.exists() }

    suspend fun direccionMedio(medioId: String) = cliente.medio(medioId)

    // --- Calendario

    suspend fun crearPublicacion(canalId: String, formato: String, fecha: String, hora: String?, titulo: String, articuloId: String?): String {
        val p = buildJsonObject {
            put("canal_id", canalId); put("formato", formato); put("fecha", fecha)
            hora?.let { put("hora", it) }; put("titulo", titulo); articuloId?.let { put("articulo_id", it) }
            put("partes", json.encodeToJsonElement(ListSerializer(Parte.serializer()), listOf(Parte())))
        }
        val op = Op.crearPublicacion(p)
        hacer(op)
        return op.id("publicacion")
    }

    suspend fun cambiarPublicacion(id: String, cambio: JsonObject) = hacer(Op.cambiarPublicacion(id, cambio))

    suspend fun cambiarPartes(id: String, partes: List<Parte>) =
        cambiarPublicacion(id, buildJsonObject { put("partes", json.encodeToJsonElement(ListSerializer(Parte.serializer()), partes)) })

    suspend fun marcarPublicada(id: String, enlace: String) = cambiarPublicacion(id, buildJsonObject {
        put("estado", "publicada")
        put("enlace", enlace.trim().takeIf { it.isNotEmpty() }?.let(::JsonPrimitive) ?: JsonNull)
    })

    suspend fun borrarPublicacion(id: String) = hacer(Op.borrarPublicacion(id))

    suspend fun borrarAvisos() = dao.borrarAvisos()
}
