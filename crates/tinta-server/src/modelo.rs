//! Operaciones sobre los datos. Las usa la API web y, más adelante, el MCP y
//! la sincronización del móvil: toda la lógica vive aquí, no en las rutas.

use crate::critic;
use crate::db::{existe, siguiente_rev};
use crate::posicion;
use rusqlite::{Connection, OptionalExtension, Row, params};
use serde::{Deserialize, Serialize};

#[derive(Debug)]
pub enum Error {
    NoExiste(&'static str),
    Invalido(String),
    Db(rusqlite::Error),
}

impl From<rusqlite::Error> for Error {
    fn from(e: rusqlite::Error) -> Self {
        Error::Db(e)
    }
}

pub type Res<T> = Result<T, Error>;

pub const ESTADOS: [&str; 5] = ["idea", "borrador", "revision", "listo", "publicado"];
pub const IDIOMAS: [&str; 2] = ["es", "en"];
pub const TIPOS: [&str; 8] = ["parrafo", "subtitulo", "lista", "cita", "codigo", "imagen", "audio", "youtube"];

/// Ids que elige el cliente al crear sin conexión, uno por tipo de pieza.
/// Solo los usa la sincronización (`sync.rs`) mientras aplica una
/// operación: así el móvil puede referirse a lo que creó antes de enviarlo.
#[derive(Debug, Default, Clone, Deserialize)]
pub struct IdsDados {
    pub articulo: Option<String>,
    pub idioma: Option<String>,
    pub rama: Option<String>,
    pub seccion: Option<String>,
    pub variante_seccion: Option<String>,
    pub bloque: Option<String>,
    pub variante_bloque: Option<String>,
    pub publicacion: Option<String>,
    pub medio: Option<String>,
    pub serie: Option<String>,
    /// Para copias (ramas, idiomas, variantes de sección): cada pieza copiada
    /// toma el id [`derivado`] de la semilla y del id de su original, que el
    /// móvil calcula igual sin preguntar al servidor.
    pub semilla: Option<String>,
}

thread_local! {
    static DADOS: std::cell::RefCell<IdsDados> = std::cell::RefCell::new(IdsDados::default());
}

pub fn id_valido(id: &str) -> bool {
    id.len() == 24 && id.bytes().all(|b| b.is_ascii_hexdigit() && !b.is_ascii_uppercase())
}

/// Ejecuta `f` usando los ids dados en lugar de generarlos. Cada uno se usa
/// una sola vez: la primera pieza de su tipo que se cree.
pub fn con_ids<T>(ids: IdsDados, f: impl FnOnce() -> Res<T>) -> Res<T> {
    let todos = [
        &ids.articulo,
        &ids.idioma,
        &ids.rama,
        &ids.seccion,
        &ids.variante_seccion,
        &ids.bloque,
        &ids.variante_bloque,
        &ids.publicacion,
        &ids.medio,
        &ids.serie,
        &ids.semilla,
    ];
    if let Some(malo) = todos.into_iter().flatten().find(|i| !id_valido(i)) {
        return Err(Error::Invalido(format!("id no válido (24 caracteres hexadecimales en minúscula): {malo}")));
    }
    DADOS.with(|d| *d.borrow_mut() = ids);
    let r = f();
    DADOS.with(|d| *d.borrow_mut() = IdsDados::default());
    r
}

/// El id dado para ese tipo de pieza, si lo hay; si no, uno nuevo.
pub fn id_para(campo: fn(&mut IdsDados) -> &mut Option<String>) -> String {
    DADOS.with(|d| campo(&mut d.borrow_mut()).take()).unwrap_or_else(nuevo_id)
}

/// Primeros 12 bytes, en hexadecimal, del SHA-256 de `texto`.
pub fn id_de(texto: &str) -> String {
    use sha2::{Digest, Sha256};
    hex::encode(&Sha256::digest(texto.as_bytes())[..12])
}

/// Id de la copia de `original`: fijo si hay semilla, nuevo si no.
fn derivado(original: &str) -> String {
    DADOS.with(|d| d.borrow().semilla.clone()).map(|s| id_de(&format!("{s}:{original}"))).unwrap_or_else(nuevo_id)
}

pub fn nuevo_id() -> String {
    let mut b = [0u8; 12];
    getrandom::fill(&mut b).expect("generador aleatorio del sistema");
    hex::encode(b)
}

pub fn ahora() -> String {
    chrono::Utc::now().to_rfc3339_opts(chrono::SecondsFormat::Secs, true)
}

/// «¿Por qué no un contenedor?» → «por-que-no-un-contenedor».
pub fn slug(titulo: &str) -> String {
    let mut s = String::new();
    for c in titulo.to_lowercase().chars() {
        let c = match c {
            'á' | 'à' | 'ä' | 'â' => 'a',
            'é' | 'è' | 'ë' | 'ê' => 'e',
            'í' | 'ì' | 'ï' | 'î' => 'i',
            'ó' | 'ò' | 'ö' | 'ô' => 'o',
            'ú' | 'ù' | 'ü' | 'û' => 'u',
            'ñ' => 'n',
            'ç' => 'c',
            c => c,
        };
        if c.is_ascii_alphanumeric() {
            s.push(c);
        } else if !s.ends_with('-') {
            s.push('-');
        }
    }
    let s = s.trim_matches('-').to_string();
    if s.is_empty() { "sin-titulo".into() } else { s }
}

fn slug_libre(c: &Connection, base: &str, salvo: Option<&str>) -> Res<String> {
    let mut n = 1;
    loop {
        let candidato = if n == 1 { base.to_string() } else { format!("{base}-{n}") };
        let ocupado: Option<String> = c
            .query_row("SELECT id FROM articulos WHERE slug = ?1 AND borrado = 0", params![candidato], |r| r.get(0))
            .optional()?;
        match ocupado {
            None => return Ok(candidato),
            Some(id) if Some(id.as_str()) == salvo => return Ok(candidato),
            Some(_) => n += 1,
        }
    }
}

// ---------------------------------------------------------------- Lectura

#[derive(Debug, Serialize, Clone)]
pub struct Serie {
    pub id: String,
    pub nombre: String,
}

#[derive(Debug, Serialize)]
pub struct IdiomaResumen {
    pub id: String,
    pub idioma: String,
    pub titulo: String,
    pub descripcion: String,
    pub borrador: bool,
    pub traducido_ia: bool,
    pub rama_principal_id: Option<String>,
}

#[derive(Debug, Serialize)]
pub struct Rama {
    pub id: String,
    pub idioma_id: String,
    pub nombre: String,
    pub origen_id: Option<String>,
    pub creado: String,
}

#[derive(Debug, Serialize)]
pub struct IdiomaDetalle {
    #[serde(flatten)]
    pub resumen: IdiomaResumen,
    pub ramas: Vec<Rama>,
}

#[derive(Debug, Serialize, Default)]
pub struct Senales {
    pub ramas: u32,
    pub propuestas: u32,
    pub notas: u32,
    pub tachados: u32,
    pub conflictos: u32,
    pub palabras: u32,
}

#[derive(Debug, Serialize)]
pub struct Articulo {
    pub id: String,
    pub slug: String,
    pub estado: String,
    pub serie: Option<Serie>,
    pub serie_parte: Option<i64>,
    pub fecha: Option<String>,
    pub etiquetas: Vec<String>,
    pub creado: String,
    pub cambiado: String,
    pub idiomas: Vec<IdiomaDetalle>,
    pub senales: Senales,
    /// Lo siguiente que toca publicar de este artículo, si hay algo planeado.
    pub proxima: Option<crate::calendario::Proxima>,
}

fn leer_idioma(r: &Row) -> rusqlite::Result<IdiomaResumen> {
    Ok(IdiomaResumen {
        id: r.get("id")?,
        idioma: r.get("idioma")?,
        titulo: r.get("titulo")?,
        descripcion: r.get("descripcion")?,
        borrador: r.get("borrador")?,
        traducido_ia: r.get("traducido_ia")?,
        rama_principal_id: r.get("rama_principal_id")?,
    })
}

fn leer_rama(r: &Row) -> rusqlite::Result<Rama> {
    Ok(Rama {
        id: r.get("id")?,
        idioma_id: r.get("idioma_id")?,
        nombre: r.get("nombre")?,
        origen_id: r.get("origen_id")?,
        creado: r.get("creado")?,
    })
}

pub fn listar_articulos(c: &Connection) -> Res<Vec<Articulo>> {
    let ids: Vec<String> = c
        .prepare("SELECT id FROM articulos WHERE borrado = 0 ORDER BY cambiado DESC")?
        .query_map([], |r| r.get(0))?
        .collect::<Result<_, _>>()?;
    ids.iter().map(|id| articulo(c, id)).collect()
}

pub fn articulo(c: &Connection, id: &str) -> Res<Articulo> {
    let mut a = c
        .query_row(
            "SELECT a.*, s.nombre AS serie_nombre FROM articulos a LEFT JOIN series s ON s.id = a.serie_id
             WHERE a.id = ?1 AND a.borrado = 0",
            params![id],
            |r| {
                let serie_id: Option<String> = r.get("serie_id")?;
                Ok(Articulo {
                    id: r.get("id")?,
                    slug: r.get("slug")?,
                    estado: r.get("estado")?,
                    serie: match serie_id {
                        Some(sid) => Some(Serie { id: sid, nombre: r.get("serie_nombre")? }),
                        None => None,
                    },
                    serie_parte: r.get("serie_parte")?,
                    fecha: r.get("fecha")?,
                    etiquetas: vec![],
                    creado: r.get("creado")?,
                    cambiado: r.get("cambiado")?,
                    idiomas: vec![],
                    senales: Senales::default(),
                    proxima: None,
                })
            },
        )
        .optional()?
        .ok_or(Error::NoExiste("artículo"))?;
    a.etiquetas = c
        .prepare(
            "SELECT e.nombre FROM articulo_etiquetas ae JOIN etiquetas e ON e.id = ae.etiqueta_id
             WHERE ae.articulo_id = ?1 AND e.borrado = 0 ORDER BY e.nombre",
        )?
        .query_map(params![id], |r| r.get(0))?
        .collect::<Result<_, _>>()?;
    let idiomas: Vec<IdiomaResumen> = c
        .prepare("SELECT * FROM idiomas WHERE articulo_id = ?1 AND borrado = 0 ORDER BY idioma DESC")?
        .query_map(params![id], leer_idioma)?
        .collect::<Result<_, _>>()?;
    for i in idiomas {
        let ramas: Vec<Rama> = c
            .prepare("SELECT * FROM ramas WHERE idioma_id = ?1 AND borrado = 0 ORDER BY creado")?
            .query_map(params![i.id], leer_rama)?
            .collect::<Result<_, _>>()?;
        a.senales.ramas += ramas.len() as u32;
        if let Some(principal) = &i.rama_principal_id {
            let doc = documento(c, principal)?;
            for s in &doc.secciones {
                for vs in &s.variantes {
                    let activa_s = s.variante_activa_id.as_deref() == Some(&vs.id);
                    if vs.origen == "conflicto" {
                        a.senales.conflictos += 1;
                    } else if vs.origen.starts_with("ia:") && !activa_s {
                        a.senales.propuestas += 1;
                    }
                    for b in &vs.bloques {
                        for vb in &b.variantes {
                            let activa = b.variante_activa_id.as_deref() == Some(&vb.id);
                            if vb.origen == "conflicto" {
                                a.senales.conflictos += 1;
                            } else if vb.origen.starts_with("ia:") && !activa {
                                a.senales.propuestas += 1;
                            }
                            if activa && activa_s {
                                let r = critic::contar(&vb.contenido);
                                a.senales.propuestas += r.propuestas;
                                a.senales.notas += r.notas;
                                a.senales.tachados += r.tachados;
                                if i.idioma == "es" || a.senales.palabras == 0 {
                                    a.senales.palabras +=
                                        critic::limpio(&vb.contenido).split_whitespace().count() as u32;
                                }
                            }
                        }
                    }
                }
            }
        }
        a.idiomas.push(IdiomaDetalle { resumen: i, ramas });
    }
    a.proxima = crate::calendario::proxima(c, id, &crate::calendario::hoy())?;
    Ok(a)
}

#[derive(Debug, Serialize, Clone)]
pub struct VarianteBloque {
    pub id: String,
    pub contenido: String,
    pub origen: String,
    pub creado: String,
    pub cambiado: String,
    pub rev: i64,
}

#[derive(Debug, Serialize)]
pub struct Bloque {
    pub id: String,
    pub posicion: String,
    pub tipo: String,
    pub variante_activa_id: Option<String>,
    pub variantes: Vec<VarianteBloque>,
}

#[derive(Debug, Serialize)]
pub struct VarianteSeccion {
    pub id: String,
    pub titulo: String,
    pub origen: String,
    pub creado: String,
    pub rev: i64,
    pub bloques: Vec<Bloque>,
}

#[derive(Debug, Serialize)]
pub struct Seccion {
    pub id: String,
    pub posicion: String,
    pub variante_activa_id: Option<String>,
    pub variantes: Vec<VarianteSeccion>,
}

#[derive(Debug, Serialize)]
pub struct Documento {
    pub rama: Rama,
    pub idioma: IdiomaResumen,
    pub articulo_id: String,
    pub secciones: Vec<Seccion>,
}

pub fn documento(c: &Connection, rama_id: &str) -> Res<Documento> {
    let rama = c
        .query_row("SELECT * FROM ramas WHERE id = ?1 AND borrado = 0", params![rama_id], leer_rama)
        .optional()?
        .ok_or(Error::NoExiste("rama"))?;
    let (idioma, articulo_id) = c.query_row("SELECT * FROM idiomas WHERE id = ?1", params![rama.idioma_id], |r| {
        Ok((leer_idioma(r)?, r.get::<_, String>("articulo_id")?))
    })?;
    let mut secciones: Vec<Seccion> = c
        .prepare("SELECT id, posicion, variante_activa_id FROM secciones WHERE rama_id = ?1 AND borrado = 0 ORDER BY posicion")?
        .query_map(params![rama_id], |r| {
            Ok(Seccion { id: r.get(0)?, posicion: r.get(1)?, variante_activa_id: r.get(2)?, variantes: vec![] })
        })?
        .collect::<Result<_, _>>()?;
    let mut q_vs = c.prepare(
        "SELECT id, titulo, origen, creado, rev FROM variantes_seccion WHERE seccion_id = ?1 AND borrado = 0 ORDER BY creado, rowid",
    )?;
    let mut q_b = c.prepare(
        "SELECT id, posicion, tipo, variante_activa_id FROM bloques WHERE variante_seccion_id = ?1 AND borrado = 0 ORDER BY posicion",
    )?;
    let mut q_vb = c.prepare(
        "SELECT id, contenido, origen, creado, cambiado, rev FROM variantes_bloque WHERE bloque_id = ?1 AND borrado = 0 ORDER BY creado, rowid",
    )?;
    for s in &mut secciones {
        s.variantes = q_vs
            .query_map(params![s.id], |r| {
                Ok(VarianteSeccion {
                    id: r.get(0)?,
                    titulo: r.get(1)?,
                    origen: r.get(2)?,
                    creado: r.get(3)?,
                    rev: r.get(4)?,
                    bloques: vec![],
                })
            })?
            .collect::<Result<_, _>>()?;
        for vs in &mut s.variantes {
            vs.bloques = q_b
                .query_map(params![vs.id], |r| {
                    Ok(Bloque {
                        id: r.get(0)?,
                        posicion: r.get(1)?,
                        tipo: r.get(2)?,
                        variante_activa_id: r.get(3)?,
                        variantes: vec![],
                    })
                })?
                .collect::<Result<_, _>>()?;
            for b in &mut vs.bloques {
                b.variantes = q_vb
                    .query_map(params![b.id], |r| {
                        Ok(VarianteBloque {
                            id: r.get(0)?,
                            contenido: r.get(1)?,
                            origen: r.get(2)?,
                            creado: r.get(3)?,
                            cambiado: r.get(4)?,
                            rev: r.get(5)?,
                        })
                    })?
                    .collect::<Result<_, _>>()?;
            }
        }
    }
    Ok(Documento { rama, idioma, articulo_id, secciones })
}

/// La versión que se publica de una rama: sección y bloque activos, en orden.
pub struct Activo {
    pub titulo_seccion: String,
    pub bloques: Vec<(String, String)>, // (tipo, contenido)
}

pub fn activo(doc: &Documento) -> Vec<Activo> {
    doc.secciones
        .iter()
        .filter_map(|s| {
            let vs = s.variantes.iter().find(|v| Some(&v.id) == s.variante_activa_id.as_ref())?;
            Some(Activo {
                titulo_seccion: vs.titulo.clone(),
                bloques: vs
                    .bloques
                    .iter()
                    .filter_map(|b| {
                        let vb = b.variantes.iter().find(|v| Some(&v.id) == b.variante_activa_id.as_ref())?;
                        Some((b.tipo.clone(), vb.contenido.clone()))
                    })
                    .collect(),
            })
        })
        .collect()
}

// ------------------------------------------------------------- Escritura

fn tocar_articulo(c: &Connection, articulo_id: &str, rev: i64) -> Res<()> {
    c.execute("UPDATE articulos SET cambiado = ?2, rev = ?3 WHERE id = ?1", params![articulo_id, ahora(), rev])?;
    Ok(())
}

/// Sube desde cualquier fila hasta su artículo, para marcarlo como cambiado.
fn articulo_de(c: &Connection, tabla: &str, id: &str) -> Res<String> {
    let sql = match tabla {
        "idiomas" => "SELECT articulo_id FROM idiomas WHERE id = ?1",
        "ramas" => "SELECT i.articulo_id FROM ramas r JOIN idiomas i ON i.id = r.idioma_id WHERE r.id = ?1",
        "secciones" => {
            "SELECT i.articulo_id FROM secciones s JOIN ramas r ON r.id = s.rama_id JOIN idiomas i ON i.id = r.idioma_id WHERE s.id = ?1"
        }
        "variantes_seccion" => {
            "SELECT i.articulo_id FROM variantes_seccion vs JOIN secciones s ON s.id = vs.seccion_id
             JOIN ramas r ON r.id = s.rama_id JOIN idiomas i ON i.id = r.idioma_id WHERE vs.id = ?1"
        }
        "bloques" => {
            "SELECT i.articulo_id FROM bloques b JOIN variantes_seccion vs ON vs.id = b.variante_seccion_id
             JOIN secciones s ON s.id = vs.seccion_id JOIN ramas r ON r.id = s.rama_id
             JOIN idiomas i ON i.id = r.idioma_id WHERE b.id = ?1"
        }
        "variantes_bloque" => {
            "SELECT i.articulo_id FROM variantes_bloque vb JOIN bloques b ON b.id = vb.bloque_id
             JOIN variantes_seccion vs ON vs.id = b.variante_seccion_id JOIN secciones s ON s.id = vs.seccion_id
             JOIN ramas r ON r.id = s.rama_id JOIN idiomas i ON i.id = r.idioma_id WHERE vb.id = ?1"
        }
        _ => unreachable!("tabla sin artículo"),
    };
    c.query_row(sql, params![id], |r| r.get(0)).optional()?.ok_or(Error::NoExiste("elemento"))
}

pub fn crear_articulo(c: &mut Connection, titulo: &str) -> Res<Articulo> {
    let tx = c.transaction()?;
    let rev = siguiente_rev(&tx)?;
    let id = id_para(|d| &mut d.articulo);
    let s = slug_libre(&tx, &slug(titulo), None)?;
    let t = ahora();
    tx.execute(
        "INSERT INTO articulos (id, slug, estado, creado, cambiado, rev) VALUES (?1, ?2, 'idea', ?3, ?3, ?4)",
        params![id, s, t, rev],
    )?;
    crear_idioma_vacio(&tx, &id, "es", titulo, rev)?;
    tx.commit()?;
    articulo(c, &id)
}

fn crear_idioma_vacio(c: &Connection, articulo_id: &str, idioma: &str, titulo: &str, rev: i64) -> Res<String> {
    let iid = id_para(|d| &mut d.idioma);
    c.execute(
        "INSERT INTO idiomas (id, articulo_id, idioma, titulo, rev) VALUES (?1, ?2, ?3, ?4, ?5)",
        params![iid, articulo_id, idioma, titulo, rev],
    )?;
    let rid = id_para(|d| &mut d.rama);
    c.execute(
        "INSERT INTO ramas (id, idioma_id, nombre, creado, rev) VALUES (?1, ?2, 'principal', ?3, ?4)",
        params![rid, iid, ahora(), rev],
    )?;
    c.execute("UPDATE idiomas SET rama_principal_id = ?2 WHERE id = ?1", params![iid, rid])?;
    insertar_seccion(c, &rid, &posicion::entre(None, None), "", rev)?;
    Ok(iid)
}

/// Crea una sección con una variante y un párrafo vacío. Devuelve su id.
fn insertar_seccion(c: &Connection, rama_id: &str, pos: &str, titulo: &str, rev: i64) -> Res<String> {
    let sid = id_para(|d| &mut d.seccion);
    c.execute(
        "INSERT INTO secciones (id, rama_id, posicion, rev) VALUES (?1, ?2, ?3, ?4)",
        params![sid, rama_id, pos, rev],
    )?;
    let vsid = insertar_variante_seccion(c, &sid, titulo, "tu", rev)?;
    c.execute("UPDATE secciones SET variante_activa_id = ?2 WHERE id = ?1", params![sid, vsid])?;
    insertar_bloque(c, &vsid, &posicion::entre(None, None), "parrafo", "", "tu", rev)?;
    Ok(sid)
}

fn insertar_variante_seccion(c: &Connection, seccion_id: &str, titulo: &str, origen: &str, rev: i64) -> Res<String> {
    insertar_variante_seccion_con(c, id_para(|d| &mut d.variante_seccion), seccion_id, titulo, origen, rev)
}

fn insertar_variante_seccion_con(c: &Connection, id: String, seccion_id: &str, titulo: &str, origen: &str, rev: i64) -> Res<String> {
    c.execute(
        "INSERT INTO variantes_seccion (id, seccion_id, titulo, origen, creado, rev) VALUES (?1, ?2, ?3, ?4, ?5, ?6)",
        params![id, seccion_id, titulo, origen, ahora(), rev],
    )?;
    Ok(id)
}

/// Crea un bloque con una variante activa. Devuelve (bloque, variante).
fn insertar_bloque(
    c: &Connection,
    vs_id: &str,
    pos: &str,
    tipo: &str,
    contenido: &str,
    origen: &str,
    rev: i64,
) -> Res<(String, String)> {
    let bid = id_para(|d| &mut d.bloque);
    let vid = id_para(|d| &mut d.variante_bloque);
    insertar_bloque_con(c, bid, vid, vs_id, pos, tipo, contenido, origen, rev)
}

#[allow(clippy::too_many_arguments)]
fn insertar_bloque_con(
    c: &Connection,
    bid: String,
    vid: String,
    vs_id: &str,
    pos: &str,
    tipo: &str,
    contenido: &str,
    origen: &str,
    rev: i64,
) -> Res<(String, String)> {
    c.execute(
        "INSERT INTO bloques (id, variante_seccion_id, posicion, tipo, rev) VALUES (?1, ?2, ?3, ?4, ?5)",
        params![bid, vs_id, pos, tipo, rev],
    )?;
    let vid = insertar_variante_bloque_con(c, vid, &bid, contenido, origen, rev)?;
    c.execute("UPDATE bloques SET variante_activa_id = ?2 WHERE id = ?1", params![bid, vid])?;
    Ok((bid, vid))
}

fn insertar_variante_bloque(c: &Connection, bloque_id: &str, contenido: &str, origen: &str, rev: i64) -> Res<String> {
    insertar_variante_bloque_con(c, id_para(|d| &mut d.variante_bloque), bloque_id, contenido, origen, rev)
}

fn insertar_variante_bloque_con(c: &Connection, id: String, bloque_id: &str, contenido: &str, origen: &str, rev: i64) -> Res<String> {
    let t = ahora();
    c.execute(
        "INSERT INTO variantes_bloque (id, bloque_id, contenido, origen, creado, cambiado, rev) VALUES (?1, ?2, ?3, ?4, ?5, ?5, ?6)",
        params![id, bloque_id, contenido, origen, t, rev],
    )?;
    Ok(id)
}

#[derive(Debug, Deserialize, Default)]
pub struct CambioArticulo {
    pub slug: Option<String>,
    pub estado: Option<String>,
    #[serde(default, with = "doble_opcion")]
    pub serie_id: Option<Option<String>>,
    #[serde(default, with = "doble_opcion")]
    pub serie_parte: Option<Option<i64>>,
    #[serde(default, with = "doble_opcion")]
    pub fecha: Option<Option<String>>,
    pub etiquetas: Option<Vec<String>>,
}

/// Distingue «campo ausente» (no tocar) de `null` (vaciar).
mod doble_opcion {
    use serde::{Deserialize, Deserializer};
    pub fn deserialize<'de, D, T>(d: D) -> Result<Option<Option<T>>, D::Error>
    where
        D: Deserializer<'de>,
        T: Deserialize<'de>,
    {
        Option::<T>::deserialize(d).map(Some)
    }
}

pub fn actualizar_articulo(c: &mut Connection, id: &str, cambio: CambioArticulo) -> Res<Articulo> {
    let tx = c.transaction()?;
    if !existe(&tx, "articulos", id)? {
        return Err(Error::NoExiste("artículo"));
    }
    let rev = siguiente_rev(&tx)?;
    if let Some(s) = cambio.slug {
        let s = slug(&s);
        if slug_libre(&tx, &s, Some(id))? != s {
            return Err(Error::Invalido(format!("ya hay otro artículo con el slug «{s}»")));
        }
        tx.execute("UPDATE articulos SET slug = ?2 WHERE id = ?1", params![id, s])?;
    }
    if let Some(e) = cambio.estado {
        if !ESTADOS.contains(&e.as_str()) {
            return Err(Error::Invalido(format!("estado desconocido: {e}")));
        }
        tx.execute("UPDATE articulos SET estado = ?2 WHERE id = ?1", params![id, e])?;
    }
    if let Some(s) = cambio.serie_id {
        if let Some(sid) = &s
            && !existe(&tx, "series", sid)?
        {
            return Err(Error::NoExiste("serie"));
        }
        tx.execute("UPDATE articulos SET serie_id = ?2 WHERE id = ?1", params![id, s])?;
    }
    if let Some(p) = cambio.serie_parte {
        tx.execute("UPDATE articulos SET serie_parte = ?2 WHERE id = ?1", params![id, p])?;
    }
    if let Some(f) = cambio.fecha {
        if let Some(f) = &f
            && chrono::NaiveDate::parse_from_str(f, "%Y-%m-%d").is_err()
        {
            return Err(Error::Invalido(format!("fecha no válida (AAAA-MM-DD): {f}")));
        }
        tx.execute("UPDATE articulos SET fecha = ?2 WHERE id = ?1", params![id, f])?;
    }
    if let Some(etiquetas) = cambio.etiquetas {
        tx.execute("DELETE FROM articulo_etiquetas WHERE articulo_id = ?1", params![id])?;
        for nombre in etiquetas {
            let nombre = nombre.trim().to_lowercase();
            if nombre.is_empty() {
                continue;
            }
            let eid = etiqueta(&tx, &nombre, rev)?;
            tx.execute("INSERT OR IGNORE INTO articulo_etiquetas VALUES (?1, ?2)", params![id, eid])?;
        }
    }
    tocar_articulo(&tx, id, rev)?;
    tx.commit()?;
    articulo(c, id)
}

fn etiqueta(c: &Connection, nombre: &str, rev: i64) -> Res<String> {
    if let Some(id) = c
        .query_row("SELECT id FROM etiquetas WHERE nombre = ?1", params![nombre], |r| r.get::<_, String>(0))
        .optional()?
    {
        c.execute("UPDATE etiquetas SET borrado = 0, rev = ?2 WHERE id = ?1 AND borrado = 1", params![id, rev])?;
        return Ok(id);
    }
    // Fijo por nombre: el móvil crea la misma etiqueta sin conexión.
    let id = id_de(&format!("etiqueta:{nombre}"));
    c.execute("INSERT INTO etiquetas (id, nombre, rev) VALUES (?1, ?2, ?3)", params![id, nombre, rev])?;
    Ok(id)
}

/// Borra un artículo y sus publicaciones aún no publicadas. Las publicadas se
/// quedan: son el historial de lo que salió. Todo lleva la misma rev, que es
/// lo que usa [`restaurar_articulo`] para deshacerlo.
pub fn borrar_articulo(c: &mut Connection, id: &str) -> Res<()> {
    let tx = c.transaction()?;
    let rev = siguiente_rev(&tx)?;
    if tx.execute("UPDATE articulos SET borrado = 1, rev = ?2 WHERE id = ?1 AND borrado = 0", params![id, rev])? == 0 {
        return Err(Error::NoExiste("artículo"));
    }
    tx.execute(
        "UPDATE publicaciones SET borrado = 1, rev = ?2 WHERE articulo_id = ?1 AND borrado = 0 AND estado != 'publicada'",
        params![id, rev],
    )?;
    tx.commit()?;
    Ok(())
}

/// Deshace [`borrar_articulo`]: vuelve el artículo y las publicaciones que se
/// borraron con él. Si otro artículo tomó su slug entretanto, recibe otro.
pub fn restaurar_articulo(c: &mut Connection, id: &str) -> Res<Articulo> {
    let tx = c.transaction()?;
    let (slug_viejo, rev_borrado): (String, i64) = tx
        .query_row("SELECT slug, rev FROM articulos WHERE id = ?1 AND borrado = 1", params![id], |r| Ok((r.get(0)?, r.get(1)?)))
        .optional()?
        .ok_or(Error::NoExiste("artículo borrado"))?;
    let rev = siguiente_rev(&tx)?;
    let s = slug_libre(&tx, &slug_viejo, Some(id))?;
    tx.execute("UPDATE articulos SET borrado = 0, slug = ?2, rev = ?3, cambiado = ?4 WHERE id = ?1", params![id, s, rev, ahora()])?;
    tx.execute(
        "UPDATE publicaciones SET borrado = 0, rev = ?3 WHERE articulo_id = ?1 AND borrado = 1 AND rev = ?2",
        params![id, rev_borrado, rev],
    )?;
    tx.commit()?;
    articulo(c, id)
}

#[derive(Debug, Serialize)]
pub struct Etiqueta {
    pub nombre: String,
    pub articulos: u32,
}

pub fn listar_etiquetas(c: &Connection) -> Res<Vec<Etiqueta>> {
    Ok(c.prepare(
        "SELECT e.nombre, COUNT(a.id) FROM etiquetas e
         JOIN articulo_etiquetas ae ON ae.etiqueta_id = e.id
         JOIN articulos a ON a.id = ae.articulo_id AND a.borrado = 0
         WHERE e.borrado = 0 GROUP BY e.id ORDER BY e.nombre",
    )?
    .query_map([], |r| Ok(Etiqueta { nombre: r.get(0)?, articulos: r.get(1)? }))?
    .collect::<Result<_, _>>()?)
}

#[derive(Debug, Serialize)]
pub struct SerieConCuenta {
    #[serde(flatten)]
    pub serie: Serie,
    pub articulos: u32,
}

pub fn listar_series(c: &Connection) -> Res<Vec<SerieConCuenta>> {
    Ok(c.prepare(
        "SELECT s.id, s.nombre, (SELECT COUNT(*) FROM articulos a WHERE a.serie_id = s.id AND a.borrado = 0)
         FROM series s WHERE s.borrado = 0 ORDER BY s.nombre",
    )?
    .query_map([], |r| Ok(SerieConCuenta { serie: Serie { id: r.get(0)?, nombre: r.get(1)? }, articulos: r.get(2)? }))?
    .collect::<Result<_, _>>()?)
}

pub fn crear_serie(c: &mut Connection, nombre: &str) -> Res<Serie> {
    let nombre = nombre.trim();
    if nombre.is_empty() {
        return Err(Error::Invalido("la serie necesita un nombre".into()));
    }
    let tx = c.transaction()?;
    let rev = siguiente_rev(&tx)?;
    let id = id_para(|d| &mut d.serie);
    tx.execute("INSERT INTO series (id, nombre, rev) VALUES (?1, ?2, ?3)", params![id, nombre, rev])?;
    tx.commit()?;
    Ok(Serie { id, nombre: nombre.into() })
}

pub fn renombrar_serie(c: &mut Connection, id: &str, nombre: &str) -> Res<Serie> {
    let tx = c.transaction()?;
    let rev = siguiente_rev(&tx)?;
    if tx.execute("UPDATE series SET nombre = ?2, rev = ?3 WHERE id = ?1 AND borrado = 0", params![id, nombre.trim(), rev])? == 0 {
        return Err(Error::NoExiste("serie"));
    }
    tx.commit()?;
    Ok(Serie { id: id.into(), nombre: nombre.trim().into() })
}

pub fn borrar_serie(c: &mut Connection, id: &str) -> Res<()> {
    let tx = c.transaction()?;
    let rev = siguiente_rev(&tx)?;
    if tx.execute("UPDATE series SET borrado = 1, rev = ?2 WHERE id = ?1 AND borrado = 0", params![id, rev])? == 0 {
        return Err(Error::NoExiste("serie"));
    }
    tx.execute("UPDATE articulos SET serie_id = NULL, serie_parte = NULL, rev = ?2 WHERE serie_id = ?1", params![id, rev])?;
    tx.commit()?;
    Ok(())
}

/// Añade un idioma al artículo. Con `copiar_de`, copia la rama principal de
/// ese idioma como punto de partida para traducir.
pub fn crear_idioma(c: &mut Connection, articulo_id: &str, idioma: &str, copiar_de: Option<&str>) -> Res<Articulo> {
    if !IDIOMAS.contains(&idioma) {
        return Err(Error::Invalido(format!("idioma desconocido: {idioma}")));
    }
    let tx = c.transaction()?;
    if !existe(&tx, "articulos", articulo_id)? {
        return Err(Error::NoExiste("artículo"));
    }
    let ya: Option<String> = tx
        .query_row(
            "SELECT id FROM idiomas WHERE articulo_id = ?1 AND idioma = ?2 AND borrado = 0",
            params![articulo_id, idioma],
            |r| r.get(0),
        )
        .optional()?;
    if ya.is_some() {
        return Err(Error::Invalido(format!("el artículo ya tiene versión «{idioma}»")));
    }
    let rev = siguiente_rev(&tx)?;
    match copiar_de {
        None => {
            crear_idioma_vacio(&tx, articulo_id, idioma, "", rev)?;
        }
        Some(otro) => {
            let (oid, rama, titulo, desc): (String, String, String, String) = tx
                .query_row(
                    "SELECT id, rama_principal_id, titulo, descripcion FROM idiomas WHERE articulo_id = ?1 AND idioma = ?2 AND borrado = 0",
                    params![articulo_id, otro],
                    |r| Ok((r.get(0)?, r.get(1)?, r.get(2)?, r.get(3)?)),
                )
                .optional()?
                .ok_or(Error::NoExiste("idioma de origen"))?;
            let _ = oid;
            let iid = id_para(|d| &mut d.idioma);
            tx.execute(
                "INSERT INTO idiomas (id, articulo_id, idioma, titulo, descripcion, rev) VALUES (?1, ?2, ?3, ?4, ?5, ?6)",
                params![iid, articulo_id, idioma, titulo, desc, rev],
            )?;
            let nueva = copiar_rama(&tx, &rama, &iid, "principal", rev)?;
            tx.execute("UPDATE idiomas SET rama_principal_id = ?2 WHERE id = ?1", params![iid, nueva])?;
        }
    }
    tocar_articulo(&tx, articulo_id, rev)?;
    tx.commit()?;
    articulo(c, articulo_id)
}

#[derive(Debug, Deserialize, Default)]
pub struct CambioIdioma {
    pub titulo: Option<String>,
    pub descripcion: Option<String>,
    pub borrador: Option<bool>,
    pub traducido_ia: Option<bool>,
    pub rama_principal_id: Option<String>,
}

pub fn actualizar_idioma(c: &mut Connection, id: &str, cambio: CambioIdioma) -> Res<Articulo> {
    let tx = c.transaction()?;
    if !existe(&tx, "idiomas", id)? {
        return Err(Error::NoExiste("idioma"));
    }
    let rev = siguiente_rev(&tx)?;
    if let Some(t) = cambio.titulo {
        tx.execute("UPDATE idiomas SET titulo = ?2 WHERE id = ?1", params![id, t])?;
    }
    if let Some(d) = cambio.descripcion {
        tx.execute("UPDATE idiomas SET descripcion = ?2 WHERE id = ?1", params![id, d])?;
    }
    if let Some(b) = cambio.borrador {
        tx.execute("UPDATE idiomas SET borrador = ?2 WHERE id = ?1", params![id, b])?;
    }
    if let Some(b) = cambio.traducido_ia {
        tx.execute("UPDATE idiomas SET traducido_ia = ?2 WHERE id = ?1", params![id, b])?;
    }
    if let Some(r) = cambio.rama_principal_id {
        let suya: bool = tx
            .query_row("SELECT 1 FROM ramas WHERE id = ?1 AND idioma_id = ?2 AND borrado = 0", params![r, id], |_| Ok(()))
            .optional()?
            .is_some();
        if !suya {
            return Err(Error::Invalido("esa rama no es de este idioma".into()));
        }
        tx.execute("UPDATE idiomas SET rama_principal_id = ?2 WHERE id = ?1", params![id, r])?;
    }
    tx.execute("UPDATE idiomas SET rev = ?2 WHERE id = ?1", params![id, rev])?;
    let aid = articulo_de(&tx, "idiomas", id)?;
    tocar_articulo(&tx, &aid, rev)?;
    tx.commit()?;
    articulo(c, &aid)
}

pub fn borrar_idioma(c: &mut Connection, id: &str) -> Res<Articulo> {
    let tx = c.transaction()?;
    let aid = articulo_de(&tx, "idiomas", id)?;
    let quedan: i64 =
        tx.query_row("SELECT COUNT(*) FROM idiomas WHERE articulo_id = ?1 AND borrado = 0", params![aid], |r| r.get(0))?;
    if quedan <= 1 {
        return Err(Error::Invalido("un artículo necesita al menos un idioma".into()));
    }
    let rev = siguiente_rev(&tx)?;
    tx.execute("UPDATE idiomas SET borrado = 1, rev = ?2 WHERE id = ?1", params![id, rev])?;
    tocar_articulo(&tx, &aid, rev)?;
    tx.commit()?;
    articulo(c, &aid)
}

/// Copia de una rama con solo lo activo: cada sección y cada bloque se quedan
/// con su variante activa. Es lo que se espera de un fork: partir del texto
/// tal como se lee, sin arrastrar las pruebas de la rama de origen.
fn copiar_rama(c: &Connection, origen: &str, idioma_id: &str, nombre: &str, rev: i64) -> Res<String> {
    let doc = documento(c, origen)?;
    let rid = id_para(|d| &mut d.rama);
    c.execute(
        "INSERT INTO ramas (id, idioma_id, nombre, origen_id, creado, rev) VALUES (?1, ?2, ?3, ?4, ?5, ?6)",
        params![rid, idioma_id, nombre, origen, ahora(), rev],
    )?;
    for s in &doc.secciones {
        let Some(vs) = s.variantes.iter().find(|v| Some(&v.id) == s.variante_activa_id.as_ref()) else { continue };
        let sid = derivado(&s.id);
        c.execute("INSERT INTO secciones (id, rama_id, posicion, rev) VALUES (?1, ?2, ?3, ?4)", params![sid, rid, s.posicion, rev])?;
        let vsid = insertar_variante_seccion_con(c, derivado(&vs.id), &sid, &vs.titulo, "tu", rev)?;
        c.execute("UPDATE secciones SET variante_activa_id = ?2 WHERE id = ?1", params![sid, vsid])?;
        copiar_bloques(c, vs, &vsid, rev)?;
    }
    Ok(rid)
}

fn copiar_bloques(c: &Connection, vs: &VarianteSeccion, destino: &str, rev: i64) -> Res<()> {
    for b in &vs.bloques {
        if let Some(vb) = b.variantes.iter().find(|v| Some(&v.id) == b.variante_activa_id.as_ref()) {
            insertar_bloque_con(c, derivado(&b.id), derivado(&vb.id), destino, &b.posicion, &b.tipo, &vb.contenido, "tu", rev)?;
        }
    }
    Ok(())
}

pub fn fork_rama(c: &mut Connection, rama_id: &str, nombre: &str) -> Res<Documento> {
    let nombre = nombre.trim();
    if nombre.is_empty() {
        return Err(Error::Invalido("la rama necesita un nombre".into()));
    }
    let tx = c.transaction()?;
    let idioma_id: String = tx
        .query_row("SELECT idioma_id FROM ramas WHERE id = ?1 AND borrado = 0", params![rama_id], |r| r.get(0))
        .optional()?
        .ok_or(Error::NoExiste("rama"))?;
    let rev = siguiente_rev(&tx)?;
    let nueva = copiar_rama(&tx, rama_id, &idioma_id, nombre, rev)?;
    let aid = articulo_de(&tx, "ramas", rama_id)?;
    tocar_articulo(&tx, &aid, rev)?;
    tx.commit()?;
    documento(c, &nueva)
}

pub fn renombrar_rama(c: &mut Connection, id: &str, nombre: &str) -> Res<Documento> {
    let tx = c.transaction()?;
    let rev = siguiente_rev(&tx)?;
    if tx.execute("UPDATE ramas SET nombre = ?2, rev = ?3 WHERE id = ?1 AND borrado = 0", params![id, nombre.trim(), rev])? == 0 {
        return Err(Error::NoExiste("rama"));
    }
    tx.commit()?;
    documento(c, id)
}

pub fn borrar_rama(c: &mut Connection, id: &str) -> Res<()> {
    let tx = c.transaction()?;
    let principal: bool = tx
        .query_row("SELECT 1 FROM idiomas WHERE rama_principal_id = ?1 AND borrado = 0", params![id], |_| Ok(()))
        .optional()?
        .is_some();
    if principal {
        return Err(Error::Invalido("no se puede borrar la rama principal: elige antes otra".into()));
    }
    let rev = siguiente_rev(&tx)?;
    if tx.execute("UPDATE ramas SET borrado = 1, rev = ?2 WHERE id = ?1 AND borrado = 0", params![id, rev])? == 0 {
        return Err(Error::NoExiste("rama"));
    }
    tx.commit()?;
    Ok(())
}

/// Vecinos para mover o insertar: el nuevo elemento queda entre `antes` y `despues`.
#[derive(Debug, Deserialize, Default)]
pub struct Entre {
    pub antes: Option<String>,
    pub despues: Option<String>,
}

fn posicion_de(c: &Connection, tabla: &str, id: &Option<String>) -> Res<Option<String>> {
    match id {
        None => Ok(None),
        Some(id) => {
            let sql = format!("SELECT posicion FROM {tabla} WHERE id = ?1 AND borrado = 0");
            Ok(Some(c.query_row(&sql, params![id], |r| r.get(0)).optional()?.ok_or(Error::NoExiste("vecino"))?))
        }
    }
}

/// Calcula la posición entre dos vecinos. Si solo se da uno, busca el otro
/// en la lista (`padre_col = padre`), para insertar justo al lado.
fn posicion_entre(c: &Connection, tabla: &str, padre_col: &str, padre: &str, e: &Entre) -> Res<String> {
    let mut a = posicion_de(c, tabla, &e.antes)?;
    let mut b = posicion_de(c, tabla, &e.despues)?;
    let base = format!("SELECT posicion FROM {tabla} WHERE {padre_col} = ?1 AND borrado = 0");
    if let (Some(pa), None) = (&a, &b)
        && e.despues.is_none()
    {
        b = c.query_row(&format!("{base} AND posicion > ?2 ORDER BY posicion LIMIT 1"), params![padre, pa], |r| r.get(0)).optional()?;
    }
    if let (None, Some(pb)) = (&a, &b)
        && e.antes.is_none()
    {
        a = c.query_row(&format!("{base} AND posicion < ?2 ORDER BY posicion DESC LIMIT 1"), params![padre, pb], |r| r.get(0)).optional()?;
    }
    if a.is_none() && b.is_none() {
        // Sin vecinos: al final de la lista.
        a = c.query_row(&format!("{base} ORDER BY posicion DESC LIMIT 1"), params![padre], |r| r.get(0)).optional()?;
    }
    if let (Some(x), Some(y)) = (&a, &b)
        && x >= y
    {
        return Err(Error::Invalido("los vecinos están al revés".into()));
    }
    Ok(posicion::entre(a.as_deref(), b.as_deref()))
}

#[derive(Debug, Deserialize)]
pub struct NuevaSeccion {
    #[serde(default)]
    pub titulo: String,
    #[serde(flatten)]
    pub entre: Entre,
}

pub fn crear_seccion(c: &mut Connection, rama_id: &str, n: NuevaSeccion) -> Res<Documento> {
    let tx = c.transaction()?;
    if !existe(&tx, "ramas", rama_id)? {
        return Err(Error::NoExiste("rama"));
    }
    let rev = siguiente_rev(&tx)?;
    let pos = posicion_entre(&tx, "secciones", "rama_id", rama_id, &n.entre)?;
    insertar_seccion(&tx, rama_id, &pos, n.titulo.trim(), rev)?;
    let aid = articulo_de(&tx, "ramas", rama_id)?;
    tocar_articulo(&tx, &aid, rev)?;
    tx.commit()?;
    documento(c, rama_id)
}

fn rama_de(c: &Connection, tabla: &str, id: &str) -> Res<String> {
    let sql = match tabla {
        "secciones" => "SELECT rama_id FROM secciones WHERE id = ?1",
        "variantes_seccion" => "SELECT s.rama_id FROM variantes_seccion vs JOIN secciones s ON s.id = vs.seccion_id WHERE vs.id = ?1",
        "bloques" => {
            "SELECT s.rama_id FROM bloques b JOIN variantes_seccion vs ON vs.id = b.variante_seccion_id
             JOIN secciones s ON s.id = vs.seccion_id WHERE b.id = ?1"
        }
        "variantes_bloque" => {
            "SELECT s.rama_id FROM variantes_bloque vb JOIN bloques b ON b.id = vb.bloque_id
             JOIN variantes_seccion vs ON vs.id = b.variante_seccion_id JOIN secciones s ON s.id = vs.seccion_id WHERE vb.id = ?1"
        }
        _ => unreachable!(),
    };
    c.query_row(sql, params![id], |r| r.get(0)).optional()?.ok_or(Error::NoExiste("elemento"))
}

#[derive(Debug, Deserialize, Default)]
pub struct CambioSeccion {
    pub variante_activa_id: Option<String>,
    pub mover: Option<Entre>,
}

pub fn actualizar_seccion(c: &mut Connection, id: &str, cambio: CambioSeccion) -> Res<Documento> {
    let tx = c.transaction()?;
    let rama = rama_de(&tx, "secciones", id)?;
    let rev = siguiente_rev(&tx)?;
    if let Some(v) = cambio.variante_activa_id {
        let suya = tx
            .query_row("SELECT 1 FROM variantes_seccion WHERE id = ?1 AND seccion_id = ?2 AND borrado = 0", params![v, id], |_| Ok(()))
            .optional()?
            .is_some();
        if !suya {
            return Err(Error::Invalido("esa variante no es de esta sección".into()));
        }
        tx.execute("UPDATE secciones SET variante_activa_id = ?2 WHERE id = ?1", params![id, v])?;
    }
    if let Some(e) = cambio.mover {
        let pos = posicion_entre(&tx, "secciones", "rama_id", &rama, &e)?;
        tx.execute("UPDATE secciones SET posicion = ?2 WHERE id = ?1", params![id, pos])?;
    }
    tx.execute("UPDATE secciones SET rev = ?2 WHERE id = ?1", params![id, rev])?;
    let aid = articulo_de(&tx, "secciones", id)?;
    tocar_articulo(&tx, &aid, rev)?;
    tx.commit()?;
    documento(c, &rama)
}

pub fn borrar_seccion(c: &mut Connection, id: &str) -> Res<Documento> {
    let tx = c.transaction()?;
    let rama = rama_de(&tx, "secciones", id)?;
    let quedan: i64 = tx.query_row("SELECT COUNT(*) FROM secciones WHERE rama_id = ?1 AND borrado = 0", params![rama], |r| r.get(0))?;
    if quedan <= 1 {
        return Err(Error::Invalido("la rama necesita al menos una sección".into()));
    }
    let rev = siguiente_rev(&tx)?;
    tx.execute("UPDATE secciones SET borrado = 1, rev = ?2 WHERE id = ?1", params![id, rev])?;
    let aid = articulo_de(&tx, "ramas", &rama)?;
    tocar_articulo(&tx, &aid, rev)?;
    tx.commit()?;
    documento(c, &rama)
}

#[derive(Debug, Deserialize)]
pub struct ForkSeccion {
    /// Variante de la que copiar; por defecto, la activa.
    pub desde: Option<String>,
    #[serde(default = "verdadero")]
    pub activar: bool,
    #[serde(default = "tu")]
    pub origen: String,
}

fn verdadero() -> bool {
    true
}
fn tu() -> String {
    "tu".into()
}

pub fn fork_seccion(c: &mut Connection, seccion_id: &str, f: ForkSeccion) -> Res<(Documento, String)> {
    let tx = c.transaction()?;
    let rama = rama_de(&tx, "secciones", seccion_id)?;
    let doc = documento(&tx, &rama)?;
    let s = doc.secciones.iter().find(|s| s.id == seccion_id).ok_or(Error::NoExiste("sección"))?;
    let desde = f.desde.as_ref().or(s.variante_activa_id.as_ref()).ok_or(Error::NoExiste("variante"))?;
    let vs = s.variantes.iter().find(|v| &v.id == desde).ok_or(Error::NoExiste("variante de sección"))?;
    let rev = siguiente_rev(&tx)?;
    let nueva = insertar_variante_seccion(&tx, seccion_id, &vs.titulo, &f.origen, rev)?;
    copiar_bloques(&tx, vs, &nueva, rev)?;
    if f.activar {
        tx.execute("UPDATE secciones SET variante_activa_id = ?2, rev = ?3 WHERE id = ?1", params![seccion_id, nueva, rev])?;
    }
    let aid = articulo_de(&tx, "secciones", seccion_id)?;
    tocar_articulo(&tx, &aid, rev)?;
    tx.commit()?;
    Ok((documento(c, &rama)?, nueva))
}

/// Un bloque escrito de una vez, para las propuestas del agente.
#[derive(Debug, Deserialize, Clone)]
pub struct BloqueEscrito {
    #[serde(default = "parrafo")]
    pub tipo: String,
    pub contenido: String,
}

/// Una sección escrita de una vez: su título (vacío en la de entrada) y bloques.
#[derive(Debug, Deserialize, Clone)]
pub struct SeccionEscrita {
    #[serde(default)]
    pub titulo: String,
    pub bloques: Vec<BloqueEscrito>,
}

fn validar_bloques(bloques: &[BloqueEscrito]) -> Res<()> {
    if bloques.is_empty() {
        return Err(Error::Invalido("hace falta al menos un bloque".into()));
    }
    if let Some(b) = bloques.iter().find(|b| !TIPOS.contains(&b.tipo.as_str())) {
        return Err(Error::Invalido(format!("tipo de bloque desconocido: {}", b.tipo)));
    }
    Ok(())
}

fn insertar_bloques(c: &Connection, vs_id: &str, bloques: &[BloqueEscrito], origen: &str, rev: i64) -> Res<()> {
    for (b, pos) in bloques.iter().zip(posicion::serie(bloques.len())) {
        insertar_bloque(c, vs_id, &pos, &b.tipo, &b.contenido, origen, rev)?;
    }
    Ok(())
}

/// Variante nueva de una sección con bloques ya escritos. No se activa.
pub fn crear_variante_seccion(c: &mut Connection, seccion_id: &str, titulo: &str, bloques: &[BloqueEscrito], origen: &str) -> Res<(Documento, String)> {
    validar_bloques(bloques)?;
    let tx = c.transaction()?;
    let rama = rama_de(&tx, "secciones", seccion_id)?;
    if !existe(&tx, "secciones", seccion_id)? {
        return Err(Error::NoExiste("sección"));
    }
    let rev = siguiente_rev(&tx)?;
    let vs = insertar_variante_seccion(&tx, seccion_id, titulo.trim(), origen, rev)?;
    insertar_bloques(&tx, &vs, bloques, origen, rev)?;
    let aid = articulo_de(&tx, "secciones", seccion_id)?;
    tocar_articulo(&tx, &aid, rev)?;
    tx.commit()?;
    Ok((documento(c, &rama)?, vs))
}

/// Un idioma nuevo con todo su contenido, en borrador. Lo usa la traducción
/// que propone un agente: queda marcada `traducido_ia`.
pub fn crear_idioma_escrito(
    c: &mut Connection,
    articulo_id: &str,
    idioma: &str,
    titulo: &str,
    descripcion: &str,
    secciones: &[SeccionEscrita],
    origen: &str,
) -> Res<Articulo> {
    if !IDIOMAS.contains(&idioma) {
        return Err(Error::Invalido(format!("idioma desconocido: {idioma}")));
    }
    if secciones.is_empty() {
        return Err(Error::Invalido("hace falta al menos una sección".into()));
    }
    for s in secciones {
        validar_bloques(&s.bloques)?;
    }
    let tx = c.transaction()?;
    if !existe(&tx, "articulos", articulo_id)? {
        return Err(Error::NoExiste("artículo"));
    }
    let ya = tx
        .query_row("SELECT 1 FROM idiomas WHERE articulo_id = ?1 AND idioma = ?2 AND borrado = 0", params![articulo_id, idioma], |_| Ok(()))
        .optional()?
        .is_some();
    if ya {
        return Err(Error::Invalido(format!("el artículo ya tiene versión «{idioma}»: propón variantes de sus secciones")));
    }
    let rev = siguiente_rev(&tx)?;
    let iid = nuevo_id();
    tx.execute(
        "INSERT INTO idiomas (id, articulo_id, idioma, titulo, descripcion, borrador, traducido_ia, rev) VALUES (?1, ?2, ?3, ?4, ?5, 1, 1, ?6)",
        params![iid, articulo_id, idioma, titulo.trim(), descripcion.trim(), rev],
    )?;
    let rid = nuevo_id();
    tx.execute("INSERT INTO ramas (id, idioma_id, nombre, creado, rev) VALUES (?1, ?2, 'principal', ?3, ?4)", params![rid, iid, ahora(), rev])?;
    tx.execute("UPDATE idiomas SET rama_principal_id = ?2 WHERE id = ?1", params![iid, rid])?;
    for (s, pos) in secciones.iter().zip(posicion::serie(secciones.len())) {
        let sid = nuevo_id();
        tx.execute("INSERT INTO secciones (id, rama_id, posicion, rev) VALUES (?1, ?2, ?3, ?4)", params![sid, rid, pos, rev])?;
        let vs = insertar_variante_seccion(&tx, &sid, s.titulo.trim(), origen, rev)?;
        tx.execute("UPDATE secciones SET variante_activa_id = ?2 WHERE id = ?1", params![sid, vs])?;
        insertar_bloques(&tx, &vs, &s.bloques, origen, rev)?;
    }
    tocar_articulo(&tx, articulo_id, rev)?;
    tx.commit()?;
    articulo(c, articulo_id)
}

#[derive(Debug, Deserialize)]
pub struct CambioVarianteSeccion {
    pub titulo: Option<String>,
}

pub fn actualizar_variante_seccion(c: &mut Connection, id: &str, cambio: CambioVarianteSeccion) -> Res<Documento> {
    let tx = c.transaction()?;
    let rama = rama_de(&tx, "variantes_seccion", id)?;
    let rev = siguiente_rev(&tx)?;
    if let Some(t) = cambio.titulo {
        tx.execute("UPDATE variantes_seccion SET titulo = ?2 WHERE id = ?1", params![id, t.trim()])?;
    }
    // Editar una propuesta o un conflicto lo hace tuyo.
    tx.execute("UPDATE variantes_seccion SET origen = 'tu', rev = ?2 WHERE id = ?1", params![id, rev])?;
    let aid = articulo_de(&tx, "variantes_seccion", id)?;
    tocar_articulo(&tx, &aid, rev)?;
    tx.commit()?;
    documento(c, &rama)
}

pub fn borrar_variante_seccion(c: &mut Connection, id: &str) -> Res<Documento> {
    let tx = c.transaction()?;
    let rama = rama_de(&tx, "variantes_seccion", id)?;
    let seccion: String = tx.query_row("SELECT seccion_id FROM variantes_seccion WHERE id = ?1", params![id], |r| r.get(0))?;
    let otra: Option<String> = tx
        .query_row(
            "SELECT id FROM variantes_seccion WHERE seccion_id = ?1 AND id != ?2 AND borrado = 0 ORDER BY creado LIMIT 1",
            params![seccion, id],
            |r| r.get(0),
        )
        .optional()?;
    let Some(otra) = otra else {
        return Err(Error::Invalido("es la única variante: borra la sección entera".into()));
    };
    let rev = siguiente_rev(&tx)?;
    tx.execute("UPDATE variantes_seccion SET borrado = 1, rev = ?2 WHERE id = ?1", params![id, rev])?;
    tx.execute(
        "UPDATE secciones SET variante_activa_id = ?2, rev = ?3 WHERE id = ?1 AND variante_activa_id = ?4",
        params![seccion, otra, rev, id],
    )?;
    let aid = articulo_de(&tx, "secciones", &seccion)?;
    tocar_articulo(&tx, &aid, rev)?;
    tx.commit()?;
    documento(c, &rama)
}

#[derive(Debug, Deserialize)]
pub struct NuevoBloque {
    #[serde(default = "parrafo")]
    pub tipo: String,
    #[serde(default)]
    pub contenido: String,
    #[serde(flatten)]
    pub entre: Entre,
}

fn parrafo() -> String {
    "parrafo".into()
}

pub fn crear_bloque(c: &mut Connection, vs_id: &str, n: NuevoBloque) -> Res<(Documento, String)> {
    if !TIPOS.contains(&n.tipo.as_str()) {
        return Err(Error::Invalido(format!("tipo de bloque desconocido: {}", n.tipo)));
    }
    let tx = c.transaction()?;
    let rama = rama_de(&tx, "variantes_seccion", vs_id)?;
    let rev = siguiente_rev(&tx)?;
    let pos = posicion_entre(&tx, "bloques", "variante_seccion_id", vs_id, &n.entre)?;
    let (bid, _) = insertar_bloque(&tx, vs_id, &pos, &n.tipo, &n.contenido, "tu", rev)?;
    let aid = articulo_de(&tx, "variantes_seccion", vs_id)?;
    tocar_articulo(&tx, &aid, rev)?;
    tx.commit()?;
    Ok((documento(c, &rama)?, bid))
}

#[derive(Debug, Deserialize, Default)]
pub struct CambioBloque {
    pub tipo: Option<String>,
    pub variante_activa_id: Option<String>,
    pub mover: Option<Entre>,
}

pub fn actualizar_bloque(c: &mut Connection, id: &str, cambio: CambioBloque) -> Res<Documento> {
    let tx = c.transaction()?;
    let rama = rama_de(&tx, "bloques", id)?;
    let rev = siguiente_rev(&tx)?;
    if let Some(t) = cambio.tipo {
        if !TIPOS.contains(&t.as_str()) {
            return Err(Error::Invalido(format!("tipo de bloque desconocido: {t}")));
        }
        tx.execute("UPDATE bloques SET tipo = ?2 WHERE id = ?1", params![id, t])?;
    }
    if let Some(v) = cambio.variante_activa_id {
        let suya = tx
            .query_row("SELECT 1 FROM variantes_bloque WHERE id = ?1 AND bloque_id = ?2 AND borrado = 0", params![v, id], |_| Ok(()))
            .optional()?
            .is_some();
        if !suya {
            return Err(Error::Invalido("esa variante no es de este bloque".into()));
        }
        tx.execute("UPDATE bloques SET variante_activa_id = ?2 WHERE id = ?1", params![id, v])?;
    }
    if let Some(e) = cambio.mover {
        let vs: String = tx.query_row("SELECT variante_seccion_id FROM bloques WHERE id = ?1", params![id], |r| r.get(0))?;
        let pos = posicion_entre(&tx, "bloques", "variante_seccion_id", &vs, &e)?;
        tx.execute("UPDATE bloques SET posicion = ?2 WHERE id = ?1", params![id, pos])?;
    }
    tx.execute("UPDATE bloques SET rev = ?2 WHERE id = ?1", params![id, rev])?;
    let aid = articulo_de(&tx, "bloques", id)?;
    tocar_articulo(&tx, &aid, rev)?;
    tx.commit()?;
    documento(c, &rama)
}

pub fn borrar_bloque(c: &mut Connection, id: &str) -> Res<Documento> {
    let tx = c.transaction()?;
    let rama = rama_de(&tx, "bloques", id)?;
    let vs: String = tx.query_row("SELECT variante_seccion_id FROM bloques WHERE id = ?1", params![id], |r| r.get(0))?;
    let rev = siguiente_rev(&tx)?;
    if tx.execute("UPDATE bloques SET borrado = 1, rev = ?2 WHERE id = ?1 AND borrado = 0", params![id, rev])? == 0 {
        return Err(Error::NoExiste("bloque"));
    }
    // Una sección sin bloques no se puede editar: deja un párrafo vacío.
    let quedan: i64 = tx.query_row("SELECT COUNT(*) FROM bloques WHERE variante_seccion_id = ?1 AND borrado = 0", params![vs], |r| r.get(0))?;
    if quedan == 0 {
        insertar_bloque(&tx, &vs, &posicion::entre(None, None), "parrafo", "", "tu", rev)?;
    }
    let aid = articulo_de(&tx, "bloques", id)?;
    tocar_articulo(&tx, &aid, rev)?;
    tx.commit()?;
    documento(c, &rama)
}

#[derive(Debug, Deserialize)]
pub struct ForkBloque {
    /// Texto de la variante nueva; por defecto, copia de la activa.
    pub contenido: Option<String>,
    #[serde(default = "verdadero")]
    pub activar: bool,
    #[serde(default = "tu")]
    pub origen: String,
}

pub fn fork_bloque(c: &mut Connection, bloque_id: &str, f: ForkBloque) -> Res<(Documento, String)> {
    let tx = c.transaction()?;
    let rama = rama_de(&tx, "bloques", bloque_id)?;
    let contenido = match f.contenido {
        Some(t) => t,
        None => tx.query_row(
            "SELECT vb.contenido FROM bloques b JOIN variantes_bloque vb ON vb.id = b.variante_activa_id WHERE b.id = ?1",
            params![bloque_id],
            |r| r.get(0),
        )?,
    };
    let rev = siguiente_rev(&tx)?;
    let nueva = insertar_variante_bloque(&tx, bloque_id, &contenido, &f.origen, rev)?;
    if f.activar {
        tx.execute("UPDATE bloques SET variante_activa_id = ?2, rev = ?3 WHERE id = ?1", params![bloque_id, nueva, rev])?;
    }
    let aid = articulo_de(&tx, "bloques", bloque_id)?;
    tocar_articulo(&tx, &aid, rev)?;
    tx.commit()?;
    Ok((documento(c, &rama)?, nueva))
}

#[derive(Debug, Serialize)]
pub struct Guardado {
    /// true si otro dispositivo cambió el bloque entretanto: el texto se
    /// guardó como variante nueva con origen «conflicto».
    pub conflicto: bool,
    pub variante: VarianteBloque,
}

/// Minutos durante los que guardados seguidos del mismo dispositivo cuentan
/// como una sola sesión de edición: solo la primera deja entrada en el
/// historial. La web guarda cada pocos segundos mientras escribes.
const SESION_HISTORIAL_MIN: i64 = 10;

pub fn guardar_variante_bloque(c: &mut Connection, id: &str, contenido: &str, rev_base: i64, dispositivo: &str) -> Res<Guardado> {
    let tx = c.transaction()?;
    let (bloque, actual, origen, rev_actual): (String, String, String, i64) = tx
        .query_row(
            "SELECT bloque_id, contenido, origen, rev FROM variantes_bloque WHERE id = ?1 AND borrado = 0",
            params![id],
            |r| Ok((r.get(0)?, r.get(1)?, r.get(2)?, r.get(3)?)),
        )
        .optional()?
        .ok_or(Error::NoExiste("variante de bloque"))?;
    let rev = siguiente_rev(&tx)?;
    let aid = articulo_de(&tx, "variantes_bloque", id)?;
    if rev_actual != rev_base && actual != contenido {
        let nueva = insertar_variante_bloque(&tx, &bloque, contenido, "conflicto", rev)?;
        tocar_articulo(&tx, &aid, rev)?;
        tx.commit()?;
        return Ok(Guardado { conflicto: true, variante: variante_bloque(c, &nueva)? });
    }
    if actual != contenido {
        let ultima: Option<(String, String)> = tx
            .query_row(
                "SELECT creado, dispositivo FROM historial WHERE variante_bloque_id = ?1 ORDER BY id DESC LIMIT 1",
                params![id],
                |r| Ok((r.get(0)?, r.get(1)?)),
            )
            .optional()?;
        let reciente = ultima.is_some_and(|(t, d)| {
            d == dispositivo
                && chrono::DateTime::parse_from_rfc3339(&t)
                    .is_ok_and(|t| chrono::Utc::now().signed_duration_since(t).num_minutes() < SESION_HISTORIAL_MIN)
        });
        if !reciente {
            tx.execute(
                "INSERT INTO historial (variante_bloque_id, contenido, origen, dispositivo, creado) VALUES (?1, ?2, ?3, ?4, ?5)",
                params![id, actual, origen, dispositivo, ahora()],
            )?;
        }
        // Editar una propuesta del agente o un conflicto lo hace tuyo.
        tx.execute(
            "UPDATE variantes_bloque SET contenido = ?2, origen = 'tu', cambiado = ?3, rev = ?4 WHERE id = ?1",
            params![id, contenido, ahora(), rev],
        )?;
        tocar_articulo(&tx, &aid, rev)?;
    }
    tx.commit()?;
    Ok(Guardado { conflicto: false, variante: variante_bloque(c, id)? })
}

pub fn variante_bloque(c: &Connection, id: &str) -> Res<VarianteBloque> {
    c.query_row(
        "SELECT id, contenido, origen, creado, cambiado, rev FROM variantes_bloque WHERE id = ?1",
        params![id],
        |r| {
            Ok(VarianteBloque {
                id: r.get(0)?,
                contenido: r.get(1)?,
                origen: r.get(2)?,
                creado: r.get(3)?,
                cambiado: r.get(4)?,
                rev: r.get(5)?,
            })
        },
    )
    .optional()?
    .ok_or(Error::NoExiste("variante de bloque"))
}

pub fn borrar_variante_bloque(c: &mut Connection, id: &str) -> Res<Documento> {
    let tx = c.transaction()?;
    let rama = rama_de(&tx, "variantes_bloque", id)?;
    let bloque: String = tx.query_row("SELECT bloque_id FROM variantes_bloque WHERE id = ?1", params![id], |r| r.get(0))?;
    let otra: Option<String> = tx
        .query_row(
            "SELECT id FROM variantes_bloque WHERE bloque_id = ?1 AND id != ?2 AND borrado = 0 ORDER BY creado LIMIT 1",
            params![bloque, id],
            |r| r.get(0),
        )
        .optional()?;
    let Some(otra) = otra else {
        return Err(Error::Invalido("es la única variante: borra el bloque entero".into()));
    };
    let rev = siguiente_rev(&tx)?;
    tx.execute("UPDATE variantes_bloque SET borrado = 1, rev = ?2 WHERE id = ?1", params![id, rev])?;
    tx.execute(
        "UPDATE bloques SET variante_activa_id = ?2, rev = ?3 WHERE id = ?1 AND variante_activa_id = ?4",
        params![bloque, otra, rev, id],
    )?;
    let aid = articulo_de(&tx, "bloques", &bloque)?;
    tocar_articulo(&tx, &aid, rev)?;
    tx.commit()?;
    documento(c, &rama)
}

#[derive(Debug, Serialize)]
pub struct EntradaHistorial {
    pub id: i64,
    pub contenido: String,
    pub origen: String,
    pub dispositivo: String,
    pub creado: String,
}

pub fn historial(c: &Connection, variante_id: &str) -> Res<Vec<EntradaHistorial>> {
    if !existe(c, "variantes_bloque", variante_id)? {
        return Err(Error::NoExiste("variante de bloque"));
    }
    Ok(c.prepare(
        "SELECT id, contenido, origen, dispositivo, creado FROM historial WHERE variante_bloque_id = ?1 ORDER BY id DESC LIMIT 200",
    )?
    .query_map(params![variante_id], |r| {
        Ok(EntradaHistorial { id: r.get(0)?, contenido: r.get(1)?, origen: r.get(2)?, dispositivo: r.get(3)?, creado: r.get(4)? })
    })?
    .collect::<Result<_, _>>()?)
}

/// Vuelve a una versión anterior. Lo de ahora pasa al historial, así que
/// restaurar también se puede deshacer.
pub fn restaurar(c: &mut Connection, variante_id: &str, entrada: i64, dispositivo: &str) -> Res<Guardado> {
    let (contenido, rev): (String, i64) = c
        .query_row(
            "SELECT h.contenido, vb.rev FROM historial h JOIN variantes_bloque vb ON vb.id = h.variante_bloque_id
             WHERE h.id = ?1 AND h.variante_bloque_id = ?2",
            params![entrada, variante_id],
            |r| Ok((r.get(0)?, r.get(1)?)),
        )
        .optional()?
        .ok_or(Error::NoExiste("entrada del historial"))?;
    // Otro «dispositivo» para que lo de ahora entre siempre en el historial,
    // aunque se acabe de editar desde este mismo.
    guardar_variante_bloque(c, variante_id, &contenido, rev, &format!("{dispositivo}:restaurar"))
}

pub fn resolver_marca(c: &mut Connection, variante_id: &str, indice: usize, d: critic::Decision, dispositivo: &str) -> Res<Guardado> {
    let v = variante_bloque(c, variante_id)?;
    let nuevo = critic::resolver(&v.contenido, indice, d).ok_or(Error::NoExiste("marca"))?;
    guardar_variante_bloque(c, variante_id, &nuevo, v.rev, dispositivo)
}

// ----------------------------------------------------------------- Medios

#[derive(Debug, Serialize, Clone)]
pub struct Medio {
    pub id: String,
    pub sha256: String,
    pub nombre: String,
    pub tipo: String,
    pub tamano: i64,
    pub creado: String,
}

pub fn registrar_medio(c: &mut Connection, sha256: &str, nombre: &str, tipo: &str, tamano: i64) -> Res<Medio> {
    let tx = c.transaction()?;
    let rev = siguiente_rev(&tx)?;
    let id = id_para(|d| &mut d.medio);
    tx.execute(
        "INSERT INTO medios (id, sha256, nombre, tipo, tamano, creado, rev) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)",
        params![id, sha256, nombre, tipo, tamano, ahora(), rev],
    )?;
    tx.commit()?;
    medio(c, &id)
}

pub fn medio(c: &Connection, id: &str) -> Res<Medio> {
    c.query_row("SELECT id, sha256, nombre, tipo, tamano, creado FROM medios WHERE id = ?1 AND borrado = 0", params![id], |r| {
        Ok(Medio { id: r.get(0)?, sha256: r.get(1)?, nombre: r.get(2)?, tipo: r.get(3)?, tamano: r.get(4)?, creado: r.get(5)? })
    })
    .optional()?
    .ok_or(Error::NoExiste("medio"))
}

#[cfg(test)]
mod pruebas {
    use super::*;
    use crate::db::en_memoria;

