package dev.rubensalas.tinta.data.resumen

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.rubensalas.tinta.MainActivity
import dev.rubensalas.tinta.R
import dev.rubensalas.tinta.TintaApp
import dev.rubensalas.tinta.data.update.UpdateCheck
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

object Avisos {
    private const val CANAL_RESUMEN = "resumen"
    private const val CANAL_VERSION = "version"

    fun crearCanales(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CANAL_RESUMEN, "Resumen diario", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CANAL_VERSION, "Versiones nuevas", NotificationManager.IMPORTANCE_LOW))
    }

    /** El próximo momento a [minutos] desde medianoche, hoy si aún no pasó, si no mañana. */
    fun proximo(ahora: LocalDateTime, minutos: Int): LocalDateTime {
        val hoy = ahora.toLocalDate().atStartOfDay().plusMinutes(minutos.toLong())
        return if (hoy.isAfter(ahora)) hoy else hoy.plusDays(1)
    }

    /**
     * Programa el próximo resumen. La alarma es inexacta (una ventana de 15
     * minutos): no pide el permiso de alarmas exactas y basta para un resumen.
     */
    fun programar(context: Context, minutos: Int) {
        val am = context.getSystemService(AlarmManager::class.java)
        val cuando = proximo(LocalDateTime.now(), minutos).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        am.setWindow(AlarmManager.RTC_WAKEUP, cuando, 15 * 60 * 1000L, intentResumen(context))
    }

    private fun intentResumen(context: Context) = PendingIntent.getBroadcast(
        context, 1, Intent(context, ResumenReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun abrirApp(context: Context) = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun puede(context: Context) =
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun mostrarResumen(context: Context, r: Resumen, nombreCanal: (String?) -> String) {
        if (!puede(context)) return
        val estilo = android.app.Notification.InboxStyle()
        r.lineas(nombreCanal).take(6).forEach { estilo.addLine(it) }
        if (r.manana.isNotEmpty()) estilo.setSummaryText("Mañana: ${Resumen.plural(r.manana.size, "publicación", "publicaciones")}")
        val n = android.app.Notification.Builder(context, CANAL_RESUMEN)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle(r.titulo)
            .setContentText(r.lineas(nombreCanal).firstOrNull() ?: "Abre Tinta para ver la semana")
            .setStyle(estilo)
            .setContentIntent(abrirApp(context))
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(1, n)
    }

    fun mostrarVersion(context: Context, version: String) {
        if (!puede(context)) return
        val n = android.app.Notification.Builder(context, CANAL_VERSION)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle("Tinta $version disponible")
            .setContentText("Abre Tinta y pulsa Actualizar")
            .setContentIntent(abrirApp(context))
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(2, n)
    }

    fun programarComprobacionDiaria(context: Context) {
        val r = PeriodicWorkRequestBuilder<TrabajoVersion>(24, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("version", ExistingPeriodicWorkPolicy.KEEP, r)
    }
}

/** Salta a la hora del resumen: avisa y programa el de mañana. */
class ResumenReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val c = (context.applicationContext as TintaApp).contenedor
        val fin = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val pubs = c.base.dao().todasPublicaciones()
                val datos = c.repositorio.datos.value
                val r = Resumen.calcular(pubs, LocalDate.now())
                Avisos.mostrarResumen(context, r) { id -> id?.let { datos.canal(it)?.nombre ?: it } ?: "Sin canal" }
                Avisos.programar(context, c.ajustes.actual().horaResumen)
            } finally {
                fin.finish()
            }
        }
    }
}

/** Al encender el móvil, las alarmas se pierden: se vuelve a programar el resumen. */
class ArranqueReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val c = (context.applicationContext as TintaApp).contenedor
        val fin = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                Avisos.programar(context, c.ajustes.actual().horaResumen)
            } finally {
                fin.finish()
            }
        }
    }
}

/** Una vez al día: si hay versión nueva en GitHub Releases, lo avisa. */
class TrabajoVersion(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as TintaApp).contenedor
        val r = c.actualizaciones.checkNow()
        if (r is UpdateCheck.Available) Avisos.mostrarVersion(applicationContext, r.release.version.toString())
        return Result.success()
    }
}
