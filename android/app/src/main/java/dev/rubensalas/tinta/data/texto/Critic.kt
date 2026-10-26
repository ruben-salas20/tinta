package dev.rubensalas.tinta.data.texto

/**
 * Marcas de revisión en CriticMarkup, igual que `crates/tinta-server/src/critic.rs`.
 * Un `{>>ia:nombre<<}` pegado justo detrás de una marca dice que la hizo un agente;
 * una nota del agente es `{>>ia:nombre: texto<<}`.
 */
sealed interface Trozo {
    data class Texto(val texto: String) : Trozo
    data class Borrado(val texto: String, val ia: String? = null) : Trozo
    data class Anadido(val texto: String, val ia: String? = null) : Trozo
    data class Cambio(val antes: String, val despues: String, val ia: String? = null) : Trozo
    data class Nota(val texto: String, val ia: String? = null) : Trozo
    data class Resaltado(val texto: String) : Trozo
}

/** Todo lo que no es texto normal ni el ancla de una nota: lo que cuenta `resolver`. */
val Trozo.esMarca: Boolean get() = this !is Trozo.Texto && this !is Trozo.Resaltado

private val MARCAS = listOf("{--" to "--}", "{++" to "++}", "{~~" to "~~}", "{>>" to "<<}", "{==" to "==}")

object Critic {
    fun leer(s: String): List<Trozo> {
        val trozos = mutableListOf<Trozo>()
        val texto = StringBuilder()
        var i = 0
        fuera@ while (i < s.length) {
            for ((abre, cierra) in MARCAS) {
                if (!s.startsWith(abre, i)) continue
                val ini = i + abre.length
                val fin = s.indexOf(cierra, ini)
                if (fin < 0) continue
                val dentro = s.substring(ini, fin)
                i = fin + cierra.length
                if (texto.isNotEmpty()) {
                    trozos += Trozo.Texto(texto.toString())
                    texto.clear()
                }
                var ia: String? = null
                if (abre != "{>>" && abre != "{==") {
                    val f = firma(s, i)
                    if (f != null) {
                        ia = f.first
                        i += f.second
                    }
                }
                trozos += when (abre) {
                    "{--" -> Trozo.Borrado(dentro, ia)
                    "{++" -> Trozo.Anadido(dentro, ia)
                    "{~~" -> {
                        val k = dentro.indexOf("~>")
                        if (k >= 0) Trozo.Cambio(dentro.substring(0, k), dentro.substring(k + 2), ia) else Trozo.Borrado(dentro, ia)
                    }
                    "{>>" -> {
                        val r = dentro.removePrefix("ia:")
                        val k = r.indexOf(": ")
                        if (dentro.startsWith("ia:") && k >= 0) Trozo.Nota(r.substring(k + 2), r.substring(0, k)) else Trozo.Nota(dentro, null)
                    }
                    else -> Trozo.Resaltado(dentro)
                }
                continue@fuera
            }
            texto.append(s[i])
            i++
        }
        if (texto.isNotEmpty()) trozos += Trozo.Texto(texto.toString())
        return trozos
    }

    /** `{>>ia:nombre<<}` en la posición [desde]: el nombre y cuánto ocupa. */
    private fun firma(s: String, desde: Int): Pair<String, Int>? {
        if (!s.startsWith("{>>ia:", desde)) return null
        val ini = desde + "{>>ia:".length
        val fin = s.indexOf("<<}", ini)
        if (fin < 0) return null
        val nombre = s.substring(ini, fin)
        if (nombre.isEmpty() || nombre.contains(": ")) return null
        return nombre to ("{>>ia:".length + nombre.length + 3)
    }

    fun escribir(trozos: List<Trozo>): String = buildString {
        fun firma(ia: String?) = ia?.let { "{>>ia:$it<<}" } ?: ""
        for (t in trozos) append(
            when (t) {
                is Trozo.Texto -> t.texto
                is Trozo.Borrado -> "{--${t.texto}--}${firma(t.ia)}"
                is Trozo.Anadido -> "{++${t.texto}++}${firma(t.ia)}"
                is Trozo.Cambio -> "{~~${t.antes}~>${t.despues}~~}${firma(t.ia)}"
                is Trozo.Nota -> if (t.ia != null) "{>>ia:${t.ia}: ${t.texto}<<}" else "{>>${t.texto}<<}"
                is Trozo.Resaltado -> "{==${t.texto}==}"
            },
        )
    }

    /** El texto que se publica: lo decidido y nada de lo pendiente. */
    fun limpio(s: String): String = leer(s).joinToString("") {
        when (it) {
            is Trozo.Texto -> it.texto
            is Trozo.Resaltado -> it.texto
            is Trozo.Borrado -> if (it.ia != null) it.texto else ""
            is Trozo.Anadido, is Trozo.Nota -> ""
            is Trozo.Cambio -> it.antes
        }
    }

    /** Como [limpio], y junta los espacios dobles que deja quitar un trozo. Solo para prosa. */
    fun limpioProsa(s: String): String = limpio(s).lines().joinToString("\n") { l ->
        val cuerpo = l.trimStart()
        val sangria = l.substring(0, l.length - cuerpo.length)
        sangria + cuerpo.trimEnd().replace(Regex(" {2,}"), " ")
    }.trim('\n')

    data class Recuento(val tachados: Int = 0, val propuestas: Int = 0, val notas: Int = 0)

    fun contar(s: String): Recuento {
        var t = 0
        var p = 0
        var n = 0
        for (x in leer(s)) when (x) {
            is Trozo.Borrado -> if (x.ia == null) t++ else p++
            is Trozo.Anadido, is Trozo.Cambio -> p++
            is Trozo.Nota -> n++
            else -> Unit
        }
        return Recuento(t, p, n)
    }

    enum class Decision { Aceptar, Rechazar }

    /**
     * Resuelve la marca número [indice], contando solo marcas desde 0, como
     * `critic::resolver`. Null si no existe.
     */
    fun resolver(s: String, indice: Int, decision: Decision): String? {
        val trozos = leer(s).toMutableList()
        val pos = trozos.withIndex().filter { it.value.esMarca }.getOrNull(indice)?.index ?: return null
        val acepta = decision == Decision.Aceptar
        val nuevo = when (val t = trozos[pos]) {
            is Trozo.Borrado -> if (acepta) "" else t.texto
            is Trozo.Anadido -> if (acepta) t.texto else ""
            is Trozo.Cambio -> if (acepta) t.despues else t.antes
            is Trozo.Nota -> {
                val anterior = trozos.getOrNull(pos - 1)
                if (anterior is Trozo.Resaltado) trozos[pos - 1] = Trozo.Texto(anterior.texto)
                ""
            }
            else -> error("no es una marca")
        }
        trozos[pos] = Trozo.Texto(nuevo)
        return escribir(trozos)
    }
}
