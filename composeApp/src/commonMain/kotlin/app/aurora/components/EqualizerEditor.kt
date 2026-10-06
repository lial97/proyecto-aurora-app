package app.aurora.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aurora.audio.Eq
import app.aurora.audio.EqSettings
import app.aurora.theme.Ui

private fun fmtDb(v: Float) = (if (v > 0) "+" else "") + (kotlin.math.round(v * 10) / 10f).toString().replace('.', ',')

/** En el móvil los valores van en enteros para que quepan en una línea ("+3", "−5"). */
private fun fmtShort(v: Float): String { val r = kotlin.math.round(v).toInt(); return if (r > 0) "+$r" else if (r < 0) "−${-r}" else "0" }

/**
 * Ecualizador de 10 bandas + preamplificador. Las 11 columnas se reparten el ancho por igual (caben en
 * cualquier pantalla) y la curva pasa exactamente por el centro de cada perilla. Cada banda se arrastra o se
 * toca; con el teclado: flechas ±0,5 dB, RePág/AvPág ±3, Inicio/Fin a los extremos, 0 al centro.
 */
@Composable
fun EqualizerEditor(eq: EqSettings, onChange: (EqSettings) -> Unit, modifier: Modifier = Modifier, height: Dp = 200.dp) {
    val c = Ui.colors
    val enabled = eq.enabled
    val desk = Ui.isDesktop
    // Números y frecuencias en una línea aunque el texto de la app esté grande (máximo +15 %).
    val base = androidx.compose.ui.platform.LocalDensity.current
    val small = androidx.compose.ui.unit.Density(base.density, minOf(base.fontScale, 1.15f))
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides small) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth().height(height + 40.dp)) {
            Band("Pre", eq.preamp, Eq.MAX_PREAMP, enabled, height, "Preamplificador", desk, Modifier.weight(1f)) { onChange(eq.withBand(-1, it)) }
            // Pre un poco separado de las bandas.
            Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height(height).background(c.ink.copy(alpha = .1f)))
            Box(Modifier.weight(10f)) {
                // Curva detrás de las bandas: misma columna (ancho / 10) y misma altura útil que cada perilla.
                Canvas(Modifier.fillMaxWidth().height(height).padding(top = BandTop, bottom = BandBottom)) {
                    val n = eq.bands.size
                    val col = size.width / n
                    fun x(i: Int) = col * (i + .5f)
                    fun y(v: Float) = size.height / 2 - v / 20f * (size.height / 2)
                    drawLine(c.ink.copy(alpha = .15f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1f)
                    val pts = eq.bands.mapIndexed { i, v -> Offset(x(i), y(v)) }
                    val path = Path().apply {
                        moveTo(pts[0].x, pts[0].y)
                        for (i in 0 until pts.size - 1) {
                            val p0 = pts.getOrElse(i - 1) { pts[i] }; val p1 = pts[i]; val p2 = pts[i + 1]; val p3 = pts.getOrElse(i + 2) { p2 }
                            cubicTo(p1.x + (p2.x - p0.x) / 6, p1.y + (p2.y - p0.y) / 6, p2.x - (p3.x - p1.x) / 6, p2.y - (p3.y - p1.y) / 6, p2.x, p2.y)
                        }
                    }
                    val fill = Path().apply { addPath(path); lineTo(pts.last().x, size.height); lineTo(pts.first().x, size.height); close() }
                    val stroke = if (enabled) c.accent else c.mute
                    drawPath(fill, Brush.verticalGradient(listOf(stroke.copy(alpha = .3f), Color.Transparent)))
                    drawPath(path, stroke, style = Stroke(2.dp.toPx()))
                }
                Row(Modifier.fillMaxWidth()) {
                    eq.bands.forEachIndexed { i, v ->
                        Band(Eq.bandLabels[i], v, Eq.MAX, enabled, height, "Banda de ${Eq.bandLabels[i]} hercios", desk, Modifier.weight(1f)) { onChange(eq.withBand(i, it)) }
                    }
                }
            }
        }
        BasicText("+20 dB arriba · 0 al centro · −20 dB abajo · Hz", style = Ui.type.caption, modifier = Modifier.padding(top = 2.dp))
        // Solo si alguna banda sube: el volumen general baja lo justo para no recortar.
        if (enabled && eq.bands.any { it > 0f } && eq.protectionDb > 0.05f) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(AuroraIcon.Check, c.accent, size = 16.dp)
                BasicText(
                    "Evitar saturación: el volumen general baja ${fmtDb(eq.protectionDb).removePrefix("+")} dB para que ninguna banda recorte el sonido",
                    style = Ui.type.caption.copy(color = c.ink), modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
    }
}

