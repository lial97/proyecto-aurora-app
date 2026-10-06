package app.aurora.audio

import kotlin.math.max

/**
 * Preset del ecualizador. [preamp] en dB relativos al volumen original (0 = sin cambio).
 * Los valores son los de VLC (su preamplificador "+12" equivale a 0 aquí).
 */
data class EqPreset(val name: String, val vlcName: String, val preamp: Float, val bands: List<Float>)

object Eq {
    /** Bandas del ecualizador de VLC 3 (Hz). */
    val bandsHz = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
    val bandLabels = listOf("31", "62", "125", "250", "500", "1K", "2K", "4K", "8K", "16K")
    const val MIN = -20f
    /** VLC admite hasta +20 en su escala, que aquí es +8 dB sobre el original. */
    const val MAX_PREAMP = 8f
    const val MAX = 20f

    private fun p(n: String, v: String, pre: Float, vararg b: Float) = EqPreset(n, v, pre - 12f, b.toList())

    val presets: List<EqPreset> = listOf(
        p("Plano", "Flat", 12f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        p("Clásica", "Classical", 12f, 0f, 0f, 0f, 0f, 0f, 0f, -7.2f, -7.2f, -7.2f, -9.6f),
        p("Club", "Club", 6f, 0f, 0f, 8f, 5.6f, 5.6f, 5.6f, 3.2f, 0f, 0f, 0f),
        p("Baile", "Dance", 5f, 9.6f, 7.2f, 2.4f, 0f, 0f, -5.6f, -7.2f, -7.2f, 0f, 0f),
        p("Graves", "Full bass", 5f, -8f, 9.6f, 9.6f, 5.6f, 1.6f, -4f, -8f, -10.4f, -11.2f, -11.2f),
        p("Graves y agudos", "Full bass and treble", 4f, 7.2f, 5.6f, 0f, -7.2f, -4.8f, 1.6f, 8f, 11.2f, 12f, 12f),
        p("Agudos", "Full treble", 3f, -9.6f, -9.6f, -9.6f, -4f, 2.4f, 11.2f, 16f, 16f, 16f, 16.8f),
        p("Auriculares", "Headphones", 4f, 4.8f, 11.2f, 5.6f, -3.2f, -2.4f, 1.6f, 4.8f, 9.6f, 12.8f, 14.4f),
        p("Sala grande", "Large Hall", 5f, 10.4f, 10.4f, 5.6f, 5.6f, 0f, -4.8f, -4.8f, -4.8f, 0f, 0f),
        p("En vivo", "Live", 7f, -4.8f, 0f, 4f, 5.6f, 5.6f, 5.6f, 4f, 2.4f, 2.4f, 2.4f),
        p("Fiesta", "Party", 6f, 7.2f, 7.2f, 0f, 0f, 0f, 0f, 0f, 0f, 7.2f, 7.2f),
        p("Pop", "Pop", 6f, -1.6f, 4.8f, 7.2f, 8f, 5.6f, 0f, -2.4f, -2.4f, -1.6f, -1.6f),
        p("Reggae", "Reggae", 8f, 0f, 0f, 0f, -5.6f, 0f, 6.4f, 6.4f, 0f, 0f, 0f),
        p("Rock", "Rock", 5f, 8f, 4.8f, -5.6f, -8f, -3.2f, 4f, 8.8f, 11.2f, 11.2f, 11.2f),
        p("Ska", "Ska", 6f, -2.4f, -4.8f, -4f, 0f, 4f, 5.6f, 8.8f, 9.6f, 11.2f, 9.6f),
        p("Suave", "Soft", 5f, 4.8f, 1.6f, 0f, -2.4f, 0f, 4f, 8f, 9.6f, 11.2f, 12f),
        p("Rock suave", "Soft rock", 7f, 4f, 4f, 2.4f, 0f, -4f, -5.6f, -3.2f, 0f, 2.4f, 8.8f),
        p("Techno", "Techno", 5f, 8f, 5.6f, 0f, -5.6f, -4.8f, 0f, 8f, 9.6f, 9.6f, 8.8f),
    )
}

/**
 * Ajustes del ecualizador. [preset] = índice en [Eq.presets], o -1 si el usuario movió las bandas.
 * [protect] baja el preamplificador lo necesario para que ninguna banda suba por encima del original:
 * así el ecualizador nunca satura (medido: "Rock" de VLC recorta el 21,8 % de las muestras sin protección).
 */
data class EqSettings(
    val enabled: Boolean = false,
    val preset: Int = 0,
    val preamp: Float = 0f,
    val bands: List<Float> = List(10) { 0f },
    val protect: Boolean = true,
) {
    val isCustom: Boolean get() = preset < 0

    /**
     * Preamplificador que realmente se aplica (dB sobre el original).
     * Las bandas de VLC se solapan y la música tiene casi toda su energía en los graves, así que subir
     * una banda grave suma mucho más que subir una aguda. Medido con canciones reales y los 18 presets:
     * bajar 2,5 × la banda grave más alta (≤ 500 Hz), 1,5 × la aguda más alta y 1,6 × la suma de las
     * 3 bandas vecinas más subidas (las subidas contiguas se suman) evita todo recorte.
     */
    val effectivePreamp: Float get() {
        if (!protect) return preamp
        val pos = bands.map { it.coerceAtLeast(0f) }
        val low = pos.take(5).maxOrNull() ?: 0f
        val high = pos.drop(5).maxOrNull() ?: 0f
        val adjacent = if (pos.size >= 3) (0..pos.size - 3).maxOf { pos[it] + pos[it + 1] + pos[it + 2] } else 0f
        return minOf(preamp, -maxOf(low * LOW_WEIGHT, high * HIGH_WEIGHT, adjacent * ADJ_WEIGHT))
    }

    /** Cuánto baja de verdad la protección (dentro del rango de VLC; 0 si no actúa). */
    val protectionDb: Float get() = (preamp - (vlcPreamp - 12f)).coerceAtLeast(0f)

    /** Valor para VLC (su escala: +12 = original), dentro de su rango ±20. */
    val vlcPreamp: Float get() = (effectivePreamp + 12f).coerceIn(-20f, 20f)

    fun withPreset(i: Int): EqSettings { val p = Eq.presets[i]; return copy(preset = i, preamp = p.preamp, bands = p.bands) }

    fun withBand(index: Int, value: Float): EqSettings {
        val v = (kotlin.math.round(value * 10f) / 10f)
        return if (index < 0) copy(preamp = v.coerceIn(Eq.MIN, Eq.MAX_PREAMP), preset = -1)
        else copy(bands = bands.toMutableList().also { it[index] = v.coerceIn(Eq.MIN, Eq.MAX) }, preset = -1)
    }

    fun encode(): String = listOf(if (enabled) "1" else "0", preset.toString(), preamp.toString(), bands.joinToString(","), if (protect) "1" else "0").joinToString(";")

    companion object {
        const val LOW_WEIGHT = 2.5f
        const val HIGH_WEIGHT = 1.5f
        const val ADJ_WEIGHT = 1.6f

        fun decode(s: String?): EqSettings = runCatching {
            val f = s!!.split(';')
            EqSettings(f[0] == "1", f[1].toInt(), f[2].toFloat(), f[3].split(',').map { it.toFloat() }.also { require(it.size == 10) }, f.getOrNull(4) != "0")
        }.getOrDefault(EqSettings())
    }
}
