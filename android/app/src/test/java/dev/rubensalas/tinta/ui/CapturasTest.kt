package dev.rubensalas.tinta.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.rubensalas.tinta.ui.ajustes.PantallaInicio
import dev.rubensalas.tinta.ui.articulos.AvisoDeshacer
import dev.rubensalas.tinta.ui.articulos.DialogoEliminarArticulo
import dev.rubensalas.tinta.ui.articulos.PantallaArticulos
import dev.rubensalas.tinta.ui.calendario.PantallaCalendario
import dev.rubensalas.tinta.ui.calendario.PantallaPublicacion
import dev.rubensalas.tinta.ui.comun.BarraInferior
import dev.rubensalas.tinta.ui.comun.Seccion
import dev.rubensalas.tinta.ui.editor.HojaFicha
import dev.rubensalas.tinta.data.local.BloqueE
import dev.rubensalas.tinta.data.local.VarianteBloqueE
import dev.rubensalas.tinta.ui.editor.BloqueLectura
import dev.rubensalas.tinta.ui.editor.Comparacion
import dev.rubensalas.tinta.ui.editor.HojaInferior
import dev.rubensalas.tinta.ui.editor.HojaTipoBloque
import dev.rubensalas.tinta.ui.editor.HojaIdiomas
import dev.rubensalas.tinta.ui.editor.HojaRamas
import dev.rubensalas.tinta.ui.editor.HojaVariantes
import dev.rubensalas.tinta.ui.editor.HojaVariantesSeccion
import dev.rubensalas.tinta.data.local.VarianteSeccionE
import dev.rubensalas.tinta.ui.editor.VersionComparada
import dev.rubensalas.tinta.ui.editor.PantallaConflicto
import dev.rubensalas.tinta.ui.editor.PantallaEditor
import dev.rubensalas.tinta.ui.hoy.PantallaHoy
import dev.rubensalas.tinta.ui.tema.TemaTinta
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Capturas de las pantallas principales, en claro y en oscuro, sin emulador.
 * Se guardan con `android/compilar.sh recordRoborazziDebug` en
 * app/build/outputs/roborazzi/, para compararlas con diseno/movil.html.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-xxhdpi", application = android.app.Application::class)
class CapturasTest(private val oscuro: Boolean) {
    @get:Rule val compose = createComposeRule()

    private val tema get() = if (oscuro) "oscuro" else "claro"
    private val d = Muestras.datos

