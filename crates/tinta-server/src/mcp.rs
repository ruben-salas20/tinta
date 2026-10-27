//! MCP para agentes (fase 3 del plan), en `/mcp` por HTTP («Streamable
//! HTTP» del protocolo, sin flujo SSE: cada POST lleva un mensaje JSON-RPC y
//! recibe su respuesta en JSON).
//!
//! - **Artículos:** el agente solo propone. No hay herramientas para borrar,
//!   aceptar ni activar: eso lo decide Rubén en la web o en la app.
//! - **Calendario:** el agente actúa directamente. Crea, mueve, edita y borra
//!   publicaciones (decisión de Rubén, 2026-10-26). Borrar deja lápida, como
//!   en todo Tinta, así que se puede recuperar desde la base.
//!
//! Todo lo que escribe queda marcado con su nombre (`ia:<nombre>`), que sale
//! de `?agente=` en la dirección del MCP.
//!
//! Acceso: `Authorization: Bearer <TINTA_MCP_KEY>`. Es otra clave que la de
//! la app, para poder revocarla sin tocar nada más. Sin ella, `/mcp` no existe.

use crate::calendario::{self, Parte};
use crate::critic::{self, Propuesta};
use crate::modelo::{self, BloqueEscrito, Error, SeccionEscrita};
use rusqlite::{Connection, OptionalExtension, params};
use serde::Deserialize;
use serde_json::{Value, json};
use std::fmt::Write;

/// Versiones del protocolo que entiende este servidor, de la más nueva a la más vieja.
const VERSIONES: [&str; 3] = ["2025-11-25", "2025-06-18", "2025-03-26"];

const INSTRUCCIONES: &str = "Tinta guarda los artículos de Rubén y su calendario de publicaciones. \
En los artículos solo propones: tus cambios quedan marcados como tuyos y Rubén los acepta o descarta. \
En el calendario actúas directamente: puedes crear, mover, editar y borrar publicaciones. Tinta no publica en las redes: Rubén publica a mano. \
Respeta su voz: corrige ortografía y puntuación, y propón cambios de estilo o contenido solo si te los pide; pulir el estilo lo hace sonar a IA. \
Empieza con listar_articulos y leer_articulo. Cada bloque lleva su id entre corchetes: úsalo en las herramientas de proponer y comentar. \
El texto usa Markdown con CriticMarkup: {--x--} tachado, {++x++} añadido, {~~a~>b~~} cambio, {==x==}{>>nota<<} nota. Lo que ya está marcado no se puede volver a marcar.";

