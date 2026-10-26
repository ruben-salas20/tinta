<script lang="ts">
  import { api, type Bloque, type TipoBloque } from '../lib/api';
  import { activaBloque, type ControlEditor } from '../lib/editor.svelte';
  import type { Ocurrencia } from '../lib/extensiones';
  import { confirmar, fallo } from '../lib/estado.svelte';
  import BloqueCodigo from './BloqueCodigo.svelte';
  import BloqueMedio from './BloqueMedio.svelte';
  import BloqueTexto from './BloqueTexto.svelte';
  import Icono from './Icono.svelte';

  let { bloque, ctl, alHistorial }: { bloque: Bloque; ctl: ControlEditor; alHistorial: () => void } = $props();

  const enfocado = $derived(ctl.foco === bloque.id);
  const variante = $derived(activaBloque(bloque));
  const conflictos = $derived(bloque.variantes.filter((v) => v.origen === 'conflicto'));
  const esTexto = $derived(['parrafo', 'subtitulo', 'lista', 'cita'].includes(bloque.tipo));
  const ubic = $derived(ctl.ubicar(bloque.id));

  let marca = $state<Ocurrencia | null>(null);
  let nota = $state<string | null>(null);
  let mas = $state(false);

  const TIPOS: [TipoBloque, string][] = [
    ['parrafo', 'Párrafo'],
    ['subtitulo', 'Subtítulo'],
    ['lista', 'Lista'],
    ['cita', 'Cita'],
    ['codigo', 'Código'],
  ];

  async function fork() {
    await ctl.flush(variante?.id);
    try {
      const r = await api.forkBloque(bloque.id);
      ctl.aplicar(r.documento);
      await ctl.enfocar(bloque.id, 'fin');
    } catch (e) {
      fallo(e);
    }
  }

  async function quedarmeConActiva() {
    if (!(await confirmar(`Se descartan ${conflictos.length === 1 ? 'la otra redacción' : 'las otras redacciones'} en conflicto.`, 'Descartar'))) return;
    for (const c of conflictos) {
      if (c.id === variante?.id) continue;
      await ctl.hacer(() => api.borrarVarianteBloque(c.id));
    }
  }

  async function borrar() {
    mas = false;
    if (!(await confirmar('¿Borrar este bloque con todas sus variantes?'))) return;
    await ctl.borrarBloque(bloque.id);
  }

  async function insertar(tipo: TipoBloque) {
    await ctl.cambiarTipo(bloque.id, tipo);
    await ctl.enfocar(bloque.id, 'inicio');
  }

  function alCursor(o: Ocurrencia | null, n: string | null) {
    marca = o;
    nota = n === 'seleccion' ? null : n;
  }

  const n = (k: number, una: string, varias: string) => `${k} ${k === 1 ? una : varias}`;
</script>

<div class="bloque" class:foco={enfocado} role="group" onfocusin={() => (ctl.foco = bloque.id)}>
  {#if enfocado}
    <div class="barra-bloque" role="toolbar" aria-label="Acciones del bloque">
      <button onmousedown={(e) => e.preventDefault()} onclick={fork}><Icono n="variantes" />Fork{esTexto && bloque.tipo === 'parrafo' ? ' del párrafo' : ''}</button>
      {#if esTexto}
        <button onmousedown={(e) => e.preventDefault()} onclick={() => ctl.api(bloque.id)?.tachar?.()}><Icono n="tachar" />Tachar</button>
        <button onmousedown={(e) => e.preventDefault()} onclick={() => ctl.api(bloque.id)?.anotar?.()}><Icono n="nota" />Nota</button>
      {/if}
      {#if marca && variante}
        <span class="sep"></span>
        {#if marca.tipo === 'nota'}
          <span class="nota-txt" title={nota ?? ''}>{nota}</span>
          <button onclick={() => ctl.resolver(variante.id, marca!.indice, 'aceptar')}><Icono n="check" />Resolver nota</button>
        {:else}
          <button onclick={() => ctl.resolver(variante.id, marca!.indice, 'rechazar')}>Quitar tachado</button>
          <button onclick={() => ctl.resolver(variante.id, marca!.indice, 'aceptar')}>Borrar del todo</button>
        {/if}
      {/if}
      <span class="sep"></span>
      <button title="Historial" aria-label="Historial" onclick={alHistorial}><Icono n="historial" /></button>
      <span class="desplegable">
        <button title="Más" aria-label="Más" onclick={() => (mas = !mas)}><Icono n="puntos" /></button>
        {#if mas}
          <div class="menu der" role="menu">
            <button disabled={!ubic || ubic.indice === 0} onclick={() => { mas = false; ctl.moverBloque(bloque.id, -1); }}>Subir</button>
            <button disabled={!ubic || ubic.indice === ubic.vs.bloques.length - 1} onclick={() => { mas = false; ctl.moverBloque(bloque.id, 1); }}>Bajar</button>
            {#if esTexto || bloque.tipo === 'codigo'}
              <div class="etq">Convertir en</div>
              {#each TIPOS as [t, nombre]}
                <button class:sel={bloque.tipo === t} onclick={() => { mas = false; ctl.cambiarTipo(bloque.id, t); }}>{nombre}</button>
              {/each}
            {/if}
            <hr />
            <button onclick={borrar}>Borrar el bloque</button>
          </div>
        {/if}
      </span>
    </div>
  {/if}
  <span class="asa" aria-hidden="true"><Icono n="asa" /></span>

  {#if esTexto}
    <BloqueTexto {bloque} {ctl} {alCursor} alInsertar={insertar} />
  {:else if bloque.tipo === 'codigo'}
    <BloqueCodigo {bloque} {ctl} />
  {:else}
    <BloqueMedio {bloque} {ctl} />
  {/if}

  {#if bloque.variantes.length > 1}
    <button class="chip pastilla" title="Variantes de este bloque" onclick={() => (ctl.foco = bloque.id)}><Icono n="variantes" s />{bloque.variantes.length}</button>
  {/if}
</div>

{#if conflictos.length}
  <div class="conflicto bajo">
    <Icono n="rama" />
    <div>
      <b>Este bloque cambió en dos sitios a la vez.</b> Tinta guardó {n(conflictos.length + 1, 'redacción', 'redacciones')} como variantes. Elige cuál queda; las demás no se pierden hasta que las descartes.
      <div class="acc">
        <button class="btn" onclick={() => (ctl.foco = bloque.id)}>Ver las variantes</button>
        <button class="btn fantasma" onclick={quedarmeConActiva}>Quedarme con la activa</button>
      </div>
    </div>
  </div>
{/if}

<style>
  .bloque .asa { display: inline-flex; }
  .conflicto.bajo { margin: 8px -14px 0; }
  .barra-bloque { white-space: nowrap; }
  .nota-txt { max-width: 220px; overflow: hidden; text-overflow: ellipsis; align-self: center; padding: 0 6px; color: var(--muted); font-size: 12.5px; }
  .barra-bloque .menu button { height: auto; }
</style>
