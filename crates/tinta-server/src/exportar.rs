//! Lo que sale de Tinta: el `.md` y el `.zip` del portfolio, y el HTML para
//! pegar en X Articles.
//!
//! Siempre se exporta la rama principal de cada idioma, con la variante
//! activa de cada sección y de cada bloque, y sin lo pendiente (ver `critic`).
//!
//! El formato sigue la plantilla del portfolio (ruben-salas20/portfolio,
//! `src/content/articles/plantilla/es.md`):
//! - Cada artículo es la carpeta `src/content/articles/<slug>/` con `es.md`,
//!   `en.md` y sus imágenes al lado.
//! - El audio va en `public/articulos/<slug>/` y se enlaza con ruta absoluta.
//! - Un vídeo de YouTube es su enlace solo en una línea.

use crate::critic;
use crate::modelo::{self, Error, Medio, Res};
use rusqlite::{Connection, params};
use serde::Serialize;
use std::collections::BTreeMap;
use std::io::Write;
use std::path::Path;

/// Un medio usado por el artículo y el nombre que lleva al exportarlo.
#[derive(Debug, Clone)]
pub struct Archivo {
    pub medio: Medio,
    pub nombre: String,
}

impl Archivo {
    pub fn es_audio(&self) -> bool {
        self.medio.tipo.starts_with("audio/")
    }
}

#[derive(Debug, Serialize, Default)]
pub struct Avisos {
    pub tachados: u32,
    pub propuestas: u32,
    pub notas: u32,
    pub conflictos: u32,
    pub imagenes_sin_alt: u32,
    pub imagenes: u32,
    pub audios: u32,
    pub videos: u32,
}

pub struct Exportado {
    pub idioma: String,
    pub markdown: String,
    /// Los trozos del cuerpo por separado: cada subtítulo y cada bloque.
    /// Un bloque de código puede llevar líneas en blanco, así que el cuerpo
    /// no se puede volver a partir por ellas.
    pub partes: Vec<String>,
    pub titulo: String,
    pub archivos: BTreeMap<String, Archivo>,
    pub avisos: Avisos,
}

/// Nombre de archivo seguro y sin repetir: «Mi Gráfica.PNG» → «mi-grafica.png».
fn nombre_archivo(nombre: &str, usados: &BTreeMap<String, Archivo>) -> String {
    let (base, ext) = match nombre.rsplit_once('.') {
        Some((b, e)) if !b.is_empty() && e.len() <= 5 => (b, format!(".{}", e.to_ascii_lowercase())),
        _ => (nombre, String::new()),
    };
    let base = modelo::slug(base);
    let mut n = 1;
    loop {
        let candidato = if n == 1 { format!("{base}{ext}") } else { format!("{base}-{n}{ext}") };
        if !usados.values().any(|a| a.nombre == candidato) {
            return candidato;
        }
        n += 1;
    }
}

const PREFIJO: &str = "medio:";

/// Cambia cada `medio:<id>` por su ruta de exportación y anota el archivo.
fn enlazar_medios(c: &Connection, texto: &str, slug: &str, archivos: &mut BTreeMap<String, Archivo>) -> Res<String> {
    let mut out = String::new();
    let mut resto = texto;
    while let Some(i) = resto.find(PREFIJO) {
        out.push_str(&resto[..i]);
        let tras = &resto[i + PREFIJO.len()..];
        let largo = tras.bytes().take_while(|b| b.is_ascii_hexdigit()).count();
        let id = &tras[..largo];
        resto = &tras[largo..];
        if id.is_empty() {
            out.push_str(PREFIJO);
            continue;
        }
        if !archivos.contains_key(id) {
            let medio = modelo::medio(c, id)?;
            let nombre = nombre_archivo(&medio.nombre, archivos);
            archivos.insert(id.to_string(), Archivo { medio, nombre });
        }
        let a = &archivos[id];
        if a.es_audio() {
            out.push_str(&format!("/articulos/{slug}/{}", a.nombre));
        } else {
            out.push_str(&format!("./{}", a.nombre));
        }
    }
    out.push_str(resto);
    Ok(out)
}

