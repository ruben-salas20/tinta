// Fechas del calendario y texto de las publicaciones. Las fechas son
// AAAA-MM-DD sin zona (la zona la pone el servicio con /api/hoy), así que
// aquí se calcula en UTC para que el huso del navegador no mueva un día.

const DIA = 86400000;

const aFecha = (iso: string) => new Date(iso + 'T00:00:00Z');
const aIso = (d: Date) => d.toISOString().slice(0, 10);

export function sumarDias(iso: string, n: number): string {
  return aIso(new Date(aFecha(iso).getTime() + n * DIA));
}

/** El mismo día del mes `n` meses después, o el último si no existe. */
export function sumarMeses(iso: string, n: number): string {
  const d = aFecha(iso);
  const y = d.getUTCFullYear();
  const m = d.getUTCMonth() + n;
  const ultimo = new Date(Date.UTC(y, m + 1, 0)).getUTCDate();
  return aIso(new Date(Date.UTC(y, m, Math.min(d.getUTCDate(), ultimo))));
}

/** El lunes de la semana de `iso`. */
export function lunes(iso: string): string {
  const dia = (aFecha(iso).getUTCDay() + 6) % 7; // 0 = lunes
  return sumarDias(iso, -dia);
}

export function primeroDelMes(iso: string): string {
  return iso.slice(0, 8) + '01';
}

/** Las semanas, de lunes a domingo, que cubren el mes de `iso`. */
export function semanasDelMes(iso: string): string[][] {
  const inicio = primeroDelMes(iso);
  const mes = inicio.slice(0, 7);
  const semanas: string[][] = [];
  let l = lunes(inicio);
  while (semanas.length === 0 || l.slice(0, 7) === mes) {
    semanas.push(semanaDe(l));
    l = sumarDias(l, 7);
  }
  return semanas;
}

export function semanaDe(iso: string): string[] {
  const l = lunes(iso);
  return Array.from({ length: 7 }, (_, i) => sumarDias(l, i));
}

const fmt = (iso: string, o: Intl.DateTimeFormatOptions) => aFecha(iso).toLocaleDateString('es', { timeZone: 'UTC', ...o });

/** «lun 2 nov» */
export function fechaCorta(iso: string): string {
  return fmt(iso, { weekday: 'short', day: 'numeric', month: 'short' }).replace(/,/g, '').replace(/\./g, '');
}

/** «lunes, 2 de noviembre» */
export function fechaLarga(iso: string): string {
  return fmt(iso, { weekday: 'long', day: 'numeric', month: 'long' });
}

/** «Noviembre 2026» */
export function nombreMes(iso: string): string {
  const s = fmt(iso, { month: 'long', year: 'numeric' }).replace(' de ', ' ');
  return s.charAt(0).toUpperCase() + s.slice(1);
}

/** «2 – 8 nov 2026» o «26 oct – 1 nov 2026» */
export function nombreSemana(iso: string): string {
  const [a, b] = [semanaDe(iso)[0], semanaDe(iso)[6]];
  const mismo = a.slice(0, 7) === b.slice(0, 7);
  const ini = mismo ? String(Number(a.slice(8))) : fmt(a, { day: 'numeric', month: 'short' }).replace('.', '');
  return `${ini} – ${fmt(b, { day: 'numeric', month: 'short', year: 'numeric' }).replace('.', '').replace(/ de /g, ' ')}`;
}

/** Caracteres como los cuenta una persona (un emoji es uno). */
export function contar(texto: string): number {
  return [...texto].length;
}

/**
 * Parte un texto que pasa del límite: la primera mitad cabe y termina en un
 * espacio si lo hay; la segunda lleva el resto. Si cabe, no lo toca.
 */
export function partir(texto: string, limite: number): [string, string] {
  const c = [...texto];
  if (c.length <= limite) return [texto, ''];
  const cabe = c.slice(0, limite).join('');
  // Mejor cortar en un final de frase, si queda en la segunda mitad del límite.
  const frase = Math.max(cabe.lastIndexOf('. '), cabe.lastIndexOf('? '), cabe.lastIndexOf('! '));
  const espacio = cabe.lastIndexOf(' ');
  const justo = /\s/.test(c[limite]);
  const corte = justo ? cabe.length : frase > cabe.length / 2 ? frase + 1 : espacio > 0 ? espacio : cabe.length;
  return [cabe.slice(0, corte).trimEnd(), (cabe.slice(corte) + c.slice(limite).join('')).trimStart()];
}

/** La hora entera de «HH:MM», o null si la publicación no tiene hora. */
export const horaEntera = (hora: string | null): number | null => (hora ? Number(hora.slice(0, 2)) : null);

const horasEntre = (desde: number, hasta: number) => Array.from({ length: hasta - desde + 1 }, (_, i) => desde + i);

/**
 * Las horas que muestra la rejilla: de la primera a la última con publicación,
 * con un mínimo de 8. Sin ninguna, de 9:00 a 16:00.
 */
export function rangoHoras(horas: number[]): number[] {
  if (!horas.length) return horasEntre(9, 16);
  let desde = Math.min(...horas);
  let hasta = Math.max(...horas);
  if (hasta - desde < 7) {
    hasta = Math.min(23, desde + 7);
    desde = Math.max(0, hasta - 7);
  }
  return horasEntre(desde, hasta);
}

/** Las publicaciones de la hora `h`; con `h` null, las que no tienen hora. */
export function enHora<T extends { hora: string | null }>(ps: T[], h: number | null): T[] {
  return ps.filter((p) => horaEntera(p.hora) === h);
}
