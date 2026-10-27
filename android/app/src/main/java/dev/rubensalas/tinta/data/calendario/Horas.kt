package dev.rubensalas.tinta.data.calendario

/** La hora entera de «HH:MM», o null si la publicación no tiene hora. */
fun horaEntera(hora: String?): Int? = hora?.take(2)?.toIntOrNull()

/**
 * Las horas que muestra la rejilla de un día: de la primera a la última con
 * publicación, con un mínimo de 8. Sin ninguna, de 9:00 a 16:00.
 */
fun rangoHoras(horas: List<Int>): IntRange {
    if (horas.isEmpty()) return 9..16
    var desde = horas.min()
    var hasta = horas.max()
    if (hasta - desde < 7) {
        hasta = minOf(23, desde + 7)
        desde = maxOf(0, hasta - 7)
    }
    return desde..hasta
}
