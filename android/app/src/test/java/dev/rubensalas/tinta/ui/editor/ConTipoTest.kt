package dev.rubensalas.tinta.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class ConTipoTest {
    @Test fun cambiaElPrefijo() {
        assertEquals("### Hola", conTipo("subtitulo", "Hola"))
        assertEquals("Hola", conTipo("parrafo", "### Hola"))
        assertEquals("- uno\n- dos", conTipo("lista", "uno\ndos"))
        assertEquals("> uno\n>\n> dos", conTipo("cita", "- uno\n\n- dos"))
        assertEquals("uno\ndos", conTipo("parrafo", "> uno\n> dos"))
    }
}
