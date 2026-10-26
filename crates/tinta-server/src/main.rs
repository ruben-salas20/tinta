//! Tinta: calendario de contenido y escritura ordenada. Ver docs/plan.md.

mod api;
mod calendario;
mod critic;
mod db;
mod exportar;
mod modelo;
mod posicion;

use std::path::PathBuf;
use std::sync::{Arc, Mutex};
use tower_http::services::{ServeDir, ServeFile};

fn variable(nombre: &str) -> String {
    match std::env::var(nombre) {
        Ok(v) if !v.trim().is_empty() => v,
        _ => {
            eprintln!("falta la variable de entorno {nombre}");
            std::process::exit(2);
        }
    }
}

#[tokio::main]
async fn main() {
    tracing_subscriber::fmt()
        .with_env_filter(tracing_subscriber::EnvFilter::try_from_default_env().unwrap_or_else(|_| "tinta_server=info".into()))
        .init();

    let datos = PathBuf::from(std::env::var("TINTA_DATOS").unwrap_or_else(|_| "/data".into()));
    let web = PathBuf::from(std::env::var("TINTA_WEB").unwrap_or_else(|_| "/srv/web".into()));
    let puerto = std::env::var("TINTA_PUERTO").unwrap_or_else(|_| "8080".into());
    std::fs::create_dir_all(&datos).expect("crear la carpeta de datos");

    let estado = api::Estado {
        db: Arc::new(Mutex::new(db::abrir(&datos.join("tinta.db")).expect("abrir la base de datos"))),
        medios: datos.join("medios"),
        clave_api: variable("TINTA_API_KEY"),
        clave_web: variable("TINTA_WEB_PASSWORD"),
        // Solo para probar en local sin HTTPS.
        cookie_segura: std::env::var("TINTA_COOKIE_INSEGURA").is_err(),
    };

    // La web es una SPA: cualquier ruta que no sea un archivo sirve index.html.
    let estaticos = ServeDir::new(&web).fallback(ServeFile::new(web.join("index.html")));
    let app = api::rutas(estado).fallback_service(estaticos).layer(tower_http::trace::TraceLayer::new_for_http());

    let escucha = tokio::net::TcpListener::bind(format!("0.0.0.0:{puerto}")).await.expect("abrir el puerto");
    tracing::info!("tinta escuchando en el puerto {puerto}, datos en {}", datos.display());
    axum::serve(escucha, app)
        .with_graceful_shutdown(async {
            // Docker para el contenedor con SIGTERM; en una terminal llega Ctrl+C.
            let mut term = tokio::signal::unix::signal(tokio::signal::unix::SignalKind::terminate()).expect("señal SIGTERM");
            tokio::select! {
                _ = tokio::signal::ctrl_c() => {}
                _ = term.recv() => {}
            }
        })
        .await
        .expect("servidor");
}
