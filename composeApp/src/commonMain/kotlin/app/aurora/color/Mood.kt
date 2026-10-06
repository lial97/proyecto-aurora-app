package app.aurora.color

/** Rango de tempo que recorre la atmósfera: azul (lento) -> rojo (movido). Spec §3.1 */
const val BPM_LO = 72
const val BPM_HI = 138

/** Tempo que se usa cuando la pista no trae BPM en sus metadatos. Spec §11 */
const val DEFAULT_BPM = 100

enum class MoodLabel(val text: String) {
    CALM("Tranquila"),
    MEDIUM("Ritmo medio"),
    UPBEAT("Movida"),
}

/**
 * Ánimo derivado del tempo.
 * @property energy 0..1
 * @property hue tono base en grados (225 azul -> 360 rojo)
 * @property speed multiplicador de duraciones de animación (1.45 lento -> 0.60 rápido)
 */
data class Mood(
    val bpm: Int,
    val energy: Float,
    val hue: Float,
    val label: MoodLabel,
    val speed: Float,
)

fun moodFromBpm(bpm: Int?): Mood {
    val b = bpm ?: DEFAULT_BPM
    val e = ((b - BPM_LO).toFloat() / (BPM_HI - BPM_LO)).coerceIn(0f, 1f)
    val label = when {
        e < 0.34f -> MoodLabel.CALM
        e < 0.67f -> MoodLabel.MEDIUM
        else -> MoodLabel.UPBEAT
    }
    return Mood(bpm = b, energy = e, hue = 225f + e * 135f, label = label, speed = 1.45f - e * 0.85f)
}
