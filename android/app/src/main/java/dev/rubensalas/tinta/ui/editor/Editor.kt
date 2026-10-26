package dev.rubensalas.tinta.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FormatStrikethrough
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rubensalas.tinta.data.BloqueVista
import dev.rubensalas.tinta.data.Datos
import dev.rubensalas.tinta.data.api.EntradaHistorial
import dev.rubensalas.tinta.data.texto.Critic
import dev.rubensalas.tinta.data.texto.Edicion
import dev.rubensalas.tinta.ui.comun.Chip
import dev.rubensalas.tinta.ui.tema.Estilos
import dev.rubensalas.tinta.ui.tema.NOMBRE_ESTADO
import dev.rubensalas.tinta.ui.tema.Tinta
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Lo que el editor pide al resto de la app. */
interface AccionesEditor {
    suspend fun guardar(varianteId: String, markup: String)
    suspend fun nuevoBloque(vsId: String, despuesDe: String?, tipo: String = "parrafo", contenido: String = ""): String
    suspend fun borrarBloque(id: String)
    suspend fun fork(bloqueId: String, contenido: String): String
    suspend fun activar(bloqueId: String, varianteId: String)
    suspend fun descartarVariante(varianteId: String)
    suspend fun cambiarTitulo(idiomaId: String, titulo: String)
    suspend fun cambiarEstado(articuloId: String, estado: String)
    suspend fun historial(varianteId: String): List<EntradaHistorial>
    suspend fun restaurar(varianteId: String, entrada: Long)
    /** Abre la galería; [onElegida] recibe el id del medio ya guardado en el móvil. */
    fun elegirImagen(onElegida: (String) -> Unit)
}

private val ESTADOS = listOf("idea", "borrador", "revision", "listo", "publicado")

