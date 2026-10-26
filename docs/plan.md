# Tinta: plan

Tinta es un servicio de ai-lab para dos cosas: planear publicaciones en redes y escribir artículos con orden. Lo abres en el navegador del portátil y en una app de Android. Los dos ven los mismos datos porque los guarda el servicio de la torre.

Estado: **borrador del plan, pendiente de tu revisión.** Después vienen las maquetas y, con las dos cosas aprobadas, la construcción.

## 1. Decisiones tomadas

| Tema | Decisión |
|---|---|
| Nombre y dirección | Tinta, en `https://tinta.rubensalas.dev`, solo dentro de la tailnet |
| Logo | Un bote de tinta con una pluma, en un solo color |
| Colores | Grises, negro y blanco. Tema claro y oscuro según el sistema. La única excepción son los estados, con una escala de color apagada: gris (idea), ámbar (borrador o preparando), violeta (revisión), azul (listo o programada) y verde (publicado) |
| Iconos de redes | Los oficiales, en un solo color: Simple Icons (X, Instagram, YouTube, TikTok) y Font Awesome (LinkedIn, que Simple Icons ya no incluye). El portfolio usa un globo |
| Móvil | App nativa de Android, en Kotlin con Jetpack Compose (la biblioteca de Google para dibujar pantallas) |
| Sin conexión | La app de Android funciona entera sin red y sincroniza al volver. La web trabaja siempre conectada |
| Ayuda para escribir | Tinta da orden y estructura, sin IA propia. La IA llega por MCP (el protocolo que deja a Claude u otro agente usar herramientas de Tinta) |
| Qué puede hacer el agente | Solo proponer. Sus cambios llegan marcados como suyos y tú los aceptas o los descartas |
| Idiomas | Cada artículo tiene versión en español y en inglés, como el portfolio |
| Repositorio | Público, `ruben-salas20/tinta`. Imagen en GHCR (el registro de imágenes de GitHub) y versiones de la app en GitHub Releases, como Atalaya |

## 2. Las piezas

```
 Portátil (navegador) ──┐
                        ├── Caddy ── tinta (servicio en Rust) ── SQLite + carpeta de medios
 Android (app Tinta) ───┤               │
                        │               └── /mcp
 Claude u otro agente ──┘
```

- **El servicio** está escrito en Rust, como el de Atalaya. Guarda todo en una base SQLite y los archivos en una carpeta de medios. Sirve tres cosas: la web, la API para la app y el MCP para los agentes.
- **La web** es una página que sirve el propio servicio. Está escrita en TypeScript y usa TipTap (un editor de texto para navegador) para las marcas de tachado y de propuesta.
- **La app de Android** guarda una copia completa en el móvil, con Room (la base de datos local de Android). Sincroniza en segundo plano con WorkManager (el planificador de tareas de Android).

## 3. Cómo se guarda un artículo

### 3.1 La estructura

```
Artículo  (slug, estado, etiquetas, serie)
└── Idioma  es | en   (title, description, date, draft, translatedWithAI)
    └── Rama  «principal» y los forks del artículo entero
        └── Sección  (un subtítulo ## y lo que hay debajo)
            └── Variante de sección  una activa; cada una con sus propios bloques
                └── Bloque  (párrafo, lista, cita, código, imagen, audio, YouTube)
                    └── Variante de bloque  una activa; las demás guardadas
```

- **Estados del artículo:** idea, borrador, revisión, listo y publicado.
- **Rama:** un fork del artículo entero. Copia todas las secciones para probar otro enfoque. Al final eliges qué rama queda como principal.
- **Variante de sección:** un fork de una sección entera. Sirve para probar otro orden u otra idea para esa parte.
- **Variante de bloque:** un fork de un párrafo suelto. Escribes otra redacción de ese párrafo y cambias entre ellas sin perder ninguna.
- **Historial:** cada vez que guardas, Tinta conserva la versión anterior de ese bloque. Puedes volver a cualquier punto.

### 3.1.1 Etiquetas y series

- **Etiquetas:** palabras libres para filtrar, por ejemplo «ia», «android» o «ai-lab». Un artículo lleva las que quieras.
- **Serie:** un grupo ordenado de artículos, por ejemplo «Montando ai-lab, parte 1, 2 y 3». Un artículo pertenece a una serie como mucho, con su número de parte.
- El portfolio aún no muestra etiquetas ni series. Tinta las guarda para ti y no las exporta, hasta que el portfolio las admita.

