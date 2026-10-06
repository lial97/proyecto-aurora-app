package app.aurora.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aurora.color.ACCENT_SAMPLE_SIZE
import app.aurora.color.Hsl
import app.aurora.color.extractAccent
import app.aurora.domain.CoverRecipe
import app.aurora.domain.CoverStyle
import app.aurora.domain.Track
import app.aurora.theme.HeroStyle
import app.aurora.theme.ParallelogramShape
import app.aurora.theme.Ui
import kotlinx.coroutines.sync.withLock
import kotlin.math.sin

/**
 * Caché de portadas reales (máx. [capacity] en memoria, se descartan las menos usadas).
 * Segura para usar desde varios hilos: la carga ocurre fuera del candado.
 */
class CoverCache(private val capacity: Int = 80, private val loader: suspend (Track) -> ImageBitmap?) {
    private val map = LinkedHashMap<String, ImageBitmap?>()
    private val lock = kotlinx.coroutines.sync.Mutex()

    suspend fun get(track: Track): ImageBitmap? {
        if (track.coverUri == null) return null
        lock.withLock {
            if (map.containsKey(track.id)) {
                val v = map.remove(track.id)
                map[track.id] = v
                return v
            }
        }
        val img = runCatching { loader(track) }.getOrNull()
        lock.withLock {
            map[track.id] = img
            while (map.size > capacity) map.remove(map.keys.first())
        }
        return img
    }
}

val LocalCoverCache = staticCompositionLocalOf<CoverCache?> { null }

/** Caché de fotogramas de vista previa de videos. */
class PreviewCache(private val capacity: Int = 24, private val loader: suspend (Track) -> List<ImageBitmap>) {
    private val map = LinkedHashMap<String, List<ImageBitmap>>()
    private val lock = kotlinx.coroutines.sync.Mutex()

    suspend fun get(track: Track): List<ImageBitmap> {
        lock.withLock { map.remove(track.id)?.let { map[track.id] = it; return it } }
        val frames = runCatching { loader(track) }.getOrDefault(emptyList())
        if (frames.isNotEmpty()) lock.withLock {
            map[track.id] = frames
            while (map.size > capacity) map.remove(map.keys.first())
        }
        return frames
    }
}

val LocalPreviewCache = staticCompositionLocalOf<PreviewCache?> { null }

/** Fotogramas de vista previa de un video (se cargan solo cuando [active] es verdadero). */
@Composable
fun rememberPreviewFrames(track: Track, active: Boolean): List<ImageBitmap> {
    val cache = LocalPreviewCache.current
    val frames by produceState(emptyList<ImageBitmap>(), track.id, active, cache) {
        if (active && cache != null && track.mediaType == app.aurora.domain.MediaType.VIDEO) value = cache.get(track)
    }
    return frames
}

/** Portada real de la pista (se carga en segundo plano), o `null` mientras tanto. */
@Composable
fun rememberCover(track: Track?): ImageBitmap? {
    val cache = LocalCoverCache.current
    val img by produceState<ImageBitmap?>(null, track?.id, cache) {
        value = if (track?.coverUri != null && cache != null) cache.get(track) else null
    }
    return img
}

/**
 * Portada de una pista con la forma del tema. La portada se ve siempre: si la pista no tiene,
 * se muestra un degradado con la nota "♪".
 */
