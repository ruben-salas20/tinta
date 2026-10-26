//! Rutas HTTP. Solo traducen entre HTTP y `modelo`: aquí no hay lógica.
//!
//! Acceso: todo `/api` pide la cookie de sesión de la web o la cabecera
//! `Authorization: Bearer <TINTA_API_KEY>` de la app. `/health` y la propia
//! web (los archivos estáticos) son libres; la web pide entrar si no hay sesión.

use crate::calendario;
use crate::critic::Decision;
use crate::exportar;
use crate::modelo::{self, Error};
use axum::extract::{DefaultBodyLimit, FromRequestParts, Multipart, Path, Query, Request, State};
use axum::http::{HeaderMap, HeaderValue, StatusCode, header, request::Parts};
use axum::middleware::{self, Next};
use axum::response::{IntoResponse, Response};
use axum::routing::{get, post};
use axum::{Json, Router};
use rusqlite::{Connection, OptionalExtension, params};
use serde::Deserialize;
use serde_json::{Value, json};
use sha2::{Digest, Sha256};
use std::path::PathBuf;
use std::sync::{Arc, Mutex};
use subtle::ConstantTimeEq;
use tokio::io::AsyncWriteExt;

#[derive(Clone)]
pub struct Estado {
    pub db: Arc<Mutex<Connection>>,
    pub medios: PathBuf,
    pub clave_api: String,
    pub clave_web: String,
    pub cookie_segura: bool,
}

const COOKIE: &str = "tinta_sesion";
const DIAS_SESION: i64 = 365;

#[derive(Debug)]
pub struct ErrorApi(StatusCode, String);

impl From<Error> for ErrorApi {
    fn from(e: Error) -> Self {
        match e {
            Error::NoExiste(que) => ErrorApi(StatusCode::NOT_FOUND, format!("no existe: {que}")),
            Error::Invalido(m) => ErrorApi(StatusCode::BAD_REQUEST, m),
            Error::Db(e) => {
                tracing::error!("base de datos: {e}");
                ErrorApi(StatusCode::INTERNAL_SERVER_ERROR, "error de base de datos".into())
            }
        }
    }
}

impl From<rusqlite::Error> for ErrorApi {
    fn from(e: rusqlite::Error) -> Self {
        Error::Db(e).into()
    }
}

impl IntoResponse for ErrorApi {
    fn into_response(self) -> Response {
        (self.0, Json(json!({ "error": self.1 }))).into_response()
    }
}

type R<T> = Result<T, ErrorApi>;

/// Quién guarda: «web», «android» o «mcp». Lo usa el historial.
pub struct Dispositivo(String);

impl<S: Send + Sync> FromRequestParts<S> for Dispositivo {
    type Rejection = std::convert::Infallible;
    async fn from_request_parts(p: &mut Parts, _: &S) -> Result<Self, Self::Rejection> {
        let d = p.headers.get("x-tinta-dispositivo").and_then(|v| v.to_str().ok()).unwrap_or("web");
        let d: String = d.chars().filter(|c| c.is_ascii_alphanumeric() || *c == '-').take(24).collect();
        Ok(Dispositivo(if d.is_empty() { "web".into() } else { d }))
    }
}

impl Estado {
    fn con<T>(&self, f: impl FnOnce(&mut Connection) -> modelo::Res<T>) -> R<T> {
        let mut c = self.db.lock().unwrap_or_else(|e| e.into_inner());
        Ok(f(&mut c)?)
    }
}

fn sha(s: &str) -> String {
    hex::encode(Sha256::digest(s.as_bytes()))
}

fn iguales(a: &str, b: &str) -> bool {
    a.as_bytes().ct_eq(b.as_bytes()).into()
}

fn token_cookie(h: &HeaderMap) -> Option<String> {
    h.get_all(header::COOKIE)
        .iter()
        .filter_map(|v| v.to_str().ok())
        .flat_map(|v| v.split(';'))
        .find_map(|p| p.trim().strip_prefix(&format!("{COOKIE}=")).map(str::to_string))
}

