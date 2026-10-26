package dev.rubensalas.tinta.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rubensalas.tinta.data.BloqueVista
import dev.rubensalas.tinta.data.api.ApiException
import dev.rubensalas.tinta.data.api.EntradaHistorial
import dev.rubensalas.tinta.data.autor
import dev.rubensalas.tinta.data.local.VarianteBloqueE
import dev.rubensalas.tinta.data.texto.Critic
import dev.rubensalas.tinta.ui.comun.MarcaIa
import dev.rubensalas.tinta.ui.comun.textoBloque
import dev.rubensalas.tinta.ui.tema.Estilos
import dev.rubensalas.tinta.ui.tema.Tinta

/** Hoja que sube desde abajo sobre un velo. Se cierra tocando el velo. */
@Composable
fun HojaInferior(onCerrar: () -> Unit, contenido: @Composable ColumnScope.() -> Unit) {
    val c = Tinta.colores
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).clickable(MutableInteractionSource(), null, onClick = onCerrar))
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = 640.dp)
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)).background(c.hoja)
                .clickable(MutableInteractionSource(), null) {}
                .navigationBarsPadding().padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 22.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.lineaMarcada))
            Spacer(Modifier.height(14.dp))
            contenido()
        }
    }
}

@Composable
fun BotonPildora(texto: String, modifier: Modifier = Modifier, principal: Boolean = false, fantasma: Boolean = false, onClick: () -> Unit, icono: (@Composable () -> Unit)? = null) {
    val c = Tinta.colores
    Row(
        modifier.height(40.dp).clip(RoundedCornerShape(99.dp))
            .background(if (principal) c.tinta else if (fantasma) Color.Transparent else c.hoja)
            .let { if (!principal && !fantasma) it.border(1.dp, c.linea, RoundedCornerShape(99.dp)) else it }
            .clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        icono?.invoke()
        Text(texto, style = Estilos.ui, color = if (principal) c.sobreTinta else c.texto)
    }
}

@Composable
fun TarjetaVariante(letra: Char, v: VarianteBloqueE, activa: Boolean, tipo: String, contenido: @Composable ColumnScope.() -> Unit = {}) {
    val c = Tinta.colores
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, if (activa) c.texto else c.linea, RoundedCornerShape(14.dp)).padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (activa) "$letra · activa" else "$letra", style = Estilos.uiPeque.copy(fontWeight = FontWeight.SemiBold), color = c.texto)
            if (v.origen.startsWith("ia:")) MarcaIa()
            Text(
                when {
                    v.origen.startsWith("ia:") -> "${autor(v.origen)} · propuesta"
                    v.origen == "conflicto" -> "conflicto"
                    else -> "Tú"
                },
                style = Estilos.uiPeque, color = c.tenue,
            )
        }
        Text(
            textoBloque(tipo, v.contenido.ifBlank { "(vacía)" }, c), Modifier.padding(top = 5.dp),
            style = Estilos.vistaPrevia.copy(color = c.texto), maxLines = 4, overflow = TextOverflow.Ellipsis,
        )
        contenido()
    }
}

@Composable
fun HojaVariantes(
    b: BloqueVista,
    onActivar: (VarianteBloqueE) -> Unit,
    onDescartar: (VarianteBloqueE) -> Unit,
    onNueva: () -> Unit,
    onCerrar: () -> Unit,
) {
    val c = Tinta.colores
    HojaInferior(onCerrar) {
        Text("Variantes del párrafo", style = Estilos.ui.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold), color = c.texto)
        Text("La activa es la que se exporta. Las demás quedan guardadas.", Modifier.padding(top = 4.dp, bottom = 14.dp), style = Estilos.uiPeque.copy(fontSize = 13.sp), color = c.secundario)
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            b.variantes.forEachIndexed { i, v ->
                val activa = v.id == b.activa?.id
                TarjetaVariante('A' + i, v, activa, b.bloque.tipo) {
                    if (!activa) Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        BotonPildora("Activar", onClick = { onActivar(v) })
                        BotonPildora("Descartar", fantasma = true, onClick = { onDescartar(v) })
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        BotonPildora("Nueva variante", Modifier.fillMaxWidth(), onClick = onNueva, icono = { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp), tint = c.texto) })
    }
}

@Composable
fun HojaHistorial(cargar: suspend () -> List<EntradaHistorial>, onRestaurar: (EntradaHistorial) -> Unit, onCerrar: () -> Unit) {
    val c = Tinta.colores
    var entradas by remember { mutableStateOf<List<EntradaHistorial>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try {
            entradas = cargar()
        } catch (e: ApiException) {
            error = e.message
        }
    }
    HojaInferior(onCerrar) {
        Text("Historial del párrafo", style = Estilos.ui.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold), color = c.texto)
        Text("Las versiones anteriores, de la más reciente a la más antigua.", Modifier.padding(top = 4.dp, bottom = 14.dp), style = Estilos.uiPeque.copy(fontSize = 13.sp), color = c.secundario)
        when {
            error != null -> Text(error!!, style = Estilos.ui, color = c.secundario)
            entradas == null -> Text("Cargando…", style = Estilos.ui, color = c.secundario)
            entradas!!.isEmpty() -> Text("Aún no hay versiones anteriores.", style = Estilos.ui, color = c.secundario)
            else -> Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (e in entradas!!) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, c.linea, RoundedCornerShape(12.dp)).padding(12.dp)) {
                    Text("${e.creado.take(16).replace('T', ' ')} · ${e.dispositivo.substringBefore(':')}", style = Estilos.uiPeque, color = c.tenue)
                    Text(Critic.limpio(e.contenido).ifBlank { "(vacío)" }, Modifier.padding(vertical = 4.dp), style = Estilos.vistaPrevia, color = c.texto, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text("Restaurar", Modifier.clickable { onRestaurar(e) }.padding(vertical = 4.dp), style = Estilos.ui.copy(fontWeight = FontWeight.Medium), color = c.texto)
                }
            }
        }
    }
}

@Composable
fun DialogoNota(onGuardar: (String) -> Unit, onCerrar: () -> Unit) {
    val c = Tinta.colores
    var texto by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Nota al margen") },
        text = {
            Column {
                Text("Si seleccionaste texto, la nota se ancla a él.", style = Estilos.uiPeque, color = c.secundario)
                BasicTextField(
                    texto, { texto = it },
                    Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 80.dp).clip(RoundedCornerShape(8.dp)).background(c.gris).padding(10.dp),
                    textStyle = Estilos.ui.copy(color = c.texto), cursorBrush = SolidColor(c.texto),
                )
            }
        },
        confirmButton = { TextButton({ onGuardar(texto) }, enabled = texto.isNotBlank()) { Text("Guardar") } },
        dismissButton = { TextButton(onCerrar) { Text("Cancelar") } },
    )
}
