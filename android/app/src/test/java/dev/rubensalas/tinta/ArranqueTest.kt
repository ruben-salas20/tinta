package dev.rubensalas.tinta

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.work.testing.WorkManagerTestInitHelper
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** La app de verdad arranca: TintaApp con su contenedor y MainActivity con la primera pantalla. */
class TintaAppPrueba : TintaApp() {
    override fun onCreate() {
        WorkManagerTestInitHelper.initializeTestWorkManager(this)
        super.onCreate()
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-xxhdpi", application = TintaAppPrueba::class)
class ArranqueTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    // Los ajustes viven en memoria todo el proceso: cada prueba empieza sin clave.
    @org.junit.Before fun sinClave() {
        kotlinx.coroutines.runBlocking {
            (compose.activity.application as TintaApp).contenedor.ajustes.guardarServidor(dev.rubensalas.tinta.data.Ajustes.URL_POR_DEFECTO, "")
        }
    }

    @Test fun primeraVezPideElServicio() {
        compose.waitUntil(5000) { compose.onAllNodes(androidx.compose.ui.test.hasText("Probar y entrar")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Dirección del servicio").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/0-arranque-real.png")
    }

    /** Con la copia de desarrollo: entrar con la clave, bajar los datos y ver Hoy y Artículos. */
    @Test fun entrarYVerLosDatos() {
        val url = System.getenv("TINTA_DEV_URL")
        org.junit.Assume.assumeTrue(!url.isNullOrBlank())
        compose.waitUntil(5000) { compose.onAllNodes(androidx.compose.ui.test.hasText("Probar y entrar")).fetchSemanticsNodes().isNotEmpty() }
        val campos = compose.onAllNodes(androidx.compose.ui.test.hasSetTextAction())
        campos[0].performTextReplacement(url!!)
        campos[1].performTextReplacement("dev-clave")
        compose.onNodeWithText("Probar y entrar").performClick()
        compose.waitUntil(15000) { compose.onAllNodes(androidx.compose.ui.test.hasText("Próximos días", substring = true, ignoreCase = true)).fetchSemanticsNodes().isNotEmpty() }
        // La sincronización corre en WorkManager; en la prueba se lanza a mano.
        kotlinx.coroutines.runBlocking { (compose.activity.application as TintaApp).contenedor.sincronizador.sincronizar() }
        compose.onNodeWithText("Artículos").performClick()
        compose.waitUntil(10000) { (compose.activity.application as TintaApp).contenedor.repositorio.datos.value.articulos.isNotEmpty() }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/0-arranque-articulos.png")
    }
}
