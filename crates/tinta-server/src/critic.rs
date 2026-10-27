//! Marcas de revisión en CriticMarkup, dentro del texto de cada bloque.
//!
//! | Marca                  | Quién                 | Al exportar      |
//! |------------------------|-----------------------|------------------|
//! | `{--x--}`              | Rubén: tachado        | desaparece       |
//! | `{--x--}{>>ia:N<<}`    | agente: propone quitar| se queda `x`     |
//! | `{++x++}`              | propuesta de añadir   | desaparece       |
//! | `{~~a~>b~~}`           | propuesta de cambio   | se queda `a`     |
//! | `{>>nota<<}`           | nota al margen        | desaparece       |
//! | `{==x==}`              | ancla de una nota     | se queda `x`     |
//!
//! Un `{>>ia:nombre<<}` pegado justo detrás de una marca dice que la hizo un
//! agente. Una nota del agente es `{>>ia:nombre: texto<<}`. La distinción
//! importa en el tachado: el de Rubén ya está decidido, el del agente no.

use crate::autoria::Rango;
use serde::Serialize;

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Trozo {
    Texto(String),
    Borrado { texto: String, ia: Option<String> },
    Anadido { texto: String, ia: Option<String> },
    Cambio { antes: String, despues: String, ia: Option<String> },
    Nota { texto: String, ia: Option<String> },
    Resaltado(String),
}

const MARCAS: [(&str, &str); 5] = [("{--", "--}"), ("{++", "++}"), ("{~~", "~~}"), ("{>>", "<<}"), ("{==", "==}")];

pub fn leer(s: &str) -> Vec<Trozo> {
    let mut trozos = Vec::new();
    let mut texto = String::new();
    let mut resto = s;
    'fuera: while !resto.is_empty() {
        for (abre, cierra) in MARCAS {
            if let Some(tras) = resto.strip_prefix(abre)
                && let Some(fin) = tras.find(cierra)
            {
                let dentro = &tras[..fin];
                resto = &tras[fin + cierra.len()..];
                if !texto.is_empty() {
                    trozos.push(Trozo::Texto(std::mem::take(&mut texto)));
                }
                let mut ia = None;
                if abre != "{>>" && abre != "{==" {
                    if let Some(nombre) = firma(resto) {
                        resto = &resto[nombre.1..];
                        ia = Some(nombre.0);
                    }
                }
                trozos.push(match abre {
                    "{--" => Trozo::Borrado { texto: dentro.into(), ia },
                    "{++" => Trozo::Anadido { texto: dentro.into(), ia },
                    "{~~" => match dentro.split_once("~>") {
                        Some((a, b)) => Trozo::Cambio { antes: a.into(), despues: b.into(), ia },
                        // Sin «~>» no es un cambio válido: se trata como tachado.
                        None => Trozo::Borrado { texto: dentro.into(), ia },
                    },
                    "{>>" => match dentro.strip_prefix("ia:").and_then(|r| r.split_once(": ")) {
                        Some((n, t)) => Trozo::Nota { texto: t.into(), ia: Some(n.into()) },
                        None => Trozo::Nota { texto: dentro.into(), ia: None },
                    },
                    _ => Trozo::Resaltado(dentro.into()),
                });
                continue 'fuera;
            }
        }
        let c = resto.chars().next().unwrap();
        texto.push(c);
        resto = &resto[c.len_utf8()..];
    }
    if !texto.is_empty() {
        trozos.push(Trozo::Texto(texto));
    }
    trozos
}

/// `{>>ia:nombre<<}` al principio de `s`: devuelve el nombre y cuánto ocupa.
fn firma(s: &str) -> Option<(String, usize)> {
    let tras = s.strip_prefix("{>>ia:")?;
    let fin = tras.find("<<}")?;
    let nombre = &tras[..fin];
    if nombre.is_empty() || nombre.contains(": ") {
        return None;
    }
    Some((nombre.into(), "{>>ia:".len() + fin + 3))
}

