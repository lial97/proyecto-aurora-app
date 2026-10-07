package app.aurora.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.height
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aurora.domain.videoCaption
import app.aurora.player.PlaybackState
import app.aurora.player.PlayerController
import app.aurora.theme.Ui

/**
 * Cuadro de video. Dibuja los fotogramas del motor dentro de Compose (con la forma del tema) y pone
 * encima los controles: botón central (en pausa o al pasar el cursor), progreso, tiempo, HD y pantalla completa.
 * Sin imagen (solo audio o mientras carga) muestra la portada. Tocar el cuadro pausa o reanuda.
 */
@Composable
fun VideoSurface(
    playback: PlaybackState,
    player: PlayerController,
    modifier: Modifier = Modifier,
    fullscreen: Boolean = false,
    onFullscreen: () -> Unit = {},
    shape: Shape = if (fullscreen) RectangleShape else Ui.shapes.card,
    controls: Boolean = true,
    /** Qué hace un toque en el cuadro (por defecto, pausar o reanudar). */
    onTap: (() -> Unit)? = null,
) {
    val output = player.video
    DisposableEffect(output) {
        output?.attach()
        onDispose { output?.detach() }
    }
    val frame by (output?.frame ?: remember { kotlinx.coroutines.flow.MutableStateFlow(null) }).collectAsState()
    val track = playback.current
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val showControls = controls && (hovered || !playback.isPlaying || !Ui.isDesktop)
    val duration = playback.durationSec.takeIf { it > 0 } ?: track?.durationSec ?: 0
    val c = Ui.colors

    Box(
        modifier
            .then(if (fullscreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            .clip(shape)
            .background(Color.Black)
            .then(if (!fullscreen && Ui.shapes.borderWidth > 0.dp) Modifier.border(Ui.shapes.borderWidth * .85f, c.ink, shape) else Modifier)
            .hoverable(hover)
            .clickable(interactionSource = hover, indication = null) { onTap?.invoke() ?: player.togglePlay() },
    ) {
        val img = frame
        // Cada fotograma ocupa varios MB fuera de la memoria de Java: se libera en cuanto se deja de mostrar.
        if (img != null) DisposableEffect(img) { onDispose { output?.recycle(img) } }
        if (output?.native == true && playback.hasVideo && playback.videoEnabled) {
            // Android: el video lo dibuja la vista nativa de Media3.
            NativeVideoView(output, Modifier.matchParentSize())
        } else if (img != null && playback.videoEnabled) {
            Image(img, "Video de ${track?.title}", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        } else {
            // La portada se ve siempre: de fondo difuminada y al centro.
            Artwork(track, 1000.dp, Modifier.matchParentSize().blur(30.dp), shape = RectangleShape, border = false)
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .45f)))
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Artwork(track, if (fullscreen) 200.dp else 96.dp)
                BasicText(
                    when {
                        !playback.videoEnabled -> "Solo audio"
                        playback.videoProblem != null -> playback.videoProblem
                        playback.hasVideo -> "Cargando video…"
                        else -> "Esta pista no tiene video"
                    },
                    style = Ui.type.caption.copy(color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                    modifier = Modifier.padding(top = 10.dp, start = 24.dp, end = 24.dp),
                )
            }
        }
        if (track != null && controls) {
            BasicText(
                track.videoCaption(),
                style = Ui.type.caption.copy(color = Color.White, fontSize = if (fullscreen) 16.sp else 13.sp, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.align(Alignment.TopStart).padding(horizontal = 14.dp, vertical = 10.dp),
                maxLines = 1,
            )
        }
        AnimatedVisibility(showControls && !playback.isPlaying, Modifier.align(Alignment.Center), enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.size(68.dp).background(Color.Black.copy(alpha = .45f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(AuroraIcon.Play, Color.White, size = 30.dp)
            }
        }
        AnimatedVisibility(showControls, Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
            Row(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .7f))))
                    .padding(start = 8.dp, end = 8.dp, top = 22.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                IconButton(if (playback.isPlaying) AuroraIcon.Pause else AuroraIcon.Play, "Reproducir o pausar", player::togglePlay, tint = Color.White)
                Box(Modifier.weight(1f)) { VideoProgress(playback.positionSec, duration, player::seekTo) }
                BasicText("${formatTime(playback.positionSec)} / ${formatTime(duration)}", style = Ui.type.caption.copy(color = Color.White))
                val h = img?.height ?: 0
                if (h >= 720) {
                    BasicText(
                        if (h >= 2160) "4K" else "HD", style = Ui.type.caption.copy(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        modifier = Modifier.border(1.dp, Color.White.copy(alpha = .7f), RoundedCornerShape(3.dp)).padding(horizontal = 4.dp),
                    )
                }
                IconButton(if (fullscreen) AuroraIcon.Shrink else AuroraIcon.Expand, if (fullscreen) "Salir de pantalla completa" else "Pantalla completa", onFullscreen, tint = Color.White)
            }
        }
    }
}

