//! Calendario: canales y publicaciones (fase 2 del plan).
//!
//! Tinta no publica: prepara el texto, lo pone en su día y recuerda. Rubén
//! publica a mano y guarda el enlace al marcar la publicación como publicada.
//!
//! Una publicación en el canal «portfolio» que sale de un artículo manda en
//! la fecha de ese artículo: es la `date` que lleva su `.md` al exportarlo.

use crate::db::{existe, siguiente_rev};
use crate::modelo::{Error, Res, ahora, nuevo_id};
use rusqlite::{Connection, OptionalExtension, Row, params};
use serde::{Deserialize, Serialize};

pub const ESTADOS: [&str; 4] = ["idea", "preparando", "programada", "publicada"];

/// Zona horaria de Rubén (`TINTA_ZONA`, por ejemplo `America/Bogota`). Las
/// fechas del calendario no llevan zona: «hoy» se calcula en esta.
pub fn zona() -> chrono_tz::Tz {
    static ZONA: std::sync::OnceLock<chrono_tz::Tz> = std::sync::OnceLock::new();
    *ZONA.get_or_init(|| std::env::var("TINTA_ZONA").ok().and_then(|z| z.parse().ok()).unwrap_or(chrono_tz::UTC))
}

/// La fecha de hoy en la zona de Rubén, AAAA-MM-DD.
pub fn hoy() -> String {
    chrono::Utc::now().with_timezone(&zona()).format("%Y-%m-%d").to_string()
}
pub const PORTFOLIO: &str = "portfolio";

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct Formato {
    pub id: String,
    pub nombre: String,
    /// Caracteres por parte; `None` si el canal no pone límite.
    pub limite: Option<u32>,
    /// true si admite varias partes, como un hilo de X.
    pub partes: bool,
}

#[derive(Debug, Serialize)]
pub struct Canal {
    pub id: String,
    pub nombre: String,
    pub icono: String,
    pub formatos: Vec<Formato>,
    pub activo: bool,
    pub posicion: String,
}

fn leer_canal(r: &Row) -> rusqlite::Result<Canal> {
    let f: String = r.get("formatos")?;
    Ok(Canal {
        id: r.get("id")?,
        nombre: r.get("nombre")?,
        icono: r.get("icono")?,
        formatos: serde_json::from_str(&f).unwrap_or_default(),
        activo: r.get("activo")?,
        posicion: r.get("posicion")?,
    })
}

pub fn listar_canales(c: &Connection) -> Res<Vec<Canal>> {
    Ok(c.prepare("SELECT * FROM canales WHERE borrado = 0 ORDER BY posicion")?
        .query_map([], leer_canal)?
        .collect::<Result<_, _>>()?)
}

pub fn canal(c: &Connection, id: &str) -> Res<Canal> {
    c.query_row("SELECT * FROM canales WHERE id = ?1 AND borrado = 0", params![id], leer_canal)
        .optional()?
        .ok_or(Error::NoExiste("canal"))
}

#[derive(Debug, Deserialize)]
pub struct CambioCanal {
    pub nombre: Option<String>,
    pub activo: Option<bool>,
    pub formatos: Option<Vec<Formato>>,
}

pub fn actualizar_canal(c: &mut Connection, id: &str, cambio: CambioCanal) -> Res<Canal> {
    let tx = c.transaction()?;
    if !existe(&tx, "canales", id)? {
        return Err(Error::NoExiste("canal"));
    }
    let rev = siguiente_rev(&tx)?;
    if let Some(n) = cambio.nombre {
        tx.execute("UPDATE canales SET nombre = ?2 WHERE id = ?1", params![id, n.trim()])?;
    }
    if let Some(a) = cambio.activo {
        tx.execute("UPDATE canales SET activo = ?2 WHERE id = ?1", params![id, a])?;
    }
    if let Some(f) = cambio.formatos {
        if f.is_empty() {
            return Err(Error::Invalido("un canal necesita al menos un formato".into()));
        }
        tx.execute("UPDATE canales SET formatos = ?2 WHERE id = ?1", params![id, serde_json::to_string(&f).unwrap()])?;
    }
    tx.execute("UPDATE canales SET rev = ?2 WHERE id = ?1", params![id, rev])?;
    tx.commit()?;
    canal(c, id)
}

