<script lang="ts">
  import { api, type Seccion } from '../lib/api';
  import { activaSeccion, type ControlEditor } from '../lib/editor.svelte';
  import { confirmar } from '../lib/estado.svelte';
  import Bloque from './Bloque.svelte';
  import Icono from './Icono.svelte';

  let {
    seccion,
    ctl,
    indice,
    total,
    alHistorial,
  }: { seccion: Seccion; ctl: ControlEditor; indice: number; total: number; alHistorial: () => void } = $props();

  const vs = $derived(activaSeccion(seccion));
  const letra = (i: number) => String.fromCharCode(65 + i);
  const posActiva = $derived(seccion.variantes.findIndex((v) => v.id === vs?.id));
  let menu = $state(false);
  let titulo = $state('');
  let mostrado = '';
  let espera: ReturnType<typeof setTimeout> | null = null;

  $effect(() => {
    if (vs && vs.id !== mostrado) {
      mostrado = vs.id;
      titulo = vs.titulo;
    }
  });

  function cambiarTitulo() {
    if (espera) clearTimeout(espera);
    const id = vs!.id;
    const t = titulo;
    espera = setTimeout(() => ctl.hacer(() => api.cambiarVarianteSeccion(id, t), false), 700);
  }

  async function nuevaVariante() {
    menu = false;
    await ctl.flush();
    const r = await api.forkSeccion(seccion.id);
    ctl.aplicar(r.documento);
    await ctl.refrescarArticulo();
  }

  async function activar(id: string) {
    menu = false;
    await ctl.hacer(() => api.cambiarSeccion(seccion.id, { variante_activa_id: id }));
  }

  async function descartar() {
    menu = false;
    if (!(await confirmar(`¿Descartar la variante ${letra(posActiva)} de esta sección?`, 'Descartar'))) return;
    await ctl.hacer(() => api.borrarVarianteSeccion(vs!.id));
  }

  async function mover(paso: -1 | 1) {
    menu = false;
    const s = ctl.doc!.secciones;
    const j = indice + paso;
    const m = paso < 0 ? { antes: s[j - 1]?.id ?? null, despues: s[j].id } : { antes: s[j].id, despues: s[j + 1]?.id ?? null };
    await ctl.hacer(() => api.cambiarSeccion(seccion.id, { mover: m }), false);
  }

  async function borrar() {
    menu = false;
    if (!(await confirmar('¿Borrar la sección entera, con sus variantes?'))) return;
    await ctl.hacer(() => api.borrarSeccion(seccion.id));
  }

  async function seccionDebajo() {
    menu = false;
    await ctl.hacer(() => api.crearSeccion(ctl.doc!.rama.id, 'Nueva sección', { antes: seccion.id }));
  }

  async function bloqueAlFinal() {
    const ultimo = vs!.bloques[vs!.bloques.length - 1];
    await ctl.crearBloque(vs!.id, 'parrafo', '', { antes: ultimo?.id ?? null });
  }
</script>

<section class="seccion">
  <div class="seccion-barra">
    <span class="desplegable">
      <button class="var" onclick={() => (menu = !menu)}>
        <Icono n="variantes" s />
        {indice === 0 && !vs?.titulo ? 'Entrada' : 'Sección'}{seccion.variantes.length > 1 ? ` · variante ${letra(posActiva)} de ${seccion.variantes.length}` : ''}
        {#if vs && vs.origen !== 'tu'}<span class="ia">{vs.origen === 'conflicto' ? 'conflicto' : 'IA'}</span>{/if}
        <Icono n="abajo" s />
      </button>
      {#if menu}
        <div class="menu" role="menu">
          {#if seccion.variantes.length > 1}
            <div class="etq">Variantes</div>
            {#each seccion.variantes as v, i (v.id)}
              <button class:sel={v.id === vs?.id} onclick={() => activar(v.id)}>
                {letra(i)} · {v.titulo || 'sin subtítulo'}<small>{v.origen === 'tu' ? '' : v.origen.replace('ia:', 'IA · ')}</small>
              </button>
            {/each}
          {/if}
          <button onclick={nuevaVariante}><Icono n="mas" s />Nueva variante de la sección</button>
          {#if seccion.variantes.length > 1}<button onclick={descartar}>Descartar esta variante</button>{/if}
          <hr />
          <button disabled={indice === 0} onclick={() => mover(-1)}>Subir la sección</button>
          <button disabled={indice === total - 1} onclick={() => mover(1)}>Bajar la sección</button>
          <button onclick={seccionDebajo}>Añadir una sección debajo</button>
          {#if total > 1}<button onclick={borrar}>Borrar la sección</button>{/if}
        </div>
      {/if}
    </span>
  </div>

  {#if indice > 0 || vs?.titulo}
    <input class="h2" bind:value={titulo} oninput={cambiarTitulo} placeholder="Subtítulo de la sección" aria-label="Subtítulo de la sección" />
  {/if}

  {#if vs}
    {#each vs.bloques as b (b.id)}
      <Bloque bloque={b} {ctl} {alHistorial} />
    {/each}
    {#if indice === total - 1}
      <button class="nuevo" onclick={bloqueAlFinal}>
        <Icono n="mas" />
        <span>Añadir un párrafo, o pulsa <span class="barra">/</span> en uno vacío para insertar otra cosa</span>
      </button>
    {/if}
  {/if}
</section>

<style>
  .var { background: transparent; cursor: pointer; font-size: 12px; }
  .seccion-barra { opacity: 0.55; transition: opacity 0.15s; }
  .seccion:hover .seccion-barra, .seccion:focus-within .seccion-barra { opacity: 1; }
  .h2 { display: block; width: 100%; border: 0; background: transparent; outline: none; font: 600 21px/1.35 var(--serif); margin: 0 0 14px; padding: 0; color: var(--text); }
  .nuevo { border: 0; background: transparent; cursor: pointer; padding: 0; text-align: left; }
  .nuevo:hover { color: var(--muted); }
  .ia { margin-left: 2px; }
</style>