pub fn escribir(trozos: &[Trozo]) -> String {
    let firma = |ia: &Option<String>| ia.as_ref().map(|n| format!("{{>>ia:{n}<<}}")).unwrap_or_default();
    trozos
        .iter()
        .map(|t| match t {
            Trozo::Texto(s) => s.clone(),
            Trozo::Borrado { texto, ia } => format!("{{--{texto}--}}{}", firma(ia)),
            Trozo::Anadido { texto, ia } => format!("{{++{texto}++}}{}", firma(ia)),
            Trozo::Cambio { antes, despues, ia } => format!("{{~~{antes}~>{despues}~~}}{}", firma(ia)),
            Trozo::Nota { texto, ia: Some(n) } => format!("{{>>ia:{n}: {texto}<<}}"),
            Trozo::Nota { texto, ia: None } => format!("{{>>{texto}<<}}"),
            Trozo::Resaltado(s) => format!("{{=={s}==}}"),
        })
        .collect()
}

/// El texto que se publica: lo decidido y nada de lo pendiente.
pub fn limpio(s: &str) -> String {
    leer(s)
        .into_iter()
        .map(|t| match t {
            Trozo::Texto(s) | Trozo::Resaltado(s) => s,
            Trozo::Borrado { texto, ia: Some(_) } => texto,
            Trozo::Borrado { ia: None, .. } | Trozo::Anadido { .. } | Trozo::Nota { .. } => String::new(),
            Trozo::Cambio { antes, .. } => antes,
        })
        .collect()
}

/// Como [`limpio`], y además junta los espacios dobles que deja quitar un
/// trozo en mitad de una frase. Respeta la sangría del principio de cada
/// línea (las listas anidadas la usan). Solo para prosa: en código no.
pub fn limpio_prosa(s: &str) -> String {
    let lineas: Vec<String> = limpio(s)
        .lines()
        .map(|l| {
            let cuerpo = l.trim_start();
            let sangria = &l[..l.len() - cuerpo.len()];
            let mut c = cuerpo.trim_end().to_string();
            while c.contains("  ") {
                c = c.replace("  ", " ");
            }
            format!("{sangria}{c}")
        })
        .collect();
    lineas.join("\n").trim_matches('\n').to_string()
}

#[derive(Debug, Default, Clone, Copy, PartialEq, Eq, Serialize)]
pub struct Recuento {
    pub tachados: u32,
    pub propuestas: u32,
    pub notas: u32,
}

