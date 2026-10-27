import { describe, expect, it } from 'vitest';
import type { Publicacion } from './api';
import { estadoPermitido, faltaParaSalir, ordenarIdeas, resumenIdea, tituloDe } from './ideas';

const base: Publicacion = {
  id: 'p1',
  canal_id: null,
  formato: null,
  fecha: null,
  hora: null,
  estado: 'idea',
  titulo: '',
  partes: [],
  articulo: null,
  enlace: null,
  origen: 'tu',
  creado: '2026-10-01T10:00:00Z',
  cambiado: '2026-10-01T10:00:00Z',
  rev: 1,
};
const con = (x: Partial<Publicacion>): Publicacion => ({ ...base, ...x });
const canales = [{ id: 'x', nombre: 'X', icono: 'x', formatos: [], activo: true, posicion: '1' }];

describe('estados sin fecha o sin canal', () => {
  it('idea y preparando se permiten siempre', () => {
    expect(estadoPermitido('idea', false, false)).toBe(true);
    expect(estadoPermitido('preparando', false, false)).toBe(true);
  });

  it('programada y publicada piden fecha y canal', () => {
    expect(estadoPermitido('programada', false, true)).toBe(false);
    expect(estadoPermitido('publicada', true, false)).toBe(false);
    expect(estadoPermitido('programada', true, true)).toBe(true);
    expect(estadoPermitido('publicada', true, true)).toBe(true);
  });

  it('dice qué falta', () => {
    expect(faltaParaSalir(false, false)).toBe('fecha y canal');
    expect(faltaParaSalir(false, true)).toBe('fecha');
    expect(faltaParaSalir(true, false)).toBe('canal');
    expect(faltaParaSalir(true, true)).toBeNull();
  });
});

describe('orden de las ideas', () => {
  it('de la más reciente a la más antigua por cambiado', () => {
    const a = con({ id: 'a', cambiado: '2026-10-01T10:00:00Z' });
    const b = con({ id: 'b', cambiado: '2026-10-03T08:00:00Z' });
    const c = con({ id: 'c', cambiado: '2026-10-02T09:00:00Z' });
    expect(ordenarIdeas([a, b, c]).map((p) => p.id)).toEqual(['b', 'c', 'a']);
  });

  it('no cambia el array original', () => {
    const lista = [con({ id: 'a' }), con({ id: 'b', cambiado: '2026-12-01T00:00:00Z' })];
    ordenarIdeas(lista);
    expect(lista.map((p) => p.id)).toEqual(['a', 'b']);
  });
});

describe('texto que resume una idea', () => {
  it('usa el título si lo hay', () => {
    expect(tituloDe(con({ titulo: '  Sobre cachés  ', partes: [{ texto: 'otra cosa', medios: [] }] }))).toBe('Sobre cachés');
  });

  it('si no, la primera línea de la primera parte con texto', () => {
    const p = con({ partes: [{ texto: '  \n', medios: [] }, { texto: 'Primera línea\nsegunda', medios: [] }] });
    expect(tituloDe(p)).toBe('Primera línea');
  });

  it('corta la primera línea a 80 caracteres', () => {
    expect(tituloDe(con({ partes: [{ texto: 'a'.repeat(100), medios: [] }] }))).toHaveLength(80);
  });

  it('si no hay nada, el título del artículo o «Sin título»', () => {
    expect(tituloDe(con({ articulo: { id: 'a', titulo: 'Artículo', slug: 's' } }))).toBe('Artículo');
    expect(tituloDe(con({}))).toBe('Sin título');
  });

  it('resumen con canal, o «Sin canal» si no lo tiene', () => {
    expect(resumenIdea(con({ titulo: 'Idea' }), canales)).toEqual({ titulo: 'Idea', canal: 'Sin canal', estado: 'Idea' });
    expect(resumenIdea(con({ titulo: 'Idea', canal_id: 'x', formato: 'post', estado: 'preparando' }), canales)).toEqual({
      titulo: 'Idea',
      canal: 'X',
      estado: 'Preparando',
    });
  });
});
