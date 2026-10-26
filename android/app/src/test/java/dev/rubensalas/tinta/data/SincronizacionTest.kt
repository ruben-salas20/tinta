package dev.rubensalas.tinta.data

import android.app.Application
import dev.rubensalas.tinta.data.api.Cliente
import dev.rubensalas.tinta.data.api.Servidor
import dev.rubensalas.tinta.data.api.json
import dev.rubensalas.tinta.data.local.BaseLocal
import dev.rubensalas.tinta.data.sync.Sincronizador
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La sincronización de verdad, contra la copia de desarrollo del servicio
 * (`scripts/dev-servidor.sh`). Solo corre con `TINTA_DEV_URL`, por ejemplo:
 *   TINTA_DEV_URL=http://127.0.0.1:8091 android/compilar.sh test
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SincronizacionTest {
    private val url = System.getenv("TINTA_DEV_URL")
    private val clave = System.getenv("TINTA_DEV_CLAVE") ?: "dev-clave"
    private lateinit var base: BaseLocal
    private val enviadas = mutableListOf<String>()
    private var rev: Long? = null

    private lateinit var cliente: Cliente
    private lateinit var sync: Sincronizador
    private lateinit var repo: Repositorio

    @Before fun preparar() {
        assumeTrue("Sin TINTA_DEV_URL: se salta la prueba de integración", !url.isNullOrBlank())
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        base = BaseLocal.enMemoria(ctx)
        // Guarda cada cuerpo enviado a /api/sync para poder reenviarlo tal cual.
        val http = Cliente.httpPorDefecto().newBuilder().addInterceptor { ch ->
            val r = ch.request()
            if (r.method == "POST" && r.url.encodedPath == "/api/sync") {
                val b = Buffer().also { r.body!!.writeTo(it) }
                enviadas += b.readUtf8()
            }
            ch.proceed(r)
        }.build()
        cliente = Cliente({ Servidor(url!!, clave) }, http)
        sync = Sincronizador(base, cliente, { rev }, { rev = it })
        repo = Repositorio(ctx, base, cliente, CoroutineScope(SupervisorJob() + Dispatchers.Default), pedirSync = {})
    }

    @After fun cerrar() {
        if (::base.isInitialized) base.close()
    }

    private val dao get() = base.dao()

    @Test fun crearSinRedSubirReenviarYConflicto() = runBlocking {
        // 1. Bajar todo.
        sync.sincronizar()
        assertNotNull("la primera bajada guarda la rev", rev)

        // 2. Sin red: un artículo, su párrafo y otro bloque, todo en la cola.
        val titulo = "Prueba móvil ${nuevoId().take(6)}"
        val aid = repo.crearArticulo(titulo)
        val ids = json.parseToJsonElement(dao.cola().first().json).jsonObject["ids"]!!.jsonObject
        val idioma = dao.idioma(ids["idioma"]!!.jsonPrimitive.content)!!
        val rama = idioma.ramaPrincipalId!!
        val vs = dao.seccionesDe(rama).single().varianteActivaId!!
        val b1 = dao.bloquesDe(vs).single()
        repo.guardar(b1.varianteActivaId!!, "Escrito sin red")
        val b2 = repo.nuevoBloque(vs, b1.id)
        repo.guardar(dao.bloque(b2)!!.varianteActivaId!!, "Segundo bloque")
        // El guardado del bloque nuevo se fusiona con su creación.
        assertEquals(listOf("crear_articulo", "guardar", "crear_bloque"), dao.cola().map { json.parseToJsonElement(it.json).jsonObject["op"]!!.jsonPrimitive.content })

        // 3. Subir: el guardado del primer párrafo espera a conocer su rev.
        val r = sync.sincronizar()
        assertEquals(0, r.pendientes)
        assertEquals(0, r.descartadas)
        assertTrue("la creación se confirmó", dao.articulo(aid)!!.rev >= 0)
        assertEquals("Escrito sin red", dao.varianteBloque(b1.varianteActivaId)!!.contenido)
        assertEquals("Segundo bloque", dao.varianteBloque(dao.bloque(b2)!!.varianteActivaId!!)!!.contenido)

        // 4. Reenviar las mismas colas: no se duplica nada.
        for (cuerpo in enviadas.toList()) {
            val ops = json.parseToJsonElement(cuerpo).jsonObject["operaciones"]!!.jsonArray
            val res = cliente.subir(ops)
            assertTrue("reenviar debe salir bien: ${res.resultados}", res.resultados.all { it["ok"].toString() == "true" })
        }
        val todo = cliente.bajar(null)
        val mios = todo.tablas["idiomas"]!!.count { (it as JsonObject)["titulo"]!!.jsonPrimitive.content == titulo }
        assertEquals(1, mios)
        val bloques = todo.tablas["bloques"]!!.filter { (it as JsonObject)["variante_seccion_id"]!!.jsonPrimitive.content == vs && it["borrado"].toString() == "0" }
        assertEquals(2, bloques.size)

        // 5. Conflicto: la web cambia el párrafo y el móvil lo edita sobre la versión vieja.
        val vid = b1.varianteActivaId!!
        val revWeb = dao.varianteBloque(vid)!!.rev
        val put = Request.Builder().url("$url/api/variantes-bloque/$vid").header("Authorization", "Bearer $clave")
            .put("""{"contenido":"Cambiado en la web","rev_base":$revWeb}""".toRequestBody("application/json".toMediaType())).build()
        OkHttpClient().newCall(put).execute().use { assertTrue(it.isSuccessful) }
        repo.guardar(vid, "Cambiado en el móvil")
        sync.sincronizar()
        val variantes = dao.variantesDe(b1.id)
        val conflicto = variantes.firstOrNull { it.origen == "conflicto" }
        assertNotNull("debe haber una variante en conflicto", conflicto)
        assertEquals("Cambiado en el móvil", conflicto!!.contenido)
        assertEquals("Cambiado en la web", dao.varianteBloque(vid)!!.contenido)

        // 6. Resolverlo: la del móvil queda activa y nada se pierde.
        repo.resolverConflicto(b1.id, conflicto, quedarseConMovil = true)
        sync.sincronizar()
        val tras = dao.variantesDe(b1.id)
        assertNull(tras.firstOrNull { it.origen == "conflicto" })
        assertEquals(setOf("Cambiado en la web", "Cambiado en el móvil"), tras.map { it.contenido }.toSet())
        assertEquals("Cambiado en el móvil", tras.first { it.id == dao.bloque(b1.id)!!.varianteActivaId }.contenido)

        // Limpieza: borrar el artículo de prueba.
        val del = Request.Builder().url("$url/api/articulos/$aid").header("Authorization", "Bearer $clave").delete().build()
        OkHttpClient().newCall(del).execute().close()
    }
}