pub fn contar(s: &str) -> Recuento {
    let mut r = Recuento::default();
    for t in leer(s) {
        match t {
            Trozo::Borrado { ia: None, .. } => r.tachados += 1,
            Trozo::Borrado { .. } | Trozo::Anadido { .. } | Trozo::Cambio { .. } => r.propuestas += 1,
            Trozo::Nota { .. } => r.notas += 1,
            Trozo::Texto(_) | Trozo::Resaltado(_) => {}
        }
    }
    r
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, serde::Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum Decision {
    Aceptar,
    Rechazar,
}

/// Resuelve la marca número `indice` (contando solo marcas, desde 0).
///
/// - Tachado de Rubén: aceptar lo borra del todo, rechazar lo restaura.
/// - Propuesta del agente: aceptar la aplica, rechazar la deshace.
/// - Nota: las dos opciones la quitan, junto con su ancla `{==x==}`.
///
/// Devuelve el texto resultante y, si aceptar una propuesta del agente dejó
/// texto, el rango que ocupa en el resultado (en caracteres) y el agente.
/// Aceptar un borrado no deja texto, así que no da rango.
/// Devuelve `None` si no existe esa marca.
pub fn resolver_con_origen(s: &str, indice: usize, decision: Decision) -> Option<(String, Option<Rango>)> {
    let mut trozos = leer(s);
    let pos = trozos
        .iter()
        .enumerate()
        .filter(|(_, t)| !matches!(t, Trozo::Texto(_) | Trozo::Resaltado(_)))
        .nth(indice)?
        .0;
    let acepta = decision == Decision::Aceptar;
    let mut origen = None;
    let nuevo = match &trozos[pos] {
        Trozo::Borrado { texto, .. } => if acepta { String::new() } else { texto.clone() },
        Trozo::Anadido { texto, ia } => {
            if acepta {
                origen = rango_de_agente(&trozos[..pos], texto, ia);
            }
            if acepta { texto.clone() } else { String::new() }
        }
        Trozo::Cambio { antes, despues, ia } => {
            if acepta {
                origen = rango_de_agente(&trozos[..pos], despues, ia);
            }
            if acepta { despues.clone() } else { antes.clone() }
        }
        Trozo::Nota { .. } => {
            if pos > 0
                && let Trozo::Resaltado(x) = &trozos[pos - 1]
            {
                trozos[pos - 1] = Trozo::Texto(x.clone());
            }
            String::new()
        }
        Trozo::Texto(_) | Trozo::Resaltado(_) => unreachable!(),
    };
    trozos[pos] = Trozo::Texto(nuevo);
    Some((escribir(&trozos), origen))
}

/// El rango de `nuevo` si lo propuso un agente. `antes` son los trozos que
/// quedan por delante, para contar dónde empieza.
fn rango_de_agente(antes: &[Trozo], nuevo: &str, ia: &Option<String>) -> Option<Rango> {
    let agente = ia.as_ref()?;
    if nuevo.is_empty() {
        return None;
    }
    let desde = escribir(antes).chars().count();
    Some(Rango { desde, hasta: desde + nuevo.chars().count(), agente: agente.clone() })
}

/// Qué propone un agente sobre un trozo de texto que ya existe.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Propuesta {
    Cambiar(String),
    Quitar,
    /// Añadir este texto justo después.
    Anadir(String),
}

/// Por qué no se puede marcar un trozo de texto.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Fallo {
    /// No está en el texto sin marcas.
    NoEsta,
    /// Corta un enlace o cae dentro de su `](url)`.
    Enlace,
    /// Corta un código en línea o cae dentro de él.
    Codigo,
}

/// Un trozo de Markdown que una marca no puede cortar.
struct Zona {
    desde: usize,
    hasta: usize,
    /// Si la marca puede abarcarla entera.
    abarcable: bool,
    /// El texto visible de un enlace, que sí se puede marcar por dentro.
    visible: Option<(usize, usize)>,
    fallo: Fallo,
}

fn zonas(x: &str) -> Vec<Zona> {
    let mut zonas = Vec::new();
    let b = x.as_bytes();
    let mut i = 0;
    while i < b.len() {
        if b[i] != b'`' {
            i += 1;
            continue;
        }
        let n = b[i..].iter().take_while(|&&c| c == b'`').count();
        let mut j = i + n;
        let mut cierre = None;
        while j < b.len() {
            let m = b[j..].iter().take_while(|&&c| c == b'`').count();
            if m == n {
                cierre = Some(j + m);
                break;
            }
            j += m.max(1);
        }
        match cierre {
            Some(fin) => {
                zonas.push(Zona { desde: i, hasta: fin, abarcable: true, visible: None, fallo: Fallo::Codigo });
                i = fin;
            }
            None => i += n,
        }
    }
    for (cierre, _) in x.match_indices("](") {
        let Some(fin) = x[cierre + 2..].find(')').map(|f| cierre + 2 + f + 1) else { continue };
        // Si el «[» queda en otro trozo, el enlace no se puede abarcar desde aquí.
        let zona = match x[..cierre].rfind('[') {
            Some(abre) => {
                let desde = if x[..abre].ends_with('!') { abre - 1 } else { abre };
                Zona { desde, hasta: fin, abarcable: true, visible: Some((abre + 1, cierre)), fallo: Fallo::Enlace }
            }
            None => Zona { desde: cierre, hasta: fin, abarcable: false, visible: None, fallo: Fallo::Enlace },
        };
        zonas.push(zona);
    }
    zonas
}