@Composable
fun Artwork(
    track: Track?,
    size: Dp,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    shape: Shape = if (large) Ui.shapes.artLarge else Ui.shapes.artSmall,
    height: Dp = size,
    border: Boolean = true,
) {
    val c = Ui.colors
    val bw = Ui.shapes.borderWidth
    val real = rememberCover(track)
    val recipe = track?.coverRecipe
    Box(
        modifier
            .width(size).height(height)
            .semantics { contentDescription = "Portada de ${track?.title ?: "canción"}" }
            .clip(shape)
            .then(if (border && bw > 0.dp) Modifier.border(if (large) bw else bw * .8f, c.ink, shape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        when {
            real != null -> Image(real, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            recipe != null -> Canvas(Modifier.fillMaxSize()) {
                val k = maxOf(this.size.width, this.size.height) / 300f
                val dx = (this.size.width - 300f * k) / 2f
                val dy = (this.size.height - 300f * k) / 2f
                translateScaled(dx, dy, k) { drawCoverRecipe(recipe) }
            }
            else -> {
                Box(Modifier.fillMaxSize().background(Brush.sweepGradient(listOf(c.light1, c.light2, c.light3, c.light1))))
                BasicText("♪", style = Ui.type.h1.copy(fontSize = (size.value * 0.34f).sp, color = Color.White.copy(alpha = .9f)))
            }
        }
    }
}

private inline fun DrawScope.translateScaled(dx: Float, dy: Float, k: Float, block: DrawScope.() -> Unit) {
    drawContext.transform.translate(dx, dy)
    scale(k, pivot = Offset.Zero) { block() }
    drawContext.transform.translate(-dx, -dy)
}

/** Portada grande con la decoración propia del tema (spec de temas §4.4). */
@Composable
fun Hero(track: Track?, size: Dp, modifier: Modifier = Modifier, scale: Float = 1f) {
    val theme = Ui.theme
    val c = Ui.colors
    val shape = Ui.shapes.artLarge
    val g = Modifier.graphicsLayer(scaleX = scale, scaleY = scale)
    when (theme.hero) {
        HeroStyle.GLOW -> Artwork(
            track, size, modifier.then(g).shadow(44.dp, shape, ambientColor = c.light1, spotColor = c.light1), large = true,
        )
        HeroStyle.THICK_BORDER -> Artwork(track, size, modifier.then(g), large = true)
        HeroStyle.WHITE_FRAME -> {
            val h = size * 1.2f
            Box(
                modifier.then(g).shadow(26.dp, shape, ambientColor = c.accent, spotColor = c.accent)
                    .width(size).height(h).clip(shape).background(Color.White).padding(6.dp),
            ) { Artwork(track, size - 12.dp, large = true, height = h - 12.dp) }
        }
        HeroStyle.GOLD_RINGS -> Box(
            modifier.then(g).size(size + 32.dp).drawBehind {
                val r = size.toPx() / 2
                drawCircle(c.accent.copy(alpha = .85f), r + 6.5.dp.toPx(), style = Stroke(1.dp.toPx()))
                drawCircle(c.accent.copy(alpha = .3f), r + 15.5.dp.toPx(), style = Stroke(1.dp.toPx()))
            },
            contentAlignment = Alignment.Center,
        ) { Artwork(track, size, Modifier.shadow(30.dp, CircleShape), large = true) }
        HeroStyle.CORNER_BRACKETS -> Box(
            modifier.then(g).size(size).drawWithBrackets(c.accent),
        ) { Artwork(track, size, large = true) }
        HeroStyle.SKEWED_BLOCK -> Box(modifier.then(g).width(size + 34.dp).height(size + 12.dp)) {
            Box(Modifier.offset(x = 34.dp, y = 12.dp).size(size).background(c.accent, ParallelogramShape(.18f)))
            Artwork(track, size, large = true)
        }
        HeroStyle.SOFT_SHADOW -> Artwork(
            track, size, modifier.then(g).shadow(22.dp, shape, ambientColor = Color(0x331F2A24), spotColor = Color(0x331F2A24)), large = true,
        )
    }
}

/** Dos escuadras de 22 dp en esquinas opuestas (Carbono). */
private fun Modifier.drawWithBrackets(color: Color) = this.drawWithContent {
        drawContent()
        val l = 22.dp.toPx()
        val o = 6.dp.toPx()
        val w = 2.dp.toPx()
        val tl = Path().apply { moveTo(-o, -o + l); lineTo(-o, -o); lineTo(-o + l, -o) }
        val br = Path().apply {
            moveTo(size.width + o, size.height + o - l); lineTo(size.width + o, size.height + o); lineTo(size.width + o - l, size.height + o)
        }
        drawPath(tl, color, style = Stroke(w, cap = StrokeCap.Square))
        drawPath(br, color, style = Stroke(w, cap = StrokeCap.Square))
    }

/** Dibuja la receta en un lienzo de 300 x 300 unidades (equivalente a paintCover del prototipo). */
fun DrawScope.drawCoverRecipe(r: CoverRecipe) {
    val s = 300f
    val bg = Color(r.background)
    val c = r.colors.map { Color(it) }
    drawRect(bg, size = Size(s, s))
    when (r.style) {
        CoverStyle.SUN -> {
            drawCircle(Brush.verticalGradient(listOf(c[2], c[1], c[0]), startY = 70f, endY = 270f), 100f, Offset(150f, 175f))
            var y = 185f; var h = 4f
            while (y < 280f) { drawRect(bg, Offset(0f, y), Size(s, h)); y += 18f; h += 2f }
            drawCircle(c[2], 9f, Offset(238f, 62f))
        }
        CoverStyle.CIRCLES -> {
            drawCircle(c[1], 90f, Offset(110f, 120f), alpha = .85f)
            drawCircle(c[0], 80f, Offset(190f, 170f), alpha = .85f)
            drawCircle(c[2], 40f, Offset(150f, 230f), alpha = .85f)
            drawCircle(c[2], 130f, Offset(150f, 150f), style = Stroke(2f))
        }
        CoverStyle.STRIPES -> {
            clipRect(0f, 0f, s, s) {
                rotate(-28.6f, Offset(150f, 150f)) {
                    for (i in -8 until 8) drawRect(c[(i + 8) % 3], Offset(150f + i * 36f, -110f), Size(18f, 520f))
                }
            }
            drawCircle(bg, 62f, Offset(150f, 150f))
            drawCircle(c[0], 22f, Offset(150f, 150f))
        }
        CoverStyle.WAVES -> {
            for (j in 0 until 4) {
                val p = Path().apply {
                    moveTo(0f, s)
                    var x = 0f
                    while (x <= s) { lineTo(x, 130f + j * 40f + sin(x / 40f + j) * 22f); x += 10f }
                    lineTo(s, s); close()
                }
                drawPath(p, c[j % 3], alpha = .9f)
            }
            drawCircle(c[1], 30f, Offset(80f, 70f))
        }
    }
}

private fun sampleAccent(draw: DrawScope.() -> Unit): Hsl? {
    val n = ACCENT_SAMPLE_SIZE
    val bmp = ImageBitmap(n, n)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bmp), Size(n.toFloat(), n.toFloat())) { draw() }
    val px = IntArray(n * n)
    bmp.readPixels(px)
    return extractAccent(px)
}

/** Énfasis de una portada de ejemplo. */
fun accentOf(recipe: CoverRecipe): Hsl? = sampleAccent { scale(ACCENT_SAMPLE_SIZE / 300f, pivot = Offset.Zero) { drawCoverRecipe(recipe) } }

/** Énfasis de una portada real: se reduce a 48 x 48 y se analiza. */
fun accentOf(image: ImageBitmap): Hsl? = sampleAccent {
    drawImage(image, IntOffset.Zero, IntSize(image.width, image.height), IntOffset.Zero, IntSize(ACCENT_SAMPLE_SIZE, ACCENT_SAMPLE_SIZE))
}
