package app.aurora.components

import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.aurora.domain.Track
import app.aurora.theme.AuroraDimens
import app.aurora.theme.ThemeId
import app.aurora.theme.Ui
import app.aurora.theme.label
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Fila de canción. La actual muestra el ecualizador y el título en color de énfasis. */
@Composable
fun SongRow(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    number: Int? = null,
    isCurrent: Boolean = false,
    playing: Boolean = false,
    subtitle: String = "${track.artist} · ${track.album}",
    trailing: String? = formatTime(track.durationSec).takeIf { track.durationSec > 0 },
    onMore: (() -> Unit)? = null,
    playlist: app.aurora.domain.Playlist? = null,
) {
    val type = Ui.type
    Row(
        modifier
            .fillMaxWidth()
            .trackContextMenu(track, playlist)
            .pressable(track.title, .98f, hoverBg = true, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (number != null) {
            Box(Modifier.width(22.dp), contentAlignment = Alignment.Center) {
                if (isCurrent) Equalizer(playing) else BasicText("$number", style = type.caption)
            }
        }
        RowArt(track, AuroraDimens.CoverRow)
        Column(Modifier.weight(1f)) {
            if (track.mediaType == app.aurora.domain.MediaType.VIDEO) VideoBadge(Modifier.padding(bottom = 2.dp))
            BasicText(
                track.title,
                style = type.rowTitle.copy(color = if (isCurrent) Ui.colors.accent else Ui.colors.ink),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            BasicText(subtitle, style = type.rowSubtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) BasicText(trailing, style = type.caption)
        if (onMore != null) IconButton(AuroraIcon.More, "Opciones de ${track.title}", onMore, tint = Ui.colors.mute, size = 32.dp, iconSize = 18.dp)
    }
}

/** Mini reproductor (móvil): portada, título, artista, reproducir/pausa y línea de progreso de 2 dp. */
@Composable
fun MiniPlayer(
    track: Track, playing: Boolean, progress: () -> Float, onOpen: () -> Unit, onToggle: () -> Unit,
    modifier: Modifier = Modifier, onOpenVideo: (() -> Unit)? = null,
    /** Devuelve `false` si no se pudo quitar (la tarjeta vuelve a su sitio). */
    onDismiss: (() -> Boolean)? = null,
    /** Canción anterior y siguiente: botones a los lados de reproducir/pausa. */
    onPrevious: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
) {
    val c = Ui.colors
    val type = Ui.type
    val shape = Ui.shapes.card
    val soft = Ui.theme.id == ThemeId.PETALO || Ui.theme.id == ThemeId.BRUMA
    // Deslizar a la derecha lo cierra, pero solo en pausa: con audio o video sonando el gesto está bloqueado.
    val offsetX = remember { Animatable(0f) }
    var widthPx by remember { mutableStateOf(0f) }
    val extraPx = with(LocalDensity.current) { 24.dp.toPx() }
    LaunchedEffect(track.id) { offsetX.snapTo(0f) }
    LaunchedEffect(playing) { if (playing) offsetX.animateTo(0f, tween(180)) }
    Box(
        modifier
            .padding(horizontal = 10.dp)
            .fillMaxWidth()
            .height(AuroraDimens.MiniPlayerHeight)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .graphicsLayer { translationX = offsetX.value }
            .then(
                if (onDismiss != null) Modifier.pointerInput(onDismiss, playing) {
                    coroutineScope {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { change, dragAmount ->
                                // En reproducción el gesto se consume (no abre el reproductor) pero no mueve la tarjeta.
                                change.consume()
                                if (!playing) launch { offsetX.snapTo((offsetX.value + dragAmount).coerceIn(0f, widthPx + extraPx)) }
                            },
                            onDragEnd = {
                                if (!playing) launch {
                                    if (widthPx > 0f && offsetX.value > widthPx * .35f) {
                                        offsetX.animateTo(widthPx + extraPx, tween(220))
                                        if (!onDismiss()) offsetX.animateTo(0f, tween(220))
                                    } else {
                                        offsetX.animateTo(0f, tween(220))
                                    }
                                }
                            },
                            onDragCancel = { if (!playing) launch { offsetX.animateTo(0f, tween(220)) } },
                        )
                    }
                } else Modifier
            )
            .pressable("Abrir reproductor", .98f, onClick = onOpen)
            .then(if (soft) Modifier.shadow(14.dp, shape, ambientColor = Color(0x22000000), spotColor = Color(0x22000000)) else Modifier)
            .clip(shape)
            .background(c.background)
            .background(c.raised)
            .border(if (Ui.shapes.borderWidth > 0.dp) 2.5.dp else 1.dp, c.surfaceBorder, shape),
    ) {
        Row(Modifier.padding(horizontal = 10.dp).align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
            Artwork(track, AuroraDimens.CoverXs)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                BasicText(track.title, style = type.rowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicText(track.artist, style = type.rowSubtitle, maxLines = 1)
            }
            if (onOpenVideo != null) IconButton(AuroraIcon.Video, "Ver video", onOpenVideo)
            if (onPrevious != null) IconButton(AuroraIcon.Previous, "Canción anterior", onPrevious, size = 40.dp, iconSize = 20.dp)
            if (Ui.theme.controlStyle == app.aurora.theme.ControlStyle.KEYS) PlayButton(playing, 36.dp, onToggle, width = 40.dp)
            else IconButton(if (playing) AuroraIcon.Pause else AuroraIcon.Play, if (playing) "Pausar" else "Reproducir", onToggle)
            if (onNext != null) IconButton(AuroraIcon.Next, "Siguiente canción", onNext, size = 40.dp, iconSize = 20.dp)
        }
        // Se lee al dibujar: la línea avanza sin recomponer el mini reproductor.
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(2.dp).graphicsLayer().drawBehind { drawRect(c.accent, size = size.copy(width = size.width * progress())) })
    }
}

/** Pestañas del móvil: Inicio, Biblioteca, Videos, Buscar y Listas. Ajustes está en el engranaje de arriba. */
enum class Tab(val label: String, val icon: AuroraIcon) {
    Home("Inicio", AuroraIcon.Home),
    Library("Biblioteca", AuroraIcon.Library),
    Videos("Videos", AuroraIcon.Video),
    Search("Buscar", AuroraIcon.Search),
    Playlists("Listas", AuroraIcon.Playlist),
}

@Composable
fun TabBar(selected: Tab?, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val c = Ui.colors
    val theme = Ui.theme
    Column(modifier.fillMaxWidth().background(c.background).background(c.barBg)) {
        // Rockola: franja de cuadros negro y crema justo encima de las pestañas.
        if (theme.backdrop == app.aurora.theme.BackdropStyle.CHECKER_BAND) CheckerBand(8.dp)
        if (Ui.shapes.borderWidth > 0.dp) Box(Modifier.fillMaxWidth().height(3.dp).background(c.ink))
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(64.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val keys = theme.controlStyle == app.aurora.theme.ControlStyle.KEYS
            Tab.entries.forEach { tab ->
                val on = tab == selected
                val tint = if (on) (if (keys) c.ink else c.accent) else c.mute
                Column(
                    Modifier.weight(1f).pressable(tab.label) { onSelect(tab) }
                        // Casete: la pestaña activa es una tecla crema con borde y sombra dura.
                        .then(if (keys && on) Modifier.padding(horizontal = 3.dp).keyFace(c.surface, c.ink, RoundedCornerShape(10.dp)).padding(vertical = 5.dp) else Modifier),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(tab.icon, tint)
                    // Siempre en una línea: con texto grande se achica un poco en vez de partirse ("BIBLIOTE CA").
                    FitText(theme.label(tab.label), Ui.type.tab.copy(color = tint), Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp).fillMaxWidth())
                }
            }
        }
    }
}

