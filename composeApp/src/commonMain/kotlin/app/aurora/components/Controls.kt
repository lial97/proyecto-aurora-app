package app.aurora.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aurora.theme.AuroraDimens
import app.aurora.theme.ThemeId
import app.aurora.theme.Ui
import app.aurora.theme.label
import app.aurora.theme.title

/** Superficie del tema (tarjetas, chips, cajas): color de superficie + borde. */
@Composable
fun Modifier.surface(shape: Shape = Ui.shapes.card, color: Color = Ui.colors.surface): Modifier {
    val bw = Ui.shapes.borderWidth
    return clip(shape).then(opaqueBase(color)).background(color).border(if (bw > 0.dp) bw * .85f else 1.dp, Ui.colors.surfaceBorder, shape)
}

/**
 * Debajo de una superficie transparente se pinta el fondo del tema, para que las decoraciones
 * (el círculo de Póster, los círculos de Seda…) queden siempre detrás y no se vean a través del texto.
 * En Aurora no: sus tarjetas son de vidrio a propósito y sus luces son suaves.
 */
@Composable
fun opaqueBase(color: Color): Modifier =
    if (color.alpha < 1f && Ui.theme.backdrop != app.aurora.theme.BackdropStyle.AURORA_LIGHTS) Modifier.background(Ui.colors.background) else Modifier

/** Clic con escala de pulsación (.9 en íconos, .92 en el principal; spec §7) y realce al pasar el cursor. */
@Composable
fun Modifier.pressable(label: String, pressedScale: Float = .9f, hoverBg: Boolean = false, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val hovered by source.collectIsHoveredAsState()
    val s by animateFloatAsState(if (pressed) pressedScale else 1f, tween(120))
    val hover = if (hoverBg && hovered) Ui.colors.ink.copy(alpha = .07f) else Color.Transparent
    val focused by source.collectIsFocusedAsState()
    // "Foco reforzado": contorno grueso con el color de énfasis al moverse con el teclado.
    val ring = if (Ui.prefs.strongFocus && focused) Modifier.border(3.dp, Ui.colors.accent, Ui.shapes.card) else Modifier
    return scale(s)
        .then(ring)
        .semantics { contentDescription = label }
        .hoverable(source)
        .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
        .background(hover, Ui.shapes.card)
}

@Composable
fun IconButton(
    icon: AuroraIcon,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Ui.colors.ink,
    size: Dp = if (Ui.isDesktop) 36.dp else AuroraDimens.IconButton,
    iconSize: Dp = if (Ui.isDesktop) 20.dp else AuroraDimens.Icon,
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Box(
        modifier.size(size).clip(CircleShape)
            .background(if (hovered) Ui.colors.ink.copy(alpha = .09f) else Color.Transparent)
            .hoverable(source)
            .pressable(label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, tint, size = iconSize) }
}

/** Botón principal con la forma, colores y halo del tema. */
@Composable
fun PlayButton(playing: Boolean, size: Dp, onClick: () -> Unit, modifier: Modifier = Modifier, width: Dp? = null) {
    val c = Ui.colors
    val theme = Ui.theme
    val shape = Ui.shapes.playButton
    val w = width ?: if (theme.id == ThemeId.ESTADIO) size * 1.26f else size
    val glow = when (theme.id) {
        ThemeId.AURORA -> Modifier.shadow(26.dp, shape, ambientColor = c.accent, spotColor = c.accent)
        ThemeId.PETALO -> Modifier.shadow(14.dp, shape, ambientColor = c.accent, spotColor = c.accent)
        ThemeId.SEDA -> Modifier.border(1.dp, c.accent.copy(alpha = .6f), shape).padding(5.dp)
        else -> Modifier
    }
    Box(
        modifier.width(w).height(size).pressable(if (playing) "Pausar" else "Reproducir", .92f, onClick = onClick).then(glow),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.matchParentSize().clip(shape).background(c.playBg), contentAlignment = Alignment.Center) {
            Icon(if (playing) AuroraIcon.Pause else AuroraIcon.Play, c.playInk, size = size * .4f)
        }
    }
}