    fn nuevo(c: &mut Connection) -> (Articulo, Documento) {
        let a = crear_articulo(c, "¿Por qué llama-swap?").unwrap();
        let rama = a.idiomas[0].resumen.rama_principal_id.clone().unwrap();
        let d = documento(c, &rama).unwrap();
        (a, d)
    }

    fn primer_bloque(d: &Documento) -> (&Bloque, &VarianteBloque) {
        let b = &d.secciones[0].variantes[0].bloques[0];
        (b, &b.variantes[0])
    }

    #[test]
    fn crear_articulo_deja_todo_listo_para_escribir() {
        let mut c = en_memoria();
        let (a, d) = nuevo(&mut c);
        assert_eq!(a.slug, "por-que-llama-swap");
        assert_eq!(a.estado, "idea");
        assert_eq!(a.idiomas.len(), 1);
        assert_eq!(d.secciones.len(), 1);
        assert_eq!(d.secciones[0].variantes[0].titulo, "");
        let (b, v) = primer_bloque(&d);
        assert_eq!(b.tipo, "parrafo");
        assert_eq!(b.variante_activa_id.as_ref(), Some(&v.id));
        // Un segundo artículo con el mismo título recibe otro slug.
        assert_eq!(crear_articulo(&mut c, "¿Por qué llama-swap?").unwrap().slug, "por-que-llama-swap-2");
    }

