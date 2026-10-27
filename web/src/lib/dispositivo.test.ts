import { describe, expect, it } from 'vitest';
import { nombreDispositivo } from './api';

describe('nombreDispositivo', () => {
  it('nombra los dispositivos conocidos y calla los demás', () => {
    expect(nombreDispositivo('android')).toBe('Móvil');
    expect(nombreDispositivo('web')).toBe('Portátil');
    expect(nombreDispositivo('mcp')).toBe('Agente');
    expect(nombreDispositivo(null)).toBeNull();
    expect(nombreDispositivo('raro')).toBeNull();
  });
});