### 3.2 Tachar y proponer, con CriticMarkup

El texto de cada sección se guarda en Markdown con CriticMarkup. CriticMarkup es una convención de texto plano para marcar revisiones:

| Marca | Qué significa | Qué sale al exportar |
|---|---|---|
| `{--texto--}` | Tachado: lo quieres quitar, pero aún no | Nada |
| `{++texto++}` | Texto añadido pendiente de aceptar | Nada hasta que lo aceptes |
| `{~~antes~>después~~}` | Cambio propuesto | «antes» hasta que lo aceptes |
| `{>>nota<<}` | Comentario al margen | Nada |

En pantalla no ves las llaves: ves el texto tachado, el añadido resaltado y la nota al margen. Las llaves solo existen por dentro.

**Por qué así:** es texto plano, así que funciona igual en la web, en Android y para un agente. Un agente lee y escribe estas marcas sin herramientas especiales.

### 3.3 Medios

- **Imágenes y GIF:** se suben a Tinta desde el móvil o la web.
- **Audio:** se sube igual. Al exportar va a la carpeta `public/` del portfolio, como pide su plantilla.
- **Vídeo:** solo enlaces de YouTube, en una línea sola. Tinta los muestra como portada, igual que el portfolio.

## 4. Sincronización sin conexión

Solo hay un usuario, pero puede editar en dos sitios sin que se vean. Tinta lo resuelve así:

1. Cada cambio del móvil se guarda primero en una cola local.
2. Al volver la red, la app envía la cola. Cada cambio dice sobre qué versión se hizo.
3. Si nadie tocó ese bloque entretanto, el cambio entra sin más.
4. **Si el portátil cambió el mismo bloque, Tinta no elige.** Guarda tu texto del móvil como otra variante de ese bloque, marcada «conflicto». Tú decides cuál queda. Como la unidad es el párrafo, editar párrafos distintos de la misma sección no choca.
5. Los datos simples (título, fecha, estado, publicaciones del calendario) se resuelven por el cambio más reciente.
6. Borrar no elimina al momento: deja una marca, para que el otro dispositivo se entere.

El orden de las secciones y de los bloques usa posiciones fraccionarias (un número entre el anterior y el siguiente). Así dos dispositivos pueden reordenar sin pisarse.

## 5. Calendario

### 5.1 Canales

Los canales son datos, no código. Añadir YouTube o TikTok más adelante es crear un canal nuevo, sin programar.

| Canal | Formatos | Límite de texto |
|---|---|---|
| X | Post, hilo, artículo | 280 por post |
| LinkedIn | Post, artículo | 3000 por post |
| Instagram | Post, carrusel, reel | 2200 en el pie |
| Portfolio | Artículo | Sin límite |
| YouTube, TikTok (más adelante) | Vídeo, short | Según el canal |

### 5.2 Publicaciones

Cada publicación tiene fecha y hora, canal, formato, estado, texto, medios y, si quieres, el artículo del que sale. Los estados son: idea, preparando, programada y publicada. Al publicarla a mano, guardas su enlace.

- **Un artículo genera varias publicaciones.** Desde el artículo pulsas «Planear difusión» y salen, por ejemplo, el portfolio el lunes, el X Article el martes y un post de LinkedIn el jueves.
- **Vistas:** mes y semana en la web, agenda en el móvil.
- **Resumen diario:** la app de Android avisa una vez al día, a la hora que elijas (por defecto, las 8:00). El aviso lista lo que toca publicar hoy y lo que se quedó atrasado. Funciona sin red porque lo programa el propio móvil.
- **Hilos de X:** el texto se parte en posts de 280 caracteres. Ves el contador de cada uno mientras escribes.

**Tinta no publica por ti.** La API de X para publicar es de pago, y las de LinkedIn e Instagram exigen apps aprobadas. Tinta te prepara el texto y te recuerda; publicas tú.

## 6. Exportar y copiar

| Acción | Resultado |
|---|---|
| Exportar para el portfolio | Un `.zip` con `src/content/articles/<slug>/es.md`, `en.md` e imágenes, y `public/articulos/<slug>/` con los audios. Lo descomprimes en la raíz del repo del portfolio |
| Copiar para X Articles | Pone el artículo en el portapapeles como texto con formato (HTML). El editor de X no entiende Markdown |
| Copiar Markdown | El `.md` de un idioma, sin cabecera |

