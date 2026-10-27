package dev.rubensalas.tinta.ui.calendario

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.rubensalas.tinta.data.Datos
import dev.rubensalas.tinta.data.calendario.esImagen
import dev.rubensalas.tinta.data.calendario.horaEntera
import dev.rubensalas.tinta.data.calendario.longitud
import dev.rubensalas.tinta.data.calendario.partir
import dev.rubensalas.tinta.data.calendario.rangoHoras
import dev.rubensalas.tinta.data.local.CanalE
import dev.rubensalas.tinta.data.local.MedioE
import dev.rubensalas.tinta.data.local.Parte
import dev.rubensalas.tinta.data.local.PublicacionE
import dev.rubensalas.tinta.ui.comun.BotonCrear
import dev.rubensalas.tinta.ui.comun.Cabecera
import dev.rubensalas.tinta.ui.comun.Chip
import dev.rubensalas.tinta.ui.comun.FilaPublicacion
import dev.rubensalas.tinta.ui.comun.Grupo
import dev.rubensalas.tinta.ui.comun.IconoRed
import dev.rubensalas.tinta.ui.comun.Tarjeta
import dev.rubensalas.tinta.ui.comun.diaCorto
import dev.rubensalas.tinta.ui.comun.fecha
import dev.rubensalas.tinta.ui.comun.fechaCorta
import dev.rubensalas.tinta.ui.comun.fechaLarga
import dev.rubensalas.tinta.ui.comun.mezcla
import dev.rubensalas.tinta.ui.editor.BotonPildora
import dev.rubensalas.tinta.ui.tema.Estilos
import dev.rubensalas.tinta.ui.tema.Mono
import dev.rubensalas.tinta.ui.tema.NOMBRE_ESTADO
import dev.rubensalas.tinta.ui.tema.Tinta
import dev.rubensalas.tinta.ui.tema.estado
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** La agenda: lo atrasado arriba y después cada día con algo, de hoy en adelante. «Mes» muestra una cuadrícula. */
@Composable
fun PantallaCalendario(datos: Datos, hoy: LocalDate, onPublicacion: (String) -> Unit, onNueva: () -> Unit, vistaInicial: String = "semana") {
    val c = Tinta.colores
    var vista by rememberSaveable { mutableStateOf(vistaInicial) }
    val d = hoy.toString()
    val atrasadas = datos.publicaciones.filter { it.fecha < d && it.estado != "publicada" }.sortedWith(Datos.ORDEN)
    val dias = datos.publicaciones.filter { it.fecha >= d }.sortedWith(Datos.ORDEN).groupBy { it.fecha }
    val recientes = datos.publicaciones.filter { it.fecha < d && it.fecha >= hoy.minusDays(14).toString() && it.estado == "publicada" }
        .sortedWith(Datos.ORDEN).reversed()
    Box(Modifier.fillMaxSize().background(c.papel)) {
        Column(Modifier.fillMaxSize()) {
            Cabecera("Calendario")
            Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("Semana", lleno = vista == "semana", onClick = { vista = "semana" })
                Chip("Mes", lleno = vista == "mes", onClick = { vista = "mes" })
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 96.dp)) {
                if (vista == "mes") Mes(datos, hoy, onPublicacion)
                else {
                    if (atrasadas.isNotEmpty()) {
                        Grupo("Atrasadas")
                        Tarjeta { Dia(null, atrasadas, datos, onPublicacion, conFecha = true) }
                    }
                    Grupo("Próximas")
                    if (dias.isEmpty()) Text("Nada planeado. Pulsa + para añadir una publicación.", style = Estilos.ui, color = c.secundario)
                    else Tarjeta {
                        dias.entries.forEachIndexed { i, (f, ps) ->
                            if (i > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp), color = c.linea)
                            DiaPorHora(fecha(f), ps, datos, onPublicacion)
                        }
                    }
                    if (recientes.isNotEmpty()) {
                        Grupo("Publicadas hace poco")
                        Tarjeta { Dia(null, recientes, datos, onPublicacion, conFecha = true) }
                    }
                }
            }
        }
        BotonCrear(Modifier.align(Alignment.BottomEnd).padding(18.dp), onClick = onNueva)
    }
}

