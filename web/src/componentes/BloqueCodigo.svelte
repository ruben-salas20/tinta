<script lang="ts">
  import type { Bloque } from '../lib/api';
  import { activaBloque, type ControlEditor } from '../lib/editor.svelte';

  let { bloque, ctl }: { bloque: Bloque; ctl: ControlEditor } = $props();
  const variante = $derived(activaBloque(bloque));

  function leer(md: string): { lenguaje: string; codigo: string } {
    const m = md.match(/^```([^\n]*)\n([\s\S]*?)\n?```\s*$/);
    return m ? { lenguaje: m[1].trim(), codigo: m[2] } : { lenguaje: '', codigo: md.replace(/^```[^\n]*\n?/, '') };
  }
  const escribir = (l: string, c: string) => '```' + l + '\n' + c + '\n```';

  let lenguaje = $state('');
  let codigo = $state('');
  let mostrada = '';
  let area: HTMLTextAreaElement;

  $effect(() => {
    const v = variante;
    if (!v) return;
    if (v.id !== mostrada || (!ctl.hayPendiente(v.id) && v.contenido !== escribir(lenguaje, codigo))) {
      mostrada = v.id;
      ({ lenguaje, codigo } = leer(v.contenido));
    }
  });

  function cambio() {
    ctl.programar(variante!.id, escribir(lenguaje.trim(), codigo));
  }

  function tecla(e: KeyboardEvent) {
    // Tab sangra en vez de salir del campo.
    if (e.key === 'Tab' && !e.shiftKey) {
      e.preventDefault();
      const { selectionStart: i, selectionEnd: f } = area;
      codigo = codigo.slice(0, i) + '  ' + codigo.slice(f);
      requestAnimationFrame(() => area.setSelectionRange(i + 2, i + 2));
      cambio();
    } else if (e.key === 'Backspace' && !codigo && !lenguaje) {
      e.preventDefault();
      ctl.cambiarTipo(bloque.id, 'parrafo');
    }
  }

  $effect(() => ctl.registrar(bloque.id, { enfocar: (d) => { area.focus(); if (d === 'fin') area.setSelectionRange(codigo.length, codigo.length); } }));

  const filas = $derived(Math.max(2, codigo.split('\n').length));
</script>

<div class="lenguaje"><input class="leng" bind:value={lenguaje} oninput={cambio} placeholder="lenguaje (yaml, python…)" onfocus={() => (ctl.foco = bloque.id)} /></div>
<textarea
  class="codigo"
  bind:this={area}
  bind:value={codigo}
  oninput={cambio}
  onkeydown={tecla}
  onfocus={() => (ctl.foco = bloque.id)}
  rows={filas}
  spellcheck="false"
  placeholder="Código"
></textarea>

<style>
  .leng { border: 0; background: transparent; font: 11px var(--mono); color: var(--faint); outline: none; padding: 0; width: 220px; }
  textarea.codigo { display: block; width: 100%; resize: none; border: 0; outline: none; margin: 0; padding: 16px 18px; background: var(--surface-2); border-radius: 10px; font: 12.5px/1.65 var(--mono); color: var(--text); overflow-x: auto; white-space: pre; tab-size: 2; }
</style>
