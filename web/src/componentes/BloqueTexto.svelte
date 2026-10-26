<script lang="ts">
  import { onDestroy, onMount } from 'svelte';
  import { Editor } from '@tiptap/core';
  import StarterKit from '@tiptap/starter-kit';
  import type { Bloque, TipoBloque } from '../lib/api';
  import { activaBloque, type ControlEditor } from '../lib/editor.svelte';
  import { CritAnadido, CritBorrado, CritCambio, CritNota, CritResaltado, Tinta, ocurrencias, type Ocurrencia } from '../lib/extensiones';
  import { docAMd, enLineaAMd, mdADoc, mdAEnLinea, tipoDeDoc, type Nodo } from '../lib/markdown';
  import { avisar, pedirTexto } from '../lib/estado.svelte';
  import MenuInsertar from './MenuInsertar.svelte';

  let {
    bloque,
    ctl,
    alCursor,
    alInsertar,
  }: {
    bloque: Bloque;
    ctl: ControlEditor;
    /** La marca bajo el cursor, para que la barra ofrezca resolverla. */
    alCursor: (o: Ocurrencia | null, nota: string | null) => void;
    alInsertar: (tipo: TipoBloque) => void;
  } = $props();

  const variante = $derived(activaBloque(bloque));
  const PISTA: Record<string, string> = { parrafo: 'Escribe, o pulsa / para insertar…', subtitulo: 'Subtítulo', cita: 'Cita', lista: '' };

  let el: HTMLDivElement;
  let editor: Editor | null = null;
  let mostrada = '';
  let actual = '';
  let menu = $state<string | null>(null);

  function marcarVacio() {
    if (!editor) return;
    const dom = editor.view.dom as HTMLElement;
    dom.classList.toggle('vacio', editor.isEmpty);
    dom.dataset.placeholder = PISTA[bloque.tipo] ?? '';
  }

  function poner(md: string) {
    if (!editor) return;
    actual = md;
    editor.commands.setContent(mdADoc(bloque.tipo, md) as never, { emitUpdate: false });
    marcarVacio();
  }

  function textoFinal(): number {
    let fin = 1;
    editor!.state.doc.descendants((n, pos) => {
      if (!n.isTextblock) return true;
      fin = pos + 1 + n.content.size;
      return false;
    });
    return fin;
  }

  function alEnter(): boolean {
    if (!editor) return false;
    if (menu !== null) return true;
    const { selection } = editor.state;
    const pf = selection.$from;
    if (bloque.tipo === 'lista') {
      // Enter en el último punto vacío de la lista: sale a un párrafo nuevo.
      const json = editor.getJSON() as Nodo;
      const lista = json.content?.[0];
      const items = lista?.content ?? [];
      const ultimo = items[items.length - 1];
      const vacio = pf.parent.content.size === 0 && pf.depth === 3 && pf.index(1) === items.length - 1 && ultimo?.content?.length === 1;
      if (!vacio || items.length < 2) return false;
      lista!.content = items.slice(0, -1);
      const md = docAMd(json);
      poner(md);
      ctl.programar(variante!.id, md);
      ctl.crearTras(bloque.id, 'parrafo', '');
      return true;
    }
    const resto = pf.parent.content.cut(pf.parentOffset).toJSON() as Nodo[] | null;
    const mdResto = enLineaAMd(resto ?? []);
    editor.chain().deleteRange({ from: selection.from, to: pf.end() }).run();
    ctl.crearTras(bloque.id, 'parrafo', mdResto);
    return true;
  }

  function alRetrocesoInicio(): boolean {
    if (!editor) return false;
    if (editor.isEmpty) {
      if (ctl.visibles().length > 1) ctl.borrarBloque(bloque.id);
      return true;
    }
    if (bloque.tipo === 'subtitulo') {
      const md = enLineaAMd((editor.getJSON() as Nodo).content?.[0]?.content ?? []);
      actual = md;
      ctl.programar(variante!.id, md);
      ctl.cambiarTipo(bloque.id, 'parrafo');
      return true;
    }
    if (bloque.tipo !== 'parrafo') return false;
    const anterior = ctl.vecino(bloque.id, -1);
    if (!anterior || anterior.bloque.tipo !== 'parrafo' || anterior.vs.id !== ctl.ubicar(bloque.id)?.vs.id) return false;
    const md = docAMd(editor.getJSON() as Nodo);
    ctl.flush(variante!.id).then(async () => {
      await ctl.unir(anterior.bloque.id, md);
      await ctl.borrarBloque(bloque.id, false);
    });
    return true;
  }

  function cursor() {
    if (!editor) return;
    const { from, empty } = editor.state.selection;
    const oc = ocurrencias(editor.state.doc).find((o) => o.tipo === 'borrado' && !o.ia && from >= o.desde && from <= o.hasta);
    alCursor(oc ?? null, empty ? null : 'seleccion');
  }

  onMount(() => {
    editor = new Editor({
      element: el,
      extensions: [
        StarterKit.configure({
          codeBlock: false,
          horizontalRule: false,
          strike: false,
          underline: false,
          trailingNode: false,
          heading: { levels: [3] },
          link: { openOnClick: false, autolink: true },
        }),
        CritBorrado,
        CritAnadido,
        CritResaltado,
        CritCambio,
        CritNota.configure({
          alNota: (pos, texto) => {
            ctl.foco = bloque.id;
            const oc = ocurrencias(editor!.state.doc).find((o) => o.tipo === 'nota' && o.desde === pos);
            alCursor(oc ?? null, texto);
          },
        }),
        Tinta.configure({
          alResolver: (i, d) => ctl.resolver(variante!.id, i, d),
          alEnter,
          alRetrocesoInicio,
          alSalirArriba: () => (ctl.moverFoco(bloque.id, -1), true),
          alSalirAbajo: () => (ctl.moverFoco(bloque.id, 1), true),
        }),
      ],
      content: mdADoc(bloque.tipo, variante?.contenido ?? '') as never,
      onUpdate: ({ editor: e }) => {
        marcarVacio();
        const json = e.getJSON() as Nodo;
        const md = docAMd(json);
        if (bloque.tipo === 'parrafo' && /^\/[\p{L}]*$/u.test(md)) menu = md.slice(1);
        else menu = null;
        if (md === actual) return;
        actual = md;
        ctl.programar(variante!.id, md);
        // «- », «> » o «### » convierten el bloque, como en Markdown.
        const tipo = tipoDeDoc(json);
        if (tipo !== bloque.tipo) ctl.cambiarTipo(bloque.id, tipo);
      },
      onFocus: () => {
        ctl.foco = bloque.id;
        cursor();
      },
      onSelectionUpdate: cursor,
      onBlur: () => setTimeout(() => (menu = null), 150),
    });
    mostrada = variante?.id ?? '';
    actual = variante?.contenido ?? '';
    marcarVacio();
    return ctl.registrar(bloque.id, {
      enfocar: (donde) => editor?.commands.focus(donde === 'inicio' ? 'start' : 'end'),
      anexar: (md) => {
        if (!editor) return;
        const fin = textoFinal();
        editor.chain().insertContentAt(fin, mdAEnLinea(md) as never).setTextSelection(fin).focus().run();
      },
      tachar: () => {
        if (!editor || editor.state.selection.empty) return avisar('Selecciona el texto que quieres tachar.');
        editor.chain().focus().toggleMark('critBorrado', { ia: null }).run();
      },
      anotar: async () => {
        if (!editor) return;
        const { from, to, empty } = editor.state.selection;
        if (empty) {
          avisar('Selecciona el texto al que va la nota.');
          return;
        }
        const texto = await pedirTexto('Nota al margen', '', 'Añadir nota', 'Solo la ves tú: no se exporta.');
        if (!texto) {
          editor.commands.focus();
          return;
        }
        editor
          .chain()
          .setTextSelection({ from, to })
          .setMark('critResaltado')
          .insertContentAt(to, { type: 'critNota', attrs: { texto, ia: null } })
          .focus()
          .run();
      },
    });
  });

  // Otra variante activa, o el texto cambió fuera (marca aceptada,
  // historial restaurado): se pone en el editor salvo si hay algo sin guardar.
  $effect(() => {
    const v = variante;
    if (!editor || !v) return;
    if (v.id !== mostrada) {
      mostrada = v.id;
      poner(v.contenido);
    } else if (v.contenido !== actual && !ctl.hayPendiente(v.id)) {
      poner(v.contenido);
    }
  });

  // Un cambio de tipo hecho desde fuera (menú, Retroceso) rehace el documento.
  let tipoMostrado = '';
  onMount(() => (tipoMostrado = bloque.tipo));
  $effect(() => {
    const t = bloque.tipo;
    if (!editor || t === tipoMostrado) return;
    tipoMostrado = t;
    if (tipoDeDoc(editor.getJSON() as Nodo) !== t) {
      const md = actual;
      poner(md);
    }
    marcarVacio();
  });

  onDestroy(() => editor?.destroy());

  function elegir(tipo: TipoBloque) {
    menu = null;
    editor?.commands.clearContent(false);
    actual = '';
    ctl.programar(variante!.id, '');
    alInsertar(tipo);
  }
</script>

<div bind:this={el} class="texto t-{bloque.tipo}"></div>
{#if menu !== null}
  <MenuInsertar filtro={menu} alElegir={elegir} alCerrar={() => (menu = null)} />
{/if}

<style>
  .texto { position: relative; }
  .t-subtitulo :global(.ProseMirror) { font: 600 18px/1.4 var(--serif); }
</style>
