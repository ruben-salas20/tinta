//! Qué texto de una variante viene de una propuesta del agente ya aceptada.
//!
//! Un rango es una parte del `contenido` crudo de la variante, con sus marcas.
//! `desde` y `hasta` cuentan caracteres Unicode (code points), no bytes, para
//! que la web y la app los usen igual. Va `desde` incluido y `hasta` excluido.
//!
//! Los rangos se guardan aparte, en la tabla `autoria_ia`: el Markdown no cambia.

use serde::Serialize;

#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub struct Rango {
    pub desde: usize,
    pub hasta: usize,
    pub agente: String,
}

/// Los rangos después de cambiar `viejo` por `nuevo`.
///
/// Se busca el prefijo y el sufijo comunes. Lo que está antes del cambio se
/// queda, lo que está después se desplaza, y lo que toca la zona cambiada se
/// recorta a los trozos que quedan intactos. Un rango que queda vacío se borra.
pub fn desplazar(rangos: &[Rango], viejo: &str, nuevo: &str) -> Vec<Rango> {
    let a: Vec<char> = viejo.chars().collect();
    let b: Vec<char> = nuevo.chars().collect();
    let prefijo = a.iter().zip(&b).take_while(|(x, y)| x == y).count();
    let maximo = a.len().min(b.len()) - prefijo;
    let sufijo = a.iter().rev().zip(b.iter().rev()).take(maximo).take_while(|(x, y)| x == y).count();
    let fin_viejo = a.len() - sufijo;
    let delta = b.len() as isize - a.len() as isize;
    let desplaza = |pos: usize| (pos as isize + delta) as usize;

    let mut r = Vec::new();
    for g in rangos {
        // Trozo anterior al cambio.
        let hasta_antes = g.hasta.min(prefijo);
        if g.desde < hasta_antes {
            r.push(Rango { desde: g.desde, hasta: hasta_antes, agente: g.agente.clone() });
        }
        // Trozo posterior al cambio, ya desplazado.
        let desde_despues = g.desde.max(fin_viejo);
        if desde_despues < g.hasta {
            r.push(Rango { desde: desplaza(desde_despues), hasta: desplaza(g.hasta), agente: g.agente.clone() });
        }
    }
    r
}

#[cfg(test)]
mod pruebas {
    use super::*;

    fn r(desde: usize, hasta: usize) -> Rango {
        Rango { desde, hasta, agente: "claude".into() }
    }

    #[test]
    fn sin_cambios_no_mueve_nada() {
        let base = "Hola mundo";
        assert_eq!(desplazar(&[r(5, 10)], base, base), vec![r(5, 10)]);
    }

    #[test]
    fn cambio_antes_desplaza() {
        // «mundo» pasa de la posición 5 a la 12 al meter «grande ».
        assert_eq!(desplazar(&[r(5, 10)], "hola mundo", "hola grande mundo"), vec![r(12, 17)]);
        assert_eq!(desplazar(&[r(5, 10)], "hola mundo", "AB hola mundo"), vec![r(8, 13)]);
    }

    #[test]
    fn cambio_despues_no_mueve() {
        assert_eq!(desplazar(&[r(0, 4)], "hola mundo", "hola mundo!!"), vec![r(0, 4)]);
        assert_eq!(desplazar(&[r(0, 4)], "hola mundo", "hola"), vec![r(0, 4)]);
    }

    #[test]
    fn cambio_encima_borra_el_rango_entero() {
        assert!(desplazar(&[r(5, 10)], "hola mundo", "hola xyz").is_empty());
    }

    #[test]
    fn cambio_en_parte_recorta_a_lo_intacto() {
        // Cambia «ndo» por «elle»: «mu» sigue intacto.
        assert_eq!(desplazar(&[r(5, 10)], "hola mundo", "hola muelle"), vec![r(5, 7)]);
        // Cambia «hola» por «adiós»: «mundo» sigue intacto y se desplaza.
        assert_eq!(desplazar(&[r(5, 10)], "hola mundo", "adiós mundo"), vec![r(6, 11)]);
    }

    #[test]
    fn insertar_dentro_parte_el_rango() {
        // Entre «ab» y «cd» se mete «X»: el rango 0..4 se parte en dos.
        assert_eq!(desplazar(&[r(0, 4)], "abcd", "abXcd"), vec![r(0, 2), r(3, 5)]);
    }

    #[test]
    fn insertar_justo_al_borde_no_lo_toca() {
        // Al principio del rango, «X» queda fuera; al final, también.
        assert_eq!(desplazar(&[r(2, 4)], "abcd", "abXcd"), vec![r(3, 5)]);
        assert_eq!(desplazar(&[r(0, 2)], "abcd", "abXcd"), vec![r(0, 2)]);
    }

    #[test]
    fn borrar_el_trozo_entero_quita_el_rango() {
        assert!(desplazar(&[r(2, 4)], "abcd", "ab").is_empty());
    }

    #[test]
    fn cuenta_caracteres_no_bytes() {
        // «ñ», «é» y el emoji ocupan varios bytes y cuentan uno cada uno.
        let viejo = "ñé🙂 mundo";
        let nuevo = "ñé🙂 grande mundo";
        // «mundo» empieza en el carácter 4 y acaba en el 9.
        assert_eq!(desplazar(&[r(4, 9)], viejo, nuevo), vec![r(11, 16)]);
    }

    #[test]
    fn varios_rangos_a_la_vez() {
        // «aa bbb cc»: «aa», «bb» (4..6) y «cc». Se mete «--» en el carácter 5.
        let rangos = [r(0, 2), r(4, 6), r(7, 9)];
        let nuevo = desplazar(&rangos, "aa bbb cc", "aa bb--b cc");
        assert_eq!(nuevo, vec![r(0, 2), r(4, 5), r(7, 8), r(9, 11)]);
    }
}
