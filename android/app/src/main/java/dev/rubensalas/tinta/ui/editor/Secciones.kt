package dev.rubensalas.tinta.ui.editor

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rubensalas.tinta.data.Datos
import dev.rubensalas.tinta.data.SeccionVista
import dev.rubensalas.tinta.data.autor
import dev.rubensalas.tinta.data.local.VarianteSeccionE
import dev.rubensalas.tinta.data.texto.Critic
import dev.rubensalas.tinta.ui.tema.Estilos
import dev.rubensalas.tinta.ui.tema.Tinta

enum class AccionSeccion { Titulo, Nueva, Subir, Bajar, Variantes, Borrar }

/** Título de la sección con su menú. La de entrada no tiene título: se ve como una línea tenue. */
@Composable
fun CabeceraSeccion(s: SeccionVista, primera: Boolean, ultima: Boolean, unica: Boolean, conMenu: Boolean, onAccion: (AccionSeccion) -> Unit) {
    val c = Tinta.colores
    val titulo = s.activa?.titulo.orEmpty()
    if (!conMenu) {
        if (titulo.isNotBlank()) Text(titulo, Modifier.padding(top = 18.dp, bottom = 8.dp), style = Estilos.subtitulo, color = c.texto)
        else Spacer(Modifier.height(8.dp))
        return
    }
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(top = if (titulo.isBlank()) 4.dp else 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (titulo.isNotBlank()) Text(titulo, Modifier.weight(1f), style = Estilos.subtitulo, color = c.texto)
        else Text("Sección sin título", Modifier.weight(1f), style = Estilos.uiPeque, color = c.tenue)
        if (s.variantes.size > 1) {
            val i = s.variantes.indexOfFirst { it.id == s.activa?.id }.coerceAtLeast(0)
            Text(
                "${'A' + i} de ${s.variantes.size}",
                Modifier.clip(RoundedCornerShape(99.dp)).clickable { onAccion(AccionSeccion.Variantes) }.padding(horizontal = 8.dp, vertical = 4.dp),
                style = Estilos.uiPeque, color = c.secundario,
            )
        }
        Box {
            Icon(Icons.Outlined.MoreHoriz, "Opciones de la sección", Modifier.size(34.dp).clip(RoundedCornerShape(99.dp)).clickable { menu = true }.padding(8.dp), tint = c.tenue)
            DropdownMenu(menu, { menu = false }) {
                val opciones = buildList {
                    add("Cambiar título" to AccionSeccion.Titulo)
                    add("Nueva sección debajo" to AccionSeccion.Nueva)
                    if (!primera) add("Subir" to AccionSeccion.Subir)
                    if (!ultima) add("Bajar" to AccionSeccion.Bajar)
                    add("Variantes de la sección…" to AccionSeccion.Variantes)
                    if (!unica) add("Borrar sección" to AccionSeccion.Borrar)
                }
                for ((t, a) in opciones) {
                    if (a == AccionSeccion.Borrar) HorizontalDivider()
                    DropdownMenuItem({ Text(t) }, { menu = false; onAccion(a) })
                }
            }
        }
    }
}

/** El texto activo de una variante de sección, de corrido, para la tarjeta y para comparar. */
private fun textoVariante(datos: Datos, vs: VarianteSeccionE): String = datos.bloques
    .filter { it.varianteSeccionId == vs.id }.sortedBy { it.posicion }
    .mapNotNull { b -> datos.variantesBloque.firstOrNull { it.id == b.varianteActivaId }?.contenido }
    .joinToString("\n\n") { Critic.limpio(it) }

