import { Extension, Mark, Node, mergeAttributes } from '@tiptap/core';
import { Plugin, PluginKey } from '@tiptap/pm/state';
import { Decoration, DecorationSet } from '@tiptap/pm/view';
import type { Node as PMNode } from '@tiptap/pm/model';
import { piezasDeDoc, tramos, type Rango } from './autoria';

const atributoIa = {
  ia: {
    default: null,
    parseHTML: (el: HTMLElement) => el.getAttribute('data-ia'),
    renderHTML: (a: { ia: string | null }) => (a.ia ? { 'data-ia': a.ia } : {}),
  },
};

export const CritBorrado = Mark.create({
  name: 'critBorrado',
  inclusive: false,
  addAttributes: () => atributoIa,
  parseHTML: () => [{ tag: 'span.tachado' }, { tag: 'del' }],
  renderHTML: ({ HTMLAttributes }) => ['span', mergeAttributes(HTMLAttributes, { class: 'tachado' }), 0],
});

export const CritAnadido = Mark.create({
  name: 'critAnadido',
  inclusive: false,
  addAttributes: () => atributoIa,
  parseHTML: () => [{ tag: 'span.anadido' }],
  renderHTML: ({ HTMLAttributes }) => ['span', mergeAttributes(HTMLAttributes, { class: 'anadido' }), 0],
});

export const CritResaltado = Mark.create({
  name: 'critResaltado',
  inclusive: false,
  parseHTML: () => [{ tag: 'span.nota-ancla' }],
  renderHTML: () => ['span', { class: 'nota-ancla' }, 0],
});

export const CritCambio = Node.create({
  name: 'critCambio',
  group: 'inline',
  inline: true,
  atom: true,
  selectable: true,
  addAttributes: () => ({ antes: { default: '' }, despues: { default: '' }, ...atributoIa }),
  parseHTML: () => [{ tag: 'span[data-cambio]' }],
  renderHTML: ({ node }) => [
    'span',
    { 'data-cambio': '', class: 'cambio' },
    ['span', { class: 'tachado' }, node.attrs.antes],
    ['span', { class: 'anadido' }, node.attrs.despues],
  ],
});

export interface OpcionesTinta {
  alResolver: (indice: number, decision: 'aceptar' | 'rechazar') => void;
  alNota: (pos: number, texto: string, ia: string | null) => void;
  /** Enter sin Mayúsculas. Devuelve true si lo atendió. */
  alEnter: () => boolean;
  /** Retroceso al principio del bloque. */
  alRetrocesoInicio: () => boolean;
  alSalirArriba: () => boolean;
  alSalirAbajo: () => boolean;
}

export const CritNota = Node.create<{ alNota: OpcionesTinta['alNota'] }>({
  name: 'critNota',
  group: 'inline',
  inline: true,
  atom: true,
  selectable: true,
  addOptions: () => ({ alNota: () => {} }),
  addAttributes: () => ({ texto: { default: '' }, ...atributoIa }),
  parseHTML: () => [{ tag: 'span[data-nota]' }],
  renderHTML: ({ node }) => ['span', { 'data-nota': node.attrs.texto, class: 'nota-marca' }],
  addNodeView() {
    return ({ node, getPos }) => {
      const dom = document.createElement('span');
      dom.className = 'nota-marca';
      dom.contentEditable = 'false';
      dom.title = node.attrs.texto;
      dom.innerHTML = '<svg class="i s"><use href="/iconos.svg#nota"/></svg>';
      dom.addEventListener('mousedown', (e) => {
        e.preventDefault();
        const pos = typeof getPos === 'function' ? getPos() : undefined;
        if (pos !== undefined) this.options.alNota(pos, node.attrs.texto, node.attrs.ia);
      });
      return { dom };
    };
  },
});

const ORDEN = ['critResaltado', 'critBorrado', 'critAnadido', 'link', 'bold', 'italic', 'code'];

export interface Ocurrencia {
  indice: number;
  tipo: 'borrado' | 'anadido' | 'cambio' | 'nota';
  ia: string | null;
  desde: number;
  hasta: number;
}

/**
 * Las marcas del bloque en el orden en que salen al escribir el texto, que
 * es el orden de critic::resolver. Imita a enLineaAMd: un mismo tachado
 * cortado por un átomo se escribe como dos marcas, y aquí cuenta como dos.
 */
export function ocurrencias(doc: PMNode): Ocurrencia[] {
  const r: Ocurrencia[] = [];
  let n = 0;
  doc.descendants((bloque, pos) => {
    if (!bloque.isTextblock) return true;
    const pila: { clave: string; oc: Ocurrencia | null }[] = [];
    bloque.forEach((hijo, offset) => {
      const abs = pos + 1 + offset;
      const fin = abs + hijo.nodeSize;
      if (hijo.type.name === 'hardBreak') {
        pila.forEach((p) => p.oc && (p.oc.hasta = fin));
        return;
      }
      const marcas = hijo.isText
        ? hijo.marks
            .filter((m) => ORDEN.includes(m.type.name))
            .sort((a, b) => ORDEN.indexOf(a.type.name) - ORDEN.indexOf(b.type.name))
        : [];
      const claves = marcas.map((m) => m.type.name + ':' + (m.type.name === 'link' ? m.attrs.href : (m.attrs.ia ?? '')));
      let k = 0;
      while (k < pila.length && k < claves.length && pila[k].clave === claves[k]) k++;
      pila.length = k;
      pila.forEach((p) => p.oc && (p.oc.hasta = fin));
      marcas.slice(k).forEach((m, i) => {
        let oc: Ocurrencia | null = null;
        if (m.type.name === 'critBorrado' || m.type.name === 'critAnadido') {
          oc = { indice: n++, tipo: m.type.name === 'critBorrado' ? 'borrado' : 'anadido', ia: m.attrs.ia ?? null, desde: abs, hasta: fin };
          r.push(oc);
        }
        pila.push({ clave: claves[k + i], oc });
      });
      if (hijo.type.name === 'critCambio' || hijo.type.name === 'critNota') {
        r.push({ indice: n++, tipo: hijo.type.name === 'critCambio' ? 'cambio' : 'nota', ia: hijo.attrs.ia ?? null, desde: abs, hasta: fin });
      }
    });
    return false;
  });
  return r;
}