/// YAML entre comillas dobles: aguanta dos puntos, comillas y almohadillas.
fn yaml(s: &str) -> String {
    format!("\"{}\"", s.replace('\\', "\\\\").replace('"', "\\\"").replace('\n', " "))
}

pub fn exportar_idioma(c: &Connection, idioma_id: &str) -> Res<Exportado> {
    let (articulo_id, idioma, rama): (String, String, Option<String>) = c.query_row(
        "SELECT articulo_id, idioma, rama_principal_id FROM idiomas WHERE id = ?1 AND borrado = 0",
        params![idioma_id],
        |r| Ok((r.get(0)?, r.get(1)?, r.get(2)?)),
    )?;
    let a = modelo::articulo(c, &articulo_id)?;
    let i = &a.idiomas.iter().find(|i| i.resumen.id == idioma_id).ok_or(Error::NoExiste("idioma"))?.resumen;
    let doc = modelo::documento(c, rama.as_deref().ok_or(Error::NoExiste("rama principal"))?)?;

    let mut avisos = Avisos::default();
    for s in &doc.secciones {
        for vs in &s.variantes {
            if vs.origen == "conflicto" {
                avisos.conflictos += 1;
            }
            for b in &vs.bloques {
                avisos.conflictos += b.variantes.iter().filter(|v| v.origen == "conflicto").count() as u32;
            }
        }
    }

    let mut archivos = BTreeMap::new();
    let mut partes: Vec<String> = Vec::new();
    for s in modelo::activo(&doc) {
        if !s.titulo_seccion.is_empty() {
            partes.push(format!("## {}", critic::limpio_prosa(&s.titulo_seccion)));
        }
        for (tipo, contenido) in s.bloques {
            let r = critic::contar(&contenido);
            avisos.tachados += r.tachados;
            avisos.propuestas += r.propuestas;
            avisos.notas += r.notas;
            let texto = if tipo == "codigo" { critic::limpio(&contenido).trim_matches('\n').to_string() } else { critic::limpio_prosa(&contenido) };
            if texto.trim().is_empty() {
                continue;
            }
            match tipo.as_str() {
                "imagen" => {
                    avisos.imagenes += 1;
                    if texto.starts_with("![]") {
                        avisos.imagenes_sin_alt += 1;
                    }
                }
                "audio" => avisos.audios += 1,
                "youtube" => avisos.videos += 1,
                _ => {}
            }
            partes.push(enlazar_medios(c, &texto, &a.slug, &mut archivos)?);
        }
    }
    let cuerpo = partes.join("\n\n");

    let fecha = a.fecha.clone().unwrap_or_else(|| chrono::Utc::now().format("%Y-%m-%d").to_string());
    let mut fm = format!("---\ntitle: {}\ndescription: {}\ndate: {fecha}\n", yaml(&i.titulo), yaml(&i.descripcion));
    if i.borrador {
        fm.push_str("draft: true\n");
    }
    if i.traducido_ia {
        fm.push_str("translatedWithAI: true\n");
    }
    fm.push_str("---\n\n");
    Ok(Exportado { idioma, markdown: format!("{fm}{cuerpo}\n"), partes, titulo: i.titulo.clone(), archivos, avisos })
}

