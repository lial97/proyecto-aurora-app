package app.aurora.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import app.aurora.theme.BackdropStyle
import app.aurora.theme.LiveColors
import app.aurora.theme.Ui
import kotlin.math.PI
import kotlin.math.sin

/** Segundos de animación que solo avanzan mientras suena la música (escalados por la velocidad del tempo). */
@Composable
fun rememberPlayClock(playing: Boolean, speed: Float): State<Float> {
    val seconds = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing, speed) {
        if (!playing) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = awaitAnimationFrame()
            seconds.floatValue += (now - last) / 1e9f / speed
            last = now
        }
    }
    return seconds
}

/**
 * Fondo de la app con la decoración del tema.
 * @param intensity 0.55 en Inicio/Lista, 1 en Reproductor/Letra (afecta a las luces de Aurora).
 * @param veil velo del color de fondo encima de la decoración (Letra: 0.4 para legibilidad).
 */
@Composable
fun Backdrop(
    playing: Boolean,
    intensity: Float,
    modifier: Modifier = Modifier,
    veil: Float = 0f,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = Ui.colors
    val style = Ui.theme.backdrop
    val desk = Ui.isDesktop
    val alpha by animateFloatAsState(intensity, tween(600))
    val veilAlpha by animateFloatAsState(veil, tween(600))
    val lights = Ui.prefs.backgroundLights
    val clock = rememberPlayClock(playing && !Ui.reduceMotion, c.speed)
    Box(
        modifier
            .fillMaxSize()
            .background(c.background)
            .drawBehind {
                val t = clock.value
                if (lights) when (style) {
                    BackdropStyle.AURORA_LIGHTS -> auroraLights(c, t, alpha, desk)
                    BackdropStyle.SPINNING_DOT -> spinningDot(c, t, desk)
                    BackdropStyle.PASTEL_BLOBS -> pastelBlobs(desk)
                    BackdropStyle.GOLD_CIRCLES -> goldCircles(c, t, desk)
                    BackdropStyle.GRID -> grid(c, desk)
                    BackdropStyle.DIAGONAL_STRIPES -> stripes(c, desk)
                    BackdropStyle.SOFT_CIRCLE -> softCircle(desk)
                }
                if (veilAlpha > 0f) drawRect(c.background.copy(alpha = veilAlpha))
            },
        content = content,
    )
}

private fun DrawScope.blob(color: Color, center: Offset, radius: Float, alpha: Float) = drawCircle(
    Brush.radialGradient(
        0f to color.copy(alpha = alpha), .55f to color.copy(alpha = alpha * .45f), 1f to Color.Transparent,
        center = center, radius = radius,
    ),
    radius, center,
)

/** Aurora: tres luces difusas que flotan 18/22/26 s x velocidad, ida y vuelta. */
private fun DrawScope.auroraLights(c: LiveColors, t: Float, alpha: Float, desk: Boolean) {
    val k = if (desk) 2f else 1f
    fun light(color: Color, period: Float, d: Float, anchor: Offset, a: Float) {
        val phase = sin(2 * PI * t / period).toFloat()
        val s = 1f + .15f * maxOf(phase, 0f) - .05f * maxOf(-phase, 0f)
        val r = d * k * density * s * .62f
        val center = Offset(size.width * anchor.x + phase * 40f * k * density, size.height * anchor.y + phase * 26f * k * density)
        blob(color, center, r, .8f * alpha)
    }
    light(c.light1, 18f, 260f, Offset(.12f, .08f), 1f)
    light(c.light3, 22f, 240f, Offset(.95f, .42f), 1f)
    light(c.light2, 26f, 220f, Offset(.35f, .98f), .55f)
}

/** Póster: círculo relleno al 75 % que gira (8 s por vuelta) mientras suena. */
private fun DrawScope.spinningDot(c: LiveColors, t: Float, desk: Boolean) {
    val d = (if (desk) 220f else 130f) * density
    val center = if (desk) Offset(size.width - 330f * density - d / 2, 70f * density + d / 2)
    else Offset(size.width + 40f * density - d / 2, 118f * density + d / 2)
    rotate((t / (if (desk) 10f else 8f)) * 360f, center) {
        drawArc(c.ink.copy(alpha = if (desk) .12f else .9f), -90f, 270f, true, center - Offset(d / 2, d / 2), Size(d, d))
    }
}