fn sesion_valida(e: &Estado, h: &HeaderMap) -> bool {
    let Some(t) = token_cookie(h) else { return false };
    let c = e.db.lock().unwrap_or_else(|e| e.into_inner());
    c.query_row("SELECT 1 FROM sesiones WHERE token_sha256 = ?1 AND caduca > ?2", params![sha(&t), modelo::ahora()], |_| Ok(()))
        .optional()
        .ok()
        .flatten()
        .is_some()
}

async fn exigir_acceso(State(e): State<Estado>, req: Request, next: Next) -> Response {
    let h = req.headers();
    let por_clave = h
        .get(header::AUTHORIZATION)
        .and_then(|v| v.to_str().ok())
        .and_then(|v| v.strip_prefix("Bearer "))
        .is_some_and(|k| iguales(k, &e.clave_api));
    if por_clave || sesion_valida(&e, h) {
        next.run(req).await
    } else {
        ErrorApi(StatusCode::UNAUTHORIZED, "hace falta entrar".into()).into_response()
    }
}

#[derive(Deserialize)]
struct Entrar {
    clave: String,
}

async fn entrar(State(e): State<Estado>, Json(b): Json<Entrar>) -> R<Response> {
    if !iguales(&b.clave, &e.clave_web) {
        // Frena a quien pruebe claves a ciegas.
        tokio::time::sleep(std::time::Duration::from_millis(800)).await;
        return Err(ErrorApi(StatusCode::UNAUTHORIZED, "clave incorrecta".into()));
    }
    let token = modelo::nuevo_id() + &modelo::nuevo_id();
    let caduca = (chrono::Utc::now() + chrono::Duration::days(DIAS_SESION)).to_rfc3339_opts(chrono::SecondsFormat::Secs, true);
    e.con(|c| {
        c.execute("DELETE FROM sesiones WHERE caduca < ?1", params![modelo::ahora()])?;
        c.execute("INSERT INTO sesiones VALUES (?1, ?2, ?3)", params![sha(&token), modelo::ahora(), caduca])?;
        Ok(())
    })?;
    let segura = if e.cookie_segura { "; Secure" } else { "" };
    let cookie = format!("{COOKIE}={token}; HttpOnly; SameSite=Strict; Path=/; Max-Age={}{segura}", DIAS_SESION * 86400);
    let mut r = Json(json!({ "ok": true })).into_response();
    r.headers_mut().insert(header::SET_COOKIE, HeaderValue::from_str(&cookie).unwrap());
    Ok(r)
}

async fn salir(State(e): State<Estado>, h: HeaderMap) -> R<Response> {
    if let Some(t) = token_cookie(&h) {
        e.con(|c| Ok(c.execute("DELETE FROM sesiones WHERE token_sha256 = ?1", params![sha(&t)])?))?;
    }
    let mut r = Json(json!({ "ok": true })).into_response();
    r.headers_mut().insert(header::SET_COOKIE, HeaderValue::from_static("tinta_sesion=; HttpOnly; Path=/; Max-Age=0"));
    Ok(r)
}

async fn yo() -> Json<Value> {
    Json(json!({ "ok": true }))
}

// --- Artículos

#[derive(Deserialize)]
struct NuevoArticulo {
    titulo: String,
}

async fn articulos(State(e): State<Estado>) -> R<Json<Vec<modelo::Articulo>>> {
    Ok(Json(e.con(|c| modelo::listar_articulos(c))?))
}

async fn crear_articulo(State(e): State<Estado>, Json(b): Json<NuevoArticulo>) -> R<(StatusCode, Json<modelo::Articulo>)> {
    Ok((StatusCode::CREATED, Json(e.con(|c| modelo::crear_articulo(c, b.titulo.trim()))?)))
}

async fn articulo(State(e): State<Estado>, Path(id): Path<String>) -> R<Json<modelo::Articulo>> {
    Ok(Json(e.con(|c| modelo::articulo(c, &id))?))
}

async fn cambiar_articulo(State(e): State<Estado>, Path(id): Path<String>, Json(b): Json<modelo::CambioArticulo>) -> R<Json<modelo::Articulo>> {
    Ok(Json(e.con(|c| modelo::actualizar_articulo(c, &id, b))?))
}