/// Comprueba que marcar `x[i..j]` no rompe el Markdown.
fn se_puede_marcar(x: &str, i: usize, j: usize) -> Result<(), Fallo> {
    for z in zonas(x) {
        let fuera = j <= z.desde || i >= z.hasta;
        let entera = z.abarcable && i <= z.desde && j >= z.hasta;
        let visible = z.visible.is_some_and(|(a, b)| i >= a && j <= b);
        if !(fuera || entera || visible) {
            return Err(z.fallo);
        }
    }
    Ok(())
}

/// Marca la primera aparición de `buscar` como propuesta del agente `ia`.
/// Solo busca en el texto sin marcas: no se propone sobre otra propuesta.
pub fn proponer(s: &str, buscar: &str, p: Propuesta, ia: &str) -> Result<String, Fallo> {
    if buscar.is_empty() {
        return Err(Fallo::NoEsta);
    }
    let mut trozos = leer(s);
    let pos = trozos.iter().position(|t| matches!(t, Trozo::Texto(x) if x.contains(buscar))).ok_or(Fallo::NoEsta)?;
    let Trozo::Texto(x) = trozos[pos].clone() else { unreachable!() };
    let i = x.find(buscar).unwrap();
    se_puede_marcar(&x, i, i + buscar.len())?;
    let (antes, despues) = (&x[..i], &x[i + buscar.len()..]);
    let ia = Some(ia.to_string());
    let mut nuevos = vec![Trozo::Texto(antes.into())];
    match p {
        Propuesta::Cambiar(nuevo) => nuevos.push(Trozo::Cambio { antes: buscar.into(), despues: nuevo, ia }),
        Propuesta::Quitar => nuevos.push(Trozo::Borrado { texto: buscar.into(), ia }),
        Propuesta::Anadir(nuevo) => {
            nuevos.push(Trozo::Texto(buscar.into()));
            nuevos.push(Trozo::Anadido { texto: nuevo, ia });
        }
    }
    nuevos.push(Trozo::Texto(despues.into()));
    trozos.splice(pos..=pos, nuevos.into_iter().filter(|t| !matches!(t, Trozo::Texto(x) if x.is_empty())));
    Ok(escribir(&trozos))
}

/// Nota del agente anclada a `ancla`, o al final del texto si no se da.
pub fn anotar(s: &str, nota: &str, ancla: Option<&str>, ia: &str) -> Result<String, Fallo> {
    let marca = format!("{{>>ia:{ia}: {}<<}}", nota.replace("<<}", "<< }"));
    let Some(ancla) = ancla.filter(|a| !a.is_empty()) else {
        return Ok(format!("{}{}{marca}", s.trim_end(), if s.trim().is_empty() { "" } else { " " }));
    };
    let mut trozos = leer(s);
    let pos = trozos.iter().position(|t| matches!(t, Trozo::Texto(x) if x.contains(ancla))).ok_or(Fallo::NoEsta)?;
    let Trozo::Texto(x) = trozos[pos].clone() else { unreachable!() };
    let i = x.find(ancla).unwrap();
    se_puede_marcar(&x, i, i + ancla.len())?;
    let nuevos = [
        Trozo::Texto(x[..i].into()),
        Trozo::Resaltado(ancla.into()),
        Trozo::Nota { texto: nota.into(), ia: Some(ia.into()) },
        Trozo::Texto(x[i + ancla.len()..].into()),
    ];
    trozos.splice(pos..=pos, nuevos.into_iter().filter(|t| !matches!(t, Trozo::Texto(x) if x.is_empty())));
    Ok(escribir(&trozos))
}

#[cfg(test)]
mod pruebas {
    use super::*;

    fn resolver(s: &str, indice: usize, decision: Decision) -> Option<String> {
        resolver_con_origen(s, indice, decision).map(|(texto, _)| texto)
    }

