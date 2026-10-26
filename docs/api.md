# API de Tinta

La API la usan la web, la app de Android y, desde la fase 3, el MCP. Todo va en JSON bajo `/api`, salvo los archivos y el exportado. La lógica vive en `crates/tinta-server/src/modelo.rs`; las rutas solo traducen.

## Acceso

| Quién | Cómo |
|---|---|
| La web | `POST /api/entrar {clave}` con `TINTA_WEB_PASSWORD`. Devuelve la cookie `tinta_sesion` (`HttpOnly`, `Secure`, `SameSite=Strict`, un año). `POST /api/salir` la borra |
| La app de Android | Cabecera `Authorization: Bearer <TINTA_API_KEY>` |

- `GET /health` responde `ok` sin clave. Lo vigila Uptime Kuma.
- `GET /api/sesion` responde 200 con acceso y 401 sin él. La web lo usa para saber si pedir la clave.
- La cabecera opcional `X-Tinta-Dispositivo` (`web`, `android`, `mcp`) dice quién guarda. Por defecto es `web`. Sirve al historial.

## Errores

| Código | Cuándo | Cuerpo |
|---|---|---|
| 400 | Dato no válido: estado desconocido, fecha mal escrita, borrar lo último que queda | `{"error": "…"}` en español, listo para mostrar |
| 401 | Sin acceso | `{"error": "hace falta entrar"}` |
| 404 | No existe, o está borrado | `{"error": "no existe: …"}` |

## El modelo, en una línea por pieza

```
Artículo → Idioma (es | en) → Rama → Sección → Variante de sección → Bloque → Variante de bloque
```

- El **contenido** de cada variante de bloque es Markdown con CriticMarkup. Las marcas están en `critic.rs`.
- Los **medios** se citan como `medio:<id>`: `![alt](medio:ID)` para una imagen y `<audio src="medio:ID" title="…" controls></audio>` para un audio.
- El **orden** usa posiciones fraccionarias (`posicion`, un texto que se ordena como tal). Para insertar o mover se mandan los vecinos: `{"antes": id | null, "despues": id | null}`. Si solo hay uno, el elemento queda pegado a él. Sin ninguno, va al final.
- `origen` de una variante: `tu`, `ia:<nombre>` o `conflicto`. Editarla la convierte en `tu`.

## Rutas

### Artículos

| Método y ruta | Cuerpo | Devuelve |
|---|---|---|
| `GET /api/articulos` | | Lista de `Articulo`, del más reciente al más antiguo |
| `POST /api/articulos` | `{titulo}` | 201 y el `Articulo`. Ya trae idioma `es`, rama `principal` y un párrafo vacío |
| `GET /api/articulos/{id}` | | `Articulo` |
| `PATCH /api/articulos/{id}` | Cualquiera de `slug`, `estado`, `serie_id`, `serie_parte`, `fecha` (`AAAA-MM-DD`), `etiquetas` (lista de nombres). `null` vacía el campo; un campo ausente no se toca | `Articulo` |
| `DELETE /api/articulos/{id}` | | 204 |
| `POST /api/articulos/{id}/idiomas` | `{idioma, copiar_de?}`. Con `copiar_de: "es"` copia la rama principal en español, como punto de partida para traducir | `Articulo` |
| `GET /api/articulos/{id}/exportar.zip?idiomas=es,en` | | El `.zip` para el portfolio. Sin `idiomas`, todos |

`Articulo` incluye `senales`: ramas, propuestas pendientes, notas, tachados, conflictos y palabras. Se calcula sobre la rama principal de cada idioma.

### Idiomas

| Método y ruta | Cuerpo | Devuelve |
|---|---|---|
| `PATCH /api/idiomas/{id}` | `titulo`, `descripcion`, `borrador`, `traducido_ia`, `rama_principal_id` | `Articulo` |
| `DELETE /api/idiomas/{id}` | | `Articulo`. No deja borrar el último |
| `GET /api/idiomas/{id}/markdown` | | El `.md` con cabecera, en `text/markdown` |
| `GET /api/idiomas/{id}/exportar` | | `{idioma, markdown, avisos, archivos}`: lo que muestra la ventana de exportar |
| `GET /api/idiomas/{id}/x` | | `{html, texto, imagenes, avisos}` para pegar en X Articles |

### Ramas, secciones y bloques

Casi todas devuelven el `Documento` de la rama entero, ya actualizado. Así el cliente no tiene que rehacer el árbol a mano.

| Método y ruta | Cuerpo | Devuelve |
|---|---|---|
| `GET /api/ramas/{id}` | | `Documento`: rama, idioma y secciones con todas sus variantes |
| `PATCH /api/ramas/{id}` | `{nombre}` | `Documento` |
| `DELETE /api/ramas/{id}` | | 204. No deja borrar la principal |
| `POST /api/ramas/{id}/fork` | `{nombre}` | 201 y el `Documento` de la rama nueva. Copia solo lo activo |
| `POST /api/ramas/{id}/secciones` | `{titulo, antes?, despues?}` | `Documento` |
| `PATCH /api/secciones/{id}` | `{variante_activa_id?, mover?: {antes, despues}}` | `Documento` |
| `DELETE /api/secciones/{id}` | | `Documento` |
| `POST /api/secciones/{id}/variantes` | `{desde?, activar = true, origen = "tu"}` | `{documento, variante_id}` |
| `PATCH /api/variantes-seccion/{id}` | `{titulo}` | `Documento` |
| `DELETE /api/variantes-seccion/{id}` | | `Documento`. Si era la activa, activa otra |
| `POST /api/variantes-seccion/{id}/bloques` | `{tipo = "parrafo", contenido = "", antes?, despues?}` | `{documento, bloque_id}` |
| `PATCH /api/bloques/{id}` | `{tipo?, variante_activa_id?, mover?}` | `Documento` |
| `DELETE /api/bloques/{id}` | | `Documento`. Si la sección se queda vacía, deja un párrafo |
| `POST /api/bloques/{id}/variantes` | `{contenido?, activar = true, origen = "tu"}`. Sin `contenido`, copia la activa | `{documento, variante_id}` |