@Composable
fun PantallaEditor(
    datos: Datos,
    articuloId: String,
    sinRed: Boolean,
    acciones: AccionesEditor,
    imagen: (String) -> Any?,
    onVolver: () -> Unit,
    onConflicto: (String) -> Unit,
    focoInicial: String? = null,
) {
    val c = Tinta.colores
    val scope = rememberCoroutineScope()
    val a = datos.articulo(articuloId)
    var idiomaElegido by rememberSaveable { mutableStateOf<String?>(null) }
    val idiomas = datos.idiomasDe(articuloId)
    val idioma = idiomas.firstOrNull { it.idioma == idiomaElegido } ?: datos.idiomaPrincipal(articuloId)
    if (a == null || idioma == null) {
        Box(Modifier.fillMaxSize().background(c.papel), contentAlignment = Alignment.Center) { Text("Este artículo ya no existe.", color = c.secundario) }
        return
    }
    val doc = idioma.ramaPrincipalId?.let { datos.documento(it) }.orEmpty()
    val rama = datos.rama(idioma.ramaPrincipalId)

    var foco by rememberSaveable { mutableStateOf(focoInicial) }
    var hoja by remember { mutableStateOf<Hoja?>(null) }
    var menu by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }

    val bloqueFoco: BloqueVista? = foco?.let { id -> doc.flatMap { it.bloques }.firstOrNull { it.bloque.id == id } }
    val vsFoco = foco?.let { id -> doc.firstOrNull { s -> s.bloques.any { it.bloque.id == id } }?.activa }
    val activaFoco = bloqueFoco?.activa

    // Estado del campo del bloque con foco. Se rehace al cambiar de bloque o de
    // variante, y cuando llega otro texto de fuera (una sincronización) sin que
    // haya nada escrito pendiente de guardar.
    var edicion by remember { mutableStateOf(Edicion.desde("")) }
    var valor by remember { mutableStateOf(TextFieldValue("")) }
    var guardado by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(activaFoco?.id, activaFoco?.contenido) {
        val cont = activaFoco?.contenido ?: return@LaunchedEffect
        if (guardado == null || cont != guardado && edicion.aMarkup() == guardado) {
            edicion = Edicion.desde(cont)
            valor = TextFieldValue(edicion.texto, TextRange(edicion.texto.length))
            guardado = cont
        }
    }
    LaunchedEffect(edicion) {
        val v = activaFoco ?: return@LaunchedEffect
        val m = edicion.aMarkup()
        if (m == guardado) return@LaunchedEffect
        delay(700)
        acciones.guardar(v.id, m)
        guardado = m
    }
    val pedirFoco = remember { FocusRequester() }

    fun enfocar(id: String?) {
        val v = activaFoco
        val m = edicion.aMarkup()
        if (v != null && m != guardado) scope.launch { acciones.guardar(v.id, m) }
        guardado = null
        foco = id
    }

    fun cambiarVariante(paso: Int) {
        val b = bloqueFoco ?: return
        val i = b.variantes.indexOfFirst { it.id == b.activa?.id }
        val otra = b.variantes.getOrNull(i + paso) ?: return
        enfocar(b.bloque.id)
        scope.launch { acciones.activar(b.bloque.id, otra.id) }
    }

    fun nuevoMarkup(m: String) {
        edicion = Edicion.desde(m)
        valor = TextFieldValue(edicion.texto, TextRange(valor.selection.start.coerceAtMost(edicion.texto.length)))
    }

    Box(Modifier.fillMaxSize().background(c.papel)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver", Modifier.size(38.dp).clip(RoundedCornerShape(99.dp)).clickable { enfocar(null); onVolver() }.padding(9.dp), tint = c.texto)
                Spacer(Modifier.weight(1f))
                Chip(rama?.nombre ?: "principal")
                Chip(idioma.idioma.uppercase(), onClick = if (idiomas.size > 1) { { enfocar(null); idiomaElegido = idiomas.first { it.id != idioma.id }.idioma } } else null)
                Box {
                    Icon(Icons.Outlined.MoreHoriz, "Más", Modifier.size(38.dp).clip(RoundedCornerShape(99.dp)).clickable { menu = true }.padding(9.dp), tint = c.texto)
                    DropdownMenu(menu, { menu = false }) {
                        for (e in ESTADOS) DropdownMenuItem(
                            { Text((if (e == a.estado) "✓ " else "") + NOMBRE_ESTADO[e]) },
                            { menu = false; scope.launch { acciones.cambiarEstado(a.id, e) } },
                        )
                        if (bloqueFoco != null) {
                            HorizontalDivider()
                            DropdownMenuItem({ Text("Borrar este bloque") }, {
                                menu = false
                                val id = bloqueFoco.bloque.id
                                foco = null
                                scope.launch { acciones.borrarBloque(id) }
                            })
                        }
                    }
                }
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            ) {
                val serie = datos.serie(a.serieId)?.let { "${it.nombre}${a.serieParte?.let { p -> " · parte $p" } ?: ""} · " }.orEmpty()
                Text("$serie${NOMBRE_ESTADO[a.estado]}", Modifier.padding(top = 4.dp, bottom = 6.dp), style = Estilos.uiPeque, color = c.tenue)
                Titulo(idioma.titulo) { t -> scope.launch { acciones.cambiarTitulo(idioma.id, t) } }
                for (s in doc) {
                    val titulo = s.activa?.titulo.orEmpty()
                    if (titulo.isNotBlank()) Text(titulo, Modifier.padding(top = 18.dp, bottom = 8.dp), style = Estilos.subtitulo, color = c.texto)
                    else Spacer(Modifier.height(8.dp))
                    for (b in s.bloques) {
                        if (b.bloque.id != foco || b.activa == null) {
                            BloqueLectura(b, imagen) { enfocar(b.bloque.id) }
                            Spacer(Modifier.height(8.dp))
                            continue
                        }
                        Column(
                            Modifier.padding(vertical = 4.dp).fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp)).background(c.hoja)
                                .border(1.5.dp, c.texto, RoundedCornerShape(12.dp))
                                .pointerInput(b.bloque.id, b.variantes.size) {
                                    var total = 0f
                                    detectHorizontalDragGestures(
                                        onDragStart = { total = 0f },
                                        onDragEnd = { if (total > 140) cambiarVariante(-1) else if (total < -140) cambiarVariante(1) },
                                    ) { _, d -> total += d }
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            when (b.bloque.tipo) {
                                "imagen" -> {
                                    VistaImagen(edicion.aMarkup(), imagen)
                                    TextoAlternativo(edicion.aMarkup()) { nuevoMarkup(it) }
                                }
                                else -> CampoBloque(b.bloque.tipo, valor, edicion, pedirFoco) { nuevo ->
                                    val e = edicion.aplicar(nuevo.text)
                                    if (e == null) {
                                        aviso = "Para quitar una marca usa sus botones ✓ o ✕"
                                    } else {
                                        edicion = e
                                        valor = nuevo
                                    }
                                }
                            }
                            val i = b.variantes.indexOfFirst { it.id == b.activa.id }.coerceAtLeast(0)
                            SelectorVariante(i, b.variantes.size, { cambiarVariante(-1) }, { cambiarVariante(1) }, { hoja = Hoja.Variantes })
                            Propuestas(edicion) { k, d -> Critic.resolver(edicion.aMarkup(), k, d)?.let(::nuevoMarkup) }
                            Notas(edicion.aMarkup()) { k -> Critic.resolver(edicion.aMarkup(), k, Critic.Decision.Aceptar)?.let(::nuevoMarkup) }
                            if (b.conflicto != null) AvisoConflicto { enfocar(null); onConflicto(b.bloque.id) }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
                if (doc.isNotEmpty() && foco == null) {
                    Row(
                        Modifier.padding(top = 12.dp).clickable {
                            val ultimo = doc.last()
                            val vs = ultimo.activa ?: return@clickable
                            scope.launch { enfocar(acciones.nuevoBloque(vs.id, ultimo.bloques.lastOrNull()?.bloque?.id)) }
                        },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Outlined.Add, null, Modifier.size(18.dp), tint = c.tenue)
                        Text("Añadir un párrafo", style = Estilos.cuerpo, color = c.tenue)
                    }
                }
            }
            aviso?.let {
                LaunchedEffect(it) { delay(2500); aviso = null }
                Text(it, Modifier.fillMaxWidth().background(c.tinta).padding(12.dp), color = c.sobreTinta, style = Estilos.uiPeque)
            }
            if (bloqueFoco != null && activaFoco != null && vsFoco != null) {
                Herramientas(
                    sinRed = sinRed,
                    onTachar = {
                        val e = edicion.tachar(valor.selection.start, valor.selection.end)
                        if (e == null) {
                            aviso = "Selecciona el texto que quieres tachar"
                        } else {
                            edicion = e
                            valor = TextFieldValue(e.texto, TextRange(valor.selection.start.coerceAtMost(e.texto.length)))
                        }
                    },
                    onNota = { hoja = Hoja.Nota(valor.selection.start, valor.selection.end) },
                    onFork = {
                        scope.launch {
                            val m = edicion.aMarkup()
                            acciones.guardar(activaFoco.id, m)
                            guardado = null
                            acciones.fork(bloqueFoco.bloque.id, m)
                            aviso = "Variante nueva: edítala sin perder la anterior"
                        }
                    },
                    onHistorial = { hoja = Hoja.Historial },
                    onMedio = {
                        acciones.elegirImagen { id ->
                            scope.launch { enfocar(acciones.nuevoBloque(vsFoco.id, bloqueFoco.bloque.id, "imagen", "![](medio:$id)")) }
                        }
                    },
                    onBloque = { scope.launch { enfocar(acciones.nuevoBloque(vsFoco.id, bloqueFoco.bloque.id)) } },
                )
            }
        }
        when (val h = hoja) {
            Hoja.Variantes -> if (bloqueFoco != null) HojaVariantes(
                bloqueFoco,
                onActivar = { v -> scope.launch { acciones.activar(bloqueFoco.bloque.id, v.id) }; hoja = null },
                onDescartar = { v -> scope.launch { acciones.descartarVariante(v.id) } },
                onNueva = { scope.launch { acciones.fork(bloqueFoco.bloque.id, edicion.aMarkup()) }; hoja = null },
                onCerrar = { hoja = null },
            )
            Hoja.Historial -> if (activaFoco != null) HojaHistorial(
                cargar = { acciones.historial(activaFoco.id) },
                onRestaurar = { e -> scope.launch { acciones.restaurar(activaFoco.id, e.id) }; guardado = null; hoja = null },
                onCerrar = { hoja = null },
            )
            is Hoja.Nota -> DialogoNota(
                onGuardar = { t ->
                    val e = edicion.anotar(h.desde, h.hasta, t)
                    if (e == null) aviso = "La nota no puede ir encima de otra marca" else { edicion = e; valor = TextFieldValue(e.texto, TextRange(e.texto.length)) }
                    hoja = null
                },
                onCerrar = { hoja = null },
            )
            null -> Unit
        }
    }
}

