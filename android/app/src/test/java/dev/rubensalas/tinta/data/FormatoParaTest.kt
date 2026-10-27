package dev.rubensalas.tinta.data

import dev.rubensalas.tinta.data.calendario.formatoPara
import dev.rubensalas.tinta.data.calendario.juntar
import dev.rubensalas.tinta.data.local.Formato
import dev.rubensalas.tinta.data.local.Parte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatoParaTest {
    private val post = Formato("post", "Post", 280, partes = false)
    private val hilo = Formato("hilo", "Hilo", 280, partes = true)

    @Test fun conVariasPartesPrefiereUnFormatoQueLasAdmita() {
        assertEquals("hilo", formatoPara(listOf(post, hilo), 3)?.id)
        assertEquals("post", formatoPara(listOf(post, hilo), 1)?.id)
        assertEquals("post", formatoPara(listOf(post), 3)?.id)
        assertNull(formatoPara(emptyList(), 2))
    }

    @Test fun juntarUneTextosYMedios() {
        val r = juntar(listOf(Parte("uno ", listOf("a")), Parte(""), Parte("dos", listOf("b"))))
        assertEquals(listOf(Parte("uno\n\ndos", listOf("a", "b"))), r)
    }
}