/** Barra de progreso del tema: tocar o arrastrar salta; la bolita aparece al pasar el cursor en escritorio. */
@Composable
fun ProgressBar(
    positionSec: Int,
    durationSec: Int,
    onSeek: (Int) -> Unit,
    modifier: Modifier = Modifier,
    barHeight: Dp = Ui.theme.progressHeight.dp.let { if (Ui.isDesktop) minOf(it, 8.dp) else it },
    /** Si no es `null`, la parte ya escuchada es una onda que avanza mientras suena y queda recta en pausa. */
    wavePlaying: Boolean? = null,
) {
    val c = Ui.colors
    val square = Ui.shapes.playButton == RectangleShape || Ui.theme.id == ThemeId.CARBONO
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    // Mientras se arrastra, la barra sigue al dedo/cursor y el salto se hace una sola vez al soltar:
    // saltar en cada movimiento (60–120 veces por segundo) trababa VLC y la ventana, sobre todo en Windows.
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val animated by animateFloatAsState(
        if (durationSec > 0) positionSec.toFloat() / durationSec else 0f, tween(900, easing = LinearEasing),
    )
    val fraction = dragFraction ?: animated
    val desk = Ui.isDesktop
    val showThumb = !desk || hovered
    val thinBar = Ui.theme.progressHeight < 10f
    // Onda como la de Android 13+: solo en barras finas (en Póster, de 12 dp, no) y sin "Reducir movimiento".
    val wavy = wavePlaying != null && thinBar
    val amplitude by animateFloatAsState(if (wavy && wavePlaying == true && !Ui.reduceMotion) 1f else 0f, tween(450))
    // La fase solo corre mientras la onda se ve (en pausa la amplitud es 0 y no hace falta redibujar).
    // Se lee al dibujar: la onda avanza sin recomponer la barra.
    val waveClock = rememberAnimationSeconds(wavy && amplitude > 0f)
    fun at(x: Float, width: Int) = (x / width).coerceIn(0f, 1f)
    fun seek(f: Float) {
        if (durationSec > 0) onSeek((f * durationSec).toInt())
    }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(maxOf(22.dp, barHeight + 8.dp))
            .hoverable(source)
            .semantics { contentDescription = "Progreso" }
            .pointerInput(durationSec) { detectTapGestures { seek(at(it.x, size.width)) } }
            .pointerInput(durationSec) {
                detectHorizontalDragGestures(
                    onDragStart = { dragFraction = at(it.x, size.width) },
                    onDragEnd = { dragFraction?.let(::seek); dragFraction = null },
                    onDragCancel = { dragFraction = null },
                ) { ch, _ -> ch.consume(); dragFraction = at(ch.position.x, size.width) }
            },
    ) {
        val h = barHeight.toPx()
        val y = size.height / 2 - h / 2
        val r = if (square) CornerRadius.Zero else CornerRadius(h)
        val w = size.width * fraction
        if (wavy && amplitude > 0f) {
            val phase = waveClock.value / 1.6f % 1f
            drawWave(w, h, amplitude, phase, Brush.horizontalGradient(c.fill, endX = w.coerceAtLeast(1f)), c.track, square)
            // Marca vertical en la posición, como la de Android.
            val tw = 4.dp.toPx(); val th = 16.dp.toPx()
            drawRoundRect(c.fill.last(), Offset(w - tw / 2, size.height / 2 - th / 2), Size(tw, th), if (square) CornerRadius.Zero else CornerRadius(tw / 2))
            return@Canvas
        }
        drawRoundRect(c.track, Offset(0f, y), Size(size.width, h), r)
        if (w > 0f) drawRoundRect(Brush.horizontalGradient(c.fill, endX = w.coerceAtLeast(1f)), Offset(0f, y), Size(w, h), r)
        if (showThumb && thinBar) {
            drawCircle(if (desk) c.ink else c.fill.last(), (if (desk) 6.dp else 7.dp).toPx(), Offset(w, size.height / 2))
        }
    }
}