#[derive(Debug, Deserialize)]
pub struct NuevoCanal {
    pub nombre: String,
    /// Uno de los iconos de la web: x, linkedin, instagram, portfolio, youtube, tiktok.
    pub icono: String,
    pub formatos: Vec<Formato>,
}

pub fn crear_canal(c: &mut Connection, n: NuevoCanal) -> Res<Canal> {
    let nombre = n.nombre.trim();
    if nombre.is_empty() || n.formatos.is_empty() {
        return Err(Error::Invalido("un canal necesita nombre y al menos un formato".into()));
    }
    let tx = c.transaction()?;
    let rev = siguiente_rev(&tx)?;
    let ultima: Option<String> = tx.query_row("SELECT max(posicion) FROM canales", [], |r| r.get(0))?;
    let pos = crate::posicion::entre(ultima.as_deref(), None);
    let id = nuevo_id();
    tx.execute(
        "INSERT INTO canales (id, nombre, icono, formatos, posicion, rev) VALUES (?1, ?2, ?3, ?4, ?5, ?6)",
        params![id, nombre, n.icono, serde_json::to_string(&n.formatos).unwrap(), pos, rev],
    )?;
    tx.commit()?;
    canal(c, &id)
}

#[derive(Debug, Serialize, Deserialize, Clone, PartialEq)]
pub struct Parte {
    #[serde(default)]
    pub texto: String,
    #[serde(default)]
    pub medios: Vec<String>,
}

#[derive(Debug, Serialize)]
pub struct ArticuloBreve {
    pub id: String,
    pub titulo: String,
    pub slug: String,
}

#[derive(Debug, Serialize)]
pub struct Publicacion {
    pub id: String,
    pub canal_id: String,
    pub formato: String,
    pub fecha: String,
    pub hora: Option<String>,
    pub estado: String,
    pub titulo: String,
    pub partes: Vec<Parte>,
    pub articulo: Option<ArticuloBreve>,
    pub enlace: Option<String>,
    pub origen: String,
    pub creado: String,
    pub cambiado: String,
    pub rev: i64,
}

const SELECT: &str = "SELECT p.*, a.slug AS a_slug,
    (SELECT i.titulo FROM idiomas i WHERE i.articulo_id = a.id AND i.borrado = 0 ORDER BY i.idioma = 'es' DESC LIMIT 1) AS a_titulo
    FROM publicaciones p LEFT JOIN articulos a ON a.id = p.articulo_id AND a.borrado = 0";

fn leer_publicacion(r: &Row) -> rusqlite::Result<Publicacion> {
    let partes: String = r.get("partes")?;
    let aid: Option<String> = r.get("articulo_id")?;
    let slug: Option<String> = r.get("a_slug")?;
    Ok(Publicacion {
        id: r.get("id")?,
        canal_id: r.get("canal_id")?,
        formato: r.get("formato")?,
        fecha: r.get("fecha")?,
        hora: r.get("hora")?,
        estado: r.get("estado")?,
        titulo: r.get("titulo")?,
        partes: serde_json::from_str(&partes).unwrap_or_default(),
        articulo: match (aid, slug) {
            (Some(id), Some(slug)) => Some(ArticuloBreve { id, slug, titulo: r.get::<_, Option<String>>("a_titulo")?.unwrap_or_default() }),
            _ => None,
        },
        enlace: r.get("enlace")?,
        origen: r.get("origen")?,
        creado: r.get("creado")?,
        cambiado: r.get("cambiado")?,
        rev: r.get("rev")?,
    })
}

#[derive(Debug, Deserialize, Default)]
pub struct Filtro {
    /// AAAA-MM-DD, incluido.
    pub desde: Option<String>,
    /// AAAA-MM-DD, incluido.
    pub hasta: Option<String>,
    pub canal: Option<String>,
    pub articulo: Option<String>,
}

pub fn listar_publicaciones(c: &Connection, f: &Filtro) -> Res<Vec<Publicacion>> {
    let sql = format!(
        "{SELECT} WHERE p.borrado = 0
         AND (?1 IS NULL OR p.fecha >= ?1) AND (?2 IS NULL OR p.fecha <= ?2)
         AND (?3 IS NULL OR p.canal_id = ?3) AND (?4 IS NULL OR p.articulo_id = ?4)
         ORDER BY p.fecha, p.hora IS NULL, p.hora, p.creado"
    );
    Ok(c.prepare(&sql)?
        .query_map(params![f.desde, f.hasta, f.canal, f.articulo], leer_publicacion)?
        .collect::<Result<_, _>>()?)
}

