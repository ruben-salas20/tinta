package dev.rubensalas.tinta.data

import dev.rubensalas.tinta.data.texto.Edicion
import dev.rubensalas.tinta.data.texto.Trozo
import dev.rubensalas.tinta.data.texto.quitarPrefijo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EdicionTest {
    private val base = "Uno {--dos--} tres {~~a~>b~~}{>>ia:claude<<} {==ancla==}{>>nota<<} fin."

    @Test fun idaYVueltaSinTocarLasMarcas() {
        val e = Edicion.desde(base, "parrafo")
        assertEquals(base, e.aMarkup())
        assertEquals(4, e.marcas.size)
        assertEquals(base.length - e.texto.length > 0, true)
    }

    @Test fun escribirNoPierdeMarcas() {
        val e = Edicion.desde(base, "parrafo")
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
        val e = Edicion.desde("Hola mundo cruel", "parrafo")
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
        assertNotNull(Edicion.desde("ab", "parrafo").let { assertEquals("ab", it.aMarkup()); it })
    }

    @Test fun subtituloQuitaYPoneElPrefijo() {
        val e = Edicion.desde("### Hola mundo", "subtitulo")
        assertEquals("Hola mundo", e.texto)
        assertEquals("### Hola mundo", e.aMarkup())
        assertEquals("### ### x", Edicion.desde("### ### x", "subtitulo").let { assertEquals("### x", it.texto); it.aMarkup() })
    }

    @Test fun listaConVariasLineas() {
        val e = Edicion.desde("- uno\n- dos\n- tres", "lista")
        assertEquals("uno\ndos\ntres", e.texto)
        assertEquals("- uno\n- dos\n- tres", e.aMarkup())
        // Al escribir una línea nueva, la lista pone su `- `.
        val nuevo = e.aplicar(e.texto + "\ncuatro")!!
        assertEquals("- uno\n- dos\n- tres\n- cuatro", nuevo.aMarkup())
    }

    @Test fun citaConVariasLineasYLineaVacia() {
        val e = Edicion.desde("> uno\n>\n> dos", "cita")
        assertEquals("uno\n\ndos", e.texto)
        assertEquals("> uno\n>\n> dos", e.aMarkup())
    }

    @Test fun lineaQueEmpiezaConUnaMarca() {
        // La marca va justo tras el prefijo: el prefijo se quita y la marca queda intacta.
        val lista = "- {--uno--}\n- dos"
        val l = Edicion.desde(lista, "lista")
        assertEquals("${Edicion.marcador(0)}\ndos", l.texto)
        assertEquals(lista, l.aMarkup())

        val cita = "> {==x==}{>>nota<<}\n> fin"
        val c = Edicion.desde(cita, "cita")
        assertEquals("${Edicion.marcador(0)}${Edicion.marcador(1)}\nfin", c.texto)
        assertEquals(cita, c.aMarkup())
    }

    @Test fun marcaQueCruzaDosLineas() {
        // Una marca con un salto de línea dentro no se parte: su contenido se guarda entero.
        val markup = "- {--a\n- b--}\n- c"
        val e = Edicion.desde(markup, "lista")
        assertEquals("${Edicion.marcador(0)}\nc", e.texto)
        assertEquals(markup, e.aMarkup())
    }

    @Test fun tacharEnListaConservaElPrefijo() {
        val e = Edicion.desde("- uno", "lista").tachar(0, 3)!!
        assertEquals("- {--uno--}", e.aMarkup())
    }

    @Test fun vacioSigueVacio() {
        assertEquals("", Edicion.desde("", "lista").aMarkup())
        assertEquals("", Edicion.desde("", "subtitulo").aMarkup())
    }

    @Test fun quitarPrefijoSinPrefijoNoCambia() {
        assertEquals("uno", quitarPrefijo("lista", "uno"))
        assertEquals("#uno", quitarPrefijo("subtitulo", "#uno"))
        assertEquals("", quitarPrefijo("cita", ">"))
    }
}