/** La cuadrícula del mes con la lista del día elegido debajo. Las semanas empiezan en lunes. */
@Composable
private fun Mes(datos: Datos, hoy: LocalDate, onPublicacion: (String) -> Unit) {
    val c = Tinta.colores
    var mes by rememberSaveable { mutableStateOf(YearMonth.from(hoy).toString()) }
    var elegido by rememberSaveable { mutableStateOf<String?>(hoy.toString()) }
    val actual = YearMonth.parse(mes)
    val porDia = datos.publicaciones.sortedWith(Datos.ORDEN).groupBy { it.fecha }
    val primero = actual.atDay(1)
    val inicio = primero.minusDays(primero.dayOfWeek.value - 1L)
    val titulo = actual.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale("es"))).replaceFirstChar { it.uppercase() }
    fun ir(nuevo: YearMonth) { mes = nuevo.toString(); elegido = null }

    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).clickable { ir(actual.minusMonths(1)) }, contentAlignment = Alignment.Center) {
            Text("‹", style = Estilos.ui.copy(fontSize = 22.sp), color = c.texto)
        }
        Text(titulo, Modifier.weight(1f), textAlign = TextAlign.Center, style = Estilos.ui.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold), color = c.texto)
        Box(Modifier.size(40.dp).clip(CircleShape).clickable { ir(actual.plusMonths(1)) }, contentAlignment = Alignment.Center) {
            Text("›", style = Estilos.ui.copy(fontSize = 22.sp), color = c.texto)
        }
        Chip("Hoy", onClick = { mes = YearMonth.from(hoy).toString(); elegido = hoy.toString() })
    }
    Row(Modifier.fillMaxWidth()) {
        for (letra in listOf("L", "M", "X", "J", "V", "S", "D")) Text(letra, Modifier.weight(1f), textAlign = TextAlign.Center, style = Estilos.uiPeque, color = c.tenue)
    }
    for (semana in 0 until 6) Row(Modifier.fillMaxWidth()) {
        for (dia in 0 until 7) {
            val d = inicio.plusDays(semana * 7L + dia)
            val k = d.toString()
            CeldaDia(
                Modifier.weight(1f), d, enMes = YearMonth.from(d) == actual, esHoy = d == hoy, sel = k == elegido, ps = porDia[k].orEmpty(),
                onClick = { elegido = k; mes = YearMonth.from(d).toString() },
            )
        }
    }

    Spacer(Modifier.height(12.dp))
    val ps = elegido?.let { porDia[it] }
    val diaElegido = elegido?.let(::fecha)
    when {
        diaElegido == null -> Text("Toca un día para ver sus publicaciones.", Modifier.padding(top = 4.dp), style = Estilos.ui, color = c.secundario)
        ps == null -> {
            Grupo(fechaLarga(diaElegido))
            Text("Nada planeado este día.", style = Estilos.ui, color = c.secundario)
        }
        else -> {
            Grupo(fechaLarga(diaElegido))
            Tarjeta { Dia(null, ps, datos, onPublicacion) }
        }
    }
}

@Composable
private fun CeldaDia(modifier: Modifier, d: LocalDate, enMes: Boolean, esHoy: Boolean, sel: Boolean, ps: List<PublicacionE>, onClick: () -> Unit) {
    val c = Tinta.colores
    Column(
        modifier.height(54.dp).padding(horizontal = 1.dp).alpha(if (enMes) 1f else 0.4f)
            .clip(RoundedCornerShape(10.dp)).background(if (sel) c.gris else Color.Transparent).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(if (esHoy) c.tinta else Color.Transparent), contentAlignment = Alignment.Center) {
            Text("${d.dayOfMonth}", style = Estilos.ui.copy(fontSize = 14.sp, fontWeight = if (esHoy || sel) FontWeight.SemiBold else FontWeight.Normal), color = if (esHoy) c.sobreTinta else c.texto)
        }
        Row(Modifier.padding(top = 3.dp).height(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (p in ps.take(3)) Box(Modifier.size(5.dp).clip(CircleShape).background(c.estado(p.estado)))
            if (ps.size > 3) Text("+${ps.size - 3}", style = Estilos.uiPeque.copy(fontSize = 9.sp, lineHeight = 11.sp), color = c.secundario)
        }
    }
}

@Composable
private fun Dia(d: LocalDate?, ps: List<PublicacionE>, datos: Datos, onPublicacion: (String) -> Unit, conFecha: Boolean = false) {
    val c = Tinta.colores
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (d != null) Column(Modifier.width(38.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${d.dayOfMonth}", style = Estilos.ui.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold), color = c.texto)
            Text(diaCorto(d).uppercase(), style = Estilos.uiPeque.copy(fontSize = 11.sp), color = c.tenue)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (p in ps) {
                val extra = if (conFecha) fecha(p.fecha)?.let(::fechaCorta) else p.hora
                FilaPublicacion(datos.canal(p.canalId)?.icono ?: "", p.titulo, p.estado, extra) { onPublicacion(p.id) }
            }
        }
    }
}

