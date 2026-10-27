package dev.rubensalas.tinta.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rubensalas.tinta.data.Datos
import dev.rubensalas.tinta.data.local.ArticuloE
import dev.rubensalas.tinta.ui.comun.Chip
import dev.rubensalas.tinta.ui.tema.Estilos
import dev.rubensalas.tinta.ui.tema.Tinta
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Etiquetas y serie del artículo. Lo que aquí se guarda sale en el artículo que se exporta para el portfolio. */
@Composable
fun HojaFicha(datos: Datos, articulo: ArticuloE, acciones: AccionesEditor, onCerrar: () -> Unit) {
    val c = Tinta.colores
    val scope = rememberCoroutineScope()
    val etiquetas = articulo.etiquetas.mapNotNull { id -> datos.etiquetas.firstOrNull { it.id == id } }
    val nombres = etiquetas.map { it.nombre }
    val serie = datos.serie(articulo.serieId)
    var nueva by rememberSaveable { mutableStateOf("") }
    var creando by rememberSaveable { mutableStateOf(false) }
    var nombreSerie by rememberSaveable { mutableStateOf("") }
    val sugerencias = datos.etiquetas
        .filter { e -> nombres.none { it.equals(e.nombre, ignoreCase = true) } && e.nombre.contains(nueva.trim(), ignoreCase = true) }
        .sortedBy { it.nombre }
        .take(8)

    fun añadir(nombre: String) {
        val n = nombre.trim().lowercase()
        if (n.isEmpty() || nombres.any { it.equals(n, ignoreCase = true) }) return
        scope.launch { acciones.cambiarEtiquetas(articulo.id, nombres + n) }
    }

    fun añadirEscrita() {
        añadir(nueva)
        nueva = ""
    }

    fun cambiarSerie(id: String?) {
        creando = false
        scope.launch { acciones.cambiarSerie(articulo.id, id, null) }
    }

    fun crearSerie() {
        val n = nombreSerie.trim()
        if (n.isEmpty()) return
        nombreSerie = ""
        creando = false
        val existente = datos.series.find { it.nombre.trim().equals(n, ignoreCase = true) }
        if (existente != null) {
            if (existente.id != articulo.serieId) scope.launch { acciones.cambiarSerie(articulo.id, existente.id, null) }
            return
        }
        scope.launch { acciones.cambiarSerie(articulo.id, acciones.crearSerie(n), null) }
    }

    HojaInferior(onCerrar) {
        Text("Etiquetas y serie", style = Estilos.ui.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold), color = c.texto)
        Text(
            "Las etiquetas y la serie salen en el artículo que exportas para el portfolio.",
            Modifier.padding(top = 4.dp), style = Estilos.uiPeque.copy(fontSize = 13.sp), color = c.secundario,
        )
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            Encabezado("Etiquetas")
            if (etiquetas.isEmpty()) Text("Este artículo aún no tiene etiquetas.", style = Estilos.uiPeque, color = c.tenue)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (e in etiquetas) Chip(
                    e.nombre,
                    onClick = { scope.launch { acciones.cambiarEtiquetas(articulo.id, nombres - e.nombre) } },
                    icono = { Icon(Icons.Outlined.Close, "Quitar ${e.nombre}", Modifier.size(14.dp), tint = c.secundario) },
                )
            }
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CampoGris(nueva, { nueva = it }, "Escribe una etiqueta", Modifier.weight(1f), ImeAction.Done) { añadirEscrita() }
                Icon(
                    Icons.Outlined.Add, "Añadir etiqueta",
                    Modifier.size(38.dp).clip(CircleShape).background(c.gris).clickable { añadirEscrita() }.padding(9.dp), tint = c.texto,
                )
            }
            if (sugerencias.isNotEmpty()) {
                Text("Otras etiquetas", Modifier.padding(top = 12.dp, bottom = 6.dp), style = Estilos.uiPeque, color = c.tenue)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (e in sugerencias) Chip(
                        e.nombre,
                        onClick = { añadir(e.nombre) },
                        icono = { Icon(Icons.Outlined.Add, null, Modifier.size(14.dp), tint = c.secundario) },
                    )
                }
            }

            Encabezado("Serie")
            Text(
                serie?.nombre ?: "Sin serie", style = Estilos.ui.copy(fontWeight = FontWeight.Medium),
                color = if (serie == null) c.tenue else c.texto,
            )
            Column(Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, c.linea, RoundedCornerShape(12.dp))) {
                for (s in datos.series.sortedBy { it.nombre }) {
                    FilaSerie(s.nombre, marcada = s.id == articulo.serieId) { if (s.id != articulo.serieId) cambiarSerie(s.id) }
                    HorizontalDivider(color = c.linea)
                }
                FilaSerie("Nueva serie…") { creando = true }
                if (serie != null) {
                    HorizontalDivider(color = c.linea)
                    FilaSerie("Quitar de la serie", color = c.secundario) { cambiarSerie(null) }
                }
            }
            if (creando) Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CampoGris(nombreSerie, { nombreSerie = it }, "Nombre de la serie", Modifier.weight(1f), ImeAction.Done) { crearSerie() }
                BotonPildora("Crear", principal = true, onClick = { crearSerie() })
            }
            if (serie != null) CampoParte(serie.id, articulo.serieParte) { n ->
                scope.launch { acciones.cambiarSerie(articulo.id, serie.id, n) }
            }
        }
    }
}