async fn borrar_articulo(State(e): State<Estado>, Path(id): Path<String>) -> R<StatusCode> {
    e.con(|c| modelo::borrar_articulo(c, &id))?;
    Ok(StatusCode::NO_CONTENT)
}

#[derive(Deserialize)]
struct NuevoIdioma {
    idioma: String,
    copiar_de: Option<String>,
}

async fn crear_idioma(State(e): State<Estado>, Path(id): Path<String>, Json(b): Json<NuevoIdioma>) -> R<Json<modelo::Articulo>> {
    Ok(Json(e.con(|c| modelo::crear_idioma(c, &id, &b.idioma, b.copiar_de.as_deref()))?))
}

async fn cambiar_idioma(State(e): State<Estado>, Path(id): Path<String>, Json(b): Json<modelo::CambioIdioma>) -> R<Json<modelo::Articulo>> {
    Ok(Json(e.con(|c| modelo::actualizar_idioma(c, &id, b))?))
}

async fn borrar_idioma(State(e): State<Estado>, Path(id): Path<String>) -> R<Json<modelo::Articulo>> {
    Ok(Json(e.con(|c| modelo::borrar_idioma(c, &id))?))
}

// --- Exportar

async fn markdown(State(e): State<Estado>, Path(id): Path<String>) -> R<Response> {
    let x = e.con(|c| exportar::exportar_idioma(c, &id))?;
    Ok(([(header::CONTENT_TYPE, "text/markdown; charset=utf-8")], x.markdown).into_response())
}

async fn avisos(State(e): State<Estado>, Path(id): Path<String>) -> R<Json<Value>> {
    let x = e.con(|c| exportar::exportar_idioma(c, &id))?;
    let archivos: Vec<Value> = x.archivos.values().map(|a| json!({ "nombre": a.nombre, "tipo": a.medio.tipo })).collect();
    Ok(Json(json!({ "idioma": x.idioma, "markdown": x.markdown, "avisos": x.avisos, "archivos": archivos })))
}

async fn para_x(State(e): State<Estado>, Path(id): Path<String>) -> R<Json<exportar::ParaX>> {
    Ok(Json(e.con(|c| exportar::para_x(c, &id))?))
}

#[derive(Deserialize)]
struct QueIdiomas {
    #[serde(default)]
    idiomas: String,
}

async fn zip(State(e): State<Estado>, Path(id): Path<String>, Query(q): Query<QueIdiomas>) -> R<Response> {
    let idiomas: Vec<String> = q.idiomas.split(',').map(str::trim).filter(|s| !s.is_empty()).map(String::from).collect();
    let medios = e.medios.clone();
    let (nombre, bytes) = e.con(|c| exportar::zip(c, &id, &idiomas, &medios))?;
    Ok((
        [
            (header::CONTENT_TYPE, "application/zip".to_string()),
            (header::CONTENT_DISPOSITION, format!("attachment; filename=\"{nombre}\"")),
        ],
        bytes,
    )
        .into_response())
}

// --- Ramas, secciones, bloques

async fn rama(State(e): State<Estado>, Path(id): Path<String>) -> R<Json<modelo::Documento>> {
    Ok(Json(e.con(|c| modelo::documento(c, &id))?))
}

#[derive(Deserialize)]
struct Nombre {
    nombre: String,
}

async fn renombrar_rama(State(e): State<Estado>, Path(id): Path<String>, Json(b): Json<Nombre>) -> R<Json<modelo::Documento>> {
    Ok(Json(e.con(|c| modelo::renombrar_rama(c, &id, &b.nombre))?))
}

async fn borrar_rama(State(e): State<Estado>, Path(id): Path<String>) -> R<StatusCode> {
    e.con(|c| modelo::borrar_rama(c, &id))?;
    Ok(StatusCode::NO_CONTENT)
}

async fn fork_rama(State(e): State<Estado>, Path(id): Path<String>, Json(b): Json<Nombre>) -> R<(StatusCode, Json<modelo::Documento>)> {
    Ok((StatusCode::CREATED, Json(e.con(|c| modelo::fork_rama(c, &id, &b.nombre))?)))
}