/** Un día por horas: arriba la franja «Sin hora» y después una fila por hora de la rejilla. */
@Composable
private fun DiaPorHora(d: LocalDate?, ps: List<PublicacionE>, datos: Datos, onPublicacion: (String) -> Unit) {
    val c = Tinta.colores
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (d != null) Column(Modifier.width(38.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${d.dayOfMonth}", style = Estilos.ui.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold), color = c.texto)
            Text(diaCorto(d).uppercase(), style = Estilos.uiPeque.copy(fontSize = 11.sp), color = c.tenue)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val sinHora = ps.filter { it.hora == null }
            if (sinHora.isNotEmpty()) {
                Text("Sin hora", style = Estilos.uiPeque, color = c.tenue)
                for (p in sinHora) FilaPublicacion(datos.canal(p.canalId)?.icono ?: "", p.titulo, p.estado, null) { onPublicacion(p.id) }
            }
            for (h in rangoHoras(ps.mapNotNull { horaEntera(it.hora) })) Row(Modifier.heightIn(min = 28.dp), verticalAlignment = Alignment.Top) {
                Text("%02d:00".format(h), Modifier.width(44.dp).padding(top = 6.dp), style = Estilos.uiPeque, color = c.tenue)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (p in ps.filter { horaEntera(it.hora) == h }) {
                        FilaPublicacion(datos.canal(p.canalId)?.icono ?: "", p.titulo, p.estado, p.hora) { onPublicacion(p.id) }
                    }
                }
            }
        }
    }
}

/** Lo que el detalle de una publicación pide a la app. */
interface AccionesPublicacion {
    fun cambiar(campos: Map<String, String?>)
    fun partes(partes: List<Parte>)
    fun publicada(enlace: String)
    fun borrar()
    fun copiar(texto: String)
    fun elegirImagen(onElegida: (String) -> Unit)
    fun abrirArticulo(id: String)
}

