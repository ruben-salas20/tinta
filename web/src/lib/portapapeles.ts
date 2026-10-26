// Copiar con formato. navigator.clipboard solo existe en contextos seguros
// (HTTPS o localhost); en la copia de desarrollo por http hace falta la vía
// antigua, que copia lo que haya seleccionado en la página.

function porSeleccion(html: string | null, texto: string): boolean {
  const caja = document.createElement(html ? 'div' : 'pre');
  if (html) caja.innerHTML = html;
  else caja.textContent = texto;
  Object.assign(caja.style, { position: 'fixed', left: '-9999px', top: '0', whiteSpace: html ? 'normal' : 'pre' });
  document.body.append(caja);
  const r = document.createRange();
  r.selectNodeContents(caja);
  const sel = getSelection()!;
  sel.removeAllRanges();
  sel.addRange(r);
  const ok = document.execCommand('copy');
  sel.removeAllRanges();
  caja.remove();
  return ok;
}

export async function copiarTexto(texto: string): Promise<void> {
  if (navigator.clipboard?.writeText) return navigator.clipboard.writeText(texto);
  if (!porSeleccion(null, texto)) throw new Error('El navegador no dejó copiar.');
}

export async function copiarHtml(html: string, texto: string): Promise<void> {
  if (navigator.clipboard?.write && typeof ClipboardItem !== 'undefined') {
    return navigator.clipboard.write([
      new ClipboardItem({
        'text/html': new Blob([html], { type: 'text/html' }),
        'text/plain': new Blob([texto], { type: 'text/plain' }),
      }),
    ]);
  }
  if (!porSeleccion(html, texto)) throw new Error('El navegador no dejó copiar.');
}
