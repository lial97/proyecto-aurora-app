package app.aurora.domain

/** Palabra con su momento de inicio (LRC mejorado: `<mm:ss.xx>palabra`). */
data class LyricWord(val startMs: Long, val text: String)

/**
 * Línea de letra sincronizada. [words] solo existe si la letra trae tiempos por palabra;
 * si no, el karaoke reparte el tiempo de la línea entre sus letras.
 */
data class LyricLine(val startMs: Long, val text: String, val words: List<LyricWord> = emptyList()) {
    val startSec: Int get() = (startMs / 1000).toInt()
    val isInstrumental: Boolean get() = text == INSTRUMENTAL

    companion object {
        const val INSTRUMENTAL = "♪"
    }
}

private val lineTag = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
private val wordTag = Regex("""<(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?>""")
private val offsetTag = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)

private fun ms(min: String, sec: String, frac: String): Long {
    val f = when (frac.length) {
        0 -> 0L
        1 -> frac.toLong() * 100
        2 -> frac.toLong() * 10
        else -> frac.take(3).toLong()
    }
    return min.toLong() * 60_000 + sec.toLong() * 1000 + f
}

/**
 * Convierte texto LRC (el formato de LRCLIB) en líneas sincronizadas. Admite varias marcas por línea
 * (estribillos repetidos), tiempos por palabra (LRC mejorado) y `[offset:±ms]`.
 * Las líneas vacías son tramos instrumentales.
 */
fun parseLrc(lrc: String): List<LyricLine> {
    val offset = offsetTag.find(lrc)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    return lrc.lineSequence().flatMap { raw ->
        val tags = lineTag.findAll(raw).toList()
        if (tags.isEmpty() || tags.first().range.first != raw.indexOfFirst { !it.isWhitespace() }) return@flatMap emptySequence()
        val body = raw.substring(tags.last().range.last + 1)
        val marks = wordTag.findAll(body).toList()
        val words = marks.mapIndexedNotNull { i, m ->
            val end = marks.getOrNull(i + 1)?.range?.first ?: body.length
            val w = body.substring(m.range.last + 1, end)
            if (w.isBlank()) null else LyricWord(ms(m.groupValues[1], m.groupValues[2], m.groupValues[3]) - offset, w)
        }
        val text = body.replace(wordTag, "").trim().replace(Regex("\\s+"), " ").ifEmpty { LyricLine.INSTRUMENTAL }
        tags.asSequence().map { m ->
            val start = ms(m.groupValues[1], m.groupValues[2], m.groupValues[3]) - offset
            LyricLine(start.coerceAtLeast(0), text, if (tags.size == 1) words else emptyList())
        }
    }.sortedBy { it.startMs }.toList()
}

/** Índice de la línea que suena en [positionMs], o -1 antes de la primera. */
fun List<LyricLine>.activeIndexAtMs(positionMs: Long): Int = indexOfLast { it.startMs <= positionMs }

fun List<LyricLine>.activeIndexAt(positionSec: Int): Int = activeIndexAtMs(positionSec * 1000L)

/**
 * Cuántas letras de la línea [index] ya se cantaron en [positionMs] (para el relleno del karaoke).
 * Con tiempos por palabra se usan esos; si no, la línea se rellena de forma pareja hasta la siguiente,
 * como máximo a ~90 ms por letra (para no quedar a medias en los silencios largos).
 */
fun List<LyricLine>.sungChars(index: Int, positionMs: Long): Float {
    val line = getOrNull(index) ?: return 0f
    if (line.isInstrumental || positionMs < line.startMs) return 0f
    val len = line.text.length
    if (line.words.isNotEmpty()) {
        var chars = 0f
        var cursor = 0
        line.words.forEachIndexed { i, w ->
            val clean = w.text.trim()
            val at = line.text.indexOf(clean, cursor).takeIf { it >= 0 } ?: cursor
            val wEnd = line.words.getOrNull(i + 1)?.startMs ?: (getOrNull(index + 1)?.startMs ?: (w.startMs + 600))
            val wDur = (wEnd - w.startMs).coerceIn(80, 1500)
            if (positionMs >= w.startMs) {
                val f = ((positionMs - w.startMs).toFloat() / wDur).coerceIn(0f, 1f)
                chars = at + clean.length * f
            }
            cursor = at + clean.length
        }
        return chars.coerceIn(0f, len.toFloat())
    }
    val next = getOrNull(index + 1)?.startMs ?: (line.startMs + 5000)
    val dur = (next - line.startMs).coerceAtMost(len * 90L + 600).coerceAtLeast(300)
    return ((positionMs - line.startMs).toFloat() / dur).coerceIn(0f, 1f) * len
}