async fn crear_seccion(State(e): State<Estado>, Path(id): Path<String>, Json(b): Json<modelo::NuevaSeccion>) -> R<Json<modelo::Documento>> {
    Ok(Json(e.con(|c| modelo::crear_seccion(c, &id, b))?))
}

async fn cambiar_seccion(State(e): State<Estado>, Path(id): Path<String>, Json(b): Json<modelo::CambioSeccion>) -> R<Json<modelo::Documento>> {
    Ok(Json(e.con(|c| modelo::actualizar_seccion(c, &id, b))?))
}

async fn borrar_seccion(State(e): State<Estado>, Path(id): Path<String>) -> R<Json<modelo::Documento>> {
    Ok(Json(e.con(|c| modelo::borrar_seccion(c, &id))?))
}

async fn fork_seccion(State(e): State<Estado>, Path(id): Path<String>, Json(b): Json<modelo::ForkSeccion>) -> R<Json<Value>> {
    let (doc, nueva) = e.con(|c| modelo::fork_seccion(c, &id, b))?;
    Ok(Json(json!({ "documento": doc, "variante_id": nueva })))
}

async fn cambiar_variante_seccion(
    State(e): State<Estado>,
    Path(id): Path<String>,
    Json(b): Json<modelo::CambioVarianteSeccion>,
) -> R<Json<modelo::Documento>> {
    Ok(Json(e.con(|c| modelo::actualizar_variante_seccion(c, &id, b))?))
}

async fn borrar_variante_seccion(State(e): State<Estado>, Path(id): Path<String>) -> R<Json<modelo::Documento>> {
    Ok(Json(e.con(|c| modelo::borrar_variante_seccion(c, &id))?))
}

async fn crear_bloque(State(e): State<Estado>, Path(id): Path<String>, Json(b): Json<modelo::NuevoBloque>) -> R<Json<Value>> {
    let (doc, bloque) = e.con(|c| modelo::crear_bloque(c, &id, b))?;
    Ok(Json(json!({ "documento": doc, "bloque_id": bloque })))
}

async fn cambiar_bloque(State(e): State<Estado>, Path(id): Path<String>, Json(b): Json<modelo::CambioBloque>) -> R<Json<modelo::Documento>> {
    Ok(Json(e.con(|c| modelo::actualizar_bloque(c, &id, b))?))
}

async fn borrar_bloque(State(e): State<Estado>, Path(id): Path<String>) -> R<Json<modelo::Documento>> {
    Ok(Json(e.con(|c| modelo::borrar_bloque(c, &id))?))
}

async fn fork_bloque(State(e): State<Estado>, Path(id): Path<String>, Json(b): Json<modelo::ForkBloque>) -> R<Json<Value>> {
    let (doc, nueva) = e.con(|c| modelo::fork_bloque(c, &id, b))?;
    Ok(Json(json!({ "documento": doc, "variante_id": nueva })))
}

#[derive(Deserialize)]
struct Guardar {
    contenido: String,
    rev_base: i64,
}

async fn guardar(State(e): State<Estado>, d: Dispositivo, Path(id): Path<String>, Json(b): Json<Guardar>) -> R<Json<modelo::Guardado>> {
    Ok(Json(e.con(|c| modelo::guardar_variante_bloque(c, &id, &b.contenido, b.rev_base, &d.0))?))
}

async fn borrar_variante_bloque(State(e): State<Estado>, Path(id): Path<String>) -> R<Json<modelo::Documento>> {
    Ok(Json(e.con(|c| modelo::borrar_variante_bloque(c, &id))?))
}

async fn historial(State(e): State<Estado>, Path(id): Path<String>) -> R<Json<Vec<modelo::EntradaHistorial>>> {
    Ok(Json(e.con(|c| modelo::historial(c, &id))?))
}

#[derive(Deserialize)]
struct Restaurar {
    entrada: i64,
}

async fn restaurar(State(e): State<Estado>, d: Dispositivo, Path(id): Path<String>, Json(b): Json<Restaurar>) -> R<Json<modelo::Guardado>> {
    Ok(Json(e.con(|c| modelo::restaurar(c, &id, b.entrada, &d.0))?))
}

