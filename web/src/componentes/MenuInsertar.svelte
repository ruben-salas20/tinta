<script lang="ts">
  import { onMount } from 'svelte';
  import type { TipoBloque } from '../lib/api';
  import Icono from './Icono.svelte';

  let { filtro, alElegir, alCerrar }: { filtro: string; alElegir: (t: TipoBloque) => void; alCerrar: () => void } = $props();

  const OPCIONES: { tipo: TipoBloque; nombre: string; icono: string; pista: string }[] = [
    { tipo: 'parrafo', nombre: 'Párrafo', icono: 'articulos', pista: '' },
    { tipo: 'subtitulo', nombre: 'Subtítulo', icono: 'articulos', pista: '###' },
    { tipo: 'lista', nombre: 'Lista', icono: 'serie', pista: '-' },
    { tipo: 'cita', nombre: 'Cita', icono: 'nota', pista: '>' },
    { tipo: 'codigo', nombre: 'Código', icono: 'asa', pista: '```' },
    { tipo: 'imagen', nombre: 'Imagen', icono: 'imagen', pista: '' },
    { tipo: 'audio', nombre: 'Audio', icono: 'audio', pista: '' },
    { tipo: 'youtube', nombre: 'Vídeo de YouTube', icono: 'play', pista: '' },
  ];
  const sinAcentos = (s: string) => s.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase();
  const lista = $derived(OPCIONES.filter((o) => sinAcentos(o.nombre).includes(sinAcentos(filtro)) || o.tipo.startsWith(sinAcentos(filtro))));
  let sel = $state(0);
  $effect(() => {
    if (sel >= lista.length) sel = 0;
  });

  onMount(() => {
    const tecla = (e: KeyboardEvent) => {
      if (e.key === 'ArrowDown') sel = (sel + 1) % Math.max(lista.length, 1);
      else if (e.key === 'ArrowUp') sel = (sel - 1 + lista.length) % Math.max(lista.length, 1);
      else if (e.key === 'Enter' && lista[sel]) alElegir(lista[sel].tipo);
      else if (e.key === 'Escape') alCerrar();
      else return;
      e.preventDefault();
      e.stopPropagation();
    };
    window.addEventListener('keydown', tecla, true);
    return () => window.removeEventListener('keydown', tecla, true);
  });
</script>

<div class="menu insertar" role="listbox">
  <div class="etq">Insertar</div>
  {#each lista as o, i (o.tipo)}
    <button role="option" aria-selected={i === sel} class:sel={i === sel} onmousedown={(e) => { e.preventDefault(); alElegir(o.tipo); }}>
      <Icono n={o.icono} />{o.nombre}{#if o.pista}<small>{o.pista}</small>{/if}
    </button>
  {:else}
    <div class="etq">Nada coincide</div>
  {/each}
</div>

<style>
  .insertar { top: 100%; left: 0; margin-top: 6px; font-family: var(--sans); }
</style>
