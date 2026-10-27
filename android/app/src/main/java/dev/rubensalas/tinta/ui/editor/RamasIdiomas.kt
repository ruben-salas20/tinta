package dev.rubensalas.tinta.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rubensalas.tinta.data.Datos
import dev.rubensalas.tinta.data.local.IdiomaE
import dev.rubensalas.tinta.data.local.RamaE
import dev.rubensalas.tinta.ui.tema.Estilos
import dev.rubensalas.tinta.ui.tema.Tinta
import kotlinx.coroutines.launch

private val NOMBRE_IDIOMA = mapOf("es" to "español", "en" to "inglés")

/** Las ramas del idioma que se lee: cambiar la que se ve, copiar una nueva, renombrar, hacer principal y borrar. */
@Composable
fun HojaRamas(
    datos: Datos,
    idioma: IdiomaE,
    ramaVista: String?,
    acciones: AccionesEditor,
    onElegir: (ramaId: String) -> Unit,
    onCerrar: () -> Unit,
) {
    val c = Tinta.colores
    val scope = rememberCoroutineScope()
    val ramas = datos.ramas.filter { it.idiomaId == idioma.id }
        .sortedWith(compareByDescending<RamaE> { it.id == idioma.ramaPrincipalId }.thenBy { it.creado })
    val desde = ramaVista ?: idioma.ramaPrincipalId
    var renombrar by remember { mutableStateOf<RamaE?>(null) }
    var borrar by remember { mutableStateOf<RamaE?>(null) }
    var nueva by remember { mutableStateOf(false) }

    HojaInferior(onCerrar) {
        TituloHoja("Ramas en ${NOMBRE_IDIOMA[idioma.idioma] ?: idioma.idioma}")
        Text(
            "Una rama es una copia del artículo para probar otra versión sin tocar la principal.",
            Modifier.padding(top = 4.dp, bottom = 14.dp), style = Estilos.uiPeque.copy(fontSize = 13.sp), color = c.secundario,
        )
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (r in ramas) FilaRama(
                r,
                esPrincipal = r.id == idioma.ramaPrincipalId,
                leyendo = r.id == ramaVista,
                onElegir = { onElegir(r.id); onCerrar() },
                onRenombrar = { renombrar = r },
                onHacerPrincipal = { scope.launch { acciones.hacerPrincipal(idioma.id, r.id) } },
                onBorrar = { borrar = r },
            )
        }
        if (desde != null) {
            Spacer(Modifier.height(14.dp))
            BotonPildora(
                "Nueva rama desde «${datos.rama(desde)?.nombre ?: "principal"}»", Modifier.fillMaxWidth(),
                onClick = { nueva = true }, icono = { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp), tint = c.texto) },
            )
        }
    }

    renombrar?.let { r ->
        DialogoNombre("Renombrar rama", r.nombre, "Guardar", onGuardar = { n ->
            renombrar = null
            scope.launch { acciones.renombrarRama(r.id, n) }
        }, onCerrar = { renombrar = null })
    }
    borrar?.let { r ->
        AlertDialog(
            onDismissRequest = { borrar = null },
            title = { Text("¿Borrar la rama «${r.nombre}»?") },
            text = { Text("Se pierde el texto de esta rama. La rama principal no cambia.", style = Estilos.ui, color = c.secundario) },
            confirmButton = { TextButton({ borrar = null; scope.launch { acciones.borrarRama(r.id) } }) { Text("Borrar") } },
            dismissButton = { TextButton({ borrar = null }) { Text("Cancelar") } },
        )
    }
    if (nueva && desde != null) {
        DialogoNombre("Nueva rama", "otra versión", "Crear", onGuardar = { n ->
            nueva = false
            scope.launch { onElegir(acciones.forkRama(desde, n)); onCerrar() }
        }, onCerrar = { nueva = false })
    }
}

