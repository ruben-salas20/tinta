import { describe, expect, it } from 'vitest';
import { esImagen, iconoDeMedio } from './medios';

describe('tipo de medio', () => {
  it('solo las imágenes se pintan como miniatura', () => {
    expect(esImagen('image/png')).toBe(true);
    expect(esImagen('image/svg+xml')).toBe(true);
    expect(esImagen('audio/mpeg')).toBe(false);
    expect(esImagen('video/mp4')).toBe(false);
    expect(esImagen(undefined)).toBe(false);
  });

  it('cada audio tiene su icono y lo demás, el genérico', () => {
    expect(iconoDeMedio('audio/ogg')).toBe('audio');
    expect(iconoDeMedio('video/webm')).toBe('nota');
    expect(iconoDeMedio(undefined)).toBe('nota');
  });
});
