package dev.rubensalas.tinta.ui.hoy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rubensalas.tinta.data.Datos
import dev.rubensalas.tinta.data.resumen.Resumen
import dev.rubensalas.tinta.ui.comun.Banda
import dev.rubensalas.tinta.ui.comun.BotonCrear
import dev.rubensalas.tinta.ui.comun.Cabecera
import dev.rubensalas.tinta.ui.comun.FilaPublicacion
import dev.rubensalas.tinta.ui.comun.Grupo
import dev.rubensalas.tinta.ui.comun.Tarjeta
import dev.rubensalas.tinta.ui.comun.diaCorto
import dev.rubensalas.tinta.ui.comun.fecha
import dev.rubensalas.tinta.ui.comun.fechaCorta
import dev.rubensalas.tinta.ui.comun.fechaLarga
import dev.rubensalas.tinta.ui.tema.Estilos
import dev.rubensalas.tinta.ui.tema.NOMBRE_ESTADO
import dev.rubensalas.tinta.ui.tema.Tinta
import java.time.LocalDate

/** Aviso de cambios sin enviar. Sin red dice que esperan; con red, que se están enviando. */
@Composable
fun BandaPendientes(pendientes: Int, sinRed: Boolean) {
    if (!sinRed && pendientes == 0) return
    val c = Tinta.colores
    Banda(Modifier.padding(bottom = 14.dp)) {
        Icon(if (sinRed) Icons.Outlined.CloudOff else Icons.Outlined.Sync, null, Modifier.size(14.dp), tint = c.secundario)
        Text(
            buildAnnotatedString {
                if (sinRed) {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = c.texto)) { append("Sin conexión. ") }
                    append(if (pendientes == 0) "Todo está guardado en el móvil." else "${Resumen.plural(pendientes, "cambio esperando", "cambios esperando")}; se envían solos.")
                } else {
                    append("Enviando ${Resumen.plural(pendientes, "cambio", "cambios")}…")
                }
            },
            style = Estilos.uiPeque, color = c.secundario,
        )
    }
}

@Composable
fun PantallaHoy(
    datos: Datos,
    hoy: LocalDate,
    sinRed: Boolean,
    aviso: @Composable () -> Unit = {},
    onArticulo: (String) -> Unit,
    onPublicacion: (String) -> Unit,
    onNuevoArticulo: () -> Unit,
) {
    val c = Tinta.colores
    val r = Resumen.calcular(datos.publicaciones, hoy)
    val semana = datos.publicaciones.count { p ->
        p.estado != "publicada" && p.fecha?.let(::fecha)?.let { !it.isBefore(hoy) && it.isBefore(hoy.plusDays(7)) } == true
    }
    val proximos = (0L until 7L).map { hoy.plusDays(it) }.mapNotNull { d ->
        val ps = datos.publicaciones.filter { it.fecha == d.toString() }.sortedWith(Datos.ORDEN)
        if (ps.isEmpty()) null else d to ps
    }
    val seguir = datos.articulos.filter { it.estado in setOf("borrador", "revision", "idea") }.maxByOrNull { it.cambiado }

    Box(Modifier.fillMaxSize().background(c.papel)) {
        Column(Modifier.fillMaxSize()) {
            Cabecera("Hoy", conLogo = true)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 96.dp)) {
                aviso()
                BandaPendientes(datos.pendientes, sinRed)
                Tarjeta {
                    Text(fechaLarga(hoy), style = Estilos.ui.copy(fontSize = 13.sp), color = c.secundario)
                    Spacer(Modifier.height(10.dp))
                    Row {
                        Cifra(r.hoy.size, "para hoy", Modifier.weight(1f))
                        Cifra(r.atrasadas.size, if (r.atrasadas.size == 1) "atrasada" else "atrasadas", Modifier.weight(1f))
                        Cifra(semana, "esta semana", Modifier.weight(1f))
                    }
                }
                if (r.atrasadas.isNotEmpty()) {
                    Grupo("Atrasadas")
                    Tarjeta {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (p in r.atrasadas) FilaPublicacion(datos.canal(p.canalId)?.icono ?: "", p.titulo, p.estado, p.fecha?.let(::fecha)?.let(::fechaCorta)) { onPublicacion(p.id) }
                        }
                    }
                }
                Grupo("Próximos días")
                Tarjeta {
                    if (proximos.isEmpty()) Text("Nada planeado esta semana.", style = Estilos.uiPeque, color = c.secundario)
                    proximos.forEachIndexed { i, (d, ps) ->
                        if (i > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp), color = c.linea)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.width(38.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${d.dayOfMonth}", style = Estilos.ui.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold), color = c.texto)
                                Text(diaCorto(d).uppercase(), style = Estilos.uiPeque.copy(fontSize = 11.sp), color = c.tenue)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (p in ps) FilaPublicacion(datos.canal(p.canalId)?.icono ?: "", p.titulo, p.estado, p.hora) { onPublicacion(p.id) }
                            }
                        }
                    }
                }
                if (seguir != null) {
                    Grupo("Seguir escribiendo")
                    val s = datos.senales(seguir.id)
                    Tarjeta(onClick = { onArticulo(seguir.id) }) {
                        val serie = datos.serie(seguir.serieId)?.let { " · ${it.nombre}" + (seguir.serieParte?.let { p -> " · $p" } ?: "") }.orEmpty()
                        Text("${NOMBRE_ESTADO[seguir.estado]}$serie", style = Estilos.uiPeque, color = c.secundario)
                        Text(datos.titulo(seguir.id), Modifier.padding(vertical = 4.dp), style = Estilos.tarjetaTitulo, color = c.texto)
                        Text(
                            "${s.palabras} palabras" + (if (s.propuestas > 0) " · ${Resumen.plural(s.propuestas, "propuesta del agente", "propuestas del agente")}" else ""),
                            style = Estilos.uiPeque, color = c.secundario,
                        )
                    }
                }
            }
        }
        BotonCrear(Modifier.align(Alignment.BottomEnd).padding(18.dp), onClick = onNuevoArticulo)
    }
}

@Composable
private fun Cifra(n: Int, texto: String, modifier: Modifier) {
    val c = Tinta.colores
    Column(modifier) {
        Text("$n", style = Estilos.ui.copy(fontSize = 26.sp, fontWeight = FontWeight.SemiBold, lineHeight = 30.sp), color = c.texto)
        Text(texto, style = Estilos.uiPeque, color = c.secundario)
    }
}
