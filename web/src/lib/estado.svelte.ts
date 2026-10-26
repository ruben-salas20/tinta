// Estado compartido por las vistas: ruta, avisos, diálogos y catálogo.

import { api, type Articulo, type Canal, type Etiqueta, type SerieConCuenta } from './api';

export const nav = $state({ ruta: location.pathname, buscar: location.search });

export function ir(ruta: string) {
  history.pushState(null, '', ruta);
  const u = new URL(ruta, location.origin);
  nav.ruta = u.pathname;
  nav.buscar = u.search;
  window.scrollTo(0, 0);
}

window.addEventListener('popstate', () => {
  nav.ruta = location.pathname;
  nav.buscar = location.search;
});

export interface Aviso {
  id: number;
  texto: string;
  tipo: 'info' | 'error';
  accion?: { texto: string; hacer: () => void };
}
export const avisos = $state<Aviso[]>([]);
let siguiente = 1;

export function quitarAviso(id: number) {
  const i = avisos.findIndex((a) => a.id === id);
  if (i >= 0) avisos.splice(i, 1);
}

/** Con `accion`, el aviso lleva un botón y dura más, para dar tiempo a pulsarlo. */
export function avisar(texto: string, tipo: Aviso['tipo'] = 'info', accion?: Aviso['accion']) {
  const id = siguiente++;
  avisos.push({ id, texto, tipo, accion });
  setTimeout(() => quitarAviso(id), accion ? 8000 : tipo === 'error' ? 7000 : 4000);
}

export function fallo(e: unknown) {
  avisar(e instanceof Error ? e.message : String(e), 'error');
}

interface Pregunta {
  titulo: string;
  valor: string;
  ayuda?: string;
  aceptar: string;
  resolver: (v: string | null) => void;
}
export const dialogo = $state<{ actual: Pregunta | null }>({ actual: null });

/** Pide un texto en una ventana. Devuelve null si se cancela. */
export function pedirTexto(titulo: string, valor = '', aceptar = 'Aceptar', ayuda?: string): Promise<string | null> {
  return new Promise((resolver) => {
    dialogo.actual = {
      titulo,
      valor,
      aceptar,
      ayuda,
      resolver: (v) => {
        dialogo.actual = null;
        resolver(v);
      },
    };
  });
}

interface Confirmacion {
  texto: string;
  aceptar: string;
  ayuda?: string;
  resolver: (ok: boolean) => void;
}
export const confirmacion = $state<{ actual: Confirmacion | null }>({ actual: null });

export function confirmar(texto: string, aceptar = 'Borrar', ayuda?: string): Promise<boolean> {
  return new Promise((resolver) => {
    confirmacion.actual = {
      texto,
      aceptar,
      ayuda,
      resolver: (ok) => {
        confirmacion.actual = null;
        resolver(ok);
      },
    };
  });
}

export const catalogo = $state<{
  articulos: Articulo[];
  series: SerieConCuenta[];
  etiquetas: Etiqueta[];
  canales: Canal[];
  /** «Hoy» según la zona del servicio, AAAA-MM-DD. */
  hoy: string;
  cargado: boolean;
}>({
  articulos: [],
  series: [],
  etiquetas: [],
  canales: [],
  hoy: new Date().toISOString().slice(0, 10),
  cargado: false,
});

/** Sube cada vez que cambian las publicaciones, para quien las muestre. */
export const difusion = $state({ version: 0 });

export const canal = (id: string) => catalogo.canales.find((c) => c.id === id);

export async function recargarCatalogo() {
  try {
    const [a, s, e, c, h] = await Promise.all([api.articulos(), api.series(), api.etiquetas(), api.canales(), api.hoy()]);
    catalogo.articulos = a;
    catalogo.series = s;
    catalogo.etiquetas = e;
    catalogo.canales = c;
    catalogo.hoy = h.fecha;
    catalogo.cargado = true;
  } catch (e) {
    fallo(e);
  }
}

/** Pregunta antes, elimina el artículo y ofrece deshacerlo. Devuelve true si lo eliminó. */
export async function eliminarArticulo(id: string, titulo: string): Promise<boolean> {
  const nombre = titulo.trim() || 'Sin título';
  const ok = await confirmar(
    `¿Eliminar «${nombre}»?`,
    'Eliminar',
    'Se elimina en todos sus idiomas y ramas. También se quitan del calendario sus publicaciones sin publicar.',
  );
  if (!ok) return false;
  try {
    await api.borrarArticulo(id);
  } catch (e) {
    fallo(e);
    return false;
  }
  catalogo.articulos = catalogo.articulos.filter((a) => a.id !== id);
  difusion.version++;
  avisar(`Eliminaste «${nombre}».`, 'info', {
    texto: 'Deshacer',
    hacer: async () => {
      try {
        await api.restaurarArticulo(id);
        await recargarCatalogo();
        difusion.version++;
      } catch (e) {
        fallo(e);
      }
    },
  });
  recargarCatalogo();
  return true;
}