/** Pétalo: manchas suaves rosa, lila y melocotón. */
private fun DrawScope.pastelBlobs(desk: Boolean) {
    val k = density
    if (desk) {
        blob(Color(0xFFF7C3D2), Offset(size.width - 440f * k, 20f * k), 330f * k, .75f)
        blob(Color(0xFFDDD0F6), Offset(330f * k, size.height + 90f * k), 280f * k, .85f)
        blob(Color(0xFFFFD9C7), Offset(size.width - 70f * k, 390f * k), 180f * k, .7f)
    } else {
        blob(Color(0xFFF7C3D2), Offset(size.width + 40f * k, 20f * k), 190f * k, .75f)
        blob(Color(0xFFDDD0F6), Offset(10f * k, size.height - 160f * k), 160f * k, .85f)
        blob(Color(0xFFFFD9C7), Offset(size.width + 20f * k, 380f * k), 90f * k, .7f)
    }
}

/** Seda: círculo fino, círculo punteado que gira muy lento (60 s) y resplandor ciruela. */
private fun DrawScope.goldCircles(c: LiveColors, t: Float, desk: Boolean) {
    val k = density
    val gold = Color(0xFFE8C9A0)
    val c1 = if (desk) Offset(size.width - 150f * k, 250f * k) else Offset(170f * k, 250f * k)
    val r1 = (if (desk) 410f else 230f) * k
    val r2 = (if (desk) 300f else 165f) * k
    val glow = if (desk) Offset(100f * k, size.height) else Offset(size.width, size.height - 30f * k)
    val gr = (if (desk) 300f else 150f) * k
    drawCircle(gold.copy(alpha = if (desk) .1f else .14f), r1, c1, style = Stroke(1f * k))
    rotate(t / (if (desk) 80f else 60f) * 360f, c1) {
        drawCircle(gold.copy(alpha = if (desk) .16f else .22f), r2, c1,
            style = Stroke(1f * k, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * k, 5f * k))))
    }
    drawCircle(Brush.radialGradient(listOf(Color(0xFF5A2340), Color.Transparent), glow, gr), gr, glow)
}

/** Carbono: retícula de líneas blancas al 3,5 % y una línea naranja arriba. */
private fun DrawScope.grid(c: LiveColors, desk: Boolean) {
    val step = (if (desk) 24f else 22f) * density
    val line = Color.White.copy(alpha = .035f)
    var x = 0f
    while (x < size.width) { drawLine(line, Offset(x, 0f), Offset(x, size.height), 1f); x += step }
    var y = 0f
    while (y < size.height) { drawLine(line, Offset(0f, y), Offset(size.width, y), 1f); y += step }
}

/** Estadio: franja de rayas rojas y una línea amarilla, inclinadas. */
private fun DrawScope.stripes(c: LiveColors, desk: Boolean) {
    val k = density
    val angle = if (desk) -14f else -18f
    val top = (if (desk) 220f else 150f) * k
    val h = (if (desk) 240f else 170f) * k
    val w = (if (desk) 22f else 16f) * k
    val pivot = Offset(size.width / 2, top + h / 2)
    rotate(angle, pivot) {
        var x = -size.width
        while (x < size.width * 2) {
            drawRect(Color(0xFFE63946).copy(alpha = if (desk) .12f else .16f), Offset(x, top), Size(w, h)); x += w * 2
        }
        drawRect(Color(0xFFFFD23F).copy(alpha = if (desk) .6f else .75f), Offset(-size.width, (if (desk) 500f else 360f) * k),
            Size(size.width * 3, (if (desk) 10f else 8f) * k))
    }
}

/** Bruma: un círculo grande muy suave arriba. */
private fun DrawScope.softCircle(desk: Boolean) {
    val k = density
    if (desk) drawCircle(Color(0xFFDFE5DC), 350f * k, Offset(650f * k, -30f * k))
    else drawCircle(Color(0xFFDFE5DC), 200f * k, Offset(60f * k, -40f * k))
}
