package dev.rubensalas.tinta.data

import android.app.Application
import dev.rubensalas.tinta.data.api.Cliente
import dev.rubensalas.tinta.data.api.Servidor
import dev.rubensalas.tinta.data.api.json
import dev.rubensalas.tinta.data.local.ArticuloE
import dev.rubensalas.tinta.data.local.BaseLocal
import dev.rubensalas.tinta.data.local.PublicacionE
import dev.rubensalas.tinta.data.sync.Local
import dev.rubensalas.tinta.data.sync.Op
import dev.rubensalas.tinta.data.sync.Sincronizador
import dev.rubensalas.tinta.data.sync.op
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Eliminar un artículo y deshacerlo: la cola y el efecto en la copia local. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BorrarArticuloTest {
    private lateinit var base: BaseLocal
    private lateinit var repo: Repositorio
    private val dao get() = base.dao()

    private fun pub(id: String, estado: String, articulo: String? = "a1", rev: Long = 3, borrado: Int = 0) =
        PublicacionE(id, "x", "post", "2026-11-01", estado = estado, articuloId = articulo, creado = "", cambiado = "", rev = rev, borrado = borrado)

    @Before fun preparar() = runBlocking {
        val ctx = RuntimeEnvironment.getApplication()
        base = BaseLocal.enMemoria(ctx)
        repo = Repositorio(ctx, base, Cliente({ Servidor("http://127.0.0.1:9", "x") }), CoroutineScope(SupervisorJob() + Dispatchers.Default), pedirSync = {})
        dao.articulos(listOf(ArticuloE("a1", "uno", "borrador", creado = "", cambiado = "", rev = 7), ArticuloE("a2", "dos", "idea", creado = "", cambiado = "", rev = 8)))
        dao.publicaciones(listOf(
            pub("p-idea", "idea"),
            pub("p-prog", "programada"),
            pub("p-hecha", "publicada"),
            pub("p-antes", "idea", rev = 5, borrado = 1),
            pub("p-otro", "idea", articulo = "a2"),
            pub("p-suelta", "idea", articulo = null),
        ))
    }

    @After fun cerrar() = base.close()

    private suspend fun ops() = dao.cola().map { json.parseToJsonElement(it.json).jsonObject }
    private suspend fun borradas() = listOf("p-idea", "p-prog", "p-hecha", "p-antes", "p-otro", "p-suelta").filter { dao.publicacion(it)!!.borrado == 1 }

    @Test fun lasOperacionesSonLasDelServicio() {
        assertEquals("""{"op":"borrar_articulo","id":"a1"}""", Op.borrarArticulo("a1").toString())
        assertEquals("""{"op":"restaurar_articulo","id":"a1"}""", Op.restaurarArticulo("a1").toString())
    }

    @Test fun borrarSeLlevaLasPublicacionesSinPublicar() = runBlocking {
        repo.borrarArticulo("a1")
        assertEquals(listOf(Op.borrarArticulo("a1")), ops())
        assertEquals(1, dao.articulo("a1")!!.borrado)
        assertEquals(0, dao.articulo("a2")!!.borrado)
        assertEquals(listOf("p-idea", "p-prog", "p-antes"), borradas())
    }

    @Test fun restaurarDevuelveSoloLoQueSeFueConEl() = runBlocking {
        repo.borrarArticulo("a1")
        repo.restaurarArticulo("a1")
        assertEquals(listOf(Op.borrarArticulo("a1"), Op.restaurarArticulo("a1")), ops())
        assertEquals(0, dao.articulo("a1")!!.borrado)
        // La que ya estaba borrada antes sigue borrada.
        assertEquals(listOf("p-antes"), borradas())
    }

    @Test fun repetirNoHaceDano() = runBlocking {
        val local = Local(dao)
        local.aplicar(Op.borrarArticulo("a1"))
        local.aplicar(Op.borrarArticulo("a1"))
        assertEquals(listOf("p-idea", "p-prog", "p-antes"), borradas())
        local.aplicar(Op.restaurarArticulo("a1"))
        local.aplicar(Op.restaurarArticulo("a1"))
        assertEquals(0, dao.articulo("a1")!!.borrado)
        assertEquals(listOf("p-antes"), borradas())
        // Restaurar lo que está vivo, o lo que no existe, no cambia nada.
        local.aplicar(Op.restaurarArticulo("a2"))
        local.aplicar(Op.borrarArticulo("no-existe"))
        assertEquals(listOf("p-antes"), borradas())
    }

    @Test fun restaurarSobreLoQueBajoDelServicio() = runBlocking {
        // El servicio ya aplicó el borrado: artículo y publicaciones con la misma rev.
        dao.articulos(listOf(dao.articulo("a1")!!.copy(borrado = 1, rev = 20)))
        dao.publicaciones(listOf(dao.publicacion("p-idea")!!.copy(borrado = 1, rev = 20), dao.publicacion("p-prog")!!.copy(borrado = 1, rev = 20)))
        Local(dao).aplicar(Op.restaurarArticulo("a1"))
        assertEquals(0, dao.articulo("a1")!!.borrado)
        assertEquals(listOf("p-antes"), borradas())
    }

    @Test fun laColaSaleEnOrdenYSeVacia() = runBlocking {
        val servidor = MockWebServer()
        servidor.start()
        try {
            servidor.enqueue(MockResponse(body = """{"resultados":[{"ok":true},{"ok":true}],"rev":22}"""))
            servidor.enqueue(MockResponse(body = """{"rev":22,"tablas":{}}"""))
            val cliente = Cliente({ Servidor(servidor.url("/").toString(), "x") })
            repo.borrarArticulo("a1")
            repo.restaurarArticulo("a1")
            var rev: Long? = 10
            val r = Sincronizador(base, cliente, { rev }, { rev = it }).sincronizar()
            assertEquals(2, r.enviadas)
            assertEquals(0, r.pendientes)
            val enviado = json.parseToJsonElement(servidor.takeRequest().body!!.utf8()).jsonObject["operaciones"]!!.jsonArray
            assertEquals(listOf("borrar_articulo", "restaurar_articulo"), enviado.map { it.jsonObject.op })
            assertEquals(0, dao.articulo("a1")!!.borrado)
            assertEquals(listOf("p-antes"), borradas())
        } finally {
            servidor.close()
        }
    }
}
