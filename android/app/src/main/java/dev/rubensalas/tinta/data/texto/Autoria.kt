package dev.rubensalas.tinta.data.texto

import dev.rubensalas.tinta.data.local.RangoAutoria

/**
 * Los rangos de autoría después de cambiar [viejo] por [nuevo], igual que
 * `autoria::desplazar` del servicio: así siguen al texto sin conexión, y la
 * sincronización los deja como los calcula el servicio.
 *
 * Cuenta puntos de código, como el servicio. Lo anterior al cambio se queda,
 * lo posterior se desplaza y lo que toca la zona cambiada se recorta.
 */
fun desplazar(rangos: List<RangoAutoria>, viejo: String, nuevo: String): List<RangoAutoria> {
    if (rangos.isEmpty()) return rangos
    val a = viejo.codePoints().toArray()
    val b = nuevo.codePoints().toArray()
    var prefijo = 0
    while (prefijo < a.size && prefijo < b.size && a[prefijo] == b[prefijo]) prefijo++
    val maximo = minOf(a.size, b.size) - prefijo
    var sufijo = 0
    while (sufijo < maximo && a[a.size - 1 - sufijo] == b[b.size - 1 - sufijo]) sufijo++
    val finViejo = a.size - sufijo
    val delta = b.size - a.size

    val r = mutableListOf<RangoAutoria>()
    for (g in rangos) {
        val hastaAntes = minOf(g.hasta, prefijo)
        if (g.desde < hastaAntes) r += g.copy(hasta = hastaAntes)
        val desdeDespues = maxOf(g.desde, finViejo)
        if (desdeDespues < g.hasta) r += g.copy(desde = desdeDespues + delta, hasta = g.hasta + delta)
    }
    return r
}