#[derive(Deserialize)]
struct Marca {
    indice: usize,
    decision: Decision,
}

async fn resolver_marca(State(e): State<Estado>, d: Dispositivo, Path(id): Path<String>, Json(b): Json<Marca>) -> R<Json<modelo::Guardado>> {
    Ok(Json(e.con(|c| modelo::resolver_marca(c, &id, b.indice, b.decision, &d.0))?))
}

// --- Etiquetas y series

async fn etiquetas(State(e): State<Estado>) -> R<Json<Vec<modelo::Etiqueta>>> {
    Ok(Json(e.con(|c| modelo::listar_etiquetas(c))?))
}

async fn series(State(e): State<Estado>) -> R<Json<Vec<modelo::SerieConCuenta>>> {
    Ok(Json(e.con(|c| modelo::listar_series(c))?))
}

async fn crear_serie(State(e): State<Estado>, Json(b): Json<Nombre>) -> R<(StatusCode, Json<modelo::Serie>)> {
    Ok((StatusCode::CREATED, Json(e.con(|c| modelo::crear_serie(c, &b.nombre))?)))
}

async fn renombrar_serie(State(e): State<Estado>, Path(id): Path<String>, Json(b): Json<Nombre>) -> R<Json<modelo::Serie>> {
    Ok(Json(e.con(|c| modelo::renombrar_serie(c, &id, &b.nombre))?))
}

async fn borrar_serie(State(e): State<Estado>, Path(id): Path<String>) -> R<StatusCode> {
    e.con(|c| modelo::borrar_serie(c, &id))?;
    Ok(StatusCode::NO_CONTENT)
}

// --- Medios

/// Tipos que se aceptan: lo que el portfolio sabe mostrar.
fn tipo_permitido(t: &str) -> bool {
    matches!(t, "image/png" | "image/jpeg" | "image/gif" | "image/webp" | "image/svg+xml" | "image/avif")
        || matches!(t, "audio/mpeg" | "audio/mp4" | "audio/ogg" | "audio/wav" | "audio/x-wav" | "audio/webm" | "audio/aac")
}

async fn subir_medio(State(e): State<Estado>, mut m: Multipart) -> R<(StatusCode, Json<modelo::Medio>)> {
    let mal = |s: &str| ErrorApi(StatusCode::BAD_REQUEST, s.to_string());
    while let Some(mut campo) = m.next_field().await.map_err(|e| mal(&e.to_string()))? {
        if campo.name() != Some("archivo") {
            continue;
        }
        let nombre = campo.file_name().unwrap_or("archivo").to_string();
        let tipo = campo.content_type().unwrap_or("application/octet-stream").to_string();
        if !tipo_permitido(&tipo) {
            return Err(mal(&format!("tipo de archivo no admitido: {tipo}")));
        }
        tokio::fs::create_dir_all(&e.medios).await.map_err(|e| mal(&e.to_string()))?;
        let temporal = e.medios.join(format!(".subiendo-{}", modelo::nuevo_id()));
        let mut f = tokio::fs::File::create(&temporal).await.map_err(|e| mal(&e.to_string()))?;
        let mut h = Sha256::new();
        let mut tamano = 0i64;
        while let Some(trozo) = campo.chunk().await.map_err(|e| mal(&e.to_string()))? {
            h.update(&trozo);
            tamano += trozo.len() as i64;
            f.write_all(&trozo).await.map_err(|e| mal(&e.to_string()))?;
        }
        f.flush().await.map_err(|e| mal(&e.to_string()))?;
        let suma = hex::encode(h.finalize());
        tokio::fs::rename(&temporal, e.medios.join(&suma)).await.map_err(|e| mal(&e.to_string()))?;
        let medio = e.con(|c| modelo::registrar_medio(c, &suma, &nombre, &tipo, tamano))?;
        return Ok((StatusCode::CREATED, Json(medio)));
    }
    Err(mal("falta el campo «archivo»"))
}