    const EJEMPLO: &str = "llama-swap {--es muy sencillo y--} arranca {~~El ttl dice~>El campo ttl marca~~}{>>ia:claude<<} y {++apaga++}{>>ia:claude<<} el {==anterior==}{>>poner tiempos<<}. {--Fin--}{>>ia:claude<<}";

    #[test]
    fn leer_y_escribir_es_identidad() {
        assert_eq!(escribir(&leer(EJEMPLO)), EJEMPLO);
        let t = leer(EJEMPLO);
        assert!(matches!(&t[1], Trozo::Borrado { ia: None, .. }));
        assert!(matches!(&t[3], Trozo::Cambio { ia: Some(n), .. } if n == "claude"));
    }

    #[test]
    fn limpio_quita_lo_pendiente_y_lo_tachado() {
        assert_eq!(limpio(EJEMPLO), "llama-swap  arranca El ttl dice y  el anterior. Fin");
        assert_eq!(limpio_prosa(EJEMPLO), "llama-swap arranca El ttl dice y el anterior. Fin");
        assert_eq!(limpio("    ttl: 300 {--x--}"), "    ttl: 300 ");
        assert_eq!(limpio_prosa("- uno {--x--} dos\n  - anidado"), "- uno dos\n  - anidado");
    }

    #[test]
    fn contar_marcas() {
        assert_eq!(contar(EJEMPLO), Recuento { tachados: 1, propuestas: 3, notas: 1 });
    }

    #[test]
    fn resolver_cada_tipo() {
        // 0 tachado, 1 cambio, 2 añadido, 3 nota, 4 borrado del agente
        let r = resolver(EJEMPLO, 1, Decision::Aceptar).unwrap();
        assert!(r.contains("arranca El campo ttl marca y"));
        let r = resolver(EJEMPLO, 0, Decision::Rechazar).unwrap();
        assert!(r.starts_with("llama-swap es muy sencillo y arranca"), "{r}");
        let r = resolver(EJEMPLO, 3, Decision::Aceptar).unwrap();
        assert!(r.contains("el anterior."), "{r}");
        assert!(!r.contains("{=="));
        let r = resolver(EJEMPLO, 4, Decision::Aceptar).unwrap();
        assert!(r.ends_with("anterior==}{>>poner tiempos<<}. "), "{r}");
        assert!(resolver(EJEMPLO, 9, Decision::Aceptar).is_none());
    }

    #[test]
    fn aceptar_propuesta_da_su_rango_en_caracteres() {
        // Tildes y emoji: «ñandú 🙂» son 7 caracteres pero más bytes.
        let s = "Qué 🚀 {++ñandú 🙂++}{>>ia:claude<<} fin";
        let (texto, origen) = resolver_con_origen(s, 0, Decision::Aceptar).unwrap();
        assert_eq!(texto, "Qué 🚀 ñandú 🙂 fin");
        assert_eq!(origen, Some(Rango { desde: 6, hasta: 13, agente: "claude".into() }));

        let (texto, origen) = resolver_con_origen("Hola {~~mundo~>mar~~}{>>ia:claude<<} y", 0, Decision::Aceptar).unwrap();
        assert_eq!(texto, "Hola mar y");
        assert_eq!(origen, Some(Rango { desde: 5, hasta: 8, agente: "claude".into() }));
    }

    #[test]
    fn solo_aceptar_propuesta_del_agente_da_rango() {
        // Un borrado del agente no deja texto. Rechazar no deja nada, y el añadido de Rubén no es de ningún agente.
        assert_eq!(resolver_con_origen("Hola {--x--}{>>ia:claude<<}", 0, Decision::Aceptar).unwrap(), ("Hola ".to_string(), None));
        assert_eq!(resolver_con_origen("Hola {++x++}{>>ia:claude<<}", 0, Decision::Rechazar).unwrap(), ("Hola ".to_string(), None));
        assert_eq!(resolver_con_origen("Hola {++x++}", 0, Decision::Aceptar).unwrap(), ("Hola x".to_string(), None));
    }

