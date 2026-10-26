package dev.rubensalas.tinta.ui.articulos

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.rubensalas.tinta.ui.tema.Estilos
import dev.rubensalas.tinta.ui.tema.Tinta
import kotlinx.coroutines.withTimeoutOrNull

/** Lo que dura el aviso con «Deshacer» tras eliminar un artículo. */
const val MS_DESHACER = 8_000L

@Composable
fun DialogoEliminarArticulo(titulo: String, onEliminar: () -> Unit, onCancelar: () -> Unit) {
    val c = Tinta.colores
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("¿Eliminar «$titulo»?") },
        text = {
            Text(
                "Se elimina en todos sus idiomas y ramas. También se eliminan sus publicaciones del calendario que aún no salieron. Las ya publicadas se quedan.",
                style = Estilos.ui, color = c.secundario,
            )
        },
        confirmButton = { TextButton(onEliminar) { Text("Eliminar") } },
        dismissButton = { TextButton(onCancelar) { Text("Cancelar") } },
    )
}

/** El aviso de abajo, en tinta como los demás avisos de la app. */
@Composable
fun AvisoDeshacer(data: SnackbarData, modifier: Modifier = Modifier) {
    val c = Tinta.colores
    Snackbar(
        modifier,
        action = data.visuals.actionLabel?.let { etiqueta ->
            { TextButton({ data.performAction() }) { Text(etiqueta, style = Estilos.ui.copy(fontWeight = FontWeight.SemiBold), color = c.sobreTinta) } }
        },
        shape = RoundedCornerShape(12.dp),
        containerColor = c.tinta,
        contentColor = c.sobreTinta,
    ) { Text(data.visuals.message, style = Estilos.ui) }
}

/** Enseña «Artículo eliminado · Deshacer». Devuelve true si se pulsó Deshacer a tiempo. */
suspend fun SnackbarHostState.ofrecerDeshacer(): Boolean = withTimeoutOrNull(MS_DESHACER) {
    showSnackbar("Artículo eliminado", actionLabel = "Deshacer", duration = SnackbarDuration.Indefinite)
} == SnackbarResult.ActionPerformed
