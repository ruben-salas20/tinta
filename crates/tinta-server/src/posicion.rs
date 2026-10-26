//! Posiciones fraccionarias: claves de texto que ordenan secciones y bloques.
//!
//! Entre dos claves siempre cabe otra, así que insertar o mover no obliga a
//! renumerar a los vecinos. Dos dispositivos sin conexión pueden reordenar a
//! la vez sin pisarse: cada uno solo cambia la clave de lo que movió.
//!
//! Las claves usan los dígitos de base 62 en orden ASCII (0-9, A-Z, a-z), así
//! que se comparan como texto normal, también en SQLite. Ninguna termina en
//! «0»: con ese cero sobrante dejaría de haber hueco por debajo.

const DIGITOS: &[u8] = b"0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
const BASE: u8 = 62;

fn valor(c: u8) -> u8 {
    DIGITOS.iter().position(|&d| d == c).expect("dígito de posición válido") as u8
}

/// Devuelve una clave estrictamente entre `antes` y `despues`.
/// `None` en `antes` es el principio de la lista; en `despues`, el final.
pub fn entre(antes: Option<&str>, despues: Option<&str>) -> String {
    let a: Vec<u8> = antes.unwrap_or("").bytes().map(valor).collect();
    let b: Option<Vec<u8>> = despues.map(|s| s.bytes().map(valor).collect());
    if let Some(b) = &b {
        assert!(a < *b, "posiciones desordenadas: {antes:?} >= {despues:?}");
    }
    medio(&a, b.as_deref()).into_iter().map(|v| DIGITOS[v as usize] as char).collect()
}

fn medio(a: &[u8], b: Option<&[u8]>) -> Vec<u8> {
    if let Some(b) = b {
        let comun = (0..b.len()).take_while(|&i| a.get(i).copied().unwrap_or(0) == b[i]).count();
        if comun > 0 {
            let mut r = b[..comun].to_vec();
            r.extend(medio(a.get(comun..).unwrap_or(&[]), Some(&b[comun..])));
            return r;
        }
    }
    let da = a.first().copied().unwrap_or(0);
    let db = b.map_or(BASE, |b| b[0]);
    if db - da > 1 {
        return vec![(da + db) / 2];
    }
    if let Some(b) = b
        && b.len() > 1
    {
        return vec![b[0]];
    }
    let mut r = vec![da];
    r.extend(medio(a.get(1..).unwrap_or(&[]), None));
    r
}

/// `n` claves seguidas al final de una lista vacía, para copiar listas enteras.
pub fn serie(n: usize) -> Vec<String> {
    let mut r: Vec<String> = Vec::with_capacity(n);
    for _ in 0..n {
        let k = entre(r.last().map(String::as_str), None);
        r.push(k);
    }
    r
}

#[cfg(test)]
mod pruebas {
    use super::*;

    #[test]
    fn lista_vacia_y_extremos() {
        let a = entre(None, None);
        let b = entre(Some(&a), None);
        let c = entre(None, Some(&a));
        assert!(c < a && a < b);
    }

    #[test]
    fn siempre_cabe_otra_y_nunca_acaba_en_cero() {
        // Insertar mil veces en el mismo hueco es el peor caso.
        let mut lo = entre(None, None);
        let hi = entre(Some(&lo), None);
        for _ in 0..1000 {
            let m = entre(Some(&lo), Some(&hi));
            assert!(lo < m && m < hi, "{lo} < {m} < {hi}");
            assert!(!m.ends_with('0'));
            lo = m;
        }
        let mut hi2 = hi.clone();
        for _ in 0..1000 {
            let m = entre(None, Some(&hi2));
            assert!(m < hi2 && !m.ends_with('0'));
            hi2 = m;
        }
    }

    #[test]
    fn serie_ordenada() {
        let s = serie(200);
        assert!(s.windows(2).all(|w| w[0] < w[1]));
    }
}
