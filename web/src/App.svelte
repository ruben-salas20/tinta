<script lang="ts">
  import { api, cuandoSinSesion } from './lib/api';
  import { avisos, confirmacion, dialogo, nav, recargarCatalogo } from './lib/estado.svelte';
  import Entrar from './vistas/Entrar.svelte';
  import Articulos from './vistas/Articulos.svelte';
  import Editor from './vistas/Editor.svelte';
  import Calendario from './vistas/Calendario.svelte';

  let sesion = $state<'comprobando' | 'si' | 'no'>('comprobando');

  cuandoSinSesion(() => (sesion = 'no'));

  async function comprobar() {
    try {
      await api.sesion();
      sesion = 'si';
      recargarCatalogo();
    } catch {
      sesion = 'no';
    }
  }
  comprobar();

  // /a/<artículo>[/<idioma>[/<rama>]]
  const editor = $derived.by(() => {
    const m = nav.ruta.match(/^\/a\/([0-9a-f]+)(?:\/(es|en))?(?:\/([0-9a-f]+))?\/?$/);
    return m ? { articulo: m[1], idioma: m[2] ?? null, rama: m[3] ?? null } : null;
  });

  let valor = $state('');
  $effect(() => {
    if (dialogo.actual) valor = dialogo.actual.valor;
  });
</script>

{#if sesion === 'no'}
  <Entrar alEntrar={comprobar} />
{:else if sesion === 'si'}
  {#if editor}
    {#key editor.articulo}
      <Editor articuloId={editor.articulo} idiomaUrl={editor.idioma} ramaUrl={editor.rama} />
    {/key}
  {:else if nav.ruta.startsWith('/calendario')}
    <Calendario />
  {:else}
    <Articulos />
  {/if}
{/if}

{#if dialogo.actual}
  {@const d = dialogo.actual}
  <div class="dialogo-velo" role="presentation" onmousedown={(e) => e.target === e.currentTarget && d.resolver(null)}>
    <form class="dialogo" onsubmit={(e) => { e.preventDefault(); d.resolver(valor.trim() || null); }}>
      <h2>{d.titulo}</h2>
      {#if d.ayuda}<p>{d.ayuda}</p>{/if}
      <!-- svelte-ignore a11y_autofocus -->
      <input class="campo" bind:value={valor} autofocus onkeydown={(e) => e.key === 'Escape' && d.resolver(null)} />
      <div class="acc">
        <button type="button" class="btn fantasma" onclick={() => d.resolver(null)}>Cancelar</button>
        <button class="btn primario" disabled={!valor.trim()}>{d.aceptar}</button>
      </div>
    </form>
  </div>
{/if}

{#if confirmacion.actual}
  {@const c = confirmacion.actual}
  <div class="dialogo-velo" role="presentation" onmousedown={(e) => e.target === e.currentTarget && c.resolver(false)}>
    <div class="dialogo" role="alertdialog" aria-label={c.texto}>
      <p style="color:var(--text);font-size:14px">{c.texto}</p>
      <div class="acc">
        <button class="btn fantasma" onclick={() => c.resolver(false)}>Cancelar</button>
        <!-- svelte-ignore a11y_autofocus -->
        <button class="btn primario" autofocus onclick={() => c.resolver(true)}>{c.aceptar}</button>
      </div>
    </div>
  </div>
{/if}

<div class="avisos" aria-live="polite">
  {#each avisos as a (a.id)}
    <div class:error={a.tipo === 'error'}>{a.texto}</div>
  {/each}
</div>