    #[test]
    fn guardar_y_conflicto() {
        let mut c = en_memoria();
        let (_, d) = nuevo(&mut c);
        let (_, v) = primer_bloque(&d);
        let g = guardar_variante_bloque(&mut c, &v.id, "hola", v.rev, "web").unwrap();
        assert!(!g.conflicto);
        // El móvil editó sobre la versión vieja: no pisa, crea una variante.
        let g2 = guardar_variante_bloque(&mut c, &v.id, "hola desde el móvil", v.rev, "android").unwrap();
        assert!(g2.conflicto);
        assert_eq!(g2.variante.origen, "conflicto");
        assert_eq!(variante_bloque(&c, &v.id).unwrap().contenido, "hola");
        // Mismo texto sobre versión vieja no es conflicto.
        assert!(!guardar_variante_bloque(&mut c, &v.id, "hola", v.rev, "android").unwrap().conflicto);
        let a = listar_articulos(&c).unwrap();
        assert_eq!(a[0].senales.conflictos, 1);
    }

    #[test]
    fn historial_agrupa_y_restaura() {
        let mut c = en_memoria();
        let (_, d) = nuevo(&mut c);
        let (_, v) = primer_bloque(&d);
        let mut rev = v.rev;
        for t in ["uno", "dos", "tres"] {
            rev = guardar_variante_bloque(&mut c, &v.id, t, rev, "web").unwrap().variante.rev;
        }
        // Tres guardados seguidos del mismo dispositivo: una sola entrada.
        let h = historial(&c, &v.id).unwrap();
        assert_eq!(h.len(), 1);
        assert_eq!(h[0].contenido, "");
        rev = guardar_variante_bloque(&mut c, &v.id, "cuatro", rev, "android").unwrap().variante.rev;
        let _ = rev;
        let h = historial(&c, &v.id).unwrap();
        assert_eq!(h[0].contenido, "tres");
        let g = restaurar(&mut c, &v.id, h[0].id, "web").unwrap();
        assert_eq!(g.variante.contenido, "tres");
        assert_eq!(historial(&c, &v.id).unwrap()[0].contenido, "cuatro");
    }

