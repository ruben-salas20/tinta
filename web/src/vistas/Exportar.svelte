<script lang="ts">
  import { api, type Articulo, type Exportacion, type ParaX } from '../lib/api';
  import { avisar, fallo } from '../lib/estado.svelte';
  import Icono from '../componentes/Icono.svelte';
  import { copiarHtml, copiarTexto } from '../lib/portapapeles';

  let { articulo, idioma, alCerrar }: { articulo: Articulo; idioma: string; alCerrar: () => void } = $props();

  let exportaciones = $state<Record<string, Exportacion>>({});
  let elegidos = $state<Record<string, boolean>>({});
  let x = $state<ParaX | null>(null);

  const idActual = $derived(articulo.idiomas.find((i) => i.idioma === idioma)?.id ?? articulo.idiomas[0].id);

  async function cargar() {
    try {
      for (const i of articulo.idiomas) {
        exportaciones[i.idioma] = await api.exportacion(i.id);
        // Por defecto, lo que no es una traducción sin revisar.
        elegidos[i.idioma] = i.idioma === 'es' || !i.traducido_ia || !i.borrador;
      }
      x = await api.paraX(idActual);
    } catch (e) {
      fallo(e);
    }
  }
  cargar();

  const seleccion = $derived(Object.keys(elegidos).filter((k) => elegidos[k]));
  const vista = $derived(exportaciones[idioma] ?? Object.values(exportaciones)[0]);
  const cabecera = $derived(vista?.markdown.match(/^---\n[\s\S]*?\n---/)?.[0] ?? '');

  const arbol = $derived.by(() => {
    const md = seleccion.map((l) => `${l}.md`);
    const nombres = new Map<string, string>();
    for (const l of seleccion) for (const a of exportaciones[l]?.archivos ?? []) nombres.set(a.nombre, a.tipo);
    const imgs = [...nombres].filter(([, t]) => !t.startsWith('audio/')).map(([n]) => n);
    const auds = [...nombres].filter(([, t]) => t.startsWith('audio/')).map(([n]) => n);
    const rama = (ultima: boolean, carpeta: string, hijos: string[]) => [
      `${ultima ? '└─' : '├─'} ${carpeta}`,
      ...hijos.map((n, i) => `${ultima ? '   ' : '│  '}${i === hijos.length - 1 ? '└─' : '├─'} ${n}`),
    ];
    return [
      ...rama(!auds.length, `src/content/articles/${articulo.slug}/`, [...md, ...imgs]),
      ...(auds.length ? rama(true, `public/articulos/${articulo.slug}/`, auds) : []),
    ].join('\n');
  });

  const suma = $derived.by(() => {
    const t = { tachados: 0, propuestas: 0, notas: 0, conflictos: 0, imagenes_sin_alt: 0 };
    for (const l of seleccion) {
      const a = exportaciones[l]?.avisos;
      if (!a) continue;
      t.tachados += a.tachados;
      t.propuestas += a.propuestas;
      t.notas += a.notas;
      t.conflictos += a.conflictos;
      t.imagenes_sin_alt += a.imagenes_sin_alt;
    }
    return t;
  });

  const plural = (n: number, una: string, varias: string) => `${n} ${n === 1 ? una : varias}`;

  async function copiarMd() {
    if (!vista) return;
    try {
      await copiarTexto(vista.markdown);
      avisar(`Markdown en ${vista.idioma.toUpperCase()} copiado.`);
    } catch (e) {
      fallo(e);
    }
  }

  async function copiar(texto: string, aviso: string) {
    try {
      await copiarTexto(texto);
      avisar(aviso);
    } catch (e) {
      fallo(e);
    }
  }

  async function copiarX() {
    if (!x) return;
    try {
      await copiarHtml(x.html, x.texto);
      avisar('Copiado. Pégalo en el editor de X Articles.');
    } catch (e) {
      fallo(e);
    }
  }
</script>

<svelte:window onkeydown={(e) => e.key === 'Escape' && alCerrar()} />

