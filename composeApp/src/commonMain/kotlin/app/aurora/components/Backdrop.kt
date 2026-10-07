package app.aurora.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
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
            awaitAnimationFrame { now ->
                seconds.floatValue += (now - last) / 1e9f / speed
                last = now
            }
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
                    BackdropStyle.NONE -> Unit
                    // Se completan con cada tema nuevo.
                    BackdropStyle.CONTOUR_LINES -> contourLines(c, desk)
                    BackdropStyle.WATERCOLOR_BLOBS -> watercolorBlobs(t, desk)
                    BackdropStyle.RETRO_STRIPE -> retroStripe(c, desk, alpha >= .9f)
                    BackdropStyle.CHECKER_BAND -> checkerBand(c, desk, alpha >= .9f)
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

/**
 * Bosque: curvas de nivel, como un mapa topográfico (la misma fórmula de la maqueta: dos cimas con 8 contornos
 * irregulares cada una, en un lienzo de 380 × 760). No se animan: el trazo se arma una vez por tamaño y se guarda.
 */
private fun DrawScope.contourLines(c: LiveColors, desk: Boolean) {
    drawPath(ContourCache.path(size, density, desk), c.mute.copy(alpha = .16f), style = Stroke(1f * density))
}

private object ContourCache {
    private var key: Triple<Size, Float, Boolean>? = null
    private var cached: Path? = null