/// El `.zip` para descomprimir en la raíz del repo del portfolio.
pub fn zip(c: &Connection, articulo_id: &str, idiomas: &[String], medios: &Path) -> Res<(String, Vec<u8>)> {
    let a = modelo::articulo(c, articulo_id)?;
    let elegidos: Vec<_> = a.idiomas.iter().filter(|i| idiomas.is_empty() || idiomas.contains(&i.resumen.idioma)).collect();
    if elegidos.is_empty() {
        return Err(Error::Invalido("ninguno de esos idiomas existe en el artículo".into()));
    }
    let mut z = zip::ZipWriter::new(std::io::Cursor::new(Vec::new()));
    let op = zip::write::SimpleFileOptions::default().compression_method(zip::CompressionMethod::Deflated);
    let mut escritos = std::collections::BTreeSet::new();
    let carpeta = format!("src/content/articles/{}", a.slug);
    for i in elegidos {
        let e = exportar_idioma(c, &i.resumen.id)?;
        let fallo = |e: zip::result::ZipError| Error::Invalido(format!("no se pudo crear el zip: {e}"));
        z.start_file(format!("{carpeta}/{}.md", e.idioma), op).map_err(fallo)?;
        z.write_all(e.markdown.as_bytes()).map_err(|e| Error::Invalido(e.to_string()))?;
        for f in e.archivos.values() {
            let ruta = if f.es_audio() { format!("public/articulos/{}/{}", a.slug, f.nombre) } else { format!("{carpeta}/{}", f.nombre) };
            if !escritos.insert(ruta.clone()) {
                continue;
            }
            let datos = std::fs::read(medios.join(&f.medio.sha256))
                .map_err(|err| Error::Invalido(format!("falta el archivo de «{}»: {err}", f.medio.nombre)))?;
            z.start_file(ruta, op).map_err(fallo)?;
            z.write_all(&datos).map_err(|e| Error::Invalido(e.to_string()))?;
        }
    }
    let bytes = z.finish().map_err(|e| Error::Invalido(e.to_string()))?.into_inner();
    Ok((format!("tinta-{}.zip", a.slug), bytes))
}

#[derive(Debug, Serialize)]
pub struct Codigo {
    pub lenguaje: String,
    pub codigo: String,
}

#[derive(Debug, Serialize)]
pub struct ParaX {
    /// X Articles tiene su propio campo de título: va aparte, no en el HTML.
    pub titulo: String,
    /// Para el portapapeles como text/html.
    pub html: String,
    /// Para el portapapeles como text/plain.
    pub texto: String,
    /// Imágenes que hay que subir a mano en el editor de X.
    pub imagenes: Vec<String>,
    /// Bloques de código que hay que añadir con el botón de código de X.
    pub codigos: Vec<Codigo>,
    pub avisos: Avisos,
}

/// HTML para pegar en X Articles, adaptado a lo que X conserva al pegar
/// (probado a mano el 2026-10-26). X conserva negrita, cursiva, enlaces,
/// un nivel de subtítulo, listas de un nivel y citas. No recibe:
/// - el título: va en su propio campo;
/// - imágenes ni bloques de código: cada uno deja un aviso en su sitio;
/// - audio: se omite;
/// - subtítulos `###` o más: pasan a un párrafo en negrita;
/// - listas anidadas: sus puntos suben al primer nivel, con «–» delante.
pub fn para_x(c: &Connection, idioma_id: &str) -> Res<ParaX> {
    let e = exportar_idioma(c, idioma_id)?;
    let mut imagenes = Vec::new();
    let mut codigos = Vec::new();
    let mut partes = Vec::new();
    for parte in &e.partes {
        let p = parte.trim();
        if p.starts_with("<audio") {
            continue;
        }
        if let Some(resto) = p.strip_prefix("![") {
            let nombre = resto.rsplit_once("](./").map(|(_, n)| n.trim_end_matches(')').to_string()).unwrap_or_default();
            partes.push(format!("**[Imagen: {nombre}. Súbela aquí a mano]**"));
            imagenes.push(nombre);
            continue;
        }
        if let Some(resto) = p.strip_prefix("```") {
            let (lenguaje, cuerpo) = resto.split_once('\n').unwrap_or((resto, ""));
            let codigo = cuerpo.trim_end().strip_suffix("```").unwrap_or(cuerpo).trim_end_matches('\n').to_string();
            codigos.push(Codigo { lenguaje: lenguaje.trim().to_string(), codigo });
            let n = codigos.len();
            partes.push(format!("**[Bloque de código {n}: añádelo aquí con el botón de código de X]**"));
            continue;
        }
        if let Some(t) = p.strip_prefix("###").map(|t| t.trim_start_matches('#').trim())
            && !p.contains('\n')
        {
            partes.push(format!("**{t}**"));
            continue;
        }
        partes.push(aplanar_listas(p));
    }
    let md = partes.join("\n\n");
    let mut html = String::new();
    let op = pulldown_cmark::Options::ENABLE_STRIKETHROUGH | pulldown_cmark::Options::ENABLE_TABLES;
    pulldown_cmark::html::push_html(&mut html, pulldown_cmark::Parser::new_ext(&md, op));
    Ok(ParaX { titulo: e.titulo, html, texto: md, imagenes, codigos, avisos: e.avisos })
}

