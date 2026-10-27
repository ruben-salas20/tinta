package dev.rubensalas.tinta.ui.tema

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.rubensalas.tinta.R

/** Los tokens de DESIGN.md. Todo es gris salvo los estados. */
@Immutable
data class Colores(
    val tinta: Color,
    val sobreTinta: Color,
    val papel: Color,
    val hoja: Color,
    val gris: Color,
    val grisMedio: Color,
    val linea: Color,
    val lineaMarcada: Color,
    val texto: Color,
    val secundario: Color,
    val tenue: Color,
    val anadido: Color,
    val idea: Color,
    val ambar: Color,
    val violeta: Color,
    val azul: Color,
    val verde: Color,
)

val Claro = Colores(
    tinta = Color(0xFF121212), sobreTinta = Color(0xFFFFFFFF), papel = Color(0xFFF4F4F4), hoja = Color(0xFFFFFFFF),
    gris = Color(0xFFEFEFEF), grisMedio = Color(0xFFE6E6E6), linea = Color(0xFFE0E0E0), lineaMarcada = Color(0xFFC9C9C9),
    texto = Color(0xFF121212), secundario = Color(0xFF666666), tenue = Color(0xFF9B9B9B), anadido = Color(0xFFE4E4E4),
    idea = Color(0xFF878787), ambar = Color(0xFFC27C0E), violeta = Color(0xFF7452D6), azul = Color(0xFF1F6FD1), verde = Color(0xFF1C9152),
)

val Oscuro = Colores(
    tinta = Color(0xFFECECEC), sobreTinta = Color(0xFF111111), papel = Color(0xFF0C0C0C), hoja = Color(0xFF161616),
    gris = Color(0xFF1D1D1D), grisMedio = Color(0xFF262626), linea = Color(0xFF2A2A2A), lineaMarcada = Color(0xFF3A3A3A),
    texto = Color(0xFFECECEC), secundario = Color(0xFF9A9A9A), tenue = Color(0xFF6A6A6A), anadido = Color(0xFF2E2E2E),
    idea = Color(0xFF8F8F8F), ambar = Color(0xFFEEAB3A), violeta = Color(0xFFAE95FF), azul = Color(0xFF5FA6FF), verde = Color(0xFF4CCF86),
)

/** El color de un estado de artículo o de publicación. */
fun Colores.estado(e: String): Color = when (e) {
    "borrador", "preparando" -> ambar
    "revision" -> violeta
    "listo", "programada" -> azul
    "publicado", "publicada" -> verde
    else -> idea
}

val NOMBRE_ESTADO = mapOf(
    "idea" to "Idea", "borrador" to "Borrador", "revision" to "Revisión", "listo" to "Listo", "publicado" to "Publicado",
    "preparando" to "Preparando", "programada" to "Programada", "publicada" to "Publicada",
)

private fun variable(id: Int, peso: Int, cursiva: Boolean = false) = Font(
    id, FontWeight(peso), if (cursiva) FontStyle.Italic else FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(peso)),
)

val Inter = FontFamily(listOf(400, 500, 600, 700).map { variable(R.font.inter, it) })
val Serif = FontFamily(listOf(400, 600).map { variable(R.font.source_serif, it) } + variable(R.font.source_serif_italic, 400, true))
val Mono = FontFamily(listOf(400, 600).map { variable(R.font.jetbrains_mono, it) })

/** Estilos de DESIGN.md para el móvil: el texto del artículo a 16,5 y el título a 26. */
object Estilos {
    val tituloArticulo = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.01).em)
    val subtitulo = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp)
    val cuerpo = TextStyle(fontFamily = Serif, fontSize = 16.5.sp, lineHeight = 27.sp)
    val vistaPrevia = TextStyle(fontFamily = Serif, fontSize = 14.5.sp, lineHeight = 21.sp)
    val tarjetaTitulo = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 21.sp)
    val codigo = TextStyle(fontFamily = Mono, fontSize = 12.5.sp, lineHeight = 20.sp)
    val ui = TextStyle(fontFamily = Inter, fontSize = 14.sp, lineHeight = 20.sp)
    val uiPeque = TextStyle(fontFamily = Inter, fontSize = 12.5.sp, lineHeight = 17.sp)
    val grupo = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.08.em)
    val pantalla = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, letterSpacing = (-0.02).em)
    val dialogo = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = (-0.01).em)
}

private val LocalColores = staticCompositionLocalOf { Claro }

object Tinta {
    val colores: Colores
        @Composable @ReadOnlyComposable get() = LocalColores.current
}

@Composable
fun TemaTinta(oscuro: Boolean = isSystemInDarkTheme(), contenido: @Composable () -> Unit) {
    val c = if (oscuro) Oscuro else Claro
    val esquema = if (oscuro) {
        darkColorScheme(
            primary = c.tinta, onPrimary = c.sobreTinta, background = c.papel, onBackground = c.texto, surface = c.hoja,
            onSurface = c.texto, surfaceContainer = c.gris, surfaceContainerHigh = c.grisMedio, surfaceContainerLow = c.hoja,
            surfaceContainerLowest = c.hoja, surfaceContainerHighest = c.grisMedio, onSurfaceVariant = c.secundario,
            outline = c.lineaMarcada, outlineVariant = c.linea, secondaryContainer = c.grisMedio, onSecondaryContainer = c.texto,
            surfaceVariant = c.gris,
        )
    } else {
        lightColorScheme(
            primary = c.tinta, onPrimary = c.sobreTinta, background = c.papel, onBackground = c.texto, surface = c.hoja,
            onSurface = c.texto, surfaceContainer = c.gris, surfaceContainerHigh = c.grisMedio, surfaceContainerLow = c.hoja,
            surfaceContainerLowest = c.hoja, surfaceContainerHighest = c.grisMedio, onSurfaceVariant = c.secundario,
            outline = c.lineaMarcada, outlineVariant = c.linea, secondaryContainer = c.grisMedio, onSecondaryContainer = c.texto,
            surfaceVariant = c.gris,
        )
    }
    val base = Typography()
    val tipos = Typography(
        // El título de los AlertDialog.
        headlineSmall = Estilos.dialogo,
        bodyLarge = base.bodyLarge.copy(fontFamily = Inter),
        bodyMedium = base.bodyMedium.copy(fontFamily = Inter),
        bodySmall = base.bodySmall.copy(fontFamily = Inter),
        titleLarge = base.titleLarge.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontFamily = Inter),
        labelLarge = base.labelLarge.copy(fontFamily = Inter),
        labelMedium = base.labelMedium.copy(fontFamily = Inter),
        labelSmall = base.labelSmall.copy(fontFamily = Inter),
    )
    CompositionLocalProvider(LocalColores provides c) {
        MaterialTheme(colorScheme = esquema, typography = tipos, content = contenido)
    }
}
