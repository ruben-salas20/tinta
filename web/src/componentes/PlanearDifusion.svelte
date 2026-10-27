<script lang="ts">
  import { untrack } from 'svelte';
  import { api, type Articulo, type Publicacion } from '../lib/api';
  import { fechaCorta, sumarDias } from '../lib/calendario';
  import { canal, catalogo, difusion, fallo, ir, recargarCatalogo } from '../lib/estado.svelte';
  import Icono from './Icono.svelte';

  let { articulo, alCerrar, alPlanear }: { articulo: Articulo; alCerrar: () => void; alPlanear: () => void } = $props();
  // La ventana se abre para un artículo y se cierra: basta su valor inicial.
  const art = untrack(() => articulo);

  interface Fila {
    usar: boolean;
    canal_id: string;
    formato: string;
    fecha: string;
    hora: string;
  }

  // Por defecto: el portfolio el primer día, X al día siguiente por la tarde y
  // LinkedIn dos días después por la mañana. El resto, sin marcar.
  const PROPUESTA: Record<string, { usar: boolean; formato: string; dias: number; hora: string }> = {
    portfolio: { usar: true, formato: 'articulo', dias: 0, hora: '' },
    x: { usar: true, formato: 'hilo', dias: 1, hora: '18:00' },
    linkedin: { usar: true, formato: 'post', dias: 3, hora: '09:00' },
    instagram: { usar: false, formato: 'carrusel', dias: 5, hora: '12:00' },
  };

  const inicio = art.fecha && art.fecha >= catalogo.hoy ? art.fecha : sumarDias(catalogo.hoy, 1);
  let filas = $state<Fila[]>(
    catalogo.canales
      .filter((c) => c.activo)
      .map((c, i) => {
        const p = PROPUESTA[c.id];
        const formato = c.formatos.some((f) => f.id === p?.formato) ? p!.formato : c.formatos[0].id;
        return { usar: p?.usar ?? false, canal_id: c.id, formato, fecha: sumarDias(inicio, p?.dias ?? i + 2), hora: p?.hora ?? '' };
      }),
  );
  let existentes = $state<Publicacion[]>([]);
  let hechas = $state<Publicacion[] | null>(null);
  let enviando = $state(false);

  api.publicaciones({ articulo: art.id }).then((x) => (existentes = x)).catch(fallo);

  const elegidas = $derived(filas.filter((f) => f.usar));
  const titulo = (art.idiomas.find((i) => i.idioma === 'es') ?? art.idiomas[0])?.titulo || 'Sin título';

  async function planear() {
    enviando = true;
    try {
      hechas = await api.difusion(
        art.id,
        elegidas.map((f) => ({ canal_id: f.canal_id, formato: f.formato, fecha: f.fecha || null, hora: (f.fecha && f.hora) || null })),
      );
      difusion.version++;
      recargarCatalogo();
      alPlanear();
    } catch (e) {
      fallo(e);
    } finally {
      enviando = false;
    }
  }
</script>

<svelte:window onkeydown={(e) => e.key === 'Escape' && alCerrar()} />

