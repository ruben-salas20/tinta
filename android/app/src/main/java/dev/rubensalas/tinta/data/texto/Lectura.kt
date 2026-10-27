package dev.rubensalas.tinta.data.texto

import dev.rubensalas.tinta.data.local.RangoAutoria

/** El signo que marca una nota en el texto leído. */
const val SIGNO_NOTA = " ✎"

/** Cómo se ve un trozo del texto leído. La interfaz le pone colores y tamaños. */
enum class Estilo { Normal, Negrita, Cursiva, Codigo, Enlace, Tachado, Anadido, Ancla, Nota }

/**
 * Un trozo del texto tal como se lee. [crudo] dice, para cada carácter UTF-16,
 * en qué punto de código del contenido crudo está. Es null si el trozo no sale
 * tal cual del contenido (una marca o el signo de una nota): no se resalta.
 */
class Pieza(val texto: String, val estilo: Estilo, val crudo: IntArray?)

/**
 * Los trozos que se leen de un bloque de tipo [tipo]. [contenido] es el
 * CriticMarkup crudo: sus prefijos (`### `, `- `, `> `, las cercas de código)
 * se quitan aquí, y el Markdown en línea se convierte en estilos.
 */
fun piezasLectura(tipo: String, contenido: String): List<Pieza> {
    val cuerpo = cuerpoDe(tipo, contenido)
    val piezas = mutableListOf<Pieza>()
    for (u in Critic.ubicar(cuerpo.texto)) {
        when (val t = u.trozo) {
            is Trozo.Texto -> enLinea(cuerpo, u.desde, t.texto, piezas)
            is Trozo.Borrado -> piezas += Pieza(t.texto, Estilo.Tachado, null)
            is Trozo.Anadido -> piezas += Pieza(t.texto, Estilo.Anadido, null)
            is Trozo.Cambio -> {
                piezas += Pieza(t.antes, Estilo.Tachado, null)
                piezas += Pieza(t.despues, Estilo.Anadido, null)
            }
            is Trozo.Nota -> piezas += Pieza(SIGNO_NOTA, Estilo.Nota, null)
            is Trozo.Resaltado -> piezas += Pieza(t.texto, Estilo.Ancla, null)
        }
    }
    return piezas
}

/**
 * Los tramos del texto leído que pinta cada rango de autoría, en índices de
 * String del texto que muestra [piezasLectura]. Un rango que cae en una marca
 * se salta, y un rango fuera del contenido no pinta nada.
 */
fun tramosAutoria(piezas: List<Pieza>, rangos: List<RangoAutoria>): List<IntRange> {
    if (rangos.isEmpty()) return emptyList()
    val total = piezas.sumOf { it.texto.length }
    val pintado = BooleanArray(total)
    var pos = 0
    for (p in piezas) {
        val crudo = p.crudo
        if (crudo != null) {
            for (j in p.texto.indices) {
                val c = crudo[j]
                if (rangos.any { c >= it.desde && c < it.hasta }) pintado[pos + j] = true
            }
        }
        pos += p.texto.length
    }
    val tramos = mutableListOf<IntRange>()
    var inicio = -1
    for (i in 0..total) {
        val dentro = i < total && pintado[i]
        if (dentro && inicio < 0) inicio = i
        if (!dentro && inicio >= 0) {
            tramos += inicio until i
            inicio = -1
        }
    }
    return tramos
}

/** El texto de un bloque sin sus prefijos, con la posición de cada carácter en el contenido crudo. */
private class Cuerpo(val texto: String, val crudo: IntArray)

