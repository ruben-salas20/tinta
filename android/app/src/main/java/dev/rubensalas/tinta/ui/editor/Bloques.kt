package dev.rubensalas.tinta.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.rubensalas.tinta.data.BloqueVista
import dev.rubensalas.tinta.data.autor
import dev.rubensalas.tinta.data.texto.Critic
import dev.rubensalas.tinta.data.texto.Edicion
import dev.rubensalas.tinta.data.texto.Trozo
import dev.rubensalas.tinta.data.texto.esMarca
import dev.rubensalas.tinta.ui.comun.Banda
import dev.rubensalas.tinta.ui.comun.MarcaIa
import dev.rubensalas.tinta.ui.comun.TransformacionMarcas
import dev.rubensalas.tinta.ui.comun.colorTexto
import dev.rubensalas.tinta.ui.comun.textoBloque
import dev.rubensalas.tinta.ui.tema.Colores
import dev.rubensalas.tinta.ui.tema.Estilos
import dev.rubensalas.tinta.ui.tema.Tinta

private val IMAGEN = Regex("""!\[(.*?)]\(medio:([0-9a-f]{24})\)""")
private val AUDIO = Regex("""<audio src="medio:([0-9a-f]{24})"(?: title="(.*?)")?""")

fun estiloBloque(tipo: String, c: Colores): TextStyle = when (tipo) {
    "subtitulo" -> Estilos.subtitulo.copy(color = c.texto)
    "codigo" -> Estilos.codigo.copy(color = c.texto)
    "cita" -> Estilos.cuerpo.copy(color = c.secundario)
    else -> Estilos.cuerpo.copy(color = colorTexto(tipo, c))
}

/** Un bloque sin foco: se lee, con sus marcas, y un toque lo edita. */
@Composable
fun BloqueLectura(b: BloqueVista, imagen: (String) -> Any?, onTocar: () -> Unit) {
    val c = Tinta.colores
    val v = b.activa ?: return
    Column(Modifier.fillMaxWidth().clickable(onClick = onTocar).padding(vertical = 4.dp)) {
        when (b.bloque.tipo) {
            "imagen" -> VistaImagen(v.contenido, imagen)
            "codigo" -> Text(
                textoBloque("codigo", v.contenido.removePrefix("```").substringAfter('\n').removeSuffix("```").trimEnd(), c),
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.gris).padding(12.dp),
                style = estiloBloque("codigo", c),
            )
            "cita" -> Row {
                Box(Modifier.size(width = 3.dp, height = 24.dp).background(c.lineaMarcada))
                Spacer(Modifier.size(12.dp))
                Text(textoBloque("cita", v.contenido, c), style = estiloBloque("cita", c))
            }
            "audio" -> VistaAudio(v.contenido)
            else -> Text(
                if (v.contenido.isBlank()) textoVacio(c) else textoBloque(b.bloque.tipo, v.contenido, c),
                style = estiloBloque(b.bloque.tipo, c),
            )
        }
        Notas(v.contenido)
        if (b.conflicto != null) AvisoConflicto()
        val otras = b.variantes.size - 1
        if (otras > 0) Text("${b.variantes.size} variantes", Modifier.padding(top = 4.dp), style = Estilos.uiPeque, color = c.tenue)
    }
}

private fun textoVacio(c: Colores) = androidx.compose.ui.text.buildAnnotatedString {
    pushStyle(androidx.compose.ui.text.SpanStyle(color = c.tenue)); append("Escribe aquí…"); pop()
}

@Composable
fun VistaImagen(contenido: String, imagen: (String) -> Any?) {
    val c = Tinta.colores
    val m = IMAGEN.find(contenido)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, c.linea, RoundedCornerShape(12.dp)).background(c.hoja)) {
        Box(Modifier.fillMaxWidth().heightIn(min = 120.dp).background(c.gris), contentAlignment = Alignment.Center) {
            val modelo = m?.groupValues?.get(2)?.let(imagen)
            if (modelo != null) AsyncImage(modelo, m.groupValues[1], Modifier.fillMaxWidth())
            else Icon(Icons.Outlined.Image, null, tint = c.tenue)
        }
        Text(
            m?.groupValues?.get(1)?.ifBlank { null }?.let { "Texto alternativo: $it" } ?: "Falta el texto alternativo",
            Modifier.padding(10.dp), style = Estilos.uiPeque, color = c.secundario,
        )
    }
}

@Composable
fun VistaAudio(contenido: String) {
    val c = Tinta.colores
    val m = AUDIO.find(contenido)
    Banda { Text("♪", color = c.texto); Text(m?.groupValues?.get(2)?.ifBlank { null } ?: "Audio", style = Estilos.uiPeque, color = c.secundario) }
}