fn herramientas() -> Value {
    let id = |d: &str| json!({ "type": "string", "description": d });
    json!([
        {
            "name": "listar_articulos",
            "description": "Lista los artículos: id, título, estado, serie, etiquetas, idiomas y lo pendiente (propuestas, notas, conflictos).",
            "inputSchema": { "type": "object", "properties": {} },
            "annotations": { "readOnlyHint": true }
        },
        {
            "name": "leer_articulo",
            "description": "Lee un artículo en un idioma, por secciones y bloques, con sus marcas de revisión y los ids que piden las demás herramientas. Por defecto, la rama principal.",
            "inputSchema": {
                "type": "object",
                "properties": {
                    "articulo_id": id("Id del artículo."),
                    "idioma": { "type": "string", "enum": ["es", "en"], "description": "Por defecto, es." },
                    "rama_id": id("Otra rama distinta de la principal (opcional).")
                },
                "required": ["articulo_id"]
            },
            "annotations": { "readOnlyHint": true }
        },
        {
            "name": "proponer_cambio",
            "description": "Propone un cambio dentro de un bloque, sobre un trozo de su texto que exista tal cual y no esté ya marcado. accion: cambiar (por texto), quitar, o anadir (texto justo después del trozo). El trozo no puede cortar un enlace ni un código en línea.",
            "inputSchema": {
                "type": "object",
                "properties": {
                    "bloque_id": id("Id del bloque."),
                    "buscar": id("Trozo exacto del texto actual del bloque. Elige uno que solo aparezca una vez."),
                    "accion": { "type": "string", "enum": ["cambiar", "quitar", "anadir"] },
                    "texto": id("Texto nuevo, para cambiar o anadir.")
                },
                "required": ["bloque_id", "buscar", "accion"]
            }
        },
        {
            "name": "proponer_variante_bloque",
            "description": "Propone otra redacción entera de un bloque. Se guarda como variante, sin activarse: Rubén la compara y decide.",
            "inputSchema": {
                "type": "object",
                "properties": { "bloque_id": id("Id del bloque."), "contenido": id("El bloque entero en Markdown.") },
                "required": ["bloque_id", "contenido"]
            }
        },
        {
            "name": "proponer_variante_seccion",
            "description": "Propone otra versión de una sección entera: su título y sus bloques. Se guarda como variante de la sección, sin activarse.",
            "inputSchema": {
                "type": "object",
                "properties": {
                    "seccion_id": id("Id de la sección."),
                    "titulo": id("Subtítulo de la sección (vacío en la de entrada)."),
                    "bloques": { "$ref": "#/$defs/bloques" }
                },
                "required": ["seccion_id", "bloques"],
                "$defs": { "bloques": bloques_schema() }
            }
        },
        {
            "name": "comentar",
            "description": "Deja una nota al margen en un bloque, anclada a un trozo de su texto o al final. El ancla no puede cortar un enlace ni un código en línea.",
            "inputSchema": {
                "type": "object",
                "properties": {
                    "bloque_id": id("Id del bloque."),
                    "nota": id("La nota."),
                    "ancla": id("Trozo exacto del texto al que se refiere (opcional).")
                },
                "required": ["bloque_id", "nota"]
            }
        },
        {
            "name": "proponer_traduccion",
            "description": "Crea la versión de un artículo en otro idioma, en borrador y marcada como traducida con IA. Solo si ese idioma aún no existe. Traduce con fidelidad, sin pulir el estilo.",
            "inputSchema": {
                "type": "object",
                "properties": {
                    "articulo_id": id("Id del artículo."),
                    "idioma": { "type": "string", "enum": ["es", "en"] },
                    "titulo": id("Título traducido."),
                    "descripcion": id("Descripción traducida."),
                    "secciones": {
                        "type": "array",
                        "description": "En el mismo orden que el original. La primera suele no tener título.",
                        "items": {
                            "type": "object",
                            "properties": { "titulo": { "type": "string" }, "bloques": { "$ref": "#/$defs/bloques" } },
                            "required": ["bloques"]
                        }
                    }
                },
                "required": ["articulo_id", "idioma", "titulo", "secciones"],
                "$defs": { "bloques": bloques_schema() }
            }
        },
        {
            "name": "leer_calendario",
            "description": "Lee los canales y las publicaciones entre dos fechas (por defecto, de hoy a 30 días).",
            "inputSchema": {
                "type": "object",
                "properties": { "desde": id("AAAA-MM-DD"), "hasta": id("AAAA-MM-DD") }
            },
            "annotations": { "readOnlyHint": true }
        },
        {
            "name": "crear_publicacion",
            "description": "Crea una publicación en el calendario. Un hilo de X admite varias partes; el resto, una. Por defecto nace en estado idea.",
            "inputSchema": {
                "type": "object",
                "properties": {
                    "canal_id": id("x, linkedin, instagram, portfolio…: los de leer_calendario."),
                    "formato": id("Un formato de ese canal: post, hilo, articulo…"),
                    "fecha": id("AAAA-MM-DD"),
                    "hora": id("HH:MM (opcional)"),
                    "estado": { "type": "string", "enum": calendario::ESTADOS, "description": "Por defecto, idea." },
                    "titulo": id("Para reconocerla en el calendario."),
                    "partes": { "type": "array", "items": { "type": "string" }, "description": "El texto de cada parte." },
                    "articulo_id": id("Artículo del que sale (opcional).")
                },
                "required": ["canal_id", "formato", "fecha", "partes"]
            }
        },
        {
            "name": "cambiar_publicacion",
            "description": "Cambia una publicación: moverla de fecha u hora, cambiar su estado, texto, título, canal, formato o artículo. Solo cambia los campos que pases. Mover la del portfolio cambia también la fecha del artículo.",
            "inputSchema": {
                "type": "object",
                "properties": {
                    "publicacion_id": id("Id de la publicación (de leer_calendario)."),
                    "fecha": id("AAAA-MM-DD"),
                    "hora": id("HH:MM, o cadena vacía para quitar la hora."),
                    "estado": { "type": "string", "enum": calendario::ESTADOS },
                    "titulo": { "type": "string" },
                    "partes": { "type": "array", "items": { "type": "string" }, "description": "Sustituye el texto de todas las partes." },
                    "canal_id": { "type": "string" },
                    "formato": { "type": "string" },
                    "articulo_id": id("Id del artículo, o cadena vacía para desligarla.")
                },
                "required": ["publicacion_id"]
            }
        },
        {
            "name": "borrar_publicacion",
            "description": "Borra una publicación del calendario.",
            "inputSchema": {
                "type": "object",
                "properties": { "publicacion_id": id("Id de la publicación.") },
                "required": ["publicacion_id"]
            },
            "annotations": { "destructiveHint": true }
        }
    ])
}