/// Sirve el archivo con su tipo guardado. ServeFile atiende `Range`, que el
/// reproductor de audio necesita para saltar a un punto.
async fn medio(State(e): State<Estado>, Path(id): Path<String>, req: Request) -> R<Response> {
    use tower::ServiceExt;
    let m = e.con(|c| modelo::medio(c, &id))?;
    let tipo: mime::Mime = m.tipo.parse().unwrap_or(mime::APPLICATION_OCTET_STREAM);
    let mut r = tower_http::services::ServeFile::new_with_mime(e.medios.join(&m.sha256), &tipo)
        .oneshot(req)
        .await
        .map_err(|_| ErrorApi(StatusCode::NOT_FOUND, "falta el archivo del medio".into()))?
        .map(axum::body::Body::new);
    // El contenido de un id nunca cambia.
    r.headers_mut().insert(header::CACHE_CONTROL, HeaderValue::from_static("private, max-age=31536000, immutable"));
    Ok(r)
}

// --- Calendario

async fn canales(State(e): State<Estado>) -> R<Json<Vec<calendario::Canal>>> {
    Ok(Json(e.con(|c| calendario::listar_canales(c))?))
}

async fn crear_canal(State(e): State<Estado>, Json(b): Json<calendario::NuevoCanal>) -> R<(StatusCode, Json<calendario::Canal>)> {
    Ok((StatusCode::CREATED, Json(e.con(|c| calendario::crear_canal(c, b))?)))
}

async fn cambiar_canal(State(e): State<Estado>, Path(id): Path<String>, Json(b): Json<calendario::CambioCanal>) -> R<Json<calendario::Canal>> {
    Ok(Json(e.con(|c| calendario::actualizar_canal(c, &id, b))?))
}

async fn publicaciones(State(e): State<Estado>, Query(f): Query<calendario::Filtro>) -> R<Json<Vec<calendario::Publicacion>>> {
    Ok(Json(e.con(|c| calendario::listar_publicaciones(c, &f))?))
}

async fn crear_publicacion(State(e): State<Estado>, Json(b): Json<calendario::NuevaPublicacion>) -> R<(StatusCode, Json<calendario::Publicacion>)> {
    Ok((StatusCode::CREATED, Json(e.con(|c| calendario::crear_publicacion(c, b))?)))
}

async fn publicacion(State(e): State<Estado>, Path(id): Path<String>) -> R<Json<calendario::Publicacion>> {
    Ok(Json(e.con(|c| calendario::publicacion(c, &id))?))
}

async fn cambiar_publicacion(State(e): State<Estado>, Path(id): Path<String>, Json(b): Json<calendario::CambioPublicacion>) -> R<Json<calendario::Publicacion>> {
    Ok(Json(e.con(|c| calendario::actualizar_publicacion(c, &id, b))?))
}

async fn borrar_publicacion(State(e): State<Estado>, Path(id): Path<String>) -> R<StatusCode> {
    e.con(|c| calendario::borrar_publicacion(c, &id))?;
    Ok(StatusCode::NO_CONTENT)
}

async fn difusion(State(e): State<Estado>, Path(id): Path<String>, Json(b): Json<Vec<calendario::Plan>>) -> R<(StatusCode, Json<Vec<calendario::Publicacion>>)> {
    Ok((StatusCode::CREATED, Json(e.con(|c| calendario::planear_difusion(c, &id, b))?)))
}

async fn hoy() -> Json<Value> {
    Json(json!({ "fecha": calendario::hoy(), "zona": calendario::zona().name() }))
}

