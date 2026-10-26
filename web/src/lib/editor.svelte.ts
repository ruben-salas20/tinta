// Controlador del editor: el documento de la rama abierta, el guardado
// automático y el foco entre bloques. Los componentes de bloque solo editan
// texto y avisan aquí.

import { tick } from 'svelte';
import { api, type Articulo, type Bloque, type Documento, type Entre, type Seccion, type TipoBloque, type VarianteBloque, type VarianteSeccion } from './api';
import { avisar, fallo } from './estado.svelte';
import { editarNota } from './critic';

const ESPERA_MS = 800;

export interface ApiBloque {
  enfocar: (donde: 'inicio' | 'fin') => void;
  /** Añade texto en línea al final (al unir con el bloque siguiente). */
  anexar?: (md: string) => void;
  tachar?: () => void;
  anotar?: () => Promise<void>;
}

export interface Ubicacion {
  seccion: Seccion;
  vs: VarianteSeccion;
  bloque: Bloque;
  indice: number;
}

export function activaSeccion(s: Seccion): VarianteSeccion | undefined {
  return s.variantes.find((v) => v.id === s.variante_activa_id) ?? s.variantes[0];
}

export function activaBloque(b: Bloque): VarianteBloque | undefined {
  return b.variantes.find((v) => v.id === b.variante_activa_id) ?? b.variantes[0];
}

interface Pendiente {
  timer: ReturnType<typeof setTimeout> | null;
  contenido: string;
  enVuelo: Promise<void> | null;
}

export class ControlEditor {
  articulo = $state<Articulo | null>(null);
  doc = $state<Documento | null>(null);
  foco = $state<string | null>(null);
  guardando = $state(0);
  guardadoEn = $state<Date | null>(null);
  errorGuardado = $state<string | null>(null);

  private revs = new Map<string, number>();
  private pendientes = new Map<string, Pendiente>();
  private apis = new Map<string, ApiBloque>();
  private focoPendiente: { bloque: string; donde: 'inicio' | 'fin' } | null = null;

  async cargarArticulo(id: string) {
    this.articulo = await api.articulo(id);
  }

  async cargarRama(id: string) {
    this.aplicar(await api.rama(id));
  }

  /** Pone un documento nuevo sin pisar lo que aún no se ha guardado. */
  aplicar(doc: Documento) {
    for (const s of doc.secciones)
      for (const vs of s.variantes)
        for (const b of vs.bloques)
          for (const v of b.variantes) if (!this.pendientes.has(v.id)) this.revs.set(v.id, v.rev);
    this.doc = doc;
  }

  async recargar() {
    if (!this.doc) return;
    await this.flush();
    this.aplicar(await api.rama(this.doc.rama.id));
    if (this.articulo) this.articulo = await api.articulo(this.articulo.id);
  }

  /** Recarga solo el artículo (señales, idiomas, ramas). */
  async refrescarArticulo() {
    if (this.articulo) this.articulo = await api.articulo(this.articulo.id);
  }

  hayPendiente(varianteId: string) {
    return this.pendientes.has(varianteId);
  }

  programar(varianteId: string, contenido: string) {
    const p = this.pendientes.get(varianteId) ?? { timer: null, contenido, enVuelo: null };
    p.contenido = contenido;
    if (p.timer) clearTimeout(p.timer);
    p.timer = setTimeout(() => this.enviar(varianteId), ESPERA_MS);
    this.pendientes.set(varianteId, p);
  }

