package dev.rubensalas.tinta

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.rubensalas.tinta.ui.AppTinta
import dev.rubensalas.tinta.ui.tema.TemaTinta

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val c = (application as TintaApp).contenedor
        setContent { TemaTinta { AppTinta(c) } }
    }

    override fun onResume() {
        super.onResume()
        // Si se fue a dar permiso para instalar una actualización, sigue sola.
        (application as TintaApp).contenedor.actualizaciones.onResumed()
    }
}
