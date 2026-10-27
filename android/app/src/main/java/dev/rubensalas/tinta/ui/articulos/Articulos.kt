package dev.rubensalas.tinta.ui.articulos

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.rubensalas.tinta.data.Datos
import dev.rubensalas.tinta.data.texto.Critic
import dev.rubensalas.tinta.ui.comun.BotonCrear
import dev.rubensalas.tinta.ui.comun.Cabecera
import dev.rubensalas.tinta.ui.comun.Chip
import dev.rubensalas.tinta.ui.comun.ChipEstado
import dev.rubensalas.tinta.ui.comun.IconoRed
import dev.rubensalas.tinta.ui.comun.Tarjeta
import dev.rubensalas.tinta.ui.comun.cuando
import dev.rubensalas.tinta.ui.tema.Estilos
import dev.rubensalas.tinta.ui.tema.Tinta
import java.time.LocalDate

private val FILTROS = listOf(null to "Todos", "idea" to "Ideas", "borrador" to "Borradores", "revision" to "Revisión", "listo" to "Listos", "publicado" to "Publicados")

@Composable
fun PantallaArticulos(datos: Datos, hoy: LocalDate, onArticulo: (String) -> Unit, onNuevo: () -> Unit, onEliminar: (String) -> Unit = {}) {
    val c = Tinta.colores
    var buscar by rememberSaveable { mutableStateOf("") }
    var filtro by rememberSaveable { mutableStateOf<String?>(null) }
    val lista = datos.articulos
        .filter { filtro == null || it.estado == filtro }
        .filter { a ->
            buscar.isBlank() || datos.idiomasDe(a.id).any { it.titulo.contains(buscar, true) || it.descripcion.contains(buscar, true) }
        }
        .sortedByDescending { it.cambiado }

    Box(Modifier.fillMaxSize().background(c.papel)) {
        Column(Modifier.fillMaxSize()) {
            Cabecera("Artículos")
            Column(Modifier.padding(horizontal = 16.dp)) {
                Row(
                    Modifier.fillMaxWidth().height(46.dp).clip(CircleShape).background(c.gris).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Outlined.Search, null, Modifier.size(20.dp), tint = c.tenue)
                    Box(Modifier.weight(1f)) {
                        if (buscar.isEmpty()) Text("Buscar en títulos y descripciones", style = Estilos.ui, color = c.tenue)
                        BasicTextField(buscar, { buscar = it }, singleLine = true, textStyle = Estilos.ui.copy(color = c.texto), cursorBrush = SolidColor(c.texto), modifier = Modifier.fillMaxWidth())
                    }
                }
                Row(Modifier.padding(vertical = 12.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for ((e, t) in FILTROS) Chip(t, lleno = filtro == e, onClick = { filtro = e })
                }
            }
            LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (lista.isEmpty()) item {
                    Text(if (datos.articulos.isEmpty()) "Aún no hay artículos. Pulsa + para empezar uno." else "Nada coincide.", Modifier.padding(16.dp), style = Estilos.ui, color = c.secundario)
                }
                items(lista, key = { it.id }) { a ->
                    val i = datos.idiomaPrincipal(a.id)
                    val s = datos.senales(a.id)
                    val p = datos.proxima(a.id, hoy.toString())
                    var menu by remember { mutableStateOf(false) }
                    Tarjeta(onClick = { onArticulo(a.id) }, onLongClick = { menu = true }) {
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem({ Text("Eliminar") }, { menu = false; onEliminar(a.id) })
                        }
                        Text(datos.titulo(a.id), style = Estilos.tarjetaTitulo, color = c.texto)
                        if (!i?.descripcion.isNullOrBlank()) {
                            Text(
                                Critic.limpio(i!!.descripcion), Modifier.padding(top = 4.dp), style = Estilos.uiPeque.copy(lineHeight = Estilos.ui.lineHeight),
                                color = c.secundario, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ChipEstado(a.estado)
                            // La serie cede sitio y se corta: la fecha y las señales nunca se parten.
                            datos.serie(a.serieId)?.let {
                                Text(
                                    "${it.nombre}${a.serieParte?.let { n -> " · $n" } ?: ""}", Modifier.weight(1f),
                                    style = Estilos.uiPeque, color = c.secundario, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            } ?: Spacer(Modifier.weight(1f))
                            if (s.propuestas > 0) Senal(Icons.Outlined.SmartToy, s.propuestas)
                            if (s.notas > 0) Senal(Icons.Outlined.StickyNote2, s.notas)
                            if (s.conflictos > 0) Senal(Icons.Outlined.CallSplit, s.conflictos)
                            if (p != null) {
                                IconoRed(datos.canal(p.canalId)?.icono ?: "", tam = 13.dp)
                                Text(p.fecha?.let { cuando(it, hoy) }.orEmpty(), style = Estilos.uiPeque, color = c.secundario, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(90.dp)) }
            }
        }
        BotonCrear(Modifier.align(Alignment.BottomEnd).padding(18.dp), onClick = onNuevo)
    }
}

@Composable
private fun Senal(icono: androidx.compose.ui.graphics.vector.ImageVector, n: Int) {
    val c = Tinta.colores
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Icon(icono, null, Modifier.size(14.dp), tint = c.secundario)
        Text("$n", style = Estilos.uiPeque, color = c.secundario)
    }
}