/** Los idiomas del artículo: cambiar el que se lee, crear el que falta y borrar. */
@Composable
fun HojaIdiomas(
    datos: Datos,
    articuloId: String,
    idioma: IdiomaE,
    acciones: AccionesEditor,
    onElegir: (idioma: String) -> Unit,
    onCerrar: () -> Unit,
) {
    val c = Tinta.colores
    val scope = rememberCoroutineScope()
    val idiomas = datos.idiomasDe(articuloId)
    var borrar by remember { mutableStateOf<IdiomaE?>(null) }

    HojaInferior(onCerrar) {
        TituloHoja("Idiomas")
        Text(
            "Un artículo puede estar en español, en inglés o en los dos.",
            Modifier.padding(top = 4.dp, bottom = 14.dp), style = Estilos.uiPeque.copy(fontSize = 13.sp), color = c.secundario,
        )
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (code in listOf("es", "en")) {
                val existente = idiomas.firstOrNull { it.idioma == code }
                val nombre = NOMBRE_IDIOMA.getValue(code)
                if (existente != null) Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, if (existente.id == idioma.id) c.texto else c.linea, RoundedCornerShape(12.dp)).padding(12.dp),
                ) {
                    Row(Modifier.fillMaxWidth().clickable { onElegir(code); onCerrar() }, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(nombre.replaceFirstChar { it.uppercase() }, style = Estilos.ui.copy(fontWeight = FontWeight.Medium), color = c.texto)
                            Text(existente.titulo.ifBlank { code.uppercase() }, style = Estilos.uiPeque, color = c.tenue, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (existente.id == idioma.id) Marca("leyendo")
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Traducido con IA", Modifier.weight(1f), style = Estilos.ui, color = c.texto)
                        Switch(
                            checked = existente.traducidoIa == 1,
                            onCheckedChange = { si -> scope.launch { acciones.marcarTraducidoIa(existente.id, si) } },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = c.sobreTinta, checkedTrackColor = c.tinta, checkedBorderColor = c.tinta,
                                uncheckedThumbColor = c.tenue, uncheckedTrackColor = c.gris, uncheckedBorderColor = c.lineaMarcada,
                            ),
                        )
                    }
                    if (idiomas.size > 1) Text(
                        "Borrar el $nombre", Modifier.padding(top = 6.dp).clickable { borrar = existente }.padding(vertical = 4.dp),
                        style = Estilos.ui, color = c.secundario,
                    )
                } else {
                    val origen = idiomas.firstOrNull()
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, c.linea, RoundedCornerShape(12.dp)).padding(12.dp)) {
                        Text("Este artículo no tiene versión en $nombre.", style = Estilos.ui, color = c.secundario)
                        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (origen != null) BotonPildora(
                                "Crear copiando el ${NOMBRE_IDIOMA.getValue(origen.idioma)}", Modifier.fillMaxWidth(), principal = true,
                                onClick = { scope.launch { acciones.crearIdioma(articuloId, code, origen.idioma); onElegir(code); onCerrar() } },
                            )
                            BotonPildora(
                                "Crear vacío", Modifier.fillMaxWidth(),
                                onClick = { scope.launch { acciones.crearIdioma(articuloId, code, null); onElegir(code); onCerrar() } },
                            )
                        }
                    }
                }
            }
        }
    }

    borrar?.let { i ->
        AlertDialog(
            onDismissRequest = { borrar = null },
            title = { Text("¿Borrar el ${NOMBRE_IDIOMA.getValue(i.idioma)}?") },
            text = { Text("Se pierden su título, su texto y todas sus ramas. El otro idioma no cambia.", style = Estilos.ui, color = c.secundario) },
            confirmButton = {
                TextButton({
                    borrar = null
                    scope.launch {
                        acciones.borrarIdioma(i.id)
                        onElegir(idiomas.first { it.id != i.id }.idioma)
                        onCerrar()
                    }
                }) { Text("Borrar") }
            },
            dismissButton = { TextButton({ borrar = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun TituloHoja(texto: String) =
    Text(texto, style = Estilos.ui.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold), color = Tinta.colores.texto)

@Composable
private fun Marca(texto: String) {
    val c = Tinta.colores
    Text(
        texto,
        Modifier.border(1.dp, c.linea, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
        style = Estilos.uiPeque.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = c.secundario,
    )
}

@Composable
private fun FilaRama(
    rama: RamaE,
    esPrincipal: Boolean,
    leyendo: Boolean,
    onElegir: () -> Unit,
    onRenombrar: () -> Unit,
    onHacerPrincipal: () -> Unit,
    onBorrar: () -> Unit,
) {
    val c = Tinta.colores
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, if (leyendo) c.texto else c.linea, RoundedCornerShape(12.dp))
            .clickable(onClick = onElegir).padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(rama.nombre, style = Estilos.ui.copy(fontWeight = FontWeight.Medium), color = c.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (esPrincipal || leyendo) Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (esPrincipal) Marca("principal")
                if (leyendo) Marca("leyendo")
            }
        }
        Box {
            Icon(
                Icons.Outlined.MoreHoriz, "Acciones de ${rama.nombre}",
                Modifier.size(38.dp).clip(RoundedCornerShape(99.dp)).clickable { menu = true }.padding(9.dp), tint = c.texto,
            )
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem({ Text("Renombrar") }, { menu = false; onRenombrar() })
                if (!esPrincipal) DropdownMenuItem({ Text("Hacer principal") }, { menu = false; onHacerPrincipal() })
                if (!esPrincipal) DropdownMenuItem({ Text("Borrar") }, { menu = false; onBorrar() })
            }
        }
    }
}

@Composable
fun DialogoNombre(titulo: String, inicial: String, boton: String, onGuardar: (String) -> Unit, onCerrar: () -> Unit, vacioVale: Boolean = false) {
    val c = Tinta.colores
    var texto by remember { mutableStateOf(inicial) }
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text(titulo) },
        text = {
            BasicTextField(
                texto, { texto = it },
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(c.gris).padding(10.dp),
                textStyle = Estilos.ui.copy(color = c.texto), cursorBrush = SolidColor(c.texto), singleLine = true,
            )
        },
        confirmButton = { TextButton({ onGuardar(texto.trim()) }, enabled = vacioVale || texto.isNotBlank()) { Text(boton) } },
        dismissButton = { TextButton(onCerrar) { Text("Cancelar") } },
    )
}