pub fn rutas(e: Estado) -> Router {
    let api = Router::new()
        .route("/sesion", get(yo))
        .route("/articulos", get(articulos).post(crear_articulo))
        .route("/articulos/{id}", get(articulo).patch(cambiar_articulo).delete(borrar_articulo))
        .route("/articulos/{id}/idiomas", post(crear_idioma))
        .route("/articulos/{id}/exportar.zip", get(zip))
        .route("/idiomas/{id}", axum::routing::patch(cambiar_idioma).delete(borrar_idioma))
        .route("/idiomas/{id}/markdown", get(markdown))
        .route("/idiomas/{id}/exportar", get(avisos))
        .route("/idiomas/{id}/x", get(para_x))
        .route("/ramas/{id}", get(rama).patch(renombrar_rama).delete(borrar_rama))
        .route("/ramas/{id}/fork", post(fork_rama))
        .route("/ramas/{id}/secciones", post(crear_seccion))
        .route("/secciones/{id}", axum::routing::patch(cambiar_seccion).delete(borrar_seccion))
        .route("/secciones/{id}/variantes", post(fork_seccion))
        .route("/variantes-seccion/{id}", axum::routing::patch(cambiar_variante_seccion).delete(borrar_variante_seccion))
        .route("/variantes-seccion/{id}/bloques", post(crear_bloque))
        .route("/bloques/{id}", axum::routing::patch(cambiar_bloque).delete(borrar_bloque))
        .route("/bloques/{id}/variantes", post(fork_bloque))
        .route("/variantes-bloque/{id}", axum::routing::put(guardar).delete(borrar_variante_bloque))
        .route("/variantes-bloque/{id}/historial", get(historial))
        .route("/variantes-bloque/{id}/restaurar", post(restaurar))
        .route("/variantes-bloque/{id}/marcas", post(resolver_marca))
        .route("/etiquetas", get(etiquetas))
        .route("/series", get(series).post(crear_serie))
        .route("/series/{id}", axum::routing::patch(renombrar_serie).delete(borrar_serie))
        .route("/medios", post(subir_medio).layer(DefaultBodyLimit::max(300 * 1024 * 1024)))
        .route("/medios/{id}", get(medio))
        .route("/hoy", get(hoy))
        .route("/canales", get(canales).post(crear_canal))
        .route("/canales/{id}", axum::routing::patch(cambiar_canal))
        .route("/publicaciones", get(publicaciones).post(crear_publicacion))
        .route("/publicaciones/{id}", get(publicacion).patch(cambiar_publicacion).delete(borrar_publicacion))
        .route("/articulos/{id}/difusion", post(difusion))
        .route_layer(middleware::from_fn_with_state(e.clone(), exigir_acceso))
        // Entrar y salir van fuera del control de acceso.
        .route("/entrar", post(entrar))
        .route("/salir", post(salir));
    Router::new()
        .route("/health", get(|| async { "ok" }))
        .nest("/api", api)
        .with_state(e)
}

#[cfg(test)]
mod pruebas {
    use super::*;
    use axum::body::Body;
    use http_body_util::BodyExt;
    use tower::ServiceExt;

    fn app() -> (Router, tempfile::TempDir) {
        let (r, _, d) = app_y_estado();
        (r, d)
    }

    fn app_y_estado() -> (Router, Estado, tempfile::TempDir) {
        let dir = tempfile::tempdir().unwrap();
        let e = Estado {
            db: Arc::new(Mutex::new(crate::db::en_memoria())),
            medios: dir.path().join("medios"),
            clave_api: "clave-app".into(),
            clave_web: "secreta".into(),
            cookie_segura: true,
        };
        (rutas(e.clone()), e, dir)
    }

    async fn pedir(app: &Router, metodo: &str, ruta: &str, cuerpo: Option<Value>, auth: Option<&str>) -> (StatusCode, HeaderMap, Vec<u8>) {
        let mut r = Request::builder().method(metodo).uri(ruta);
        if let Some(a) = auth {
            r = r.header(if a.starts_with(COOKIE) { "cookie" } else { "authorization" }, a);
        }
        let req = match cuerpo {
            Some(v) => r.header("content-type", "application/json").body(Body::from(v.to_string())).unwrap(),
            None => r.body(Body::empty()).unwrap(),
        };
        let res = app.clone().oneshot(req).await.unwrap();
        let (p, b) = res.into_parts();
        (p.status, p.headers, b.collect().await.unwrap().to_bytes().to_vec())
    }

