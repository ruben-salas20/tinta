<script lang="ts">
  import { onMount } from 'svelte';
  import { api, ESTADOS, NOMBRE_ESTADO, type Estado } from '../lib/api';
  import { vistazo } from '../lib/critic';
  import { activaBloque, ControlEditor } from '../lib/editor.svelte';
  import { catalogo, fallo, ir, pedirTexto, recargarCatalogo } from '../lib/estado.svelte';
  import Icono from '../componentes/Icono.svelte';
  import Inspector from '../componentes/Inspector.svelte';
  import Lateral from '../componentes/Lateral.svelte';
  import Seccion from '../componentes/Seccion.svelte';
  import Exportar from './Exportar.svelte';

  let { articuloId, idiomaUrl, ramaUrl }: { articuloId: string; idiomaUrl: string | null; ramaUrl: string | null } = $props();

  const ctl = new ControlEditor();
  let error = $state<string | null>(null);
  let exportar = $state(false);
  let menu = $state<'estado' | 'rama' | 'serie' | 'en' | null>(null);
  let pedidoHistorial = $state(0);

  onMount(() => {
    ctl.cargarArticulo(articuloId).catch((e) => (error = e.message));
    const salir = (e: BeforeUnloadEvent) => {
      if (ctl.guardando > 0) e.preventDefault();
      ctl.flush();
    };
    window.addEventListener('beforeunload', salir);
    const cerrarMenus = (e: MouseEvent) => {
      if (!(e.target as HTMLElement).closest('.desplegable')) menu = null;
    };
    window.addEventListener('mousedown', cerrarMenus);
    return () => {
      ctl.flush();
      window.removeEventListener('beforeunload', salir);
      window.removeEventListener('mousedown', cerrarMenus);
    };
  });

  const a = $derived(ctl.articulo);
  const idioma = $derived(a ? (a.idiomas.find((i) => i.idioma === (idiomaUrl ?? 'es')) ?? a.idiomas[0]) : null);
  const ramaId = $derived(ramaUrl && idioma?.ramas.some((r) => r.id === ramaUrl) ? ramaUrl : idioma?.rama_principal_id);
  const rama = $derived(idioma?.ramas.find((r) => r.id === ramaId));
  const tieneEn = $derived(!!a?.idiomas.some((i) => i.idioma === 'en'));

  $effect(() => {
    const id = ramaId;
    if (id && id !== ctl.doc?.rama.id) {
      ctl.flush().then(() => ctl.cargarRama(id)).catch((e) => (error = e.message));
    }
  });

  // Título y entradilla: se editan aquí y se guardan con espera.
  let titulo = $state('');
  let descripcion = $state('');
  let idiomaMostrado = '';
  $effect(() => {
    if (idioma && idioma.id !== idiomaMostrado) {
      idiomaMostrado = idioma.id;
      titulo = idioma.titulo;
      descripcion = idioma.descripcion;
    }
  });
  let espera: ReturnType<typeof setTimeout> | null = null;
  function guardarIdioma() {
    if (espera) clearTimeout(espera);
    const id = idioma!.id;
    const c = { titulo, descripcion };
    espera = setTimeout(async () => {
      ctl.guardando++;
      try {
        ctl.articulo = await api.cambiarIdioma(id, c);
        ctl.guardadoEn = new Date();
      } catch (e) {
        fallo(e);
      } finally {
        ctl.guardando--;
      }
    }, 700);
  }

  async function cambiarIdiomaCampo(c: { borrador?: boolean; traducido_ia?: boolean }) {
    try {
      ctl.articulo = await api.cambiarIdioma(idioma!.id, c);
    } catch (e) {
      fallo(e);
    }
  }

  async function cambiarArticulo(c: Record<string, unknown>) {
    try {
      ctl.articulo = await api.cambiarArticulo(a!.id, c);
      recargarCatalogo();
    } catch (e) {
      fallo(e);
    }
  }

  async function estado(e: Estado) {
    menu = null;
    await cambiarArticulo({ estado: e });
  }

  async function quitarEtiqueta(e: string) {
    await cambiarArticulo({ etiquetas: a!.etiquetas.filter((x) => x !== e) });
  }
  async function anadirEtiqueta() {
    const e = await pedirTexto('Añadir etiqueta', '', 'Añadir', catalogo.etiquetas.length ? `Ya usas: ${catalogo.etiquetas.map((x) => x.nombre).join(', ')}` : undefined);
    if (e) await cambiarArticulo({ etiquetas: [...a!.etiquetas, ...e.split(',').map((x) => x.trim())] });
  }

  async function elegirSerie(id: string | null) {
    menu = null;
    await cambiarArticulo({ serie_id: id, serie_parte: id ? (a!.serie_parte ?? 1) : null });
  }
  async function nuevaSerie() {
    menu = null;
    const nombre = await pedirTexto('Nueva serie', '', 'Crear', 'Un grupo ordenado de artículos, como «Montando ai-lab».');
    if (!nombre) return;
    try {
      const s = await api.crearSerie(nombre);
      await cambiarArticulo({ serie_id: s.id, serie_parte: 1 });
    } catch (e) {
      fallo(e);
    }
  }

  let slug = $state('');
  $effect(() => {
    if (a) slug = a.slug;
  });

  function verIdioma(l: string) {
    menu = null;
    ir(`/a/${articuloId}/${l}`);
  }
  async function crearEn(copiar: boolean) {
    menu = null;
    try {
      await ctl.flush();
      ctl.articulo = await api.crearIdioma(a!.id, 'en', copiar ? 'es' : null);
      ir(`/a/${articuloId}/en`);
    } catch (e) {
      fallo(e);
    }
  }

  async function nuevaSeccion() {
    const ultima = ctl.doc!.secciones[ctl.doc!.secciones.length - 1];
    await ctl.hacer(() => api.crearSeccion(ctl.doc!.rama.id, 'Nueva sección', { antes: ultima?.id ?? null }), false);
  }

  async function abrirExportar() {
    await ctl.flush();
    exportar = true;
  }

  const palabras = $derived(
    ctl
      .visibles()
      .map((u) => vistazo(activaBloque(u.bloque)?.contenido ?? ''))
      .join(' ')
      .split(/\s+/)
      .filter(Boolean).length,
  );
  const otraLengua = $derived(a?.idiomas.find((i) => i.idioma !== idioma?.idioma));
  const estadoGuardado = $derived(
    ctl.guardando > 0 ? 'Guardando…' : ctl.errorGuardado ? 'Sin guardar' : ctl.guardadoEn ? 'Guardado' : 'Al día',
  );