Al exportar, Tinta:

- Quita lo tachado, las propuestas sin aceptar y los comentarios.
- Usa solo la rama principal y la variante activa de cada sección.
- Escribe la cabecera que pide el portfolio: `title`, `description`, `date`, `draft` y `translatedWithAI`.
- Convierte cada imagen en `./nombre.png` y cada audio en `<audio src="/articulos/<slug>/...">`.
- Si una imagen no tiene texto alternativo, avisa antes de exportar.

## 7. MCP para agentes

Tinta expone un MCP en `https://tinta.rubensalas.dev/mcp`, con su propia clave. Las herramientas son:

| Herramienta | Qué hace |
|---|---|
| `listar_articulos` | Títulos, estados e idiomas |
| `leer_articulo` | El texto con sus marcas, por secciones |
| `proponer_cambio` | Añade `{~~ ~> ~~}`, `{++ ++}` o `{-- --}` en una sección |
| `proponer_variante` | Crea una variante nueva de una sección o de un párrafo, sin activarla |
| `comentar` | Añade una nota al margen |
| `proponer_traduccion` | Crea la versión en inglés como borrador marcado `translatedWithAI` |
| `leer_calendario` y `proponer_publicacion` | Consulta el calendario y sugiere publicaciones en estado idea |

**No hay herramientas para borrar, aceptar ni activar.** Eso solo lo haces tú. Cada propuesta lleva el nombre del agente que la hizo.

## 8. Seguridad

- El dominio solo responde dentro de la tailnet, como los demás.
- Hay tres claves en `~/servicios/.env`, separadas para poder revocar una sin tocar las otras: `TINTA_API_KEY` para la app, `TINTA_MCP_KEY` para los agentes y `TINTA_WEB_PASSWORD` para entrar desde el navegador.

## 9. Montaje en ai-lab

| Pieza | Valor |
|---|---|
| Contenedor | `tinta`, imagen `tinta-server:desplegada` descargada de GHCR |
| Puerto local | `127.0.0.1:3008`, libre a fecha de hoy |
| Datos | `~/servicios/datos/tinta`: `tinta.db` y la carpeta `medios/` |
| Caddy | Bloque `tinta.rubensalas.dev` hacia `tinta:8080` |
| Salud | `/health`, vigilado por Uptime Kuma |
| Copia de seguridad | Volcado de SQLite como el de Atalaya, más la carpeta `medios/`. Hay que editar `backup-ai-lab.sh`, que pide `sudo` |
| App de Android | Compilada en Docker con una imagen como la de Atalaya. El APK se publica en GitHub Releases |
| Documentación | Una sección nueva en `ai-lab-stack.md` al terminar |

## 10. Fases

| Fase | Qué queda hecho |
|---|---|
| 0 | Este plan y las maquetas en HTML, de móvil y de web |
| 1 | Servicio y web con artículos: secciones, párrafos, variantes de los dos niveles, ramas, tachado, historial, etiquetas, series, medios, exportar y copiar para X. Montado en ai-lab |
| 2 | Calendario en la web |
| 3 | MCP para agentes |
| 4 | App de Android: sincronización sin conexión, editor, calendario y resumen diario |

El MCP va antes que Android porque es pequeño y te deja usar un agente desde la fase 3. Android es la fase más larga, por la sincronización.

## 11. Riesgos y cosas que no se ven a primera vista

- **X Articles pide X Premium.** Además, el pegado de HTML hay que probarlo en la fase 1: no sé aún si X conserva bloques de código o tablas.
- **Las imágenes no viajan al pegar en X.** Hay que subirlas a mano en su editor. El audio no existe en X.
- **Los medios pesan en el móvil.** Propongo que la app baje el texto entero y los medios solo cuando los abras.
- **Cambiar el slug de un artículo publicado rompe su URL** en el portfolio. Tinta avisará si lo intentas.
- **La fecha del portfolio sale del calendario.** Si cambias el día de publicación, el `date` exportado cambia con él.
- **Texto tuyo y texto de IA.** El AGENTS.md del portfolio separa lo que escribes tú de lo que hace la IA. Cuando aceptas una propuesta del agente, ese texto deja de estar marcado como suyo.

## 12. Mejoras para después, sin incluir en este plan

- Publicar en el portfolio con un commit o un pull request directo, en vez del `.zip`.
- Una marca que diga qué parte de un artículo aceptaste de una propuesta del agente, para no perder la autoría.