    #[test]
    fn variantes_de_bloque_y_seccion() {
        let mut c = en_memoria();
        let (_, d) = nuevo(&mut c);
        let (b, v) = primer_bloque(&d);
        guardar_variante_bloque(&mut c, &v.id, "A", v.rev, "web").unwrap();
        let (d, nueva) = fork_bloque(&mut c, &b.id, ForkBloque { contenido: None, activar: true, origen: "tu".into() }).unwrap();
        let (b2, _) = primer_bloque(&d);
        assert_eq!(b2.variantes.len(), 2);
        assert_eq!(b2.variante_activa_id.as_ref(), Some(&nueva));
        assert_eq!(b2.variantes[1].contenido, "A");
        // Propuesta del agente: no se activa.
        let (d, _) = fork_bloque(&mut c, &b.id, ForkBloque { contenido: Some("C".into()), activar: false, origen: "ia:claude".into() }).unwrap();
        assert_eq!(primer_bloque(&d).0.variante_activa_id.as_ref(), Some(&nueva));
        assert_eq!(listar_articulos(&c).unwrap()[0].senales.propuestas, 1);
        // Borrar la activa activa otra.
        let d = borrar_variante_bloque(&mut c, &nueva).unwrap();
        assert_eq!(primer_bloque(&d).0.variantes.len(), 2);
        assert!(primer_bloque(&d).0.variante_activa_id.is_some());

        let sid = d.secciones[0].id.clone();
        let (d, vs) = fork_seccion(&mut c, &sid, ForkSeccion { desde: None, activar: true, origen: "tu".into() }).unwrap();
        assert_eq!(d.secciones[0].variantes.len(), 2);
        assert_eq!(d.secciones[0].variante_activa_id.as_ref(), Some(&vs));
        // La copia solo lleva la variante activa de cada bloque.
        let copia = d.secciones[0].variantes.iter().find(|v| v.id == vs).unwrap();
        assert_eq!(copia.bloques[0].variantes.len(), 1);
    }

