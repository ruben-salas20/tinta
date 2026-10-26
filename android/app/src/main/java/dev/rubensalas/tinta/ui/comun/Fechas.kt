package dev.rubensalas.tinta.ui.comun

import java.time.LocalDate

private val DIAS = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")
private val DIAS_CORTOS = listOf("lun", "mar", "mié", "jue", "vie", "sáb", "dom")
private val MESES = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")
private val MESES_CORTOS = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sept", "oct", "nov", "dic")

/** «Lunes 26 de octubre». */
fun fechaLarga(d: LocalDate) = "${DIAS[d.dayOfWeek.value - 1].replaceFirstChar { it.uppercase() }} ${d.dayOfMonth} de ${MESES[d.monthValue - 1]}"

fun diaCorto(d: LocalDate) = DIAS_CORTOS[d.dayOfWeek.value - 1]

/** «lun 2 nov». */
fun fechaCorta(d: LocalDate) = "${DIAS_CORTOS[d.dayOfWeek.value - 1]} ${d.dayOfMonth} ${MESES_CORTOS[d.monthValue - 1]}"

fun fecha(s: String): LocalDate? = runCatching { LocalDate.parse(s) }.getOrNull()

/** «hoy», «mañana», «jue 29», o la fecha corta si está lejos. */
fun cuando(s: String, hoy: LocalDate): String {
    val d = fecha(s) ?: return s
    return when (d) {
        hoy -> "hoy"
        hoy.plusDays(1) -> "mañana"
        hoy.minusDays(1) -> "ayer"
        else -> if (d.isAfter(hoy) && d.isBefore(hoy.plusDays(7))) "${DIAS_CORTOS[d.dayOfWeek.value - 1]} ${d.dayOfMonth}" else fechaCorta(d)
    }
}
