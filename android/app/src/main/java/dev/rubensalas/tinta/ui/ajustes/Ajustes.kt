package dev.rubensalas.tinta.ui.ajustes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rubensalas.tinta.data.Ajustes
import dev.rubensalas.tinta.data.local.AvisoE
import dev.rubensalas.tinta.ui.comun.Cabecera
import dev.rubensalas.tinta.ui.comun.Grupo
import dev.rubensalas.tinta.ui.comun.Logo
import dev.rubensalas.tinta.ui.comun.Tarjeta
import dev.rubensalas.tinta.ui.editor.BotonPildora
import dev.rubensalas.tinta.ui.tema.Estilos
import dev.rubensalas.tinta.ui.tema.Tinta
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun CampoTexto(etiqueta: String, valor: String, onCambio: (String) -> Unit, secreto: Boolean = false) {
    val c = Tinta.colores
    Column {
        Text(etiqueta, style = Estilos.uiPeque, color = c.secundario)
        BasicTextField(
            valor, { onCambio(it.trim()) },
            Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(10.dp)).background(c.gris).padding(12.dp),
            textStyle = Estilos.ui.copy(color = c.texto), singleLine = true, cursorBrush = SolidColor(c.texto),
            visualTransformation = if (secreto) PasswordVisualTransformation() else VisualTransformation.None,
        )
    }
}

/** La primera vez: a qué servicio se conecta y con qué clave. */
@Composable
fun PantallaInicio(urlInicial: String, onEntrar: (url: String, clave: String) -> Unit, error: String?, probando: Boolean) {
    val c = Tinta.colores
    var url by remember { mutableStateOf(urlInicial) }
    var clave by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(c.papel).statusBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Spacer(Modifier.height(48.dp))
        Logo(tam = 48.dp)
        Text("Tinta", Modifier.padding(top = 12.dp), style = Estilos.pantalla.copy(fontSize = 34.sp), color = c.texto)
        Text("Calendario de contenido y escritura ordenada.", Modifier.padding(top = 4.dp, bottom = 32.dp), style = Estilos.ui, color = c.secundario)
        Tarjeta {
            CampoTexto("Dirección del servicio", url, { url = it })
            Spacer(Modifier.height(12.dp))
            CampoTexto("Clave de la app (TINTA_API_KEY)", clave, { clave = it }, secreto = true)
            Text("Está en ~/servicios/.env de la torre. Pégala tal cual.", Modifier.padding(top = 6.dp), style = Estilos.uiPeque, color = c.tenue)
            error?.let { Text(it, Modifier.padding(top = 10.dp), style = Estilos.uiPeque, color = c.texto) }
            Spacer(Modifier.height(14.dp))
            BotonPildora(if (probando) "Probando…" else "Probar y entrar", Modifier.fillMaxWidth(), principal = true, onClick = { if (!probando) onEntrar(url, clave) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaAjustes(
    ajustes: Ajustes,
    version: String,
    pendientes: Int,
    avisos: List<AvisoE>,
    estadoVersion: String?,
    onGuardarServidor: (String, String) -> Unit,
    onHora: (Int) -> Unit,
    onSincronizar: () -> Unit,
    onBuscarVersion: () -> Unit,
    onBorrarAvisos: () -> Unit,
) {
    val c = Tinta.colores
    var url by remember(ajustes.url) { mutableStateOf(ajustes.url) }
    var clave by remember(ajustes.clave) { mutableStateOf(ajustes.clave) }
    var elegirHora by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(c.papel)) {
        Cabecera("Ajustes")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Grupo("Servicio")
            Tarjeta {
                CampoTexto("Dirección", url, { url = it })
                Spacer(Modifier.height(10.dp))
                CampoTexto("Clave de la app", clave, { clave = it }, secreto = true)
                Spacer(Modifier.height(12.dp))
                BotonPildora("Guardar y probar", onClick = { onGuardarServidor(url, clave) })
            }
            Grupo("Sincronización")
            Tarjeta {
                val hora = ajustes.ultimaSync?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("d/M HH:mm"))
                Text(hora?.let { "Última: $it" } ?: "Aún no se ha sincronizado", style = Estilos.ui, color = c.texto)
                Text(if (pendientes == 0) "Nada pendiente de enviar" else "$pendientes cambios esperando", style = Estilos.uiPeque, color = c.secundario)
                Spacer(Modifier.height(10.dp))
                BotonPildora("Sincronizar ahora", onClick = onSincronizar)
                if (avisos.isNotEmpty()) {
                    HorizontalDivider(Modifier.padding(vertical = 10.dp), color = c.linea)
                    for (a in avisos.take(5)) Text(a.texto, Modifier.padding(bottom = 4.dp), style = Estilos.uiPeque, color = c.secundario)
                    Text("Borrar avisos", Modifier.clickable(onClick = onBorrarAvisos).padding(vertical = 4.dp), style = Estilos.uiPeque.copy(fontWeight = FontWeight.Medium), color = c.texto)
                }
            }
            Grupo("Resumen diario")
            Tarjeta(onClick = { elegirHora = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Hora del aviso", Modifier.weight(1f), style = Estilos.ui, color = c.texto)
                    Text("%02d:%02d".format(ajustes.horaResumen / 60, ajustes.horaResumen % 60), style = Estilos.ui, color = c.secundario)
                }
                Text("Lo que toca publicar hoy y lo atrasado. Llega aunque no haya red.", Modifier.padding(top = 4.dp), style = Estilos.uiPeque, color = c.tenue)
            }
            Grupo("Versión")
            Tarjeta {
                Text("Tinta $version", style = Estilos.ui, color = c.texto)
                estadoVersion?.let { Text(it, Modifier.padding(top = 2.dp), style = Estilos.uiPeque, color = c.secundario) }
                Spacer(Modifier.height(10.dp))
                BotonPildora("Buscar actualizaciones", onClick = onBuscarVersion)
            }
        }
    }
    if (elegirHora) {
        val estado = rememberTimePickerState(ajustes.horaResumen / 60, ajustes.horaResumen % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { elegirHora = false },
            confirmButton = { TextButton({ onHora(estado.hour * 60 + estado.minute); elegirHora = false }) { Text("Aceptar") } },
            dismissButton = { TextButton({ elegirHora = false }) { Text("Cancelar") } },
            text = { TimePicker(estado) },
        )
    }
}
