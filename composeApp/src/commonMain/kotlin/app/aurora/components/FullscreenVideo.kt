package app.aurora.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aurora.domain.videoCaption
import app.aurora.player.PlaybackState
import app.aurora.player.PlayerController
import kotlinx.coroutines.delay

/** Cuánto tiempo sin tocar la pantalla hasta que se ocultan los controles. */
const val FULLSCREEN_HIDE_MS = 3000L

/**
 * Video a pantalla completa (móvil), con controles encima como en YouTube:
 * arriba salir y título; al centro anterior, reproducir/pausa y siguiente; abajo el progreso y salir.
 * Los controles se ocultan a los 3 s (fundido de 250 ms), salvo en pausa o mientras se arrastra la barra.
 * Tocar muestra u oculta; doble toque a la izquierda o a la derecha retrocede o avanza 10 s.
 * Todo respeta las zonas del sistema y el recorte de la cámara (safeDrawing).
 */
@Composable
fun FullscreenVideo(playback: PlaybackState, player: PlayerController, onExit: () -> Unit) {
    val track = playback.current
    val duration = playback.durationSec.takeIf { it > 0 } ?: track?.durationSec ?: 0
    var visible by remember { mutableStateOf(true) }
    var dragging by remember { mutableStateOf(false) }
    // Cada toque reinicia la cuenta para ocultar.
    var touches by remember { mutableIntStateOf(0) }
    // Aviso "−10 s" / "+10 s" del doble toque (−1 izquierda, 1 derecha, 0 nada).
    var skip by remember { mutableIntStateOf(0) }
    var skipCount by remember { mutableIntStateOf(0) }
    val keepVisible = !playback.isPlaying || dragging
    LaunchedEffect(visible, keepVisible, touches) {
        if (visible && !keepVisible) { delay(FULLSCREEN_HIDE_MS); visible = false }
    }
    // Al pausar (desde aquí, la notificación o los auriculares) los controles vuelven a verse.
    LaunchedEffect(playback.isPlaying) { if (!playback.isPlaying) visible = true }
    LaunchedEffect(skipCount) { if (skip != 0) { delay(700); skip = 0 } }
    val pos by rememberUpdatedState(playback.positionSec)
    val dur by rememberUpdatedState(duration)
    val fade = tween<Float>(250)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VideoSurface(playback, player, Modifier.fillMaxSize(), fullscreen = true, controls = false, shape = RectangleShape, onTap = {})
        // Capa de gestos: toque (mostrar u ocultar) y doble toque (±10 s).
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures(
                    onTap = { visible = !visible; touches++ },
                    onDoubleTap = { o ->
                        val dir = if (o.x < size.width / 2) -1 else 1
                        player.seekTo((pos + dir * 10).coerceIn(0, maxOf(0, dur - 1)))
                        skip = dir; skipCount++; touches++
                    },
                )
            },
        )
        if (skip != 0) {
            BasicText(
                if (skip < 0) "−10 s" else "+10 s",
                style = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.align(if (skip < 0) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 48.dp).background(Color.Black.copy(alpha = .45f), RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
        AnimatedVisibility(visible, Modifier.fillMaxSize(), enter = fadeIn(fade), exit = fadeOut(fade)) {
            Box(Modifier.fillMaxSize()) {
                // Degradados para que los controles se lean sobre cualquier imagen.
                Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(140.dp)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .75f), Color.Transparent))))
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(160.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .8f)))))
                // Arriba: salir y título.
                Row(
                    Modifier.align(Alignment.TopStart).fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(AuroraIcon.Back, "Salir de pantalla completa", onExit, tint = Color.White)
                    BasicText(
                        track?.videoCaption().orEmpty(),
                        style = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp).weight(1f),
                    )
                }
                // Centro: anterior, reproducir/pausa y siguiente.
                Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(36.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoundButton(AuroraIcon.Previous, "Anterior", 52.dp) { player.previous(); touches++ }
                    RoundButton(if (playback.isPlaying) AuroraIcon.Pause else AuroraIcon.Play, if (playback.isPlaying) "Pausar" else "Reproducir", 72.dp) { player.togglePlay(); touches++ }
                    RoundButton(AuroraIcon.Next, "Siguiente", 52.dp) { player.next(); touches++ }
                }
                // Abajo: progreso con tiempos y salir, siempre por encima de las barras del sistema.
                Row(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                        .padding(start = 16.dp, end = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val time = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 13.sp, fontFeatureSettings = "tnum")
                    BasicText(formatTime(playback.positionSec), style = time)
                    Box(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        FullscreenProgress(playback.positionSec, duration, { player.seekTo(it); touches++ }, { dragging = it; touches++ })
                    }
                    BasicText(formatTime(duration), style = time)
                    IconButton(AuroraIcon.Shrink, "Salir de pantalla completa", onExit, tint = Color.White, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun RoundButton(icon: AuroraIcon, label: String, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size).pressable(label, .9f, onClick = onClick).background(Color.Black.copy(alpha = .4f), CircleShape),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, Color.White, size = size * .45f) }
}

/** Barra de progreso de pantalla completa: área táctil de 32 dp; avisa mientras se arrastra. */
@Composable
private fun FullscreenProgress(positionSec: Int, durationSec: Int, onSeek: (Int) -> Unit, onDragging: (Boolean) -> Unit) {
    var dragPos by remember { mutableStateOf<Float?>(null) }
    val accent = app.aurora.theme.Ui.colors.accent
    fun at(x: Float, w: Int) = (x / w).coerceIn(0f, 1f)
    Canvas(
        Modifier.fillMaxWidth().height(32.dp)
            .semantics { contentDescription = "Progreso del video" }
            .pointerInput(durationSec) { detectTapGestures { if (durationSec > 0) onSeek((at(it.x, size.width) * durationSec).toInt()) } }
            .pointerInput(durationSec) {
                detectHorizontalDragGestures(
                    onDragStart = { dragPos = at(it.x, size.width); onDragging(true) },
                    onDragEnd = { dragPos?.let { if (durationSec > 0) onSeek((it * durationSec).toInt()) }; dragPos = null; onDragging(false) },
                    onDragCancel = { dragPos = null; onDragging(false) },
                ) { ch, _ -> ch.consume(); dragPos = at(ch.position.x, size.width) }
            },
    ) {
        val h = 4.dp.toPx(); val y = size.height / 2
        val f = dragPos ?: if (durationSec > 0) positionSec.toFloat() / durationSec else 0f
        drawRoundRect(Color.White.copy(alpha = .3f), Offset(0f, y - h / 2), Size(size.width, h), CornerRadius(h))
        drawRoundRect(accent, Offset(0f, y - h / 2), Size(size.width * f, h), CornerRadius(h))
        drawCircle(accent, if (dragPos != null) 9.dp.toPx() else 7.dp.toPx(), Offset(size.width * f, y))
    }
}
