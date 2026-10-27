// Rangos de autoría (crates/tinta-server/src/autoria.rs) sobre el documento de TipTap.
//
// Los rangos cuentan caracteres del Markdown crudo del bloque. El documento de
// TipTap no guarda esas posiciones: el Markdown se convierte en nodos y se
// quitan sus marcas. Aquí cada carácter del documento se busca, en orden, en el
// crudo. Es exacto en texto normal. Cerca de sintaxis (negrita, enlaces,
// escapes) puede equivocarse con un carácter.

import type { Node as PMNode } from '@tiptap/pm/model';
import { atomoAMd, type Nodo } from './markdown';

export interface Rango {
  desde: number;
  hasta: number;
  agente: string;
}

/** Una pieza del documento, en orden: un carácter de texto, un salto de línea o un átomo (nota o cambio). */
export type Pieza =
  | { t: 'char'; ch: string; pos: number }
  | { t: 'salto'; pos: number }
  | { t: 'atomo'; md: string; pos: number };

/** Parte del documento que pinta la marca. Posiciones de TipTap (UTF-16). */
export interface Tramo {
  from: number;
  to: number;
  agente: string;
}

/** Los rangos después de cambiar `viejo` por `nuevo`. Igual que `desplazar` en autoria.rs. */
export function desplazar(rangos: Rango[], viejo: string, nuevo: string): Rango[] {
  const a = Array.from(viejo);
  const b = Array.from(nuevo);
  let prefijo = 0;
  while (prefijo < a.length && prefijo < b.length && a[prefijo] === b[prefijo]) prefijo++;
  const maximo = Math.min(a.length, b.length) - prefijo;
  let sufijo = 0;
  while (sufijo < maximo && a[a.length - 1 - sufijo] === b[b.length - 1 - sufijo]) sufijo++;
  const finViejo = a.length - sufijo;
  const delta = b.length - a.length;

  const r: Rango[] = [];
  for (const g of rangos) {
    const hastaAntes = Math.min(g.hasta, prefijo);
    if (g.desde < hastaAntes) r.push({ desde: g.desde, hasta: hastaAntes, agente: g.agente });
    const desdeDespues = Math.max(g.desde, finViejo);
    if (desdeDespues < g.hasta) r.push({ desde: desdeDespues + delta, hasta: g.hasta + delta, agente: g.agente });
  }
  return r;
}

/** Índice (en caracteres de `cps`) de cada pieza en el crudo, o -1 si no aparece. */
function alinear(cps: string[], piezas: Pieza[]): number[] {
  const r: number[] = [];
  let q = 0;
  for (const p of piezas) {
    if (p.t === 'atomo') {
      const md = Array.from(p.md);
      const k = md.length ? buscarSecuencia(cps, md, q) : -1;
      if (k >= 0) q = k + md.length;
      r.push(-1);
      continue;
    }
    const k = cps.indexOf(p.t === 'salto' ? '\n' : p.ch, q);
    if (k < 0) {
      r.push(-1);
      continue;
    }
    r.push(k);
    q = k + 1;
  }
  return r;
}

function buscarSecuencia(cps: string[], seq: string[], desde: number): number {
  for (let k = desde; k + seq.length <= cps.length; k++) {
    if (seq.every((c, i) => cps[k + i] === c)) return k;
  }
  return -1;
}

/**
 * Los tramos del documento que pinta cada rango. `raw` es el Markdown que
 * generó las piezas, y los rangos se cuentan sobre ese mismo texto.
 * Los tramos contiguos del mismo agente se juntan.
 */
export function tramos(raw: string, piezas: Pieza[], rangos: Rango[]): Tramo[] {
  if (!rangos.length) return [];
  const idx = alinear(Array.from(raw), piezas);
  const r: Tramo[] = [];
  piezas.forEach((p, i) => {
    if (p.t !== 'char' || idx[i] < 0) return;
    const g = rangos.find((g) => idx[i] >= g.desde && idx[i] < g.hasta);
    if (!g) return;
    const fin = p.pos + p.ch.length;
    const ult = r[r.length - 1];
    if (ult && ult.to === p.pos && ult.agente === g.agente) ult.to = fin;
    else r.push({ from: p.pos, to: fin, agente: g.agente });
  });
  return r;
}

/** Las piezas de un documento de TipTap, en orden. */
export function piezasDeDoc(doc: PMNode): Pieza[] {
  const r: Pieza[] = [];
  doc.descendants((bloque, pos) => {
    if (!bloque.isTextblock) return true;
    bloque.forEach((hijo, offset) => {
      const abs = pos + 1 + offset;
      if (hijo.type.name === 'hardBreak') {
        r.push({ t: 'salto', pos: abs });
      } else if (hijo.isText) {
        let p = abs;
        for (const ch of hijo.text ?? '') {
          r.push({ t: 'char', ch, pos: p });
          p += ch.length;
        }
      } else {
        r.push({ t: 'atomo', md: atomoAMd(hijo.toJSON() as Nodo), pos: abs });
      }
    });
    return false;
  });
  return r;
}