pub fn publicacion(c: &Connection, id: &str) -> Res<Publicacion> {
    c.query_row(&format!("{SELECT} WHERE p.id = ?1 AND p.borrado = 0"), params![id], leer_publicacion)
        .optional()?
        .ok_or(Error::NoExiste("publicación"))
}

fn validar_fecha(f: &str) -> Res<()> {
    chrono::NaiveDate::parse_from_str(f, "%Y-%m-%d")
        .map(|_| ())
        .map_err(|_| Error::Invalido(format!("fecha no válida (AAAA-MM-DD): {f}")))
}

fn validar_hora(h: &str) -> Res<()> {
    chrono::NaiveTime::parse_from_str(h, "%H:%M").map(|_| ()).map_err(|_| Error::Invalido(format!("hora no válida (HH:MM): {h}")))
}

fn validar(c: &Connection, canal_id: &str, formato: &str, estado: &str, partes: &[Parte]) -> Res<()> {
    let canal = canal(c, canal_id)?;
    let f = canal
        .formatos
        .iter()
        .find(|f| f.id == formato)
        .ok_or_else(|| Error::Invalido(format!("{} no tiene el formato «{formato}»", canal.nombre)))?;
    if !ESTADOS.contains(&estado) {
        return Err(Error::Invalido(format!("estado desconocido: {estado}")));
    }
    if partes.is_empty() {
        return Err(Error::Invalido("una publicación necesita al menos una parte".into()));
    }
    if !f.partes && partes.len() > 1 {
        return Err(Error::Invalido(format!("un {} de {} tiene una sola parte", f.nombre.to_lowercase(), canal.nombre)));
    }
    Ok(())
}

/// La fecha de una publicación del portfolio es la del artículo.
fn sincronizar_fecha_articulo(c: &Connection, publicacion_id: &str, rev: i64) -> Res<()> {
    c.execute(
        "UPDATE articulos SET fecha = p.fecha, rev = ?2, cambiado = ?3
         FROM publicaciones p
         WHERE p.id = ?1 AND p.canal_id = ?4 AND p.borrado = 0 AND articulos.id = p.articulo_id
           AND articulos.fecha IS NOT p.fecha",
        params![publicacion_id, rev, ahora(), PORTFOLIO],
    )?;
    Ok(())
}

#[derive(Debug, Deserialize)]
pub struct NuevaPublicacion {
    pub canal_id: String,
    pub formato: String,
    pub fecha: String,
    pub hora: Option<String>,
    #[serde(default = "idea")]
    pub estado: String,
    #[serde(default)]
    pub titulo: String,
    pub partes: Option<Vec<Parte>>,
    pub articulo_id: Option<String>,
    #[serde(default = "tu")]
    pub origen: String,
}

fn idea() -> String {
    "idea".into()
}
fn tu() -> String {
    "tu".into()
}

pub fn crear_publicacion(c: &mut Connection, n: NuevaPublicacion) -> Res<Publicacion> {
    let tx = c.transaction()?;
    let id = insertar(&tx, n)?;
    tx.commit()?;
    publicacion(c, &id)
}

fn insertar(c: &Connection, n: NuevaPublicacion) -> Res<String> {
    let partes = n.partes.unwrap_or_else(|| vec![Parte { texto: String::new(), medios: vec![] }]);
    validar(c, &n.canal_id, &n.formato, &n.estado, &partes)?;
    validar_fecha(&n.fecha)?;
    if let Some(h) = &n.hora {
        validar_hora(h)?;
    }
    if let Some(a) = &n.articulo_id
        && !existe(c, "articulos", a)?
    {
        return Err(Error::NoExiste("artículo"));
    }
    let rev = siguiente_rev(c)?;
    let id = crate::modelo::id_para(|d| &mut d.publicacion);
    let t = ahora();
    c.execute(
        "INSERT INTO publicaciones (id, canal_id, formato, fecha, hora, estado, titulo, partes, articulo_id, origen, creado, cambiado, rev)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?11, ?12)",
        params![
            id,
            n.canal_id,
            n.formato,
            n.fecha,
            n.hora,
            n.estado,
            n.titulo.trim(),
            serde_json::to_string(&partes).unwrap(),
            n.articulo_id,
            n.origen,
            t,
            rev
        ],
    )?;
    sincronizar_fecha_articulo(c, &id, rev)?;
    Ok(id)
}