<div class="velo" role="presentation" onmousedown={(e) => e.target === e.currentTarget && alCerrar()}>
  <div class="ventana" role="dialog" aria-label="Exportar y copiar">
    <div class="v-cab">
      <b>Exportar y copiar</b><span>{articulo.idiomas.find((i) => i.idioma === idioma)?.titulo}</span>
      <span style="flex:1"></span>
      <button class="btn fantasma" style="padding:0 6px" aria-label="Cerrar" onclick={alCerrar}><Icono n="x" /></button>
    </div>
    <div class="v-cuerpo">
      <section class="col">
        <h3><Icono n="portfolio" red />Para el portfolio</h3>
        <p>Un .zip para descomprimir en la raíz del repo del portfolio.</p>
        <dl class="opciones">
          <dt>Idiomas</dt>
          <dd>
            {#each articulo.idiomas as i (i.id)}
              <label class="idioma">
                <input type="checkbox" bind:checked={elegidos[i.idioma]} />
                {i.idioma.toUpperCase()}
                <span style="color:var(--faint)">{i.borrador ? '· borrador' : ''}{i.traducido_ia ? ' · traducido con IA' : ''}</span>
              </label>
            {/each}
          </dd>
          <dt>Rama</dt><dd><span class="chip"><Icono n="rama" s />principal de cada idioma</span></dd>
          <dt>Fecha</dt><dd>{articulo.fecha ?? 'hoy'} <span style="color:var(--faint)">{articulo.fecha ? '' : '· pon una fecha en los detalles'}</span></dd>
        </dl>
        <div class="arbol"><span class="d">tinta-{articulo.slug}.zip</span>{'\n' + arbol}</div>
        <div class="revision">
          {#if suma.tachados + suma.propuestas + suma.notas}
            <div><Icono n="check" s />Se quitan {[suma.tachados && plural(suma.tachados, 'tachado', 'tachados'), suma.propuestas && plural(suma.propuestas, 'propuesta sin aceptar', 'propuestas sin aceptar'), suma.notas && plural(suma.notas, 'nota', 'notas')].filter(Boolean).join(', ')}.</div>
          {/if}
          {#if suma.imagenes_sin_alt}
            <div class="mal"><Icono n="imagen" s />{plural(suma.imagenes_sin_alt, 'imagen no tiene', 'imágenes no tienen')} texto alternativo.</div>
          {:else}
            <div><Icono n="check" s />Todas las imágenes tienen texto alternativo.</div>
          {/if}
          {#if suma.conflictos}
            <div class="mal"><Icono n="rama" s />{plural(suma.conflictos, 'conflicto sin resolver', 'conflictos sin resolver')}. Se exporta la variante activa.</div>
          {/if}
        </div>
        {#if cabecera}<pre class="fm">{cabecera}</pre>{/if}
        <div class="v-pie">
          <button class="btn" onclick={copiarMd} disabled={!vista}><Icono n="copiar" s />Copiar Markdown</button>
          <span class="espacio"></span>
          <a class="btn primario" class:apagado={!seleccion.length} href={seleccion.length ? api.urlZip(articulo.id, seleccion) : undefined} download><Icono n="descargar" s />Descargar .zip</a>
        </div>
      </section>

      <section class="col">
        <h3><Icono n="x" red />Para X Articles</h3>
        <p>El título va en su propio campo de X. El texto se copia con formato, ya adaptado a lo que X conserva.</p>
        {#if x}
          <div class="titulo-x">
            <b>{x.titulo}</b>
            <button class="btn" onclick={() => copiar(x!.titulo, 'Título copiado.')}><Icono n="copiar" s />Copiar título</button>
          </div>
        {/if}
        <div class="vista-x">
          {#if x}
            <!-- El HTML lo genera el servidor desde el Markdown del artículo. -->
            {@html x.html}
          {:else}
            <p class="sigue">Preparando…</p>
          {/if}
        </div>
        {#if x}
          <div class="revision">
            {#if x.imagenes.length}
              <div class="mal"><Icono n="imagen" s />{plural(x.imagenes.length, 'imagen', 'imágenes')}: X no las recibe al pegar. Súbelas a mano: {x.imagenes.join(', ')}.</div>
            {/if}
            {#if x.avisos.audios}<div><Icono n="audio" s />El audio se omite: X Articles no admite audio.</div>{/if}
            {#if x.codigos.length}
              <div class="mal"><Icono n="nota" s />{plural(x.codigos.length, 'bloque de código', 'bloques de código')}: X no los recibe al pegar. Añádelos con el botón de código de X y pega cada uno:</div>
              <div class="codigos">
                {#each x.codigos as c, i (i)}
                  <button class="btn" onclick={() => copiar(c.codigo, `Código ${i + 1} copiado.`)}><Icono n="copiar" s />Código {i + 1}{c.lenguaje ? ` · ${c.lenguaje}` : ''}</button>
                {/each}
              </div>
            {/if}
            {#if x.avisos.videos}<div><Icono n="play" s />Los vídeos de YouTube van como enlace.</div>{/if}
            <div style="color:var(--muted)"><Icono n="nota" s />Los subtítulos de tercer nivel pasan a negrita y los puntos anidados suben un nivel: X no admite ninguno de los dos.</div>
          </div>
        {/if}
        <div class="v-pie">
          <span class="espacio"></span>
          <button class="btn primario" onclick={copiarX} disabled={!x}><Icono n="copiar" s />Copiar para X</button>
        </div>
      </section>
    </div>
  </div>
</div>

<style>
  .idioma { display: inline-flex; gap: 6px; align-items: center; margin-right: 12px; }
  .apagado { opacity: 0.45; pointer-events: none; }
  .vista-x { max-height: 380px; overflow: auto; }
  .titulo-x { display: flex; align-items: center; gap: 10px; }
  .titulo-x b { flex: 1; font-size: 15px; }
  .codigos { display: flex; gap: 6px; flex-wrap: wrap; padding-left: 22px; }
  .vista-x :global(h1) { font-size: 22px; line-height: 1.2; margin: 0 0 12px; font-weight: 800; }
  .vista-x :global(h2) { font-size: 17px; margin: 14px 0 6px; font-weight: 700; }
  .vista-x :global(p) { margin: 0 0 8px; font-size: 14.5px; line-height: 1.5; }
  .vista-x :global(pre) { font: 12px var(--mono); background: var(--surface-2); padding: 8px; border-radius: 6px; overflow-x: auto; }
  .vista-x :global(img) { max-width: 100%; }
</style>
