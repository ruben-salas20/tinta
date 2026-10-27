package dev.rubensalas.tinta.data.sync

import dev.rubensalas.tinta.data.Posicion
import dev.rubensalas.tinta.data.api.json
import dev.rubensalas.tinta.data.local.ArticuloE
import dev.rubensalas.tinta.data.local.BloqueE
import dev.rubensalas.tinta.data.local.DaoTinta
import dev.rubensalas.tinta.data.local.EtiquetaE
import dev.rubensalas.tinta.data.local.SerieE
import dev.rubensalas.tinta.data.local.IdiomaE
import dev.rubensalas.tinta.data.local.OperacionE
import dev.rubensalas.tinta.data.local.Parte
import dev.rubensalas.tinta.data.local.PublicacionE
import dev.rubensalas.tinta.data.local.RamaE
import dev.rubensalas.tinta.data.local.SeccionE
import dev.rubensalas.tinta.data.local.VarianteBloqueE
import dev.rubensalas.tinta.data.local.VarianteSeccionE
import dev.rubensalas.tinta.data.derivado
import dev.rubensalas.tinta.data.idEtiqueta
import dev.rubensalas.tinta.data.nuevoId
import dev.rubensalas.tinta.data.texto.desplazar
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
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
    fun borrarArticulo(id: String) = buildJsonObject { put("op", "borrar_articulo"); put("id", id) }
    fun restaurarArticulo(id: String) = buildJsonObject { put("op", "restaurar_articulo"); put("id", id) }

    /** Sin [copiarDe] nace vacío, con una sección y un párrafo. */
    fun crearIdioma(articuloId: String, idioma: String, copiarDe: String?) = buildJsonObject {
        put("op", "crear_idioma"); put("articulo_id", articuloId); put("idioma", idioma)
        copiarDe?.let { put("copiar_de", it) }
        put("ids", ids("idioma", "rama", "semilla", "seccion", "variante_seccion", "bloque", "variante_bloque"))
    }

    fun borrarIdioma(id: String) = buildJsonObject { put("op", "borrar_idioma"); put("id", id) }

    fun forkRama(ramaId: String, nombre: String) = buildJsonObject {
        put("op", "fork_rama"); put("rama_id", ramaId); put("nombre", nombre); put("ids", ids("rama", "semilla"))
    }

    fun renombrarRama(id: String, nombre: String) = buildJsonObject { put("op", "renombrar_rama"); put("id", id); put("nombre", nombre) }
    fun borrarRama(id: String) = buildJsonObject { put("op", "borrar_rama"); put("id", id) }

    fun crearSerie(nombre: String) = buildJsonObject { put("op", "crear_serie"); put("nombre", nombre); put("ids", ids("serie")) }
    fun renombrarSerie(id: String, nombre: String) = buildJsonObject { put("op", "renombrar_serie"); put("id", id); put("nombre", nombre) }
    fun borrarSerie(id: String) = buildJsonObject { put("op", "borrar_serie"); put("id", id) }

    fun borrarSeccion(id: String) = buildJsonObject { put("op", "borrar_seccion"); put("id", id) }

    /** Otra variante de la sección, copiada de [desde] (o de la activa). */
    fun forkSeccion(seccionId: String, desde: String?, activar: Boolean) = buildJsonObject {
        put("op", "fork_seccion"); put("seccion_id", seccionId); desde?.let { put("desde", it) }; put("activar", activar)
        put("ids", ids("variante_seccion", "semilla"))
    }

    fun borrarVarianteSeccion(id: String) = buildJsonObject { put("op", "borrar_variante_seccion"); put("id", id) }

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
                val contenido = o.texto("contenido")!!
                dao.variantesBloque(listOf(it.copy(contenido = contenido, cambiado = ahora(), autoria = desplazar(it.autoria, it.contenido, contenido))))
            }
            "crear_articulo" -> crearArticulo(o)
            "cambiar_articulo" -> dao.articulo(o.texto("id")!!)?.let { a ->
                val c = o["cambio"]!!.jsonObject
                dao.articulos(listOf(a.copy(
                    estado = c.texto("estado") ?: a.estado,
                    fecha = if ("fecha" in c) c.texto("fecha") else a.fecha,
                    serieId = if ("serie_id" in c) c.texto("serie_id") else a.serieId,
                    serieParte = if ("serie_parte" in c) (c["serie_parte"] as? JsonPrimitive)?.longOrNull else a.serieParte,
                    etiquetas = c["etiquetas"]?.let { etiquetas(it.jsonArray.mapNotNull { e -> (e as? JsonPrimitive)?.contentOrNull }) } ?: a.etiquetas,
                    cambiado = ahora(),
                )))
            }
            "crear_idioma" -> crearIdioma(o)
            "borrar_idioma" -> dao.idioma(o.texto("id")!!)?.let { i ->
                if (dao.idiomasDe(i.articuloId).size > 1) dao.idiomas(listOf(i.copy(borrado = 1)))
            }
            "fork_rama" -> forkRama(o)
            "renombrar_rama" -> dao.rama(o.texto("id")!!)?.let { dao.ramas(listOf(it.copy(nombre = o.texto("nombre")!!.trim()))) }
            "borrar_rama" -> dao.rama(o.texto("id")!!)?.let { r ->
                if (dao.idioma(r.idiomaId)?.ramaPrincipalId != r.id) dao.ramas(listOf(r.copy(borrado = 1)))
            }
            "crear_serie" -> {
                val id = o.id("serie")
                if (dao.serie(id) == null) dao.series(listOf(SerieE(id, o.texto("nombre")!!.trim(), rev = -1)))
            }
            "renombrar_serie" -> dao.serie(o.texto("id")!!)?.let { dao.series(listOf(it.copy(nombre = o.texto("nombre")!!.trim()))) }
            "borrar_serie" -> dao.serie(o.texto("id")!!)?.let { s ->
                dao.series(listOf(s.copy(borrado = 1)))
                dao.articulos(dao.articulosDeSerie(s.id).map { it.copy(serieId = null, serieParte = null) })
            }
            "borrar_seccion" -> dao.seccion(o.texto("id")!!)?.let { s ->
                if (dao.seccionesDe(s.ramaId).size > 1) dao.secciones(listOf(s.copy(borrado = 1)))
            }
            "fork_seccion" -> forkSeccion(o)
            "borrar_variante_seccion" -> dao.varianteSeccion(o.texto("id")!!)?.let { v ->
                val otra = dao.variantesSeccionDe(v.seccionId).firstOrNull { it.id != v.id } ?: return@let
                dao.variantesSeccion(listOf(v.copy(borrado = 1)))
                dao.seccion(v.seccionId)?.takeIf { it.varianteActivaId == v.id }?.let { dao.secciones(listOf(it.copy(varianteActivaId = otra.id))) }
            }
            "borrar_articulo" -> borrarArticulo(o.texto("id")!!)
            "restaurar_articulo" -> restaurarArticulo(o.texto("id")!!)
            "cambiar_idioma" -> dao.idioma(o.texto("id")!!)?.let { i ->
                val c = o["cambio"]!!.jsonObject
                dao.idiomas(listOf(i.copy(
                    titulo = c.texto("titulo") ?: i.titulo,
                    descripcion = c.texto("descripcion") ?: i.descripcion,
                    borrador = (c["borrador"] as? JsonPrimitive)?.booleanOrNull?.let { if (it) 1 else 0 } ?: i.borrador,
                    ramaPrincipalId = c.texto("rama_principal_id") ?: i.ramaPrincipalId,
                )))
            }
            "crear_seccion" -> crearSeccion(o)
            "cambiar_seccion" -> dao.seccion(o.texto("id")!!)?.let { s ->
                val c = o["cambio"]!!.jsonObject
                val pos = c["mover"]?.jsonObject?.let { m ->
                    posicionEntre(dao.seccionesDe(s.ramaId).filter { it.id != s.id }.map { it.id to it.posicion }, m.texto("antes"), m.texto("despues"))
                }
                dao.secciones(listOf(s.copy(varianteActivaId = c.texto("variante_activa_id") ?: s.varianteActivaId, posicion = pos ?: s.posicion)))
            }
            "cambiar_variante_seccion" -> dao.varianteSeccion(o.texto("id")!!)?.let {
                dao.variantesSeccion(listOf(it.copy(titulo = o.texto("titulo")!!)))
            }
            "crear_bloque" -> crearBloque(o)
            "cambiar_bloque" -> dao.bloque(o.texto("id")!!)?.let { b ->
                val c = o["cambio"]!!.jsonObject
                val pos = c["mover"]?.jsonObject?.let { m ->
                    posicionEntre(dao.bloquesDe(b.varianteSeccionId).filter { it.id != b.id }.map { it.id to it.posicion }, m.texto("antes"), m.texto("despues"))
                }
                dao.bloques(listOf(b.copy(
                    varianteActivaId = c.texto("variante_activa_id") ?: b.varianteActivaId,
                    tipo = c.texto("tipo") ?: b.tipo,
                    posicion = pos ?: b.posicion,
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

    /**
     * Como `modelo::borrar_articulo`: el artículo y sus publicaciones sin
     * publicar. Estas toman la rev del artículo, igual que en el servicio,
     * y así [restaurarArticulo] sabe cuáles se fueron con él.
     */
    private suspend fun borrarArticulo(id: String) {
        val a = dao.articulo(id) ?: return
        dao.articulos(listOf(a.copy(borrado = 1)))
        dao.publicaciones(dao.publicacionesDe(id).filter { it.borrado == 0 && it.estado != "publicada" }.map { it.copy(borrado = 1, rev = a.rev) })
    }

    private suspend fun restaurarArticulo(id: String) {
        val a = dao.articulo(id) ?: return
        if (a.borrado == 0) return
        dao.articulos(listOf(a.copy(borrado = 0, cambiado = ahora())))
        dao.publicaciones(dao.publicacionesDe(id).filter { it.borrado == 1 && it.rev == a.rev }.map { it.copy(borrado = 0) })
    }

    /** Como `modelo::etiqueta`: busca por nombre y, si no existe, la crea con el id fijo del nombre. */
    private suspend fun etiquetas(nombres: List<String>): List<String> = nombres
        .map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()
        .map { n ->
            val e = dao.etiquetaLlamada(n)
            when {
                e == null -> EtiquetaE(idEtiqueta(n), n, rev = -1).also { dao.etiquetas(listOf(it)) }.id
                e.borrado == 1 -> e.copy(borrado = 0).also { dao.etiquetas(listOf(it)) }.id
                else -> e.id
            }
        }

    private suspend fun crearIdioma(o: JsonObject) {
        val iid = o.id("idioma")
        if (dao.idioma(iid) != null) return
        val aid = o.texto("articulo_id")!!
        val lengua = o.texto("idioma")!!
        val vivos = dao.idiomasDe(aid)
        if (vivos.any { it.idioma == lengua }) return
        val t = ahora()
        val desde = o.texto("copiar_de")
        if (desde == null) {
            dao.idiomas(listOf(IdiomaE(iid, aid, lengua, ramaPrincipalId = o.id("rama"), rev = -1)))
            dao.ramas(listOf(RamaE(o.id("rama"), iid, "principal", creado = t, rev = -1)))
            dao.secciones(listOf(SeccionE(o.id("seccion"), o.id("rama"), Posicion.entre(null, null), o.id("variante_seccion"), rev = -1)))
            dao.variantesSeccion(listOf(VarianteSeccionE(o.id("variante_seccion"), o.id("seccion"), creado = t, rev = -1)))
            dao.bloques(listOf(BloqueE(o.id("bloque"), o.id("variante_seccion"), Posicion.entre(null, null), varianteActivaId = o.id("variante_bloque"), rev = -1)))
            dao.variantesBloque(listOf(VarianteBloqueE(o.id("variante_bloque"), o.id("bloque"), creado = t, cambiado = t, rev = -1)))
            return
        }
        val origen = vivos.firstOrNull { it.idioma == desde } ?: return
        val rama = origen.ramaPrincipalId ?: return
        dao.idiomas(listOf(IdiomaE(iid, aid, lengua, origen.titulo, origen.descripcion, ramaPrincipalId = o.id("rama"), rev = -1)))
        copiarRama(rama, o.id("rama"), iid, "principal", o.id("semilla"))
    }

    private suspend fun forkRama(o: JsonObject) {
        val rid = o.id("rama")
        if (dao.rama(rid) != null) return
        val origen = dao.rama(o.texto("rama_id")!!)?.takeIf { it.borrado == 0 } ?: return
        val nombre = o.texto("nombre")!!.trim().ifEmpty { return }
        copiarRama(origen.id, rid, origen.idiomaId, nombre, o.id("semilla"))
    }

    /**
     * Como `modelo::copiar_rama`: solo lo activo, con los ids derivados de la
     * semilla para que coincidan con los que pondrá el servicio.
     */
    private suspend fun copiarRama(origen: String, rid: String, idiomaId: String, nombre: String, semilla: String) {
        dao.ramas(listOf(RamaE(rid, idiomaId, nombre, origenId = origen, creado = ahora(), rev = -1)))
        for (s in dao.seccionesDe(origen)) {
            val vs = s.varianteActivaId?.let { dao.varianteSeccion(it) }?.takeIf { it.borrado == 0 } ?: continue
            val sid = derivado(semilla, s.id)
            val vsid = derivado(semilla, vs.id)
            dao.secciones(listOf(SeccionE(sid, rid, s.posicion, vsid, rev = -1)))
            dao.variantesSeccion(listOf(VarianteSeccionE(vsid, sid, vs.titulo, creado = ahora(), rev = -1)))
            copiarBloques(vs.id, vsid, semilla)
        }
    }

    private suspend fun copiarBloques(desde: String, destino: String, semilla: String) {
        val t = ahora()
        for (b in dao.bloquesDe(desde)) {
            val vb = b.varianteActivaId?.let { dao.varianteBloque(it) }?.takeIf { it.borrado == 0 } ?: continue
            val bid = derivado(semilla, b.id)
            val vid = derivado(semilla, vb.id)
            dao.bloques(listOf(BloqueE(bid, destino, b.posicion, b.tipo, vid, rev = -1)))
            dao.variantesBloque(listOf(VarianteBloqueE(vid, bid, vb.contenido, creado = t, cambiado = t, rev = -1)))
        }
    }

    private suspend fun forkSeccion(o: JsonObject) {
        val nueva = o.id("variante_seccion")
        if (dao.varianteSeccion(nueva) != null) return
        val s = dao.seccion(o.texto("seccion_id")!!)?.takeIf { it.borrado == 0 } ?: return
        val desde = (o.texto("desde") ?: s.varianteActivaId)?.let { dao.varianteSeccion(it) }?.takeIf { it.borrado == 0 } ?: return
        dao.variantesSeccion(listOf(VarianteSeccionE(nueva, s.id, desde.titulo, creado = ahora(), rev = -1)))
        copiarBloques(desde.id, nueva, o.id("semilla"))
        if ((o["activar"] as? JsonPrimitive)?.booleanOrNull == true) dao.secciones(listOf(s.copy(varianteActivaId = nueva)))
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
        val base = PublicacionE(id, canalId = null, formato = null, fecha = null, creado = t, cambiado = t, rev = -1)
        dao.publicaciones(listOf(cambiar(base, p)))
    }

    /** Como `modelo::cambiar_publicacion`: quitar la fecha quita la hora, y quitar el canal quita el formato. */
    private fun cambiar(p: PublicacionE, c: JsonObject): PublicacionE {
        val canal = if ("canal_id" in c) c.texto("canal_id") else p.canalId
        val fecha = if ("fecha" in c) c.texto("fecha") else p.fecha
        return p.copy(
            canalId = canal,
            formato = if (canal == null) null else c.texto("formato") ?: p.formato,
            fecha = fecha,
            hora = if (fecha == null) null else if ("hora" in c) c.texto("hora") else p.hora,
            estado = c.texto("estado") ?: p.estado,
            titulo = c.texto("titulo") ?: p.titulo,
            partes = c["partes"]?.let { json.decodeFromJsonElement(ListSerializer(Parte.serializer()), it) } ?: p.partes,
            articuloId = if ("articulo_id" in c) c.texto("articulo_id") else p.articuloId,
            enlace = if ("enlace" in c) c.texto("enlace") else p.enlace,
            cambiado = ahora(),
        )
    }
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
