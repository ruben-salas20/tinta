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
/// Devuelve `None` si no existe esa marca.
pub fn resolver(s: &str, indice: usize, decision: Decision) -> Option<String> {
    let mut trozos = leer(s);
    let pos = trozos
        .iter()
        .enumerate()
        .filter(|(_, t)| !matches!(t, Trozo::Texto(_) | Trozo::Resaltado(_)))
        .nth(indice)?
        .0;
    let acepta = decision == Decision::Aceptar;
    let nuevo = match &trozos[pos] {
        Trozo::Borrado { texto, .. } => if acepta { String::new() } else { texto.clone() },
        Trozo::Anadido { texto, .. } => if acepta { texto.clone() } else { String::new() },
        Trozo::Cambio { antes, despues, .. } => if acepta { despues.clone() } else { antes.clone() },
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
    Some(escribir(&trozos))
}

#[cfg(test)]
mod pruebas {
    use super::*;

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
    fn llaves_sueltas_son_texto() {
        assert_eq!(leer("a {-- b"), vec![Trozo::Texto("a {-- b".into())]);
        assert_eq!(limpio("código {x}"), "código {x}");
    }
}