private val ESTADOS_PUB = listOf("idea", "preparando", "programada", "publicada")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaPublicacion(datos: Datos, publicacionId: String, acciones: AccionesPublicacion, imagen: (String) -> Any?, onVolver: () -> Unit) {
    val c = Tinta.colores
    val p = datos.publicaciones.firstOrNull { it.id == publicacionId }
    if (p == null) {
        Box(Modifier.fillMaxSize().background(c.papel), contentAlignment = Alignment.Center) { Text("Esta publicación ya no existe.", color = c.secundario) }
        return
    }
    val canal = datos.canal(p.canalId)
    val formato = canal?.formatos?.firstOrNull { it.id == p.formato }
    var partes by remember(p.id) { mutableStateOf(p.partes.ifEmpty { listOf(Parte()) }) }
    var titulo by remember(p.id) { mutableStateOf(p.titulo) }
    var elegirFecha by remember { mutableStateOf(false) }
    var elegirHora by remember { mutableStateOf(false) }
    var pedirEnlace by remember { mutableStateOf(false) }
    var confirmarBorrar by remember { mutableStateOf(false) }

    LaunchedEffect(partes) { if (partes != p.partes) { delay(700); acciones.partes(partes) } }
    LaunchedEffect(titulo) { if (titulo != p.titulo) { delay(700); acciones.cambiar(mapOf("titulo" to titulo)) } }

    Column(Modifier.fillMaxSize().background(c.papel)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver", Modifier.size(38.dp).clip(RoundedCornerShape(99.dp)).clickable(onClick = onVolver).padding(9.dp), tint = c.texto)
            IconoRed(canal?.icono ?: "")
            BasicTextField(
                titulo, { titulo = it.replace("\n", " ") }, Modifier.weight(1f),
                textStyle = Estilos.ui.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.texto), singleLine = true,
                cursorBrush = SolidColor(c.texto),
                decorationBox = { campo -> if (titulo.isEmpty()) Text("Título", style = Estilos.ui, color = c.tenue); campo() },
            )
            Icon(Icons.Outlined.Delete, "Borrar", Modifier.size(38.dp).clip(RoundedCornerShape(99.dp)).clickable { confirmarBorrar = true }.padding(9.dp), tint = c.secundario)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Tarjeta {
                Campo("Fecha") { Text(fecha(p.fecha)?.let(::fechaCorta) ?: p.fecha, Modifier.clickable { elegirFecha = true }, style = Estilos.ui, color = c.texto) }
                HorizontalDivider(color = c.linea)
                Campo("Hora") {
                    Text(p.hora ?: "Sin hora", Modifier.clickable { elegirHora = true }, style = Estilos.ui, color = if (p.hora == null) c.tenue else c.texto)
                    if (p.hora != null) Text("Quitar", Modifier.padding(start = 12.dp).clickable { acciones.cambiar(mapOf("hora" to null)) }, style = Estilos.uiPeque, color = c.secundario)
                }
                HorizontalDivider(color = c.linea)
                Campo("Formato") {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (f in canal?.formatos.orEmpty()) Chip(f.nombre, lleno = f.id == p.formato, onClick = {
                            val nuevas = if (!f.partes && partes.size > 1) listOf(Parte(partes.joinToString("\n\n") { it.texto }, partes.flatMap { it.medios })) else null
                            acciones.cambiar(mapOf("formato" to f.id))
                            if (nuevas != null) partes = nuevas
                        })
                    }
                }
                HorizontalDivider(color = c.linea)
                Campo("Estado") {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (e in ESTADOS_PUB) {
                            val sel = e == p.estado
                            val col = c.estado(e)
                            Text(
                                NOMBRE_ESTADO[e] ?: e,
                                Modifier.clip(RoundedCornerShape(6.dp))
                                    .background(if (sel) mezcla(col, 0.14f, c.hoja) else c.hoja)
                                    .border(1.dp, if (sel) col else c.linea, RoundedCornerShape(6.dp))
                                    .clickable { if (e == "publicada") pedirEnlace = true else acciones.cambiar(mapOf("estado" to e)) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                style = Estilos.uiPeque, color = if (sel) c.texto else c.secundario,
                            )
                        }
                    }
                }
                p.articuloId?.let { aid ->
                    HorizontalDivider(color = c.linea)
                    Campo("Artículo") { Text(datos.titulo(aid), Modifier.clickable { acciones.abrirArticulo(aid) }, style = Estilos.ui, color = c.texto, maxLines = 1) }
                }
                p.enlace?.let { e ->
                    HorizontalDivider(color = c.linea)
                    Campo("Enlace") { Text(e, style = Estilos.uiPeque, color = c.texto, maxLines = 1) }
                }
            }
            val limite = formato?.limite
            Grupo(if (formato?.partes == true) "Posts del ${formato.nombre.lowercase()}" else "Texto")
            partes.forEachIndexed { i, parte ->
                EditorParte(
                    parte, i, partes.size, limite, formato?.partes == true, imagen, datos.medios,
                    onTexto = { t -> partes = partes.toMutableList().also { it[i] = parte.copy(texto = t) } },
                    onCopiar = { acciones.copiar(parte.texto) },
                    onPartir = {
                        val (a, b) = partir(parte.texto, limite ?: Int.MAX_VALUE)
                        partes = partes.toMutableList().also { it[i] = parte.copy(texto = a); it.add(i + 1, Parte(b)) }
                    },
                    onQuitar = { partes = partes.toMutableList().also { it.removeAt(i) } },
                    onSubir = { if (i > 0) partes = partes.toMutableList().also { val x = it[i]; it[i] = it[i - 1]; it[i - 1] = x } },
                    onBajar = { if (i < partes.size - 1) partes = partes.toMutableList().also { val x = it[i]; it[i] = it[i + 1]; it[i + 1] = x } },
                    onImagen = { acciones.elegirImagen { id -> partes = partes.toMutableList().also { it[i] = it[i].copy(medios = it[i].medios + id) } } },
                    onQuitarMedio = { m -> partes = partes.toMutableList().also { it[i] = it[i].copy(medios = it[i].medios - m) } },
                )
                Spacer(Modifier.height(8.dp))
            }
            if (formato?.partes == true) {
                BotonPildora("Post", onClick = { partes = partes + Parte() }, icono = { Icon(Icons.Outlined.Add, null, Modifier.size(16.dp), tint = c.texto) })
            }
            Row(Modifier.padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotonPildora("Copiar post 1", Modifier.weight(1f), onClick = { acciones.copiar(partes.firstOrNull()?.texto.orEmpty()) },
                    icono = { Icon(Icons.Outlined.ContentCopy, null, Modifier.size(16.dp), tint = c.texto) })
                BotonPildora("Publicada", Modifier.weight(1f), principal = true, onClick = { pedirEnlace = true },
                    icono = { Icon(Icons.Outlined.Check, null, Modifier.size(16.dp), tint = c.sobreTinta) })
            }
            Text("Tinta no publica por ti: copia el texto, publícalo en la red y marca aquí que salió.", Modifier.padding(bottom = 24.dp), style = Estilos.uiPeque, color = c.tenue)
        }
    }

    if (elegirFecha) {
        val inicial = fecha(p.fecha)?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        val estado = rememberDatePickerState(initialSelectedDateMillis = inicial)
        DatePickerDialog(
            onDismissRequest = { elegirFecha = false },
            confirmButton = {
                TextButton({
                    estado.selectedDateMillis?.let { ms -> acciones.cambiar(mapOf("fecha" to Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toString())) }
                    elegirFecha = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton({ elegirFecha = false }) { Text("Cancelar") } },
        ) { DatePicker(estado) }
    }
    if (elegirHora) {
        val (h, m) = (p.hora ?: "09:00").split(":").map { it.toIntOrNull() ?: 0 }
        val estado = rememberTimePickerState(h, m, is24Hour = true)
        AlertDialog(
            onDismissRequest = { elegirHora = false },
            confirmButton = { TextButton({ acciones.cambiar(mapOf("hora" to "%02d:%02d".format(estado.hour, estado.minute))); elegirHora = false }) { Text("Aceptar") } },
            dismissButton = { TextButton({ elegirHora = false }) { Text("Cancelar") } },
            text = { TimePicker(estado) },
        )
    }
    if (pedirEnlace) {
        var enlace by remember { mutableStateOf(p.enlace.orEmpty()) }
        AlertDialog(
            onDismissRequest = { pedirEnlace = false },
            title = { Text("Marcar como publicada") },
            text = {
                Column {
                    Text("Pega el enlace del post para tenerlo a mano. Puedes dejarlo vacío.", style = Estilos.uiPeque, color = c.secundario)
                    BasicTextField(
                        enlace, { enlace = it.trim() },
                        Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(8.dp)).background(c.gris).padding(10.dp),
                        textStyle = Estilos.ui.copy(color = c.texto), singleLine = true, cursorBrush = SolidColor(c.texto),
                    )
                }
            },
            confirmButton = { TextButton({ acciones.publicada(enlace); pedirEnlace = false }) { Text("Publicada") } },
            dismissButton = { TextButton({ pedirEnlace = false }) { Text("Cancelar") } },
        )
    }
    if (confirmarBorrar) {
        AlertDialog(
            onDismissRequest = { confirmarBorrar = false },
            title = { Text("¿Borrar esta publicación?") },
            text = { Text("Sale del calendario en el móvil y en la web.") },
            confirmButton = { TextButton({ confirmarBorrar = false; acciones.borrar() }) { Text("Borrar") } },
            dismissButton = { TextButton({ confirmarBorrar = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun Campo(etiqueta: String, contenido: @Composable () -> Unit) {
    val c = Tinta.colores
    Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(etiqueta, Modifier.width(74.dp), style = Estilos.uiPeque.copy(fontSize = 13.sp), color = c.tenue)
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) { contenido() }
    }
}

@Composable
private fun EditorParte(
    parte: Parte, i: Int, total: Int, limite: Int?, varias: Boolean, imagen: (String) -> Any?, medios: List<MedioE>,
    onTexto: (String) -> Unit, onCopiar: () -> Unit, onPartir: () -> Unit, onQuitar: () -> Unit,
    onSubir: () -> Unit, onBajar: () -> Unit, onImagen: () -> Unit, onQuitarMedio: (String) -> Unit,
) {
    val c = Tinta.colores
    val n = longitud(parte.texto)
    val pasa = limite != null && n > limite
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.hoja)
            .border(1.dp, if (pasa) c.texto else c.linea, RoundedCornerShape(14.dp)).padding(12.dp),
    ) {
        BasicTextField(
            parte.texto, onTexto, Modifier.fillMaxWidth(),
            textStyle = Estilos.ui.copy(color = c.texto, lineHeight = 21.sp), cursorBrush = SolidColor(c.texto),
            decorationBox = { campo -> if (parte.texto.isEmpty()) Text("Escribe el post…", style = Estilos.ui, color = c.tenue); campo() },
        )
        if (parte.medios.isNotEmpty()) Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (m in parte.medios) Box(Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(c.gris).clickable { onQuitarMedio(m) }) {
                val medio = medios.firstOrNull { it.id == m }
                if (medio != null && esImagen(medio.tipo)) {
                    imagen(m)?.let { AsyncImage(it, null, Modifier.fillMaxSize()) }
                } else {
                    Column(Modifier.fillMaxSize().padding(horizontal = 3.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(if (medio?.tipo?.startsWith("audio/") == true) Icons.Outlined.MusicNote else Icons.AutoMirrored.Outlined.InsertDriveFile, null, Modifier.size(16.dp), tint = c.secundario)
                        Text(medio?.nombre ?: "Medio", style = Estilos.uiPeque.copy(fontSize = 9.sp, lineHeight = 11.sp), color = c.secundario, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                }
                Icon(Icons.Outlined.Close, "Quitar", Modifier.align(Alignment.TopEnd).size(14.dp), tint = c.texto)
            }
        }
        if (limite != null) {
            Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(c.grisMedio)) {
                Box(Modifier.fillMaxWidth((n.toFloat() / limite).coerceIn(0f, 1f)).height(3.dp).background(if (pasa) c.texto else c.secundario))
            }
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (varias) "${i + 1} / $total" + if (pasa) " · se pasa" else "" else if (pasa) "se pasa" else "", style = Estilos.uiPeque, color = c.tenue)
            Spacer(Modifier.weight(1f))
            Accion(Icons.Outlined.Image, "Imagen", onImagen)
            Accion(Icons.Outlined.ContentCopy, "Copiar", onCopiar)
            if (varias && total > 1) {
                Accion(Icons.Outlined.KeyboardArrowUp, "Subir", onSubir)
                Accion(Icons.Outlined.KeyboardArrowDown, "Bajar", onBajar)
                Accion(Icons.Outlined.Close, "Quitar", onQuitar)
            }
            if (limite != null) Text("$n / $limite", style = Estilos.uiPeque.copy(fontFamily = Mono, fontWeight = if (pasa) FontWeight.SemiBold else FontWeight.Normal), color = if (pasa) c.texto else c.tenue)
        }
        if (pasa && varias) Text("Partir en dos", Modifier.padding(top = 8.dp).clickable(onClick = onPartir), style = Estilos.ui.copy(fontWeight = FontWeight.Medium), color = c.texto)
    }
}

@Composable
private fun Accion(icono: androidx.compose.ui.graphics.vector.ImageVector, d: String, onClick: () -> Unit) {
    Icon(icono, d, Modifier.size(20.dp).clickable(onClick = onClick), tint = Tinta.colores.secundario)
}

/** Ventana para crear una publicación: canal, formato y día. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogoNuevaPublicacion(canales: List<CanalE>, hoy: LocalDate, onCrear: (canal: String, formato: String, fecha: String) -> Unit, onCerrar: () -> Unit) {
    val c = Tinta.colores
    val activos = canales.filter { it.activo == 1 }
    var canal by remember { mutableStateOf(activos.firstOrNull()) }
    var formato by remember(canal) { mutableStateOf(canal?.formatos?.firstOrNull()) }
    var dia by remember { mutableStateOf(hoy) }
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Nueva publicación") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (k in activos) Chip(k.nombre, lleno = k.id == canal?.id, onClick = { canal = k }, icono = { IconoRed(k.icono, tam = 13.dp, color = if (k.id == canal?.id) c.sobreTinta else c.texto) })
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (f in canal?.formatos.orEmpty()) Chip(f.nombre, lleno = f.id == formato?.id, onClick = { formato = f })
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("‹", Modifier.clickable { dia = dia.minusDays(1) }.padding(8.dp), style = Estilos.ui.copy(fontSize = 20.sp), color = c.texto)
                    Text(fechaCorta(dia), style = Estilos.ui, color = c.texto)
                    Text("›", Modifier.clickable { dia = dia.plusDays(1) }.padding(8.dp), style = Estilos.ui.copy(fontSize = 20.sp), color = c.texto)
                }
            }
        },
        confirmButton = {
            TextButton({ val k = canal ?: return@TextButton; val f = formato ?: return@TextButton; onCrear(k.id, f.id, dia.toString()) }, enabled = canal != null && formato != null) { Text("Crear") }
        },
        dismissButton = { TextButton(onCerrar) { Text("Cancelar") } },
    )
}
