package app.aurora.domain

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Audio en mono, de −1 a 1, para calcular el tempo. */
class Pcm(val samples: FloatArray, val sampleRate: Int)

/**
 * Tempo aproximado (BPM) de un fragmento de audio (~20 s a ~11 kHz):
 * 1. espectro cada 10 ms (ventanas de 512 muestras) y "flujo espectral": cuánto sube cada frecuencia (golpes);
 * 2. se le quita la tendencia de medio segundo;
 * 3. autocorrelación entre 50 y 220 BPM, con preferencia suave por ~120 (evita elegir el doble o la mitad);
 * 4. el resultado se lleva al rango 70–180, donde caen casi todas las canciones.
 * Probado con canciones reales contra el BPM de sus etiquetas: acierta o da la mitad/el doble.
 * `null` si el fragmento es muy corto o está en silencio.
 */
fun estimateBpm(pcm: Pcm): Int? {
    val onset = onsetEnvelope(pcm) ?: return null
    val n = onset.size
    val mean = onset.average().toFloat()
    for (i in 0 until n) onset[i] -= mean
    fun ac(lag: Int): Float {
        var s = 0f
        for (i in 0 until n - lag) s += onset[i] * onset[i + lag]
        return s / (n - lag)
    }
    val zero = ac(0)
    if (zero <= 1e-9f) return null
    var bestLag = -1
    var bestScore = 0f
    for (lag in (FPS * 60 / 220).toInt()..(FPS * 60 / 50).toInt()) {
        val octaves = ln(FPS * 60 / lag / 120f) / ln(2f)
        val score = ac(lag) * exp(-0.5f * octaves * octaves)
        if (score > bestScore) { bestScore = score; bestLag = lag }
    }
    if (bestLag < 0) return null
    val a = ac(bestLag - 1); val b = ac(bestLag); val c = ac(bestLag + 1)
    val den = a - 2 * b + c
    val shift = if (abs(den) > 1e-12f) (0.5f * (a - c) / den).coerceIn(-0.5f, 0.5f) else 0f
    var bpm = FPS * 60 / (bestLag + shift)
    while (bpm < 70f) bpm *= 2f
    while (bpm > 180f) bpm /= 2f
    return bpm.roundToInt()
}

private const val FPS = 100f
private const val WINDOW = 512

/** Flujo espectral cada 10 ms, sin tendencia y sin valores negativos; `null` si hay menos de 6 s o es silencio. */
private fun onsetEnvelope(pcm: Pcm): FloatArray? {
    val x = pcm.samples
    val hop = pcm.sampleRate / FPS
    val frames = ((x.size - WINDOW) / hop).toInt()
    if (frames < 600 || rms(x) < 1e-4f) return null
    val win = FloatArray(WINDOW) { (0.5 - 0.5 * cos(2 * PI * it / (WINDOW - 1))).toFloat() }
    val re = FloatArray(WINDOW); val im = FloatArray(WINDOW)
    val bins = WINDOW / 2 + 1
    var prev = FloatArray(bins)
    var cur = FloatArray(bins)
    val flux = FloatArray(frames)
    for (f in 0 until frames) {
        val start = (f * hop).toInt()
        for (i in 0 until WINDOW) { re[i] = x[start + i] * win[i]; im[i] = 0f }
        fft(re, im)
        var s = 0f
        for (k in 0 until bins) {
            cur[k] = ln(1f + 100f * sqrt(re[k] * re[k] + im[k] * im[k]))
            if (f > 0) s += maxOf(0f, cur[k] - prev[k])
        }
        flux[f] = s
        val t = prev; prev = cur; cur = t
    }
    // Quitar la tendencia: restar el promedio de medio segundo alrededor.
    val half = 25
    val prefix = DoubleArray(frames + 1)
    for (i in 0 until frames) prefix[i + 1] = prefix[i] + flux[i]
    return FloatArray(frames) { i ->
        val lo = maxOf(0, i - half); val hi = minOf(frames, i + half)
        maxOf(0f, flux[i] - ((prefix[hi] - prefix[lo]) / (hi - lo)).toFloat())
    }
}

/** FFT de base 2, en el lugar (tamaño potencia de 2). */
private fun fft(re: FloatArray, im: FloatArray) {
    val n = re.size
    var j = 0
    for (i in 1 until n) {
        var bit = n shr 1
        while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
        j = j xor bit
        if (i < j) { var t = re[i]; re[i] = re[j]; re[j] = t; t = im[i]; im[i] = im[j]; im[j] = t }
    }
    var len = 2
    while (len <= n) {
        val ang = -2 * PI / len
        val wr = cos(ang).toFloat(); val wi = sin(ang).toFloat()
        var i = 0
        while (i < n) {
            var cr = 1f; var ci = 0f
            for (k in 0 until len / 2) {
                val a = i + k; val b = a + len / 2
                val tr = re[b] * cr - im[b] * ci
                val ti = re[b] * ci + im[b] * cr
                re[b] = re[a] - tr; im[b] = im[a] - ti
                re[a] += tr; im[a] += ti
                val ncr = cr * wr - ci * wi
                ci = cr * wi + ci * wr; cr = ncr
            }
            i += len
        }
        len = len shl 1
    }
}

/** Media cuadrática, para descartar fragmentos en silencio. */
fun rms(samples: FloatArray): Float {
    var s = 0.0
    for (x in samples) s += x * x
    return sqrt(s / samples.size.coerceAtLeast(1)).toFloat()
}