@Composable
private fun Encabezado(texto: String) {
    Text(texto, Modifier.padding(top = 18.dp, bottom = 8.dp), style = Estilos.ui.copy(fontWeight = FontWeight.SemiBold), color = Tinta.colores.texto)
}

@Composable
private fun CampoGris(valor: String, onCambio: (String) -> Unit, pista: String, modifier: Modifier, accion: ImeAction, onHecho: () -> Unit) {
    val c = Tinta.colores
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(c.gris).padding(horizontal = 14.dp, vertical = 10.dp)) {
        if (valor.isEmpty()) Text(pista, style = Estilos.ui, color = c.tenue)
        BasicTextField(
            value = valor,
            onValueChange = onCambio,
            modifier = Modifier.fillMaxWidth(),
            textStyle = Estilos.ui.copy(color = c.texto),
            cursorBrush = SolidColor(c.texto),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = accion),
            keyboardActions = KeyboardActions(onDone = { onHecho() }),
        )
    }
}

@Composable
private fun FilaSerie(texto: String, marcada: Boolean = false, color: Color = Tinta.colores.texto, onClick: () -> Unit) {
    val c = Tinta.colores
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(texto, Modifier.weight(1f), style = Estilos.ui, color = color)
        if (marcada) Icon(Icons.Outlined.Check, null, Modifier.size(18.dp), tint = c.texto)
    }
}

/** El número de parte. Guarda tras una pausa breve, igual que el título del editor. */
@Composable
private fun CampoParte(serieId: String, parte: Long?, onCambio: (Long?) -> Unit) {
    val c = Tinta.colores
    var texto by remember(serieId, parte) { mutableStateOf(parte?.toString().orEmpty()) }
    LaunchedEffect(texto) {
        if (texto == parte?.toString().orEmpty()) return@LaunchedEffect
        delay(700)
        onCambio(texto.toLongOrNull())
    }
    Row(Modifier.padding(top = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Parte", Modifier.weight(1f), style = Estilos.ui, color = c.secundario)
        BasicTextField(
            value = texto,
            onValueChange = { texto = it.filter(Char::isDigit).take(4) },
            modifier = Modifier.width(72.dp).clip(RoundedCornerShape(12.dp)).background(c.gris).padding(horizontal = 14.dp, vertical = 10.dp),
            textStyle = Estilos.ui.copy(color = c.texto),
            cursorBrush = SolidColor(c.texto),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
}