private fun cuerpoDe(tipo: String, s: String): Cuerpo {
    val c = Armado(s)
    when (tipo) {
        "subtitulo" -> c.poner(s.indexOfFirst { it != '#' && it != ' ' }.let { if (it < 0) s.length else it }, s.length)
        "cita" -> c.lineas { ini, fin ->
            var k = ini
            if (k < fin && s[k] == '>') k++
            while (k < fin && s[k].isWhitespace()) k++
            c.poner(k, fin)
        }
        "lista" -> c.lineas { ini, fin ->
            var k = ini
            while (k < fin && s[k].isWhitespace()) k++
            // La sangría se ve igual, con espacios.
            for (u in ini until k) c.poner(u, ' ')
            if (k + 1 < fin && (s[k] == '-' || s[k] == '*') && s[k + 1] == ' ') {
                c.poner(k, '•')
                c.poner(k + 1)
                k += 2
            }
            c.poner(k, fin)
        }
        "codigo" -> {
            // Quita «```» y la línea de la lengua, y el «```» final.
            val tras = if (s.startsWith("```")) 3 else 0
            val salto = s.indexOf('\n', tras)
            val ini = if (salto >= 0) salto + 1 else tras
            var fin = s.length
            if (fin - ini >= 3 && s.endsWith("```")) fin -= 3
            while (fin > ini && s[fin - 1].isWhitespace()) fin--
            c.poner(ini, fin)
        }
        else -> c.poner(0, s.length)
    }
    return c.cuerpo()
}

/** Va sumando caracteres del texto sin prefijos, cada uno con su punto de código de origen. */
private class Armado(private val s: String) {
    private val texto = StringBuilder()
    private val crudo = ArrayList<Int>()
    private val puntos = puntosDeCodigo(s)

    fun poner(u: Int, c: Char = s[u]) {
        texto.append(c)
        crudo += puntos[u]
    }

    fun poner(desde: Int, hasta: Int) {
        for (u in desde until hasta) poner(u)
    }

    /** Llama a [f] con el inicio y el fin de cada línea, y pone el salto de línea entre ellas. */
    fun lineas(f: (Int, Int) -> Unit) {
        var ini = 0
        while (true) {
            val fin = s.indexOf('\n', ini).let { if (it < 0) s.length else it }
            f(ini, fin)
            if (fin >= s.length) break
            poner(fin)
            ini = fin + 1
        }
    }

    fun cuerpo() = Cuerpo(texto.toString(), crudo.toIntArray())
}

/** El punto de código (no la unidad UTF-16) de cada carácter de [s]. Un emoji ocupa dos caracteres y un punto. */
private fun puntosDeCodigo(s: String): IntArray {
    val r = IntArray(s.length)
    var punto = 0
    var i = 0
    while (i < s.length) {
        val n = Character.charCount(s.codePointAt(i))
        for (k in 0 until n) r[i + k] = punto
        punto++
        i += n
    }
    return r
}

private val EN_LINEA = Regex("""\*\*(.+?)\*\*|\*(.+?)\*|`(.+?)`|\[(.+?)]\((.+?)\)""")

/** Quita el Markdown en línea de un trozo de texto normal y lo añade a [salida]. */
private fun enLinea(c: Cuerpo, desde: Int, t: String, salida: MutableList<Pieza>) {
    var i = 0
    for (m in EN_LINEA.findAll(t)) {
        if (m.range.first > i) salida += trozo(c, desde + i, t.substring(i, m.range.first), Estilo.Normal)
        val (grupo, estilo) = when {
            m.groups[1] != null -> 1 to Estilo.Negrita
            m.groups[2] != null -> 2 to Estilo.Cursiva
            m.groups[3] != null -> 3 to Estilo.Codigo
            else -> 4 to Estilo.Enlace
        }
        val g = m.groups[grupo]!!
        salida += trozo(c, desde + g.range.first, g.value, estilo)
        i = m.range.last + 1
    }
    if (i < t.length) salida += trozo(c, desde + i, t.substring(i), Estilo.Normal)
}

/** Un trozo que sale tal cual del contenido, desde la posición [inicio] del texto sin prefijos. */
private fun trozo(c: Cuerpo, inicio: Int, texto: String, estilo: Estilo) =
    Pieza(texto, estilo, IntArray(texto.length) { c.crudo[inicio + it] })