fn bloques_schema() -> Value {
    json!({
        "type": "array",
        "items": {
            "type": "object",
            "properties": {
                "tipo": { "type": "string", "enum": modelo::TIPOS, "description": "Por defecto, parrafo." },
                "contenido": { "type": "string", "description": "El bloque en Markdown." }
            },
            "required": ["contenido"]
        }
    })
}

/// Un mensaje JSON-RPC. Sin `id` es una notificación y no lleva respuesta.
pub fn atender(c: &mut Connection, agente: &str, msg: &Value) -> Option<Value> {
    let id = msg.get("id").cloned();
    let metodo = msg.get("method").and_then(Value::as_str).unwrap_or("");
    let params = msg.get("params").cloned().unwrap_or(json!({}));
    let id = id?;
    let r = match metodo {
        "initialize" => {
            let pedida = params.get("protocolVersion").and_then(Value::as_str).unwrap_or(VERSIONES[0]);
            let version = VERSIONES.iter().find(|v| **v == pedida).unwrap_or(&VERSIONES[0]);
            Ok(json!({
                "protocolVersion": version,
                "capabilities": { "tools": {} },
                "serverInfo": { "name": "tinta", "version": env!("CARGO_PKG_VERSION") },
                "instructions": INSTRUCCIONES
            }))
        }
        "ping" => Ok(json!({})),
        "tools/list" => Ok(json!({ "tools": herramientas() })),
        "tools/call" => {
            let nombre = params.get("name").and_then(Value::as_str).unwrap_or("");
            let args = params.get("arguments").cloned().unwrap_or(json!({}));
            Ok(match llamar(c, agente, nombre, args) {
                Ok(texto) => json!({ "content": [{ "type": "text", "text": texto }] }),
                Err(e) => json!({ "content": [{ "type": "text", "text": e }], "isError": true }),
            })
        }
        _ => Err((-32601, format!("método desconocido: {metodo}"))),
    };
    Some(match r {
        Ok(resultado) => json!({ "jsonrpc": "2.0", "id": id, "result": resultado }),
        Err((codigo, m)) => json!({ "jsonrpc": "2.0", "id": id, "error": { "code": codigo, "message": m } }),
    })
}

fn fallo_markdown(f: critic::Fallo) -> String {
    match f {
        critic::Fallo::Enlace => "Ese trozo corta un enlace o cae dentro de su «](url)», y el Markdown se rompería. Marca solo el texto visible del enlace o el enlace entero.".into(),
        critic::Fallo::Codigo => "Ese trozo corta un código en línea o cae dentro de él, y el Markdown se rompería. Marca el código entero, con sus comillas invertidas.".into(),
        critic::Fallo::NoEsta => "No encuentro ese trozo en el texto sin marcar del bloque.".into(),
    }
}

fn texto_error(e: Error) -> String {
    match e {
        Error::NoExiste(q) => format!("No existe: {q}."),
        Error::Invalido(m) => m,
        Error::Db(e) => {
            tracing::error!("mcp, base de datos: {e}");
            "Error de base de datos.".into()
        }
    }
}

fn args<T: for<'de> Deserialize<'de>>(v: Value) -> Result<T, String> {
    serde_json::from_value(v).map_err(|e| format!("Argumentos no válidos: {e}"))
}