/** Las notas del bloque, cada una en su tarjeta: el texto completo y quién la dejó. */
@Composable
fun Notas(contenido: String, onResolver: ((Int) -> Unit)? = null) {
    val c = Tinta.colores
    val marcas = Critic.leer(contenido).filter { it.esMarca }
    marcas.forEachIndexed { i, t ->
        if (t !is Trozo.Nota) return@forEachIndexed
        Row(
            Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.hoja)
                .border(1.dp, c.linea, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Outlined.ChatBubbleOutline, null, Modifier.size(14.dp).padding(top = 2.dp), tint = c.secundario)
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (t.ia != null) MarcaIa()
                    Text(if (t.ia != null) autor("ia:${t.ia}") else "Tú", style = Estilos.uiPeque.copy(fontSize = 12.sp), color = c.tenue)
                }
                Text(t.texto, style = Estilos.uiPeque.copy(fontSize = 13.sp, lineHeight = 19.sp), color = c.texto)
            }
            if (onResolver != null) {
                Text("Resolver", Modifier.clickable { onResolver(i) }.padding(4.dp), style = Estilos.uiPeque, color = c.texto)
            }
        }
    }
}

@Composable
fun AvisoConflicto(onAbrir: (() -> Unit)? = null) {
    val c = Tinta.colores
    Row(
        Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .border(1.dp, c.lineaMarcada, RoundedCornerShape(10.dp))
            .let { if (onAbrir != null) it.clickable(onClick = onAbrir) else it }
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.AutoMirrored.Outlined.CallSplit, null, Modifier.size(14.dp), tint = c.texto)
        Text(
            "Este párrafo cambió en dos sitios a la vez. Tinta guardó las dos redacciones." + if (onAbrir != null) " Toca para elegir." else "",
            style = Estilos.uiPeque, color = c.texto,
        )
    }
}

/** Las propuestas y tachados del bloque con foco, con aceptar y descartar. */
@Composable
fun Propuestas(edicion: Edicion, onResolver: (Int, Critic.Decision) -> Unit) {
    val c = Tinta.colores
    val marcas = edicion.marcasEnOrden().filter { it.esMarca }
    marcas.forEachIndexed { i, t ->
        val (ia, texto) = when (t) {
            is Trozo.Borrado -> t.ia to (if (t.ia != null) "Quitar «${t.texto.trim()}»" else "Tachado: «${t.texto.trim()}»")
            is Trozo.Anadido -> t.ia to "Añadir «${t.texto.trim()}»"
            is Trozo.Cambio -> t.ia to "«${t.antes.trim()}» → «${t.despues.trim()}»"
            else -> return@forEachIndexed
        }
        Row(
            Modifier.padding(top = 6.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (ia != null) MarcaIa()
            Text(texto, Modifier.weight(1f), style = Estilos.uiPeque, color = c.secundario, maxLines = 2, overflow = TextOverflow.Ellipsis)
            BotonIcono(Icons.Outlined.Check, "Aceptar") { onResolver(i, Critic.Decision.Aceptar) }
            BotonIcono(Icons.Outlined.Close, "Descartar") { onResolver(i, Critic.Decision.Rechazar) }
        }
    }
}

@Composable
fun BotonIcono(icono: androidx.compose.ui.graphics.vector.ImageVector, descripcion: String, onClick: () -> Unit) {
    val c = Tinta.colores
    Box(
        Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, c.linea, RoundedCornerShape(8.dp)).background(c.hoja).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icono, descripcion, Modifier.size(16.dp), tint = c.texto) }
}

/** El campo de texto del bloque con foco. Las marcas se ven y no se pueden partir. */
@Composable
fun CampoBloque(
    tipo: String,
    valor: TextFieldValue,
    edicion: Edicion,
    foco: FocusRequester,
    onCambio: (TextFieldValue) -> Unit,
) {
    val c = Tinta.colores
    BasicTextField(
        value = valor,
        onValueChange = onCambio,
        modifier = Modifier.fillMaxWidth().focusRequester(foco),
        textStyle = estiloBloque(tipo, c),
        cursorBrush = SolidColor(c.texto),
        visualTransformation = TransformacionMarcas(edicion, c),
    )
}

/** «‹ A de 3 ›»: cambia de variante del bloque. */
@Composable
fun SelectorVariante(actual: Int, total: Int, onAnterior: () -> Unit, onSiguiente: () -> Unit, onVer: () -> Unit) {
    val c = Tinta.colores
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.clip(RoundedCornerShape(99.dp)).border(1.dp, c.linea, RoundedCornerShape(99.dp)).background(c.gris).padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Variante anterior", Modifier.size(22.dp).clickable(onClick = onAnterior), tint = c.secundario)
            Text("${('A' + actual)} de $total", Modifier.clickable(onClick = onVer).padding(horizontal = 4.dp), style = Estilos.uiPeque, color = c.secundario)
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Variante siguiente", Modifier.size(22.dp).clickable(onClick = onSiguiente), tint = c.secundario)
        }
        Text(if (total > 1) "desliza para cambiar de variante" else "una sola variante", style = Estilos.uiPeque.copy(fontSize = 12.sp), color = c.tenue)
    }
}

/** La posición del cursor que deja [nueva] tras rechazar un cambio que partía una marca. */
fun mantener(anterior: TextFieldValue): TextFieldValue = anterior.copy(selection = TextRange(anterior.selection.start))
