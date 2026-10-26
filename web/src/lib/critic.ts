// Port de crates/tinta-server/src/critic.rs. Tiene que leer exactamente igual
// que el servidor: el índice de una marca que manda la web a /marcas es el
// que usa critic::resolver.

export type Trozo =
  | { t: 'texto'; texto: string }
  | { t: 'borrado'; texto: string; ia: string | null }
  | { t: 'anadido'; texto: string; ia: string | null }
  | { t: 'cambio'; antes: string; despues: string; ia: string | null }
  | { t: 'nota'; texto: string; ia: string | null }
  | { t: 'resaltado'; texto: string };

const MARCAS: [string, string][] = [
  ['{--', '--}'],
  ['{++', '++}'],
  ['{~~', '~~}'],
  ['{>>', '<<}'],
  ['{==', '==}'],
];

function firma(s: string): [string, number] | null {
  if (!s.startsWith('{>>ia:')) return null;
  const tras = s.slice(6);
  const fin = tras.indexOf('<<}');
  if (fin < 0) return null;
  const nombre = tras.slice(0, fin);
  if (!nombre || nombre.includes(': ')) return null;
  return [nombre, 6 + fin + 3];
}

export function leer(s: string): Trozo[] {
  const trozos: Trozo[] = [];
  let texto = '';
  let resto = s;
  fuera: while (resto.length > 0) {
    for (const [abre, cierra] of MARCAS) {
      if (!resto.startsWith(abre)) continue;
      const tras = resto.slice(abre.length);
      const fin = tras.indexOf(cierra);
      if (fin < 0) continue;
      const dentro = tras.slice(0, fin);
      resto = tras.slice(fin + cierra.length);
      if (texto) {
        trozos.push({ t: 'texto', texto });
        texto = '';
      }
      let ia: string | null = null;
      if (abre !== '{>>' && abre !== '{==') {
        const f = firma(resto);
        if (f) {
          ia = f[0];
          resto = resto.slice(f[1]);
        }
      }
      if (abre === '{--') trozos.push({ t: 'borrado', texto: dentro, ia });
      else if (abre === '{++') trozos.push({ t: 'anadido', texto: dentro, ia });
      else if (abre === '{~~') {
        const i = dentro.indexOf('~>');
        if (i >= 0) trozos.push({ t: 'cambio', antes: dentro.slice(0, i), despues: dentro.slice(i + 2), ia });
        else trozos.push({ t: 'borrado', texto: dentro, ia });
      } else if (abre === '{>>') {
        const m = dentro.startsWith('ia:') ? dentro.slice(3) : null;
        const i = m ? m.indexOf(': ') : -1;
        if (m && i >= 0) trozos.push({ t: 'nota', texto: m.slice(i + 2), ia: m.slice(0, i) });
        else trozos.push({ t: 'nota', texto: dentro, ia: null });
      } else trozos.push({ t: 'resaltado', texto: dentro });
      continue fuera;
    }
    // Avanza un punto de código entero, como chars() en Rust.
    const c = String.fromCodePoint(resto.codePointAt(0)!);
    texto += c;
    resto = resto.slice(c.length);
  }
  if (texto) trozos.push({ t: 'texto', texto });
  return trozos;
}

const firmaDe = (ia: string | null) => (ia ? `{>>ia:${ia}<<}` : '');

export function escribir(trozos: Trozo[]): string {
  return trozos
    .map((t) => {
      switch (t.t) {
        case 'texto':
          return t.texto;
        case 'borrado':
          return `{--${t.texto}--}${firmaDe(t.ia)}`;
        case 'anadido':
          return `{++${t.texto}++}${firmaDe(t.ia)}`;
        case 'cambio':
          return `{~~${t.antes}~>${t.despues}~~}${firmaDe(t.ia)}`;
        case 'nota':
          return t.ia ? `{>>ia:${t.ia}: ${t.texto}<<}` : `{>>${t.texto}<<}`;
        case 'resaltado':
          return `{==${t.texto}==}`;
      }
    })
    .join('');
}

/** El texto que se publica (como critic::limpio). */
export function limpio(s: string): string {
  return leer(s)
    .map((t) => {
      switch (t.t) {
        case 'texto':
        case 'resaltado':
          return t.texto;
        case 'borrado':
          return t.ia ? t.texto : '';
        case 'cambio':
          return t.antes;
        default:
          return '';
      }
    })
    .join('');
}

export interface Recuento {
  tachados: number;
  propuestas: number;
  notas: number;
}

export function contar(s: string): Recuento {
  const r = { tachados: 0, propuestas: 0, notas: 0 };
  for (const t of leer(s)) {
    if (t.t === 'borrado' && !t.ia) r.tachados++;
    else if (t.t === 'borrado' || t.t === 'anadido' || t.t === 'cambio') r.propuestas++;
    else if (t.t === 'nota') r.notas++;
  }
  return r;
}

/** Texto para leer de un vistazo: sin marcas ni Markdown en línea. */
export function vistazo(s: string): string {
  return limpio(s)
    .replace(/!\[([^\]]*)\]\([^)]*\)/g, '[imagen: $1]')
    .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/<audio[^>]*title="([^"]*)"[^>]*><\/audio>/g, '[audio: $1]')
    .replace(/[*`#>]/g, '')
    .replace(/\s+/g, ' ')
    .trim();
}
