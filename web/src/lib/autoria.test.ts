import { describe, expect, it } from 'vitest';
import { Node as PMNode, Schema } from '@tiptap/pm/model';
import { desplazar, piezasDeDoc, tramos, type Rango } from './autoria';
import { mdADoc } from './markdown';

// Solo las piezas que usa el Markdown de un párrafo. Mismos nombres que extensiones.ts.
const schema = new Schema({
  nodes: {
    doc: { content: 'block+' },
    paragraph: { content: 'inline*', group: 'block' },
    text: { group: 'inline' },
    hardBreak: { inline: true, group: 'inline', selectable: false },
    critCambio: {
      inline: true,
      group: 'inline',
      atom: true,
      attrs: { antes: { default: '' }, despues: { default: '' }, ia: { default: null } },
    },
    critNota: { inline: true, group: 'inline', atom: true, attrs: { texto: { default: '' }, ia: { default: null } } },
  },
  marks: {
    bold: {},
    italic: {},
    code: {},
    link: { attrs: { href: {} } },
    critBorrado: { attrs: { ia: { default: null } } },
    critAnadido: { attrs: { ia: { default: null } } },
    critResaltado: {},
  },
});

const r = (desde: number, hasta: number): Rango => ({ desde, hasta, agente: 'claude' });

function documento(raw: string): PMNode {
  return PMNode.fromJSON(schema, mdADoc('parrafo', raw));
}

/** El rango que ocupa la primera aparición de `buscado` en `raw`, en caracteres. */
function rangoDe(raw: string, buscado: string, agente = 'claude'): Rango {
  const desde = Array.from(raw.slice(0, raw.indexOf(buscado))).length;
  return { desde, hasta: desde + Array.from(buscado).length, agente };
}

/** El texto que pinta cada tramo, tal como lo ve el editor. */
function pintado(raw: string, rangos: Rango[]): string[] {
  const doc = documento(raw);
  return tramos(raw, piezasDeDoc(doc), rangos).map((t) => doc.textBetween(t.from, t.to));
}

describe('desplazar, igual que el servidor', () => {
  it('sin cambios no mueve nada', () => {
    expect(desplazar([r(5, 10)], 'Hola mundo', 'Hola mundo')).toEqual([r(5, 10)]);
  });

  it('un cambio antes desplaza', () => {
    expect(desplazar([r(5, 10)], 'hola mundo', 'hola grande mundo')).toEqual([r(12, 17)]);
    expect(desplazar([r(5, 10)], 'hola mundo', 'AB hola mundo')).toEqual([r(8, 13)]);
  });

  it('un cambio después no mueve', () => {
    expect(desplazar([r(0, 4)], 'hola mundo', 'hola mundo!!')).toEqual([r(0, 4)]);
    expect(desplazar([r(0, 4)], 'hola mundo', 'hola')).toEqual([r(0, 4)]);
  });

  it('un cambio encima borra el rango', () => {
    expect(desplazar([r(5, 10)], 'hola mundo', 'hola xyz')).toEqual([]);
  });

  it('un cambio en parte recorta a lo intacto', () => {
    expect(desplazar([r(5, 10)], 'hola mundo', 'hola muelle')).toEqual([r(5, 7)]);
    expect(desplazar([r(5, 10)], 'hola mundo', 'adiós mundo')).toEqual([r(6, 11)]);
  });

  it('insertar dentro parte el rango', () => {
    expect(desplazar([r(0, 4)], 'abcd', 'abXcd')).toEqual([r(0, 2), r(3, 5)]);
  });

  it('insertar justo al borde no toca el rango', () => {
    expect(desplazar([r(2, 4)], 'abcd', 'abXcd')).toEqual([r(3, 5)]);
    expect(desplazar([r(0, 2)], 'abcd', 'abXcd')).toEqual([r(0, 2)]);
  });

  it('cuenta caracteres y no bytes', () => {
    expect(desplazar([r(4, 9)], 'ñé🙂 mundo', 'ñé🙂 grande mundo')).toEqual([r(11, 16)]);
  });
});

describe('tramos: de la posición en el Markdown a la del documento', () => {
  it('texto normal', () => {
    expect(pintado('Hola mundo', [rangoDe('Hola mundo', 'mundo')])).toEqual(['mundo']);
  });

  it('con tildes y emoji: las posiciones del documento van en UTF-16', () => {
    const raw = 'Qué 🚀 {++ñandú 🙂++}{>>ia:claude<<} fin';
    expect(pintado(raw, [rangoDe(raw, 'ñandú 🙂')])).toEqual(['ñandú 🙂']);
  });

  it('con negrita: la sintaxis no cuenta', () => {
    const raw = '**Hola** {++mundo++}{>>ia:claude<<}';
    expect(pintado(raw, [rangoDe(raw, 'mundo')])).toEqual(['mundo']);
  });

  it('con un escape de Markdown', () => {
    const raw = 'a\\*b {++c++}{>>ia:claude<<}';
    expect(pintado(raw, [rangoDe(raw, 'c')])).toEqual(['c']);
  });

  it('después de un cambio o una nota (átomos)', () => {
    const raw = 'Hola {~~a~>b~~}{>>ia:claude<<} {++mundo++}{>>ia:claude<<}';
    expect(pintado(raw, [rangoDe(raw, 'mundo')])).toEqual(['mundo']);
  });

  it('un rango sobre la sintaxis no pinta nada', () => {
    expect(pintado('**Hola**', [r(0, 2)])).toEqual([]);
  });

  it('sin rangos no hay tramos', () => {
    expect(tramos('Hola', piezasDeDoc(documento('Hola')), [])).toEqual([]);
  });

  it('dos rangos seguidos del mismo agente se juntan en un tramo', () => {
    const raw = 'uno {++dos++}{>>ia:claude<<}{++ tres++}{>>ia:claude<<}';
    const doc = documento(raw);
    const t = tramos(raw, piezasDeDoc(doc), [rangoDe(raw, 'dos'), rangoDe(raw, ' tres')]);
    expect(t.map((x) => doc.textBetween(x.from, x.to))).toEqual(['dos tres']);
  });
});
