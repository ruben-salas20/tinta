package dev.rubensalas.tinta.ui.editor

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.rubensalas.tinta.data.Datos
import dev.rubensalas.tinta.data.local.IdiomaE

/**
 * Las ramas del idioma que se lee: cambiar la que se ve, copiar una nueva,
 * renombrar, hacer principal y borrar. [ramaVista] es la que se está leyendo.
 */
@Composable
fun HojaRamas(
    datos: Datos,
    idioma: IdiomaE,
    ramaVista: String?,
    acciones: AccionesEditor,
    onElegir: (ramaId: String) -> Unit,
    onCerrar: () -> Unit,
) {
    HojaInferior(onCerrar) { Text("Ramas: pendiente") }
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
    HojaInferior(onCerrar) { Text("Idiomas: pendiente") }
}
