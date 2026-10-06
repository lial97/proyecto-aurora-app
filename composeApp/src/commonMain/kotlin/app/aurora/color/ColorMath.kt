package app.aurora.color

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** HSL con tono en grados [0, 360) y saturación/luminosidad en [0, 1]. */
data class Hsl(val h: Float, val s: Float, val l: Float)

fun rgbToHsl(r: Int, g: Int, b: Int): Hsl = rgbToHsl(r.toFloat(), g.toFloat(), b.toFloat())

fun rgbToHsl(r: Float, g: Float, b: Float): Hsl {
    val rf = r / 255f
    val gf = g / 255f
    val bf = b / 255f
    val mx = max(rf, max(gf, bf))
    val mn = min(rf, min(gf, bf))
    val l = (mx + mn) / 2f
    if (mx == mn) return Hsl(0f, 0f, l)
    val d = mx - mn
    val s = if (l > 0.5f) d / (2f - mx - mn) else d / (mx + mn)
    val h = when (mx) {
        rf -> (gf - bf) / d + (if (gf < bf) 6f else 0f)
        gf -> (bf - rf) / d + 2f
        else -> (rf - gf) / d + 4f
    } * 60f
    return Hsl(h, s, l)
}

internal fun normalizeHue(h: Float): Float = ((h % 360f) + 360f) % 360f

internal fun hueDistance(a: Float, b: Float): Float {
    val d = abs(normalizeHue(a) - normalizeHue(b))
    return min(d, 360f - d)
}