/// Sube los puntos anidados al primer nivel de su lista, con «–» delante.
fn aplanar_listas(p: &str) -> String {
    let es_punto = |l: &str| {
        let l = l.trim_start();
        l.starts_with("- ") || l.starts_with("* ") || l.split_once(". ").is_some_and(|(n, _)| !n.is_empty() && n.bytes().all(|b| b.is_ascii_digit()))
    };
    if !p.lines().next().is_some_and(es_punto) {
        return p.to_string();
    }
    let primero = p.lines().next().unwrap();
    let marca = if primero.starts_with("- ") || primero.starts_with("* ") { "-" } else { "1." };
    p.lines()
        .map(|l| {
            if l.starts_with(' ') && es_punto(l) {
                let t = l.trim_start();
                let t = t.split_once(' ').map_or(t, |(_, r)| r);
                format!("{marca} – {t}")
            } else {
                l.to_string()
            }
        })
        .collect::<Vec<_>>()
        .join("\n")
}

#[cfg(test)]
mod pruebas {
    use super::*;
    use crate::db::en_memoria;
    use crate::modelo::*;

    #[test]
    fn markdown_zip_y_x() {
        let mut c = en_memoria();
        let a = crear_articulo(&mut c, "llama-swap: un contenedor").unwrap();
        let iid = a.idiomas[0].resumen.id.clone();
        actualizar_idioma(&mut c, &iid, CambioIdioma { descripcion: Some("Cómo \"dejé\" de tener".into()), ..Default::default() }).unwrap();
        actualizar_articulo(&mut c, &a.id, CambioArticulo { fecha: Some(Some("2026-11-02".into())), ..Default::default() }).unwrap();
        let rama = a.idiomas[0].resumen.rama_principal_id.clone().unwrap();
        let d = documento(&c, &rama).unwrap();
        let v = &d.secciones[0].variantes[0].bloques[0].variantes[0];
        guardar_variante_bloque(&mut c, &v.id, "Antes {--muy muy--} tenía {++mucho++}{>>ia:claude<<} lío.", v.rev, "web").unwrap();
        let img = registrar_medio(&mut c, "aa11", "VRAM en reposo.PNG", "image/png", 3).unwrap();
        let aud = registrar_medio(&mut c, "bb22", "voz.mp3", "audio/mpeg", 3).unwrap();
        let d = crear_seccion(&mut c, &rama, NuevaSeccion { titulo: "La configuración".into(), entre: Entre::default() }).unwrap();
        let vs = d.secciones[1].variantes[0].id.clone();
        let b0 = d.secciones[1].variantes[0].bloques[0].id.clone();
        crear_bloque(&mut c, &vs, NuevoBloque { tipo: "codigo".into(), contenido: "```yaml\nmodels:\n  ttl: 300 {--x--}\n```".into(), entre: Entre::default() }).unwrap();
        crear_bloque(&mut c, &vs, NuevoBloque { tipo: "imagen".into(), contenido: format!("![Gráfica de VRAM]({PREFIJO}{})", img.id), entre: Entre::default() }).unwrap();
        crear_bloque(&mut c, &vs, NuevoBloque { tipo: "audio".into(), contenido: format!("<audio src=\"{PREFIJO}{}\" title=\"Resumen\" controls></audio>", aud.id), entre: Entre::default() }).unwrap();
        crear_bloque(&mut c, &vs, NuevoBloque { tipo: "youtube".into(), contenido: "[Demo](https://youtu.be/jNQXAC9IVRw)".into(), entre: Entre::default() }).unwrap();
        borrar_bloque(&mut c, &b0).unwrap();

        let e = exportar_idioma(&c, &iid).unwrap();
        let esperado = "---\ntitle: \"llama-swap: un contenedor\"\ndescription: \"Cómo \\\"dejé\\\" de tener\"\ndate: 2026-11-02\ndraft: true\n---\n\n\
Antes tenía lío.\n\n## La configuración\n\n```yaml\nmodels:\n  ttl: 300 \n```\n\n![Gráfica de VRAM](./vram-en-reposo.png)\n\n\
<audio src=\"/articulos/llama-swap-un-contenedor/voz.mp3\" title=\"Resumen\" controls></audio>\n\n[Demo](https://youtu.be/jNQXAC9IVRw)\n";
        assert_eq!(e.markdown, esperado);
        assert_eq!((e.avisos.tachados, e.avisos.propuestas, e.avisos.imagenes, e.avisos.audios), (2, 1, 1, 1));

        let dir = tempfile::tempdir().unwrap();
        std::fs::write(dir.path().join("aa11"), b"png").unwrap();
        std::fs::write(dir.path().join("bb22"), b"mp3").unwrap();
        let (nombre, bytes) = zip(&c, &a.id, &[], dir.path()).unwrap();
        assert_eq!(nombre, "tinta-llama-swap-un-contenedor.zip");
        let mut z = zip::ZipArchive::new(std::io::Cursor::new(bytes)).unwrap();
        let mut rutas: Vec<_> = (0..z.len()).map(|i| z.by_index(i).unwrap().name().unwrap().to_string()).collect();
        rutas.sort();
        assert_eq!(
            rutas,
            [
                "public/articulos/llama-swap-un-contenedor/voz.mp3",
                "src/content/articles/llama-swap-un-contenedor/es.md",
                "src/content/articles/llama-swap-un-contenedor/vram-en-reposo.png",
            ]
        );

        let x = para_x(&c, &iid).unwrap();
        assert_eq!(x.titulo, "llama-swap: un contenedor");
        assert!(!x.html.contains("<h1>"));
        assert!(x.html.contains("Bloque de código 1"));
        assert_eq!(x.codigos[0].lenguaje, "yaml");
        assert_eq!(x.codigos[0].codigo, "models:\n  ttl: 300 ");
        assert!(x.html.contains("<h2>La configuración</h2>"));
        assert!(x.html.contains("Imagen: vram-en-reposo.png"));
        assert!(!x.html.contains("audio"));
        assert_eq!(x.imagenes, ["vram-en-reposo.png"]);
    }

