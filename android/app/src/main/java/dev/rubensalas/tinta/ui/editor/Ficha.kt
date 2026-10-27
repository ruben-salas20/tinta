package dev.rubensalas.tinta.ui.editor

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.rubensalas.tinta.data.Datos
import dev.rubensalas.tinta.data.local.ArticuloE

/** Etiquetas y serie del artículo. */
@Composable
fun HojaFicha(datos: Datos, articulo: ArticuloE, acciones: AccionesEditor, onCerrar: () -> Unit) {
    HojaInferior(onCerrar) { Text("Etiquetas y serie: pendiente") }
}