    #[test]
    fn secciones_y_bloques_en_orden() {
        let mut c = en_memoria();
        let (_, d) = nuevo(&mut c);
        let rama = d.rama.id.clone();
        let d = crear_seccion(&mut c, &rama, NuevaSeccion { titulo: "Final".into(), entre: Entre::default() }).unwrap();
        let primera = d.secciones[0].id.clone();
        let d = crear_seccion(&mut c, &rama, NuevaSeccion { titulo: "Medio".into(), entre: Entre { antes: Some(primera), despues: None } }).unwrap();
        let titulos: Vec<_> = d.secciones.iter().map(|s| s.variantes[0].titulo.clone()).collect();
        assert_eq!(titulos, ["", "Medio", "Final"]);

        let vs = d.secciones[0].variantes[0].id.clone();
        let b0 = d.secciones[0].variantes[0].bloques[0].id.clone();
        let (_, b1) = crear_bloque(&mut c, &vs, NuevoBloque { tipo: "codigo".into(), contenido: "```\nx\n```".into(), entre: Entre::default() }).unwrap();
        let (d, _) = crear_bloque(&mut c, &vs, NuevoBloque { tipo: "parrafo".into(), contenido: "en medio".into(), entre: Entre { antes: Some(b0.clone()), despues: None } }).unwrap();
        let tipos: Vec<_> = d.secciones[0].variantes[0].bloques.iter().map(|b| b.tipo.clone()).collect();
        assert_eq!(tipos, ["parrafo", "parrafo", "codigo"]);
        // Mover el código al principio.
        let d = actualizar_bloque(&mut c, &b1, CambioBloque { mover: Some(Entre { antes: None, despues: Some(b0) }), ..Default::default() }).unwrap();
        assert_eq!(d.secciones[0].variantes[0].bloques[0].tipo, "codigo");
    }