sealed interface Hoja {
    data object Variantes : Hoja
    data object Historial : Hoja
    data class Nota(val desde: Int, val hasta: Int) : Hoja
}

@Composable
private fun Titulo(titulo: String, onCambio: (String) -> Unit) {
    val c = Tinta.colores
    var texto by remember(titulo) { mutableStateOf(titulo) }
    LaunchedEffect(texto) {
        if (texto == titulo) return@LaunchedEffect
        delay(700)
        onCambio(texto)
    }
    BasicTextField(
        texto, { texto = it.replace("\n", " ") },
        Modifier.fillMaxWidth().padding(bottom = 10.dp),
        textStyle = Estilos.tituloArticulo.copy(color = c.texto),
        cursorBrush = SolidColor(c.texto),
    )
}

@Composable
private fun TextoAlternativo(contenido: String, onCambio: (String) -> Unit) {
    val c = Tinta.colores
    val m = Regex("""!\[(.*?)]\((.*?)\)""").find(contenido) ?: return
    var alt by remember(contenido) { mutableStateOf(m.groupValues[1]) }
    Column(Modifier.padding(top = 8.dp)) {
        Text("Texto alternativo (describe la imagen para quien no la ve)", style = Estilos.uiPeque, color = c.secundario)
        BasicTextField(
            alt, { alt = it.replace("]", ""); onCambio("![${alt}](${m.groupValues[2]})") },
            Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(8.dp)).background(c.gris).padding(10.dp),
            textStyle = Estilos.ui.copy(color = c.texto), cursorBrush = SolidColor(c.texto),
        )
    }
}

