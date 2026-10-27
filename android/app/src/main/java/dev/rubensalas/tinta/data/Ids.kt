package dev.rubensalas.tinta.data

import java.security.MessageDigest
import java.security.SecureRandom

private val azar = SecureRandom()

/** Id nuevo, como los del servicio: 24 caracteres hexadecimales en minúscula. */
fun nuevoId(): String {
    val b = ByteArray(12)
    azar.nextBytes(b)
    return b.joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

/** Como `modelo::id_de`: los 12 primeros bytes del SHA-256 de [texto], en hexadecimal. */
fun idDe(texto: String): String =
    MessageDigest.getInstance("SHA-256").digest(texto.toByteArray()).take(12).joinToString("") { "%02x".format(it.toInt() and 0xff) }

/** Id de la copia de [original] en una copia con [semilla]: el mismo que calcula el servicio. */
fun derivado(semilla: String, original: String) = idDe("$semilla:$original")

/** Id de una etiqueta nueva: fijo por nombre, para crearla igual sin conexión. */
fun idEtiqueta(nombre: String) = idDe("etiqueta:$nombre")

/**
 * Posiciones fraccionarias, igual que `crates/tinta-server/src/posicion.rs`:
 * entre dos claves siempre cabe otra, en base 62 con orden ASCII.
 */
object Posicion {
    private const val DIGITOS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    private const val BASE = 62

    fun entre(antes: String?, despues: String?): String {
        val a = (antes ?: "").map { DIGITOS.indexOf(it) }
        val b = despues?.map { DIGITOS.indexOf(it) }
        if (b != null) require(compara(a, b) < 0) { "posiciones desordenadas: $antes >= $despues" }
        return medio(a, b).joinToString("") { DIGITOS[it].toString() }
    }

    private fun compara(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until minOf(a.size, b.size)) if (a[i] != b[i]) return a[i] - b[i]
        return a.size - b.size
    }

    private fun medio(a: List<Int>, b: List<Int>?): List<Int> {
        if (b != null) {
            var comun = 0
            while (comun < b.size && (a.getOrNull(comun) ?: 0) == b[comun]) comun++
            if (comun > 0) return b.subList(0, comun) + medio(a.drop(comun), b.drop(comun))
        }
        val da = a.firstOrNull() ?: 0
        val db = b?.get(0) ?: BASE
        if (db - da > 1) return listOf((da + db) / 2)
        if (b != null && b.size > 1) return listOf(b[0])
        return listOf(da) + medio(a.drop(1), null)
    }
}
