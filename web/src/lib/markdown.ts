// Texto de un bloque (Markdown + CriticMarkup) ↔ documento JSON de TipTap.
//
// El servidor guarda siempre texto. Cada bloque de prosa se edita con su
// propio TipTap, así que aquí solo hace falta lo que cabe en un bloque:
// un párrafo, un subtítulo (###), una cita o una lista, con negrita,
// cursiva, código en línea, enlaces y las marcas de revisión.
//
// Las marcas de revisión van así en TipTap:
// - tachado, añadido y resaltado (ancla de nota): marcas sobre el texto;
// - cambio propuesto y nota: nodos atómicos, porque no se editan por dentro.

import { leer } from './critic';

export interface Marca {
  type: string;
  attrs?: Record<string, unknown>;
}

export interface Nodo {
  type: string;
  attrs?: Record<string, unknown>;
  content?: Nodo[];
  text?: string;
  marks?: Marca[];
}

// De fuera adentro: así se anidan los delimitadores al escribir.
const ORDEN = ['critResaltado', 'critBorrado', 'critAnadido', 'link', 'bold', 'italic', 'code'];

const ia = (m: Marca) => (m.attrs?.ia as string | null | undefined) ?? null;
const firma = (m: Marca) => (ia(m) ? `{>>ia:${ia(m)}<<}` : '');

function abre(m: Marca): string {
  switch (m.type) {
    case 'critResaltado':
      return '{==';
    case 'critBorrado':
      return '{--';
    case 'critAnadido':
      return '{++';
    case 'link':
      return '[';
    case 'bold':
      return '**';
    case 'italic':
      return '*';
    case 'code':
      return '`';
  }
  return '';
}

function cierra(m: Marca): string {
  switch (m.type) {
    case 'critResaltado':
      return '==}';
    case 'critBorrado':
      return '--}' + firma(m);
    case 'critAnadido':
      return '++}' + firma(m);
    case 'link':
      return `](${String(m.attrs?.href ?? '')})`;
    case 'bold':
      return '**';
    case 'italic':
      return '*';
    case 'code':
      return '`';
  }
  return '';
}

function igual(a: Marca, b: Marca): boolean {
  if (a.type !== b.type) return false;
  if (a.type === 'link') return a.attrs?.href === b.attrs?.href;
  if (a.type === 'critBorrado' || a.type === 'critAnadido') return ia(a) === ia(b);
  return true;
}

function ordenar(marcas: Marca[] | undefined): Marca[] {
  return (marcas ?? []).filter((m) => ORDEN.includes(m.type)).sort((a, b) => ORDEN.indexOf(a.type) - ORDEN.indexOf(b.type));
}