fn llamar(c: &mut Connection, agente: &str, nombre: &str, a: Value) -> Result<String, String> {
    let origen = format!("ia:{agente}");
    let r = match nombre {
        "listar_articulos" => listar(c).map_err(texto_error),
        "leer_articulo" => {
            #[derive(Deserialize)]
            struct A {
                articulo_id: String,
                idioma: Option<String>,
                rama_id: Option<String>,
            }
            let a: A = args(a)?;
            leer(c, &a.articulo_id, a.idioma.as_deref().unwrap_or("es"), a.rama_id.as_deref()).map_err(texto_error)
        }
        "proponer_cambio" => {
            #[derive(Deserialize)]
            struct A {
                bloque_id: String,
                buscar: String,
                accion: String,
                texto: Option<String>,
            }
            let a: A = args(a)?;
            let p = match (a.accion.as_str(), a.texto) {
                ("cambiar", Some(t)) => Propuesta::Cambiar(t),
                ("anadir", Some(t)) => Propuesta::Anadir(t),
                ("quitar", _) => Propuesta::Quitar,
                ("cambiar" | "anadir", None) => return Err("Falta «texto» para cambiar o anadir.".into()),
                (otra, _) => return Err(format!("Acción desconocida: {otra}.")),
            };
            editar_activa(c, &a.bloque_id, |s| {
                critic::proponer(s, &a.buscar, p, agente).map_err(|f| match f {
                    critic::Fallo::NoEsta => "No encuentro ese trozo en el texto sin marcar del bloque. Cópialo tal cual de leer_articulo; si ya está marcado, no se puede volver a marcar.".to_string(),
                    otro => fallo_markdown(otro),
                })
            })
            .map(|_| "Propuesta guardada en el bloque.".to_string())
        }
        "proponer_variante_bloque" => {
            #[derive(Deserialize)]
            struct A {
                bloque_id: String,
                contenido: String,
            }
            let a: A = args(a)?;
            modelo::fork_bloque(c, &a.bloque_id, modelo::ForkBloque { contenido: Some(a.contenido), activar: false, origen })
                .map(|(_, v)| format!("Variante guardada sin activar [{v}]."))
                .map_err(texto_error)
        }
        "proponer_variante_seccion" => {
            #[derive(Deserialize)]
            struct A {
                seccion_id: String,
                #[serde(default)]
                titulo: String,
                bloques: Vec<BloqueEscrito>,
            }
            let a: A = args(a)?;
            modelo::crear_variante_seccion(c, &a.seccion_id, &a.titulo, &a.bloques, &origen)
                .map(|(_, v)| format!("Variante de sección guardada sin activar [{v}]."))
                .map_err(texto_error)
        }
        "comentar" => {
            #[derive(Deserialize)]
            struct A {
                bloque_id: String,
                nota: String,
                ancla: Option<String>,
            }
            let a: A = args(a)?;
            editar_activa(c, &a.bloque_id, |s| {
                critic::anotar(s, &a.nota, a.ancla.as_deref(), agente).map_err(|f| match f {
                    critic::Fallo::NoEsta => "No encuentro el ancla en el texto sin marcar del bloque.".to_string(),
                    otro => fallo_markdown(otro),
                })
            })
            .map(|_| "Nota guardada.".to_string())
        }
        "proponer_traduccion" => {
            #[derive(Deserialize)]
            struct A {
                articulo_id: String,
                idioma: String,
                titulo: String,
                #[serde(default)]
                descripcion: String,
                secciones: Vec<SeccionEscrita>,
            }
            let a: A = args(a)?;
            modelo::crear_idioma_escrito(c, &a.articulo_id, &a.idioma, &a.titulo, &a.descripcion, &a.secciones, &origen)
                .map(|_| format!("Versión «{}» creada en borrador y marcada como traducida con IA.", a.idioma))
                .map_err(texto_error)
        }
        "leer_calendario" => {
            #[derive(Deserialize)]
            struct A {
                desde: Option<String>,
                hasta: Option<String>,
            }
            let a: A = args(a)?;
            calendario_texto(c, a.desde, a.hasta).map_err(texto_error)
        }
        "crear_publicacion" => {
            #[derive(Deserialize)]
            struct A {
                canal_id: String,
                formato: String,
                fecha: String,
                hora: Option<String>,
                estado: Option<String>,
                #[serde(default)]
                titulo: String,
                partes: Vec<String>,
                articulo_id: Option<String>,
            }
            let a: A = args(a)?;
            calendario::crear_publicacion(
                c,
                calendario::NuevaPublicacion {
                    canal_id: a.canal_id,
                    formato: a.formato,
                    fecha: a.fecha,
                    hora: a.hora.filter(|h| !h.is_empty()),
                    estado: a.estado.unwrap_or_else(|| "idea".into()),
                    titulo: a.titulo,
                    partes: Some(partes(a.partes)),
                    articulo_id: a.articulo_id,
                    origen,
                },
            )
            .map(|p| format!("Publicación creada el {} en estado {} [{}].", p.fecha, p.estado, p.id))
            .map_err(texto_error)
        }
        "cambiar_publicacion" => {
            #[derive(Deserialize)]
            struct A {
                publicacion_id: String,
                fecha: Option<String>,
                hora: Option<String>,
                estado: Option<String>,
                titulo: Option<String>,
                partes: Option<Vec<String>>,
                canal_id: Option<String>,
                formato: Option<String>,
                articulo_id: Option<String>,
            }
            let a: A = args(a)?;
            // Cadena vacía = quitar: un agente no siempre sabe mandar null.
            let vaciable = |v: Option<String>| v.map(|x| Some(x).filter(|x| !x.is_empty()));
            let cambio = calendario::CambioPublicacion {
                canal_id: a.canal_id,
                formato: a.formato,
                fecha: a.fecha,
                hora: vaciable(a.hora),
                estado: a.estado,
                titulo: a.titulo,
                // Cada parte conserva los medios que ya tenía en su posición.
                partes: match a.partes {
                    None => None,
                    Some(textos) => {
                        let viejas = calendario::publicacion(c, &a.publicacion_id).map_err(texto_error)?.partes;
                        Some(
                            textos
                                .into_iter()
                                .enumerate()
                                .map(|(i, texto)| Parte { texto, medios: viejas.get(i).map(|p| p.medios.clone()).unwrap_or_default() })
                                .collect(),
                        )
                    }
                },
                articulo_id: vaciable(a.articulo_id),
                enlace: None,
            };
            calendario::actualizar_publicacion_como(c, &a.publicacion_id, cambio, &origen)
                .map(|p| format!("Publicación cambiada: {}{} · {} [{}].", p.fecha, p.hora.map(|h| format!(" {h}")).unwrap_or_default(), p.estado, p.id))
                .map_err(texto_error)
        }
        "borrar_publicacion" => {
            #[derive(Deserialize)]
            struct A {
                publicacion_id: String,
            }
            let a: A = args(a)?;
            calendario::borrar_publicacion(c, &a.publicacion_id).map(|_| "Publicación borrada.".to_string()).map_err(texto_error)
        }
        otra => return Err(format!("Herramienta desconocida: {otra}.")),
    };
    r
}

