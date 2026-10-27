//! Base SQLite: esquema y migraciones.
//!
//! Todas las tablas que sincroniza el móvil llevan `rev` y `borrado`:
//! - `rev` es el valor del contador global (`meta.rev`) en el último cambio de
//!   la fila. El móvil pide «lo que cambió desde la rev N».
//! - `borrado = 1` es una lápida: la fila se queda para que el otro
//!   dispositivo se entere de que ya no existe.

use rusqlite::{Connection, OptionalExtension, params};
use std::path::Path;

const MIGRACIONES: &[&str] = &[r#"
CREATE TABLE meta (clave TEXT PRIMARY KEY, valor TEXT NOT NULL);
INSERT INTO meta VALUES ('rev', '0');

CREATE TABLE series (
  id TEXT PRIMARY KEY, nombre TEXT NOT NULL,
  rev INTEGER NOT NULL, borrado INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE etiquetas (
  id TEXT PRIMARY KEY, nombre TEXT NOT NULL UNIQUE,
  rev INTEGER NOT NULL, borrado INTEGER NOT NULL DEFAULT 0
);

-- estado: idea | borrador | revision | listo | publicado
CREATE TABLE articulos (
  id TEXT PRIMARY KEY,
  slug TEXT NOT NULL,
  estado TEXT NOT NULL DEFAULT 'idea',
  serie_id TEXT REFERENCES series(id),
  serie_parte INTEGER,
  fecha TEXT,                 -- fecha de publicación (AAAA-MM-DD); la usa el portfolio
  creado TEXT NOT NULL, cambiado TEXT NOT NULL,
  rev INTEGER NOT NULL, borrado INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX articulos_slug ON articulos(slug) WHERE borrado = 0;

CREATE TABLE articulo_etiquetas (
  articulo_id TEXT NOT NULL REFERENCES articulos(id),
  etiqueta_id TEXT NOT NULL REFERENCES etiquetas(id),
  PRIMARY KEY (articulo_id, etiqueta_id)
);

-- Una versión del artículo en un idioma: lo que será es.md o en.md.
CREATE TABLE idiomas (
  id TEXT PRIMARY KEY,
  articulo_id TEXT NOT NULL REFERENCES articulos(id),
  idioma TEXT NOT NULL,       -- es | en
  titulo TEXT NOT NULL DEFAULT '',
  descripcion TEXT NOT NULL DEFAULT '',
  borrador INTEGER NOT NULL DEFAULT 1,
  traducido_ia INTEGER NOT NULL DEFAULT 0,
  rama_principal_id TEXT,
  rev INTEGER NOT NULL, borrado INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX idiomas_unico ON idiomas(articulo_id, idioma) WHERE borrado = 0;

-- Rama: un fork del artículo entero en un idioma.
CREATE TABLE ramas (
  id TEXT PRIMARY KEY,
  idioma_id TEXT NOT NULL REFERENCES idiomas(id),
  nombre TEXT NOT NULL,
  origen_id TEXT,             -- rama de la que salió el fork
  creado TEXT NOT NULL,
  rev INTEGER NOT NULL, borrado INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE secciones (
  id TEXT PRIMARY KEY,
  rama_id TEXT NOT NULL REFERENCES ramas(id),
  posicion TEXT NOT NULL,
  variante_activa_id TEXT,
  rev INTEGER NOT NULL, borrado INTEGER NOT NULL DEFAULT 0
);

-- titulo: el texto del ## (vacío en la sección de entrada, antes del primer ##).
-- origen: tu | ia:<nombre> | conflicto
CREATE TABLE variantes_seccion (
  id TEXT PRIMARY KEY,
  seccion_id TEXT NOT NULL REFERENCES secciones(id),
  titulo TEXT NOT NULL DEFAULT '',
  origen TEXT NOT NULL DEFAULT 'tu',
  creado TEXT NOT NULL,
  rev INTEGER NOT NULL, borrado INTEGER NOT NULL DEFAULT 0
);

-- tipo: parrafo | subtitulo | lista | cita | codigo | imagen | audio | youtube
-- Es una pista para el editor: el contenido siempre es Markdown.
CREATE TABLE bloques (
  id TEXT PRIMARY KEY,
  variante_seccion_id TEXT NOT NULL REFERENCES variantes_seccion(id),
  posicion TEXT NOT NULL,
  tipo TEXT NOT NULL DEFAULT 'parrafo',
  variante_activa_id TEXT,
  rev INTEGER NOT NULL, borrado INTEGER NOT NULL DEFAULT 0
);

-- contenido: Markdown con CriticMarkup. Los medios van como medio:<id>.
CREATE TABLE variantes_bloque (
  id TEXT PRIMARY KEY,
  bloque_id TEXT NOT NULL REFERENCES bloques(id),
  contenido TEXT NOT NULL DEFAULT '',
  origen TEXT NOT NULL DEFAULT 'tu',
  creado TEXT NOT NULL, cambiado TEXT NOT NULL,
  rev INTEGER NOT NULL, borrado INTEGER NOT NULL DEFAULT 0
);

-- Cada guardado deja aquí el contenido anterior. No se sincroniza al móvil.
CREATE TABLE historial (
  id INTEGER PRIMARY KEY,
  variante_bloque_id TEXT NOT NULL REFERENCES variantes_bloque(id),
  contenido TEXT NOT NULL,
  origen TEXT NOT NULL,
  dispositivo TEXT NOT NULL,
  creado TEXT NOT NULL
);
CREATE INDEX historial_variante ON historial(variante_bloque_id, id);

-- El archivo vive en medios/<sha256>; dos subidas iguales comparten archivo.
CREATE TABLE medios (
  id TEXT PRIMARY KEY,
  sha256 TEXT NOT NULL,
  nombre TEXT NOT NULL,
  tipo TEXT NOT NULL,
  tamano INTEGER NOT NULL,
  creado TEXT NOT NULL,
  rev INTEGER NOT NULL, borrado INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE sesiones (
  token_sha256 TEXT PRIMARY KEY,
  creado TEXT NOT NULL,
  caduca TEXT NOT NULL
);
"#, r#"
-- Fase 2: calendario.
-- formatos: JSON [{"id", "nombre", "limite" (caracteres por parte, o null), "partes" (true si admite varias, como un hilo)}]
-- icono: el del archivo redes.svg de la web.
CREATE TABLE canales (
  id TEXT PRIMARY KEY,
  nombre TEXT NOT NULL,
  icono TEXT NOT NULL,
  formatos TEXT NOT NULL,
  activo INTEGER NOT NULL DEFAULT 1,
  posicion TEXT NOT NULL,
  rev INTEGER NOT NULL, borrado INTEGER NOT NULL DEFAULT 0
);
INSERT INTO canales VALUES
 ('x', 'X', 'x', '[{"id":"post","nombre":"Post","limite":280,"partes":false},{"id":"hilo","nombre":"Hilo","limite":280,"partes":true},{"id":"articulo","nombre":"Artículo","limite":null,"partes":false}]', 1, 'a', 0, 0),
 ('linkedin', 'LinkedIn', 'linkedin', '[{"id":"post","nombre":"Post","limite":3000,"partes":false},{"id":"articulo","nombre":"Artículo","limite":null,"partes":false}]', 1, 'b', 0, 0),
 ('instagram', 'Instagram', 'instagram', '[{"id":"post","nombre":"Post","limite":2200,"partes":false},{"id":"carrusel","nombre":"Carrusel","limite":2200,"partes":false},{"id":"reel","nombre":"Reel","limite":2200,"partes":false}]', 1, 'c', 0, 0),
 ('portfolio', 'Portfolio', 'portfolio', '[{"id":"articulo","nombre":"Artículo","limite":null,"partes":false}]', 1, 'd', 0, 0),
 ('youtube', 'YouTube', 'youtube', '[{"id":"video","nombre":"Vídeo","limite":5000,"partes":false},{"id":"short","nombre":"Short","limite":5000,"partes":false}]', 0, 'e', 0, 0),
 ('tiktok', 'TikTok', 'tiktok', '[{"id":"video","nombre":"Vídeo","limite":4000,"partes":false}]', 0, 'f', 0, 0);

-- estado: idea | preparando | programada | publicada
-- fecha AAAA-MM-DD y hora HH:MM, en hora local de Rubén, sin zona.
-- partes: JSON [{"texto", "medios": [id]}]. Un post tiene una; un hilo, varias.
CREATE TABLE publicaciones (
  id TEXT PRIMARY KEY,
  canal_id TEXT NOT NULL REFERENCES canales(id),
  formato TEXT NOT NULL,
  fecha TEXT NOT NULL,
  hora TEXT,
  estado TEXT NOT NULL DEFAULT 'idea',
  titulo TEXT NOT NULL DEFAULT '',
  partes TEXT NOT NULL DEFAULT '[{"texto":"","medios":[]}]',
  articulo_id TEXT REFERENCES articulos(id),
  enlace TEXT,
  origen TEXT NOT NULL DEFAULT 'tu',
  creado TEXT NOT NULL, cambiado TEXT NOT NULL,
  rev INTEGER NOT NULL, borrado INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX publicaciones_fecha ON publicaciones(fecha) WHERE borrado = 0;
"#, r#"
-- dispositivo: quién guardó una variante «conflicto» (web, android o mcp). Nulo en las demás.
ALTER TABLE variantes_bloque ADD COLUMN dispositivo TEXT;
"#, r#"
-- Texto de una variante que viene de una propuesta del agente aceptada.
-- desde y hasta: caracteres (no bytes) en el contenido crudo, con sus marcas.
-- No tiene rev: viaja dentro de su variante (campo autoria) y cambia con ella.
CREATE TABLE autoria_ia (
  id INTEGER PRIMARY KEY,
  variante_bloque_id TEXT NOT NULL REFERENCES variantes_bloque(id),
  desde INTEGER NOT NULL,
  hasta INTEGER NOT NULL,
  agente TEXT NOT NULL,
  creado TEXT NOT NULL
);
CREATE INDEX autoria_ia_variante ON autoria_ia(variante_bloque_id);
"#, r#"
-- Ideas sin fecha ni canal: fecha, canal_id y formato admiten nulo.
-- SQLite no quita un NOT NULL con ALTER, así que se rehace la tabla. Ninguna
-- otra tabla la referencia, así que borrarla no rompe claves externas.
CREATE TABLE publicaciones_nueva (
  id TEXT PRIMARY KEY,
  canal_id TEXT REFERENCES canales(id),
  formato TEXT,
  fecha TEXT,
  hora TEXT,
  estado TEXT NOT NULL DEFAULT 'idea',
  titulo TEXT NOT NULL DEFAULT '',
  partes TEXT NOT NULL DEFAULT '[{"texto":"","medios":[]}]',
  articulo_id TEXT REFERENCES articulos(id),
  enlace TEXT,
  origen TEXT NOT NULL DEFAULT 'tu',
  creado TEXT NOT NULL, cambiado TEXT NOT NULL,
  rev INTEGER NOT NULL, borrado INTEGER NOT NULL DEFAULT 0
);
INSERT INTO publicaciones_nueva (id, canal_id, formato, fecha, hora, estado, titulo, partes, articulo_id, enlace, origen, creado, cambiado, rev, borrado)
  SELECT id, canal_id, formato, fecha, hora, estado, titulo, partes, articulo_id, enlace, origen, creado, cambiado, rev, borrado FROM publicaciones;
DROP TABLE publicaciones;
ALTER TABLE publicaciones_nueva RENAME TO publicaciones;
CREATE INDEX publicaciones_fecha ON publicaciones(fecha) WHERE borrado = 0;
"#];

pub fn abrir(ruta: &Path) -> rusqlite::Result<Connection> {
    let c = Connection::open(ruta)?;
    preparar(c)
}

#[cfg(test)]
pub fn en_memoria() -> Connection {
    preparar(Connection::open_in_memory().unwrap()).unwrap()
}

fn preparar(mut c: Connection) -> rusqlite::Result<Connection> {
    c.pragma_update(None, "journal_mode", "WAL")?;
    c.pragma_update(None, "foreign_keys", true)?;
    c.pragma_update(None, "busy_timeout", 5000)?;
    let hecha: i64 = c.pragma_query_value(None, "user_version", |r| r.get(0))?;
    let tx = c.transaction()?;
    for (i, sql) in MIGRACIONES.iter().enumerate().skip(hecha as usize) {
        tx.execute_batch(sql)?;
        tx.pragma_update(None, "user_version", (i + 1) as i64)?;
    }
    tx.commit()?;
    Ok(c)
}

/// Sube el contador global y devuelve el valor nuevo, para marcar la fila.
pub fn siguiente_rev(c: &Connection) -> rusqlite::Result<i64> {
    c.query_row(
        "UPDATE meta SET valor = CAST(valor AS INTEGER) + 1 WHERE clave = 'rev' RETURNING CAST(valor AS INTEGER)",
        [],
        |r| r.get(0),
    )
}

pub fn rev_actual(c: &Connection) -> rusqlite::Result<i64> {
    c.query_row("SELECT CAST(valor AS INTEGER) FROM meta WHERE clave = 'rev'", [], |r| r.get(0))
}

pub fn existe(c: &Connection, tabla: &str, id: &str) -> rusqlite::Result<bool> {
    let sql = format!("SELECT 1 FROM {tabla} WHERE id = ?1 AND borrado = 0");
    Ok(c.query_row(&sql, params![id], |_| Ok(())).optional()?.is_some())
}

#[cfg(test)]
mod pruebas {
    #[test]
    fn migra_y_cuenta() {
        let c = super::en_memoria();
        assert_eq!(super::siguiente_rev(&c).unwrap(), 1);
        assert_eq!(super::siguiente_rev(&c).unwrap(), 2);
        assert_eq!(super::rev_actual(&c).unwrap(), 2);
    }

    #[test]
    fn migra_publicaciones_sin_perder_datos() {
        let c = rusqlite::Connection::open_in_memory().unwrap();
        c.pragma_update(None, "foreign_keys", true).unwrap();
        let anteriores = super::MIGRACIONES.len() - 1;
        for sql in &super::MIGRACIONES[..anteriores] {
            c.execute_batch(sql).unwrap();
        }
        c.pragma_update(None, "user_version", anteriores as i64).unwrap();
        c.execute_batch(
            r#"INSERT INTO publicaciones (id, canal_id, formato, fecha, hora, estado, titulo, partes, creado, cambiado, rev) VALUES
               ('p1', 'x', 'post', '2026-11-01', '18:00', 'programada', 'Uno', '[{"texto":"hola","medios":[]}]', 't', 't', 5),
               ('p2', 'portfolio', 'articulo', '2026-11-02', NULL, 'idea', '', '[{"texto":"","medios":[]}]', 't', 't', 6);"#,
        )
        .unwrap();

        let c = super::preparar(c).unwrap();
        let fila: (String, Option<String>, String, String, Option<String>, i64) = c
            .query_row(
                "SELECT canal_id, hora, formato, fecha, titulo, rev FROM publicaciones WHERE id = 'p1'",
                [],
                |r| Ok((r.get(0)?, r.get(1)?, r.get(2)?, r.get(3)?, r.get(4)?, r.get(5)?)),
            )
            .unwrap();
        assert_eq!((fila.0.as_str(), fila.1.as_deref(), fila.2.as_str(), fila.3.as_str(), fila.4.as_deref(), fila.5), ("x", Some("18:00"), "post", "2026-11-01", Some("Uno"), 5));
        let total: i64 = c.query_row("SELECT count(*) FROM publicaciones", [], |r| r.get(0)).unwrap();
        assert_eq!(total, 2);
        // Ahora cabe una idea sin fecha ni canal, y el índice por fecha sigue ahí.
        c.execute("INSERT INTO publicaciones (id, creado, cambiado, rev) VALUES ('p3', 't', 't', 7)", []).unwrap();
        let indices: i64 = c.query_row("SELECT count(*) FROM sqlite_master WHERE name = 'publicaciones_fecha'", [], |r| r.get(0)).unwrap();
        assert_eq!(indices, 1);
        let version: i64 = c.pragma_query_value(None, "user_version", |r| r.get(0)).unwrap();
        assert_eq!(version, super::MIGRACIONES.len() as i64);
    }
}
