<script lang="ts">
  import { api } from '../lib/api';
  import { catalogo, confirmar, fallo, ir, nav } from '../lib/estado.svelte';
  import Icono from './Icono.svelte';
  import Logo from './Logo.svelte';

  const filtro = $derived(new URLSearchParams(nav.buscar));
  const enLista = $derived(nav.ruta === '/');
  const enCalendario = $derived(nav.ruta.startsWith('/calendario'));

  // El filtro de canal es parte de la dirección del calendario; el resto de
  // la dirección (vista, fecha) se conserva al cambiarlo.
  function conCanal(id: string | null): string {
    const q = new URLSearchParams(nav.buscar);
    if (id) q.set('canal', id);
    else q.delete('canal');
    q.delete('p');
    const s = q.toString();
    return '/calendario' + (s ? `?${s}` : '');
  }

  async function activar(c: { id: string; nombre: string }) {
    if (!(await confirmar(`¿Activar ${c.nombre}? Aparecerá en el calendario y al planear la difusión.`, 'Activar'))) return;
    try {
      const nuevo = await api.cambiarCanal(c.id, { activo: true });
      const i = catalogo.canales.findIndex((x) => x.id === c.id);
      if (i >= 0) catalogo.canales[i] = nuevo;
    } catch (e) {
      fallo(e);
    }
  }

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
  <a class="item" class:activo={enCalendario && !filtro.get('canal')} href="/calendario" onclick={(e) => enlace(e, conCanal(null))}>
    <Icono n="calendario" />Calendario
  </a>

  {#if enCalendario}
    <div class="grupo">Canales</div>
    {#each catalogo.canales as c (c.id)}
      {#if c.activo}
        <a class="item" class:activo={filtro.get('canal') === c.id} href={conCanal(c.id)} onclick={(e) => enlace(e, filtro.get('canal') === c.id ? conCanal(null) : conCanal(c.id))}>
          <Icono n={c.icono} red />{c.nombre}
        </a>
      {:else}
        <button class="item inactivo" title="Activar {c.nombre}" onclick={() => activar(c)}>
          <Icono n={c.icono} red />{c.nombre}<span class="n">pronto</span>
        </button>
      {/if}
    {/each}
  {:else if catalogo.series.length}
    <div class="grupo">Series</div>
    {#each catalogo.series as s (s.id)}
      <a class="item" class:activo={enLista && filtro.get('serie') === s.id} href="/?serie={s.id}" onclick={(e) => enlace(e, `/?serie=${s.id}`)}>
        <Icono n="serie" />{s.nombre}<span class="n">{s.articulos}</span>
      </a>
    {/each}
  {/if}

  {#if !enCalendario && catalogo.etiquetas.length}
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
  .inactivo { display: flex; align-items: center; gap: 10px; padding: 7px 8px; color: var(--faint); border: 0; background: transparent; border-radius: 7px; cursor: pointer; font: inherit; text-align: left; }
  .inactivo:hover { background: var(--surface-2); color: var(--muted); }
  .inactivo .n { margin-left: auto; font-size: 12px; }
  .lateral { overflow-y: auto; }
</style>