    #[test]
    fn ramas_e_idiomas() {
        let mut c = en_memoria();
        let (a, d) = nuevo(&mut c);
        let (_, v) = primer_bloque(&d);
        guardar_variante_bloque(&mut c, &v.id, "texto", v.rev, "web").unwrap();
        let f = fork_rama(&mut c, &d.rama.id, "enfoque-tutorial").unwrap();
        assert_eq!(f.rama.origen_id.as_ref(), Some(&d.rama.id));
        assert_eq!(primer_bloque(&f).1.contenido, "texto");
        assert!(borrar_rama(&mut c, &d.rama.id).is_err());
        let iid = a.idiomas[0].resumen.id.clone();
        actualizar_idioma(&mut c, &iid, CambioIdioma { rama_principal_id: Some(f.rama.id.clone()), ..Default::default() }).unwrap();
        borrar_rama(&mut c, &d.rama.id).unwrap();

        let a = crear_idioma(&mut c, &a.id, "en", Some("es")).unwrap();
        assert_eq!(a.idiomas.len(), 2);
        let en = a.idiomas.iter().find(|i| i.resumen.idioma == "en").unwrap();
        let den = documento(&c, en.resumen.rama_principal_id.as_ref().unwrap()).unwrap();
        assert_eq!(primer_bloque(&den).1.contenido, "texto");
        assert!(crear_idioma(&mut c, &a.id, "en", None).is_err());
    }

