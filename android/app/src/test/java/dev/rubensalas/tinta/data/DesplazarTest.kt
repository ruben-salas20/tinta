package dev.rubensalas.tinta.data

import dev.rubensalas.tinta.data.local.RangoAutoria
import dev.rubensalas.tinta.data.texto.desplazar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Los mismos casos que `autoria.rs` del servicio: los dos deben desplazar igual. */
class DesplazarTest {
    private fun r(desde: Int, hasta: Int) = RangoAutoria(desde, hasta, "claude")

    @Test fun sinCambiosNoMueveNada() {
        assertEquals(listOf(r(5, 10)), desplazar(listOf(r(5, 10)), "Hola mundo", "Hola mundo"))
    }

    @Test fun cambioAntesDesplaza() {
        assertEquals(listOf(r(12, 17)), desplazar(listOf(r(5, 10)), "hola mundo", "hola grande mundo"))
        assertEquals(listOf(r(8, 13)), desplazar(listOf(r(5, 10)), "hola mundo", "AB hola mundo"))
    }

    @Test fun cambioDespuesNoMueve() {
        assertEquals(listOf(r(0, 4)), desplazar(listOf(r(0, 4)), "hola mundo", "hola mundo!!"))
        assertEquals(listOf(r(0, 4)), desplazar(listOf(r(0, 4)), "hola mundo", "hola"))
    }

    @Test fun cambioEncimaBorraElRango() {
        assertTrue(desplazar(listOf(r(5, 10)), "hola mundo", "hola xyz").isEmpty())
        assertTrue(desplazar(listOf(r(2, 4)), "abcd", "ab").isEmpty())
    }

    @Test fun cambioEnParteRecortaALoIntacto() {
        assertEquals(listOf(r(5, 7)), desplazar(listOf(r(5, 10)), "hola mundo", "hola muelle"))
        assertEquals(listOf(r(6, 11)), desplazar(listOf(r(5, 10)), "hola mundo", "adiós mundo"))
    }

    @Test fun insertarDentroParteElRango() {
        assertEquals(listOf(r(0, 2), r(3, 5)), desplazar(listOf(r(0, 4)), "abcd", "abXcd"))
        assertEquals(listOf(r(3, 5)), desplazar(listOf(r(2, 4)), "abcd", "abXcd"))
        assertEquals(listOf(r(0, 2)), desplazar(listOf(r(0, 2)), "abcd", "abXcd"))
    }

    @Test fun cuentaPuntosDeCodigoNoUnidadesUtf16() {
        assertEquals(listOf(r(11, 16)), desplazar(listOf(r(4, 9)), "ñé🙂 mundo", "ñé🙂 grande mundo"))
    }

    @Test fun variosRangosALaVez() {
        val nuevo = desplazar(listOf(r(0, 2), r(4, 6), r(7, 9)), "aa bbb cc", "aa bb--b cc")
        assertEquals(listOf(r(0, 2), r(4, 5), r(7, 8), r(9, 11)), nuevo)
    }
}