/// Partes nuevas a partir de solo texto: el agente no maneja medios.
fn partes(textos: Vec<String>) -> Vec<Parte> {
    textos.into_iter().map(|texto| Parte { texto, medios: vec![] }).collect()
}

/// Aplica `f` al texto de la variante activa de un bloque y lo guarda.
fn editar_activa(c: &mut Connection, bloque_id: &str, f: impl FnOnce(&str) -> Result<String, String>) -> Result<(), String> {
    let (vid, contenido, rev): (String, String, i64) = c
        .query_row(
            "SELECT vb.id, vb.contenido, vb.rev FROM bloques b JOIN variantes_bloque vb ON vb.id = b.variante_activa_id
             WHERE b.id = ?1 AND b.borrado = 0",
            params![bloque_id],
            |r| Ok((r.get(0)?, r.get(1)?, r.get(2)?)),
        )
        .optional()
        .map_err(|e| texto_error(e.into()))?
        .ok_or("No existe ese bloque.")?;
    let nuevo = f(&contenido)?;
    modelo::guardar_variante_bloque(c, &vid, &nuevo, rev, "mcp").map(|_| ()).map_err(texto_error)
}

fn listar(c: &Connection) -> Result<String, Error> {
    let mut s = String::new();
    for a in modelo::listar_articulos(c)? {
        let titulo = a.idiomas.first().map(|i| i.resumen.titulo.as_str()).unwrap_or("");
        let idiomas: Vec<_> = a.idiomas.iter().map(|i| i.resumen.idioma.as_str()).collect();
        let _ = write!(s, "- {titulo} [{}] · {} · idiomas: {}", a.id, a.estado, idiomas.join(", "));
        if let Some(serie) = &a.serie {
            let _ = write!(s, " · serie «{}»{}", serie.nombre, a.serie_parte.map(|p| format!(", parte {p}")).unwrap_or_default());
        }
        if !a.etiquetas.is_empty() {
            let _ = write!(s, " · etiquetas: {}", a.etiquetas.join(", "));
        }
        let n = &a.senales;
        let _ = writeln!(s, " · {} palabras, {} propuestas, {} notas, {} conflictos", n.palabras, n.propuestas, n.notas, n.conflictos);
    }
    if s.is_empty() {
        s.push_str("No hay artículos.");
    }
    Ok(s)
}

