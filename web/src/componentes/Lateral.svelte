<script lang="ts">
  import { api } from '../lib/api';
  import { catalogo, ir, nav } from '../lib/estado.svelte';
  import Icono from './Icono.svelte';
  import Logo from './Logo.svelte';

  const filtro = $derived(new URLSearchParams(nav.buscar));
  const enLista = $derived(nav.ruta === '/');

  function enlace(e: MouseEvent, ruta: string) {
    e.preventDefault();
    ir(ruta);
  }

  async function salir() {
    await api.salir().catch(() => {});
    location.reload();
  }
</script>

<nav class="lateral">
  <a class="logo" href="/" onclick={(e) => enlace(e, '/')}><Logo />Tinta</a>
  <a class="item" class:activo={enLista && !filtro.get('serie') && !filtro.get('etiqueta')} href="/" onclick={(e) => enlace(e, '/')}>
    <Icono n="articulos" />Artículos<span class="n">{catalogo.articulos.length}</span>
  </a>
  <span class="item desactivado" title="Llega en la fase 2"><Icono n="calendario" />Calendario<span class="n">pronto</span></span>

  {#if catalogo.series.length}
    <div class="grupo">Series</div>
    {#each catalogo.series as s (s.id)}
      <a class="item" class:activo={enLista && filtro.get('serie') === s.id} href="/?serie={s.id}" onclick={(e) => enlace(e, `/?serie=${s.id}`)}>
        <Icono n="serie" />{s.nombre}<span class="n">{s.articulos}</span>
      </a>
    {/each}
  {/if}

  {#if catalogo.etiquetas.length}
    <div class="grupo">Etiquetas</div>
    {#each catalogo.etiquetas as e (e.nombre)}
      <a
        class="item"
        class:activo={enLista && filtro.get('etiqueta') === e.nombre}
        href="/?etiqueta={encodeURIComponent(e.nombre)}"
        onclick={(ev) => enlace(ev, `/?etiqueta=${encodeURIComponent(e.nombre)}`)}
      >
        <Icono n="etiqueta" />{e.nombre}<span class="n">{e.articulos}</span>
      </a>
    {/each}
  {/if}

  <div class="pie">
    <button class="btn fantasma" style="justify-content:flex-start;padding:0;height:24px;color:var(--faint)" onclick={salir}>
      <Icono n="izq" s />Salir
    </button>
  </div>
</nav>

<style>
  .desactivado { display: flex; align-items: center; gap: 10px; padding: 7px 8px; color: var(--faint); cursor: default; }
  .desactivado .n { margin-left: auto; font-size: 12px; }
  .lateral { overflow-y: auto; }
</style>
