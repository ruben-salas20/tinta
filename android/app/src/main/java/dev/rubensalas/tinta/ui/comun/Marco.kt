package dev.rubensalas.tinta.ui.comun

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rubensalas.tinta.ui.tema.Estilos
import dev.rubensalas.tinta.ui.tema.Tinta

enum class Seccion(val etiqueta: String, val icono: ImageVector) {
    Hoy("Hoy", Icons.Outlined.LightMode),
    Articulos("Artículos", Icons.AutoMirrored.Outlined.Article),
    Calendario("Calendario", Icons.Outlined.CalendarMonth),
    Ajustes("Ajustes", Icons.Outlined.Tune),
}

/** La navegación de abajo: la sección elegida lleva la píldora gris. */
@Composable
fun BarraInferior(actual: Seccion, onElegir: (Seccion) -> Unit) {
    val c = Tinta.colores
    Column(Modifier.fillMaxWidth().background(c.hoja)) {
        HorizontalDivider(color = c.linea)
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(top = 8.dp, bottom = 10.dp)) {
            for (s in Seccion.entries) {
                val sel = s == actual
                Column(
                    Modifier.weight(1f).clickable { onElegir(s) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        Modifier.width(56.dp).height(30.dp).clip(CircleShape).background(if (sel) c.grisMedio else c.hoja),
                        contentAlignment = Alignment.Center,
                    ) { Icon(s.icono, null, Modifier.size(20.dp), tint = if (sel) c.texto else c.secundario) }
                    Text(
                        s.etiqueta, fontSize = 11.sp, fontFamily = Estilos.ui.fontFamily,
                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal, color = if (sel) c.texto else c.secundario,
                    )
                }
            }
        }
    }
}

/** Cabecera de una pantalla principal: título grande y acciones a la derecha. */
@Composable
fun Cabecera(titulo: String, conLogo: Boolean = false, acciones: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (conLogo) Logo()
        Text(titulo, style = Estilos.pantalla, color = Tinta.colores.texto)
        Spacer(Modifier.weight(1f))
        acciones()
    }
}

/** El botón flotante de crear: cuadrado redondeado, en tinta. */
@Composable
fun BotonCrear(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Tinta.colores
    Box(
        modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(c.tinta).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Outlined.Add, "Crear", Modifier.size(22.dp), tint = c.sobreTinta) }
}
