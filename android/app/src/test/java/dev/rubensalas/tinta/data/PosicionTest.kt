package dev.rubensalas.tinta.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PosicionTest {
    @Test fun igualQueElServicio() {
        // posicion::entre(None, None) da «V» en el servicio.
        assertEquals("V", Posicion.entre(null, null))
        val a = Posicion.entre(null, null)
        val b = Posicion.entre(a, null)
        val c = Posicion.entre(null, a)
        assertTrue(c < a && a < b)
    }

    @Test fun siempreCabeOtra() {
        var lo = Posicion.entre(null, null)
        val hi = Posicion.entre(lo, null)
        repeat(500) {
            val m = Posicion.entre(lo, hi)
            assertTrue("$lo < $m < $hi", lo < m && m < hi && !m.endsWith("0"))
            lo = m
        }
    }

    @Test fun idsComoLosDelServicio() {
        repeat(20) { assertTrue(Regex("[0-9a-f]{24}").matches(nuevoId())) }
    }
}
