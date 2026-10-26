package dev.rubensalas.tinta.ui.comun

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rubensalas.tinta.R
import dev.rubensalas.tinta.ui.tema.Estilos
import dev.rubensalas.tinta.ui.tema.Mono
import dev.rubensalas.tinta.ui.tema.NOMBRE_ESTADO
import dev.rubensalas.tinta.ui.tema.Tinta
import dev.rubensalas.tinta.ui.tema.estado

/** El color [c] al [alfa] sobre [fondo]: los tintados de estado de DESIGN.md. */
fun mezcla(c: Color, alfa: Float, fondo: Color) = c.copy(alpha = alfa).compositeOver(fondo)

@DrawableRes
fun iconoRed(icono: String): Int = when (icono) {
    "x" -> R.drawable.red_x
    "linkedin" -> R.drawable.red_linkedin
    "instagram" -> R.drawable.red_instagram
    "youtube" -> R.drawable.red_youtube
    "tiktok" -> R.drawable.red_tiktok
    else -> R.drawable.red_portfolio
}

/** El icono de una red, en el color del texto: nunca en su color de marca. */
@Composable
fun IconoRed(icono: String, modifier: Modifier = Modifier, tam: Dp = 15.dp, color: Color = Tinta.colores.texto) {
    Icon(painterResource(iconoRed(icono)), contentDescription = null, modifier = modifier.size(tam), tint = color)
}

@Composable
fun Logo(modifier: Modifier = Modifier, tam: Dp = 26.dp) {
    Icon(painterResource(R.drawable.ic_notificacion), contentDescription = "Tinta", modifier = modifier.size(tam), tint = Tinta.colores.texto)
}

/** Chip con el punto de color de su estado y el fondo tintado. */
@Composable
fun ChipEstado(estado: String, modifier: Modifier = Modifier) {
    val c = Tinta.colores
    val e = c.estado(estado)
    Row(
        modifier
            .clip(CircleShape)
            .background(mezcla(e, 0.10f, c.hoja))
            .border(1.dp, mezcla(e, 0.35f, c.linea), CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(e))
        Text(NOMBRE_ESTADO[estado] ?: estado, style = Estilos.uiPeque, color = c.texto)
    }
}

@Composable
fun Chip(texto: String, modifier: Modifier = Modifier, lleno: Boolean = false, onClick: (() -> Unit)? = null, icono: (@Composable () -> Unit)? = null) {
    val c = Tinta.colores
    Row(
        modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (lleno) c.tinta else c.gris)
            .border(1.dp, if (lleno) c.tinta else c.linea, RoundedCornerShape(9.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        icono?.invoke()
        Text(texto, style = Estilos.uiPeque.copy(fontSize = 13.sp), color = if (lleno) c.sobreTinta else c.secundario)
    }
}

/** La marca «IA» de lo que propone un agente. */
@Composable
fun MarcaIa() {
    val c = Tinta.colores
    Text(
        "IA",
        Modifier.border(1.dp, c.lineaMarcada, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp),
        style = Estilos.uiPeque.copy(fontFamily = Mono, fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
        color = c.secundario,
    )
}

@Composable
fun Tarjeta(
    modifier: Modifier = Modifier,
    borde: Color = Tinta.colores.linea,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    val c = Tinta.colores
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.hoja)
            .border(1.dp, borde, RoundedCornerShape(16.dp))
            .let {
                when {
                    onLongClick != null -> it.combinedClickable(onLongClick = onLongClick, onClick = onClick ?: {})
                    onClick != null -> it.clickable(onClick = onClick)
                    else -> it
                }
            }
            .padding(14.dp),
        content = contenido,
    )
}

@Composable
fun Grupo(texto: String, modifier: Modifier = Modifier) {
    Text(texto.uppercase(), modifier.padding(start = 2.dp, top = 18.dp, bottom = 8.dp), style = Estilos.grupo, color = Tinta.colores.tenue)
}

/** Banda gris de aviso: sin conexión, cambios esperando. */
@Composable
fun Banda(modifier: Modifier = Modifier, contenido: @Composable RowScope.() -> Unit) {
    val c = Tinta.colores
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.gris).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = contenido,
    )
}

/** Una publicación en una agenda: franja del color del estado a la izquierda. */
@Composable
fun FilaPublicacion(icono: String, titulo: String, estado: String, hora: String?, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val c = Tinta.colores
    val e = c.estado(estado)
    val fondo = when (estado) {
        "idea", "publicada" -> Color.Transparent
        "programada" -> mezcla(e, 0.16f, c.hoja)
        else -> mezcla(e, 0.09f, c.hoja)
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(fondo)
            .let { if (estado == "idea") it.border(1.dp, c.lineaMarcada, RoundedCornerShape(6.dp)) else it }
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).height(30.dp).background(e))
        Row(Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            IconoRed(icono, color = if (estado == "publicada") c.tenue else c.texto)
            Text(
                titulo.ifBlank { "Sin título" }, Modifier.weight(1f), style = Estilos.uiPeque.copy(fontSize = 13.sp),
                color = if (estado == "publicada") c.tenue else if (estado == "idea") c.secundario else c.texto, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            if (estado == "publicada") Text("✓", color = e, style = Estilos.uiPeque)
            hora?.let { Text(it, style = Estilos.uiPeque.copy(fontSize = 11.sp), color = c.tenue) }
        }
    }
}
