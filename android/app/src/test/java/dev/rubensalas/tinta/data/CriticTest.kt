package dev.rubensalas.tinta.data

import dev.rubensalas.tinta.data.texto.Critic
import dev.rubensalas.tinta.data.texto.Critic.Decision
import dev.rubensalas.tinta.data.texto.Trozo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Las mismas pruebas que `critic.rs`: el móvil y el servicio leen igual. */
class CriticTest {
    private val ejemplo = "llama-swap {--es muy sencillo y--} arranca {~~El ttl dice~>El campo ttl marca~~}{>>ia:claude<<} y {++apaga++}{>>ia:claude<<} el {==anterior==}{>>poner tiempos<<}. {--Fin--}{>>ia:claude<<}"

    @Test fun leerYEscribirEsIdentidad() {
        assertEquals(ejemplo, Critic.escribir(Critic.leer(ejemplo)))
        val t = Critic.leer(ejemplo)
        assertEquals(Trozo.Borrado("es muy sencillo y", null), t[1])
        assertEquals("claude", (t[3] as Trozo.Cambio).ia)
    }

    @Test fun limpioQuitaLoPendienteYLoTachado() {
        assertEquals("llama-swap  arranca El ttl dice y  el anterior. Fin", Critic.limpio(ejemplo))
        assertEquals("llama-swap arranca El ttl dice y el anterior. Fin", Critic.limpioProsa(ejemplo))
        assertEquals("    ttl: 300 ", Critic.limpio("    ttl: 300 {--x--}"))
        assertEquals("- uno dos\n  - anidado", Critic.limpioProsa("- uno {--x--} dos\n  - anidado"))
    }

    @Test fun contarMarcas() {
        assertEquals(Critic.Recuento(tachados = 1, propuestas = 3, notas = 1), Critic.contar(ejemplo))
    }

    @Test fun resolverCadaTipo() {
        // 0 tachado, 1 cambio, 2 añadido, 3 nota, 4 borrado del agente
        assertTrue(Critic.resolver(ejemplo, 1, Decision.Aceptar)!!.contains("arranca El campo ttl marca y"))
        assertTrue(Critic.resolver(ejemplo, 0, Decision.Rechazar)!!.startsWith("llama-swap es muy sencillo y arranca"))
        val r = Critic.resolver(ejemplo, 3, Decision.Aceptar)!!
        assertTrue(r.contains("el anterior."))
        assertFalse(r.contains("{=="))
        assertTrue(Critic.resolver(ejemplo, 4, Decision.Aceptar)!!.endsWith("anterior==}{>>poner tiempos<<}. "))
        assertNull(Critic.resolver(ejemplo, 9, Decision.Aceptar))
    }

    @Test fun llavesSueltasSonTexto() {
        assertEquals(listOf(Trozo.Texto("a {-- b")), Critic.leer("a {-- b"))
        assertEquals("código {x}", Critic.limpio("código {x}"))
    }

    @Test fun notaDelAgente() {
        val t = Critic.leer("Hola {==mundo==}{>>ia:claude: ¿Seguro?<<}")
        assertEquals(Trozo.Nota("¿Seguro?", "claude"), t[2])
        assertEquals("Hola {==mundo==}{>>ia:claude: ¿Seguro?<<}", Critic.escribir(t))
    }
}