    private fun capturar(nombre: String, contenido: @Composable () -> Unit) {
        compose.setContent { TemaTinta(oscuro = oscuro) { contenido() } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$nombre-$tema.png")
    }

    @Composable
    private fun ConBarra(s: Seccion, contenido: @Composable () -> Unit) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) { contenido() }
            BarraInferior(s) {}
        }
    }

    @Test fun hoy() = capturar("1-hoy") {
        ConBarra(Seccion.Hoy) { PantallaHoy(d, Muestras.hoy, sinRed = true, onArticulo = {}, onPublicacion = {}, onNuevoArticulo = {}) }
    }

    @Test fun articulos() = capturar("2-articulos") {
        ConBarra(Seccion.Articulos) { PantallaArticulos(d, Muestras.hoy, onArticulo = {}, onNuevo = {}) }
    }

    @Test fun editor() = capturar("3-editor") {
        PantallaEditor(d, "llama", sinRed = false, Muestras.accionesEditor, { null }, onVolver = {}, onConflicto = {}, focoInicial = "b2")
    }

    @Test fun editorPropuestas() = capturar("3b-editor-propuestas") {
        PantallaEditor(d, "llama", sinRed = true, Muestras.accionesEditor, { null }, onVolver = {}, onConflicto = {}, focoInicial = "b1")
    }

    @Test fun variantes() = capturar("4-variantes") {
        Box(Modifier.fillMaxSize()) {
            PantallaEditor(d, "llama", sinRed = false, Muestras.accionesEditor, { null }, onVolver = {}, onConflicto = {})
            HojaVariantes(d.documentoDeBloque("b2")!!, {}, {}, {}, {})
        }
    }

    @Test fun ficha() = capturar("4b-ficha") {
        Box(Modifier.fillMaxSize()) {
            PantallaEditor(d, "llama", sinRed = false, Muestras.accionesEditor, { null }, onVolver = {}, onConflicto = {})
            HojaFicha(d, d.articulo("llama")!!, Muestras.accionesEditor, onCerrar = {})
        }
    }

    @Test fun comparar() = capturar("4b-comparar") {
        val b = d.documentoDeBloque("b2")!!
        HojaInferior({}) {
            Comparacion("parrafo", VersionComparada("A · activa", b.activa!!.contenido), VersionComparada("C", b.variantes[2].contenido), onVolver = {})
        }
    }

    @Test fun tiposBloque() = capturar("4c-tipos-bloque") {
        Box(Modifier.fillMaxSize()) {
            PantallaEditor(d, "llama", sinRed = false, Muestras.accionesEditor, { null }, onVolver = {}, onConflicto = {}, focoInicial = "b2")
            HojaTipoBloque({ _, _ -> }, {}, {}, {})
        }
    }

    @Test fun bloquesLectura() = capturar("4d-bloques-lectura") {
        val e = d.copy(
            bloques = d.bloques + listOf(BloqueE("b4", "vs-b", "W", "youtube", "v4", 1), BloqueE("b5", "vs-b", "X", "codigo", "v5", 1)),
            variantesBloque = d.variantesBloque + listOf(
                VarianteBloqueE("v4", "b4", "https://youtu.be/jNQXAC9IVRw", "tu", "2026-10-26T09:00:00Z", "2026-10-26T09:00:00Z", 1),
                VarianteBloqueE("v5", "b5", "```yaml\nmodels:\n  ttl: 300\n```", "tu", "2026-10-26T09:00:00Z", "2026-10-26T09:00:00Z", 1),
            ),
        )
        Column(Modifier.padding(20.dp)) {
            for (id in listOf("b4", "b5")) BloqueLectura(e.documentoDeBloque(id)!!, { null }) {}
        }
    }

    @Test fun secciones() = capturar("4e-secciones") {
        val e = d.copy(
            variantesSeccion = d.variantesSeccion + VarianteSeccionE("vs-b2", "s-b", "Lo que aprendí, más corto", creado = "2", rev = 1),
            bloques = d.bloques + BloqueE("b9", "vs-b2", "V", "parrafo", "v9", 1),
            variantesBloque = d.variantesBloque + VarianteBloqueE("v9", "b9", "Una sola idea: medir antes de comprar.", "tu", "2", "2", 1),
        )
        Box(Modifier.fillMaxSize()) {
            PantallaEditor(e, "llama", sinRed = false, Muestras.accionesEditor, { null }, onVolver = {}, onConflicto = {})
            HojaVariantesSeccion(e, e.documento("r-llama").first { it.seccion.id == "s-b" }, {}, {}, {}, {})
        }
    }

    @Test fun ramas() = capturar("4b-ramas") {
        Box(Modifier.fillMaxSize()) {
            PantallaEditor(d, "llama", sinRed = false, Muestras.accionesEditor, { null }, onVolver = {}, onConflicto = {})
            HojaRamas(d, d.idiomaPrincipal("llama")!!, "r-llama-2", Muestras.accionesEditor, {}, {})
        }
    }

    @Test fun idiomas() = capturar("4c-idiomas") {
        Box(Modifier.fillMaxSize()) {
            PantallaEditor(d, "llama", sinRed = false, Muestras.accionesEditor, { null }, onVolver = {}, onConflicto = {})
            HojaIdiomas(d, "llama", d.idiomaPrincipal("llama")!!, Muestras.accionesEditor, {}, {})
        }
    }

    @Test fun idiomasSinIngles() = capturar("4d-idiomas-sin-ingles") {
        Box(Modifier.fillMaxSize()) {
            PantallaEditor(d, "agentes", sinRed = false, Muestras.accionesEditor, { null }, onVolver = {}, onConflicto = {})
            HojaIdiomas(d, "agentes", d.idiomaPrincipal("agentes")!!, Muestras.accionesEditor, {}, {})
        }
    }

    @Test fun conflicto() = capturar("5-conflicto") {
        PantallaConflicto(d.documentoDeBloque("b3")!!, {}, {}, {})
    }

    @Test fun calendario() = capturar("6a-calendario") {
        ConBarra(Seccion.Calendario) { PantallaCalendario(d, Muestras.hoy, onPublicacion = {}, onNueva = {}, onNuevaIdea = {}) }
    }

    @Test fun calendarioIdeas() = capturar("6a3-calendario-ideas") {
        ConBarra(Seccion.Calendario) { PantallaCalendario(d, Muestras.hoy, onPublicacion = {}, onNueva = {}, onNuevaIdea = {}, vistaInicial = "ideas") }
    }

    @Test fun calendarioMes() = capturar("6a2-calendario-mes") {
        // Tres publicaciones más el 27 para que aparezca el «+N» en la celda.
        val extra = (1..3).map { d.publicaciones.first { p -> p.id == "p2" }.copy(id = "e$it", fecha = "2026-10-27") }
        ConBarra(Seccion.Calendario) { PantallaCalendario(d.copy(publicaciones = d.publicaciones + extra), Muestras.hoy, onPublicacion = {}, onNueva = {}, onNuevaIdea = {}, vistaInicial = "mes") }
    }

    @Test fun publicacion() = capturar("6b-publicacion") {
        PantallaPublicacion(d, "p-hilo", Muestras.accionesPublicacion, { null }, onVolver = {})
    }

    @Test fun eliminarDialogo() {
        compose.setContent {
            TemaTinta(oscuro = oscuro) {
                ConBarra(Seccion.Articulos) { PantallaArticulos(d, Muestras.hoy, onArticulo = {}, onNuevo = {}) }
                DialogoEliminarArticulo(d.titulo("llama"), {}, {})
            }
        }
        compose.waitForIdle()
        // El diálogo va en su propia ventana: se captura la pantalla entera.
        captureScreenRoboImage("build/outputs/roborazzi/7a-eliminar-$tema.png")
    }

    @Test fun eliminarDeshacer() = capturar("7b-deshacer") {
        val aviso = remember { SnackbarHostState() }
        LaunchedEffect(Unit) { aviso.showSnackbar("Artículo eliminado", "Deshacer", duration = SnackbarDuration.Indefinite) }
        ConBarra(Seccion.Articulos) {
            Box(Modifier.fillMaxSize()) {
                PantallaArticulos(d, Muestras.hoy, onArticulo = {}, onNuevo = {})
                SnackbarHost(aviso, Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 8.dp)) { AvisoDeshacer(it) }
            }
        }
    }

    @Test fun inicio() = capturar("0-inicio") {
        PantallaInicio("https://tinta.rubensalas.dev", { _, _ -> }, error = null, probando = false)
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "oscuro={0}")
        fun temas() = listOf(arrayOf<Any>(false), arrayOf<Any>(true))
    }
}