fn leer(c: &Connection, articulo_id: &str, idioma: &str, rama: Option<&str>) -> Result<String, Error> {
    let a = modelo::articulo(c, articulo_id)?;
    let i = a.idiomas.iter().find(|i| i.resumen.idioma == idioma).ok_or_else(|| {
        let hay: Vec<_> = a.idiomas.iter().map(|i| i.resumen.idioma.clone()).collect();
        Error::Invalido(format!("El artículo no tiene versión «{idioma}». Tiene: {}.", hay.join(", ")))
    })?;
    let rama = rama.map(String::from).or(i.resumen.rama_principal_id.clone()).ok_or(Error::NoExiste("rama"))?;
    let d = modelo::documento(c, &rama)?;
    let mut s = String::new();
    let _ = writeln!(s, "# {} ({idioma})", i.resumen.titulo);
    let _ = writeln!(s, "Artículo [{}] · rama «{}» [{}] · estado {} · slug {}", a.id, d.rama.nombre, d.rama.id, a.estado, a.slug);
    let otras: Vec<_> = i.ramas.iter().filter(|r| r.id != d.rama.id).map(|r| format!("«{}» [{}]", r.nombre, r.id)).collect();
    if !otras.is_empty() {
        let _ = writeln!(s, "Otras ramas: {}", otras.join(", "));
    }
    let _ = writeln!(s, "Descripción: {}", i.resumen.descripcion);
    for sec in &d.secciones {
        let Some(vs) = sec.variantes.iter().find(|v| Some(&v.id) == sec.variante_activa_id.as_ref()) else { continue };
        let extra = sec.variantes.len() - 1;
        let titulo = if vs.titulo.is_empty() { "(entrada, sin subtítulo)".to_string() } else { vs.titulo.clone() };
        let _ = writeln!(s, "\n## {titulo}\n[sección {}{}]", sec.id, if extra > 0 { format!(" · {extra} variantes más") } else { String::new() });
        for b in &vs.bloques {
            let Some(vb) = b.variantes.iter().find(|v| Some(&v.id) == b.variante_activa_id.as_ref()) else { continue };
            let mas: Vec<_> = b.variantes.iter().filter(|v| v.id != vb.id).map(|v| v.origen.as_str()).collect();
            let mas = if mas.is_empty() { String::new() } else { format!(" · otras variantes: {}", mas.join(", ")) };
            let _ = writeln!(s, "\n[bloque {} · {}{mas}]\n{}", b.id, b.tipo, vb.contenido);
        }
    }
    Ok(s)
}