    #[test]
    fn etiquetas_series_y_campos() {
        let mut c = en_memoria();
        let (a, _) = nuevo(&mut c);
        let s = crear_serie(&mut c, "Montando ai-lab").unwrap();
        let a = actualizar_articulo(
            &mut c,
            &a.id,
            CambioArticulo {
                estado: Some("borrador".into()),
                serie_id: Some(Some(s.id.clone())),
                serie_parte: Some(Some(2)),
                fecha: Some(Some("2026-11-02".into())),
                etiquetas: Some(vec!["IA".into(), "ai-lab".into(), " ".into()]),
                ..Default::default()
            },
        )
        .unwrap();
        assert_eq!(a.etiquetas, ["ai-lab", "ia"]);
        assert_eq!(a.serie.as_ref().unwrap().nombre, "Montando ai-lab");
        assert_eq!(listar_series(&c).unwrap()[0].articulos, 1);
        assert_eq!(listar_etiquetas(&c).unwrap().len(), 2);
        assert!(actualizar_articulo(&mut c, &a.id, CambioArticulo { estado: Some("raro".into()), ..Default::default() }).is_err());
        assert!(actualizar_articulo(&mut c, &a.id, CambioArticulo { fecha: Some(Some("2 nov".into())), ..Default::default() }).is_err());
        borrar_serie(&mut c, &s.id).unwrap();
        assert!(articulo(&c, &a.id).unwrap().serie.is_none());
    }