/** La barra de herramientas sobre el teclado. Historial solo con red: vive en el servicio. */
@Composable
fun Herramientas(
    sinRed: Boolean,
    onTachar: () -> Unit,
    onNota: () -> Unit,
    onFork: () -> Unit,
    onHistorial: () -> Unit,
    onMedio: () -> Unit,
    onBloque: () -> Unit,
    seleccionada: String? = null,
) {
    val c = Tinta.colores
    Column(Modifier.fillMaxWidth().background(c.hoja).imePadding().navigationBarsPadding()) {
        HorizontalDivider(color = c.linea)
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Herramienta(Icons.Outlined.FormatStrikethrough, "Tachar", seleccionada == "Tachar", onTachar)
            Herramienta(Icons.Outlined.ChatBubbleOutline, "Nota", seleccionada == "Nota", onNota)
            Herramienta(Icons.Outlined.ContentCopy, "Fork", seleccionada == "Fork", onFork)
            if (!sinRed) Herramienta(Icons.Outlined.History, "Historial", seleccionada == "Historial", onHistorial)
            Herramienta(Icons.Outlined.Image, "Medio", seleccionada == "Medio", onMedio)
            Herramienta(Icons.Outlined.Add, "Bloque", seleccionada == "Bloque", onBloque)
        }
    }
}

@Composable
private fun Herramienta(icono: ImageVector, texto: String, sel: Boolean, onClick: () -> Unit) {
    val c = Tinta.colores
    Column(
        Modifier.widthIn(min = 52.dp).clip(RoundedCornerShape(10.dp)).background(if (sel) c.grisMedio else c.hoja).clickable(onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(icono, null, Modifier.size(19.dp), tint = if (sel) c.texto else c.secundario)
        Text(texto, fontSize = 10.5.sp, fontFamily = Estilos.ui.fontFamily, color = if (sel) c.texto else c.secundario)
    }
}
