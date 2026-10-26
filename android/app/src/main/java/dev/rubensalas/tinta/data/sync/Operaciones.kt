package dev.rubensalas.tinta.data.sync

import dev.rubensalas.tinta.data.Posicion
import dev.rubensalas.tinta.data.api.json
import dev.rubensalas.tinta.data.local.ArticuloE
import dev.rubensalas.tinta.data.local.BloqueE
import dev.rubensalas.tinta.data.local.DaoTinta
import dev.rubensalas.tinta.data.local.IdiomaE
import dev.rubensalas.tinta.data.local.OperacionE
import dev.rubensalas.tinta.data.local.Parte
import dev.rubensalas.tinta.data.local.PublicacionE
import dev.rubensalas.tinta.data.local.RamaE
import dev.rubensalas.tinta.data.local.SeccionE
import dev.rubensalas.tinta.data.local.VarianteBloqueE
import dev.rubensalas.tinta.data.local.VarianteSeccionE
import dev.rubensalas.tinta.data.nuevoId
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.text.Normalizer
import java.time.Instant

/**
 * Las operaciones de `POST /api/sync` (ver docs/api.md, «Sincronización»).
 * Cada una es el JSON que espera el servicio; aquí se construyen y se aplican
 * a la copia local para que la pantalla cambie al momento, sin esperar a la red.
 */
object Op {
    fun guardar(varianteId: String, contenido: String, revBase: Long?) = buildJsonObject {
        put("op", "guardar"); put("variante_bloque_id", varianteId); put("contenido", contenido)
        put("rev_base", revBase?.let(::JsonPrimitive) ?: JsonNull)
    }

    fun crearArticulo(titulo: String) = buildJsonObject {
        put("op", "crear_articulo"); put("titulo", titulo)
        put("ids", ids("articulo", "idioma", "rama", "seccion", "variante_seccion", "bloque", "variante_bloque"))
    }

    fun cambiarArticulo(id: String, cambio: JsonObject) = buildJsonObject { put("op", "cambiar_articulo"); put("id", id); put("cambio", cambio) }
    fun cambiarIdioma(id: String, cambio: JsonObject) = buildJsonObject { put("op", "cambiar_idioma"); put("id", id); put("cambio", cambio) }

    fun crearSeccion(ramaId: String, titulo: String, antes: String?, despues: String?) = buildJsonObject {
        put("op", "crear_seccion"); put("rama_id", ramaId); put("titulo", titulo)
        antes?.let { put("antes", it) }; despues?.let { put("despues", it) }
        put("ids", ids("seccion", "variante_seccion", "bloque", "variante_bloque"))
    }

    fun cambiarSeccion(id: String, cambio: JsonObject) = buildJsonObject { put("op", "cambiar_seccion"); put("id", id); put("cambio", cambio) }
    fun cambiarVarianteSeccion(id: String, titulo: String) = buildJsonObject { put("op", "cambiar_variante_seccion"); put("id", id); put("titulo", titulo) }

    fun crearBloque(vsId: String, tipo: String, contenido: String, antes: String?, despues: String?) = buildJsonObject {
        put("op", "crear_bloque"); put("variante_seccion_id", vsId); put("tipo", tipo); put("contenido", contenido)
        antes?.let { put("antes", it) }; despues?.let { put("despues", it) }
        put("ids", ids("bloque", "variante_bloque"))
    }

    fun cambiarBloque(id: String, cambio: JsonObject) = buildJsonObject { put("op", "cambiar_bloque"); put("id", id); put("cambio", cambio) }
    fun borrarBloque(id: String) = buildJsonObject { put("op", "borrar_bloque"); put("id", id) }

    fun forkBloque(bloqueId: String, contenido: String?, activar: Boolean) = buildJsonObject {
        put("op", "fork_bloque"); put("bloque_id", bloqueId); contenido?.let { put("contenido", it) }; put("activar", activar)
        put("ids", ids("variante_bloque"))
    }

    fun borrarVarianteBloque(id: String) = buildJsonObject { put("op", "borrar_variante_bloque"); put("id", id) }

