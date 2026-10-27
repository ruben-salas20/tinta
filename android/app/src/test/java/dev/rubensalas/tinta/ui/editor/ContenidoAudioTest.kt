package dev.rubensalas.tinta.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class ContenidoAudioTest {
    private val id = "0123456789abcdef01234567"

    @Test fun usaElNombreSinExtensionComoTitulo() {
        assertEquals(
            """<audio src="medio:$id" title="voz" controls></audio>""",
            contenidoAudio(id, "voz.mp3"),
        )
    }

    @Test fun escapaLasComillasDelTitulo() {
        assertEquals(
            """<audio src="medio:$id" title="Di &quot;hola&quot;" controls></audio>""",
            contenidoAudio(id, "Di \"hola\".ogg"),
        )
    }

    @Test fun sinExtensionDejaElNombreEntero() {
        assertEquals(
            """<audio src="medio:$id" title="audio" controls></audio>""",
            contenidoAudio(id, "audio"),
        )
    }
}