#[derive(Debug, Deserialize, Default)]
pub struct CambioPublicacion {
    pub canal_id: Option<String>,
    pub formato: Option<String>,
    pub fecha: Option<String>,
    #[serde(default, with = "doble")]
    pub hora: Option<Option<String>>,
    pub estado: Option<String>,
    pub titulo: Option<String>,
    pub partes: Option<Vec<Parte>>,
    #[serde(default, with = "doble")]
    pub articulo_id: Option<Option<String>>,
    #[serde(default, with = "doble")]
    pub enlace: Option<Option<String>>,
}

mod doble {
    use serde::{Deserialize, Deserializer};
    pub fn deserialize<'de, D: Deserializer<'de>, T: Deserialize<'de>>(d: D) -> Result<Option<Option<T>>, D::Error> {
        Option::<T>::deserialize(d).map(Some)
    }
}

pub fn actualizar_publicacion(c: &mut Connection, id: &str, cambio: CambioPublicacion) -> Res<Publicacion> {
    actualizar_publicacion_como(c, id, cambio, "tu")
}

/// Como [`actualizar_publicacion`], pero dejando como autor `origen`: el MCP
/// firma así lo que cambia un agente.
pub fn actualizar_publicacion_como(c: &mut Connection, id: &str, cambio: CambioPublicacion, origen: &str) -> Res<Publicacion> {
    let tx = c.transaction()?;
    let p = publicacion(&tx, id)?;
    let canal_id = cambio.canal_id.unwrap_or(p.canal_id);
    let formato = cambio.formato.unwrap_or(p.formato);
    let estado = cambio.estado.unwrap_or(p.estado);
    let partes = cambio.partes.unwrap_or(p.partes);
    validar(&tx, &canal_id, &formato, &estado, &partes)?;
    let fecha = cambio.fecha.unwrap_or(p.fecha);
    validar_fecha(&fecha)?;
    let hora = cambio.hora.unwrap_or(p.hora);
    if let Some(h) = &hora {
        validar_hora(h)?;
    }
    let articulo_id = cambio.articulo_id.unwrap_or(p.articulo.map(|a| a.id));
    if let Some(a) = &articulo_id
        && !existe(&tx, "articulos", a)?
    {
        return Err(Error::NoExiste("artículo"));
    }
    let enlace = cambio.enlace.unwrap_or(p.enlace).map(|e| e.trim().to_string()).filter(|e| !e.is_empty());
    let titulo = cambio.titulo.map(|t| t.trim().to_string()).unwrap_or(p.titulo);
    let rev = siguiente_rev(&tx)?;
    tx.execute(
        "UPDATE publicaciones SET canal_id = ?2, formato = ?3, fecha = ?4, hora = ?5, estado = ?6, titulo = ?7,
           partes = ?8, articulo_id = ?9, enlace = ?10, origen = ?13, cambiado = ?11, rev = ?12 WHERE id = ?1",
        params![id, canal_id, formato, fecha, hora, estado, titulo, serde_json::to_string(&partes).unwrap(), articulo_id, enlace, ahora(), rev, origen],
    )?;
    sincronizar_fecha_articulo(&tx, id, rev)?;
    tx.commit()?;
    publicacion(c, id)
}

pub fn borrar_publicacion(c: &mut Connection, id: &str) -> Res<()> {
    let tx = c.transaction()?;
    let rev = siguiente_rev(&tx)?;
    if tx.execute("UPDATE publicaciones SET borrado = 1, rev = ?2 WHERE id = ?1 AND borrado = 0", params![id, rev])? == 0 {
        return Err(Error::NoExiste("publicación"));
    }
    tx.commit()?;
    Ok(())
}

#[derive(Debug, Deserialize)]
pub struct Plan {
    pub canal_id: String,
    pub formato: String,
    pub fecha: String,
    pub hora: Option<String>,
}

