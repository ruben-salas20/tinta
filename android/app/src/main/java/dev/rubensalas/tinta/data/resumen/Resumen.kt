package dev.rubensalas.tinta.data.resumen

import dev.rubensalas.tinta.data.local.PublicacionE
import java.time.LocalDate

/** Lo que dice el aviso del resumen diario. Se calcula con la copia local, sin red. */
data class Resumen(
    val hoy: List<PublicacionE>,
    val atrasadas: List<PublicacionE>,
    val manana: List<PublicacionE>,
) {
    val vacio get() = hoy.isEmpty() && atrasadas.isEmpty()

    val titulo: String get() = when {
        hoy.isEmpty() && atrasadas.isEmpty() -> "Hoy no toca publicar nada"
        hoy.isEmpty() -> "Nada para hoy, ${plural(atrasadas.size, "atrasada", "atrasadas")}"
        atrasadas.isEmpty() -> "Hoy: ${plural(hoy.size, "publicación", "publicaciones")}"
        else -> "Hoy: ${plural(hoy.size, "publicación", "publicaciones")} y ${plural(atrasadas.size, "atrasada", "atrasadas")}"
    }

    /** Una línea por publicación: hoy primero, luego lo atrasado. */
    fun lineas(nombreCanal: (String?) -> String): List<String> =
        hoy.map { "${it.hora ?: "Hoy"} · ${nombreCanal(it.canalId)} · ${it.titulo.ifBlank { "Sin título" }}" } +
            atrasadas.map { "Atrasada (${it.fecha}) · ${nombreCanal(it.canalId)} · ${it.titulo.ifBlank { "Sin título" }}" }

    companion object {
        /** Las ideas sin fecha no entran: no tienen día que avisar. */
        fun calcular(publicaciones: List<PublicacionE>, hoy: LocalDate): Resumen {
            val vivas = publicaciones.filter { it.borrado == 0 && it.estado != "publicada" && it.fecha != null }
            val d = hoy.toString()
            val orden = compareBy<PublicacionE>({ it.fecha }, { it.hora == null }, { it.hora })
            return Resumen(
                hoy = vivas.filter { it.fecha == d }.sortedWith(orden),
                atrasadas = vivas.filter { it.fecha?.let { f -> f < d } == true }.sortedWith(orden),
                manana = vivas.filter { it.fecha == hoy.plusDays(1).toString() }.sortedWith(orden),
            )
        }

        fun plural(n: Int, una: String, varias: String) = "$n ${if (n == 1) una else varias}"
    }
}