    #[test]
    fn resolver_marcas_guarda() {
        let mut c = en_memoria();
        let (_, d) = nuevo(&mut c);
        let (_, v) = primer_bloque(&d);
        guardar_variante_bloque(&mut c, &v.id, "El {~~ttl dice~>campo ttl marca~~}{>>ia:claude<<} algo", v.rev, "web").unwrap();
        let g = resolver_marca(&mut c, &v.id, 0, critic::Decision::Aceptar, "web").unwrap();
        assert_eq!(g.variante.contenido, "El campo ttl marca algo");
    }

    #[test]
    fn escritos_de_una_vez() {
        let mut c = en_memoria();
        let (a, d) = nuevo(&mut c);
        let sid = d.secciones[0].id.clone();
        let bloques = vec![
            BloqueEscrito { tipo: "parrafo".into(), contenido: "uno".into() },
            BloqueEscrito { tipo: "codigo".into(), contenido: "```\nx\n```".into() },
        ];
        let (d, vs) = crear_variante_seccion(&mut c, &sid, "Otra idea", &bloques, "ia:claude").unwrap();
        let s = &d.secciones[0];
        assert_ne!(s.variante_activa_id.as_ref(), Some(&vs));
        let v = s.variantes.iter().find(|v| v.id == vs).unwrap();
        assert_eq!((v.origen.as_str(), v.bloques.len(), v.bloques[1].tipo.as_str()), ("ia:claude", 2, "codigo"));
        assert!(crear_variante_seccion(&mut c, &sid, "", &[], "ia:c").is_err());

        let secs = vec![SeccionEscrita { titulo: String::new(), bloques: bloques.clone() }, SeccionEscrita { titulo: "Setup".into(), bloques }];
        let a = crear_idioma_escrito(&mut c, &a.id, "en", "Why llama-swap?", "One container", &secs, "ia:claude").unwrap();
        let en = a.idiomas.iter().find(|i| i.resumen.idioma == "en").unwrap();
        assert!(en.resumen.traducido_ia && en.resumen.borrador);
        let den = documento(&c, en.resumen.rama_principal_id.as_ref().unwrap()).unwrap();
        assert_eq!(den.secciones.len(), 2);
        assert_eq!(den.secciones[1].variantes[0].titulo, "Setup");
        assert!(crear_idioma_escrito(&mut c, &a.id, "en", "x", "", &secs, "ia:c").is_err());
    }

    #[test]
    fn borrar_y_restaurar_articulo() {
        let mut c = en_memoria();
        let (a, _) = nuevo(&mut c);
        let nueva = |fecha: &str, estado: &str| crate::calendario::NuevaPublicacion {
            canal_id: "x".into(), formato: "post".into(), fecha: fecha.into(), hora: None, estado: estado.into(),
            titulo: String::new(), partes: None, articulo_id: Some(a.id.clone()), origen: "tu".into(),
        };
        let idea = crate::calendario::crear_publicacion(&mut c, nueva("2026-11-01", "idea")).unwrap();
        let hecha = crate::calendario::crear_publicacion(&mut c, nueva("2026-10-01", "publicada")).unwrap();
        borrar_articulo(&mut c, &a.id).unwrap();
        assert!(listar_articulos(&c).unwrap().is_empty());
        assert!(crate::calendario::publicacion(&c, &idea.id).is_err());
        assert!(crate::calendario::publicacion(&c, &hecha.id).is_ok(), "la publicada se queda");
        // Otro artículo toma su slug mientras está borrado.
        let otro = crear_articulo(&mut c, "¿Por qué llama-swap?").unwrap();
        assert_eq!(otro.slug, a.slug);
        let r = restaurar_articulo(&mut c, &a.id).unwrap();
        assert_ne!(r.slug, otro.slug);
        assert!(crate::calendario::publicacion(&c, &idea.id).is_ok());
        assert!(restaurar_articulo(&mut c, &a.id).is_err());
    }
}
