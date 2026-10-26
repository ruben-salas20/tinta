package dev.rubensalas.tinta.data

import dev.rubensalas.tinta.data.texto.Edicion
import dev.rubensalas.tinta.data.texto.Trozo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EdicionTest {
    private val base = "Uno {--dos--} tres {~~a~>b~~}{>>ia:claude<<} {==ancla==}{>>nota<<} fin."

    @Test fun idaYVueltaSinTocarLasMarcas() {
        val e = Edicion.desde(base)
        assertEquals(base, e.aMarkup())
        assertEquals(4, e.marcas.size)
        assertEquals(base.length - e.texto.length > 0, true)
    }

    @Test fun escribirNoPierdeMarcas() {
        val e = Edicion.desde(base)
        val nuevo = e.aplicar("Hola " + e.texto)!!
        assertEquals("Hola $base", nuevo.aMarkup())
        // Borrar un marcador se rechaza.
        val sinMarca = e.texto.filterIndexed { i, _ -> i != e.texto.indexOfFirst { Edicion.indice(it) != null } }
        assertNull(e.aplicar(sinMarca))
        // Reordenarlos también.
        val i = e.texto.indexOfFirst { Edicion.indice(it) != null }
        val j = e.texto.indexOfLast { Edicion.indice(it) != null }
        val c = e.texto.toCharArray().also { val x = it[i]; it[i] = it[j]; it[j] = x }
        assertNull(e.aplicar(String(c)))
    }

    @Test fun tacharYAnotar() {
        val e = Edicion.desde("Hola mundo cruel")
        val t = e.tachar(5, 11)!!
        assertEquals("Hola {--mundo --}cruel", t.aMarkup())
        // No se tacha encima de una marca.
        assertNull(t.tachar(3, 7))
        assertNull(e.tachar(4, 4))
        val n = e.anotar(5, 10, "¿Seguro?")!!
        assertEquals("Hola {==mundo==}{>>¿Seguro?<<} cruel", n.aMarkup())
        val n2 = e.anotar(16, 16, "al final")!!
        assertEquals("Hola mundo cruel{>>al final<<}", n2.aMarkup())
        assertTrue(n2.marcasEnOrden().single() is Trozo.Nota)
    }

    @Test fun marcadoresEscritosAMano() {
        assertNotNull(Edicion.desde("ab").let { assertEquals("ab", it.aMarkup()); it })
    }
}