    #[test]
    fn llaves_sueltas_son_texto() {
        assert_eq!(leer("a {-- b"), vec![Trozo::Texto("a {-- b".into())]);
        assert_eq!(limpio("código {x}"), "código {x}");
    }

    #[test]
    fn proponer_y_anotar() {
        let base = "El ttl dice cuánto {--muy--} dura.";
        assert_eq!(
            proponer(base, "El ttl dice", Propuesta::Cambiar("El campo ttl marca".into()), "claude").unwrap(),
            "{~~El ttl dice~>El campo ttl marca~~}{>>ia:claude<<} cuánto {--muy--} dura."
        );
        assert_eq!(proponer(base, " dura", Propuesta::Quitar, "c").unwrap(), "El ttl dice cuánto {--muy--}{-- dura--}{>>ia:c<<}.");
        assert_eq!(proponer(base, "cuánto", Propuesta::Anadir(" tiempo".into()), "c").unwrap(), "El ttl dice cuánto{++ tiempo++}{>>ia:c<<} {--muy--} dura.");
        // No propone sobre lo que ya está marcado, ni sobre lo que no existe.
        assert_eq!(proponer(base, "muy", Propuesta::Quitar, "c"), Err(Fallo::NoEsta));
        assert_eq!(proponer(base, "nada", Propuesta::Quitar, "c"), Err(Fallo::NoEsta));
        let r = proponer(base, "ttl", Propuesta::Quitar, "c").unwrap();
        assert_eq!(limpio_prosa(&r), "El ttl dice cuánto dura.");

        assert_eq!(anotar("Hola mundo", "¿Seguro?", Some("mundo"), "claude").unwrap(), "Hola {==mundo==}{>>ia:claude: ¿Seguro?<<}");
        assert_eq!(anotar("Hola", "fin", None, "c").unwrap(), "Hola {>>ia:c: fin<<}");
        let t = leer(&anotar("Hola mundo", "x", Some("mundo"), "c").unwrap());
        assert!(matches!(&t[2], Trozo::Nota { ia: Some(n), .. } if n == "c"));
        assert_eq!(anotar("Hola", "x", Some("adiós"), "c"), Err(Fallo::NoEsta));
    }

    #[test]
    fn no_marca_dentro_de_un_enlace_ni_de_codigo() {
        let base = "Lee [la guía](https://ejemplo.dev/guia) y usa `ttl: 300` ya.";
        let quitar = |b: &str| proponer(base, b, Propuesta::Quitar, "c");
        assert_eq!(quitar("ejemplo"), Err(Fallo::Enlace));
        assert_eq!(quitar("guía]("), Err(Fallo::Enlace));
        assert_eq!(quitar("Lee [la"), Err(Fallo::Enlace));
        assert_eq!(quitar("guia) y"), Err(Fallo::Enlace));
        assert_eq!(quitar("300"), Err(Fallo::Codigo));
        assert_eq!(quitar("usa `ttl"), Err(Fallo::Codigo));
        assert_eq!(anotar(base, "x", Some("ejemplo.dev"), "c"), Err(Fallo::Enlace));
        assert_eq!(anotar(base, "x", Some("ttl"), "c"), Err(Fallo::Codigo));
        // El texto visible, el enlace entero y el código entero sí valen.
        assert!(quitar("la guía").is_ok());
        assert!(quitar("guí").is_ok());
        assert!(quitar("[la guía](https://ejemplo.dev/guia)").is_ok());
        assert!(quitar("`ttl: 300`").is_ok());
        assert!(anotar(base, "x", Some("la guía"), "c").is_ok());
        assert_eq!(proponer("![logo](a.png) fin", "[logo](a.png)", Propuesta::Quitar, "c"), Err(Fallo::Enlace));
        assert!(proponer("![logo](a.png) fin", "![logo](a.png)", Propuesta::Quitar, "c").is_ok());
        // Un «](» que no cierra no es un enlace.
        assert!(proponer("a ](b sin cierre", "(b", Propuesta::Quitar, "c").is_ok());
    }
}
