package dev.rubensalas.tinta.data

import dev.rubensalas.tinta.data.api.json
import dev.rubensalas.tinta.data.local.RangoAutoria
import dev.rubensalas.tinta.data.local.VarianteBloqueE
import dev.rubensalas.tinta.data.texto.piezasLectura
import dev.rubensalas.tinta.data.texto.tramosAutoria
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoriaTest {
    /** Lo que se ve de cada tramo pintado, para comparar con la palabra esperada. */
    private fun pintado(tipo: String, contenido: String, vararg rangos: RangoAutoria): List<String> {
        val piezas = piezasLectura(tipo, contenido)
        val texto = piezas.joinToString("") { it.texto }
        return tramosAutoria(piezas, rangos.toList()).map { texto.substring(it.first, it.last + 1) }
    }

    @Test fun leeLaAutoriaDelJsonDeSincronizacion() {
        val fila = """{"id":"v1","bloque_id":"b1","contenido":"Hola mundo","origen":"tu",
            "creado":"2026-01-01T10:00:00Z","cambiado":"2026-01-01T10:00:00Z","rev":5,
            "autoria":[{"desde":6,"hasta":13,"agente":"claude"}]}"""
        val v = json.decodeFromString(VarianteBloqueE.serializer(), fila)
        assertEquals(listOf(RangoAutoria(6, 13, "claude")), v.autoria)
    }

    @Test fun sinAutoriaLaListaVa() {
        val fila = """{"id":"v1","bloque_id":"b1","creado":"x","cambiado":"x","rev":5}"""
        assertTrue(json.decodeFromString(VarianteBloqueE.serializer(), fila).autoria.isEmpty())
    }

    @Test fun emojisYTildesCuentanPuntosDeCodigo() {
        // Cada emoji ocupa dos caracteres de String, pero aquí cuenta uno.
        assertEquals(listOf("mundo"), pintado("parrafo", "Año 🙂 mundo", RangoAutoria(6, 11, "claude")))
    }

    @Test fun marcaAntesDelRango() {
        // «{--xx--}» ocupa 8 puntos; «hola» empieza en el 8.
        assertEquals(listOf("hola"), pintado("parrafo", "{--xx--}hola mundo", RangoAutoria(8, 12, "claude")))
        // Un rango que cae dentro de la marca no pinta nada.
        assertTrue(pintado("parrafo", "{--xx--}hola", RangoAutoria(3, 5, "claude")).isEmpty())
        // Un rango que cruza la marca pinta solo el texto normal.
        assertEquals(listOf("hola mundo"), pintado("parrafo", "{--xx--}hola mundo", RangoAutoria(0, 30, "claude")))
    }

    @Test fun lineaDeListaSinPrefijo() {
        // «- uno\n- dos»: el texto mostrado es «• uno\n• dos».
        assertEquals(listOf("dos"), pintado("lista", "- uno\n- dos", RangoAutoria(8, 11, "claude")))
    }

    @Test fun subtituloSinPrefijo() {
        assertEquals(listOf("Hola"), pintado("subtitulo", "### Hola", RangoAutoria(4, 8, "claude")))
    }

    @Test fun citaSinPrefijo() {
        assertEquals(listOf("hola"), pintado("cita", "> hola", RangoAutoria(2, 6, "claude")))
    }

    @Test fun negritaSeMapeaAlTextoQueSeVe() {
        assertEquals(listOf("hola"), pintado("parrafo", "**hola** mundo", RangoAutoria(2, 6, "claude")))
    }

    @Test fun cercasDeCodigoNoCuentan() {
        assertEquals(listOf("foo"), pintado("codigo", "```\nfoo\n```", RangoAutoria(4, 7, "claude")))
    }

    @Test fun rangoFueraDelTextoNoRompe() {
        assertTrue(pintado("parrafo", "hola", RangoAutoria(10, 20, "claude")).isEmpty())
        assertTrue(pintado("parrafo", "", RangoAutoria(0, 3, "claude")).isEmpty())
        assertEquals(listOf("ola"), pintado("parrafo", "hola", RangoAutoria(1, 99, "claude")))
    }
}
