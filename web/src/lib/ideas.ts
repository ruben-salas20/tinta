// Ideas: publicaciones sin fecha. Sin fecha solo pueden ser idea o preparando.
// Programada y publicada exigen fecha y canal; el servicio hace la misma comprobación.

import { NOMBRE_ESTADO_PUB, type Canal, type EstadoPub, type Formato, type Publicacion } from './api';

export function exigeFechaYCanal(estado: EstadoPub): boolean {
  return estado === 'programada' || estado === 'publicada';
}

export function estadoPermitido(estado: EstadoPub, tieneFecha: boolean, tieneCanal: boolean): boolean {
  return !exigeFechaYCanal(estado) || (tieneFecha && tieneCanal);
}

/** Lo que falta para programar o publicar, o null si ya está todo. */
export function faltaParaSalir(tieneFecha: boolean, tieneCanal: boolean): string | null {
  const falta: string[] = [];
  if (!tieneFecha) falta.push('fecha');
  if (!tieneCanal) falta.push('canal');
  return falta.length ? falta.join(' y ') : null;
}

/** De la más reciente a la más antigua por el último cambio. */
export function ordenarIdeas(lista: Publicacion[]): Publicacion[] {
  return [...lista].sort((a, b) => Date.parse(b.cambiado) - Date.parse(a.cambiado));
}

/** El título, o si no hay, la primera línea del texto, o el título del artículo. */
export function tituloDe(p: Publicacion): string {
  const titulo = p.titulo.trim();
  if (titulo) return titulo;
  const primero = p.partes.map((x) => x.texto.trim()).find((t) => t !== '');
  if (primero) return primero.split('\n')[0].slice(0, 80);
  return p.articulo?.titulo || 'Sin título';
}

export function resumenIdea(p: Publicacion, canales: Canal[]) {
  const nombre = canales.find((c) => c.id === p.canal_id)?.nombre;
  return {
    titulo: tituloDe(p),
    canal: p.canal_id ? (nombre ?? p.canal_id) : 'Sin canal',
    estado: NOMBRE_ESTADO_PUB[p.estado],
  };
}

/** El formato que se toma al elegir un canal: con varias partes, uno que las admita si lo hay. */
export function formatoPara(formatos: Formato[], nPartes: number): Formato | undefined {
  return formatos.find((f) => nPartes > 1 && f.partes) ?? formatos[0];
}