  private async enviar(varianteId: string) {
    const p = this.pendientes.get(varianteId);
    if (!p) return;
    if (p.enVuelo) await p.enVuelo;
    if (p.timer) clearTimeout(p.timer);
    p.timer = null;
    const contenido = p.contenido;
    this.guardando++;
    p.enVuelo = (async () => {
      try {
        const g = await api.guardar(varianteId, contenido, this.revs.get(varianteId) ?? 0);
        this.errorGuardado = null;
        this.guardadoEn = new Date();
        if (g.conflicto) {
          avisar('Este párrafo cambió en otro sitio. Tu texto quedó guardado como otra variante, marcada «conflicto».', 'error');
          this.pendientes.delete(varianteId);
          await this.recargar();
          return;
        }
        this.revs.set(varianteId, g.variante.rev);
        this.actualizarVariante(g.variante);
      } catch (e) {
        this.errorGuardado = e instanceof Error ? e.message : String(e);
        fallo(e);
      } finally {
        this.guardando--;
      }
    })();
    await p.enVuelo;
    p.enVuelo = null;
    // Si se escribió más mientras se enviaba, queda otro guardado programado.
    if (!p.timer && p.contenido === contenido) this.pendientes.delete(varianteId);
  }

  /** Guarda ya lo que esté pendiente (de una variante o de todas). */
  async flush(varianteId?: string) {
    const ids = varianteId ? [varianteId] : [...this.pendientes.keys()];
    await Promise.all(ids.filter((id) => this.pendientes.has(id)).map((id) => this.enviar(id)));
  }

  private actualizarVariante(v: VarianteBloque) {
    if (!this.doc) return;
    for (const s of this.doc.secciones)
      for (const vs of s.variantes)
        for (const b of vs.bloques) {
          const i = b.variantes.findIndex((x) => x.id === v.id);
          if (i >= 0) {
            // Solo los metadatos: el texto ya lo tiene el editor del bloque.
            b.variantes[i].rev = v.rev;
            b.variantes[i].origen = v.origen;
            b.variantes[i].cambiado = v.cambiado;
            b.variantes[i].contenido = v.contenido;
          }
        }
  }

  /** Aplica una respuesta de guardado que cambia el texto desde fuera (marcas, restaurar). */
  async aplicarGuardado(v: VarianteBloque) {
    this.revs.set(v.id, v.rev);
    this.actualizarVariante(v);
    await this.refrescarArticulo();
  }

  // ------------------------------------------------------------ Bloques

  visibles(): Ubicacion[] {
    const r: Ubicacion[] = [];
    for (const seccion of this.doc?.secciones ?? []) {
      const vs = activaSeccion(seccion);
      if (!vs) continue;
      vs.bloques.forEach((bloque, indice) => r.push({ seccion, vs, bloque, indice }));
    }
    return r;
  }

  ubicar(bloqueId: string): Ubicacion | undefined {
    for (const seccion of this.doc?.secciones ?? [])
      for (const vs of seccion.variantes) {
        const indice = vs.bloques.findIndex((b) => b.id === bloqueId);
        if (indice >= 0) return { seccion, vs, bloque: vs.bloques[indice], indice };
      }
  }

  registrar(bloqueId: string, a: ApiBloque) {
    this.apis.set(bloqueId, a);
    if (this.focoPendiente?.bloque === bloqueId) {
      const { donde } = this.focoPendiente;
      this.focoPendiente = null;
      setTimeout(() => a.enfocar(donde), 0);
    }
    return () => {
      if (this.apis.get(bloqueId) === a) this.apis.delete(bloqueId);
    };
  }

  async enfocar(bloqueId: string, donde: 'inicio' | 'fin') {
    await tick();
    const a = this.apis.get(bloqueId);
    if (a) a.enfocar(donde);
    else this.focoPendiente = { bloque: bloqueId, donde };
  }

  /** Pega texto al final de otro bloque y le pasa el foco. */
  async unir(bloqueId: string, md: string) {
    this.apis.get(bloqueId)?.anexar?.(md);
  }

  api(bloqueId: string): ApiBloque | undefined {
    return this.apis.get(bloqueId);
  }

  vecino(bloqueId: string, paso: -1 | 1): Ubicacion | undefined {
    const v = this.visibles();
    const i = v.findIndex((u) => u.bloque.id === bloqueId);
    return i >= 0 ? v[i + paso] : undefined;
  }