/// «Planear difusión»: varias publicaciones de un artículo de una vez. Cada
/// una nace como idea, con el título del artículo y, salvo en el portfolio,
/// su descripción como primer borrador del texto.
pub fn planear_difusion(c: &mut Connection, articulo_id: &str, planes: Vec<Plan>) -> Res<Vec<Publicacion>> {
    if planes.is_empty() {
        return Err(Error::Invalido("elige al menos un canal".into()));
    }
    let tx = c.transaction()?;
    let (titulo, descripcion): (String, String) = tx
        .query_row(
            "SELECT i.titulo, i.descripcion FROM idiomas i JOIN articulos a ON a.id = i.articulo_id
             WHERE a.id = ?1 AND a.borrado = 0 AND i.borrado = 0 ORDER BY i.idioma = 'es' DESC LIMIT 1",
            params![articulo_id],
            |r| Ok((r.get(0)?, r.get(1)?)),
        )
        .optional()?
        .ok_or(Error::NoExiste("artículo"))?;
    let mut ids = Vec::new();
    for p in planes {
        let texto = if p.canal_id == PORTFOLIO { String::new() } else { descripcion.clone() };
        ids.push(insertar(
            &tx,
            NuevaPublicacion {
                canal_id: p.canal_id,
                formato: p.formato,
                fecha: p.fecha,
                hora: p.hora,
                estado: "idea".into(),
                titulo: titulo.clone(),
                partes: Some(vec![Parte { texto, medios: vec![] }]),
                articulo_id: Some(articulo_id.into()),
                origen: "tu".into(),
            },
        )?);
    }
    tx.commit()?;
    ids.iter().map(|id| publicacion(c, id)).collect()
}

/// Lo siguiente que toca publicar de un artículo, para la lista de artículos.
#[derive(Debug, Serialize)]
pub struct Proxima {
    pub canal_id: String,
    pub fecha: String,
    pub hora: Option<String>,
}

pub fn proxima(c: &Connection, articulo_id: &str, hoy: &str) -> Res<Option<Proxima>> {
    Ok(c.query_row(
        "SELECT canal_id, fecha, hora FROM publicaciones
         WHERE articulo_id = ?1 AND borrado = 0 AND estado != 'publicada' AND fecha >= ?2
         ORDER BY fecha, hora IS NULL, hora LIMIT 1",
        params![articulo_id, hoy],
        |r| Ok(Proxima { canal_id: r.get(0)?, fecha: r.get(1)?, hora: r.get(2)? }),
    )
    .optional()?)
}

#[cfg(test)]
mod pruebas {
    use super::*;
    use crate::db::en_memoria;
    use crate::modelo;

    fn nueva(canal: &str, formato: &str, fecha: &str) -> NuevaPublicacion {
        NuevaPublicacion {
            canal_id: canal.into(),
            formato: formato.into(),
            fecha: fecha.into(),
            hora: None,
            estado: "idea".into(),
            titulo: String::new(),
            partes: None,
            articulo_id: None,
            origen: "tu".into(),
        }
    }

    #[test]
    fn canales_de_serie() {
        let c = en_memoria();
        let cs = listar_canales(&c).unwrap();
        let ids: Vec<_> = cs.iter().map(|c| c.id.as_str()).collect();
        assert_eq!(ids, ["x", "linkedin", "instagram", "portfolio", "youtube", "tiktok"]);
        assert!(!cs[4].activo && cs[0].activo);
        assert_eq!(cs[0].formatos[1].limite, Some(280));
        assert!(cs[0].formatos[1].partes);
    }