    #[test]
    fn x_aplana_listas_y_subtitulos() {
        assert_eq!(aplanar_listas("- uno\n- dos\n  - anidado"), "- uno\n- dos\n- – anidado");
        assert_eq!(aplanar_listas("1. uno\n   - sub"), "1. uno\n1. – sub");
        assert_eq!(aplanar_listas("Un párrafo\n  - no es lista"), "Un párrafo\n  - no es lista");

        let mut c = en_memoria();
        let a = crear_articulo(&mut c, "T").unwrap();
        let iid = a.idiomas[0].resumen.id.clone();
        let d = documento(&c, a.idiomas[0].resumen.rama_principal_id.as_ref().unwrap()).unwrap();
        let vs = d.secciones[0].variantes[0].id.clone();
        crear_bloque(&mut c, &vs, NuevoBloque { tipo: "subtitulo".into(), contenido: "### Detalle".into(), entre: Entre::default() }).unwrap();
        // Un bloque de código con una línea en blanco dentro no se parte.
        crear_bloque(&mut c, &vs, NuevoBloque { tipo: "codigo".into(), contenido: "```\na\n\nb\n```".into(), entre: Entre::default() }).unwrap();
        let x = para_x(&c, &iid).unwrap();
        assert!(x.html.contains("<p><strong>Detalle</strong></p>"), "{}", x.html);
        assert_eq!(x.codigos.len(), 1);
        assert_eq!(x.codigos[0].codigo, "a\n\nb");
        assert!(!x.html.contains("<pre>"));
    }
}
