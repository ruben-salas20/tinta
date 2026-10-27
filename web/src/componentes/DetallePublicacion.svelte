<script lang="ts">
  import { onDestroy, untrack } from 'svelte';
  import { api, ESTADOS_PUB, NOMBRE_ESTADO_PUB, type EstadoPub, type Medio, type Publicacion } from '../lib/api';
  import { contar, partir } from '../lib/calendario';
  import { avisar, canal, catalogo, confirmar, difusion, fallo, ir, pedirTexto, recargarCatalogo } from '../lib/estado.svelte';
  import { esImagen, iconoDeMedio } from '../lib/medios';
  import { estadoPermitido, exigeFechaYCanal, faltaParaSalir, formatoPara } from '../lib/ideas';
  import { copiarTexto } from '../lib/portapapeles';
  import Icono from './Icono.svelte';

  let { id, alCambiar, alBorrar, alCerrar }: {
    id: string;
    alCambiar: (p: Publicacion) => void;
    alBorrar: (id: string) => void;
    alCerrar: () => void;
  } = $props();

  let p = $state<Publicacion | null>(null);
  let guardando = $state(false);
  let pendiente: ReturnType<typeof setTimeout> | null = null;
  let enEspera = $state(false);
  // Por id de medio. Se piden al abrir el panel, así el tipo es el que tiene el servicio ahora.
  let medios = $state<Record<string, Medio>>({});

  // El panel se recrea con cada publicación ({#key} en Calendario).
  untrack(() => Promise.all([api.publicacion(id), api.medios()]))
    .then(([x, lista]) => {
      p = x;
      medios = Object.fromEntries(lista.map((m) => [m.id, m]));
    })
    .catch(fallo);

  const c = $derived(p ? canal(p.canal_id) : undefined);
  const fmt = $derived(c?.formatos.find((f) => f.id === p?.formato));
  const canales = $derived(catalogo.canales.filter((x) => x.activo || x.id === p?.canal_id));
  const falta = $derived(p ? faltaParaSalir(!!p.fecha, !!p.canal_id) : null);
  const bloqueado = $derived(!!p && exigeFechaYCanal(p.estado));
  // Sin canal, la publicación puede tener varias partes: no hay formato que lo limite.
  const hilo = $derived(!p?.canal_id || !!fmt?.partes);
  const articulos = $derived(
    catalogo.articulos.map((a) => ({ id: a.id, titulo: (a.idiomas.find((i) => i.idioma === 'es') ?? a.idiomas[0])?.titulo || 'Sin título' })),
  );

  /** Guarda todo lo editable tras una pausa: escribir no lanza una petición por tecla. */
  function programar(inmediato = false) {
    if (pendiente) clearTimeout(pendiente);
    enEspera = true;
    pendiente = setTimeout(guardar, inmediato ? 0 : 800);
  }

  async function guardar() {
    pendiente = null;
    enEspera = false;
    if (!p) return;
    guardando = true;
    const portfolioAntes = p.canal_id === 'portfolio';
    try {
      const r = await api.cambiarPublicacion(p.id, {
        canal_id: p.canal_id,
        formato: p.formato,
        fecha: p.fecha,
        hora: p.hora || null,
        estado: p.estado,
        titulo: p.titulo,
        partes: p.partes,
        articulo_id: p.articulo?.id ?? null,
        enlace: p.enlace,
      });
      // Solo se copia lo que pone el servicio: lo que se escribe sigue en local.
      p.rev = r.rev;
      p.cambiado = r.cambiado;
      p.articulo = r.articulo;
      alCambiar(r);
      difusion.version++;
      if ((portfolioAntes || r.canal_id === 'portfolio') && r.articulo) recargarCatalogo();
    } catch (e) {
      fallo(e);
    } finally {
      guardando = false;
    }
  }

  onDestroy(() => {
    if (pendiente) {
      clearTimeout(pendiente);
      guardar();
    }
  });

  // Sin canal no hay formato. Con canal, si el formato no es suyo, se elige otro:
  // con varias partes, uno que las admita, para no juntarlas sin querer.
  function cambiarCanal(nuevo: string | null) {
    if (!p) return;
    p.canal_id = nuevo;
    if (!nuevo) p.formato = null;
    else {
      const fs = canal(nuevo)?.formatos ?? [];
      if (!fs.some((f) => f.id === p!.formato)) p.formato = formatoPara(fs, p.partes.length)?.id ?? null;
    }
    ajustarPartes();
    programar(true);
  }

  // Sin fecha no hay hora. Con programada o publicada la fecha no se quita: devuelve false.
  function cambiarFecha(nueva: string | null): boolean {
    if (!p) return false;
    if (!nueva && bloqueado) return false;
    p.fecha = nueva;
    if (!nueva) p.hora = null;
    programar(true);
    return true;
  }

  function cambiarFormato(nuevo: string) {
    if (!p) return;
    p.formato = nuevo;
    ajustarPartes();
    programar(true);
  }

  // Un formato de una sola parte no puede quedarse con un hilo: se juntan.
  function ajustarPartes() {
    if (!p) return;
    const f = canal(p.canal_id)?.formatos.find((x) => x.id === p!.formato);
    if (f && !f.partes && p.partes.length > 1) {
      p.partes = [{ texto: p.partes.map((x) => x.texto.trim()).filter(Boolean).join('\n\n'), medios: p.partes.flatMap((x) => x.medios) }];
    }
  }

  function estado(e: EstadoPub) {
    if (!p) return;
    p.estado = e;
    programar(true);
  }

  function anadirParte(despues: number) {
    p!.partes.splice(despues + 1, 0, { texto: '', medios: [] });
    programar(true);
  }

  function quitarParte(i: number) {
    p!.partes.splice(i, 1);
    programar(true);
  }

  function moverParte(i: number, d: -1 | 1) {
    const ps = p!.partes;
    [ps[i], ps[i + d]] = [ps[i + d], ps[i]];
    programar(true);
  }

  function partirParte(i: number) {
    if (!fmt?.limite) return;
    const [a, b] = partir(p!.partes[i].texto, fmt.limite);
    p!.partes[i].texto = a;
    p!.partes.splice(i + 1, 0, { texto: b, medios: [] });
    programar(true);
  }

  async function subir(i: number, ev: Event) {
    const input = ev.currentTarget as HTMLInputElement;
    for (const f of input.files ?? []) {
      try {
        const m = await api.subirMedio(f);
        p!.partes[i].medios.push(m.id);
        medios[m.id] = m;
      } catch (e) {
        fallo(e);
      }
    }
    input.value = '';
    programar(true);
  }

  // Una imagen que no carga (rotas) se muestra como icono con su nombre.
  let rotas = $state<Record<string, boolean>>({});

  async function copiar(i: number) {
    try {
      await copiarTexto(p!.partes[i].texto);
      avisar(p!.partes.length > 1 ? `Parte ${i + 1} copiada.` : 'Texto copiado.');
    } catch (e) {
      fallo(e);
    }
  }

  async function marcarPublicada() {
    const enlace = await pedirTexto('Enlace de la publicación', p!.enlace ?? '', 'Marcar publicada', `Pega el enlace del ${fmt?.nombre.toLowerCase() ?? 'post'} en ${c?.nombre ?? 'la red'}.`);
    if (!enlace) return;
    p!.enlace = enlace;
    p!.estado = 'publicada';
    programar(true);
  }

  async function borrar() {
    if (!(await confirmar('¿Borrar esta publicación del calendario?'))) return;
    try {
      if (pendiente) clearTimeout(pendiente);
      pendiente = null;
      await api.borrarPublicacion(id);
      difusion.version++;
      alBorrar(id);
    } catch (e) {
      fallo(e);
    }
  }

  function elegirArticulo(aid: string) {
    const a = articulos.find((x) => x.id === aid);
    p!.articulo = a ? { id: a.id, titulo: a.titulo, slug: '' } : null;
    programar(true);
  }
