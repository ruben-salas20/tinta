package dev.rubensalas.tinta.ui.comun

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import dev.rubensalas.tinta.data.texto.Critic
import dev.rubensalas.tinta.data.texto.Edicion
import dev.rubensalas.tinta.data.texto.Trozo
import dev.rubensalas.tinta.ui.tema.Colores
import dev.rubensalas.tinta.ui.tema.Mono

/** Marca de nota dentro del texto. La nota completa va en su tarjeta, bajo el bloque. */
const val SIGNO_NOTA = " ✎"

fun estiloTachado(c: Colores) = SpanStyle(color = c.tenue, textDecoration = TextDecoration.LineThrough)
fun estiloAnadido(c: Colores) = SpanStyle(background = c.anadido, textDecoration = TextDecoration.Underline)
fun estiloAncla(c: Colores) = SpanStyle(textDecoration = TextDecoration.Underline, background = c.gris)
fun estiloNota(c: Colores) = SpanStyle(color = c.secundario, fontSize = 13.sp)

/** Pinta una marca como se ve en la web: tachado gris, añadido resaltado, cambio con las dos partes. */
fun AnnotatedString.Builder.marca(t: Trozo, c: Colores) {
    when (t) {
        is Trozo.Borrado -> withStyle(estiloTachado(c)) { append(t.texto) }
        is Trozo.Anadido -> withStyle(estiloAnadido(c)) { append(t.texto) }
        is Trozo.Cambio -> {
            withStyle(estiloTachado(c)) { append(t.antes) }
            withStyle(estiloAnadido(c)) { append(t.despues) }
        }
        is Trozo.Nota -> withStyle(estiloNota(c)) { append(SIGNO_NOTA) }
        is Trozo.Resaltado -> withStyle(estiloAncla(c)) { append(t.texto) }
        is Trozo.Texto -> enLinea(t.texto, c)
    }
}

private val EN_LINEA = Regex("""\*\*(.+?)\*\*|\*(.+?)\*|`(.+?)`|\[(.+?)]\((.+?)\)""")

/** Negrita, cursiva, código y enlaces de Markdown, sin enseñar los asteriscos. */
fun AnnotatedString.Builder.enLinea(s: String, c: Colores) {
    var i = 0
    for (m in EN_LINEA.findAll(s)) {
        append(s.substring(i, m.range.first))
        val (b, it, code, txt) = m.destructured
        when {
            b.isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(b) }
            it.isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(it) }
            code.isNotEmpty() -> withStyle(SpanStyle(fontFamily = Mono, background = c.gris, fontSize = 14.sp)) { append(code) }
            else -> withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { append(txt) }
        }
        i = m.range.last + 1
    }
    append(s.substring(i))
}

/** El contenido de un bloque, listo para leer. */
fun textoBloque(tipo: String, contenido: String, c: Colores): AnnotatedString = buildAnnotatedString {
    var cuerpo = contenido
    if (tipo == "subtitulo") cuerpo = cuerpo.trimStart('#', ' ')
    if (tipo == "cita") cuerpo = cuerpo.lines().joinToString("\n") { it.removePrefix(">").trimStart() }
    if (tipo == "lista") cuerpo = cuerpo.lines().joinToString("\n") { l ->
        val sangria = l.length - l.trimStart().length
        val t = l.trimStart()
        val sin = when {
            t.startsWith("- ") || t.startsWith("* ") -> "• " + t.drop(2)
            else -> t
        }
        " ".repeat(sangria) + sin
    }
    for (t in Critic.leer(cuerpo)) marca(t, c)
}

/**
 * Pinta el texto de una [Edicion]: cada marcador se ve como su marca. El cursor
 * no puede entrar en una marca: salta a uno de sus lados.
 */
class TransformacionMarcas(private val edicion: Edicion, private val c: Colores) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val s = text.text
        val mapa = IntArray(s.length + 1)
        val salida = buildAnnotatedString {
            for ((i, ch) in s.withIndex()) {
                mapa[i] = length
                val k = Edicion.indice(ch)
                val m = k?.let { edicion.marcas.getOrNull(it) }
                if (m == null) append(ch) else marca(m, c)
            }
            mapa[s.length] = length
        }
        val mapeo = object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = mapa[offset.coerceIn(0, s.length)]
            override fun transformedToOriginal(offset: Int): Int {
                // El último carácter original que empieza en o antes de [offset].
                var i = 0
                while (i < s.length && mapa[i + 1] <= offset) i++
                return i
            }
        }
        return TransformedText(salida, mapeo)
    }
}

/** El color con el que se ve el texto normal de un bloque. */
fun colorTexto(tipo: String, c: Colores): Color = if (tipo == "cita") c.secundario else c.texto
