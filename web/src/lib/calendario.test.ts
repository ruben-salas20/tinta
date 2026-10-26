import { describe, expect, it } from 'vitest';
import { contar, fechaCorta, lunes, nombreMes, nombreSemana, partir, semanaDe, semanasDelMes, sumarDias, sumarMeses } from './calendario';

describe('fechas', () => {
  it('lunes de la semana', () => {
    expect(lunes('2026-10-26')).toBe('2026-10-26'); // lunes
    expect(lunes('2026-11-01')).toBe('2026-10-26'); // domingo
    expect(lunes('2026-11-04')).toBe('2026-11-02');
  });

  it('semanas del mes empiezan en lunes y cubren el mes entero', () => {
    const s = semanasDelMes('2026-11-15');
    expect(s[0][0]).toBe('2026-10-26');
    expect(s.at(-1)![6]).toBe('2026-12-06');
    expect(s).toHaveLength(6);
    expect(s.every((w) => w.length === 7)).toBe(true);
    // Febrero de 2027 empieza en lunes y tiene 28 días: cuatro semanas justas.
    expect(semanasDelMes('2027-02-10')).toHaveLength(4);
  });

  it('sumar días y meses cruza meses y años', () => {
    expect(sumarDias('2026-12-31', 1)).toBe('2027-01-01');
    expect(sumarMeses('2026-01-31', 1)).toBe('2026-02-28');
    expect(sumarMeses('2026-11-15', -11)).toBe('2025-12-15');
    expect(semanaDe('2026-11-04')).toEqual(['2026-11-02', '2026-11-03', '2026-11-04', '2026-11-05', '2026-11-06', '2026-11-07', '2026-11-08']);
  });

  it('nombres en español', () => {
    expect(nombreMes('2026-11-02')).toBe('Noviembre 2026');
    expect(fechaCorta('2026-11-02')).toBe('lun 2 nov');
    expect(nombreSemana('2026-11-04')).toBe('2 – 8 nov 2026');
    expect(nombreSemana('2026-10-28')).toBe('26 oct – 1 nov 2026');
  });
});

describe('partir', () => {
  it('no toca lo que cabe', () => {
    expect(partir('hola', 10)).toEqual(['hola', '']);
  });

  it('corta en el último espacio y no pierde texto', () => {
    const t = 'uno dos tres cuatro cinco';
    const [a, b] = partir(t, 10);
    expect(a).toBe('uno dos');
    expect(b).toBe('tres cuatro cinco');
    // Si el corte cae justo en un espacio, no retrocede una palabra.
    expect(partir(t, 12)).toEqual(['uno dos tres', 'cuatro cinco']);
  });

  it('prefiere un final de frase', () => {
    const [a, b] = partir('Primera frase larga aquí. Segunda frase que sigue y sigue', 40);
    expect(a).toBe('Primera frase larga aquí.');
    expect(b).toBe('Segunda frase que sigue y sigue');
  });

  it('sin espacios, corta en el límite; cuenta emojis como uno', () => {
    expect(partir('abcdefghij', 4)).toEqual(['abcd', 'efghij']);
    expect(contar('🧵 hilo')).toBe(6);
  });
});
