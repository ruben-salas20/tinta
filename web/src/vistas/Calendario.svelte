<script lang="ts">
  import { api, NOMBRE_ESTADO_PUB, ESTADOS_PUB, type Publicacion } from '../lib/api';
  import {
    enHora,
    fechaCorta,
    fechaLarga,
    horaEntera,
    nombreMes,
    nombreSemana,
    primeroDelMes,
    rangoHoras,
    semanaDe,
    semanasDelMes,
    sumarDias,
    sumarMeses,
  } from '../lib/calendario';
  import { canal, catalogo, difusion, fallo, nav, recargarCatalogo } from '../lib/estado.svelte';
  import { ordenarIdeas, resumenIdea, tituloDe } from '../lib/ideas';
  import Icono from '../componentes/Icono.svelte';
  import Lateral from '../componentes/Lateral.svelte';
  import DetallePublicacion from '../componentes/DetallePublicacion.svelte';

  type Vista = 'mes' | 'semana' | 'lista' | 'ideas';
  const DIAS = ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'];

  const inicial = new URLSearchParams(nav.buscar);
  let vista = $state<Vista>((['mes', 'semana', 'lista', 'ideas'] as const).find((v) => v === inicial.get('vista')) ?? 'mes');
  let fecha = $state(inicial.get('fecha') ?? '');
  let elegida = $state<string | null>(inicial.get('p'));
  let pubs = $state<Publicacion[]>([]);
  let ideas = $state<Publicacion[]>([]);
  let arrastrando = $state<string | null>(null);
  let encima = $state<string | null>(null);

  if (!catalogo.cargado) recargarCatalogo();
  const ref = $derived(fecha || catalogo.hoy);
  const filtroCanal = $derived(new URLSearchParams(nav.buscar).get('canal'));

  // La vista, la fecha y la publicación abierta viven en la dirección, para
  // que recargar o volver atrás deje el calendario donde estaba.
  $effect(() => {
    const q = new URLSearchParams();
    if (vista !== 'mes') q.set('vista', vista);
    if (fecha) q.set('fecha', fecha);
    if (filtroCanal) q.set('canal', filtroCanal);
    if (elegida) q.set('p', elegida);
    const s = q.toString() ? `?${q}` : '';
    if (s !== nav.buscar) {
      history.replaceState(null, '', `/calendario${s}`);
      nav.buscar = s;
    }
  });

  const semanas = $derived(semanasDelMes(ref));
  const dias = $derived(semanaDe(ref));
  const rango = $derived.by(() => {
    if (vista === 'mes') return { desde: semanas[0][0], hasta: semanas.at(-1)![6] };
    if (vista === 'semana') return { desde: dias[0], hasta: dias[6] };
    const desde = primeroDelMes(ref);
    return { desde, hasta: sumarDias(primeroDelMes(sumarMeses(desde, 1)), -1) };
  });

  async function cargar() {
    try {
      if (vista === 'ideas') ideas = ordenarIdeas(await api.publicaciones({ sin_fecha: true, canal: filtroCanal ?? undefined }));
      else pubs = await api.publicaciones({ ...rango, canal: filtroCanal ?? undefined });
    } catch (e) {
      fallo(e);
    }
  }
  $effect(() => {
    void vista;
    void rango.desde;
    void rango.hasta;
    void filtroCanal;
    void difusion.version;
    cargar();
  });

  const porDia = $derived.by(() => {
    const m = new Map<string, Publicacion[]>();
    for (const p of pubs) if (p.fecha) m.set(p.fecha, [...(m.get(p.fecha) ?? []), p]);
    return m;
  });

  // Las filas de la semana: de la primera a la última hora con publicación.
  const horas = $derived(rangoHoras(pubs.map((p) => horaEntera(p.hora)).filter((h): h is number => h !== null)));

  const titulo = $derived(vista === 'ideas' ? 'Ideas sin fecha' : vista === 'semana' ? nombreSemana(ref) : nombreMes(ref));
  const mesRef = $derived(ref.slice(0, 7));

  function mover(n: number) {
    fecha = vista === 'semana' ? sumarDias(ref, 7 * n) : sumarMeses(primeroDelMes(ref), n);
  }

  const formato = (p: Publicacion) => canal(p.canal_id)?.formatos.find((f) => f.id === p.formato)?.nombre ?? p.formato ?? '';

  async function crear(dia: string) {
    const c = canal(filtroCanal ?? '') ?? catalogo.canales.find((c) => c.activo);
    if (!c) return;
    try {
      const p = await api.crearPublicacion({ canal_id: c.id, formato: c.formatos[0].id, fecha: dia });
      pubs = [...pubs, p];
      elegida = p.id;
    } catch (e) {
      fallo(e);
    }
  }

  // Una idea nace sin fecha. Con un canal filtrado nace en ese canal, para que se vea en la lista.
  async function nuevaIdea() {
    const c = canal(filtroCanal ?? '');
    try {
      const p = await api.crearPublicacion(c ? { estado: 'idea', canal_id: c.id, formato: c.formatos[0]?.id } : { estado: 'idea' });
      ideas = [p, ...ideas];
      elegida = p.id;
    } catch (e) {
      fallo(e);
    }
  }

  async function soltar(dia: string) {
    const id = arrastrando;
    arrastrando = encima = null;
    const p = pubs.find((x) => x.id === id);
    if (!p || p.fecha === dia) return;
    const antes = p.fecha;
    p.fecha = dia;
    try {
      const nueva = await api.cambiarPublicacion(p.id, { fecha: dia });
      actualizada(nueva);
      // La del portfolio mueve también la fecha del artículo.
      if (nueva.canal_id === 'portfolio' && nueva.articulo) recargarCatalogo();
    } catch (e) {
      p.fecha = antes;
      fallo(e);
    }
  }

  // Si una publicación cambia de fecha o de canal, entra o sale de la vista que se ve.
  function enVista(p: Publicacion): boolean {
    if (filtroCanal && p.canal_id !== filtroCanal) return false;
    if (vista === 'ideas') return p.fecha === null;
    return p.fecha !== null && p.fecha >= rango.desde && p.fecha <= rango.hasta;
  }

  function actualizada(p: Publicacion) {
    const dentro = enVista(p);
    const poner = (lista: Publicacion[]) => {
      if (!lista.some((x) => x.id === p.id)) return dentro ? [...lista, p] : lista;
      return dentro ? lista.map((x) => (x.id === p.id ? p : x)) : lista.filter((x) => x.id !== p.id);
    };
    if (vista === 'ideas') ideas = ordenarIdeas(poner(ideas));
    else pubs = poner(pubs);
  }

  function borrada(id: string) {
    pubs = pubs.filter((p) => p.id !== id);
    ideas = ideas.filter((p) => p.id !== id);
    elegida = null;
  }

  function abrir(e: Event, p: Publicacion) {
    e.stopPropagation();
    elegida = elegida === p.id ? null : p.id;
  }