  async moverFoco(bloqueId: string, paso: -1 | 1): Promise<boolean> {
    const u = this.vecino(bloqueId, paso);
    if (!u) return false;
    await this.enfocar(u.bloque.id, paso < 0 ? 'fin' : 'inicio');
    return true;
  }

  async crearBloque(vsId: string, tipo: TipoBloque, contenido: string, entre: Entre, enfocar = true): Promise<string | null> {
    try {
      const r = await api.crearBloque(vsId, tipo, contenido, entre);
      this.aplicar(r.documento);
      if (enfocar) await this.enfocar(r.bloque_id, 'inicio');
      return r.bloque_id;
    } catch (e) {
      fallo(e);
      return null;
    }
  }

  async crearTras(bloqueId: string, tipo: TipoBloque = 'parrafo', contenido = '') {
    const u = this.ubicar(bloqueId);
    if (!u) return null;
    return this.crearBloque(u.vs.id, tipo, contenido, { antes: bloqueId });
  }

  async borrarBloque(bloqueId: string, enfocarAnterior = true) {
    const anterior = this.vecino(bloqueId, -1);
    try {
      this.aplicar(await api.borrarBloque(bloqueId));
      if (enfocarAnterior && anterior) await this.enfocar(anterior.bloque.id, 'fin');
      await this.refrescarArticulo();
    } catch (e) {
      fallo(e);
    }
  }

  async moverBloque(bloqueId: string, paso: -1 | 1) {
    const u = this.ubicar(bloqueId);
    if (!u) return;
    const bloques = u.vs.bloques;
    const j = u.indice + paso;
    if (j < 0 || j >= bloques.length) return;
    const mover = paso < 0 ? { antes: bloques[j - 1]?.id ?? null, despues: bloques[j].id } : { antes: bloques[j].id, despues: bloques[j + 1]?.id ?? null };
    try {
      this.aplicar(await api.cambiarBloque(bloqueId, { mover }));
    } catch (e) {
      fallo(e);
    }
  }

  async cambiarTipo(bloqueId: string, tipo: TipoBloque) {
    try {
      this.aplicar(await api.cambiarBloque(bloqueId, { tipo }));
    } catch (e) {
      fallo(e);
    }
  }

  /** Ejecuta una operación que devuelve el documento y lo aplica. */
  async hacer(f: () => Promise<Documento>, guardarAntes = true) {
    try {
      if (guardarAntes) await this.flush();
      this.aplicar(await f());
      await this.refrescarArticulo();
    } catch (e) {
      fallo(e);
    }
  }

  /** Cambia el texto de una nota, como un cambio hecho desde fuera del editor. */
  async editarNota(varianteId: string, orden: number, texto: string) {
    try {
      await this.flush(varianteId);
      const v = this.buscarVariante(varianteId);
      if (!v) return;
      const g = await api.guardar(varianteId, editarNota(v.contenido, orden, texto), this.revs.get(varianteId) ?? v.rev);
      if (g.conflicto) {
        avisar('Este párrafo cambió en otro sitio. Tu cambio quedó guardado como otra variante, marcada «conflicto».', 'error');
        await this.recargar();
        return;
      }
      await this.aplicarGuardado(g.variante);
    } catch (e) {
      fallo(e);
    }
  }

  private buscarVariante(id: string): VarianteBloque | undefined {
    for (const s of this.doc?.secciones ?? [])
      for (const vs of s.variantes)
        for (const b of vs.bloques) {
          const v = b.variantes.find((x) => x.id === id);
          if (v) return v;
        }
  }

  async resolver(varianteId: string, indice: number, decision: 'aceptar' | 'rechazar') {
    try {
      await this.flush(varianteId);
      const g = await api.resolverMarca(varianteId, indice, decision);
      await this.aplicarGuardado(g.variante);
    } catch (e) {
      fallo(e);
    }
  }
}