/** Progreso del video: relleno con el color de énfasis; tocar o arrastrar salta. */
@Composable
private fun VideoProgress(positionSec: Int, durationSec: Int, onSeek: (Int) -> Unit) {
    val c = Ui.colors
    // Igual que ProgressBar: se salta una sola vez al soltar, no en cada movimiento.
    var dragPos by remember { mutableStateOf<Float?>(null) }
    fun at(x: Float, w: Int) = (x / w).coerceIn(0f, 1f)
    fun seek(f: Float) { if (durationSec > 0) onSeek((f * durationSec).toInt()) }
    Canvas(
        Modifier.fillMaxWidth().height(16.dp)
            .pointerInput(durationSec) { detectTapGestures { seek(at(it.x, size.width)) } }
            .pointerInput(durationSec) {
                detectHorizontalDragGestures(
                    onDragStart = { dragPos = at(it.x, size.width) },
                    onDragEnd = { dragPos?.let(::seek); dragPos = null },
                    onDragCancel = { dragPos = null },
                ) { ch, _ -> ch.consume(); dragPos = at(ch.position.x, size.width) }
            },
    ) {
        val h = 4.dp.toPx()
        val y = size.height / 2 - h / 2
        drawRect(Color.White.copy(alpha = .3f), Offset(0f, y), Size(size.width, h))
        val f = dragPos ?: if (durationSec > 0) positionSec.toFloat() / durationSec else 0f
        drawRect(c.accent, Offset(0f, y), Size(size.width * f, h))
    }
}

/**
 * Mini video flotante: aparece cuando hay un video sonando y no estás en su vista (segundo plano).
 * Tocarlo o "ampliar" abre el video; "cerrar" lo oculta y el sonido sigue.
 */
@Composable
fun PipVideo(playback: PlaybackState, player: PlayerController, onExpand: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier, width: androidx.compose.ui.unit.Dp = 300.dp) {
    val shape = Ui.shapes.card
    Box(
        modifier.size(width = width, height = width * 9f / 16f)
            .shadow(22.dp, shape)
            .clip(shape)
            .background(Color.Black),
    ) {
        VideoSurface(playback, player, Modifier.matchParentSize(), controls = false, shape = shape, onTap = onExpand)
        Row(Modifier.align(Alignment.TopEnd).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            IconButton(if (playback.isPlaying) AuroraIcon.Pause else AuroraIcon.Play, "Reproducir o pausar", player::togglePlay,
                Modifier.background(Color.Black.copy(alpha = .45f), CircleShape), tint = Color.White, size = 30.dp, iconSize = 16.dp)
            IconButton(AuroraIcon.Expand, "Ampliar video", onExpand,
                Modifier.background(Color.Black.copy(alpha = .45f), CircleShape), tint = Color.White, size = 30.dp, iconSize = 16.dp)
            IconButton(AuroraIcon.Close, "Cerrar mini video (el sonido sigue)", onClose,
                Modifier.background(Color.Black.copy(alpha = .45f), CircleShape), tint = Color.White, size = 30.dp, iconSize = 16.dp)
        }
        val duration = playback.durationSec.takeIf { it > 0 } ?: playback.current?.durationSec ?: 0
        val f = if (duration > 0) playback.positionSec.toFloat() / duration else 0f
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(f).height(3.dp).background(Ui.colors.accent))
    }
}

/** Vista de video nativa (Android: PlayerView de Media3). En escritorio no se usa. */
@Composable
expect fun NativeVideoView(output: app.aurora.player.VideoOutput, modifier: Modifier)
