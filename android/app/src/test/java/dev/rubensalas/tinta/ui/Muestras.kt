package dev.rubensalas.tinta.ui

import dev.rubensalas.tinta.data.Datos
import dev.rubensalas.tinta.data.api.EntradaHistorial
import dev.rubensalas.tinta.data.local.ArticuloE
import dev.rubensalas.tinta.data.local.BloqueE
import dev.rubensalas.tinta.data.local.CanalE
import dev.rubensalas.tinta.data.local.Formato
import dev.rubensalas.tinta.data.local.IdiomaE
import dev.rubensalas.tinta.data.local.Parte
import dev.rubensalas.tinta.data.local.PublicacionE
import dev.rubensalas.tinta.data.local.RamaE
import dev.rubensalas.tinta.data.local.SeccionE
import dev.rubensalas.tinta.data.local.SerieE
import dev.rubensalas.tinta.data.local.VarianteBloqueE
import dev.rubensalas.tinta.data.local.VarianteSeccionE
import dev.rubensalas.tinta.ui.calendario.AccionesPublicacion
import dev.rubensalas.tinta.ui.editor.AccionesEditor
import java.time.LocalDate

/** Datos como los de las maquetas de diseno/movil.html, para las capturas. */
object Muestras {
    val hoy: LocalDate = LocalDate.parse("2026-10-26")

    private fun a(id: String, estado: String, cambiado: String, serie: String? = null, parte: Long? = null) =
        ArticuloE(id, id, estado, serie, parte, null, "2026-10-01", cambiado, 1)

    private fun i(art: String, titulo: String, desc: String) = IdiomaE("i-$art", art, "es", titulo, desc, ramaPrincipalId = "r-$art", rev = 1)
    private fun vb(id: String, b: String, cont: String, origen: String = "tu", creado: String = "2026-10-25T10:00:00Z") =
        VarianteBloqueE(id, b, cont, origen, creado, creado, 1)

    private fun pub(id: String, canal: String, fecha: String, estado: String, titulo: String, hora: String? = null, formato: String = "post", partes: List<Parte> = listOf(Parte())) =
        PublicacionE(id, canal, formato, fecha, hora, estado, titulo, partes, creado = "", cambiado = "", rev = 1)

    val canales = listOf(
        CanalE("x", "X", "x", listOf(Formato("post", "Post", 280), Formato("hilo", "Hilo", 280, true), Formato("articulo", "Artículo")), 1, "a", 1),
        CanalE("linkedin", "LinkedIn", "linkedin", listOf(Formato("post", "Post", 3000)), 1, "b", 1),
        CanalE("instagram", "Instagram", "instagram", listOf(Formato("post", "Post", 2200), Formato("carrusel", "Carrusel", 2200)), 1, "c", 1),
        CanalE("portfolio", "Portfolio", "portfolio", listOf(Formato("articulo", "Artículo")), 1, "d", 1),
    )

    val hilo = pub(
        "p-hilo", "x", "2026-11-02", "preparando", "Hilo: llama-swap en 4 posts", "18:00", "hilo",
        listOf(
            Parte("Tenía un contenedor por cada modelo de IA en mi torre. Con 6 GB de VRAM solo cabía uno a la vez, y yo hacía de semáforo."),
            Parte("Lo que no esperaba: como ya no pienso en qué está encendido, pruebo modelos nuevos mucho más a menudo. Añadir uno son cinco líneas de YAML. Antes era una tarde entera de pelearme con Docker, puertos y volúmenes, y casi siempre lo dejaba para otro día."),
            Parte("Lo escribí entero, con la configuración, en mi web."),
        ),
    ).copy(articuloId = "llama")