</script>

<aside class="detalle" aria-label="Detalle de la publicación">
  {#if !p}
    <p class="aviso">Cargando…</p>
  {:else}
    <div class="cab">
      <Icono n={c?.icono ?? 'x'} red />
      <input class="titulo-pub" bind:value={p.titulo} oninput={() => programar()} placeholder={p.articulo?.titulo || 'Título interno'} aria-label="Título" />
      <span class="h" style="font-size:11px;color:var(--faint)">{guardando || enEspera ? 'Guardando…' : ''}</span>
      <button class="btn fantasma" style="padding:0 6px" aria-label="Cerrar" onclick={alCerrar}><Icono n="x" /></button>
    </div>

    <dl class="campos">
      <dt>Canal</dt>
      <dd>
        <select class="campo" value={p.canal_id ?? ''} onchange={(e) => cambiarCanal(e.currentTarget.value || null)} aria-label="Canal">
          <option value="" disabled={bloqueado}>Sin canal</option>
          {#each canales as x (x.id)}<option value={x.id}>{x.nombre}</option>{/each}
        </select>
        {#if p.canal_id}
          <select class="campo" value={p.formato ?? ''} onchange={(e) => cambiarFormato(e.currentTarget.value)} aria-label="Formato">
            {#each c?.formatos ?? [] as f (f.id)}<option value={f.id}>{f.nombre}</option>{/each}
          </select>
        {/if}
      </dd>
      <dt>Fecha</dt>
      <dd>
        <input
          class="campo"
          type="date"
          value={p.fecha ?? ''}
          onchange={(e) => { if (!cambiarFecha(e.currentTarget.value || null)) e.currentTarget.value = p?.fecha ?? ''; }}
          aria-label="Fecha"
        />
        {#if p.fecha}
          <button class="btn fantasma" disabled={bloqueado} onclick={() => cambiarFecha(null)}>Quitar fecha</button>
        {/if}
        <input
          class="campo"
          type="time"
          value={p.hora ?? ''}
          disabled={!p.fecha}
          onchange={(e) => { p!.hora = e.currentTarget.value || null; programar(true); }}
          aria-label="Hora"
        />
        {#if !p.fecha}<small class="ayuda">Pon fecha para elegir hora.</small>{/if}
      </dd>
      <dt>Estado</dt>
      <dd class="estados">
        {#each ESTADOS_PUB as e}
          <button class="e-{e}" class:sel={p.estado === e} disabled={!estadoPermitido(e, !!p.fecha, !!p.canal_id)} onclick={() => estado(e)}>{NOMBRE_ESTADO_PUB[e]}</button>
        {/each}
        {#if falta}
          <small class="ayuda">Para programarla o publicarla hace falta {falta}.</small>
        {:else if bloqueado}
          <small class="ayuda">Para quitar la fecha o el canal, cambia antes el estado.</small>
        {/if}
      </dd>
      <dt>Artículo</dt>
      <dd>
        <select class="campo" style="flex:1;min-width:0" value={p.articulo?.id ?? ''} onchange={(e) => elegirArticulo(e.currentTarget.value)} aria-label="Artículo">
          <option value="">Sin artículo</option>
          {#each articulos as a (a.id)}<option value={a.id}>{a.titulo}</option>{/each}
        </select>
        {#if p.articulo}
          <a class="btn fantasma" style="padding:0 6px" href="/a/{p.articulo.id}" title="Abrir el artículo" onclick={(e) => { e.preventDefault(); ir(`/a/${p!.articulo!.id}`); }}><Icono n="articulos" s /></a>
        {/if}
      </dd>
    </dl>

    {#if p.canal_id === 'portfolio' && p.articulo}
      <div class="aviso">La fecha de esta publicación es la del artículo: la lleva su <code>.md</code> al exportar.</div>
    {/if}

    <div>
      <h4>{hilo ? 'Posts del hilo' : 'Texto'}{#if fmt?.limite}<small>límite {fmt.limite}{fmt.partes ? ' por post' : ''}</small>{/if}</h4>
      {#each p.partes as parte, i (i)}
        {@const n = contar(parte.texto)}
        {@const pasa = !!fmt?.limite && n > fmt.limite}
        <div class="post" class:pasa>
          <textarea bind:value={parte.texto} oninput={() => programar()} placeholder={hilo ? `Post ${i + 1}` : 'Escribe el texto'} aria-label="Texto de la parte {i + 1}"></textarea>
          {#if parte.medios.length}
            <div class="medios">
              {#each parte.medios as m, j (m)}
                {@const med = medios[m]}
                <span class="m">
                  {#if esImagen(med?.tipo) && !rotas[m]}
                    <img src="/api/medios/{m}" alt="" onerror={() => (rotas[m] = true)} />
                  {:else}
                    <Icono n={iconoDeMedio(med?.tipo)} />
                    <small title={med?.nombre}>{med?.nombre ?? 'Medio'}</small>
                  {/if}
                  <button aria-label="Quitar" onclick={() => { parte.medios.splice(j, 1); programar(true); }}><Icono n="x" s /></button>
                </span>
              {/each}
            </div>
          {/if}
          {#if fmt?.limite}
            <div class="barra-uso"><i style="width:{Math.min(100, (n / fmt.limite) * 100)}%"></i></div>
          {/if}
          <div class="pie">
            {#if p.partes.length > 1}<span>{i + 1} / {p.partes.length}</span>{/if}
            <label class="btn fantasma" title="Añadir imagen o audio">
              <Icono n="imagen" s />
              <input class="oculto" type="file" accept="image/*,audio/*" multiple onchange={(e) => subir(i, e)} />
            </label>
            <button class="btn fantasma" title="Copiar" onclick={() => copiar(i)}><Icono n="copiar" s /></button>
            {#if hilo && p.partes.length > 1}
              <button class="btn fantasma" title="Subir" disabled={i === 0} onclick={() => moverParte(i, -1)}>↑</button>
              <button class="btn fantasma" title="Bajar" disabled={i === p.partes.length - 1} onclick={() => moverParte(i, 1)}>↓</button>
              <button class="btn fantasma" title="Quitar este post" onclick={() => quitarParte(i)}><Icono n="x" s /></button>
            {/if}
            {#if pasa && fmt?.partes}
              <button class="btn" onclick={() => partirParte(i)}>Partir en dos</button>
            {/if}
            {#if fmt?.limite}<span class="cuenta" class:pasa>{n} / {fmt.limite}</span>{/if}
          </div>
        </div>
      {/each}
      {#if hilo}
        <div class="acciones" style="margin-top:10px">
          <button class="btn" onclick={() => anadirParte(p!.partes.length - 1)}><Icono n="mas" s />Post</button>
        </div>
      {/if}
    </div>

    {#if p.estado === 'publicada' && p.enlace}
      <div class="aviso">Publicada: <a href={p.enlace} target="_blank" rel="noreferrer">{p.enlace}</a></div>
    {/if}

    <div class="acciones">
      {#if p.estado !== 'publicada'}
        <button class="btn primario" disabled={!!falta} onclick={marcarPublicada}><Icono n="check" s />Marcar publicada</button>
      {:else}
        <button class="btn" onclick={marcarPublicada}>Cambiar enlace</button>
      {/if}
      <span style="flex:1"></span>
      <button class="btn fantasma" onclick={borrar}>Borrar</button>
    </div>
    <div class="aviso">Tinta no publica por ti: copia el texto, publícalo en {c?.nombre ?? 'la red'} y marca aquí que ya salió.</div>
  {/if}
</aside>