/** Alto del número de arriba y del nombre de abajo de cada banda (la curva usa los mismos márgenes). */
private val BandTop = 22.dp
private val BandBottom = 22.dp

@Composable
private fun Band(label: String, value: Float, max: Float, enabled: Boolean, height: Dp, a11y: String, desk: Boolean, modifier: Modifier, onValue: (Float) -> Unit) {
    val c = Ui.colors
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val current by rememberUpdatedState(value)
    val set by rememberUpdatedState(onValue)
    fun fromY(y: Float, h: Float) = ((.5f - y / h) * 40f).coerceIn(Eq.MIN, max)
    val one = Ui.type.caption.copy(fontSize = 10.5.sp)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.height(height)) {
        Box(Modifier.height(BandTop), contentAlignment = Alignment.Center) {
            BasicText(if (desk) fmtDb(value) else fmtShort(value), style = one.copy(color = if (enabled) c.ink else c.mute), maxLines = 1, softWrap = false)
        }
        Box(
            // Toda la columna responde al dedo (no solo la línea): área táctil ancha y alta.
            Modifier.weight(1f).fillMaxWidth()
                .semantics { contentDescription = a11y; stateDescription = "${fmtDb(value)} decibeles" }
                .then(if (focused) Modifier.border(2.dp, c.accent, RoundedCornerShape(8.dp)) else Modifier)
                .focusable(enabled, source)
                .onKeyEvent { e ->
                    if (!enabled || e.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val v = current
                    val nv = when (e.key) {
                        Key.DirectionUp, Key.DirectionRight -> v + .5f
                        Key.DirectionDown, Key.DirectionLeft -> v - .5f
                        Key.PageUp -> v + 3f
                        Key.PageDown -> v - 3f
                        Key.MoveHome -> max
                        Key.MoveEnd -> Eq.MIN
                        Key.Zero, Key.NumPad0 -> 0f
                        else -> return@onKeyEvent false
                    }
                    set(nv.coerceIn(Eq.MIN, max)); true
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { set(fromY(it.y, size.height.toFloat())) }
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectVerticalDragGestures { ch, _ -> ch.consume(); set(fromY(ch.position.y, size.height.toFloat())) }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val h = size.height
                val cx = size.width / 2
                val y = h / 2 - value / 20f * (h / 2)
                drawLine(c.track, Offset(cx, 0f), Offset(cx, h), 4.dp.toPx())
                drawLine(if (enabled) c.accent else c.mute, Offset(cx, h / 2), Offset(cx, y), 4.dp.toPx())
                // Perilla de 22 dp.
                drawCircle(if (enabled) c.ink else c.mute, 11.dp.toPx(), Offset(cx, y))
            }
        }
        Box(Modifier.height(BandBottom), contentAlignment = Alignment.Center) {
            BasicText(label, style = one.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, softWrap = false)
        }
    }
}

/** Curva en miniatura de un ajuste del ecualizador (para la ficha de Reproduciendo). */
@Composable
fun EqSpark(bands: List<Float>, modifier: Modifier = Modifier) {
    val c = Ui.colors
    Canvas(modifier.size(110.dp, 34.dp)) {
        val w = size.width; val h = size.height
        drawLine(c.ink.copy(alpha = .2f), Offset(0f, h / 2), Offset(w, h / 2), 1f)
        val p = Path()
        bands.forEachIndexed { i, v ->
            val x = i * w / (bands.size - 1); val y = h / 2 - v / 20f * (h / 2 - 3)
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        drawPath(p, c.accent, style = Stroke(2.dp.toPx()))
    }
}

/** Interruptor encendido/apagado con el estilo del tema. */
@Composable
fun Switch(checked: Boolean, label: String, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val c = Ui.colors
    val track = if (checked && enabled) c.accent else c.track
    Box(
        Modifier.width(44.dp).height(24.dp)
            .then(if (enabled) Modifier.pressable(label, .94f) { onChange(!checked) } else Modifier)
            .semantics { stateDescription = if (checked) "Activado" else "Desactivado" }
            .clip(RoundedCornerShape(50)).background(track).padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) { Box(Modifier.size(18.dp).background(if (enabled) Color.White else c.mute, CircleShape)) }
}