/** Aviso: píldora arriba (móvil) o abajo (escritorio). Con [actionLabel] y [onAction], incluye un botón. */
@Composable
fun Toast(
    message: String?,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val c = Ui.colors
    AnimatedVisibility(
        visible = message != null,
        modifier = modifier.statusBarsPadding().padding(top = 12.dp, bottom = 104.dp),
        enter = fadeIn() + slideInVertically { -it / 2 },
        exit = fadeOut() + slideOutVertically { -it / 2 },
    ) {
        Row(
            Modifier.clip(Ui.shapes.chip).background(c.ink).padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                message.orEmpty(),
                style = Ui.type.body.copy(color = c.background, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.weight(1f, fill = false),
            )
            if (actionLabel != null && onAction != null) {
                BasicText(
                    actionLabel,
                    style = Ui.type.body.copy(color = c.background, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline),
                    modifier = Modifier.padding(start = 16.dp).pressable(actionLabel, .95f, onClick = onAction),
                )
            }
        }
    }
}

/** Rockola: franja de cuadros negro y crema de [height] de alto. */
@Composable
fun CheckerBand(height: Dp, modifier: Modifier = Modifier) {
    val ink = Ui.colors.ink
    androidx.compose.foundation.Canvas(modifier.fillMaxWidth().height(height)) { drawChecker(ink, this.size.height) }
}

/** Dos filas de cuadros negro y crema (de [band] / 2 de lado) en el borde superior. */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawChecker(ink: Color, band: Float) {
    val s = band / 2
    drawRect(Color(0xFFFFF4E2), size = Size(size.width, band))
    var x = 0f
    var i = 0
    while (x < size.width) {
        drawRect(ink.copy(alpha = .9f), Offset(x, if (i % 2 == 0) 0f else s), Size(s, s))
        x += s; i++
    }
}
