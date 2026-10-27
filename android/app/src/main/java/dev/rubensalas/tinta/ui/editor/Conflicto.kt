package dev.rubensalas.tinta.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.rubensalas.tinta.data.BloqueVista
import dev.rubensalas.tinta.data.dispositivo
import dev.rubensalas.tinta.data.texto.Critic
import dev.rubensalas.tinta.ui.comun.Banda
import dev.rubensalas.tinta.ui.comun.estiloAnadido
import dev.rubensalas.tinta.ui.tema.Colores
import dev.rubensalas.tinta.ui.tema.Estilos
import dev.rubensalas.tinta.ui.tema.Tinta

/**
 * Las dos redacciones de un párrafo que cambió en dos sitios a la vez. La
 * activa es la que ya tenía el servicio; la otra llegó después y chocó.
 */
@Composable
fun PantallaConflicto(
    b: BloqueVista,
    onQuedarseConLaQueLlego: () -> Unit,
    onQuedarseConLaQueEstaba: () -> Unit,
    onMasTarde: () -> Unit,
) {
    val c = Tinta.colores
    val llego = b.conflicto ?: return
    val estaba = b.activa ?: return
    val (dLlego, dEstaba) = diferencias(Critic.limpio(llego.contenido), Critic.limpio(estaba.contenido), c)
    Column(Modifier.fillMaxSize().background(c.papel)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Close, "Cerrar", Modifier.size(38.dp).clip(RoundedCornerShape(99.dp)).clickable(onClick = onMasTarde).padding(9.dp), tint = c.texto)
            Text("Elige una redacción", style = Estilos.ui.copy(fontWeight = FontWeight.SemiBold), color = c.texto)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Banda {
                Icon(Icons.AutoMirrored.Outlined.CallSplit, null, Modifier.size(14.dp), tint = c.secundario)
                Text(
                    buildAnnotatedString {
                        append("Este párrafo se editó en dos sitios sin que se vieran, y Tinta guardó las dos redacciones. ")
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = c.texto)) { append("La que no elijas queda como variante.") }
                    },
                    style = Estilos.uiPeque, color = c.secundario,
                )
            }
            Redaccion("Llegó después", listOfNotNull(dispositivo(llego.dispositivo), llego.cambiado.take(16).replace('T', ' ')).joinToString(" · "), dLlego, true)
            Redaccion("La que ya estaba", estaba.cambiado.take(16).replace('T', ' '), dEstaba, false)
            Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BotonPildora("Quedarme con la que llegó después", Modifier.fillMaxWidth(), principal = true, onClick = onQuedarseConLaQueLlego)
                BotonPildora("Quedarme con la que ya estaba", Modifier.fillMaxWidth(), onClick = onQuedarseConLaQueEstaba)
                BotonPildora("Decidir más tarde", Modifier.fillMaxWidth(), fantasma = true, onClick = onMasTarde)
            }
        }
    }
}

@Composable
private fun Redaccion(titulo: String, cuando: String, texto: AnnotatedString, marcada: Boolean) {
    val c = Tinta.colores
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.hoja)
            .border(1.dp, if (marcada) c.texto else c.linea, RoundedCornerShape(14.dp))
            .padding(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(titulo, style = Estilos.uiPeque.copy(fontWeight = FontWeight.SemiBold), color = c.texto)
            Text(cuando, style = Estilos.uiPeque, color = c.tenue)
        }
        Text(texto, Modifier.padding(top = 5.dp), style = Estilos.vistaPrevia.copy(color = c.texto))
    }
}

/** Resalta lo que cambia entre dos textos: lo que sobra del principio y del final común. */
fun diferencias(a: String, b: String, c: Colores): Pair<AnnotatedString, AnnotatedString> {
    val pa = a.split(" ")
    val pb = b.split(" ")
    var ini = 0
    while (ini < pa.size && ini < pb.size && pa[ini] == pb[ini]) ini++
    var fin = 0
    while (fin < pa.size - ini && fin < pb.size - ini && pa[pa.size - 1 - fin] == pb[pb.size - 1 - fin]) fin++
    fun marcar(p: List<String>) = buildAnnotatedString {
        p.forEachIndexed { i, w ->
            if (i > 0) append(" ")
            if (i >= ini && i < p.size - fin) withStyle(estiloAnadido(c)) { append(w) } else append(w)
        }
    }
    return marcar(pa) to marcar(pb)
}
