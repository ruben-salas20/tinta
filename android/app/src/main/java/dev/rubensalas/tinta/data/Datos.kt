package dev.rubensalas.tinta.data

import dev.rubensalas.tinta.data.local.ArticuloE
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
import dev.rubensalas.tinta.data.texto.Critic

/**
 * Todo lo vivo de la copia local, de una vez. Es poco (un solo autor), así
 * que las pantallas calculan lo que necesitan en memoria.
 */
data class Datos(
    val series: List<SerieE> = emptyList(),
    val etiquetas: List<EtiquetaE> = emptyList(),
    val articulos: List<ArticuloE> = emptyList(),
    val idiomas: List<IdiomaE> = emptyList(),
    val ramas: List<RamaE> = emptyList(),
    val secciones: List<SeccionE> = emptyList(),
    val variantesSeccion: List<VarianteSeccionE> = emptyList(),
    val bloques: List<BloqueE> = emptyList(),
    val variantesBloque: List<VarianteBloqueE> = emptyList(),
    val medios: List<MedioE> = emptyList(),
    val canales: List<CanalE> = emptyList(),
    val publicaciones: List<PublicacionE> = emptyList(),
    /** Cambios del móvil que esperan a enviarse. */
    val pendientes: Int = 0,
) {
    private val vsPorSeccion by lazy { variantesSeccion.groupBy { it.seccionId } }
    private val bloquesPorVs by lazy { bloques.groupBy { it.varianteSeccionId } }
    private val vbPorBloque by lazy { variantesBloque.groupBy { it.bloqueId } }

    fun articulo(id: String) = articulos.firstOrNull { it.id == id }
    fun canal(id: String) = canales.firstOrNull { it.id == id }
    fun serie(id: String?) = series.firstOrNull { it.id == id }

    fun idiomasDe(articuloId: String) = idiomas.filter { it.articuloId == articuloId }.sortedByDescending { it.idioma }

    /** El idioma que se enseña por defecto: el español si existe. */
    fun idiomaPrincipal(articuloId: String): IdiomaE? =
        idiomasDe(articuloId).let { l -> l.firstOrNull { it.idioma == "es" } ?: l.firstOrNull() }

    fun titulo(articuloId: String) = idiomaPrincipal(articuloId)?.titulo.orEmpty().ifBlank { "Sin título" }

    fun rama(id: String?) = ramas.firstOrNull { it.id == id }

    fun documento(ramaId: String): List<SeccionVista> = secciones
        .filter { it.ramaId == ramaId }
        .sortedBy { it.posicion }
        .map { s ->
            val variantes = vsPorSeccion[s.id].orEmpty().sortedBy { it.creado }
            val activa = variantes.firstOrNull { it.id == s.varianteActivaId } ?: variantes.firstOrNull()
            SeccionVista(
                seccion = s,
                activa = activa,
                variantes = variantes,
                bloques = activa?.let { vs ->
                    bloquesPorVs[vs.id].orEmpty().sortedBy { it.posicion }.map { b ->
                        val vbs = vbPorBloque[b.id].orEmpty().sortedBy { it.creado }
                        BloqueVista(b, vbs.firstOrNull { it.id == b.varianteActivaId } ?: vbs.firstOrNull(), vbs)
                    }
                }.orEmpty(),
            )
        }

    /** El bloque [bloqueId] tal como está en su documento. */
    fun documentoDeBloque(bloqueId: String): BloqueVista? {
        val b = bloques.firstOrNull { it.id == bloqueId } ?: return null
        val vs = variantesSeccion.firstOrNull { it.id == b.varianteSeccionId } ?: return null
        val s = secciones.firstOrNull { it.id == vs.seccionId } ?: return null
        val vbs = vbPorBloque[b.id].orEmpty().sortedBy { it.creado }
        return BloqueVista(b, vbs.firstOrNull { it.id == b.varianteActivaId } ?: vbs.firstOrNull(), vbs).takeIf { s.ramaId.isNotEmpty() }
    }

    fun senales(articuloId: String): Senales {
        val i = idiomaPrincipal(articuloId) ?: return Senales()
        var propuestas = 0
        var notas = 0
        var conflictos = 0
        var palabras = 0
        for (s in documento(i.ramaPrincipalId ?: return Senales())) {
            for (b in s.bloques) {
                for (v in b.variantes) {
                    if (v.origen == "conflicto") conflictos++
                    else if (v.origen.startsWith("ia:") && v.id != b.activa?.id) propuestas++
                }
                val a = b.activa ?: continue
                val r = Critic.contar(a.contenido)
                propuestas += r.propuestas
                notas += r.notas
                palabras += Critic.limpio(a.contenido).split(Regex("\\s+")).count { it.isNotBlank() }
            }
        }
        val ramas = ramas.count { it.idiomaId == i.id }
        return Senales(propuestas, notas, conflictos, palabras, ramas)
    }

    /** Lo siguiente sin publicar de un artículo, desde [hoy]. */
    fun proxima(articuloId: String, hoy: String): PublicacionE? = publicaciones
        .filter { it.articuloId == articuloId && it.estado != "publicada" && it.fecha >= hoy }
        .minWithOrNull(ORDEN)

    companion object {
        val ORDEN = compareBy<PublicacionE>({ it.fecha }, { it.hora == null }, { it.hora })
    }
}

data class Senales(val propuestas: Int = 0, val notas: Int = 0, val conflictos: Int = 0, val palabras: Int = 0, val ramas: Int = 0)

data class SeccionVista(
    val seccion: SeccionE,
    val activa: VarianteSeccionE?,
    val variantes: List<VarianteSeccionE>,
    val bloques: List<BloqueVista>,
)

data class BloqueVista(val bloque: BloqueE, val activa: VarianteBloqueE?, val variantes: List<VarianteBloqueE>) {
    val conflicto get() = variantes.firstOrNull { it.origen == "conflicto" }
}

/** Quién escribió una variante: «Tú», el nombre del agente o «conflicto». */
fun autor(origen: String): String = when {
    origen == "tu" -> "Tú"
    origen.startsWith("ia:") -> origen.removePrefix("ia:").replaceFirstChar { it.uppercase() }
    else -> origen
}
