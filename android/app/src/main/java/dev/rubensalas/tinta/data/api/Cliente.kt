package dev.rubensalas.tinta.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.long
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Un fallo al hablar con el servicio. [estado] es null si no hubo respuesta (sin red). */
class ApiException(val estado: Int?, message: String, cause: Throwable? = null) : Exception(message, cause)

data class Servidor(val url: String, val clave: String)

/** Lo que baja `GET /api/sync`: cada tabla, como filas JSON, y la rev actual. */
data class Bajada(val rev: Long, val tablas: Map<String, JsonArray>)

/** Lo que responde `POST /api/sync`: un resultado por operación, en orden. */
data class Subida(val resultados: List<JsonObject>, val rev: Long)

@Serializable
data class EntradaHistorial(val id: Long, val contenido: String, val origen: String, val dispositivo: String, val creado: String)

val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

/** Habla con el servicio de Tinta. Todo lo que no es `/health` lleva la clave de la app. */
class Cliente(
    private val servidor: suspend () -> Servidor,
    private val http: OkHttpClient = httpPorDefecto(),
) {
    suspend fun probar() {
        pedir("GET", "/api/sesion")
    }

    suspend fun bajar(desde: Long?): Bajada {
        val o = json.parseToJsonElement(pedir("GET", "/api/sync" + (desde?.let { "?desde=$it" } ?: ""))) as JsonObject
        val tablas = (o["tablas"] as JsonObject).mapValues { it.value as JsonArray }
        return Bajada(o["rev"]!!.jsonPrimitive.long, tablas)
    }

    suspend fun subir(operaciones: List<JsonElement>): Subida {
        val cuerpo = buildJsonObject { put("operaciones", JsonArray(operaciones)) }.toString()
        val o = json.parseToJsonElement(pedir("POST", "/api/sync", cuerpo.toRequestBody(JSON))) as JsonObject
        return Subida((o["resultados"] as JsonArray).map { it as JsonObject }, o["rev"]!!.jsonPrimitive.long)
    }

    /** Sube un archivo con el id que eligió el móvil. Si ya existe, el servicio no lo repite. */
    suspend fun subirMedio(id: String, archivo: File, nombre: String, tipo: String) {
        val cuerpo = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("id", id)
            .addFormDataPart("archivo", nombre, archivo.asRequestBody(tipo.toMediaType()))
            .build()
        pedir("POST", "/api/medios", cuerpo)
    }

    suspend fun historial(varianteId: String): List<EntradaHistorial> =
        json.decodeFromString(pedir("GET", "/api/variantes-bloque/$varianteId/historial"))

    suspend fun restaurar(varianteId: String, entrada: Long) {
        val cuerpo = buildJsonObject { put("entrada", entrada) }.toString().toRequestBody(JSON)
        pedir("POST", "/api/variantes-bloque/$varianteId/restaurar", cuerpo)
    }

    /** Dirección de un medio y la cabecera para pedirlo (la usa Coil). */
    suspend fun medio(id: String): Pair<String, String> {
        val s = servidor()
        return "${s.url.trimEnd('/')}/api/medios/$id" to "Bearer ${s.clave}"
    }

    private suspend fun pedir(metodo: String, ruta: String, cuerpo: RequestBody? = null): String = withContext(Dispatchers.IO) {
        val s = servidor()
        if (s.clave.isBlank()) throw ApiException(401, "Falta la clave de la app")
        val req = Request.Builder()
            .url(s.url.trimEnd('/') + ruta)
            .header("Authorization", "Bearer ${s.clave}")
            .header("X-Tinta-Dispositivo", "android")
            .method(metodo, cuerpo)
            .build()
        try {
            http.newCall(req).execute().use { r ->
                val texto = r.body.string()
                if (!r.isSuccessful) {
                    val msg = runCatching { (json.parseToJsonElement(texto) as JsonObject)["error"]!!.jsonPrimitive.content }.getOrNull()
                    throw ApiException(r.code, when (r.code) {
                        401 -> "El servicio no acepta la clave"
                        else -> msg ?: "El servicio respondió ${r.code}"
                    })
                }
                texto
            }
        } catch (e: IOException) {
            throw ApiException(null, "No se pudo conectar con el servicio", e)
        } catch (e: IllegalArgumentException) {
            throw ApiException(null, "La dirección del servicio no es válida", e)
        }
    }

    companion object {
        private val JSON = "application/json".toMediaType()

        fun httpPorDefecto(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }
}