Tipos de bloque: `parrafo`, `subtitulo`, `lista`, `cita`, `codigo`, `imagen`, `audio`, `youtube`.

### Guardar texto

| Método y ruta | Cuerpo | Devuelve |
|---|---|---|
| `PUT /api/variantes-bloque/{id}` | `{contenido, rev_base}` | `{conflicto, variante}` |
| `DELETE /api/variantes-bloque/{id}` | | `Documento` |
| `GET /api/variantes-bloque/{id}/historial` | | Las 200 versiones anteriores más recientes |
| `POST /api/variantes-bloque/{id}/restaurar` | `{entrada}` | `{conflicto, variante}` |
| `POST /api/variantes-bloque/{id}/marcas` | `{indice, decision: "aceptar" \| "rechazar"}` | `{conflicto, variante}` |

**`rev_base` evita pisar cambios.** Es el `rev` de la variante que tenía el cliente al empezar a editar:
- Si coincide, el texto se guarda y vuelve el `rev` nuevo, que el cliente usa en el siguiente guardado.
- Si no coincide y el texto es distinto, otro dispositivo cambió el bloque entretanto. **No se pisa nada:** el texto llega como variante nueva con `origen: "conflicto"`, y la respuesta trae `conflicto: true`.

**El historial agrupa.** Los guardados seguidos del mismo dispositivo durante 10 minutos cuentan como una sola sesión de edición: solo el primero deja entrada. Restaurar siempre deja entrada, así que también se puede deshacer.

**`indice` de una marca** cuenta solo las marcas del bloque, en orden y desde 0: tachados, añadidos, cambios y notas. El texto normal y el `{==ancla==}` de una nota no cuentan.

### Etiquetas, series y medios

| Método y ruta | Cuerpo | Devuelve |
|---|---|---|
| `GET /api/etiquetas` | | `[{nombre, articulos}]`, solo las que usa algún artículo |
| `GET /api/series` | | `[{id, nombre, articulos}]` |
| `POST /api/series` | `{nombre}` | 201 y la serie |
| `PATCH /api/series/{id}` | `{nombre}` | La serie |
| `DELETE /api/series/{id}` | | 204. Los artículos quedan sin serie |
| `POST /api/medios` | Multipart, campo `archivo`. Hasta 300 MB | 201 y el `Medio` |
| `GET /api/medios/{id}` | | El archivo, con su tipo |

Medios admitidos: imágenes PNG, JPEG, GIF, WebP, SVG y AVIF, y audios MP3, M4A, OGG, WAV, WebM y AAC. Es lo que el portfolio sabe mostrar. Los vídeos son siempre enlaces de YouTube.

### Calendario

Las fechas van en `AAAA-MM-DD` y las horas en `HH:MM`, en la hora local de Rubén y sin zona. La zona sale de `TINTA_ZONA` (por ejemplo, `America/Bogota`); sin ella, UTC.

| Método y ruta | Cuerpo | Devuelve |
|---|---|---|
| `GET /api/hoy` | | `{fecha, zona}`: el «hoy» del servicio |
| `GET /api/canales` | | `[Canal]`, en orden. Cada uno trae `formatos: [{id, nombre, limite, partes}]` y `activo` |
| `POST /api/canales` | `{nombre, icono, formatos}` | 201 y el canal |
| `PATCH /api/canales/{id}` | `{nombre?, activo?, formatos?}` | El canal |
| `GET /api/publicaciones?desde&hasta&canal&articulo` | | `[Publicacion]`, por fecha y hora. Los cuatro filtros son opcionales; las fechas, incluidas |
| `POST /api/publicaciones` | `{canal_id, formato, fecha, hora?, estado = "idea", titulo?, partes?, articulo_id?}` | 201 y la publicación |
| `GET /api/publicaciones/{id}` | | `Publicacion` |
| `PATCH /api/publicaciones/{id}` | Cualquier campo de los de crear, más `enlace`. `null` vacía `hora`, `articulo_id` o `enlace` | `Publicacion` |
| `DELETE /api/publicaciones/{id}` | | 204 |
| `POST /api/articulos/{id}/difusion` | `[{canal_id, formato, fecha, hora?}]` | 201 y las publicaciones nuevas, en estado `idea` con el título del artículo. Salvo en el portfolio, su texto empieza con la descripción |

- **Estados:** `idea`, `preparando`, `programada` y `publicada`.
- **Partes:** `[{texto, medios: [id]}]`. Un formato con `partes: false` admite una sola; un hilo de X, varias. `limite` son los caracteres por parte; la web cuenta, el servicio no corta.
- **Canales de serie:** `x`, `linkedin`, `instagram` y `portfolio`, activos; `youtube` y `tiktok`, inactivos hasta que se activen.
- **El portfolio manda en la fecha del artículo.** Crear o mover una publicación del canal `portfolio` que sale de un artículo cambia su `fecha`, que es la `date` del `.md` exportado.
- `Articulo` trae `proxima: {canal_id, fecha, hora} | null`: la siguiente publicación sin publicar desde hoy.

## Lo que falta para Android (fase 4)

Cada tabla ya lleva `rev` y lápidas (`borrado`), y el contador global está en `meta.rev`. Falta la ruta de sincronización: «dame lo que cambió desde la rev N» y «aquí va mi cola de cambios». Se diseña en la fase 4.
