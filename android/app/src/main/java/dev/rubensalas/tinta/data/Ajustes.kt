package dev.rubensalas.tinta.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.rubensalas.tinta.data.api.Servidor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant

data class Ajustes(
    val url: String = URL_POR_DEFECTO,
    val clave: String = "",
    /** La rev de la última bajada; null si aún no se bajó nada. */
    val rev: Long? = null,
    /** Hora del resumen diario, en minutos desde medianoche. */
    val horaResumen: Int = 8 * 60,
    val ultimaSync: Instant? = null,
    val ultimaComprobacion: Instant? = null,
) {
    val servidor get() = Servidor(url, clave)
    val configurada get() = clave.isNotBlank()

    companion object {
        const val URL_POR_DEFECTO = "https://tinta.rubensalas.dev"
    }
}

private val Context.almacen: DataStore<Preferences> by preferencesDataStore(name = "ajustes")

/** Ajustes en el almacenamiento privado de la app, que solo ella puede leer. */
class RepositorioAjustes(context: Context) {
    private val store = context.applicationContext.almacen

    val ajustes: Flow<Ajustes> = store.data.map { p ->
        Ajustes(
            url = p[URL] ?: Ajustes.URL_POR_DEFECTO,
            clave = p[CLAVE] ?: "",
            rev = p[REV],
            horaResumen = p[HORA_RESUMEN] ?: 8 * 60,
            ultimaSync = p[ULTIMA_SYNC]?.let(Instant::ofEpochMilli),
            ultimaComprobacion = p[ULTIMA_COMPROBACION]?.let(Instant::ofEpochMilli),
        )
    }

    suspend fun actual(): Ajustes = ajustes.first()

    suspend fun guardarServidor(url: String, clave: String) = store.edit {
        // Otro servicio, otra copia: hay que bajarla entera.
        if (it[URL] != url.trim() || it[CLAVE] != clave.trim()) it.remove(REV)
        it[URL] = url.trim()
        it[CLAVE] = clave.trim()
    }
    suspend fun guardarRev(rev: Long) = store.edit {
        it[REV] = rev
        it[ULTIMA_SYNC] = System.currentTimeMillis()
    }
    suspend fun guardarHoraResumen(minutos: Int) = store.edit { it[HORA_RESUMEN] = minutos.coerceIn(0, 24 * 60 - 1) }
    suspend fun marcarComprobacion(at: Instant = Instant.now()) = store.edit { it[ULTIMA_COMPROBACION] = at.toEpochMilli() }

    private companion object {
        val URL = stringPreferencesKey("url")
        val CLAVE = stringPreferencesKey("clave")
        val REV = longPreferencesKey("rev")
        val HORA_RESUMEN = intPreferencesKey("hora_resumen")
        val ULTIMA_SYNC = longPreferencesKey("ultima_sync")
        val ULTIMA_COMPROBACION = longPreferencesKey("ultima_comprobacion")
    }
}
