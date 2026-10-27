package dev.rubensalas.tinta.data

import dev.rubensalas.tinta.data.api.json
import dev.rubensalas.tinta.data.local.Parte
import dev.rubensalas.tinta.data.local.PublicacionE
import dev.rubensalas.tinta.data.resumen.Resumen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Publicaciones sin fecha o sin canal: lo que ven el calendario, «Hoy» y el resumen. */
class IdeasTest {
    private fun p(
        id: String, fecha: String?, estado: String = "idea", canal: String? = null, cambiado: String = "2026-10-01T00:00:00Z",
        titulo: String = id, partes: List<Parte> = listOf(Parte()), borrado: Int = 0,
    ) = PublicacionE(id, canal, canal?.let { "post" }, fecha, null, estado, titulo, partes, creado = "", cambiado = cambiado, rev = 1, borrado = borrado)

    @Test fun ideasSonLasSinFechaVivasOrdenadasPorCambiado() {
        val datos = Datos(publicaciones = listOf(
            p("vieja", null, cambiado = "2026-10-01T10:00:00Z"),
            p("con-fecha", "2026-11-02", cambiado = "2026-10-30T10:00:00Z"),
            p("reciente", null, cambiado = "2026-10-20T10:00:00Z"),
            p("borrada", null, cambiado = "2026-10-29T10:00:00Z", borrado = 1),
        ))
        assertEquals(listOf("reciente", "vieja"), datos.ideas().map { it.id })
    }

    @Test fun tituloOPrimerTrozoDelTexto() {
        assertEquals("Mi título", Datos.tituloDe(p("a", null, titulo = "Mi título")))
        assertEquals("Primera línea", Datos.tituloDe(p("b", null, titulo = "", partes = listOf(Parte("\n  Primera línea  \nsegunda")))))
        assertEquals("Sin título", Datos.tituloDe(p("c", null, titulo = "", partes = listOf(Parte("  \n ")))))
    }

    @Test fun estadosSinFechaOCanal() {
        val suelta = p("s", null)
        assertTrue(Datos.estadoPermitido("idea", suelta))
        assertTrue(Datos.estadoPermitido("preparando", suelta))
        assertFalse(Datos.estadoPermitido("programada", suelta))
        assertFalse(Datos.estadoPermitido("publicada", suelta))
        // Con fecha pero sin canal: tampoco se programa.
        assertFalse(Datos.estadoPermitido("programada", p("f", "2026-11-02")))
        assertTrue(Datos.estadoPermitido("programada", p("g", "2026-11-02", canal = "x")))
    }

    @Test fun resumenIgnoraLasSinFecha() {
        val hoy = LocalDate.parse("2026-10-26")
        val r = Resumen.calcular(
            listOf(p("idea", null), p("idea-canal", null, canal = "x"), p("hoy", "2026-10-26", canal = "x"), p("atras", "2026-10-20", canal = "x")),
            hoy,
        )
        assertEquals(listOf("hoy"), r.hoy.map { it.id })
        assertEquals(listOf("atras"), r.atrasadas.map { it.id })
        assertEquals("Hoy: 1 publicación y 1 atrasada", r.titulo)
    }

    @Test fun proximaIgnoraLasSinFecha() {
        val datos = Datos(publicaciones = listOf(
            p("sin-fecha", null).copy(articuloId = "a"),
            p("con-fecha", "2026-11-03", canal = "x").copy(articuloId = "a"),
        ))
        assertEquals("con-fecha", datos.proxima("a", "2026-10-26")?.id)
    }

    @Test fun jsonDeSincronizacionAceptaNulos() {
        val p = json.decodeFromString(PublicacionE.serializer(), """
            {"id":"p1","canal_id":null,"formato":null,"fecha":null,"hora":null,"estado":"idea","titulo":"Idea",
             "partes":[{"texto":"x","medios":[]}],"articulo_id":null,"enlace":null,"origen":"tu",
             "creado":"2026-10-01T00:00:00Z","cambiado":"2026-10-01T00:00:00Z","rev":4,"borrado":0}
        """.trimIndent())
        assertNull(p.canalId)
        assertNull(p.formato)
        assertNull(p.fecha)
        assertNull(p.hora)
    }
}
