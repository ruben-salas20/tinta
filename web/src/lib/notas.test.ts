import { describe, expect, it } from 'vitest';
import { editarNota, notas } from './critic';

const S = 'Hola {--x--} {==mundo==}{>>¿seguro?<<} y {~~a~>b~~}{>>ia:claude<<} fin{>>ia:claude: Mira esto<<}.';

describe('notas', () => {
  it('lee notas con su índice de marca, ancla y autor', () => {
    expect(notas(S)).toEqual([
      { indice: 1, orden: 0, texto: '¿seguro?', ia: null, ancla: 'mundo' },
      { indice: 3, orden: 1, texto: 'Mira esto', ia: 'claude', ancla: null },
    ]);
  });
  it('edita una nota sin tocar lo demás', () => {
    expect(editarNota(S, 0, 'cambiada')).toBe(S.replace('¿seguro?', 'cambiada'));
    expect(editarNota(S, 1, 'otra')).toBe(S.replace('Mira esto', 'otra'));
    expect(editarNota('a{>>x<<}', 0, 'p<<}q')).toBe('a{>>p<< }q<<}');
  });
});