@Composable
fun HojaVariantesSeccion(
    datos: Datos,
    s: SeccionVista,
    onActivar: (VarianteSeccionE) -> Unit,
    onDescartar: (VarianteSeccionE) -> Unit,
    onNueva: () -> Unit,
    onCerrar: () -> Unit,
) {
    val c = Tinta.colores
    var comparada by remember { mutableStateOf<VarianteSeccionE?>(null) }
    val activa = s.activa
    fun etiqueta(v: VarianteSeccionE) = "${'A' + s.variantes.indexOfFirst { it.id == v.id }}" + (if (v.titulo.isNotBlank()) " · ${v.titulo}" else "")
    HojaInferior(onCerrar) {
        val otra = comparada
        if (otra != null && activa != null) {
            Text("Comparar variantes", style = Estilos.ui.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold), color = c.texto)
            Text("La activa arriba y la que elegiste debajo.", Modifier.padding(top = 4.dp, bottom = 14.dp), style = Estilos.uiPeque.copy(fontSize = 13.sp), color = c.secundario)
            Comparacion(
                "parrafo",
                VersionComparada("${etiqueta(activa)} · activa", textoVariante(datos, activa)),
                VersionComparada(etiqueta(otra), textoVariante(datos, otra)),
                onVolver = { comparada = null },
            )
            return@HojaInferior
        }
        Text("Variantes de la sección", style = Estilos.ui.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold), color = c.texto)
        Text(
            "Otra versión de la sección entera, con sus párrafos. La activa es la que se lee y se exporta.",
            Modifier.padding(top = 4.dp, bottom = 14.dp), style = Estilos.uiPeque.copy(fontSize = 13.sp), color = c.secundario,
        )
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (v in s.variantes) {
                val esActiva = v.id == activa?.id
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .border(1.dp, if (esActiva) c.texto else c.linea, RoundedCornerShape(14.dp))
                        .padding(12.dp),
                ) {
                    Text(
                        (if (esActiva) "${etiqueta(v)} · activa" else etiqueta(v)) + " · " + autor(v.origen),
                        style = Estilos.uiPeque.copy(fontWeight = FontWeight.SemiBold), color = c.texto,
                    )
                    Text(
                        textoVariante(datos, v).ifBlank { "(vacía)" }, Modifier.padding(top = 5.dp),
                        style = Estilos.vistaPrevia.copy(color = c.texto), maxLines = 4, overflow = TextOverflow.Ellipsis,
                    )
                    if (!esActiva) Column(Modifier.padding(top = 10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            BotonPildora("Activar", onClick = { onActivar(v) })
                            BotonPildora("Descartar", fantasma = true, onClick = { onDescartar(v) })
                        }
                        if (activa != null) Text(
                            "Comparar con la activa", Modifier.padding(top = 8.dp).clickable { comparada = v },
                            style = Estilos.ui.copy(fontWeight = FontWeight.Medium), color = c.texto,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        BotonPildora("Nueva variante", Modifier.fillMaxWidth(), onClick = onNueva, icono = { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp), tint = c.texto) })
    }
}

@Composable
fun DialogoBorrarSeccion(onBorrar: () -> Unit, onCerrar: () -> Unit) = AlertDialog(
    onDismissRequest = onCerrar,
    title = { Text("¿Borrar la sección?") },
    text = { Text("Se borran su título y sus párrafos, con todas sus variantes.") },
    confirmButton = { TextButton(onBorrar) { Text("Borrar") } },
    dismissButton = { TextButton(onCerrar) { Text("Cancelar") } },
)

/** Los tipos de bloque que son texto y pueden pasar de uno a otro. */
val TIPOS_TEXTO = listOf("parrafo", "subtitulo", "lista", "cita")
private val NOMBRE_TIPO = mapOf("parrafo" to "Párrafo", "subtitulo" to "Subtítulo", "lista" to "Lista", "cita" to "Cita")

/**
 * El contenido con el prefijo Markdown de [tipo]: `### ` para subtítulo y
 * `- ` o `> ` en cada línea para lista y cita. Antes quita el que tuviera.
 */
fun conTipo(tipo: String, contenido: String): String {
    val lineas = contenido.lines().map { it.removePrefix("### ").removePrefix("- ").removePrefix("> ").removePrefix(">") }
    return when (tipo) {
        "subtitulo" -> "### " + lineas.filter { it.isNotBlank() }.joinToString(" ")
        "lista" -> lineas.filter { it.isNotBlank() }.joinToString("\n") { "- $it" }
        "cita" -> lineas.joinToString("\n") { if (it.isBlank()) ">" else "> $it" }
        else -> lineas.joinToString("\n")
    }
}

@Composable
fun HojaCambiarTipo(actual: String, onElegir: (String) -> Unit, onCerrar: () -> Unit) {
    val c = Tinta.colores
    HojaInferior(onCerrar) {
        Text("Cambiar el tipo del bloque", style = Estilos.ui.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold), color = c.texto)
        Spacer(Modifier.height(8.dp))
        for (t in TIPOS_TEXTO) Text(
            (if (t == actual) "✓ " else "") + NOMBRE_TIPO[t],
            Modifier.fillMaxWidth().clickable(enabled = t != actual) { onElegir(t) }.padding(vertical = 13.dp),
            style = Estilos.ui, color = if (t == actual) c.tenue else c.texto,
        )
    }
}
