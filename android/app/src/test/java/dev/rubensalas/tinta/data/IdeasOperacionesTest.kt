package dev.rubensalas.tinta.data

import android.app.Application
import dev.rubensalas.tinta.data.api.Cliente
import dev.rubensalas.tinta.data.api.Servidor
import dev.rubensalas.tinta.data.api.json
import dev.rubensalas.tinta.data.local.BaseLocal
import dev.rubensalas.tinta.data.local.PublicacionE
import dev.rubensalas.tinta.data.sync.Local
import dev.rubensalas.tinta.data.sync.Op
import dev.rubensalas.tinta.data.sync.id
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Ideas sin fecha ni canal: crear, poner y quitar fecha y canal, en la copia local y en la cola. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class IdeasOperacionesTest {
    private lateinit var base: BaseLocal
    private lateinit var repo: Repositorio
    private val dao get() = base.dao()

    @Before fun preparar() = runBlocking {
        val ctx = RuntimeEnvironment.getApplication()
        base = BaseLocal.enMemoria(ctx)
        repo = Repositorio(ctx, base, Cliente({ Servidor("http://127.0.0.1:9", "x") }), CoroutineScope(SupervisorJob() + Dispatchers.Default), pedirSync = {})
        dao.publicaciones(listOf(
            PublicacionE("con", "x", "post", "2026-11-01", "18:00", "idea", "Con fecha", creado = "", cambiado = "", rev = 3),
            PublicacionE("idea", null, null, null, null, "idea", "Idea", creado = "", cambiado = "", rev = 3),
        ))
    }

    @After fun cerrar() = base.close()

    @Test fun crearIdeaSinFechaNiCanal() = runBlocking {
        val id = repo.crearPublicacion(null, null, null, null, "Nueva", null)
        val p = dao.publicacion(id)!!
        assertNull(p.canalId)
        assertNull(p.formato)
        assertNull(p.fecha)
        assertEquals("idea", p.estado)
        // La operación que se envía no lleva esos campos.
        val op = dao.cola().single().json.let { json.parseToJsonElement(it).jsonObject["publicacion"]!!.jsonObject }
        assertFalse(op.containsKey("canal_id"))
        assertFalse(op.containsKey("formato"))
        assertFalse(op.containsKey("fecha"))
    }

    @Test fun quitarFechaQuitaLaHora() = runBlocking {
        Local(dao).aplicar(Op.cambiarPublicacion("con", buildJsonObject { put("fecha", JsonNull) }))
        val p = dao.publicacion("con")!!
        assertNull(p.fecha)
        assertNull(p.hora)
    }

    @Test fun quitarCanalQuitaElFormato() = runBlocking {
        Local(dao).aplicar(Op.cambiarPublicacion("con", buildJsonObject { put("canal_id", JsonNull) }))
        val p = dao.publicacion("con")!!
        assertNull(p.canalId)
        assertNull(p.formato)
        assertEquals("2026-11-01", p.fecha)
    }

    @Test fun ponerCanalYFormatoAUnaIdea() = runBlocking {
        Local(dao).aplicar(Op.cambiarPublicacion("idea", buildJsonObject { put("canal_id", "linkedin"); put("formato", "post") }))
        val p = dao.publicacion("idea")!!
        assertEquals("linkedin", p.canalId)
        assertEquals("post", p.formato)
        assertNull(p.fecha)
    }

    @Test fun ponerFechaSinHoraDejaLaHoraVacia() = runBlocking {
        Local(dao).aplicar(Op.cambiarPublicacion("idea", buildJsonObject { put("fecha", "2026-12-01") }))
        assertEquals("2026-12-01", dao.publicacion("idea")!!.fecha)
        assertNull(dao.publicacion("idea")!!.hora)
    }

    @Test fun crearPublicacionLocalSinNulos() = runBlocking {
        val op = Op.crearPublicacion(buildJsonObject { put("titulo", "Sin nada") })
        Local(dao).aplicar(op)
        val p = dao.publicacion(op.id("publicacion"))!!
        assertNull(p.canalId)
        assertNull(p.fecha)
    }
}