    #[tokio::test]
    async fn acceso() {
        let (app, _d) = app();
        assert_eq!(pedir(&app, "GET", "/health", None, None).await.0, StatusCode::OK);
        assert_eq!(pedir(&app, "GET", "/api/articulos", None, None).await.0, StatusCode::UNAUTHORIZED);
        assert_eq!(pedir(&app, "GET", "/api/articulos", None, Some("Bearer mala")).await.0, StatusCode::UNAUTHORIZED);
        assert_eq!(pedir(&app, "GET", "/api/articulos", None, Some("Bearer clave-app")).await.0, StatusCode::OK);
        let (s, _, _) = pedir(&app, "POST", "/api/entrar", Some(json!({"clave": "otra"})), None).await;
        assert_eq!(s, StatusCode::UNAUTHORIZED);
        let (s, h, _) = pedir(&app, "POST", "/api/entrar", Some(json!({"clave": "secreta"})), None).await;
        assert_eq!(s, StatusCode::OK);
        let cookie = h.get("set-cookie").unwrap().to_str().unwrap();
        assert!(cookie.contains("HttpOnly") && cookie.contains("Secure"));
        let par = cookie.split(';').next().unwrap();
        assert_eq!(pedir(&app, "GET", "/api/sesion", None, Some(par)).await.0, StatusCode::OK);
        pedir(&app, "POST", "/api/salir", None, Some(par)).await;
        assert_eq!(pedir(&app, "GET", "/api/sesion", None, Some(par)).await.0, StatusCode::UNAUTHORIZED);
    }

    #[tokio::test]
    async fn flujo_completo() {
        let (app, _d) = app();
        let k = Some("Bearer clave-app");
        let (s, _, b) = pedir(&app, "POST", "/api/articulos", Some(json!({"titulo": "Hola mundo"})), k).await;
        assert_eq!(s, StatusCode::CREATED);
        let a: Value = serde_json::from_slice(&b).unwrap();
        let rama = a["idiomas"][0]["rama_principal_id"].as_str().unwrap().to_string();
        let (_, _, b) = pedir(&app, "GET", &format!("/api/ramas/{rama}"), None, k).await;
        let d: Value = serde_json::from_slice(&b).unwrap();
        let v = &d["secciones"][0]["variantes"][0]["bloques"][0]["variantes"][0];
        let vid = v["id"].as_str().unwrap();
        let rev = v["rev"].as_i64().unwrap();
        let (s, _, b) = pedir(&app, "PUT", &format!("/api/variantes-bloque/{vid}"), Some(json!({"contenido": "Primer párrafo", "rev_base": rev})), k).await;
        assert_eq!(s, StatusCode::OK, "{}", String::from_utf8_lossy(&b));
        let iid = a["idiomas"][0]["id"].as_str().unwrap();
        let (_, h, b) = pedir(&app, "GET", &format!("/api/idiomas/{iid}/markdown"), None, k).await;
        assert!(h["content-type"].to_str().unwrap().starts_with("text/markdown"));
        assert!(String::from_utf8(b).unwrap().ends_with("Primer párrafo\n"));
        let (s, _, b) = pedir(&app, "PATCH", &format!("/api/articulos/{}", a["id"].as_str().unwrap()), Some(json!({"estado": "nada"})), k).await;
        assert_eq!(s, StatusCode::BAD_REQUEST);
        assert!(String::from_utf8(b).unwrap().contains("estado desconocido"));
        assert_eq!(pedir(&app, "GET", "/api/ramas/noexiste", None, k).await.0, StatusCode::NOT_FOUND);
    }

    #[tokio::test]
    async fn medios_por_trozos() {
        let (app, e, _d) = app_y_estado();
        std::fs::create_dir_all(&e.medios).unwrap();
        std::fs::write(e.medios.join("abc"), b"0123456789").unwrap();
        let m = e.con(|c| modelo::registrar_medio(c, "abc", "voz.mp3", "audio/mpeg", 10)).unwrap();
        let req = Request::builder()
            .uri(format!("/api/medios/{}", m.id))
            .header("authorization", "Bearer clave-app")
            .header("range", "bytes=2-4")
            .body(Body::empty())
            .unwrap();
        let res = app.clone().oneshot(req).await.unwrap();
        assert_eq!(res.status(), StatusCode::PARTIAL_CONTENT);
        assert_eq!(res.headers()["content-type"], "audio/mpeg");
        assert!(res.headers()["cache-control"].to_str().unwrap().contains("immutable"));
        assert_eq!(res.into_body().collect().await.unwrap().to_bytes().as_ref(), b"234");
    }
}
