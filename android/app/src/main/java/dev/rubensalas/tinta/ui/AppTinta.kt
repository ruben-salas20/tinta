package dev.rubensalas.tinta.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import dev.rubensalas.tinta.BuildConfig
import dev.rubensalas.tinta.Contenedor
import dev.rubensalas.tinta.data.Ajustes
import dev.rubensalas.tinta.data.api.ApiException
import dev.rubensalas.tinta.data.api.EntradaHistorial
import dev.rubensalas.tinta.data.local.Parte
import dev.rubensalas.tinta.data.resumen.Avisos
import dev.rubensalas.tinta.data.sync.Sync
import dev.rubensalas.tinta.data.texto.Critic
import dev.rubensalas.tinta.data.update.UpdateCheck
import dev.rubensalas.tinta.ui.actualizacion.UpdateBanner
import dev.rubensalas.tinta.ui.actualizacion.startUpdate
import dev.rubensalas.tinta.ui.ajustes.PantallaAjustes
import dev.rubensalas.tinta.ui.ajustes.PantallaInicio
import dev.rubensalas.tinta.ui.articulos.AvisoDeshacer
import dev.rubensalas.tinta.ui.articulos.DialogoEliminarArticulo
import dev.rubensalas.tinta.ui.articulos.PantallaArticulos
import dev.rubensalas.tinta.ui.articulos.ofrecerDeshacer
import dev.rubensalas.tinta.ui.calendario.AccionesPublicacion
import dev.rubensalas.tinta.ui.calendario.DialogoNuevaPublicacion
import dev.rubensalas.tinta.ui.calendario.PantallaCalendario
import dev.rubensalas.tinta.ui.calendario.PantallaPublicacion
import dev.rubensalas.tinta.ui.comun.BarraInferior
import dev.rubensalas.tinta.ui.comun.Seccion
import dev.rubensalas.tinta.ui.editor.AccionesEditor
import dev.rubensalas.tinta.ui.editor.PantallaConflicto
import dev.rubensalas.tinta.ui.editor.PantallaEditor
import dev.rubensalas.tinta.ui.hoy.PantallaHoy
import dev.rubensalas.tinta.ui.tema.Tinta
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.time.LocalDate

/** ¿Hay red ahora? Se actualiza cuando Android avisa de un cambio. */
@Composable
fun rememberSinRed(context: Context): Boolean {
    val cm = remember { context.getSystemService(ConnectivityManager::class.java) }
    fun ahora() = cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) != true
    var sinRed by remember { mutableStateOf(ahora()) }
    DisposableEffect(cm) {
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { sinRed = false }
            override fun onLost(network: Network) { sinRed = ahora() }
        }
        cm.registerDefaultNetworkCallback(cb)
        onDispose { cm.unregisterNetworkCallback(cb) }
    }
    return sinRed
}

/** El modelo que Coil pinta para un medio: el archivo local si aún no se subió, si no el del servicio. */
fun modeloImagen(context: Context, ajustes: Ajustes, medioId: String): Any {
    val local = File(context.filesDir, "medios/$medioId")
    if (local.exists()) return local
    return ImageRequest.Builder(context)
        .data("${ajustes.url.trimEnd('/')}/api/medios/$medioId")
        .httpHeaders(NetworkHeaders.Builder().set("Authorization", "Bearer ${ajustes.clave}").build())
        .build()
}