/**
 * Parte escuchada como onda (seno de ~28 dp de largo y 3 dp de alto, como en la maqueta) y el resto recto.
 * [amplitude] 0…1 aplana la onda al pausar; [phase] 0…1 la hace avanzar.
 */
private fun DrawScope.drawWave(w: Float, stroke: Float, amplitude: Float, phase: Float, played: Brush, track: Color, square: Boolean) {
    val mid = size.height / 2
    val cap = if (square) StrokeCap.Butt else StrokeCap.Round
    val gap = 4.dp.toPx() * amplitude
    if (w + gap < size.width) drawLine(track, Offset(w + gap, mid), Offset(size.width, mid), stroke, cap)
    if (w <= 0f) return
    val length = 28.dp.toPx()
    val amp = 3.dp.toPx() * amplitude
    // La onda se suaviza en los últimos 8 dp para llegar recta a la marca.
    fun yAt(x: Float) = mid + kotlin.math.sin((x / length - phase) * 2f * kotlin.math.PI.toFloat()) * amp * ((w - x) / 8.dp.toPx()).coerceIn(0f, 1f)
    val path = Path().apply {
        moveTo(0f, yAt(0f))
        var x = 0f
        while (x < w) {
            x = minOf(x + 2.dp.toPx(), w)
            lineTo(x, yAt(x))
        }
    }
    drawPath(path, played, style = Stroke(width = stroke, cap = cap, join = StrokeJoin.Round))
}

fun formatTime(sec: Int): String {
    val s = sec.coerceAtLeast(0)
    val mm = (s % 3600 / 60).toString()
    val ss = (s % 60).toString().padStart(2, '0')
    return if (s >= 3600) "${s / 3600}:${mm.padStart(2, '0')}:$ss" else "${s / 60}:$ss"
}

/** Segundos de subida (y de bajada) de cada barra del ecualizador mini, a velocidad 1. */
private val EqBarSeconds = floatArrayOf(.8f, .6f, 1f)

