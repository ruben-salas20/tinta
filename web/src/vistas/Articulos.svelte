<script lang="ts">
  import { api, cuando, ESTADOS, NOMBRE_ESTADO, type Articulo, type Estado } from '../lib/api';
  import { canal, catalogo, fallo, ir, nav, pedirTexto, recargarCatalogo } from '../lib/estado.svelte';
  import { fechaCorta } from '../lib/calendario';
  import Icono from '../componentes/Icono.svelte';
  import Lateral from '../componentes/Lateral.svelte';

  const PLURAL: Record<Estado, string> = { idea: 'Ideas', borrador: 'Borradores', revision: 'Revisión', listo: 'Listos', publicado: 'Publicados' };
  let buscar = $state('');
  let estado = $state<Estado | 'todos'>('todos');
  recargarCatalogo();

  const filtro = $derived(new URLSearchParams(nav.buscar));
  const serie = $derived(filtro.get('serie'));
  const etiqueta = $derived(filtro.get('etiqueta'));

  const principal = (a: Articulo) => a.idiomas.find((i) => i.idioma === 'es') ?? a.idiomas[0];

  const base = $derived(
    catalogo.articulos.filter((a) => {
      if (serie && a.serie?.id !== serie) return false;
      if (etiqueta && !a.etiquetas.includes(etiqueta)) return false;
      if (buscar.trim()) {
        const q = buscar.trim().toLowerCase();
        if (!a.idiomas.some((i) => (i.titulo + ' ' + i.descripcion).toLowerCase().includes(q))) return false;
      }
      return true;
    }),
  );
  const visibles = $derived(base.filter((a) => estado === 'todos' || a.estado === estado));
  const enMarcha = $derived(visibles.filter((a) => a.estado !== 'publicado'));
  const publicados = $derived(visibles.filter((a) => a.estado === 'publicado'));
  const cuenta = (e: Estado) => base.filter((a) => a.estado === e).length;

  const nombreSerie = $derived(catalogo.series.find((s) => s.id === serie)?.nombre);

  async function nuevo() {
    const titulo = await pedirTexto('Nuevo artículo', '', 'Crear', 'El título se puede cambiar después.');
    if (!titulo) return;
    try {
      const a = await api.crearArticulo(titulo);
      await recargarCatalogo();
      ir(`/a/${a.id}`);
    } catch (e) {
      fallo(e);
    }
  }

  function abrir(e: MouseEvent, a: Articulo) {
    e.preventDefault();
    ir(`/a/${a.id}`);
  }
</script>

{#snippet fila(a: Articulo)}
  {@const p = principal(a)}
  <a class="fila" href="/a/{a.id}" onclick={(e) => abrir(e, a)}>
    <div class="t">
      <b>{p?.titulo || 'Sin título'}</b>
      <span>{p?.descripcion || ' '}</span>
      <div class="señales">
        <span class="chip punto e-{a.estado}">{NOMBRE_ESTADO[a.estado]}</span>
        {#if a.senales.ramas > a.idiomas.length}<span><Icono n="rama" s />{a.senales.ramas - a.idiomas.length + 1} ramas</span>{/if}
        {#if a.senales.propuestas}<span><Icono n="agente" s />{a.senales.propuestas} {a.senales.propuestas === 1 ? 'propuesta' : 'propuestas'}</span>{/if}
        {#if a.senales.notas}<span><Icono n="nota" s />{a.senales.notas} {a.senales.notas === 1 ? 'nota' : 'notas'}</span>{/if}
        {#if a.senales.conflictos}<span><Icono n="variantes" s />{a.senales.conflictos} {a.senales.conflictos === 1 ? 'conflicto' : 'conflictos'}</span>{/if}
        {#each a.etiquetas as e}<span class="chip">{e}</span>{/each}
      </div>
    </div>
    <span class="peq">{a.serie ? `${a.serie.nombre} · ${a.serie_parte ?? '—'}` : '—'}</span>
    <div class="idi">
      {#each ['es', 'en'] as l}
        {@const i = a.idiomas.find((x) => x.idioma === l)}
        {#if i}
          <span class:ok={!i.borrador} class:ia={i.traducido_ia} title={i.borrador ? 'Borrador' : 'Se publica'}>{l.toUpperCase()}</span>
        {/if}
      {/each}
    </div>
    <div class="prox">
      {#if a.proxima}<Icono n={canal(a.proxima.canal_id)?.icono ?? 'x'} red /><span title={canal(a.proxima.canal_id)?.nombre}>{fechaCorta(a.proxima.fecha)}</span>{:else}<span class="vacio">Sin planear</span>{/if}
    </div>
    <span class="peq">{cuando(a.cambiado)}</span>
  </a>
{/snippet}

<div class="app">
  <Lateral />
  <div>
    <header class="cabecera">
      <h1>
        Artículos
        {#if nombreSerie}<span class="ruta"> · serie «{nombreSerie}»</span>{/if}
        {#if etiqueta}<span class="ruta"> · etiqueta «{etiqueta}»</span>{/if}
      </h1>
      {#if serie || etiqueta}
        <button class="btn fantasma" onclick={() => ir('/')}><Icono n="x" s />Quitar filtro</button>
      {/if}
      <div class="espacio"></div>
      <label class="buscar"><Icono n="buscar" s /><input bind:value={buscar} placeholder="Buscar en títulos" /></label>
      <button class="btn primario" onclick={nuevo}><Icono n="mas" />Nuevo artículo</button>
    </header>

    <div class="contenido">
      <div class="filtros">
        <div class="pestanas" role="tablist">
          <button role="tab" class:sel={estado === 'todos'} onclick={() => (estado = 'todos')}>Todos<small>{base.length}</small></button>
          {#each ESTADOS as e}
            <button role="tab" class:sel={estado === e} onclick={() => (estado = e)}>{PLURAL[e]}<small>{cuenta(e)}</small></button>
          {/each}
        </div>
      </div>

      {#if !catalogo.cargado}
        <p class="vacio-centro">Cargando…</p>
      {:else if !visibles.length}
        <div class="vacio-centro">
          <p>{catalogo.articulos.length ? 'Ningún artículo coincide.' : 'Aún no hay artículos.'}</p>
          {#if !catalogo.articulos.length}<button class="btn primario" onclick={nuevo}><Icono n="mas" />Escribir el primero</button>{/if}
        </div>
      {:else}
        {#if enMarcha.length}
          <div class="grupo-t">En marcha <span class="chip">{enMarcha.length}</span></div>
          <div class="lista">
            <div class="fila cab"><span>Artículo</span><span>Serie</span><span>Idiomas</span><span>Próxima publicación</span><span>Cambiado</span></div>
            {#each enMarcha as a (a.id)}{@render fila(a)}{/each}
          </div>
        {/if}
        {#if publicados.length}
          <div class="grupo-t">Publicados <span class="chip">{publicados.length}</span></div>
          <div class="lista">
            {#each publicados as a (a.id)}{@render fila(a)}{/each}
          </div>
        {/if}
      {/if}
    </div>
  </div>
</div>

<style>
  .buscar input { border: 0; background: transparent; outline: none; flex: 1; min-width: 0; color: var(--text); font-size: 13px; }
  .pestanas button { padding: 5px 11px; border-radius: 7px; font-size: 13px; color: var(--muted); border: 0; background: transparent; cursor: pointer; }
  .pestanas button.sel { background: var(--surface); color: var(--text); box-shadow: 0 1px 2px rgb(0 0 0 / 0.08); font-weight: 500; }
  .pestanas button small { color: var(--faint); margin-left: 4px; }
  .vacio-centro .btn { margin-top: 8px; }
</style>
