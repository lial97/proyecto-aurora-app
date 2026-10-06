package app.aurora.color

import androidx.compose.ui.graphics.Color

enum class AccentSource { COVER, TEMPO }

/**
 * Colores de la canción actual (spec §3.3 y §3.4).
 * La atmósfera (fondo + 3 luces) sale siempre del tempo; el énfasis sale de la
 * portada si tiene un color vivo y, si no, del tempo.
 */
data class AuroraPalette(
    val mood: Mood,
    val background: Color,
    val light1: Color,
    val light2: Color,
    val light3: Color,
    val accent: Color,
    val accentSource: AccentSource,
)

fun Hsl.toColor(): Color = Color.hsl(normalizeHue(h), s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))

fun buildPalette(bpm: Int?, coverAccent: Hsl?): AuroraPalette {
    val mood = moodFromBpm(bpm)
    val h = mood.hue
    val tempoAccent = Hsl(h + 10f, 0.95f, 0.67f)
    return AuroraPalette(
        mood = mood,
        background = Hsl(h, 0.48f, 0.10f).toColor(),
        light1 = Hsl(h, 0.85f, 0.62f).toColor(),
        light2 = Hsl(h + 28f, 0.90f, 0.64f).toColor(),
        light3 = Hsl(h - 32f, 0.80f, 0.58f).toColor(),
        accent = (coverAccent ?: tempoAccent).toColor(),
        accentSource = if (coverAccent != null) AccentSource.COVER else AccentSource.TEMPO,
    )
}
