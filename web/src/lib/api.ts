// Cliente de la API de tinta-server. Los tipos reflejan los structs de
// crates/tinta-server/src/modelo.rs.

export interface Serie {
  id: string;
  nombre: string;
}
export interface SerieConCuenta extends Serie {
  articulos: number;
}
export interface Etiqueta {
  nombre: string;
  articulos: number;
}
export interface Rama {
  id: string;
  idioma_id: string;
  nombre: string;
  origen_id: string | null;
  creado: string;
}
export interface Idioma {
  id: string;
  idioma: 'es' | 'en';
  titulo: string;
  descripcion: string;
  borrador: boolean;
  traducido_ia: boolean;
  rama_principal_id: string | null;
}
export interface IdiomaDetalle extends Idioma {
  ramas: Rama[];
}
export interface Senales {
  ramas: number;
  propuestas: number;
  notas: number;
  tachados: number;
  conflictos: number;
  palabras: number;
}
export type Estado = 'idea' | 'borrador' | 'revision' | 'listo' | 'publicado';
export interface Articulo {
  id: string;
  slug: string;
  estado: Estado;
  serie: Serie | null;
  serie_parte: number | null;
  fecha: string | null;
  etiquetas: string[];
  creado: string;
  cambiado: string;
  idiomas: IdiomaDetalle[];
  senales: Senales;
}
export interface VarianteBloque {
  id: string;
  contenido: string;
  origen: string;
  creado: string;
  cambiado: string;
  rev: number;
}
export type TipoBloque = 'parrafo' | 'subtitulo' | 'lista' | 'cita' | 'codigo' | 'imagen' | 'audio' | 'youtube';
export interface Bloque {
  id: string;
  posicion: string;
  tipo: TipoBloque;
  variante_activa_id: string | null;
  variantes: VarianteBloque[];
}
export interface VarianteSeccion {
  id: string;
  titulo: string;
  origen: string;
  creado: string;
  rev: number;
  bloques: Bloque[];
}
export interface Seccion {
  id: string;
  posicion: string;
  variante_activa_id: string | null;
  variantes: VarianteSeccion[];
}
export interface Documento {
  rama: Rama;
  idioma: Idioma;
  articulo_id: string;
  secciones: Seccion[];
}
export interface Guardado {
  conflicto: boolean;
  variante: VarianteBloque;
}
export interface EntradaHistorial {
  id: number;
  contenido: string;
  origen: string;
  dispositivo: string;
  creado: string;
}
export interface Medio {
  id: string;
  sha256: string;
  nombre: string;
  tipo: string;
  tamano: number;
  creado: string;
}
export interface Avisos {
  tachados: number;
  propuestas: number;
  notas: number;
  conflictos: number;
  imagenes_sin_alt: number;
  imagenes: number;
  audios: number;
  videos: number;
}
export interface Exportacion {
  idioma: string;
  markdown: string;
  avisos: Avisos;
  archivos: { nombre: string; tipo: string }[];
}
export interface ParaX {
  titulo: string;
  html: string;
  texto: string;
  imagenes: string[];
  codigos: { lenguaje: string; codigo: string }[];
  avisos: Avisos;
}
export interface Entre {
  antes?: string | null;
  despues?: string | null;
}

export class ErrorApi extends Error {
  constructor(
    public estado: number,
    mensaje: string,
  ) {
    super(mensaje);
  }
}

/** Se llama con cada 401, para volver a la pantalla de entrar. */
let alSalir: () => void = () => {};
export function cuandoSinSesion(f: () => void) {
  alSalir = f;
}

async function pedir<T>(metodo: string, ruta: string, cuerpo?: unknown): Promise<T> {
  const r = await fetch('/api' + ruta, {
    method: metodo,
    headers: cuerpo === undefined ? {} : { 'content-type': 'application/json' },
    body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
    credentials: 'same-origin',
  });
  if (r.status === 401 && ruta !== '/entrar') alSalir();
  if (!r.ok) {
    let mensaje = `Error ${r.status}`;
    try {
      mensaje = (await r.json()).error ?? mensaje;
    } catch {
      /* sin cuerpo JSON */
    }
    throw new ErrorApi(r.status, mensaje);
  }
  if (r.status === 204) return undefined as T;
  const tipo = r.headers.get('content-type') ?? '';
  return (tipo.includes('json') ? r.json() : r.text()) as Promise<T>;
}

const get = <T>(r: string) => pedir<T>('GET', r);
const post = <T>(r: string, b: unknown = {}) => pedir<T>('POST', r, b);
const patch = <T>(r: string, b: unknown) => pedir<T>('PATCH', r, b);
const put = <T>(r: string, b: unknown) => pedir<T>('PUT', r, b);
const del = <T>(r: string) => pedir<T>('DELETE', r);

