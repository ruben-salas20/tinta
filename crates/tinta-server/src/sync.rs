//! Sincronización con la app de Android (fase 4), que trabaja sin conexión.
//!
//! - **Bajar:** `GET /api/sync?desde=N` devuelve cada fila con `rev > N`, de
//!   todas las tablas que viven en el móvil, lápidas incluidas (`borrado = 1`),
//!   y la rev actual, que el móvil guarda para la próxima vez.
//! - **Subir:** `POST /api/sync` aplica la cola de cambios del móvil, en orden.
//!   Cada operación usa la misma lógica que la web (`modelo`, `calendario`).
//!
//! Reglas para que reintentar una cola sea seguro (la red puede cortarse justo
//! después de aplicar y antes de responder):
//! - Crear lleva los ids que eligió el móvil. Si ya existe, no se repite.
//! - Borrar lo ya borrado no es un error.
//! - Guardar texto lleva `rev_base`: si otro dispositivo cambió el bloque, el
//!   texto entra como variante «conflicto» y no pisa nada.
//! - El resto de cambios (fechas, estados, títulos) gana el último en llegar.

use crate::calendario;
use crate::critic::Decision;
use crate::db::{existe, rev_actual};
use crate::modelo::{self, Error, IdsDados, Res};
use rusqlite::Connection;
use rusqlite::types::ValueRef;
use serde::Deserialize;
use serde_json::{Map, Value, json};

/// Tablas que se copian al móvil. El historial y las sesiones se quedan aquí.
const TABLAS: [&str; 12] = [
    "series",
    "etiquetas",
    "articulos",
    "idiomas",
    "ramas",
    "secciones",
    "variantes_seccion",
    "bloques",
    "variantes_bloque",
    "medios",
    "canales",
    "publicaciones",
];

/// Columnas que guardan JSON como texto: se mandan ya como JSON.
const JSON: [&str; 2] = ["formatos", "partes"];

pub fn bajar(c: &Connection, desde: i64) -> Res<Value> {
    let rev = rev_actual(c)?;
    let mut tablas = Map::new();
    for t in TABLAS {
        let mut q = c.prepare(&format!("SELECT * FROM {t} WHERE rev > ?1 ORDER BY rev"))?;
        let cols: Vec<String> = q.column_names().into_iter().map(String::from).collect();
        let filas: Vec<Value> = q
            .query_map([desde], |r| {
                let mut m = Map::new();
                for (i, col) in cols.iter().enumerate() {
                    let v = match r.get_ref(i)? {
                        ValueRef::Null => Value::Null,
                        ValueRef::Integer(n) => json!(n),
                        ValueRef::Real(f) => json!(f),
                        ValueRef::Text(b) => {
                            let s = String::from_utf8_lossy(b).into_owned();
                            if JSON.contains(&col.as_str()) { serde_json::from_str(&s).unwrap_or(Value::String(s)) } else { Value::String(s) }
                        }
                        ValueRef::Blob(_) => Value::Null,
                    };
                    m.insert(col.clone(), v);
                }
                Ok(Value::Object(m))
            })?
            .collect::<Result<_, _>>()?;
        tablas.insert(t.into(), Value::Array(filas));
    }
    // Los rangos de autoría no tienen tabla propia en el móvil: van dentro de
    // su variante, que cambia con ellos (su rev sube al cambiar el texto).
    if let Some(Value::Array(vbs)) = tablas.get_mut("variantes_bloque") {
        for v in vbs {
            let id = v["id"].as_str().unwrap_or_default().to_string();
            v["autoria"] = json!(modelo::autoria_de(c, &id)?);
        }
    }
    // Las etiquetas de cada artículo no tienen rev propia: cambiarlas sube la
    // del artículo, así que van dentro de su fila.
    if let Some(Value::Array(arts)) = tablas.get_mut("articulos") {
        let mut q = c.prepare(
            "SELECT e.id FROM articulo_etiquetas ae JOIN etiquetas e ON e.id = ae.etiqueta_id WHERE ae.articulo_id = ?1 ORDER BY e.nombre",
        )?;
        for a in arts {
            let id = a["id"].as_str().unwrap_or_default().to_string();
            let ids: Vec<String> = q.query_map([&id], |r| r.get(0))?.collect::<Result<_, _>>()?;
            a["etiquetas"] = json!(ids);
        }
    }
    Ok(json!({ "rev": rev, "tablas": tablas }))
}