fn calendario_texto(c: &Connection, desde: Option<String>, hasta: Option<String>) -> Result<String, Error> {
    let hoy = calendario::hoy();
    let desde = desde.unwrap_or(hoy.clone());
    let hasta = hasta.unwrap_or_else(|| {
        chrono::NaiveDate::parse_from_str(&desde, "%Y-%m-%d")
            .map(|d| (d + chrono::Duration::days(30)).format("%Y-%m-%d").to_string())
            .unwrap_or(desde.clone())
    });
    let mut s = format!("Hoy es {hoy}.\n\nCanales activos:\n");
    for k in calendario::listar_canales(c)?.into_iter().filter(|k| k.activo) {
        let f: Vec<_> = k
            .formatos
            .iter()
            .map(|f| format!("{}{}", f.id, f.limite.map(|l| format!(" ({l} caracteres{})", if f.partes { " por parte" } else { "" })).unwrap_or_default()))
            .collect();
        let _ = writeln!(s, "- {} [{}]: {}", k.nombre, k.id, f.join(", "));
    }
    let _ = writeln!(s, "\nPublicaciones del {desde} al {hasta}:");
    let ps = calendario::listar_publicaciones(c, &calendario::Filtro { desde: Some(desde), hasta: Some(hasta), ..Default::default() })?;
    if ps.is_empty() {
        s.push_str("(ninguna)\n");
    }
    for p in ps {
        let _ = write!(s, "- {}{} · {} {} · {} · «{}» [{}]", p.fecha, p.hora.map(|h| format!(" {h}")).unwrap_or_default(), p.canal_id, p.formato, p.estado, p.titulo, p.id);
        if let Some(a) = p.articulo {
            let _ = write!(s, " · del artículo [{}]", a.id);
        }
        let _ = writeln!(s, " · {} partes", p.partes.len());
    }
    Ok(s)
}

#[cfg(test)]
mod pruebas {
    use super::*;
    use crate::db::en_memoria;

    fn llamar_ok(c: &mut Connection, nombre: &str, a: Value) -> String {
        let r = atender(c, "claude", &json!({ "jsonrpc": "2.0", "id": 1, "method": "tools/call", "params": { "name": nombre, "arguments": a } })).unwrap();
        assert!(r["result"]["isError"].is_null(), "{nombre}: {r}");
        r["result"]["content"][0]["text"].as_str().unwrap().to_string()
    }

    #[test]
    fn protocolo() {
        let mut c = en_memoria();
        let r = atender(&mut c, "c", &json!({ "jsonrpc": "2.0", "id": 0, "method": "initialize", "params": { "protocolVersion": "2025-06-18" } })).unwrap();
        assert_eq!(r["result"]["protocolVersion"], "2025-06-18");
        assert!(r["result"]["instructions"].as_str().unwrap().contains("solo propones"));
        let r = atender(&mut c, "c", &json!({ "jsonrpc": "2.0", "id": 0, "method": "initialize", "params": { "protocolVersion": "1999-01-01" } })).unwrap();
        assert_eq!(r["result"]["protocolVersion"], VERSIONES[0]);
        assert!(atender(&mut c, "c", &json!({ "jsonrpc": "2.0", "method": "notifications/initialized" })).is_none());
        let r = atender(&mut c, "c", &json!({ "jsonrpc": "2.0", "id": 2, "method": "tools/list" })).unwrap();
        let nombres: Vec<_> = r["result"]["tools"].as_array().unwrap().iter().map(|t| t["name"].as_str().unwrap().to_string()).collect();
        assert_eq!(nombres.len(), 11);
        // En los artículos, ninguna herramienta borra, acepta ni activa. Solo
        // se borra en el calendario.
        assert!(nombres.iter().all(|n| !n.contains("aceptar") && !n.contains("activar")));
        assert_eq!(nombres.iter().filter(|n| n.contains("borrar")).collect::<Vec<_>>(), ["borrar_publicacion"]);
        let r = atender(&mut c, "c", &json!({ "jsonrpc": "2.0", "id": 3, "method": "nada" })).unwrap();
        assert_eq!(r["error"]["code"], -32601);
    }