/** Ecualizador mini de 3 barras de 3 x 14 dp (spec §6). */
@Composable
fun Equalizer(playing: Boolean, modifier: Modifier = Modifier) {
    val c = Ui.colors
    // En pausa el reloj se detiene: si no, la ventana se redibujaría sin parar aunque las barras estén quietas.
    // Las barras leen el reloj al dibujar, sin recomponer.
    val clock = rememberAnimationSeconds(playing)
    val speed = c.speed
    Canvas(modifier.width(13.dp).height(14.dp)) {
        val w = 3.dp.toPx()
        val gap = 2.dp.toPx()
        EqBarSeconds.forEachIndexed { i, d ->
            // Sube y baja entre 0,3 y 1: d × velocidad segundos en cada sentido.
            val b = if (playing) .3f + .7f * (.5f - .5f * kotlin.math.cos(kotlin.math.PI.toFloat() * clock.value / (d * speed))) else .35f
            val h = size.height * b
            drawRect(c.accent, Offset(i * (w + gap), size.height - h), Size(w, h))
        }
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: AuroraIcon? = null) {
    val c = Ui.colors
    val theme = Ui.theme
    val shape = Ui.shapes.chip
    val bw = Ui.shapes.borderWidth
    val ink = if (selected) c.chipOnInk else c.ink
    // Misma cadena de modificadores en ambos estados; la escala de pulsación va por fuera.
    Row(
        modifier
            .pressable(text, .95f, onClick = onClick)
            .clip(shape)
            .then(opaqueBase(if (selected) c.chipOn else c.surface))
            .background(if (selected) c.chipOn else c.surface)
            .border(if (bw > 0.dp) bw * .85f else 1.dp, if (selected) c.chipOn else c.surfaceBorder, shape)
            .padding(horizontal = if (theme.id == ThemeId.ESTADIO) 16.dp else 14.dp, vertical = if (Ui.isDesktop) 6.dp else 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, ink, size = 15.dp)
        BasicText(
            theme.label(text),
            style = Ui.type.label.copy(color = ink, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
            // Un chip nunca parte su texto: mide lo que mide (las filas de chips pasan a la línea siguiente).
            maxLines = 1, softWrap = false,
        )
    }
}

/** Subtítulo de sección con las mayúsculas del tema y el cuadrito naranja de Carbono. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: String? = null, top: Dp = if (Ui.isDesktop) 26.dp else 22.dp) {
    val theme = Ui.theme
    Row(modifier.fillMaxWidth().padding(top = top, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (theme.headingMarker) {
            Box(Modifier.size(8.dp).background(Ui.colors.accent))
            Spacer(Modifier.width(10.dp))
        }
        BasicText(theme.title(text), style = Ui.type.h2, modifier = Modifier.weight(1f))
        if (trailing != null) BasicText(trailing, style = Ui.type.caption.copy(fontWeight = FontWeight.SemiBold))
    }
}

/** Etiquetas técnicas bajo el título (Carbono): BPM, formato y calidad. */
@Composable
fun TechBadges(bpm: Int?, format: String?, bitrateKbps: Int?, modifier: Modifier = Modifier) {
    val c = Ui.colors
    val items = listOfNotNull("${bpm ?: 100} BPM", format, bitrateKbps?.let { "$it kbps" })
    Row(modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEachIndexed { i, s ->
            val col = if (i == 0) c.accent else c.mute
            BasicText(
                s, style = Ui.type.caption.copy(fontFamily = Ui.type.h1.fontFamily, color = col),
                modifier = Modifier.border(1.dp, if (i == 0) c.accent else c.surfaceBorder, RoundedCornerShape(1.dp))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
    }
}

/** Avatar circular con iniciales; tocarlo abre Ajustes. */
@Composable
fun Avatar(initials: String, onClick: () -> Unit, size: Dp = if (Ui.isDesktop) 34.dp else 40.dp) {
    val c = Ui.colors
    val shape = if (Ui.theme.id == ThemeId.POSTER || Ui.theme.id == ThemeId.CARBONO) RectangleShape else CircleShape
    Box(
        Modifier.size(size).pressable("Ajustes", onClick = onClick).clip(shape).background(c.accent),
        contentAlignment = Alignment.Center,
    ) { BasicText(initials, style = Ui.type.caption.copy(color = c.onAccent, fontWeight = FontWeight.Bold)) }
}

/**
 * Selector "Canción | Video" del reproductor. "Canción" muestra portada y letra mientras el video sigue sonando
 * (segundo plano); "Video" muestra la imagen. Si la pista no tiene video, "Video" aparece deshabilitado.
 */
@Composable
fun SongVideoSwitch(showingVideo: Boolean, hasVideo: Boolean, onSong: () -> Unit, onVideo: () -> Unit, modifier: Modifier = Modifier) {
    val c = Ui.colors
    val shape = Ui.shapes.chip
    Row(modifier.clip(shape).background(c.surface).border(1.dp, c.surfaceBorder, shape).padding(3.dp)) {
        listOf(false, true).forEach { video ->
            val on = video == showingVideo
            val enabled = !video || hasVideo
            val label = if (video) (if (hasVideo) "Video" else "Sin video") else "Canción"
            Box(
                Modifier.clip(shape).background(if (on) c.chipOn else Color.Transparent)
                    .then(if (enabled) Modifier.pressable(label, .96f) { if (video) onVideo() else onSong() } else Modifier)
                    .padding(horizontal = 14.dp, vertical = 5.dp),
            ) {
                BasicText(
                    Ui.theme.label(label),
                    style = Ui.type.label.copy(
                        color = when { on -> c.chipOnInk; enabled -> c.ink; else -> c.mute.copy(alpha = .6f) },
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    ),
                )
            }
        }
    }
}
