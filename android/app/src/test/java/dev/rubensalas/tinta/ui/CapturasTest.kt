package dev.rubensalas.tinta.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dev.rubensalas.tinta.ui.ajustes.PantallaInicio
import dev.rubensalas.tinta.ui.articulos.PantallaArticulos
import dev.rubensalas.tinta.ui.calendario.PantallaCalendario
import dev.rubensalas.tinta.ui.calendario.PantallaPublicacion
import dev.rubensalas.tinta.ui.comun.BarraInferior
import dev.rubensalas.tinta.ui.comun.Seccion
import dev.rubensalas.tinta.ui.editor.HojaVariantes
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

    @Test fun conflicto() = capturar("5-conflicto") {
        PantallaConflicto(d.documentoDeBloque("b3")!!, {}, {}, {})
    }

    @Test fun calendario() = capturar("6a-calendario") {
        ConBarra(Seccion.Calendario) { PantallaCalendario(d, Muestras.hoy, onPublicacion = {}, onNueva = {}) }
    }

    @Test fun publicacion() = capturar("6b-publicacion") {
        PantallaPublicacion(d, "p-hilo", Muestras.accionesPublicacion, { null }, onVolver = {})
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