function escapar(s: string): string {
  return s.replace(/[\\*`[\]]/g, (c) => '\\' + c);
}

export function atomoAMd(n: Nodo): string {
  const a = n.attrs ?? {};
  const f = a.ia ? `{>>ia:${a.ia}<<}` : '';
  if (n.type === 'critCambio') return `{~~${a.antes ?? ''}~>${a.despues ?? ''}~~}${f}`;
  if (n.type === 'critNota') return a.ia ? `{>>ia:${a.ia}: ${a.texto ?? ''}<<}` : `{>>${a.texto ?? ''}<<}`;
  return '';
}

/** Contenido en línea (hijos de un párrafo o un título) a texto. */
export function enLineaAMd(nodos: Nodo[]): string {
  let out = '';
  const pila: Marca[] = [];
  const llevar = (marcas: Marca[]) => {
    let k = 0;
    while (k < pila.length && k < marcas.length && igual(pila[k], marcas[k])) k++;
    while (pila.length > k) out += cierra(pila.pop()!);
    for (const m of marcas.slice(k)) {
      out += abre(m);
      pila.push(m);
    }
  };
  for (const n of nodos) {
    if (n.type === 'hardBreak') {
      // Un salto no cierra las marcas: «{--a\nb--}» sigue siendo una sola.
      out += '\n';
      continue;
    }
    if (n.type === 'text') {
      const marcas = ordenar(n.marks);
      llevar(marcas);
      out += marcas.some((m) => m.type === 'code') ? (n.text ?? '') : escapar(n.text ?? '');
      continue;
    }
    llevar([]);
    out += atomoAMd(n);
  }
  llevar([]);
  return out;
}

// ------------------------------------------------------------------ Leer

function texto(t: string, marcas: Marca[]): Nodo[] {
  const r: Nodo[] = [];
  t.split('\n').forEach((parte, i) => {
    if (i > 0) r.push({ type: 'hardBreak' });
    if (parte) r.push(marcas.length ? { type: 'text', text: parte, marks: marcas } : { type: 'text', text: parte });
  });
  return r;
}

const PUNTUACION = /[!-/:-@[-`{-~]/;

function buscarCierre(s: string, desde: number, delim: '*' | '**'): number {
  let j = desde;
  while (j < s.length) {
    const c = s[j];
    if (c === '\\') {
      j += 2;
      continue;
    }
    if (c === '`') {
      const k = s.indexOf('`', j + 1);
      if (k > j) {
        j = k + 1;
        continue;
      }
    }
    if (s.startsWith('**', j)) {
      if (delim === '**') return j;
      // Dentro de una cursiva, salta la negrita entera.
      const k = s.indexOf('**', j + 2);
      if (k > j) {
        j = k + 2;
        continue;
      }
    }
    if (delim === '*' && c === '*') return j;
    j++;
  }
  return -1;
}

function buscarCorchete(s: string, desde: number): number {
  for (let j = desde; j < s.length; j++) {
    if (s[j] === '\\') j++;
    else if (s[j] === ']') return j;
  }
  return -1;
}

function md(s: string, marcas: Marca[]): Nodo[] {
  const r: Nodo[] = [];
  let buf = '';
  const vaciar = () => {
    if (buf) r.push(...texto(buf, marcas));
    buf = '';
  };
  let i = 0;
  while (i < s.length) {
    const c = s[i];
    if (c === '\\' && i + 1 < s.length && PUNTUACION.test(s[i + 1])) {
      buf += s[i + 1];
      i += 2;
      continue;
    }
    if (c === '`') {
      const j = s.indexOf('`', i + 1);
      if (j > i + 1) {
        vaciar();
        r.push(...texto(s.slice(i + 1, j), [...marcas, { type: 'code' }]));
        i = j + 1;
        continue;
      }
    }
    if (s.startsWith('**', i)) {
      const j = buscarCierre(s, i + 2, '**');
      if (j > i + 2) {
        vaciar();
        r.push(...md(s.slice(i + 2, j), [...marcas, { type: 'bold' }]));
        i = j + 2;
        continue;
      }
    } else if (c === '*') {
      const j = buscarCierre(s, i + 1, '*');
      if (j > i + 1) {
        vaciar();
        r.push(...md(s.slice(i + 1, j), [...marcas, { type: 'italic' }]));
        i = j + 1;
        continue;
      }
    }
    if (c === '[') {
      const j = buscarCorchete(s, i + 1);
      if (j > i && s[j + 1] === '(') {
        const k = s.indexOf(')', j + 2);
        if (k > j) {
          vaciar();
          const href = s.slice(j + 2, k);
          r.push(...md(s.slice(i + 1, j), [...marcas, { type: 'link', attrs: { href } }]));
          i = k + 1;
          continue;
        }
      }
    }
    buf += c;
    i++;
  }
  vaciar();
  return r;
}

function juntar(nodos: Nodo[]): Nodo[] {
  const r: Nodo[] = [];
  for (const n of nodos) {
    const prev = r[r.length - 1];
    if (prev && prev.type === 'text' && n.type === 'text' && JSON.stringify(ordenar(prev.marks)) === JSON.stringify(ordenar(n.marks))) {
      prev.text = (prev.text ?? '') + (n.text ?? '');
    } else r.push({ ...n });
  }
  return r;
}

/** Texto en línea a nodos de TipTap. */
export function mdAEnLinea(s: string): Nodo[] {
  const r: Nodo[] = [];
  for (const t of leer(s)) {
    switch (t.t) {
      case 'texto':
        r.push(...md(t.texto, []));
        break;
      case 'borrado':
        r.push(...md(t.texto, [{ type: 'critBorrado', attrs: { ia: t.ia } }]));
        break;
      case 'anadido':
        r.push(...md(t.texto, [{ type: 'critAnadido', attrs: { ia: t.ia } }]));
        break;
      case 'resaltado':
        r.push(...md(t.texto, [{ type: 'critResaltado' }]));
        break;
      case 'cambio':
        r.push({ type: 'critCambio', attrs: { antes: t.antes, despues: t.despues, ia: t.ia } });
        break;
      case 'nota':
        r.push({ type: 'critNota', attrs: { texto: t.texto, ia: t.ia } });
        break;
    }
  }
  return juntar(r);
}

// --------------------------------------------------------------- Bloques

function parrafo(s: string): Nodo {
  const content = mdAEnLinea(s);
  return content.length ? { type: 'paragraph', content } : { type: 'paragraph' };
}

// Al principio de línea, «- », «1. », «# » o «> » cambiarían el bloque al
// publicarlo. Se escapan para que un párrafo siga siendo un párrafo.
function escaparInicios(s: string): string {
  return s
    .split('\n')
    .map((l) => l.replace(/^(\s*)([-+*#>])(?=\s|$)/, '$1\\$2').replace(/^(\s*\d+)([.)])(?=\s|$)/, '$1\\$2'))
    .join('\n');
}

function parrafoAMd(n: Nodo): string {
  return escaparInicios(enLineaAMd(n.content ?? []));
}

const MARCADOR = /^(\s*)([-*+]|(\d+)[.)])\s(.*)$/;

function leerLista(lineas: string[]): Nodo[] {
  const raiz: Nodo[] = [];
  const pila: { sangria: number; lista: Nodo }[] = [];
  let ultimo: Nodo | null = null;
  for (const linea of lineas) {
    const m = linea.match(MARCADOR);
    if (!m) {
      if (ultimo && linea.trim()) {
        const p = ultimo.content![0];
        p.content = juntar([...(p.content ?? []), { type: 'hardBreak' }, ...mdAEnLinea(linea.trim())]);
      }
      continue;
    }
    const sangria = m[1].length;
    const ordenada = m[3] !== undefined;
    while (pila.length && pila[pila.length - 1].sangria > sangria) pila.pop();
    const tope = pila[pila.length - 1];
    if (!tope || sangria > tope.sangria) {
      const lista: Nodo = ordenada
        ? { type: 'orderedList', attrs: { start: Number(m[3]) }, content: [] }
        : { type: 'bulletList', content: [] };
      if (tope && ultimo) ultimo.content!.push(lista);
      else raiz.push(lista);
      pila.push({ sangria, lista });
    }
    ultimo = { type: 'listItem', content: [parrafo(m[4])] };
    pila[pila.length - 1].lista.content!.push(ultimo);
  }
  return raiz.length ? raiz : [{ type: 'bulletList', content: [{ type: 'listItem', content: [{ type: 'paragraph' }] }] }];
}

function listaAMd(n: Nodo, sangria: string): string[] {
  const r: string[] = [];
  let num = Number(n.attrs?.start ?? 1);
  for (const item of n.content ?? []) {
    const marcador = n.type === 'orderedList' ? `${num++}.` : '-';
    const hueco = ' '.repeat(marcador.length + 1);
    let primero = true;
    for (const hijo of item.content ?? []) {
      if (hijo.type === 'paragraph') {
        enLineaAMd(hijo.content ?? []).split('\n').forEach((l, i) => {
          r.push(primero && i === 0 ? `${sangria}${marcador} ${l}` : `${sangria}${hueco}${l}`);
        });
        primero = false;
      } else if (hijo.type === 'bulletList' || hijo.type === 'orderedList') {
        if (primero) {
          r.push(`${sangria}${marcador} `);
          primero = false;
        }
        r.push(...listaAMd(hijo, sangria + hueco));
      }
    }
    if (primero) r.push(`${sangria}${marcador} `);
  }
  return r;
}

export type TipoTexto = 'parrafo' | 'subtitulo' | 'cita' | 'lista';

/** Texto de un bloque a documento de TipTap. */
export function mdADoc(tipo: string, s: string): Nodo {
  let content: Nodo[];
  if (tipo === 'subtitulo') {
    const t = s.replace(/^#{1,6}\s?/, '');
    const inl = mdAEnLinea(t);
    content = [inl.length ? { type: 'heading', attrs: { level: 3 }, content: inl } : { type: 'heading', attrs: { level: 3 } }];
  } else if (tipo === 'cita') {
    const lineas = s.split('\n').map((l) => l.replace(/^>\s?/, ''));
    const parrafos: string[][] = [[]];
    for (const l of lineas) {
      if (l.trim() === '') parrafos.push([]);
      else parrafos[parrafos.length - 1].push(l);
    }
    content = [{ type: 'blockquote', content: parrafos.filter((p) => p.length).map((p) => parrafo(p.join('\n'))) }];
    if (!content[0].content!.length) content[0].content = [{ type: 'paragraph' }];
  } else if (tipo === 'lista') {
    content = leerLista(s.split('\n'));
  } else {
    content = [parrafo(s)];
  }
  return { type: 'doc', content };
}

function nodoAMd(n: Nodo): string {
  switch (n.type) {
    case 'heading':
      return '### ' + enLineaAMd(n.content ?? []);
    case 'blockquote':
      return (n.content ?? [])
        .map((p) => parrafoAMd(p).split('\n').map((l) => (l ? `> ${l}` : '>')).join('\n'))
        .join('\n>\n');
    case 'bulletList':
    case 'orderedList':
      return listaAMd(n, '').join('\n');
    case 'paragraph':
      return parrafoAMd(n);
  }
  return '';
}

/** Documento de TipTap a texto del bloque. */
export function docAMd(doc: Nodo): string {
  return (doc.content ?? []).map(nodoAMd).join('\n\n');
}

/** El tipo de bloque que corresponde a un documento (tras «- », «> » o «### »). */
export function tipoDeDoc(doc: Nodo): TipoTexto {
  switch (doc.content?.[0]?.type) {
    case 'heading':
      return 'subtitulo';
    case 'blockquote':
      return 'cita';
    case 'bulletList':
    case 'orderedList':
      return 'lista';
  }
  return 'parrafo';
}
