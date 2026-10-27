package dev.rubensalas.tinta.data

import dev.rubensalas.tinta.data.calendario.esImagen
import dev.rubensalas.tinta.data.calendario.partir
import dev.rubensalas.tinta.data.local.PublicacionE
import dev.rubensalas.tinta.data.resumen.Avisos
import dev.rubensalas.tinta.data.resumen.Resumen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class CalendarioTest {
    @Test fun partirSinCortarPalabras() {
        val (a, b) = partir("uno dos tres cuatro", 9)
        assertEquals("uno dos", a)
        assertEquals("tres cuatro", b)
        assertEquals("corto" to "", partir("corto", 280))
        assertEquals("abcde" to "fgh", partir("abcdefgh", 5))
        val largo = "palabra ".repeat(50).trim()
        val (x, y) = partir(largo, 280)
        assertTrue(x.length <= 280 && (x + " " + y) == largo)
    }

    @Test fun soloLasImagenesSeVenComoMiniatura() {
        assertTrue(esImagen("image/png"))
        assertFalse(esImagen("audio/mpeg"))
        assertFalse(esImagen("video/mp4"))
    }

    private fun p(id: String, fecha: String, estado: String = "idea", hora: String? = null) =
        PublicacionE(id, "x", "post", fecha, hora, estado, titulo = id, creado = "", cambiado = "", rev = 1)

    @Test fun resumenDelDia() {
        val hoy = LocalDate.parse("2026-10-26")
        val r = Resumen.calcular(
            listOf(
                p("tarde", "2026-10-26", hora = "18:00"), p("pronto", "2026-10-26", hora = "09:00"), p("sinhora", "2026-10-26"),
                p("hecha", "2026-10-26", "publicada"), p("atrasada", "2026-10-24", "preparando"), p("manana", "2026-10-27"),
                p("borrada", "2026-10-26").copy(borrado = 1),
            ),
            hoy,
        )
        assertEquals(listOf("pronto", "tarde", "sinhora"), r.hoy.map { it.id })
        assertEquals(listOf("atrasada"), r.atrasadas.map { it.id })
        assertEquals(listOf("manana"), r.manana.map { it.id })
        assertEquals("Hoy: 3 publicaciones y 1 atrasada", r.titulo)
        assertEquals("09:00 · X · pronto", r.lineas { "X" }.first())
        assertEquals("Hoy no toca publicar nada", Resumen.calcular(emptyList(), hoy).titulo)
    }

    @Test fun proximoAviso() {
        val ahora = LocalDateTime.parse("2026-10-26T07:30")
        assertEquals(LocalDateTime.parse("2026-10-26T08:00"), Avisos.proximo(ahora, 8 * 60))
        assertEquals(LocalDateTime.parse("2026-10-27T07:00"), Avisos.proximo(ahora, 7 * 60))
    }
}
