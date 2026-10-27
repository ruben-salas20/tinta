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
import dev.rubensalas.tinta.data.local.RangoAutoria
import dev.rubensalas.tinta.data.texto.Edicion
import dev.rubensalas.tinta.data.texto.Estilo
import dev.rubensalas.tinta.data.texto.SIGNO_NOTA
import dev.rubensalas.tinta.data.texto.Trozo
import dev.rubensalas.tinta.data.texto.piezasLectura
import dev.rubensalas.tinta.data.texto.tramosAutoria
import dev.rubensalas.tinta.ui.tema.Colores
import dev.rubensalas.tinta.ui.tema.Mono

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
        is Trozo.Texto -> append(t.texto)
    }
}

/** El contenido de un bloque, listo para leer. Lo que viene de una propuesta del agente sale con un fondo suave. */
fun textoBloque(tipo: String, contenido: String, c: Colores, autoria: List<RangoAutoria> = emptyList()): AnnotatedString {
    val piezas = piezasLectura(tipo, contenido)
    return buildAnnotatedString {
        for (p in piezas) {
            val estilo = estiloDe(p.estilo, c)
            if (estilo == null) append(p.texto) else withStyle(estilo) { append(p.texto) }
        }
        for (t in tramosAutoria(piezas, autoria)) addStyle(estiloAutoria(c), t.first, t.last + 1)
    }
}

private fun estiloDe(e: Estilo, c: Colores): SpanStyle? = when (e) {
    Estilo.Normal -> null
    Estilo.Negrita -> SpanStyle(fontWeight = FontWeight.SemiBold)
    Estilo.Cursiva -> SpanStyle(fontStyle = FontStyle.Italic)
    Estilo.Codigo -> SpanStyle(fontFamily = Mono, background = c.gris, fontSize = 14.sp)
    Estilo.Enlace -> SpanStyle(textDecoration = TextDecoration.Underline)
    Estilo.Tachado -> estiloTachado(c)
    Estilo.Anadido -> estiloAnadido(c)
    Estilo.Ancla -> estiloAncla(c)
    Estilo.Nota -> estiloNota(c)
}

/** Fondo muy suave: se ve que el texto es del agente, sin llamar la atención. */
fun estiloAutoria(c: Colores) = SpanStyle(background = c.violeta.copy(alpha = 0.12f))

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