    #[test]
    fn propone_de_punta_a_punta() {
        let mut c = en_memoria();
        let a = modelo::crear_articulo(&mut c, "llama-swap").unwrap();
        let rama = a.idiomas[0].resumen.rama_principal_id.clone().unwrap();
        let d = modelo::documento(&c, &rama).unwrap();
        let b = &d.secciones[0].variantes[0].bloques[0];
        let v = &b.variantes[0];
        modelo::guardar_variante_bloque(&mut c, &v.id, "El ttl dice cuánto dura.", v.rev, "web").unwrap();

        assert!(llamar_ok(&mut c, "listar_articulos", json!({})).contains(&a.id));
        let texto = llamar_ok(&mut c, "leer_articulo", json!({ "articulo_id": a.id }));
        assert!(texto.contains(&format!("[bloque {}", b.id)) && texto.contains("El ttl dice"), "{texto}");

        llamar_ok(&mut c, "proponer_cambio", json!({ "bloque_id": b.id, "buscar": "El ttl dice", "accion": "cambiar", "texto": "El campo ttl marca" }));
        llamar_ok(&mut c, "comentar", json!({ "bloque_id": b.id, "nota": "¿Cuántos segundos?", "ancla": "dura" }));
        let ahora = modelo::variante_bloque(&c, &v.id).unwrap().contenido;
        assert_eq!(ahora, "{~~El ttl dice~>El campo ttl marca~~}{>>ia:claude<<} cuánto {==dura==}{>>ia:claude: ¿Cuántos segundos?<<}.");
        // El texto publicado no cambia hasta que Rubén acepte.
        assert_eq!(critic::limpio_prosa(&ahora), "El ttl dice cuánto dura.");

        // Sobre lo ya marcado, error claro en vez de estropear el texto.
        let r = atender(&mut c, "claude", &json!({ "jsonrpc": "2.0", "id": 9, "method": "tools/call", "params": { "name": "proponer_cambio", "arguments": { "bloque_id": b.id, "buscar": "El ttl dice", "accion": "quitar" } } })).unwrap();
        assert_eq!(r["result"]["isError"], true);

        llamar_ok(&mut c, "proponer_variante_bloque", json!({ "bloque_id": b.id, "contenido": "Otra forma." }));
        let sid = d.secciones[0].id.clone();
        llamar_ok(&mut c, "proponer_variante_seccion", json!({ "seccion_id": sid, "bloques": [{ "contenido": "Uno" }, { "tipo": "lista", "contenido": "- a" }] }));
        let d = modelo::documento(&c, &rama).unwrap();
        assert_eq!(d.secciones[0].variantes.len(), 2);
        assert_eq!(d.secciones[0].variante_activa_id, Some(d.secciones[0].variantes[0].id.clone()));
        let b2 = &d.secciones[0].variantes[0].bloques[0];
        assert_eq!(b2.variantes.len(), 2);
        assert_eq!(b2.variantes[1].origen, "ia:claude");
        assert_eq!(b2.variante_activa_id.as_ref(), Some(&v.id));

        llamar_ok(&mut c, "proponer_traduccion", json!({ "articulo_id": a.id, "idioma": "en", "titulo": "llama-swap", "secciones": [{ "bloques": [{ "contenido": "The ttl says how long." }] }] }));
        let a2 = modelo::articulo(&c, &a.id).unwrap();
        assert!(a2.idiomas.iter().any(|i| i.resumen.idioma == "en" && i.resumen.traducido_ia));

        assert!(llamar_ok(&mut c, "leer_calendario", json!({})).contains("[x]"));
        llamar_ok(&mut c, "crear_publicacion", json!({ "canal_id": "x", "formato": "hilo", "fecha": "2026-11-03", "partes": ["Uno", "Dos"], "articulo_id": a.id }));
        let ps = calendario::listar_publicaciones(&c, &calendario::Filtro::default()).unwrap();
        assert_eq!((ps[0].estado.as_str(), ps[0].origen.as_str(), ps[0].partes.len()), ("idea", "ia:claude", 2));
        let pid = ps[0].id.clone();
        llamar_ok(&mut c, "cambiar_publicacion", json!({ "publicacion_id": pid, "fecha": "2026-11-05", "hora": "18:00", "estado": "programada" }));
        let p = calendario::publicacion(&c, &pid).unwrap();
        assert_eq!((p.fecha.as_str(), p.hora.as_deref(), p.estado.as_str(), p.origen.as_str()), ("2026-11-05", Some("18:00"), "programada", "ia:claude"));
        llamar_ok(&mut c, "cambiar_publicacion", json!({ "publicacion_id": pid, "hora": "" }));
        assert!(calendario::publicacion(&c, &pid).unwrap().hora.is_none());
        // La del portfolio arrastra la fecha del artículo también desde el MCP.
        llamar_ok(&mut c, "crear_publicacion", json!({ "canal_id": "portfolio", "formato": "articulo", "fecha": "2026-11-10", "partes": [""], "articulo_id": a.id }));
        assert_eq!(modelo::articulo(&c, &a.id).unwrap().fecha.as_deref(), Some("2026-11-10"));
        llamar_ok(&mut c, "borrar_publicacion", json!({ "publicacion_id": pid }));
        assert!(calendario::publicacion(&c, &pid).is_err());
    }
}