export const api = {
  sesion: () => get<{ ok: boolean }>('/sesion'),
  entrar: (clave: string) => post<{ ok: boolean }>('/entrar', { clave }),
  salir: () => post<{ ok: boolean }>('/salir'),

  articulos: () => get<Articulo[]>('/articulos'),
  articulo: (id: string) => get<Articulo>(`/articulos/${id}`),
  crearArticulo: (titulo: string) => post<Articulo>('/articulos', { titulo }),
  cambiarArticulo: (id: string, c: Record<string, unknown>) => patch<Articulo>(`/articulos/${id}`, c),
  borrarArticulo: (id: string) => del<void>(`/articulos/${id}`),
  crearIdioma: (id: string, idioma: string, copiar_de: string | null) => post<Articulo>(`/articulos/${id}/idiomas`, { idioma, copiar_de }),
  cambiarIdioma: (id: string, c: Partial<Omit<Idioma, 'id' | 'idioma'>>) => patch<Articulo>(`/idiomas/${id}`, c),
  borrarIdioma: (id: string) => del<Articulo>(`/idiomas/${id}`),

  exportacion: (idiomaId: string) => get<Exportacion>(`/idiomas/${idiomaId}/exportar`),
  paraX: (idiomaId: string) => get<ParaX>(`/idiomas/${idiomaId}/x`),
  urlZip: (articuloId: string, idiomas: string[]) => `/api/articulos/${articuloId}/exportar.zip?idiomas=${idiomas.join(',')}`,

  rama: (id: string) => get<Documento>(`/ramas/${id}`),
  renombrarRama: (id: string, nombre: string) => patch<Documento>(`/ramas/${id}`, { nombre }),
  borrarRama: (id: string) => del<void>(`/ramas/${id}`),
  forkRama: (id: string, nombre: string) => post<Documento>(`/ramas/${id}/fork`, { nombre }),

  crearSeccion: (ramaId: string, titulo: string, entre: Entre) => post<Documento>(`/ramas/${ramaId}/secciones`, { titulo, ...entre }),
  cambiarSeccion: (id: string, c: { variante_activa_id?: string; mover?: Entre }) => patch<Documento>(`/secciones/${id}`, c),
  borrarSeccion: (id: string) => del<Documento>(`/secciones/${id}`),
  forkSeccion: (id: string, desde?: string) => post<{ documento: Documento; variante_id: string }>(`/secciones/${id}/variantes`, { desde }),
  cambiarVarianteSeccion: (id: string, titulo: string) => patch<Documento>(`/variantes-seccion/${id}`, { titulo }),
  borrarVarianteSeccion: (id: string) => del<Documento>(`/variantes-seccion/${id}`),

  crearBloque: (vsId: string, tipo: TipoBloque, contenido: string, entre: Entre) =>
    post<{ documento: Documento; bloque_id: string }>(`/variantes-seccion/${vsId}/bloques`, { tipo, contenido, ...entre }),
  cambiarBloque: (id: string, c: { tipo?: TipoBloque; variante_activa_id?: string; mover?: Entre }) => patch<Documento>(`/bloques/${id}`, c),
  borrarBloque: (id: string) => del<Documento>(`/bloques/${id}`),
  forkBloque: (id: string, contenido?: string) => post<{ documento: Documento; variante_id: string }>(`/bloques/${id}/variantes`, { contenido }),

  guardar: (varianteId: string, contenido: string, rev_base: number) => put<Guardado>(`/variantes-bloque/${varianteId}`, { contenido, rev_base }),
  borrarVarianteBloque: (id: string) => del<Documento>(`/variantes-bloque/${id}`),
  historial: (id: string) => get<EntradaHistorial[]>(`/variantes-bloque/${id}/historial`),
  restaurar: (id: string, entrada: number) => post<Guardado>(`/variantes-bloque/${id}/restaurar`, { entrada }),
  resolverMarca: (id: string, indice: number, decision: 'aceptar' | 'rechazar') =>
    post<Guardado>(`/variantes-bloque/${id}/marcas`, { indice, decision }),

  etiquetas: () => get<Etiqueta[]>('/etiquetas'),
  series: () => get<SerieConCuenta[]>('/series'),
  crearSerie: (nombre: string) => post<Serie>('/series', { nombre }),

  async subirMedio(archivo: File): Promise<Medio> {
    const f = new FormData();
    f.append('archivo', archivo);
    const r = await fetch('/api/medios', { method: 'POST', body: f, credentials: 'same-origin' });
    if (!r.ok) throw new ErrorApi(r.status, (await r.json().catch(() => ({}))).error ?? `Error ${r.status}`);
    return r.json();
  },
};

export const NOMBRE_ESTADO: Record<Estado, string> = {
  idea: 'Idea',
  borrador: 'Borrador',
  revision: 'Revisión',
  listo: 'Listo',
  publicado: 'Publicado',
};
export const ESTADOS = Object.keys(NOMBRE_ESTADO) as Estado[];

export function cuando(iso: string): string {
  const d = new Date(iso);
  const s = (Date.now() - d.getTime()) / 1000;
  if (s < 60) return 'ahora';
  if (s < 3600) return `hace ${Math.floor(s / 60)} min`;
  const hoy = new Date();
  const hora = d.toLocaleTimeString('es', { hour: '2-digit', minute: '2-digit' });
  if (d.toDateString() === hoy.toDateString()) return `hoy ${hora}`;
  const ayer = new Date(hoy.getTime() - 86400000);
  if (d.toDateString() === ayer.toDateString()) return `ayer ${hora}`;
  return d.toLocaleDateString('es', { day: 'numeric', month: 'short' });
}

export function quien(origen: string): string {
  if (origen === 'tu') return 'Tú';
  if (origen === 'conflicto') return 'Conflicto';
  if (origen.startsWith('ia:')) return origen.slice(3);
  return origen;
}
