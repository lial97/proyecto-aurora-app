package app.aurora.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Ecualizador de 10 bandas en software (Android; en escritorio lo hace VLC).
 * Un filtro "peaking" por banda (receta de Robert Bristow-Johnson) en las mismas frecuencias que VLC,
 * con un ancho de una octava, más la ganancia general (preamplificador con la protección anti-saturación).
 * Trabaja con muestras float intercaladas (L R L R…) y nunca deja pasar valores fuera de −1…1.
 */
class TenBandEq(private val sampleRate: Int, private val channels: Int) {
    private class Biquad(val b0: Float, val b1: Float, val b2: Float, val a1: Float, val a2: Float)

    private var filters: List<Biquad> = emptyList()
    private var gain = 1f
    /** Estado de cada filtro por canal: x1, x2, y1, y2. */
    private var state = FloatArray(0)

    /** `null` o desactivado = sin cambios (las muestras pasan tal cual). */
    var settings: EqSettings? = null
        set(value) {
            field = value
            rebuild(value)
        }

    val active: Boolean get() = filters.isNotEmpty() || gain != 1f

    private fun rebuild(eq: EqSettings?) {
        if (eq == null || !eq.enabled) { filters = emptyList(); gain = 1f; return }
        val nyquist = sampleRate / 2f
        val fs = Eq.bandsHz.zip(eq.bands).filter { (hz, db) -> db != 0f && hz < nyquist * .95f }.map { (hz, db) -> peaking(hz.toFloat(), db) }
        // Si cambia la cantidad de filtros activos, el estado se reinicia (evita mezclar estados).
        if (fs.size != filters.size) state = FloatArray(fs.size * channels * 4)
        filters = fs
        gain = dbToGain(eq.effectivePreamp.coerceIn(-40f, Eq.MAX_PREAMP))
    }

    private fun peaking(f0: Float, db: Float, q: Float = 1.41f): Biquad {
        val a = 10f.pow(db / 40f)
        val w0 = 2 * PI.toFloat() * f0 / sampleRate
        val alpha = sin(w0) / (2 * q)
        val cw = cos(w0)
        val a0 = 1 + alpha / a
        return Biquad((1 + alpha * a) / a0, (-2 * cw) / a0, (1 - alpha * a) / a0, (-2 * cw) / a0, (1 - alpha / a) / a0)
    }

    /** Procesa [count] muestras intercaladas de [buf] en su lugar. */
    fun process(buf: FloatArray, count: Int = buf.size) {
        val fs = filters
        if (fs.isEmpty() && gain == 1f) return
        val st = state
        var i = 0
        while (i < count) {
            val ch = i % channels
            var x = buf[i] * gain
            for (k in fs.indices) {
                val f = fs[k]
                val s = (k * channels + ch) * 4
                val y = f.b0 * x + f.b1 * st[s] + f.b2 * st[s + 1] - f.a1 * st[s + 2] - f.a2 * st[s + 3]
                st[s + 1] = st[s]; st[s] = x
                st[s + 3] = st[s + 2]; st[s + 2] = y
                x = y
            }
            buf[i] = if (x > 1f) 1f else if (x < -1f) -1f else x
            i++
        }
    }

    fun reset() { state.fill(0f) }

    companion object {
        fun dbToGain(db: Float) = 10f.pow(db / 20f)
    }
}
