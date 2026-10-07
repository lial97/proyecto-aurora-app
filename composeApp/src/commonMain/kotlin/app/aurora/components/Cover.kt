package app.aurora.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.aurora.color.ACCENT_SAMPLE_SIZE
import app.aurora.color.Hsl
import app.aurora.color.extractAccent
import app.aurora.domain.CoverRecipe
import app.aurora.domain.CoverStyle
import app.aurora.domain.Track
import app.aurora.theme.HeroStyle
import app.aurora.theme.OrganicBlobShape
import app.aurora.theme.SmallCoverStyle
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

    /** Aumenta al cambiar la portada de alguna pista: las portadas en pantalla se vuelven a cargar. */
    var version by androidx.compose.runtime.mutableIntStateOf(0)
        private set

    /** Olvida la portada de [id] (se descargó una nueva). */
    suspend fun invalidate(id: String) {
        lock.withLock { map.remove(id) }
        version++
    }

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
    val img by produceState<ImageBitmap?>(null, track?.id, track?.coverUri, cache, cache?.version) {
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
            // Casete: sombra dura café hacia abajo a la derecha (por fuera del recorte) y borde crema.
            .then(if (!large && Ui.theme.smallCoverStyle == SmallCoverStyle.CREAM_FRAME) Modifier.creamFrame(shape, c.ink, c.surface) else Modifier)
            // Rockola: aro negro alrededor, como un disco (por fuera del recorte).
            .then(if (!large && Ui.theme.smallCoverStyle == SmallCoverStyle.VINYL_RING) Modifier.drawBehind {
                drawCircle(Color(0xFF1B1416), this.size.minDimension / 2 + 3.dp.toPx())
            } else Modifier)
            .clip(shape)
            .then(if (border && bw > 0.dp) Modifier.border(if (large) bw else bw * .8f, c.ink, shape) else Modifier)
            // Bosque: contorno fino en las portadas pequeñas.
            .then(if (!large && Ui.theme.smallCoverStyle == SmallCoverStyle.OUTLINE) Modifier.border(1.dp, c.surfaceBorder, shape) else Modifier),
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

/**
 * Portada grande con la decoración propia del tema (spec de temas §4.4).
 * @param playing si suena la canción (anima la gota de Acuarela, los carretes y el vinilo); `null` = portada
 *   estática de una lista, sin animación ni detalles del reproductor.
 */
@Composable
fun Hero(track: Track?, size: Dp, modifier: Modifier = Modifier, scale: Float = 1f, playing: Boolean? = null, progress: Float = 0f) {
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
        // Se completan con cada tema nuevo.
        // Grafito: sombra negra profunda y un reflejo azul muy suave (elipse al 18 %) debajo.
        HeroStyle.DEEP_SHADOW -> Box(
            modifier.then(g).drawBehind {
                val w = this.size.width * .78f
                val h = 18.dp.toPx()
                val center = Offset(this.size.width / 2, this.size.height + 10.dp.toPx())
                drawOval(
                    Brush.radialGradient(listOf(Color(0xFF8AB4FF).copy(alpha = .18f), Color.Transparent), center, w / 2),
                    topLeft = Offset(center.x - w / 2, center.y - h / 2), size = Size(w, h),
                )
            },
        ) { Artwork(track, size, Modifier.shadow(36.dp, shape, ambientColor = Color.Black, spotColor = Color.Black), large = true) }
        // Bosque: 2 dp del color de fondo, marco de cobre de 1,5 dp y sombra profunda. Debajo, unas coordenadas
        // decorativas: se dibujan (no ocupan lugar en el diseño) y los lectores de pantalla no las leen.
        // Acuarela: mancha difuminada lila → durazno detrás y la portada en forma de gota, que cambia suavemente
        // (12 s ida y vuelta) solo mientras suena.
        HeroStyle.WATERCOLOR -> {
            val clock = rememberPlayClock(playing == true && !Ui.reduceMotion, 1f)
            Box(
                modifier.then(g).size(size).drawBehind {
                    val center = Offset(this.size.width / 2, this.size.height / 2)
                    val r = this.size.width * .66f
                    scale(1f, 270f / 290f, center) {
                        drawCircle(
                            Brush.radialGradient(0f to Color(0xFFD9C8FA), .6f to Color(0xFFFFD7C4), 1f to Color.Transparent, center = center, radius = r),
                            r, center, alpha = .9f,
                        )
                    }
                },
                contentAlignment = Alignment.Center,
            ) {
                val blob = OrganicBlobShape((1f - kotlin.math.cos(clock.value / 12f * 2f * kotlin.math.PI.toFloat())) / 2f)
                Artwork(track, size, Modifier.shadow(22.dp, blob, ambientColor = Color(0x477B57D1), spotColor = Color(0x477B57D1)), large = true, shape = blob)
            }
        }
        HeroStyle.COPPER_FRAME -> {
            val measurer = androidx.compose.ui.text.rememberTextMeasurer()
            val coords = Ui.type.caption.copy(color = c.mute, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = .2.em)
            Box(
                modifier.then(g).size(size + 7.dp)
                    .shadow(28.dp, RoundedCornerShape(14.dp), clip = false, ambientColor = Color.Black, spotColor = Color.Black)
                    .drawWithContent {
                        val o = shape.createOutline(this.size, layoutDirection, this)
                        drawOutline(o, c.background)
                        val w = 1.5.dp.toPx()
                        val inner = shape.createOutline(Size(this.size.width - w, this.size.height - w), layoutDirection, this)
                        translate(w / 2, w / 2) { drawOutline(inner, c.accent, style = Stroke(w)) }
                        drawContent()
                        if (playing != null) {
                            val text = measurer.measure("N 4°35′ · O 74°04′", coords)
                            drawText(text, topLeft = Offset((this.size.width - text.size.width) / 2, this.size.height + 8.dp.toPx()))
                        }
                    },
                contentAlignment = Alignment.Center,
            ) { Artwork(track, size, large = true) }
        }
        HeroStyle.CASSETTE -> CassetteHero(track, size, modifier.then(g), playing, progress)
        HeroStyle.VINYL -> VinylHero(track, size, modifier.then(g), playing)
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

/** Casete: sombra dura de 3 dp hacia abajo a la derecha y, encima de la portada, un borde crema de 2,5 dp. */
private fun Modifier.creamFrame(shape: Shape, ink: Color, cream: Color) = drawBehind {
    val o = shape.createOutline(size, layoutDirection, this)
    translate(3.dp.toPx(), 3.dp.toPx()) { drawOutline(o, ink) }
}.drawWithContent {
    drawContent()
    val w = 2.5.dp.toPx()
    val inner = shape.createOutline(Size(size.width - w, size.height - w), layoutDirection, this)
    translate(w / 2, w / 2) { drawOutline(inner, cream, style = Stroke(w)) }
}

/**
 * Casete: la portada es la carátula en su estuche (borde crema de 7 dp, sombra dura de 6 dp y un lomo naranja a la
 * izquierda). Debajo, montada 18 dp sobre la portada, la cinta con dos carretes que giran (una vuelta cada 3 s, solo
 * mientras suena): la cinta del carrete izquierdo se achica y la del derecho crece con el progreso. En el
 * reproductor ([playing] no nulo) lleva además la etiqueta "LADO A · álbum".
 */
@Composable
private fun CassetteHero(track: Track?, size: Dp, modifier: Modifier, playing: Boolean?, progress: Float) {
    val c = Ui.colors
    val cream = c.surface
    val orange = Color(0xFFD9542B)
    val clock = rememberPlayClock(playing == true && !Ui.reduceMotion, 1f)
    val p = progress.coerceIn(0f, 1f)
    Column(modifier.width(size + 6.dp), horizontalAlignment = Alignment.Start) {
        Box(Modifier.size(size)) {
            val shape = RoundedCornerShape(8.dp)
            Box(
                Modifier.size(size)
                    .drawBehind { translate(6.dp.toPx(), 6.dp.toPx()) { drawRoundRect(c.ink, cornerRadius = CornerRadius(8.dp.toPx())) } }
                    .clip(shape).background(cream).padding(7.dp),
            ) { Artwork(track, size - 14.dp, large = true, shape = RoundedCornerShape(3.dp)) }
            // Lomo naranja.
            Box(Modifier.offset(x = (-4).dp, y = 12.dp).width(4.dp).height(size - 24.dp).clip(RoundedCornerShape(2.dp)).background(orange))
        }
        // Cinta: tira café con dos carretes crema.
        Canvas(Modifier.offset(y = (-18).dp).padding(horizontal = 14.dp).fillMaxWidth().height(56.dp).clearAndSetSemantics { }) {
            val h = this.size.height
            val w = this.size.width
            drawRoundRect(Color.Black.copy(alpha = .25f), topLeft = Offset(0f, 3.dp.toPx()), size = this.size, cornerRadius = CornerRadius(h / 2))
            drawRoundRect(c.ink, cornerRadius = CornerRadius(h / 2))
            val left = Offset(30.dp.toPx(), h / 2)
            val right = Offset(w - 30.dp.toPx(), h / 2)
            val brown = Color(0xFF6B3F22)
            // Cinta entre carretes (rayada) y cinta enrollada en cada uno según el progreso.
            val bandH = 16.dp.toPx()
            var x = left.x
            var dark = false
            while (x < right.x) {
                drawRect(if (dark) Color(0xFF4D2C18) else brown, Offset(x, h / 2 - bandH / 2), Size(minOf(3.dp.toPx(), right.x - x), bandH))
                x += 3.dp.toPx(); dark = !dark
            }
            val min = 21.dp.toPx(); val max = 27.dp.toPx()
            drawCircle(brown, min + (max - min) * (1f - p), left)
            drawCircle(brown, min + (max - min) * p, right)
            val angle = clock.value / 3f * 360f
            for (center in listOf(left, right)) {
                drawCircle(cream, 20.dp.toPx(), center)
                rotate(angle, center) {
                    for (k in 0 until 3) rotate(k * 60f, center) {
                        drawRoundRect(c.ink, Offset(center.x - 2.dp.toPx(), center.y - 18.dp.toPx()), Size(4.dp.toPx(), 36.dp.toPx()), CornerRadius(2.dp.toPx()))
                    }
                }
                drawCircle(c.ink, 8.dp.toPx(), center)
                drawCircle(cream, 7.dp.toPx(), center, style = Stroke(3.dp.toPx()))
                drawCircle(c.ink, 5.5.dp.toPx(), center)
            }
        }
        if (playing != null && track != null) {
            Row(Modifier.offset(y = (-6).dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(orange))
                BasicText(
                    "LADO A · ${track.album.uppercase()}",
                    style = Ui.type.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .12.em, color = c.mute),
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

/**
 * Rockola: disco negro con surcos, un brillo diagonal y la portada como etiqueta circular (168/262 del disco) con aro
 * cereza y un agujero crema en el medio. El disco gira (una vuelta cada 2,4 s) solo mientras suena; el brillo queda
 * quieto, como la luz sobre un disco de verdad.
 */
@Composable
private fun VinylHero(track: Track?, size: Dp, modifier: Modifier, playing: Boolean?) {
    val c = Ui.colors
    val clock = rememberPlayClock(playing == true && !Ui.reduceMotion, 1f)
    val label = size * (168f / 262f)
    Box(
        modifier.size(size)
            .shadow(26.dp, CircleShape, clip = false, ambientColor = Color(0x592A1B1F), spotColor = Color(0x592A1B1F))
            .drawWithContent {
                drawContent()
                // Brillo diagonal (130°) y aro interior oscuro.
                drawCircle(
                    Brush.linearGradient(
                        .40f to Color.Transparent, .50f to Color.White.copy(alpha = .12f), .60f to Color.Transparent,
                        start = Offset(0f, this.size.height * .1f), end = Offset(this.size.width, this.size.height * .9f),
                    ),
                )
                drawCircle(Color(0xFF0E0A0B), this.size.minDimension / 2 - 2.dp.toPx(), style = Stroke(4.dp.toPx()))
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.fillMaxSize().graphicsLayer { rotationZ = clock.value / 2.4f * 360f }.clip(CircleShape).drawBehind {
                drawCircle(Color(0xFF171112))
                val step = 4.dp.toPx()
                var r = this.size.minDimension / 2 - step / 2
                while (r > 0f) { drawCircle(Color(0xFF221A1C), r, style = Stroke(2.dp.toPx())); r -= step }
            },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(label + 6.dp).clip(CircleShape).background(Color(0xFFC81E35)), contentAlignment = Alignment.Center) {
                Artwork(track, label, large = true, shape = CircleShape)
            }
            Box(Modifier.size(10.dp).clip(CircleShape).background(c.background))
        }
    }
}