#[derive(Debug, Deserialize)]
#[serde(tag = "op", rename_all = "snake_case")]
pub enum Op {
    Guardar { variante_bloque_id: String, contenido: String, rev_base: i64 },
    CrearArticulo { titulo: String, ids: IdsDados },
    CambiarArticulo { id: String, cambio: modelo::CambioArticulo },
    CambiarIdioma { id: String, cambio: modelo::CambioIdioma },
    BorrarArticulo { id: String },
    RestaurarArticulo { id: String },
    CrearIdioma { articulo_id: String, idioma: String, copiar_de: Option<String>, ids: IdsDados },
    BorrarIdioma { id: String },
    ForkRama { rama_id: String, nombre: String, ids: IdsDados },
    RenombrarRama { id: String, nombre: String },
    BorrarRama { id: String },
    CrearSerie { nombre: String, ids: IdsDados },
    RenombrarSerie { id: String, nombre: String },
    BorrarSerie { id: String },
    CrearSeccion { rama_id: String, #[serde(default)] titulo: String, antes: Option<String>, despues: Option<String>, ids: IdsDados },
    CambiarSeccion { id: String, cambio: modelo::CambioSeccion },
    BorrarSeccion { id: String },
    CambiarVarianteSeccion { id: String, titulo: String },
    ForkSeccion { seccion_id: String, desde: Option<String>, #[serde(default)] activar: bool, ids: IdsDados },
    BorrarVarianteSeccion { id: String },
    CrearBloque {
        variante_seccion_id: String,
        #[serde(default = "parrafo")]
        tipo: String,
        #[serde(default)]
        contenido: String,
        antes: Option<String>,
        despues: Option<String>,
        ids: IdsDados,
    },
    CambiarBloque { id: String, cambio: modelo::CambioBloque },
    BorrarBloque { id: String },
    ForkBloque { bloque_id: String, contenido: Option<String>, #[serde(default)] activar: bool, ids: IdsDados },
    BorrarVarianteBloque { id: String },
    ResolverMarca { variante_bloque_id: String, indice: usize, decision: Decision },
    CrearPublicacion { publicacion: calendario::NuevaPublicacion, ids: IdsDados },
    CambiarPublicacion { id: String, cambio: calendario::CambioPublicacion },
    BorrarPublicacion { id: String },
}

fn parrafo() -> String {
    "parrafo".into()
}

/// Si la pieza que se iba a crear ya existe, la operación ya se aplicó.
fn ya_creado(c: &Connection, tabla: &str, id: &Option<String>) -> Res<bool> {
    match id {
        Some(id) => Ok(existe(c, tabla, id)?),
        None => Err(Error::Invalido(format!("crear sin conexión necesita el id de {tabla}"))),
    }
}

/// Borrar lo que ya no existe no es un error: el otro dispositivo se adelantó.
fn borrado_ok<T>(r: Res<T>) -> Res<Value> {
    match r {
        Ok(_) | Err(Error::NoExiste(_)) => Ok(json!({})),
        Err(e) => Err(e),
    }
}

pub fn aplicar(c: &mut Connection, op: Op, dispositivo: &str) -> Res<Value> {
    use Op::*;
    match op {
        Guardar { variante_bloque_id, contenido, rev_base } => {
            let g = modelo::guardar_variante_bloque(c, &variante_bloque_id, &contenido, rev_base, dispositivo)?;
            Ok(json!({ "conflicto": g.conflicto, "variante_id": g.variante.id, "rev": g.variante.rev }))
        }
        CrearArticulo { titulo, ids } => {
            if ya_creado(c, "articulos", &ids.articulo)? {
                return Ok(json!({ "repetida": true }));
            }
            modelo::con_ids(ids, || modelo::crear_articulo(c, &titulo)).map(|_| json!({}))
        }
        CambiarArticulo { id, cambio } => modelo::actualizar_articulo(c, &id, cambio).map(|_| json!({})),
        CambiarIdioma { id, cambio } => modelo::actualizar_idioma(c, &id, cambio).map(|_| json!({})),
        BorrarArticulo { id } => borrado_ok(modelo::borrar_articulo(c, &id)),
        // Restaurar lo que ya está vivo tampoco es un error.
        RestaurarArticulo { id } => borrado_ok(modelo::restaurar_articulo(c, &id)),
        CrearIdioma { articulo_id, idioma, copiar_de, ids } => {
            if ya_creado(c, "idiomas", &ids.idioma)? {
                return Ok(json!({ "repetida": true }));
            }
            modelo::con_ids(ids, || modelo::crear_idioma(c, &articulo_id, &idioma, copiar_de.as_deref())).map(|_| json!({}))
        }
        BorrarIdioma { id } => borrado_ok(modelo::borrar_idioma(c, &id)),
        ForkRama { rama_id, nombre, ids } => {
            if ya_creado(c, "ramas", &ids.rama)? {
                return Ok(json!({ "repetida": true }));
            }
            modelo::con_ids(ids, || modelo::fork_rama(c, &rama_id, &nombre)).map(|_| json!({}))
        }
        RenombrarRama { id, nombre } => modelo::renombrar_rama(c, &id, &nombre).map(|_| json!({})),
        BorrarRama { id } => borrado_ok(modelo::borrar_rama(c, &id)),
        CrearSerie { nombre, ids } => {
            if ya_creado(c, "series", &ids.serie)? {
                return Ok(json!({ "repetida": true }));
            }
            modelo::con_ids(ids, || modelo::crear_serie(c, &nombre)).map(|_| json!({}))
        }
        RenombrarSerie { id, nombre } => modelo::renombrar_serie(c, &id, &nombre).map(|_| json!({})),
        BorrarSerie { id } => borrado_ok(modelo::borrar_serie(c, &id)),
        ForkSeccion { seccion_id, desde, activar, ids } => {
            if ya_creado(c, "variantes_seccion", &ids.variante_seccion)? {
                return Ok(json!({ "repetida": true }));
            }
            let f = modelo::ForkSeccion { desde, activar, origen: "tu".into() };
            modelo::con_ids(ids, || modelo::fork_seccion(c, &seccion_id, f)).map(|_| json!({}))
        }
        BorrarVarianteSeccion { id } => borrado_ok(modelo::borrar_variante_seccion(c, &id)),
        CrearSeccion { rama_id, titulo, antes, despues, ids } => {
            if ya_creado(c, "secciones", &ids.seccion)? {
                return Ok(json!({ "repetida": true }));
            }
            let n = modelo::NuevaSeccion { titulo, entre: modelo::Entre { antes, despues } };
            modelo::con_ids(ids, || modelo::crear_seccion(c, &rama_id, n)).map(|_| json!({}))
        }
        CambiarSeccion { id, cambio } => modelo::actualizar_seccion(c, &id, cambio).map(|_| json!({})),
        BorrarSeccion { id } => borrado_ok(modelo::borrar_seccion(c, &id)),
        CambiarVarianteSeccion { id, titulo } => {
            modelo::actualizar_variante_seccion(c, &id, modelo::CambioVarianteSeccion { titulo: Some(titulo) }).map(|_| json!({}))
        }
        CrearBloque { variante_seccion_id, tipo, contenido, antes, despues, ids } => {
            if ya_creado(c, "bloques", &ids.bloque)? {
                return Ok(json!({ "repetida": true }));
            }
            let n = modelo::NuevoBloque { tipo, contenido, entre: modelo::Entre { antes, despues } };
            modelo::con_ids(ids, || modelo::crear_bloque(c, &variante_seccion_id, n)).map(|_| json!({}))
        }
        CambiarBloque { id, cambio } => modelo::actualizar_bloque(c, &id, cambio).map(|_| json!({})),
        BorrarBloque { id } => borrado_ok(modelo::borrar_bloque(c, &id)),
        ForkBloque { bloque_id, contenido, activar, ids } => {
            if ya_creado(c, "variantes_bloque", &ids.variante_bloque)? {
                return Ok(json!({ "repetida": true }));
            }
            let f = modelo::ForkBloque { contenido, activar, origen: "tu".into() };
            modelo::con_ids(ids, || modelo::fork_bloque(c, &bloque_id, f)).map(|_| json!({}))
        }
        BorrarVarianteBloque { id } => borrado_ok(modelo::borrar_variante_bloque(c, &id)),
        ResolverMarca { variante_bloque_id, indice, decision } => {
            modelo::resolver_marca(c, &variante_bloque_id, indice, decision, dispositivo).map(|_| json!({}))
        }
        CrearPublicacion { mut publicacion, ids } => {
            if ya_creado(c, "publicaciones", &ids.publicacion)? {
                return Ok(json!({ "repetida": true }));
            }
            publicacion.origen = "tu".into();
            modelo::con_ids(ids, || calendario::crear_publicacion(c, publicacion)).map(|_| json!({}))
        }
        CambiarPublicacion { id, cambio } => calendario::actualizar_publicacion(c, &id, cambio).map(|_| json!({})),
        BorrarPublicacion { id } => borrado_ok(calendario::borrar_publicacion(c, &id)),
    }
}

/// Aplica la cola entera. Un fallo no para las siguientes: cada una dice cómo
/// fue, y el móvil descarta las que fallaron por datos no válidos.
pub fn subir(c: &mut Connection, ops: Vec<Value>, dispositivo: &str) -> Value {
    let resultados: Vec<Value> = ops
        .into_iter()
        .map(|v| match serde_json::from_value::<Op>(v) {
            Err(e) => json!({ "ok": false, "error": format!("operación no válida: {e}") }),
            Ok(op) => match aplicar(c, op, dispositivo) {
                Ok(mut r) => {
                    r["ok"] = json!(true);
                    r
                }
                Err(Error::NoExiste(q)) => json!({ "ok": false, "error": format!("no existe: {q}") }),
                Err(Error::Invalido(m)) => json!({ "ok": false, "error": m }),
                Err(Error::Db(e)) => {
                    tracing::error!("sync, base de datos: {e}");
                    json!({ "ok": false, "error": "error de base de datos", "reintentar": true })
                }
            },
        })
        .collect();
    json!({ "resultados": resultados, "rev": rev_actual(c).unwrap_or(0) })
}

#[cfg(test)]
mod pruebas {
    use super::*;
    use crate::db::en_memoria;

    fn id(n: u8) -> String {
        format!("{:024x}", n as u128 + 0xabc0000)
    }

    #[test]
    fn crear_sin_conexion_y_repetir() {
        let mut c = en_memoria();
        let ids = json!({ "articulo": id(1), "idioma": id(2), "rama": id(3), "seccion": id(4), "variante_seccion": id(5), "bloque": id(6), "variante_bloque": id(7) });
        let cola = vec![
            json!({ "op": "crear_articulo", "titulo": "Desde el móvil", "ids": ids }),
            json!({ "op": "guardar", "variante_bloque_id": id(7), "contenido": "Escrito sin red", "rev_base": 1 }),
            json!({ "op": "crear_bloque", "variante_seccion_id": id(5), "contenido": "Segundo", "antes": id(6), "ids": { "bloque": id(8), "variante_bloque": id(9) } }),
            json!({ "op": "crear_publicacion", "publicacion": { "canal_id": "x", "formato": "post", "fecha": "2026-11-01" }, "ids": { "publicacion": id(10) } }),
            json!({ "op": "cambiar_publicacion", "id": id(10), "cambio": { "hora": "09:00" } }),
        ];
        let r = subir(&mut c, cola.clone(), "android");
        assert!(r["resultados"].as_array().unwrap().iter().all(|x| x["ok"] == true), "{r}");
        let d = modelo::documento(&c, &id(3)).unwrap();
        let bloques = &d.secciones[0].variantes[0].bloques;
        assert_eq!(bloques.len(), 2);
        assert_eq!(bloques[0].variantes[0].contenido, "Escrito sin red");
        assert_eq!(bloques[1].id, id(8));
        // Reenviar la misma cola no duplica nada.
        let r2 = subir(&mut c, cola, "android");
        assert_eq!(r2["resultados"][0]["repetida"], true);
        assert_eq!(modelo::listar_articulos(&c).unwrap().len(), 1);
        assert_eq!(modelo::documento(&c, &id(3)).unwrap().secciones[0].variantes[0].bloques.len(), 2);
        assert_eq!(calendario::publicacion(&c, &id(10)).unwrap().hora.as_deref(), Some("09:00"));
    }

    #[test]
    fn conflicto_y_errores_sin_parar() {
        let mut c = en_memoria();
        let a = modelo::crear_articulo(&mut c, "A").unwrap();
        let d = modelo::documento(&c, a.idiomas[0].resumen.rama_principal_id.as_ref().unwrap()).unwrap();
        let v = d.secciones[0].variantes[0].bloques[0].variantes[0].clone();
        modelo::guardar_variante_bloque(&mut c, &v.id, "desde la web", v.rev, "web").unwrap();
        let r = subir(
            &mut c,
            vec![
                json!({ "op": "guardar", "variante_bloque_id": v.id, "contenido": "desde el móvil", "rev_base": v.rev }),
                json!({ "op": "nada" }),
                json!({ "op": "borrar_bloque", "id": "000000000000000000000000" }),
                json!({ "op": "crear_bloque", "variante_seccion_id": "x", "ids": { "bloque": "MAL" } }),
            ],
            "android",
        );
        let rs = r["resultados"].as_array().unwrap();
        assert_eq!(rs[0]["conflicto"], true);
        assert_eq!(rs[1]["ok"], false);
        assert_eq!(rs[2]["ok"], true, "borrar lo que no existe no es error");
        assert_eq!(rs[3]["ok"], false);
        assert_eq!(modelo::variante_bloque(&c, &v.id).unwrap().contenido, "desde la web");
    }

    #[test]
    fn bajar_desde_una_rev() {
        let mut c = en_memoria();
        let todo = bajar(&c, -1).unwrap();
        assert_eq!(todo["tablas"]["canales"].as_array().unwrap().len(), 6);
        assert!(todo["tablas"]["canales"][0]["formatos"].is_array());
        let r0 = todo["rev"].as_i64().unwrap();
        let a = modelo::crear_articulo(&mut c, "Nuevo").unwrap();
        modelo::actualizar_articulo(&mut c, &a.id, modelo::CambioArticulo { etiquetas: Some(vec!["ia".into()]), ..Default::default() }).unwrap();
        let nuevo = bajar(&c, r0).unwrap();
        assert!(nuevo["tablas"]["canales"].as_array().unwrap().is_empty());
        let arts = nuevo["tablas"]["articulos"].as_array().unwrap();
        assert_eq!(arts.len(), 1);
        assert_eq!(arts[0]["etiquetas"].as_array().unwrap().len(), 1);
        assert_eq!(nuevo["tablas"]["variantes_bloque"].as_array().unwrap().len(), 1);
        modelo::borrar_articulo(&mut c, &a.id).unwrap();
        let tras = bajar(&c, nuevo["rev"].as_i64().unwrap()).unwrap();
        assert_eq!(tras["tablas"]["articulos"][0]["borrado"], 1);
    }

    #[test]
    fn bajar_lleva_la_autoria_de_cada_variante() {
        let mut c = en_memoria();
        let a = modelo::crear_articulo(&mut c, "A").unwrap();
        let d = modelo::documento(&c, a.idiomas[0].resumen.rama_principal_id.as_ref().unwrap()).unwrap();
        let v = d.secciones[0].variantes[0].bloques[0].variantes[0].clone();
        modelo::guardar_variante_bloque(&mut c, &v.id, "Hola {++mundo++}{>>ia:claude<<}", v.rev, "web").unwrap();
        modelo::resolver_marca(&mut c, &v.id, 0, crate::critic::Decision::Aceptar, "web").unwrap();
        let r = bajar(&c, -1).unwrap();
        let fila = &r["tablas"]["variantes_bloque"][0];
        assert_eq!(fila["contenido"], "Hola mundo");
        assert_eq!(fila["autoria"], json!([{ "desde": 5, "hasta": 10, "agente": "claude" }]));
    }

    #[test]
    fn copias_con_ids_derivados() {
        assert_eq!(modelo::id_de(&format!("{}:{}", "a".repeat(24), "b".repeat(24))), "db1a5ddbd892f21e46beccd1");
        let mut c = en_memoria();
        let a = modelo::crear_articulo(&mut c, "Copiar").unwrap();
        let es = &a.idiomas[0];
        let rama = es.resumen.rama_principal_id.clone().unwrap();
        let doc = modelo::documento(&c, &rama).unwrap();
        let s0 = &doc.secciones[0];
        let semilla = id(50);
        let cola = vec![
            json!({ "op": "fork_rama", "rama_id": rama, "nombre": "otra", "ids": { "rama": id(51), "semilla": semilla } }),
            json!({ "op": "fork_rama", "rama_id": rama, "nombre": "otra", "ids": { "rama": id(51), "semilla": semilla } }),
            json!({ "op": "crear_idioma", "articulo_id": a.id, "idioma": "en", "copiar_de": "es", "ids": { "idioma": id(52), "rama": id(53), "semilla": id(54) } }),
            json!({ "op": "fork_seccion", "seccion_id": s0.id, "activar": true, "ids": { "variante_seccion": id(55), "semilla": id(56) } }),
            json!({ "op": "crear_serie", "nombre": "Serie", "ids": { "serie": id(57) } }),
            json!({ "op": "cambiar_articulo", "id": a.id, "cambio": { "serie_id": id(57), "etiquetas": ["IA"] } }),
        ];
        let r = subir(&mut c, cola, "android");
        let rs = r["resultados"].as_array().unwrap();
        assert!(rs.iter().all(|x| x["ok"] == true), "{rs:?}");
        assert_eq!(rs[1]["repetida"], true);
        let copia = modelo::documento(&c, &id(51)).unwrap();
        assert_eq!(copia.secciones[0].id, modelo::id_de(&format!("{semilla}:{}", s0.id)));
        let b0 = &s0.variantes[0].bloques[0];
        let cb = &copia.secciones[0].variantes[0].bloques[0];
        assert_eq!(cb.id, modelo::id_de(&format!("{semilla}:{}", b0.id)));
        assert_eq!(cb.variantes[0].id, modelo::id_de(&format!("{semilla}:{}", b0.variantes[0].id)));
        let en = modelo::documento(&c, &id(53)).unwrap();
        assert_eq!(en.secciones[0].id, modelo::id_de(&format!("{}:{}", id(54), s0.id)));
        let tras = modelo::documento(&c, &rama).unwrap();
        assert_eq!(tras.secciones[0].variante_activa_id.as_deref(), Some(id(55).as_str()));
        let art = modelo::articulo(&c, &a.id).unwrap();
        assert_eq!(art.serie.map(|s| s.id).as_deref(), Some(id(57).as_str()));
        let eid: String = c.query_row("SELECT id FROM etiquetas WHERE nombre = 'ia'", [], |r| r.get(0)).unwrap();
        assert_eq!(eid, "44000961e19ffa8806626024");
    }
}
