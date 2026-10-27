package dev.rubensalas.tinta.data.texto

/**
 * El texto de un bloque tal como se edita en el móvil.
 *
 * Cada marca de revisión (tachado, añadido, cambio, nota y el ancla de una
 * nota) se cambia por un solo carácter de uso privado de Unicode. El campo de
 * texto los pinta con su estilo y la edición no puede partirlos: si un cambio
 * borra alguno, se rechaza ([aplicar]). Así escribir nunca pierde una marca;
 * solo se quitan con sus botones de aceptar o descartar.
 *
 * El prefijo Markdown del tipo de bloque (`### `, `- `, `> `) no está en el
 * texto: se quita al leer y se pone al volver a CriticMarkup.
 */
class Edicion private constructor(
    /** El texto que edita el campo, con un marcador por marca. */
    val texto: String,
    /** La marca de cada marcador, por orden de aparición. */
    val marcas: List<Trozo>,
    /** El tipo de bloque, que decide el prefijo de cada línea. */
    private val tipo: String,
) {
    /** Vuelve a CriticMarkup, con el prefijo de cada línea. */
    fun aMarkup(): String =
        if (texto.isEmpty()) "" else aMarkup(texto.split('\n').joinToString("\n") { ponerPrefijo(tipo, it) })

    private fun aMarkup(t: String): String {
        val trozos = mutableListOf<Trozo>()
        val plano = StringBuilder()
        for (c in t) {
            val i = indice(c)
            if (i == null) {
                plano.append(c)
            } else {
                if (plano.isNotEmpty()) trozos += Trozo.Texto(plano.toString()).also { plano.clear() }
                trozos += marcas[i]
            }
        }
        if (plano.isNotEmpty()) trozos += Trozo.Texto(plano.toString())
        return Critic.escribir(trozos)
    }

    /**
     * El texto nuevo que propone el campo, o null si quita o reordena algún
     * marcador. Escribir un `{--` a mano sí crea una marca: es CriticMarkup.
     */
    fun aplicar(nuevo: String): Edicion? {
        val antes = texto.filter { indice(it) != null }
        val despues = nuevo.filter { indice(it) != null }
        if (antes != despues) return null
        return Edicion(nuevo, marcas, tipo)
    }

    /**
     * Tacha el trozo [desde]..[hasta] del texto. Null si la selección está vacía
     * o toca una marca: no se tacha sobre lo que ya está marcado.
     */
    fun tachar(desde: Int, hasta: Int): Edicion? = envolver(desde, hasta) { Trozo.Borrado(it) }

    /** Nota anclada a la selección, o en el cursor si no hay selección. */
    fun anotar(desde: Int, hasta: Int, nota: String): Edicion? {
        if (nota.isBlank()) return null
        val (a, b) = minOf(desde, hasta) to maxOf(desde, hasta)
        if (a == b) {
            val conNota = marcas + Trozo.Nota(nota.trim())
            val t = texto.substring(0, a) + marcador(conNota.size - 1) + texto.substring(a)
            return Edicion(t, conNota, tipo)
        }
        val trozo = texto.substring(a, b)
        if (trozo.any { indice(it) != null }) return null
        val nuevas = marcas + Trozo.Resaltado(trozo) + Trozo.Nota(nota.trim())
        val t = texto.substring(0, a) + marcador(nuevas.size - 2) + marcador(nuevas.size - 1) + texto.substring(b)
        return Edicion(t, nuevas, tipo)
    }

    private fun envolver(desde: Int, hasta: Int, f: (String) -> Trozo): Edicion? {
        val (a, b) = minOf(desde, hasta) to maxOf(desde, hasta)
        if (a == b) return null
        val trozo = texto.substring(a, b)
        if (trozo.any { indice(it) != null }) return null
        val nuevas = marcas + f(trozo)
        return Edicion(texto.substring(0, a) + marcador(nuevas.size - 1) + texto.substring(b), nuevas, tipo)
    }

    /** Las marcas en el orden del texto, con su índice para `Critic.resolver`. */
    fun marcasEnOrden(): List<Trozo> = texto.mapNotNull { c -> indice(c)?.let { marcas[it] } }

    companion object {
        private const val BASE = 0xE000
        private const val MAX = 0x1800

        fun indice(c: Char): Int? = (c.code - BASE).takeIf { it in 0 until MAX }

        fun marcador(i: Int): Char = (BASE + i).toChar()

        /** Lee el CriticMarkup de un bloque de tipo [tipo] y quita su prefijo de cada línea. */
        fun desde(markup: String, tipo: String): Edicion {
            // Un marcador escrito de verdad en el texto se perdería: se quita.
            val trozos = Critic.leer(markup.filter { indice(it) == null })
            val marcas = mutableListOf<Trozo>()
            val t = StringBuilder()
            for (x in trozos) {
                if (x is Trozo.Texto) {
                    t.append(x.texto)
                } else {
                    marcas += x
                    t.append(marcador(marcas.size - 1))
                }
            }
            val texto = t.toString().split('\n').joinToString("\n") { quitarPrefijo(tipo, it) }
            return Edicion(texto, marcas, tipo)
        }
    }
}

/** El prefijo Markdown que lleva cada línea de un bloque de tipo [tipo]. */
fun ponerPrefijo(tipo: String, linea: String): String = when (tipo) {
    "subtitulo" -> "### $linea"
    "lista" -> "- $linea"
    "cita" -> if (linea.isEmpty()) ">" else "> $linea"
    else -> linea
}

/** Quita el prefijo de [ponerPrefijo]. Una línea sin prefijo se queda igual. */
fun quitarPrefijo(tipo: String, linea: String): String = when (tipo) {
    "subtitulo" -> linea.removePrefix("### ")
    "lista" -> linea.removePrefix("- ")
    "cita" -> if (linea.startsWith("> ")) linea.drop(2) else linea.removePrefix(">")
    else -> linea
}