    fun crearPublicacion(publicacion: JsonObject) = buildJsonObject {
        put("op", "crear_publicacion"); put("publicacion", publicacion); put("ids", ids("publicacion"))
    }

    fun cambiarPublicacion(id: String, cambio: JsonObject) = buildJsonObject { put("op", "cambiar_publicacion"); put("id", id); put("cambio", cambio) }
    fun borrarPublicacion(id: String) = buildJsonObject { put("op", "borrar_publicacion"); put("id", id) }

    private fun ids(vararg claves: String) = buildJsonObject { for (k in claves) put(k, nuevoId()) }
}

val JsonObject.op: String get() = this["op"]!!.jsonPrimitive.content
fun JsonObject.texto(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull
fun JsonObject.id(k: String): String = (this["ids"] as JsonObject)[k]!!.jsonPrimitive.content
fun JsonObject.con(k: String, v: JsonElement) = JsonObject(this + (k to v))

/** Igual que `modelo::slug` del servicio, para que la lista se vea bien antes de sincronizar. */
fun slug(titulo: String): String {
    val sinTildes = Normalizer.normalize(titulo.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
    return sinTildes.replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "sin-titulo" }
}

/** Posición nueva entre dos vecinos de [lista], como `posicion_entre` del servicio. */
fun posicionEntre(lista: List<Pair<String, String>>, antes: String?, despues: String?): String {
    val ordenada = lista.sortedBy { it.second }
    var a = antes?.let { id -> ordenada.firstOrNull { it.first == id }?.second }
    var b = despues?.let { id -> ordenada.firstOrNull { it.first == id }?.second }
    if (a != null && despues == null) b = ordenada.firstOrNull { it.second > a!! }?.second
    if (b != null && antes == null) a = ordenada.lastOrNull { it.second < b!! }?.second
    if (antes == null && despues == null) a = ordenada.lastOrNull()?.second
    return Posicion.entre(a, b)
}

/** Aplica una operación a la copia local. Repetirla no hace daño. */
class Local(private val dao: DaoTinta) {
    private fun ahora() = Instant.now().toString()

    suspend fun aplicar(o: JsonObject) {
        when (o.op) {
            "guardar" -> dao.varianteBloque(o.texto("variante_bloque_id")!!)?.let {
                dao.variantesBloque(listOf(it.copy(contenido = o.texto("contenido")!!, cambiado = ahora())))
            }
            "crear_articulo" -> crearArticulo(o)
            "cambiar_articulo" -> dao.articulo(o.texto("id")!!)?.let { a ->
                val c = o["cambio"]!!.jsonObject
                dao.articulos(listOf(a.copy(
                    estado = c.texto("estado") ?: a.estado,
                    fecha = if ("fecha" in c) c.texto("fecha") else a.fecha,
                    cambiado = ahora(),
                )))
            }
            "cambiar_idioma" -> dao.idioma(o.texto("id")!!)?.let { i ->
                val c = o["cambio"]!!.jsonObject
                dao.idiomas(listOf(i.copy(
                    titulo = c.texto("titulo") ?: i.titulo,
                    descripcion = c.texto("descripcion") ?: i.descripcion,
                    borrador = (c["borrador"] as? JsonPrimitive)?.booleanOrNull?.let { if (it) 1 else 0 } ?: i.borrador,
                )))
            }
            "crear_seccion" -> crearSeccion(o)
            "cambiar_seccion" -> dao.seccion(o.texto("id")!!)?.let { s ->
                val c = o["cambio"]!!.jsonObject
                dao.secciones(listOf(s.copy(varianteActivaId = c.texto("variante_activa_id") ?: s.varianteActivaId)))
            }
            "cambiar_variante_seccion" -> dao.varianteSeccion(o.texto("id")!!)?.let {
                dao.variantesSeccion(listOf(it.copy(titulo = o.texto("titulo")!!)))
            }
            "crear_bloque" -> crearBloque(o)
            "cambiar_bloque" -> dao.bloque(o.texto("id")!!)?.let { b ->
                val c = o["cambio"]!!.jsonObject
                dao.bloques(listOf(b.copy(
                    varianteActivaId = c.texto("variante_activa_id") ?: b.varianteActivaId,
                    tipo = c.texto("tipo") ?: b.tipo,
                )))
            }
            "borrar_bloque" -> dao.bloque(o.texto("id")!!)?.let { dao.bloques(listOf(it.copy(borrado = 1))) }
            "fork_bloque" -> forkBloque(o)
            "borrar_variante_bloque" -> dao.varianteBloque(o.texto("id")!!)?.let { v ->
                dao.variantesBloque(listOf(v.copy(borrado = 1)))
                val b = dao.bloque(v.bloqueId) ?: return@let
                if (b.varianteActivaId == v.id) {
                    val otra = dao.variantesDe(b.id).firstOrNull()?.id
                    dao.bloques(listOf(b.copy(varianteActivaId = otra)))
                }
            }
            "crear_publicacion" -> crearPublicacion(o)
            "cambiar_publicacion" -> dao.publicacion(o.texto("id")!!)?.let { p -> dao.publicaciones(listOf(cambiar(p, o["cambio"]!!.jsonObject))) }
            "borrar_publicacion" -> dao.publicacion(o.texto("id")!!)?.let { dao.publicaciones(listOf(it.copy(borrado = 1))) }
        }
    }

    private suspend fun crearArticulo(o: JsonObject) {
        val id = o.id("articulo")
        if (dao.articulo(id) != null) return
        val t = ahora()
        val titulo = o.texto("titulo")!!
        dao.articulos(listOf(ArticuloE(id, slug(titulo), "idea", creado = t, cambiado = t, rev = -1)))
        dao.idiomas(listOf(IdiomaE(o.id("idioma"), id, "es", titulo, ramaPrincipalId = o.id("rama"), rev = -1)))
        dao.ramas(listOf(RamaE(o.id("rama"), o.id("idioma"), "principal", creado = t, rev = -1)))
        dao.secciones(listOf(SeccionE(o.id("seccion"), o.id("rama"), Posicion.entre(null, null), o.id("variante_seccion"), rev = -1)))
        dao.variantesSeccion(listOf(VarianteSeccionE(o.id("variante_seccion"), o.id("seccion"), creado = t, rev = -1)))
        dao.bloques(listOf(BloqueE(o.id("bloque"), o.id("variante_seccion"), Posicion.entre(null, null), varianteActivaId = o.id("variante_bloque"), rev = -1)))
        dao.variantesBloque(listOf(VarianteBloqueE(o.id("variante_bloque"), o.id("bloque"), creado = t, cambiado = t, rev = -1)))
    }

    private suspend fun crearSeccion(o: JsonObject) {
        val id = o.id("seccion")
        if (dao.seccion(id) != null) return
        val rama = o.texto("rama_id")!!
        val pos = posicionEntre(dao.seccionesDe(rama).map { it.id to it.posicion }, o.texto("antes"), o.texto("despues"))
        val t = ahora()
        dao.secciones(listOf(SeccionE(id, rama, pos, o.id("variante_seccion"), rev = -1)))
        dao.variantesSeccion(listOf(VarianteSeccionE(o.id("variante_seccion"), id, titulo = o.texto("titulo").orEmpty(), creado = t, rev = -1)))
        dao.bloques(listOf(BloqueE(o.id("bloque"), o.id("variante_seccion"), Posicion.entre(null, null), varianteActivaId = o.id("variante_bloque"), rev = -1)))
        dao.variantesBloque(listOf(VarianteBloqueE(o.id("variante_bloque"), o.id("bloque"), creado = t, cambiado = t, rev = -1)))
    }

    private suspend fun crearBloque(o: JsonObject) {
        val id = o.id("bloque")
        if (dao.bloque(id) != null) {
            // Ya creado: solo puede cambiar su texto, si se fusionó un guardado.
            dao.varianteBloque(o.id("variante_bloque"))?.let { v ->
                if (v.rev < 0) dao.variantesBloque(listOf(v.copy(contenido = o.texto("contenido").orEmpty())))
            }
            return
        }
        val vs = o.texto("variante_seccion_id")!!
        val pos = posicionEntre(dao.bloquesDe(vs).map { it.id to it.posicion }, o.texto("antes"), o.texto("despues"))
        val t = ahora()
        dao.bloques(listOf(BloqueE(id, vs, pos, o.texto("tipo") ?: "parrafo", o.id("variante_bloque"), rev = -1)))
        dao.variantesBloque(listOf(VarianteBloqueE(o.id("variante_bloque"), id, o.texto("contenido").orEmpty(), creado = t, cambiado = t, rev = -1)))
    }

    private suspend fun forkBloque(o: JsonObject) {
        val vid = o.id("variante_bloque")
        val b = dao.bloque(o.texto("bloque_id")!!) ?: return
        val ya = dao.varianteBloque(vid)
        val contenido = o.texto("contenido") ?: dao.varianteBloque(b.varianteActivaId ?: "")?.contenido.orEmpty()
        if (ya == null || ya.rev < 0) {
            val t = ahora()
            dao.variantesBloque(listOf(VarianteBloqueE(vid, b.id, contenido, creado = ya?.creado ?: t, cambiado = t, rev = -1)))
        }
        if ((o["activar"] as? JsonPrimitive)?.booleanOrNull == true && ya == null) dao.bloques(listOf(b.copy(varianteActivaId = vid)))
    }

    private suspend fun crearPublicacion(o: JsonObject) {
        val id = o.id("publicacion")
        if (dao.publicacion(id) != null) return
        val p = o["publicacion"]!!.jsonObject
        val t = ahora()
        val base = PublicacionE(id, p.texto("canal_id")!!, p.texto("formato")!!, p.texto("fecha")!!, creado = t, cambiado = t, rev = -1)
        dao.publicaciones(listOf(cambiar(base, p)))
    }

    private fun cambiar(p: PublicacionE, c: JsonObject): PublicacionE = p.copy(
        canalId = c.texto("canal_id") ?: p.canalId,
        formato = c.texto("formato") ?: p.formato,
        fecha = c.texto("fecha") ?: p.fecha,
        hora = if ("hora" in c) c.texto("hora") else p.hora,
        estado = c.texto("estado") ?: p.estado,
        titulo = c.texto("titulo") ?: p.titulo,
        partes = c["partes"]?.let { json.decodeFromJsonElement(ListSerializer(Parte.serializer()), it) } ?: p.partes,
        articuloId = if ("articulo_id" in c) c.texto("articulo_id") else p.articuloId,
        enlace = if ("enlace" in c) c.texto("enlace") else p.enlace,
        cambiado = ahora(),
    )
}

/** La cola de cambios pendientes de enviar. */
class Cola(private val dao: DaoTinta) {
    /**
     * Añade una operación. Un guardado del mismo bloque que otro aún pendiente
     * se fusiona con él: así cada `guardar` lleva la `rev_base` de lo que había
     * en el servicio cuando se empezó a editar. Si el bloque se creó en el móvil
     * y aún no se envió, su texto va dentro de la propia creación.
     */
    suspend fun encolar(o: JsonObject) {
        if (o.op == "guardar") {
            val vid = o.texto("variante_bloque_id")!!
            for (p in dao.cola()) {
                val q = json.parseToJsonElement(p.json).jsonObject
                val fusion = when {
                    q.op == "guardar" && q.texto("variante_bloque_id") == vid -> q.con("contenido", o["contenido"]!!)
                    q.op in setOf("crear_bloque", "fork_bloque") && runCatching { q.id("variante_bloque") }.getOrNull() == vid ->
                        q.con("contenido", o["contenido"]!!)
                    else -> null
                }
                if (fusion != null) {
                    dao.cambiarOperacion(p.seq, fusion.toString())
                    return
                }
            }
        }
        dao.encolar(OperacionE(json = o.toString(), creado = System.currentTimeMillis()))
    }

    /** La `rev_base` para guardar un bloque: la que tiene la copia, o null si aún no la conoce el servicio. */
    suspend fun revBase(varianteId: String): Long? = dao.varianteBloque(varianteId)?.rev?.takeIf { it >= 0 }

    suspend fun pendientes(): List<Pair<Long, JsonObject>> = dao.cola().map { it.seq to json.parseToJsonElement(it.json).jsonObject }
}

val JsonObject.revBase: Long? get() = (this["rev_base"] as? JsonPrimitive)?.longOrNull