@Composable
fun AppTinta(c: Contenedor) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ajustes by c.ajustes.ajustes.collectAsStateWithLifecycle(initialValue = null)
    val datos by c.repositorio.datos.collectAsStateWithLifecycle()
    val avisos by c.repositorio.avisos.collectAsStateWithLifecycle(initialValue = emptyList())
    val update by c.actualizaciones.state.collectAsStateWithLifecycle()
    val sinRed = rememberSinRed(context)
    val hoy = LocalDate.now()
    val nav = rememberNavController()

    // La galería: quien pide una imagen deja aquí qué hacer con su id.
    var alElegir by remember { mutableStateOf<((String) -> Unit)?>(null) }
    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val cb = alElegir
        alElegir = null
        if (uri != null && cb != null) scope.launch { cb(c.repositorio.guardarImagen(uri)) }
    }
    fun elegirImagen(cb: (String) -> Unit) {
        alElegir = cb
        galeria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    // El audio no va por la galería: el selector del sistema muestra archivos de audio.
    var alElegirAudio by remember { mutableStateOf<((String, String) -> Unit)?>(null) }
    val selectorAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val cb = alElegirAudio
        alElegirAudio = null
        if (uri != null && cb != null) scope.launch {
            val medio = c.repositorio.guardarMedio(uri, "audio/mpeg", "audio")
            cb(medio.id, medio.nombre)
        }
    }
    fun elegirAudio(cb: (String, String) -> Unit) {
        alElegirAudio = cb
        selectorAudio.launch(arrayOf("audio/*"))
    }
    val permiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        permiso.launch(Manifest.permission.POST_NOTIFICATIONS)
        c.actualizaciones.checkOnOpen()
        Sync.pronto(context, 0)
    }

    val a = ajustes ?: return
    if (!a.configurada) {
        var error by remember { mutableStateOf<String?>(null) }
        var probando by remember { mutableStateOf(false) }
        PantallaInicio(a.url, { url, clave ->
            probando = true
            scope.launch {
                c.ajustes.guardarServidor(url, clave)
                try {
                    c.cliente.probar()
                    error = null
                    Sync.pronto(context, 0)
                } catch (e: ApiException) {
                    error = e.message
                    c.ajustes.guardarServidor(url, "")
                } finally {
                    probando = false
                }
            }
        }, error, probando)
        return
    }

    val imagen: (String) -> Any? = { id -> modeloImagen(context, a, id) }
    fun copiar(texto: String) {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Tinta", texto))
        Toast.makeText(context, "Copiado", Toast.LENGTH_SHORT).show()
    }

    val repo = c.repositorio
    val accionesEditor = remember {
        object : AccionesEditor {
            override suspend fun guardar(varianteId: String, markup: String) = repo.guardar(varianteId, markup)
            override suspend fun nuevoBloque(vsId: String, despuesDe: String?, tipo: String, contenido: String) = repo.nuevoBloque(vsId, despuesDe, tipo, contenido)
            override suspend fun borrarBloque(id: String) = repo.borrarBloque(id)
            override suspend fun fork(bloqueId: String, contenido: String) = repo.forkBloque(bloqueId, contenido)
            override suspend fun activar(bloqueId: String, varianteId: String) = repo.activarVariante(bloqueId, varianteId)
            override suspend fun descartarVariante(varianteId: String) = repo.borrarVariante(varianteId)
            override suspend fun cambiarTitulo(idiomaId: String, titulo: String) = repo.cambiarTitulo(idiomaId, titulo)
            override suspend fun cambiarEstado(articuloId: String, estado: String) = repo.cambiarEstado(articuloId, estado)
            override suspend fun historial(varianteId: String): List<EntradaHistorial> = repo.historial(varianteId)
            override suspend fun restaurar(varianteId: String, entrada: Long) = repo.restaurar(varianteId, entrada)
            override fun elegirImagen(onElegida: (String) -> Unit) = elegirImagen(onElegida)
            override fun elegirAudio(onElegido: (String, String) -> Unit) = elegirAudio(onElegido)
            override suspend fun nuevaSeccion(ramaId: String, despuesDe: String?, titulo: String) = repo.nuevaSeccion(ramaId, despuesDe, titulo)
            override suspend fun cambiarTituloSeccion(vsId: String, titulo: String) = repo.cambiarTituloSeccion(vsId, titulo)
            override suspend fun borrarSeccion(id: String) = repo.borrarSeccion(id)
            override suspend fun moverSeccion(id: String, antes: String?, despues: String?) = repo.moverSeccion(id, antes, despues)
            override suspend fun moverBloque(id: String, antes: String?, despues: String?) = repo.moverBloque(id, antes, despues)
            override suspend fun cambiarTipoBloque(id: String, tipo: String) = repo.cambiarTipoBloque(id, tipo)
            override suspend fun forkSeccion(seccionId: String, desde: String?, activar: Boolean) = repo.forkSeccion(seccionId, desde, activar)
            override suspend fun activarVarianteSeccion(seccionId: String, vsId: String) = repo.activarVarianteSeccion(seccionId, vsId)
            override suspend fun borrarVarianteSeccion(id: String) = repo.borrarVarianteSeccion(id)
            override suspend fun forkRama(ramaId: String, nombre: String) = repo.forkRama(ramaId, nombre)
            override suspend fun renombrarRama(id: String, nombre: String) = repo.renombrarRama(id, nombre)
            override suspend fun borrarRama(id: String) = repo.borrarRama(id)
            override suspend fun hacerPrincipal(idiomaId: String, ramaId: String) = repo.hacerPrincipal(idiomaId, ramaId)
            override suspend fun crearIdioma(articuloId: String, idioma: String, copiarDe: String?) = repo.crearIdioma(articuloId, idioma, copiarDe)
            override suspend fun borrarIdioma(id: String) = repo.borrarIdioma(id)
            override suspend fun marcarTraducidoIa(idiomaId: String, si: Boolean) = repo.marcarTraducidoIa(idiomaId, si)
            override suspend fun cambiarDescripcion(idiomaId: String, descripcion: String) = repo.cambiarDescripcion(idiomaId, descripcion)
            override suspend fun cambiarEtiquetas(articuloId: String, nombres: List<String>) = repo.cambiarEtiquetas(articuloId, nombres)
            override suspend fun cambiarSerie(articuloId: String, serieId: String?, parte: Long?) = repo.cambiarSerie(articuloId, serieId, parte)
            override suspend fun crearSerie(nombre: String) = repo.crearSerie(nombre)
        }
    }

    val ruta = nav.currentBackStackEntryAsState().value?.destination?.route
    val seccion = Seccion.entries.firstOrNull { it.name == ruta }
    var nuevaPublicacion by remember { mutableStateOf(false) }
    var porEliminar by remember { mutableStateOf<String?>(null) }
    val avisoAbajo = remember { SnackbarHostState() }

    // Eliminar desde el editor vuelve a la lista. Deshacer encola restaurar.
    fun eliminar(id: String) {
        if (nav.currentBackStackEntry?.destination?.route == "editor/{id}") nav.popBackStack()
        scope.launch {
            repo.borrarArticulo(id)
            avisoAbajo.currentSnackbarData?.dismiss()
            if (avisoAbajo.ofrecerDeshacer()) repo.restaurarArticulo(id)
        }
    }

    fun nuevoArticulo() = scope.launch { nav.navigate("editor/${repo.crearArticulo("Sin título")}") }

    Column(Modifier.fillMaxSize().background(Tinta.colores.papel)) {
        Box(Modifier.weight(1f)) {
            NavHost(nav, startDestination = Seccion.Hoy.name) {
                composable(Seccion.Hoy.name) {
                    PantallaHoy(
                        datos, hoy, sinRed,
                        aviso = { UpdateBanner(update, { startUpdate(context, c.actualizaciones) }, { c.actualizaciones.closeBanner() }) },
                        onArticulo = { nav.navigate("editor/$it") },
                        onPublicacion = { nav.navigate("publicacion/$it") },
                        onNuevoArticulo = { nuevoArticulo() },
                    )
                }
                composable(Seccion.Articulos.name) {
                    PantallaArticulos(datos, hoy, onArticulo = { nav.navigate("editor/$it") }, onNuevo = { nuevoArticulo() }, onEliminar = { porEliminar = it })
                }
                composable(Seccion.Calendario.name) {
                    PantallaCalendario(
                        datos, hoy, onPublicacion = { nav.navigate("publicacion/$it") }, onNueva = { nuevaPublicacion = true },
                        onNuevaIdea = { scope.launch { nav.navigate("publicacion/${repo.crearPublicacion(null, null, null, null, "", null)}") } },
                    )
                }
                composable(Seccion.Ajustes.name) {
                    var estadoVersion by remember { mutableStateOf<String?>(null) }
                    PantallaAjustes(
                        a, BuildConfig.VERSION_NAME, datos.pendientes, avisos, estadoVersion,
                        onGuardarServidor = { url, clave ->
                            scope.launch {
                                c.ajustes.guardarServidor(url, clave)
                                val r = runCatching { c.cliente.probar() }
                                Toast.makeText(context, r.exceptionOrNull()?.message ?: "Conectado", Toast.LENGTH_SHORT).show()
                                Sync.pronto(context, 0)
                            }
                        },
                        onHora = { m -> scope.launch { c.ajustes.guardarHoraResumen(m); Avisos.programar(context, m) } },
                        onSincronizar = {
                            scope.launch {
                                val r = runCatching { c.sincronizador.sincronizar() }
                                Toast.makeText(context, r.exceptionOrNull()?.message ?: "Sincronizado", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onBuscarVersion = {
                            scope.launch {
                                estadoVersion = when (val r = c.actualizaciones.checkNow()) {
                                    UpdateCheck.UpToDate -> "Tienes la última versión"
                                    is UpdateCheck.Available -> "Tinta ${r.release.version} disponible: pulsa Actualizar en Hoy".also { startUpdate(context, c.actualizaciones) }
                                    is UpdateCheck.Failed -> r.message
                                }
                            }
                        },
                        onBorrarAvisos = { scope.launch { repo.borrarAvisos() } },
                    )
                }
                composable("editor/{id}") { e ->
                    val id = e.arguments?.getString("id").orEmpty()
                    PantallaEditor(datos, id, sinRed, accionesEditor, imagen, onVolver = { nav.popBackStack() }, onConflicto = { nav.navigate("conflicto/$it") }, onEliminar = { porEliminar = id })
                }
                composable("conflicto/{bloque}") { e ->
                    val bid = e.arguments?.getString("bloque").orEmpty()
                    val b = datos.documentoDeBloque(bid)
                    if (b == null) nav.popBackStack() else PantallaConflicto(
                        b,
                        onQuedarseConLaQueLlego = { scope.launch { repo.resolverConflicto(bid, b.conflicto!!, true) }; nav.popBackStack() },
                        onQuedarseConLaQueEstaba = { scope.launch { repo.resolverConflicto(bid, b.conflicto!!, false) }; nav.popBackStack() },
                        onMasTarde = { nav.popBackStack() },
                    )
                }
                composable("publicacion/{id}") { e ->
                    val id = e.arguments?.getString("id").orEmpty()
                    PantallaPublicacion(datos, id, accionesPublicacion(c, id, scope, ::copiar, ::elegirImagen, nav), imagen, onVolver = { nav.popBackStack() })
                }
            }
            // Encima del botón «+» (56 dp más su margen de 18), para no taparlo.
            val conBoton = seccion != null && seccion != Seccion.Ajustes
            SnackbarHost(
                avisoAbajo,
                Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = if (conBoton) 82.dp else 8.dp),
            ) { AvisoDeshacer(it) }
        }
        if (seccion != null) BarraInferior(seccion) { s ->
            nav.navigate(s.name) { popUpTo(Seccion.Hoy.name); launchSingleTop = true }
        }
    }
    porEliminar?.let { id ->
        DialogoEliminarArticulo(datos.titulo(id), onEliminar = { porEliminar = null; eliminar(id) }, onCancelar = { porEliminar = null })
    }
    if (nuevaPublicacion) DialogoNuevaPublicacion(datos.canales, hoy, { canal, formato, fecha ->
        nuevaPublicacion = false
        scope.launch { nav.navigate("publicacion/${repo.crearPublicacion(canal, formato, fecha, null, "", null)}") }
    }, { nuevaPublicacion = false })
}

private fun accionesPublicacion(
    c: Contenedor, id: String, scope: kotlinx.coroutines.CoroutineScope, copiar: (String) -> Unit,
    elegir: ((String) -> Unit) -> Unit, nav: NavHostController,
) = object : AccionesPublicacion {
    override fun cambiar(campos: Map<String, String?>) {
        scope.launch {
            c.repositorio.cambiarPublicacion(id, JsonObject(campos.mapValues { (_, v) -> v?.let(::JsonPrimitive) ?: JsonNull }))
        }
    }
    override fun partes(partes: List<Parte>) { scope.launch { c.repositorio.cambiarPartes(id, partes) } }
    override fun partesYCambiar(partes: List<Parte>, campos: Map<String, String?>) {
        scope.launch {
            c.repositorio.cambiarPartes(id, partes)
            c.repositorio.cambiarPublicacion(id, JsonObject(campos.mapValues { (_, v) -> v?.let(::JsonPrimitive) ?: JsonNull }))
        }
    }
    override fun publicada(enlace: String) { scope.launch { c.repositorio.marcarPublicada(id, enlace) } }
    override fun borrar() { scope.launch { c.repositorio.borrarPublicacion(id) }; nav.popBackStack() }
    override fun copiar(texto: String) = copiar(Critic.limpio(texto))
    override fun elegirImagen(onElegida: (String) -> Unit) = elegir(onElegida)
    override fun abrirArticulo(id: String) { nav.navigate("editor/$id") }
}