    val datos = Datos(
        series = listOf(SerieE("s1", "Montando ai-lab", 1)),
        articulos = listOf(
            a("llama", "borrador", "2026-10-26T09:00:00Z", "s1", 2),
            a("agentes", "revision", "2026-10-25T09:00:00Z"),
            a("atalaya", "listo", "2026-10-20T09:00:00Z", "s1", 3),
            a("copias", "idea", "2026-10-18T09:00:00Z"),
        ),
        idiomas = listOf(
            i("llama", "llama-swap: un contenedor para todos mis modelos", "Cómo dejé de tener un servicio por modelo y por qué la GPU ahora descansa cuando no la uso."),
            i("agentes", "Lo que aprendí dirigiendo agentes durante un mes", "Qué cambió en cómo pido las cosas, y qué sigo haciendo mal."),
            i("atalaya", "Atalaya: leer el mundo en mi idioma", "Un lector RSS que traduce en la torre, sin mandar nada fuera."),
            i("copias", "Copias de seguridad que de verdad se pueden restaurar", "Idea: probar la restauración cada mes, no solo la copia."),
        ),
        ramas = listOf(RamaE("r-llama", "i-llama", "principal", creado = "", rev = 1)),
        secciones = listOf(SeccionE("s-a", "r-llama", "V", "vs-a", 1), SeccionE("s-b", "r-llama", "k", "vs-b", 1)),
        variantesSeccion = listOf(
            VarianteSeccionE("vs-a", "s-a", "Por qué no un contenedor por modelo", creado = "1", rev = 1),
            VarianteSeccionE("vs-b", "s-b", "Lo que aprendí", creado = "1", rev = 1),
        ),
        bloques = listOf(
            BloqueE("b1", "vs-a", "V", varianteActivaId = "v1", rev = 1),
            BloqueE("b2", "vs-a", "k", varianteActivaId = "v2a", rev = 1),
            BloqueE("b3", "vs-b", "V", varianteActivaId = "v3", rev = 1),
        ),
        variantesBloque = listOf(
            vb("v1", "b1", "llama-swap cambia eso: {--es un programa muy sencillo que--} arranca el modelo que pide cada petición y {~~apaga~>descarga~~}{>>ia:claude<<} el anterior."),
            vb("v2a", "b2", "La consecuencia que no esperaba fue otra: como ya no tengo que pensar en qué está encendido, pruebo modelos nuevos mucho más a menudo.", creado = "2026-10-25T10:42:00Z"),
            vb("v2b", "b2", "Lo mejor no fue ahorrar VRAM, sino dejar de tener miedo a probar modelos nuevos.", creado = "2026-10-25T11:00:00Z"),
            vb("v2c", "b2", "El efecto secundario más valioso: probar un modelo nuevo pasó de ser un proyecto a ser un rato.", "ia:claude", "2026-10-25T12:00:00Z"),
            vb("v3", "b3", "No hace falta la máquina más grande. Hace falta que la máquina pequeña {==no desperdicie nada==}{>>ia:claude: ¿Un ejemplo con cifras de VRAM?<<}.", creado = "2026-10-26T09:02:00Z"),
            vb("v3c", "b3", "No hace falta la máquina más grande. Hace falta que la máquina pequeña no tire nada.", "conflicto", "2026-10-26T08:15:00Z"),
        ),
        canales = canales,
        publicaciones = listOf(
            pub("p1", "instagram", "2026-10-26", "programada", "Carrusel: la escena pixel art", "12:00"),
            pub("p2", "linkedin", "2026-10-27", "preparando", "Lo que cambió al dirigir agentes", "09:00"),
            pub("p3", "x", "2026-10-29", "programada", "X Article: dirigir agentes", "18:00"),
            pub("p4", "x", "2026-10-30", "idea", "Post: una pregunta sobre MCP"),
            pub("p5", "portfolio", "2026-11-02", "programada", "llama-swap: un contenedor…").copy(articuloId = "llama"),
            hilo,
            pub("p6", "x", "2026-10-22", "publicada", "Por qué monté una torre en casa"),
            pub("p7", "linkedin", "2026-10-24", "preparando", "Montando ai-lab: el hardware"),
        ),
        pendientes = 3,
    )

    val accionesEditor = object : AccionesEditor {
        override suspend fun guardar(varianteId: String, markup: String) = Unit
        override suspend fun nuevoBloque(vsId: String, despuesDe: String?, tipo: String, contenido: String) = ""
        override suspend fun borrarBloque(id: String) = Unit
        override suspend fun fork(bloqueId: String, contenido: String) = ""
        override suspend fun activar(bloqueId: String, varianteId: String) = Unit
        override suspend fun descartarVariante(varianteId: String) = Unit
        override suspend fun cambiarTitulo(idiomaId: String, titulo: String) = Unit
        override suspend fun cambiarEstado(articuloId: String, estado: String) = Unit
        override suspend fun historial(varianteId: String) = emptyList<EntradaHistorial>()
        override suspend fun restaurar(varianteId: String, entrada: Long) = Unit
        override fun elegirImagen(onElegida: (String) -> Unit) = Unit
        override suspend fun nuevaSeccion(ramaId: String, despuesDe: String?, titulo: String) = ""
        override suspend fun cambiarTituloSeccion(vsId: String, titulo: String) = Unit
        override suspend fun borrarSeccion(id: String) = Unit
        override suspend fun moverSeccion(id: String, antes: String?, despues: String?) = Unit
        override suspend fun moverBloque(id: String, antes: String?, despues: String?) = Unit
        override suspend fun cambiarTipoBloque(id: String, tipo: String) = Unit
        override suspend fun forkSeccion(seccionId: String, desde: String?, activar: Boolean) = ""
        override suspend fun activarVarianteSeccion(seccionId: String, vsId: String) = Unit
        override suspend fun borrarVarianteSeccion(id: String) = Unit
        override suspend fun forkRama(ramaId: String, nombre: String) = ""
        override suspend fun renombrarRama(id: String, nombre: String) = Unit
        override suspend fun borrarRama(id: String) = Unit
        override suspend fun hacerPrincipal(idiomaId: String, ramaId: String) = Unit
        override suspend fun crearIdioma(articuloId: String, idioma: String, copiarDe: String?) = ""
        override suspend fun borrarIdioma(id: String) = Unit
        override suspend fun marcarTraducidoIa(idiomaId: String, si: Boolean) = Unit
        override suspend fun cambiarDescripcion(idiomaId: String, descripcion: String) = Unit
        override suspend fun cambiarEtiquetas(articuloId: String, nombres: List<String>) = Unit
        override suspend fun cambiarSerie(articuloId: String, serieId: String?, parte: Long?) = Unit
        override suspend fun crearSerie(nombre: String) = ""
    }

    val accionesPublicacion = object : AccionesPublicacion {
        override fun cambiar(campos: Map<String, String?>) = Unit
        override fun partes(partes: List<Parte>) = Unit
        override fun publicada(enlace: String) = Unit
        override fun borrar() = Unit
        override fun copiar(texto: String) = Unit
        override fun elegirImagen(onElegida: (String) -> Unit) = Unit
        override fun abrirArticulo(id: String) = Unit
    }
}