    fun path(size: Size, density: Float, desk: Boolean): Path {
        val k = Triple(size, density, desk)
        cached?.takeIf { key == k }?.let { return it }
        // Como en la maqueta: 20 dp más grande por cada lado y recortado al centro. En escritorio el dibujo se
        // ajusta al alto y se repite a lo ancho, así los contornos no quedan enormes en una ventana ancha.
        val pad = 20f * density
        val w = size.width + pad * 2
        val h = size.height + pad * 2
        val scale = if (desk) h / 760f else maxOf(w / 380f, h / 760f)
        val tileW = 380f * scale
        val tiles = if (desk) kotlin.math.ceil(w / tileW).toInt() else 1
        val x0 = if (desk) -pad else (w - tileW) / 2 - pad
        val y0 = (h - 760f * scale) / 2 - pad
        val path = Path()
        repeat(tiles) { tile ->
            val ox = x0 + tile * tileW
            listOf(60f to 130f, 250f to 520f).forEachIndexed { ci, (cx, cy) ->
                for (ring in 1 until 9) {
                    val r = ring * 24f
                    var a = 0
                    while (a <= 360) {
                        val rad = a * PI.toFloat() / 180f
                        val rr = r * (1 + .18f * sin(3 * rad + ring + ci) + .08f * kotlin.math.cos(5 * rad + ring))
                        val x = ox + (cx + rr * kotlin.math.cos(rad)) * scale
                        val y = y0 + (cy + rr * .8f * sin(rad)) * scale
                        if (a == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        a += 12
                    }
                    path.close()
                }
            }
        }
        key = k; cached = path
        return path
    }
}

/**
 * Acuarela: manchas lila, durazno y menta al 55 % que flotan muy despacio (14 s, cada una desfasada) mientras
 * suena. Degradados radiales en lugar de desenfoque, como las luces de Aurora.
 */
private fun DrawScope.watercolorBlobs(t: Float, desk: Boolean) {
    val k = density * (if (desk) 1.6f else 1f)
    fun wash(color: Color, w: Float, h: Float, center: Offset, delay: Float) {
        val p = (1f - kotlin.math.cos((t + delay) / 14f * 2f * PI.toFloat())) / 2f
        val s = 1f + .05f * p
        val c = center + Offset(10f * k * p, -8f * k * p)
        // El desenfoque de 26 px de la maqueta agranda la mancha: el degradado llega un poco más lejos.
        val r = maxOf(w, h) / 2f * k * s * 1.25f
        // Núcleo parejo al 55 % y borde que se desvanece, como la mancha desenfocada de la maqueta.
        scale(1f, h / w, c) {
            drawCircle(Brush.radialGradient(0f to color.copy(alpha = .55f), .5f to color.copy(alpha = .45f), 1f to Color.Transparent, center = c, radius = r), r, c)
        }
    }
    wash(Color(0xFFC9B6F2), 220f, 180f, Offset(size.width - 50f * k, 40f * k), 0f)
    wash(Color(0xFFFFC9B0), 200f, 160f, Offset(10f * k, 380f * k), 5f)
    wash(Color(0xFFBFE8D9), 180f, 150f, Offset(size.width - 30f * k, size.height - 115f * k), 9f)
}

/**
 * Casete: grano de papel muy leve en toda la pantalla y, en el reproductor del móvil ([player]), una franja diagonal
 * abajo, detrás de las teclas. En escritorio la franja va al final del panel derecho ([RetroStripeBox]), así se
 * desplaza con el contenido y nunca queda detrás de texto.
 */
private fun DrawScope.retroStripe(c: LiveColors, desk: Boolean, player: Boolean) {
    drawRect(PaperGrain.brush)
    if (desk || !player) return
    val k = density
    retroBands(Offset(size.width + 130f * k - 420f * k, size.height - 30f * k - 72f * k), 420f * k, 72f * k, c.ink)
}

/** Franja de Casete al final de un panel: un adorno que ocupa su lugar, no un fondo. */
@Composable
fun RetroStripeBox(modifier: Modifier = Modifier) {
    val ink = Ui.colors.ink
    Box(modifier.fillMaxWidth().height(110.dp).clipToBounds().drawBehind {
        val k = density
        retroBands(Offset(size.width - 330f * k, 30f * k), 420f * k, 72f * k, ink)
    })
}

/** Cinco bandas (naranja, crema, verde petróleo, crema y café) en un rectángulo girado -28°. */
private fun DrawScope.retroBands(topLeft: Offset, w: Float, h: Float, ink: Color) {
    val center = topLeft + Offset(w / 2, h / 2)
    val cream = Color(0xFFF6E7C8)
    rotate(-28f, center) {
        val bands = listOf(0f to Color(0xFFD9542B), .33f to cream, .40f to Color(0xFF1D524F), .73f to cream, .80f to ink, 1f to Color.Transparent)
        for (i in 0 until bands.size - 1) {
            val (from, color) = bands[i]
            val to = bands[i + 1].first
            drawRect(color.copy(alpha = .9f), topLeft + Offset(0f, h * from), Size(w, h * (to - from)))
        }
    }
}

/** Textura de ruido (puntos café muy tenues) que se genera una vez y se repite como mosaico. */
private object PaperGrain {
    val brush: Brush by lazy {
        val n = 96
        val img = androidx.compose.ui.graphics.ImageBitmap(n, n)
        val canvas = androidx.compose.ui.graphics.Canvas(img)
        val paint = androidx.compose.ui.graphics.Paint()
        val rnd = kotlin.random.Random(7)
        repeat(n * n / 3) {
            paint.color = Color(0xFF3B2416).copy(alpha = rnd.nextFloat() * .07f)
            val x = rnd.nextInt(n).toFloat(); val y = rnd.nextInt(n).toFloat()
            canvas.drawRect(x, y, x + 1f, y + 1f, paint)
        }
        androidx.compose.ui.graphics.ShaderBrush(
            androidx.compose.ui.graphics.ImageShader(img, androidx.compose.ui.graphics.TileMode.Repeated, androidx.compose.ui.graphics.TileMode.Repeated),
        )
    }
}

/**
 * Rockola: estrella menta (✦) en una esquina y, en el reproductor del móvil ([player]), franja de cuadros de 16 dp
 * en el borde inferior. Fuera del reproductor la franja va en la barra de pestañas o de reproducción (no en el
 * fondo), así nunca queda detrás de texto.
 */
private fun DrawScope.checkerBand(c: LiveColors, desk: Boolean, player: Boolean) {
    val k = density
    if (player && !desk) {
        val band = 16f * k
        translate(top = size.height - band) {
            val s = band / 2
            drawRect(Color(0xFFFFF4E2), size = Size(size.width, band))
            var x = 0f
            var i = 0
            while (x < size.width) {
                drawRect(c.ink.copy(alpha = .9f), Offset(x, if (i % 2 == 0) 0f else s), Size(s, s))
                x += s; i++
            }
        }
    }
    if (desk) return // en escritorio la esquina de arriba a la derecha es el panel derecho
    val r = 13f * k
    val center = if (player) Offset(22f * k + r, size.height - 70f * k - r) else Offset(size.width - 26f * k - r, 58f * k + r)
    // Estrella de cuatro puntas con lados curvos.
    val star = Path().apply {
        moveTo(center.x, center.y - r)
        quadraticTo(center.x, center.y, center.x + r, center.y)
        quadraticTo(center.x, center.y, center.x, center.y + r)
        quadraticTo(center.x, center.y, center.x - r, center.y)
        quadraticTo(center.x, center.y, center.x, center.y - r)
        close()
    }
    drawPath(star, Color(0xFF5DB8A4))
}
