<script lang="ts">
  import { api, type Bloque } from '../lib/api';
  import { activaBloque, type ControlEditor } from '../lib/editor.svelte';
  import { fallo } from '../lib/estado.svelte';
  import Icono from './Icono.svelte';

  let { bloque, ctl }: { bloque: Bloque; ctl: ControlEditor } = $props();
  const variante = $derived(activaBloque(bloque));
  const tipo = $derived(bloque.tipo as 'imagen' | 'audio' | 'youtube');

  let medio = $state('');
  let texto = $state('');
  let url = $state('');
  let mostrada = '';
  let subiendo = $state(false);
  let primero: HTMLElement | undefined = $state();

  function leer(md: string) {
    if (tipo === 'imagen') {
      const m = md.match(/^!\[([^\]]*)\]\(medio:([0-9a-f]+)\)/);
      return { medio: m?.[2] ?? '', texto: m?.[1] ?? '', url: '' };
    }
    if (tipo === 'audio') {
      const m = md.match(/src="medio:([0-9a-f]+)"/);
      const t = md.match(/title="([^"]*)"/);
      return { medio: m?.[1] ?? '', texto: t?.[1]?.replace(/&quot;/g, '"') ?? '', url: '' };
    }
    const m = md.match(/^\[([^\]]*)\]\(([^)]*)\)$/);
    return m ? { medio: '', texto: m[1], url: m[2] } : { medio: '', texto: '', url: md.trim() };
  }

  function escribir(): string {
    if (tipo === 'imagen') return medio ? `![${texto.replace(/[[\]]/g, '')}](medio:${medio})` : '';
    if (tipo === 'audio') return medio ? `<audio src="medio:${medio}" title="${texto.replace(/"/g, '&quot;')}" controls></audio>` : '';
    if (!url.trim()) return '';
    return texto.trim() ? `[${texto.replace(/[[\]]/g, '')}](${url.trim()})` : url.trim();
  }

  $effect(() => {
    const v = variante;
    if (!v) return;
    if (v.id !== mostrada || (!ctl.hayPendiente(v.id) && v.contenido !== escribir())) {
      mostrada = v.id;
      ({ medio, texto, url } = leer(v.contenido));
    }
  });

  const cambio = () => ctl.programar(variante!.id, escribir());

  async function subir(e: Event) {
    const f = (e.currentTarget as HTMLInputElement).files?.[0];
    if (!f) return;
    subiendo = true;
    try {
      const m = await api.subirMedio(f);
      medio = m.id;
      if (!texto && tipo === 'audio') texto = f.name.replace(/\.[^.]+$/, '');
      cambio();
    } catch (err) {
      fallo(err);
    } finally {
      subiendo = false;
    }
  }

  function idYoutube(u: string): string | null {
    try {
      const x = new URL(u);
      const host = x.hostname.replace(/^(www\.|m\.)/, '');
      const id = host === 'youtu.be' ? x.pathname.slice(1) : host === 'youtube.com' && x.pathname === '/watch' ? x.searchParams.get('v') : null;
      return id && /^[\w-]{11}$/.test(id) ? id : null;
    } catch {
      return null;
    }
  }
  const yt = $derived(tipo === 'youtube' ? idYoutube(url) : null);

  $effect(() => ctl.registrar(bloque.id, { enfocar: () => primero?.focus() }));
  const foco = () => (ctl.foco = bloque.id);
</script>

{#if tipo === 'youtube'}
  <div class="medio yt">
    <div class="mini">
      {#if yt}<img src="https://i.ytimg.com/vi/{yt}/hqdefault.jpg" alt="" />{/if}
      <span><Icono n="play" s /></span>
    </div>
    <div class="txt">
      <input class="campo" bind:this={primero} bind:value={url} oninput={cambio} onfocus={foco} placeholder="https://youtu.be/…" />
      <input class="campo" bind:value={texto} oninput={cambio} onfocus={foco} placeholder="Título que sale debajo (opcional)" />
      {#if url && !yt}<small>No parece un enlace de vídeo de YouTube: saldrá como enlace normal.</small>{/if}
    </div>
  </div>
{:else}
  <div class="medio" class:audio={tipo === 'audio' && medio}>
    {#if !medio}
      <label class="subir" bind:this={primero} tabindex="-1">
        <Icono n={tipo === 'imagen' ? 'imagen' : 'audio'} />
        {subiendo ? 'Subiendo…' : tipo === 'imagen' ? 'Elegir una imagen' : 'Elegir un audio'}
        <input type="file" accept={tipo === 'imagen' ? 'image/*' : 'audio/*'} onchange={subir} hidden />
      </label>
    {:else if tipo === 'imagen'}
      <img class="foto" src="/api/medios/{medio}" alt={texto} />
      <div class="pie">
        <Icono n="imagen" s /><b>Texto alternativo:</b>
        <input class="alt" bind:this={primero} bind:value={texto} oninput={cambio} onfocus={foco} placeholder="Describe la imagen para quien no la ve" />
        <label class="btn fantasma">Cambiar<input type="file" accept="image/*" onchange={subir} hidden /></label>
      </div>
    {:else}
      <!-- svelte-ignore a11y_media_has_caption -->
      <audio controls src="/api/medios/{medio}"></audio>
      <input class="alt" bind:this={primero} bind:value={texto} oninput={cambio} onfocus={foco} placeholder="Título del audio" />
    {/if}
  </div>
{/if}

<style>
  .subir { display: flex; align-items: center; justify-content: center; gap: 8px; height: 120px; cursor: pointer; color: var(--muted); font: 13px var(--sans); background: repeating-linear-gradient(135deg, var(--surface-2) 0 10px, var(--surface-3) 10px 20px); }
  .subir:hover { color: var(--text); }
  .foto { display: block; max-width: 100%; margin: 0 auto; }
  .alt { flex: 1; min-width: 0; border: 0; background: transparent; outline: none; font: 13px var(--sans); color: var(--text); }
  .audio { display: flex; align-items: center; gap: 12px; padding: 10px 12px; }
  audio { height: 36px; flex: none; }
  .yt .mini { overflow: hidden; }
  .yt .mini img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; }
  .yt .mini span { position: relative; }
  .yt .txt { display: flex; flex-direction: column; gap: 6px; }
  .yt small { color: var(--faint); }
  .pie .btn { height: 26px; font-size: 12px; }
</style>