<div class="velo" role="presentation" onmousedown={(e) => e.target === e.currentTarget && alCerrar()}>
  <div class="ventana difusion" role="dialog" aria-label="Planear difusión">
    <div class="v-cab">
      <b>Planear difusión</b><span>{titulo}</span>
      <span style="flex:1"></span>
      <button class="btn fantasma" style="padding:0 6px" aria-label="Cerrar" onclick={alCerrar}><Icono n="x" /></button>
    </div>

    {#if hechas}
      <div class="col">
        <h3><Icono n="check" />{hechas.length === 1 ? 'Una publicación nueva' : `${hechas.length} publicaciones nuevas`} en el calendario</h3>
        <ul class="hechas">
          {#each hechas as p (p.id)}
            <li><Icono n={canal(p.canal_id)?.icono ?? 'x'} red />{p.fecha ? fechaCorta(p.fecha) : 'Sin fecha'}{p.hora ? ` · ${p.hora}` : ''} · {canal(p.canal_id)?.nombre}</li>
          {/each}
        </ul>
        <p>Nacen como idea, con la descripción del artículo como primer borrador del texto.</p>
        <div class="v-pie">
          <button class="btn" onclick={alCerrar}>Seguir escribiendo</button>
          <span class="espacio"></span>
          <button class="btn primario" onclick={() => ir(hechas![0].fecha ? `/calendario?fecha=${hechas![0].fecha}` : '/calendario?vista=ideas')}><Icono n="calendario" s />Ir al calendario</button>
        </div>
      </div>
    {:else}
      <div class="col">
        <p>Elige dónde y cuándo sale este artículo. Cada fila es una publicación del calendario.</p>
        {#if existentes.length}
          <div class="aviso-ya">Ya hay {existentes.length === 1 ? 'una publicación' : `${existentes.length} publicaciones`} de este artículo: {existentes.map((p) => `${canal(p.canal_id)?.nombre} ${p.fecha ? fechaCorta(p.fecha) : 'sin fecha'}`).join(', ')}. Las nuevas se suman.</div>
        {/if}
        <div class="filas">
          {#each filas as f (f.canal_id)}
            {@const c = canal(f.canal_id)}
            <label class="fila-d" class:apagada={!f.usar}>
              <input type="checkbox" bind:checked={f.usar} />
              <span class="nombre"><Icono n={c?.icono ?? 'x'} red />{c?.nombre}</span>
              <select class="campo" bind:value={f.formato} disabled={!f.usar} aria-label="Formato en {c?.nombre}">
                {#each c?.formatos ?? [] as fo (fo.id)}<option value={fo.id}>{fo.nombre}</option>{/each}
              </select>
              <input class="campo" type="date" bind:value={f.fecha} disabled={!f.usar} aria-label="Fecha en {c?.nombre}" />
              <input class="campo" type="time" bind:value={f.hora} disabled={!f.usar || !f.fecha} aria-label="Hora en {c?.nombre}" />
            </label>
          {/each}
        </div>
        {#if elegidas.some((f) => !f.fecha)}
          <p class="nota">Las que van sin fecha quedan en la pestaña «Ideas» del calendario.</p>
        {/if}
        {#if elegidas.some((f) => f.canal_id === 'portfolio' && f.fecha)}
          <p class="nota">La fecha del portfolio pasa a ser la del artículo: es la que lleva su <code>.md</code>.</p>
        {/if}
        <div class="v-pie">
          <span class="espacio"></span>
          <button class="btn primario" disabled={!elegidas.length || enviando} onclick={planear}>
            <Icono n="difundir" s />Planear {elegidas.length === 1 ? 'una publicación' : `${elegidas.length} publicaciones`}
          </button>
        </div>
      </div>
    {/if}
  </div>
</div>

<style>
  .difusion { width: min(720px, 100%); }
  .col { padding: 20px; display: flex; flex-direction: column; gap: 14px; }
  .col p { margin: 0; color: var(--muted); font-size: 13px; }
  .col h3 { margin: 0; font-size: 15px; display: flex; gap: 8px; align-items: center; }
  .filas { display: flex; flex-direction: column; gap: 6px; }
  .fila-d { display: grid; grid-template-columns: 20px 130px 1fr 150px 110px; gap: 8px; align-items: center; padding: 8px 10px; border: 1px solid var(--line); border-radius: 10px; font-size: 13px; }
  .fila-d.apagada { opacity: 0.55; }
  .fila-d .nombre { display: flex; gap: 8px; align-items: center; font-weight: 500; }
  .fila-d .campo { height: 30px; width: 100%; }
  .aviso-ya, .nota { font-size: 12px; color: var(--muted); }
  .aviso-ya { padding: 10px 12px; border-radius: 10px; background: var(--surface-2); }
  .hechas { margin: 0; padding: 0; list-style: none; display: flex; flex-direction: column; gap: 6px; font-size: 13px; }
  .hechas li { display: flex; gap: 8px; align-items: center; text-transform: capitalize; }
</style>