    #[test]
    fn crear_validar_y_mover() {
        let mut c = en_memoria();
        let p = crear_publicacion(&mut c, nueva("x", "hilo", "2026-11-02")).unwrap();
        assert_eq!(p.partes.len(), 1);
        assert!(crear_publicacion(&mut c, nueva("x", "carrusel", "2026-11-02")).is_err());
        assert!(crear_publicacion(&mut c, nueva("x", "post", "2 nov")).is_err());
        assert!(crear_publicacion(&mut c, nueva("nada", "post", "2026-11-02")).is_err());
        let dos = vec![Parte { texto: "1".into(), medios: vec![] }, Parte { texto: "2".into(), medios: vec![] }];
        let p = actualizar_publicacion(
            &mut c,
            &p.id,
            CambioPublicacion { partes: Some(dos.clone()), hora: Some(Some("18:00".into())), estado: Some("preparando".into()), ..Default::default() },
        )
        .unwrap();
        assert_eq!(p.partes, dos);
        assert_eq!(p.hora.as_deref(), Some("18:00"));
        // Un post no admite dos partes.
        assert!(actualizar_publicacion(&mut c, &p.id, CambioPublicacion { formato: Some("post".into()), ..Default::default() }).is_err());
        let p = actualizar_publicacion(
            &mut c,
            &p.id,
            CambioPublicacion { fecha: Some("2026-11-03".into()), hora: Some(None), estado: Some("publicada".into()), enlace: Some(Some(" https://x.com/a/1 ".into())), ..Default::default() },
        )
        .unwrap();
        assert_eq!((p.fecha.as_str(), p.hora.is_none(), p.enlace.as_deref()), ("2026-11-03", true, Some("https://x.com/a/1")));
        let f = Filtro { desde: Some("2026-11-01".into()), hasta: Some("2026-11-30".into()), ..Default::default() };
        assert_eq!(listar_publicaciones(&c, &f).unwrap().len(), 1);
        borrar_publicacion(&mut c, &p.id).unwrap();
        assert!(listar_publicaciones(&c, &f).unwrap().is_empty());
    }

    #[test]
    fn difusion_y_fecha_del_portfolio() {
        let mut c = en_memoria();
        let a = modelo::crear_articulo(&mut c, "llama-swap").unwrap();
        let iid = a.idiomas[0].resumen.id.clone();
        modelo::actualizar_idioma(&mut c, &iid, modelo::CambioIdioma { descripcion: Some("Un contenedor para todo".into()), ..Default::default() }).unwrap();
        let ps = planear_difusion(
            &mut c,
            &a.id,
            vec![
                Plan { canal_id: "portfolio".into(), formato: "articulo".into(), fecha: "2026-11-02".into(), hora: None },
                Plan { canal_id: "x".into(), formato: "hilo".into(), fecha: "2026-11-03".into(), hora: Some("18:00".into()) },
            ],
        )
        .unwrap();
        assert_eq!(ps.len(), 2);
        assert_eq!(ps[1].partes[0].texto, "Un contenedor para todo");
        assert_eq!(ps[0].partes[0].texto, "");
        assert_eq!(ps[1].articulo.as_ref().unwrap().titulo, "llama-swap");
        // La publicación del portfolio fija la fecha del artículo, y moverla la mueve.
        assert_eq!(modelo::articulo(&c, &a.id).unwrap().fecha.as_deref(), Some("2026-11-02"));
        actualizar_publicacion(&mut c, &ps[0].id, CambioPublicacion { fecha: Some("2026-11-09".into()), ..Default::default() }).unwrap();
        assert_eq!(modelo::articulo(&c, &a.id).unwrap().fecha.as_deref(), Some("2026-11-09"));
        // Mover la de X no la toca.
        actualizar_publicacion(&mut c, &ps[1].id, CambioPublicacion { fecha: Some("2026-11-20".into()), ..Default::default() }).unwrap();
        assert_eq!(modelo::articulo(&c, &a.id).unwrap().fecha.as_deref(), Some("2026-11-09"));
        let pr = proxima(&c, &a.id, "2026-11-05").unwrap().unwrap();
        assert_eq!((pr.canal_id.as_str(), pr.fecha.as_str()), ("portfolio", "2026-11-09"));
        assert!(proxima(&c, &a.id, "2026-12-01").unwrap().is_none());
    }

    #[test]
    fn canales_nuevos_y_desactivar() {
        let mut c = en_memoria();
        let yt = actualizar_canal(&mut c, "youtube", CambioCanal { activo: Some(true), nombre: None, formatos: None }).unwrap();
        assert!(yt.activo);
        let n = crear_canal(
            &mut c,
            NuevoCanal { nombre: "Bluesky".into(), icono: "x".into(), formatos: vec![Formato { id: "post".into(), nombre: "Post".into(), limite: Some(300), partes: false }] },
        )
        .unwrap();
        assert_eq!(listar_canales(&c).unwrap().last().unwrap().id, n.id);
    }
}
