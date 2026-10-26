package dev.rubensalas.tinta.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.rubensalas.tinta.TintaApp
import dev.rubensalas.tinta.data.api.ApiException
import java.util.concurrent.TimeUnit

/** Sincroniza en segundo plano. WorkManager solo lo arranca con red. */
class TrabajoSync(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as TintaApp).contenedor
        if (!c.ajustes.actual().configurada) return Result.success()
        return try {
            c.sincronizador.sincronizar()
            Result.success()
        } catch (e: ApiException) {
            // Una clave mala no se arregla sola; lo demás, sí.
            if (e.estado == 401) Result.failure() else Result.retry()
        }
    }
}

/** Cuándo se sincroniza: cada 15 minutos, al abrir la app, al volver la red y poco después de cada cambio. */
object Sync {
    private const val PERIODICA = "sync-periodica"
    private const val PRONTO = "sync-pronto"

    private val conRed = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun programarPeriodica(context: Context) {
        val r = PeriodicWorkRequestBuilder<TrabajoSync>(15, TimeUnit.MINUTES)
            .setConstraints(conRed)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODICA, ExistingPeriodicWorkPolicy.KEEP, r)
    }

    /**
     * Sincroniza en cuanto haya red, tras [esperaSegundos]. Cada cambio nuevo
     * reinicia la espera: varios toques seguidos viajan juntos. Reemplazar una
     * sincronización a medias es seguro: reenviar la cola no duplica nada.
     */
    fun pronto(context: Context, esperaSegundos: Long = 3) {
        val r = OneTimeWorkRequestBuilder<TrabajoSync>()
            .setConstraints(conRed)
            .setInitialDelay(esperaSegundos, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(PRONTO, ExistingWorkPolicy.REPLACE, r)
    }
}
