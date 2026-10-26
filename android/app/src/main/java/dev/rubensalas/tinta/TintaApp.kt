package dev.rubensalas.tinta

import android.app.Application
import dev.rubensalas.tinta.data.RepositorioAjustes
import dev.rubensalas.tinta.data.Repositorio
import dev.rubensalas.tinta.data.api.Cliente
import dev.rubensalas.tinta.data.local.BaseLocal
import dev.rubensalas.tinta.data.resumen.Avisos
import dev.rubensalas.tinta.data.sync.Sincronizador
import dev.rubensalas.tinta.data.sync.Sync
import dev.rubensalas.tinta.data.update.ReleaseClient
import dev.rubensalas.tinta.data.update.UpdateChecker
import dev.rubensalas.tinta.data.update.UpdateRepository
import dev.rubensalas.tinta.data.update.Version
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Las piezas compartidas de la app, creadas una vez. Sin biblioteca de inyección. */
class Contenedor(app: Application, scope: CoroutineScope) {
    val ajustes = RepositorioAjustes(app)
    val base = BaseLocal.crear(app)
    val cliente = Cliente(servidor = { ajustes.actual().servidor })
    val sincronizador = Sincronizador(base, cliente, revGuardada = { ajustes.actual().rev }, guardarRev = { ajustes.guardarRev(it) })
    val repositorio = Repositorio(app, base, cliente, scope, pedirSync = { Sync.pronto(app) })
    val actualizaciones = run {
        val releases = ReleaseClient(BuildConfig.RELEASES_API)
        val instalada = Version.parse(BuildConfig.VERSION_NAME) ?: error("VERSION_NAME no es X.Y.Z: ${BuildConfig.VERSION_NAME}")
        UpdateRepository(app, ajustes, releases, UpdateChecker(releases, instalada), scope)
    }
}

open class TintaApp : Application() {
    lateinit var contenedor: Contenedor
        private set

    override fun onCreate() {
        super.onCreate()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        contenedor = Contenedor(this, scope)
        Avisos.crearCanales(this)
        Sync.programarPeriodica(this)
        Avisos.programarComprobacionDiaria(this)
        scope.launch { Avisos.programar(this@TintaApp, contenedor.ajustes.actual().horaResumen) }
    }
}