const clavePropuestas = new PluginKey('propuestas');

function botones(oc: Ocurrencia, alResolver: OpcionesTinta['alResolver']): HTMLElement {
  const caja = document.createElement('span');
  caja.className = 'propuesta-acc';
  caja.contentEditable = 'false';
  const quien = document.createElement('span');
  quien.className = 'ia';
  quien.textContent = 'IA';
  quien.title = `Propuesta de ${oc.ia}`;
  caja.append(quien);
  for (const [decision, icono, titulo] of [
    ['aceptar', 'check', 'Aceptar'],
    ['rechazar', 'x', 'Descartar'],
  ] as const) {
    const b = document.createElement('button');
    b.type = 'button';
    b.title = titulo;
    b.innerHTML = `<svg class="i s"><use href="/iconos.svg#${icono}"/></svg>`;
    b.addEventListener('mousedown', (e) => {
      e.preventDefault();
      alResolver(oc.indice, decision);
    });
    caja.append(b);
  }
  return caja;
}

export const Tinta = Extension.create<OpcionesTinta>({
  name: 'tinta',
  addOptions: () => ({
    alResolver: () => {},
    alNota: () => {},
    alEnter: () => false,
    alRetrocesoInicio: () => false,
    alSalirArriba: () => false,
    alSalirAbajo: () => false,
  }),
  addKeyboardShortcuts() {
    // Principio del primer párrafo o final del último, sea cual sea la
    // anidación (lista, cita).
    const extremos = () => {
      const { doc } = this.editor.state;
      let primero = -1;
      let ultimo = -1;
      doc.descendants((n, pos) => {
        if (!n.isTextblock) return true;
        if (primero < 0) primero = pos;
        ultimo = pos;
        return false;
      });
      return { primero, ultimo };
    };
    const alInicio = () => {
      const { selection } = this.editor.state;
      return selection.empty && selection.$from.parentOffset === 0 && selection.$from.before() === extremos().primero;
    };
    const alFinal = () => {
      const { selection } = this.editor.state;
      const $t = selection.$to;
      return selection.empty && $t.parentOffset === $t.parent.content.size && $t.before() === extremos().ultimo;
    };
    return {
      Enter: () => this.options.alEnter(),
      Backspace: () => (alInicio() ? this.options.alRetrocesoInicio() : false),
      ArrowUp: () => (alInicio() ? this.options.alSalirArriba() : false),
      ArrowDown: () => (alFinal() ? this.options.alSalirAbajo() : false),
    };
  },
  addProseMirrorPlugins() {
    const alResolver = (i: number, d: 'aceptar' | 'rechazar') => this.options.alResolver(i, d);
    return [
      new Plugin({
        key: clavePropuestas,
        props: {
          decorations(state) {
            const decos = ocurrencias(state.doc)
              .filter((o) => (o.tipo !== 'nota' && o.ia) || o.tipo === 'cambio' || o.tipo === 'anadido')
              .map((o) => Decoration.widget(o.hasta, () => botones(o, alResolver), { side: 1, key: `p${o.indice}-${o.hasta}` }));
            return DecorationSet.create(state.doc, decos);
          },
        },
      }),
    ];
  },
});

/**
 * Marca el texto que viene de una propuesta del agente ya aceptada. Solo pinta:
 * no cambia el documento ni el Markdown que se guarda. Al escribir, las marcas
 * se mueven con el texto (mapeo de ProseMirror).
 */
export const claveAutoria = new PluginKey<DecorationSet>('autoria');

export const AutoriaIa = Extension.create({
  name: 'autoriaIa',
  addProseMirrorPlugins() {
    return [
      new Plugin({
        key: claveAutoria,
        state: {
          init: () => DecorationSet.empty,
          apply(tr, marcas: DecorationSet) {
            // Lo manda BloqueTexto al poner un texto: sus rangos son del Markdown `raw`.
            const meta = tr.getMeta(claveAutoria) as { raw: string; rangos: Rango[] } | undefined;
            if (meta) {
              const decos = tramos(meta.raw, piezasDeDoc(tr.doc), meta.rangos).map((t) =>
                Decoration.inline(t.from, t.to, { class: 'escrito-ia', title: `Escrito por ${t.agente}` }),
              );
              return DecorationSet.create(tr.doc, decos);
            }
            return tr.docChanged ? marcas.map(tr.mapping, tr.doc) : marcas;
          },
        },
        props: {
          decorations: (state) => claveAutoria.getState(state),
        },
      }),
    ];
  },
});
