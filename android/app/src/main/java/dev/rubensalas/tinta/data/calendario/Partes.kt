package dev.rubensalas.tinta.data.calendario

/**
 * Parte un post que se pasa de [limite] en dos, por el último espacio que
 * cabe: no corta palabras. Si no hay ningún espacio, corta en el límite.
 */
fun partir(texto: String, limite: Int): Pair<String, String> {
    if (texto.length <= limite) return texto to ""
    val corte = texto.lastIndexOf(' ', limite).takeIf { it > 0 } ?: limite
    return texto.substring(0, corte).trimEnd() to texto.substring(corte).trimStart()
}

/** Caracteres como los cuenta X: por puntos de código, así un emoji cuenta uno. */
fun longitud(texto: String): Int = texto.codePointCount(0, texto.length)

/** Las imágenes se ven como miniatura; el resto de medios, con un icono y su nombre. */
fun esImagen(tipo: String): Boolean = tipo.startsWith("image/")
