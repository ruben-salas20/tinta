<script lang="ts">
  import { untrack } from 'svelte';
  import { api, cuando, NOMBRE_ESTADO_PUB, quien, type EntradaHistorial, type IdiomaDetalle, type Publicacion, type VarianteBloque } from '../lib/api';
  import { fechaCorta } from '../lib/calendario';
  import { contar, notas as leerNotas, vistazo } from '../lib/critic';
  import { activaBloque, activaSeccion, type ControlEditor } from '../lib/editor.svelte';
  import { canal, confirmar, difusion, fallo, ir, pedirTexto } from '../lib/estado.svelte';
  import Icono from './Icono.svelte';

  let { ctl, idioma, pedidoHistorial }: { ctl: ControlEditor; idioma: IdiomaDetalle; pedidoHistorial: number } = $props();

  const ubic = $derived(ctl.foco ? ctl.ubicar(ctl.foco) : undefined);
  const bloque = $derived(ubic?.bloque);
  const activa = $derived(bloque ? activaBloque(bloque) : undefined);
  const letra = (i: number) => String.fromCharCode(65 + i);
  const rama = $derived(ctl.doc?.rama);
  const articuloId = $derived(ctl.articulo?.id ?? '');

  let comparar = $state<VarianteBloque | null>(null);

  let pubs = $state<Publicacion[]>([]);
  $effect(() => {
    const id = articuloId;
    void difusion.version;
    if (id) api.publicaciones({ articulo: id }).then((x) => (pubs = x)).catch(fallo);
  });
  let historial = $state<EntradaHistorial[] | null>(null);
  let seccionHistorial: HTMLElement | undefined = $state();

  // Propuestas pendientes de la rama: marcas del agente en lo activo y
  // variantes propuestas o en conflicto sin activar.
  const propuestas = $derived.by(() => {
    const r: { bloque: string; seccion: string; texto: string; tipo: 'marca' | 'variante' | 'conflicto'; quien: string }[] = [];
    for (const u of ctl.visibles()) {
      const sec = u.vs.titulo || 'Entrada';
      const a = activaBloque(u.bloque);
      if (a) {
        const c = contar(a.contenido).propuestas;
        if (c) r.push({ bloque: u.bloque.id, seccion: sec, texto: `${c} ${c === 1 ? 'cambio propuesto' : 'cambios propuestos'}: ${vistazo(a.contenido).slice(0, 70)}`, tipo: 'marca', quien: 'IA' });
      }
      for (const v of u.bloque.variantes) {
        if (v.id === a?.id) continue;
        if (v.origen.startsWith('ia:')) r.push({ bloque: u.bloque.id, seccion: sec, texto: vistazo(v.contenido).slice(0, 90), tipo: 'variante', quien: quien(v.origen) });
        else if (v.origen === 'conflicto') r.push({ bloque: u.bloque.id, seccion: sec, texto: vistazo(v.contenido).slice(0, 90), tipo: 'conflicto', quien: 'conflicto' });
      }
    }
    return r;
  });

  // Notas de lo activo, en el orden del artículo.
  const notas = $derived.by(() => {
    const r: { bloque: string; seccion: string; texto: string; quien: string | null }[] = [];
    for (const u of ctl.visibles()) {
      const a = activaBloque(u.bloque);
      if (!a) continue;
      for (const n of leerNotas(a.contenido)) r.push({ bloque: u.bloque.id, seccion: u.vs.titulo || 'Entrada', texto: n.texto, quien: n.ia });
    }
    return r;
  });

  async function cargarHistorial() {
    if (!activa) return (historial = null);
    try {
      await ctl.flush(activa.id);
      historial = await api.historial(activa.id);
    } catch (e) {
      fallo(e);
    }
  }

  $effect(() => {
    // Vuelve a pedir el historial al cambiar de bloque o de variante.
    void activa?.id;
    historial = null;
    cargarHistorial();
  });

  $effect(() => {
    if (pedidoHistorial)
      untrack(() => {
        cargarHistorial();
        seccionHistorial?.scrollIntoView({ behavior: 'smooth', block: 'start' });
      });
  });

  async function activar(v: VarianteBloque) {
    comparar = null;
    await ctl.hacer(() => api.cambiarBloque(bloque!.id, { variante_activa_id: v.id }));
  }

  async function descartar(v: VarianteBloque) {
    if (!(await confirmar('¿Descartar esta variante?', 'Descartar'))) return;
    comparar = null;
    await ctl.hacer(() => api.borrarVarianteBloque(v.id));
  }

  async function nueva() {
    await ctl.flush(activa?.id);
    try {
      const r = await api.forkBloque(bloque!.id);
      ctl.aplicar(r.documento);
      await ctl.enfocar(bloque!.id, 'fin');
    } catch (e) {
      fallo(e);
    }
  }

  async function restaurar(h: EntradaHistorial) {
    try {
      await ctl.flush(activa!.id);
      const g = await api.restaurar(activa!.id, h.id);
      await ctl.aplicarGuardado(g.variante);
      await cargarHistorial();
    } catch (e) {
      fallo(e);
    }
  }

  async function forkRama() {
    const nombre = await pedirTexto('Fork del artículo entero', `${rama?.nombre}-2`, 'Crear la rama', 'Copia el texto tal como se lee ahora, en una rama nueva.');
    if (!nombre) return;
    try {
      await ctl.flush();
      const d = await api.forkRama(rama!.id, nombre);
      await ctl.refrescarArticulo();
      ir(`/a/${articuloId}/${idioma.idioma}/${d.rama.id}`);
    } catch (e) {
      fallo(e);
    }
  }

  async function hacerPrincipal(id: string) {
    try {
      ctl.articulo = await api.cambiarIdioma(idioma.id, { rama_principal_id: id });
    } catch (e) {
      fallo(e);
    }
  }

  async function renombrar(id: string, actual: string) {
    const nombre = await pedirTexto('Renombrar la rama', actual, 'Renombrar');
    if (!nombre) return;
    try {
      await api.renombrarRama(id, nombre);
      await ctl.refrescarArticulo();
    } catch (e) {
      fallo(e);
    }
  }

  async function borrarRama(id: string, nombre: string) {
    if (!(await confirmar(`¿Borrar la rama «${nombre}»?`))) return;
    try {
      await api.borrarRama(id);
      await ctl.refrescarArticulo();
      if (id === rama?.id) ir(`/a/${articuloId}/${idioma.idioma}`);
    } catch (e) {
      fallo(e);
    }
  }

  function dispositivo(d: string) {
    const base = d.replace(/:restaurar$/, '');
    const nombre = { web: 'en el portátil', android: 'en el móvil', mcp: 'por el agente' }[base] ?? base;
    return d.endsWith(':restaurar') ? `antes de restaurar, ${nombre}` : `editado ${nombre}`;
  }
