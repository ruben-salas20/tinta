<script lang="ts">
  import { api, nombreDispositivo, type Bloque, type TipoBloque } from '../lib/api';
  import { activaBloque, type ControlEditor } from '../lib/editor.svelte';
  import type { Ocurrencia } from '../lib/extensiones';
  import { confirmar, fallo, pedirTexto } from '../lib/estado.svelte';
  import { notas as leerNotas, type Nota } from '../lib/critic';
  import BloqueCodigo from './BloqueCodigo.svelte';
  import BloqueMedio from './BloqueMedio.svelte';
  import BloqueTexto from './BloqueTexto.svelte';
  import Icono from './Icono.svelte';

  let { bloque, ctl, alHistorial }: { bloque: Bloque; ctl: ControlEditor; alHistorial: () => void } = $props();

  const enfocado = $derived(ctl.foco === bloque.id);
  const variante = $derived(activaBloque(bloque));
  const conflictos = $derived(bloque.variantes.filter((v) => v.origen === 'conflicto'));
  const llegoDe = $derived([...new Set(conflictos.map((v) => nombreDispositivo(v.dispositivo)).filter((d) => d !== null))].map((d) => `el ${d.toLowerCase()}`));
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

  const notas = $derived(variante ? leerNotas(variante.contenido) : []);
  let caja: HTMLDivElement;
  /** Nota iluminada: la de la tarjeta o el ancla bajo el ratón. */
  let ilum = $state<number | null>(null);

  // Las notas de un bloque de texto se pintan en el editor como .nota-marca,
  // en el mismo orden que en el texto; su ancla son los .nota-ancla de delante.
  const marcas = () => [...(caja?.querySelectorAll<HTMLElement>('.nota-marca') ?? [])];
  function anclasDe(m: HTMLElement): HTMLElement[] {
    const r: HTMLElement[] = [];
    let e = m.previousElementSibling as HTMLElement | null;
    while (e?.classList.contains('nota-ancla') && e.nextSibling === (r[r.length - 1] ?? m)) {
      r.push(e);
      e = e.previousElementSibling as HTMLElement | null;
    }
    return r;
  }

  $effect(() => {
    const k = ilum;
    void variante?.contenido;
    for (const e of caja?.querySelectorAll('.nota-marca.ilum, .nota-ancla.ilum') ?? []) e.classList.remove('ilum');
    if (k === null) return;
    const m = marcas()[k];
    if (m) [m, ...anclasDe(m)].forEach((e) => e.classList.add('ilum'));
  });

  function sobreTexto(e: MouseEvent) {
    const t = e.target as HTMLElement;
    const m = t.closest<HTMLElement>('.nota-marca');
    const a = m ? null : t.closest<HTMLElement>('.nota-ancla');
    if (m) return (ilum = marcas().indexOf(m));
    if (a) {
      let x: Element | null = a;
      while (x && !x.classList.contains('nota-marca')) x = x.nextElementSibling;
      return (ilum = x ? marcas().indexOf(x as HTMLElement) : null);
    }
    if (!t.closest('.nota-tarjeta')) ilum = null;
  }

  async function editar(nota: Nota) {
    if (!variante) return;
    const texto = await pedirTexto('Editar nota', nota.texto, 'Guardar', 'Solo la ves tú: no se exporta.');
    if (texto === null || texto === nota.texto || !texto.trim()) return;
    await ctl.editarNota(variante.id, nota.orden, texto.trim());
  }
</script>

<div class="bloque" class:foco={enfocado} role="group" bind:this={caja} onfocusin={() => (ctl.foco = bloque.id)} onmouseover={sobreTexto} onmouseleave={() => (ilum = null)}>
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

  {#if notas.length && variante}
    <div class="notas">
      {#each notas as nota (nota.indice)}
        <div class="nota-tarjeta" class:ilum={ilum === nota.orden} role="note" onmouseenter={() => (ilum = nota.orden)}>
          <div class="quien">
            <Icono n="nota" s />
            {#if nota.ia}<span class="ia">IA</span><span>{nota.ia}</span>{:else}<span>Tú</span>{/if}
            {#if nota.ancla}<span class="sobre">· sobre «{nota.ancla}»</span>{/if}
          </div>
          <p>{nota.texto}</p>
          <div class="acc">
            <button class="btn" onclick={() => ctl.resolver(variante!.id, nota.indice, 'aceptar')}><Icono n="check" s />Resolver</button>
            {#if !nota.ia}<button class="btn fantasma" onclick={() => editar(nota)}>Editar</button>{/if}
          </div>
        </div>
      {/each}
    </div>
  {/if}

  {#if bloque.variantes.length > 1}
    <button class="chip pastilla" title="Variantes de este bloque" onclick={() => (ctl.foco = bloque.id)}><Icono n="variantes" s />{bloque.variantes.length}</button>
  {/if}
</div>

{#if conflictos.length}
  <div class="conflicto bajo">
    <Icono n="rama" />
    <div>
      <b>Este bloque cambió en dos sitios a la vez.</b> Tinta guardó {n(conflictos.length + 1, 'redacción', 'redacciones')} como variantes.{#if llegoDe.length} {conflictos.length === 1 ? 'La otra llegó' : 'Las otras llegaron'} desde {llegoDe.join(' y ')}.{/if} Elige cuál queda; las demás no se pierden hasta que las descartes.
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
  /* Como la .margen de la maqueta, pero bajo el bloque y una por nota. */
  .notas { display: flex; flex-direction: column; align-items: flex-end; gap: 8px; margin: 10px 0 2px; }
  .nota-tarjeta { width: min(340px, 100%); font: 13px/1.45 var(--sans); padding: 10px 12px; border-radius: 10px; background: var(--surface); border: 1px solid var(--line); transition: border-color 0.12s, box-shadow 0.12s; }
  .nota-tarjeta.ilum { border-color: var(--line-strong); box-shadow: var(--shadow); }
  .nota-tarjeta .quien { display: flex; align-items: center; gap: 6px; font-size: 12px; color: var(--faint); margin-bottom: 4px; }
  .nota-tarjeta .sobre { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; min-width: 0; }
  .nota-tarjeta p { margin: 0; font: 13px/1.45 var(--sans); color: var(--text); white-space: pre-wrap; }
  .nota-tarjeta .acc { display: flex; gap: 6px; margin-top: 8px; }
  .nota-tarjeta .acc .btn { height: 26px; padding: 0 9px; font-size: 12px; }
  .bloque :global(.nota-ancla.ilum) { background: var(--mark-add); border-bottom-color: var(--text); }
  .bloque :global(.nota-marca.ilum) { color: var(--text); border-color: var(--text); }
</style>
