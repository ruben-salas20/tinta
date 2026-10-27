// Qué se muestra de un medio según su tipo MIME (como "image/png").

/** true si el medio es una imagen: se pinta su miniatura. */
export function esImagen(tipo: string | undefined): boolean {
  return tipo?.startsWith('image/') ?? false;
}

/** El icono de un medio que no es imagen. Los audios tienen el suyo; el resto, uno genérico. */
export function iconoDeMedio(tipo: string | undefined): 'audio' | 'nota' {
  return tipo?.startsWith('audio/') ? 'audio' : 'nota';
}
