package app.aurora.color

import kotlin.math.abs

/** Lado de la miniatura que se analiza. La plataforma reduce la portada a este tamaño. */
const val ACCENT_SAMPLE_SIZE = 48

/**
 * Color de énfasis más vivo de una portada (spec §3.2).
 *
 * @param argbPixels píxeles ARGB de la portada reducida (idealmente 48 x 48).
 * @return el énfasis ajustado para fondo oscuro, o `null` si la portada no tiene
 *         un color vivo (gris, blanco y negro, muy oscura).
 */
fun extractAccent(argbPixels: IntArray): Hsl? {
    val weight = FloatArray(24)
    val sumR = FloatArray(24)
    val sumG = FloatArray(24)
    val sumB = FloatArray(24)

    for (p in argbPixels) {
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        val c = rgbToHsl(r, g, b)
        if (c.l < 0.15f || c.l > 0.92f || c.s < 0.25f) continue
        val w = c.s * c.s * (1f - abs(c.l - 0.55f))
        val k = (c.h / 15f).toInt() % 24
        weight[k] += w
        sumR[k] += r * w
        sumG[k] += g * w
        sumB[k] += b * w
    }

    var best = -1
    for (k in 0 until 24) if (best < 0 || weight[k] > weight[best]) best = k
    if (best < 0 || weight[best] < 4f) return null

    val w = weight[best]
    val avg = rgbToHsl(sumR[best] / w, sumG[best] / w, sumB[best] / w)
    return Hsl(
        h = avg.h,
        s = maxOf(avg.s, 0.65f),
        l = avg.l.coerceIn(0.58f, 0.72f),
    )
}
