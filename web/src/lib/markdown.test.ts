import { describe, expect, it } from 'vitest';
import { contar, escribir, leer, limpio } from './critic';
import { docAMd, mdADoc, mdAEnLinea, tipoDeDoc } from './markdown';

// El mismo ejemplo que crates/tinta-server/src/critic.rs.
const EJEMPLO =
  'llama-swap {--es muy sencillo y--} arranca {~~El ttl dice~>El campo ttl marca~~}{>>ia:claude<<} y {++apaga++}{>>ia:claude<<} el {==anterior==}{>>poner tiempos<<}. {--Fin--}{>>ia:claude<<}';

const ida = (tipo: string, s: string) => docAMd(mdADoc(tipo, s));

describe('critic, igual que el servidor', () => {
  it('leer y escribir es identidad', () => {
    expect(escribir(leer(EJEMPLO))).toBe(EJEMPLO);
  });
  it('limpio y contar', () => {
    expect(limpio(EJEMPLO)).toBe('llama-swap  arranca El ttl dice y  el anterior. Fin');
    expect(contar(EJEMPLO)).toEqual({ tachados: 1, propuestas: 3, notas: 1 });
  });
  it('llaves sueltas son texto', () => {
    expect(leer('a {-- b')).toEqual([{ t: 'texto', texto: 'a {-- b' }]);
  });
});

describe('ida y vuelta', () => {
  const casos: [string, string][] = [
    ['parrafo', EJEMPLO],
    ['parrafo', 'Un párrafo con **negrita**, *cursiva*, `código` y un [enlace](https://rubensalas.dev).'],
    ['parrafo', 'Negrita con *cursiva dentro*: **a *b* c** y [**enlace fuerte**](https://x.y)'],
    ['parrafo', '{--tachado con **negrita** dentro--} y {++añadido *en cursiva*++}{>>ia:claude<<}'],
    ['parrafo', 'Dos líneas\nen un párrafo'],
    ['parrafo', '{--a\nb--} sigue'],
    ['parrafo', 'Escapes: 2 \\* 3 y \\`no código\\` y \\[no enlace\\]'],
    ['parrafo', '\\- no es lista'],
    ['parrafo', '1\\. no es lista'],
    ['parrafo', ''],
    ['subtitulo', '### Subtítulo con `código`'],
    ['cita', '> Una cita\n> de dos líneas\n>\n> y otro párrafo'],
    ['lista', '- Un punto\n- Otro con **negrita**\n  - anidado\n  - otro\n- vuelta'],
    ['lista', '1. Primer paso\n2. Segundo paso\n   - detalle\n3. Tercero'],
    ['lista', '3. Empieza en tres\n4. Sigue'],
  ];
  for (const [tipo, s] of casos) {
    it(`${tipo}: ${JSON.stringify(s).slice(0, 50)}`, () => {
      expect(ida(tipo, s)).toBe(s);
    });
  }

  it('lo que no está escapado se normaliza y luego es estable', () => {
    const una = ida('parrafo', 'precio 2 * 3 [x]');
    expect(una).toBe('precio 2 \\* 3 \\[x\\]');
    expect(ida('parrafo', una)).toBe(una);
    expect(ida('parrafo', '- empieza como lista')).toBe('\\- empieza como lista');
  });
});

describe('estructura', () => {
  it('marcas y átomos', () => {
    const n = mdAEnLinea(EJEMPLO);
    expect(n.find((x) => x.type === 'critCambio')?.attrs).toEqual({ antes: 'El ttl dice', despues: 'El campo ttl marca', ia: 'claude' });
    expect(n.find((x) => x.type === 'critNota')?.attrs).toEqual({ texto: 'poner tiempos', ia: null });
    const tachado = n.find((x) => x.text === 'es muy sencillo y');
    expect(tachado?.marks).toEqual([{ type: 'critBorrado', attrs: { ia: null } }]);
  });
  it('tipo según el documento', () => {
    expect(tipoDeDoc(mdADoc('lista', '- a'))).toBe('lista');
    expect(tipoDeDoc(mdADoc('cita', '> a'))).toBe('cita');
    expect(tipoDeDoc(mdADoc('subtitulo', '### a'))).toBe('subtitulo');
    expect(tipoDeDoc(mdADoc('parrafo', 'a'))).toBe('parrafo');
  });
});