</script>

<aside class="inspector">
  {#if bloque && activa}
    <div>
      <h3><Icono n="variantes" s />Variantes del bloque<span class="n">{bloque.variantes.length}</span></h3>
      {#each bloque.variantes as v, i (v.id)}
        {@const esActiva = v.id === activa.id}
        <div class="tarjeta-v" class:activa={esActiva}>
          <div class="cab">
            <b>{letra(i)}{esActiva ? ' · activa' : ''}</b>
            {#if v.origen.startsWith('ia:')}<span class="ia">IA</span><span>{quien(v.origen)} · propuesta</span>
            {:else if v.origen === 'conflicto'}<span class="ia">!</span><span>conflicto</span>
            {:else}<span>Tú</span>{/if}
            <span class="d">{cuando(v.cambiado)}</span>
          </div>
          <p>{vistazo(v.contenido) || 'Vacío'}</p>
          {#if !esActiva}
            <div class="acc">
              <button class="btn" onclick={() => activar(v)}>Activar</button>
              <button class="btn fantasma" onclick={() => (comparar = v)}>Comparar</button>
              <button class="btn fantasma" onclick={() => descartar(v)}>Descartar</button>
            </div>
          {/if}
        </div>
      {/each}
      <button class="btn fantasma" style="margin-top:8px" onclick={nueva}><Icono n="mas" s />Nueva variante</button>
    </div>
  {:else}
    <div>
      <h3><Icono n="variantes" s />Variantes del bloque</h3>
      <p class="ayuda">Pon el cursor en un párrafo para ver sus variantes y su historial.</p>
    </div>
  {/if}

  <div class="difusion">
    <h3><Icono n="difundir" s />Difusión<span class="n">{pubs.length || ''}</span></h3>
    {#if pubs.length}
      {#each pubs as p (p.id)}
        <a class="pub-d" href="/calendario?fecha={p.fecha}&p={p.id}" onclick={(e) => { e.preventDefault(); ir(`/calendario?fecha=${p.fecha}&p=${p.id}`); }}>
          <Icono n={canal(p.canal_id)?.icono ?? 'x'} red />
          <span class="f">{fechaCorta(p.fecha)}{p.hora ? ` · ${p.hora}` : ''}</span>
          <span class="chip punto e-{p.estado}">{NOMBRE_ESTADO_PUB[p.estado]}</span>
        </a>
      {/each}
    {:else}
      <p class="vacio">Sin planear. «Planear difusión», arriba, lo pone en el calendario.</p>
    {/if}
  </div>

  <div class="ramas">
    <h3><Icono n="rama" s />Ramas del artículo<span class="n">{idioma.ramas.length}</span></h3>
    {#each idioma.ramas as r (r.id)}
      {@const principal = r.id === idioma.rama_principal_id}
      <div class="r" class:sel={r.id === rama?.id}>
        {#if principal}<Icono n="check" s />{:else}<span style="width:14px"></span>{/if}
        <a href="/a/{articuloId}/{idioma.idioma}/{r.id}" onclick={(e) => { e.preventDefault(); ir(`/a/${articuloId}/${idioma.idioma}/${r.id}`); }}>
          {#if r.id === rama?.id}<b>{r.nombre}</b>{:else}{r.nombre}{/if}
        </a>
        <small>{principal ? 'la que se exporta' : `desde ${cuando(r.creado)}`}</small>
        <span class="acc-r">
          {#if !principal}<button class="mini" title="Hacer principal" onclick={() => hacerPrincipal(r.id)}><Icono n="check" s /></button>{/if}
          <button class="mini" title="Renombrar" onclick={() => renombrar(r.id, r.nombre)}><Icono n="tachar" s /></button>
          {#if !principal}<button class="mini" title="Borrar" onclick={() => borrarRama(r.id, r.nombre)}><Icono n="x" s /></button>{/if}
        </span>
      </div>
    {/each}
    <button class="btn fantasma" style="margin-top:4px" onclick={forkRama}><Icono n="mas" s />Fork del artículo entero</button>
  </div>

  <div>
    <h3><Icono n="agente" s />Propuestas pendientes<span class="n">{propuestas.length}</span></h3>
    {#each propuestas as p, i (i)}
      <button class="tarjeta-v prop" onclick={() => ctl.enfocar(p.bloque, 'inicio')}>
        <div class="cab"><span class="ia">{p.tipo === 'conflicto' ? '!' : 'IA'}</span><span>{p.tipo === 'variante' ? `Variante de ${p.quien}` : p.tipo === 'conflicto' ? 'Conflicto' : 'En el texto'} · «{p.seccion}»</span></div>
        <p>{p.texto}</p>
      </button>
    {:else}
      <p class="ayuda">Nada pendiente. Cuando un agente proponga cambios por MCP, saldrán aquí.</p>
    {/each}
  </div>

  <div>
    <h3><Icono n="nota" s />Notas<span class="n">{notas.length}</span></h3>
    {#each notas as nt, i (i)}
      <button class="tarjeta-v prop" onclick={() => ctl.enfocar(nt.bloque, 'inicio')}>
        <div class="cab">{#if nt.quien}<span class="ia">IA</span><span>{nt.quien}</span>{:else}<span>Tú</span>{/if}<span>· «{nt.seccion}»</span></div>
        <p>{nt.texto}</p>
      </button>
    {:else}
      <p class="ayuda">Sin notas. Selecciona texto y pulsa «Nota» para dejar una.</p>
    {/each}
  </div>

  <div bind:this={seccionHistorial}>
    <h3><Icono n="historial" s />Historial del bloque</h3>
    {#if !activa}
      <p class="ayuda">Elige un bloque.</p>
    {:else if historial === null}
      <p class="ayuda">Cargando…</p>
    {:else}
      <div class="linea">
        <div class="ahora"><b>Ahora</b> · {activa.origen === 'tu' ? 'tu versión' : quien(activa.origen)}</div>
        {#each historial as h (h.id)}
          <div class="h">
            <b>{cuando(h.creado)}</b> · {dispositivo(h.dispositivo)}
            <p>{vistazo(h.contenido) || 'Vacío'}</p>
            <button class="btn fantasma mini-btn" onclick={() => restaurar(h)}>Restaurar</button>
          </div>
        {:else}
          <p class="ayuda" style="margin:4px 0 0">Aún no hay versiones anteriores.</p>
        {/each}
      </div>
    {/if}
  </div>
</aside>

{#if comparar && activa}
  <div class="dialogo-velo" role="presentation" onmousedown={(e) => e.target === e.currentTarget && (comparar = null)}>
    <div class="dialogo comparar">
      <h2>Comparar variantes</h2>
      <div class="dos">
        <div><small>Activa</small><p>{vistazo(activa.contenido)}</p></div>
        <div><small>{comparar.origen === 'tu' ? 'Otra tuya' : quien(comparar.origen)} · {cuando(comparar.cambiado)}</small><p>{vistazo(comparar.contenido)}</p></div>
      </div>
      <div class="acc">
        <button class="btn fantasma" onclick={() => (comparar = null)}>Cerrar</button>
        <button class="btn primario" onclick={() => activar(comparar!)}>Activar la otra</button>
      </div>
    </div>
  </div>
{/if}

<style>
  .ayuda { margin: 0; font-size: 12.5px; color: var(--faint); }
  .ramas .r a { text-decoration: none; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .ramas .r .acc-r { display: none; gap: 2px; }
  .ramas .r:hover .acc-r { display: inline-flex; }
  .ramas .r:hover small { display: none; }
  .mini { display: inline-grid; place-items: center; width: 24px; height: 24px; border: 0; background: transparent; border-radius: 6px; cursor: pointer; color: var(--muted); }
  .mini:hover { background: var(--surface-3); color: var(--text); }
  .prop { display: block; width: 100%; text-align: left; background: var(--surface); cursor: pointer; }
  .prop:hover { border-color: var(--line-strong); }
  .linea .h p { margin: 2px 0 0; font: 13px/1.45 var(--serif); color: var(--muted); display: -webkit-box; -webkit-line-clamp: 2; line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
  .mini-btn { height: 24px; padding: 0 6px; font-size: 12px; margin-top: 2px; }
  .linea .h::before { top: 11px; }
  .comparar { width: min(820px, 100%); }
  .dos { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
  .dos div { border: 1px solid var(--line); border-radius: 10px; padding: 12px; }
  .dos small { color: var(--faint); font-size: 12px; }
  .dos p { margin: 6px 0 0; font: 15px/1.6 var(--serif); color: var(--text); }
  .difusion .pub-d { display: flex; align-items: center; gap: 8px; padding: 6px 8px; border-radius: 7px; font-size: 13px; text-decoration: none; color: var(--text); }
  .difusion .pub-d:hover { background: var(--surface-2); }
  .difusion .pub-d .f { flex: 1; text-transform: capitalize; }
  .difusion .vacio { margin: 0; font-size: 12.5px; color: var(--faint); }
</style>