</script>

{#snippet pub(p: Publicacion, conHora = false)}
  {@const c = canal(p.canal_id)}
  <button
    class="pub {p.estado}"
    class:sel={elegida === p.id}
    draggable="true"
    title="{c?.nombre ?? 'Sin canal'} · {formato(p)} · {NOMBRE_ESTADO_PUB[p.estado]}"
    ondragstart={(e) => { arrastrando = p.id; e.dataTransfer?.setData('text/plain', p.id); }}
    ondragend={() => (arrastrando = encima = null)}
    onclick={(e) => abrir(e, p)}
  >
    <Icono n={c?.icono ?? 'x'} red />
    {#if conHora && p.hora}<span class="h">{p.hora}</span>{/if}
    <span class="t">{tituloDe(p)}</span>
  </button>
{/snippet}

{#snippet dia(d: string, extra = '')}
  <div
    class="dia {extra}"
    class:hoy={d === catalogo.hoy}
    class:pasado={d < catalogo.hoy}
    class:fuera={vista === 'mes' && d.slice(0, 7) !== mesRef}
    class:encima={encima === d}
    role="gridcell"
    tabindex="-1"
    title="Clic para crear una publicación este día"
    onclick={() => crear(d)}
    onkeydown={(e) => e.key === 'Enter' && crear(d)}
    ondragover={(e) => { e.preventDefault(); encima = d; }}
    ondragleave={() => encima === d && (encima = null)}
    ondrop={(e) => { e.preventDefault(); soltar(d); }}
  >
    <span class="num">{d.slice(8) === '01' || d === semanas[0][0] ? fechaCorta(d).split(' ').slice(1).join(' ') : Number(d.slice(8))}</span>
    {#each porDia.get(d) ?? [] as p (p.id)}
      {@render pub(p)}
    {/each}
  </div>
{/snippet}

{#snippet celda(d: string, h: number | null)}
  <div
    class="celda {d < catalogo.hoy ? 'pasado' : ''}"
    class:encima={encima === d}
    role="gridcell"
    tabindex="-1"
    title="Clic para crear una publicación este día"
    onclick={() => crear(d)}
    onkeydown={(e) => e.key === 'Enter' && crear(d)}
    ondragover={(e) => { e.preventDefault(); encima = d; }}
    ondragleave={() => encima === d && (encima = null)}
    ondrop={(e) => { e.preventDefault(); soltar(d); }}
  >
    {#each enHora(porDia.get(d) ?? [], h) as p (p.id)}
      {@const c = canal(p.canal_id)}
      <button
        class="pub {p.estado}"
        class:sel={elegida === p.id}
        draggable="true"
        ondragstart={(e) => { arrastrando = p.id; e.dataTransfer?.setData('text/plain', p.id); }}
        ondragend={() => (arrastrando = encima = null)}
        onclick={(e) => abrir(e, p)}
      >
        <span class="linea-pub" title="{c?.nombre ?? 'Sin canal'} · {formato(p)}"><Icono n={c?.icono ?? 'x'} red />{#if p.hora}<span class="h">{p.hora}</span>{/if}</span>
        <span class="t">{tituloDe(p)}</span>
      </button>
    {/each}
  </div>
{/snippet}

<div class="app cal">
  <Lateral />
  <div>
    <header class="cabecera">
      <h1>Calendario{#if filtroCanal}<span class="ruta"> · {canal(filtroCanal)?.nombre}</span>{/if}</h1>
      <div class="espacio"></div>
      {#if vista === 'ideas'}
        <button class="btn primario" onclick={nuevaIdea}><Icono n="mas" />Nueva idea</button>
      {:else}
        <button class="btn primario" onclick={() => crear(ref < catalogo.hoy ? catalogo.hoy : ref)}><Icono n="mas" />Nueva publicación</button>
      {/if}
    </header>

    <div class="cuerpo" class:con-detalle={!!elegida}>
      <div class="zona">
        <div class="barra">
          {#if vista !== 'ideas'}
            <button class="btn" style="padding:0 7px" aria-label="Anterior" onclick={() => mover(-1)}><Icono n="izq" /></button>
            <button class="btn" onclick={() => (fecha = '')}>Hoy</button>
            <button class="btn" style="padding:0 7px" aria-label="Siguiente" onclick={() => mover(1)}><Icono n="der" /></button>
          {/if}
          <h2>{titulo}</h2>
          <div class="pestanas" role="tablist">
            <button role="tab" class:sel={vista === 'mes'} onclick={() => (vista = 'mes')}>Mes</button>
            <button role="tab" class:sel={vista === 'semana'} onclick={() => (vista = 'semana')}>Semana</button>
            <button role="tab" class:sel={vista === 'lista'} onclick={() => (vista = 'lista')}>Lista</button>
            <button role="tab" class:sel={vista === 'ideas'} onclick={() => (vista = 'ideas')}>Ideas</button>
          </div>
        </div>

        {#if vista === 'mes'}
          <div class="cab-semana">{#each DIAS as d}<span>{d}</span>{/each}</div>
          <div class="mes" role="grid">
            {#each semanas as s}{#each s as d (d)}{@render dia(d)}{/each}{/each}
          </div>
        {:else if vista === 'semana'}
          <div class="semana-horas" role="grid">
            <span></span>
            {#each dias as d (d)}
              <span class="cab-dia"><span class="num">{Number(d.slice(8))}</span><small>{fechaLarga(d).split(',')[0]}</small></span>
            {/each}
            <span class="franja">Sin hora</span>
            {#each dias as d (d)}{@render celda(d, null)}{/each}
            {#each horas as h (h)}
              <span class="franja">{String(h).padStart(2, '0')}:00</span>
              {#each dias as d (d)}{@render celda(d, h)}{/each}
            {/each}
          </div>
        {:else if vista === 'ideas'}
          {#if !ideas.length}
            <div class="vacio-centro"><p>No hay ideas sin fecha. Crea una con «Nueva idea».</p></div>
          {:else}
            <div class="lista-ideas">
              {#each ideas as p (p.id)}
                {@const r = resumenIdea(p, catalogo.canales)}
                <button class="pub {p.estado}" class:sel={elegida === p.id} onclick={(e) => abrir(e, p)}>
                  <Icono n={canal(p.canal_id)?.icono ?? 'x'} red />
                  <span class="t">{r.titulo}</span>
                  <span class="formato">{r.canal}</span>
                  <span class="chip punto e-{p.estado}">{r.estado}</span>
                </button>
              {/each}
            </div>
          {/if}
        {:else}
          {@const conPubs = [...porDia.keys()].sort()}
          {#if !conPubs.length}
            <div class="vacio-centro"><p>No hay publicaciones en {nombreMes(ref).toLowerCase()}.</p></div>
          {:else}
            <div class="lista-dias">
              {#each conPubs as d (d)}
                <section>
                  <h3>{fechaLarga(d)}{#if d === catalogo.hoy}<span class="chip">hoy</span>{/if}</h3>
                  <div class="items">
                    {#each porDia.get(d) ?? [] as p (p.id)}
                      {@const c = canal(p.canal_id)}
                      <button class="pub {p.estado}" class:sel={elegida === p.id} onclick={(e) => abrir(e, p)}>
                        <Icono n={c?.icono ?? 'x'} red />
                        <span class="h">{p.hora ?? '—'}</span>
                        <span class="t">{tituloDe(p)}</span>
                        <span class="formato">{c ? `${c.nombre} · ${formato(p)}` : 'Sin canal'}</span>
                        <span class="chip punto e-{p.estado}">{NOMBRE_ESTADO_PUB[p.estado]}</span>
                      </button>
                    {/each}
                  </div>
                </section>
              {/each}
            </div>
          {/if}
        {/if}

        <div class="leyenda">
          {#each ESTADOS_PUB as e}<div class="pub {e}"><span class="t">{NOMBRE_ESTADO_PUB[e]}</span></div>{/each}
          {#if vista === 'mes' || vista === 'semana'}<span>Arrastra una publicación a otro día para moverla. Clic en un hueco para crear una.</span>{/if}
        </div>
      </div>

      {#if elegida}
        {#key elegida}
          <DetallePublicacion id={elegida} alCambiar={actualizada} alBorrar={borrada} alCerrar={() => (elegida = null)} />
        {/key}
      {/if}
    </div>
  </div>
</div>
