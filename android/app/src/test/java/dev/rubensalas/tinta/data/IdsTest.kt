package dev.rubensalas.tinta.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Los mismos valores que la prueba `copias_con_ids_derivados` del servicio. */
class IdsTest {
    @Test fun derivadoComoElServicio() {
        assertEquals("db1a5ddbd892f21e46beccd1", derivado("a".repeat(24), "b".repeat(24)))
        assertEquals("44000961e19ffa8806626024", idEtiqueta("ia"))
    }
}