</script>

<div class="app editor">
  <Lateral />
  <div>
    <header class="cabecera">
      <a class="btn fantasma" href="/" style="padding:0 6px" aria-label="Volver a artículos" onclick={(e) => { e.preventDefault(); ir('/'); }}><Icono n="izq" /></a>
      <h1><span class="ruta">Artículos /</span> {titulo || 'Sin título'}</h1>
      {#if a}
        <span class="desplegable">
          <button class="chip punto e-{a.estado}" onclick={() => (menu = menu === 'estado' ? null : 'estado')}>{NOMBRE_ESTADO[a.estado]}</button>
          {#if menu === 'estado'}
            <div class="menu" role="menu">
              {#each ESTADOS as e}
                <button class:sel={a.estado === e} onclick={() => estado(e)}><span class="chip punto e-{e}">{NOMBRE_ESTADO[e]}</span></button>
              {/each}
            </div>
          {/if}
        </span>
      {/if}
      <div class="espacio"></div>
      {#if a && idioma}
        <span class="desplegable">
          <div class="idiomas">
            <button class:sel={idioma.idioma === 'es'} onclick={() => verIdioma('es')}>ES</button>
            <button class:sel={idioma.idioma === 'en'} onclick={() => (tieneEn ? verIdioma('en') : (menu = menu === 'en' ? null : 'en'))}>EN</button>
          </div>
          {#if menu === 'en'}
            <div class="menu der" role="menu">
              <div class="etq">Versión en inglés</div>
              <button onclick={() => crearEn(true)}>Empezar copiando el español</button>
              <button onclick={() => crearEn(false)}>Empezar vacía</button>
            </div>
          {/if}
        </span>
        <span class="desplegable">
          <button class="btn" onclick={() => (menu = menu === 'rama' ? null : 'rama')}><Icono n="rama" />{rama?.nombre ?? '…'}<Icono n="abajo" s /></button>
          {#if menu === 'rama'}
            <div class="menu der" role="menu">
              <div class="etq">Ramas en {idioma.idioma.toUpperCase()}</div>
              {#each idioma.ramas as r (r.id)}
                <button class:sel={r.id === rama?.id} onclick={() => { menu = null; ir(`/a/${articuloId}/${idioma.idioma}/${r.id}`); }}>
                  {r.nombre}<small>{r.id === idioma.rama_principal_id ? 'principal' : ''}</small>
                </button>
              {/each}
            </div>
          {/if}
        </span>
        <button class="btn" onclick={abrirExportar}><Icono n="copiar" />Copiar para X</button>
        <button class="btn primario" onclick={abrirExportar}><Icono n="descargar" />Exportar</button>
      {/if}
    </header>

    {#if error}
      <div class="vacio-centro"><p>{error}</p><button class="btn" onclick={() => ir('/')}>Volver a artículos</button></div>
    {:else if !a || !idioma || !ctl.doc}
      <p class="vacio-centro">Cargando…</p>
    {:else}
      <div class="cuerpo">
        <div class="hoja">
          <article class="papel">
            <div class="meta">
              <span class="desplegable">
                <button class="chip" onclick={() => (menu = menu === 'serie' ? null : 'serie')}>
                  <Icono n="serie" s />{a.serie ? `${a.serie.nombre} · parte ${a.serie_parte ?? '—'}` : 'Sin serie'}
                </button>
                {#if menu === 'serie'}
                  <div class="menu" role="menu">
                    <div class="etq">Serie</div>
                    {#each catalogo.series as s (s.id)}
                      <button class:sel={a.serie?.id === s.id} onclick={() => elegirSerie(s.id)}>{s.nombre}<small>{s.articulos}</small></button>
                    {/each}
                    <button onclick={nuevaSerie}><Icono n="mas" s />Nueva serie…</button>
                    {#if a.serie}
                      <hr />
                      <label class="parte">Parte <input class="campo" type="number" min="1" value={a.serie_parte ?? 1} onchange={(e) => cambiarArticulo({ serie_parte: Number(e.currentTarget.value) || null })} /></label>
                      <button onclick={() => elegirSerie(null)}>Quitar de la serie</button>
                    {/if}
                  </div>
                {/if}
              </span>
              {#each a.etiquetas as e (e)}
                <button class="chip" title="Quitar etiqueta" onclick={() => quitarEtiqueta(e)}>{e}<Icono n="x" s /></button>
              {/each}
              <button class="chip" style="border-style:dashed" onclick={anadirEtiqueta}>+ etiqueta</button>
            </div>

            <textarea class="titulo" rows="1" bind:value={titulo} oninput={guardarIdioma} placeholder="Título" aria-label="Título"></textarea>
            <textarea class="entradilla" rows="1" bind:value={descripcion} oninput={guardarIdioma} placeholder="Una línea para la lista, el RSS y los buscadores" aria-label="Descripción"></textarea>

            <details class="detalles">
              <summary>Detalles de publicación</summary>
              <div class="rejilla">
                <label>Slug <input class="campo" bind:value={slug} onchange={() => slug !== a.slug && cambiarArticulo({ slug })} /></label>
                <label>Fecha <input class="campo" type="date" value={a.fecha ?? ''} onchange={(e) => cambiarArticulo({ fecha: e.currentTarget.value || null })} /></label>
                <label class="caja"><input type="checkbox" checked={idioma.borrador} onchange={(e) => cambiarIdiomaCampo({ borrador: e.currentTarget.checked })} /> Borrador: no se publica</label>
                <label class="caja"><input type="checkbox" checked={idioma.traducido_ia} onchange={(e) => cambiarIdiomaCampo({ traducido_ia: e.currentTarget.checked })} /> Traducido con IA</label>
              </div>
              {#if a.estado === 'publicado'}<p class="aviso-slug">Este artículo ya está publicado: cambiar el slug rompe su dirección en el portfolio.</p>{/if}
            </details>

            {#each ctl.doc.secciones as s, i (s.id)}
              <Seccion seccion={s} {ctl} indice={i} total={ctl.doc.secciones.length} alHistorial={() => pedidoHistorial++} />
            {/each}
            <button class="btn fantasma nueva-seccion" onclick={nuevaSeccion}><Icono n="mas" s />Nueva sección</button>
          </article>
        </div>
        <Inspector {ctl} {idioma} {pedidoHistorial} />
      </div>
      <div class="estado">
        <span style="display:flex;gap:6px;align-items:center"><Icono n={ctl.guardando ? 'historial' : 'check'} s />{estadoGuardado}</span>
        <span>{palabras} palabras · {Math.max(1, Math.round(palabras / 220))} min de lectura</span>
        {#if otraLengua}<span>{otraLengua.idioma.toUpperCase()}: {otraLengua.borrador ? 'borrador' : 'se publica'}{otraLengua.traducido_ia ? ' · traducido con IA' : ''}</span>{/if}
        <span class="espacio"></span>
        <span><span class="kbd">/</span> insertar <span class="kbd">⇧ Enter</span> salto de línea</span>
      </div>
    {/if}
  </div>
</div>

{#if exportar && a && idioma}
  <Exportar articulo={a} idioma={idioma.idioma} alCerrar={() => (exportar = false)} />
{/if}

<style>
  .titulo, .entradilla { display: block; width: 100%; border: 0; background: transparent; outline: none; resize: none; padding: 0; color: var(--text); field-sizing: content; }
  .entradilla { color: var(--muted); margin-bottom: 18px; }
  .idiomas button { display: grid; place-items: center; padding: 0 10px; font-size: 12px; font-weight: 600; color: var(--muted); border: 0; background: transparent; cursor: pointer; }
  .idiomas button.sel { background: var(--accent); color: var(--accent-text); }
  .detalles { margin: 0 0 8px; font: 13px var(--sans); color: var(--muted); }
  .detalles summary { cursor: pointer; color: var(--faint); font-size: 12px; width: fit-content; }
  .detalles .rejilla { display: grid; grid-template-columns: 1fr 1fr; gap: 10px 16px; margin-top: 10px; padding: 12px; border: 1px solid var(--line); border-radius: 10px; background: var(--surface); }
  .detalles label { display: flex; flex-direction: column; gap: 4px; font-size: 12px; }
  .detalles label.caja { flex-direction: row; align-items: center; gap: 8px; font-size: 13px; color: var(--text); }
  .aviso-slug { font-size: 12px; margin: 6px 0 0; }
  .parte { display: flex; align-items: center; gap: 8px; padding: 6px 8px; font-size: 13px; }
  .parte .campo { width: 64px; height: 28px; }
  .nueva-seccion { margin-top: 36px; color: var(--muted); }
  .meta .chip :global(svg) { opacity: 0.7; }
</style>
