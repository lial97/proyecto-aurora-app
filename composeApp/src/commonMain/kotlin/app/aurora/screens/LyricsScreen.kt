package app.aurora.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aurora.components.Artwork
import app.aurora.components.AuroraIcon
import app.aurora.components.EmptyState
import app.aurora.components.IconButton
import app.aurora.components.PlayButton
import app.aurora.components.ProgressBar
import app.aurora.components.formatTime
import app.aurora.components.pressable
import app.aurora.domain.Track
import app.aurora.data.LyricsState
import app.aurora.domain.LyricLine
import app.aurora.domain.activeIndexAtMs
import app.aurora.domain.sungChars
import app.aurora.components.Chip
import app.aurora.components.Icon
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import app.aurora.player.PlaybackState
import app.aurora.player.PlayerController
import app.aurora.theme.AuroraDimens
import app.aurora.theme.ThemeId
import app.aurora.theme.Ui
import app.aurora.theme.title

/**
 * Posición suave en ms: parte del último aviso del motor y avanza con el reloj de la pantalla
 * mientras suena (el motor avisa cada ~250 ms; el karaoke necesita moverse en cada fotograma).
 */
@Composable
fun rememberSmoothPositionMs(playback: PlaybackState): State<Long> {
    val now = remember { mutableLongStateOf(playback.positionMs) }
    LaunchedEffect(playback.positionMs, playback.isPlaying, playback.current?.id) {
        val anchor = playback.positionMs
        now.longValue = anchor
        if (!playback.isPlaying) return@LaunchedEffect
        val t0 = withFrameNanos { it }
        while (true) {
            val t = app.aurora.components.awaitAnimationFrame()
            now.longValue = anchor + ((t - t0) / 1_000_000).coerceAtMost(1500)
        }
    }
    return now
}

/** Texto corto que explica de dónde viene la letra (o por qué no hay). */
fun lyricsStatus(l: LyricsState): String = when (l) {
    LyricsState.None -> ""
    LyricsState.Loading -> "Buscando la letra…"
    is LyricsState.Found -> if (l.synced) "Letra sincronizada ${l.source.label}" else "Letra sin sincronizar ${l.source.label}"
    LyricsState.Instrumental -> "Canción instrumental"
    is LyricsState.NotFound -> l.reason
    LyricsState.Disabled -> "La descarga automática de letras está desactivada (Ajustes)"
}

/**
 * Letra tipo karaoke. La línea activa se ubica a ~36 % de la altura y se rellena de color mientras se canta
 * (palabra por palabra si la letra trae esos tiempos). Futuras al 28 % con desenfoque, pasadas al 45 %.
 * Tocar una línea salta a ese momento.
 */
@Composable
fun LyricsView(
    lyrics: LyricsState,
    playback: PlaybackState,
    onSeek: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    onSave: (() -> Unit)? = null,
    /** Desfase de la letra en ms (positivo = adelantada). */
    offsetMs: Long = 0,
    /** Escala del texto de la letra (A− / A+). */
    textScale: Float = 1f,
    /** Barra de herramientas propia (la vista de escritorio pone la suya). */
    showHeader: Boolean = true,
) {
    val theme = Ui.theme
    val type = Ui.type
    val c = Ui.colors
    Column(modifier) {
        // Origen de la letra y acciones.
        if (showHeader) Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (lyrics == LyricsState.Loading) Icon(AuroraIcon.Refresh, c.mute, size = 14.dp, modifier = Modifier.padding(end = 6.dp))
            BasicText(lyricsStatus(lyrics), style = type.caption, modifier = Modifier.weight(1f), maxLines = 1)
            if (lyrics is LyricsState.NotFound || (lyrics is LyricsState.Found && !lyrics.synced) || lyrics == LyricsState.Disabled) {
                Chip("Buscar de nuevo", false, onRetry, icon = AuroraIcon.Refresh)
            }
            if (onSave != null && lyrics is LyricsState.Found && lyrics.synced && lyrics.source == app.aurora.data.LyricsSource.LRCLIB) {
                Chip("Guardar .lrc", false, onSave, icon = AuroraIcon.Download)
            }
        }
        val found = lyrics as? LyricsState.Found
        when {
            found != null && found.synced -> KaraokeLines(found.lines, playback, onSeek, Modifier.weight(1f).fillMaxWidth(), offsetMs, textScale * (if (Ui.prefs.bigLyrics) 1.25f else 1f))
            found?.plain != null -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                BasicText(found.plain, style = type.body.copy(lineHeight = type.body.fontSize * 1.6f))
            }
            lyrics == LyricsState.Loading -> EmptyState("Buscando la letra…", "Se busca en LRCLIB por título, artista y duración.", Modifier.weight(1f))
            lyrics == LyricsState.Instrumental -> EmptyState("Instrumental", "Esta canción no tiene letra.", Modifier.weight(1f))
            else -> EmptyState(
                "Sin letra",
                "Puedes poner un archivo .lrc con el mismo nombre junto a la canción, o pulsar \"Buscar de nuevo\".",
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun KaraokeLines(lines: List<LyricLine>, playback: PlaybackState, onSeek: (Int) -> Unit, modifier: Modifier, offsetMs: Long, textScale: Float) {
    val theme = Ui.theme
    val type = Ui.type.let { t -> t.copy(lyric = t.lyric.copy(fontSize = t.lyric.fontSize * textScale, lineHeight = t.lyric.lineHeight * textScale)) }
    val c = Ui.colors
    val raw = rememberSmoothPositionMs(playback)
    // La letra se adelanta o atrasa según el desfase guardado para esta canción.
    val pos = remember(offsetMs) { derivedStateOf { raw.value + offsetMs } }
    val active by remember(lines, offsetMs) { derivedStateOf { lines.activeIndexAtMs(pos.value) } }
    val listState = rememberLazyListState()
    val reduce = Ui.reduceMotion
    // Seguir la canción: si el usuario gira la rueda o arrastra, se deja de seguir hasta que pulse "Volver".
    var follow by remember { mutableStateOf(true) }
    BoxWithConstraints(modifier) {
        // El relleno superior es el 36 % del alto: desplazarse a un ítem lo deja justo debajo.
        LaunchedEffect(active, follow) { if (active >= 0 && follow) { if (reduce) listState.scrollToItem(active) else listState.animateScrollToItem(active) } }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = maxHeight * .36f, bottom = maxHeight * .64f),
            verticalArrangement = Arrangement.spacedBy(if (Ui.isDesktop) 22.dp else 16.dp),
            modifier = Modifier.fillMaxSize().fadingEdges().pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                        if (e.type == androidx.compose.ui.input.pointer.PointerEventType.Scroll) follow = false
                    }
                }
            },
        ) {
            itemsIndexed(lines) { i, line ->
                val state = when {
                    i == active -> 0
                    i < active -> -1
                    else -> 1
                }
                val alpha by animateFloatAsState(if (state == 0) 1f else if (state < 0) .45f else .28f, tween(500))
                val scale by animateFloatAsState(if (state == 0) 1f else .96f, tween(500))
                val glow = if (state == 0 && theme.id == ThemeId.AURORA) c.accent else Color.Transparent
                val shown = if (line.isInstrumental) "♪   ♪   ♪" else theme.title(line.text)
                val base = type.lyric.copy(shadow = Shadow(glow, blurRadius = 26f))
                val mod = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { this.alpha = alpha; scaleX = scale; scaleY = scale; transformOrigin = TransformOrigin(0f, .5f) }
                    .then(if (state > 0) Modifier.blur(1.2.dp) else Modifier)
                    .pressable(line.text, .98f) { onSeek(line.startSec) }
                if (state == 0 && !line.isInstrumental) {
                    // Karaoke: lo ya cantado en el color del tema, el resto más tenue.
                    val sung by remember(lines, i) { derivedStateOf { lines.sungChars(i, pos.value).toInt() } }
                    val text = buildAnnotatedString {
                        withStyle(SpanStyle(color = c.lyricOn)) { append(shown.take(sung)) }
                        withStyle(SpanStyle(color = c.ink.copy(alpha = .5f))) { append(shown.drop(sung)) }
                    }
                    if (theme.hero == app.aurora.theme.HeroStyle.CASSETTE) {
                        // Casete: la línea activa lleva un subrayado mostaza (el 40 % inferior de cada renglón).
                        var layout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
                        val marker = Color(0xFFE9B949).copy(alpha = .8f)
                        BasicText(text, style = base, onTextLayout = { layout = it }, modifier = mod.drawBehind {
                            val l = layout ?: return@drawBehind
                            for (k in 0 until l.lineCount) {
                                val top = l.getLineTop(k); val bottom = l.getLineBottom(k)
                                val y = top + (bottom - top) * .6f
                                drawRect(marker, Offset(l.getLineLeft(k), y), Size(l.getLineRight(k) - l.getLineLeft(k), bottom - y))
                            }
                        })
                    } else BasicText(text, style = base, modifier = mod)
                } else {
                    BasicText(shown, style = base.copy(color = if (state == 0) c.lyricOn else c.ink), modifier = mod)
                }
            }
        }
        androidx.compose.animation.AnimatedVisibility(!follow, Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)) {
            Chip("Volver a la línea actual", true, { follow = true }, icon = AuroraIcon.Down)
        }
    }
}

/** Pantalla Letra del móvil. */
@Composable
fun LyricsScreen(playback: PlaybackState, player: PlayerController, lyrics: LyricsState, onClose: () -> Unit, onRetry: () -> Unit = {}, onSave: (() -> Unit)? = null, offsetMs: Long = 0) {
    val track = playback.current ?: return
    val type = Ui.type
    val duration = playback.durationSec.takeIf { it > 0 } ?: track.durationSec
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = AuroraDimens.ScreenPadding)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(AuroraIcon.Down, "Cerrar letra", onClose)
            Artwork(track, AuroraDimens.CoverXs, Modifier.padding(start = 6.dp))
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                BasicText(track.title, style = type.rowTitle, maxLines = 1)
                BasicText(track.artist, style = type.rowSubtitle, maxLines = 1)
            }
        }
        LyricsView(lyrics, playback, player::seekTo, Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp), onRetry, onSave, offsetMs = offsetMs)
        ProgressBar(playback.positionSec, duration, player::seekTo)
        Row(Modifier.fillMaxWidth()) {
            app.aurora.components.PositionText(playback.positionSec)
            Spacer(Modifier.weight(1f))
            BasicText("-" + formatTime(duration - playback.positionSec), style = type.caption)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(AuroraIcon.Previous, "Anterior", player::previous, size = 48.dp, key = true)
            PlayButton(playback.isPlaying, 60.dp, player::togglePlay)
            IconButton(AuroraIcon.Next, "Siguiente", player::next, size = 48.dp, key = true)
        }
    }
}

/** Desvanece los bordes superior e inferior (máscara degradada). */
fun Modifier.fadingEdges() = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            Brush.verticalGradient(0f to Color.Transparent, .14f to Color.Black, .78f to Color.Black, 1f to Color.Transparent),
            blendMode = BlendMode.DstIn,
        )
    }

/**
 * Letra compacta para el panel "Reproduciendo" (escritorio): la línea anterior, la actual con el relleno
 * del karaoke y las siguientes, que suben con suavidad al cambiar de línea.
 */
@Composable
fun MiniKaraoke(lyrics: LyricsState, playback: PlaybackState, modifier: Modifier = Modifier, visibleAfter: Int = 4, textScale: Float = 1f) {
    val c = Ui.colors
    val type = Ui.type
    val theme = Ui.theme
    val found = lyrics as? LyricsState.Found
    if (found == null || !found.synced) {
        val text = when {
            found?.plain != null -> found.plain.lineSequence().filter { it.isNotBlank() }.take(6).joinToString("\n")
            else -> lyricsStatus(lyrics).ifEmpty { "Sin letra" }
        }
        BasicText(text, style = type.body.copy(color = if (found?.plain != null) c.ink else c.mute, lineHeight = type.body.fontSize * 1.5f), modifier = modifier)
        return
    }
    val lines = found.lines
    val pos = rememberSmoothPositionMs(playback)
    val active by remember(lines) { derivedStateOf { lines.activeIndexAtMs(pos.value) } }
    val big = type.lyric.copy(fontSize = type.lyric.fontSize * .62f * textScale, lineHeight = type.lyric.fontSize * .74f * textScale)
    val listState = rememberLazyListState()
    // La línea anterior queda arriba y la actual justo debajo; la lista sube con suavidad.
    LaunchedEffect(active) { listState.animateScrollToItem((active - 1).coerceAtLeast(0)) }
    LazyColumn(
        state = listState,
        userScrollEnabled = false,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 400.dp),
        modifier = modifier.clipToBounds().fadeBottom(),
    ) {
        itemsIndexed(lines) { i, line ->
            val isActive = i == active
            val alpha by animateFloatAsState(when { isActive -> 1f; i < active -> .35f; else -> .55f }, tween(400))
            val shown = if (line.isInstrumental) "♪  ♪  ♪" else theme.title(line.text)
            val style = big.copy(shadow = Shadow(if (isActive && theme.id == ThemeId.AURORA) c.accent else Color.Transparent, blurRadius = 22f))
            val m = Modifier.fillMaxWidth().graphicsLayer { this.alpha = alpha }
            when {
                isActive && !line.isInstrumental -> {
                    val sung by remember(lines, i) { derivedStateOf { lines.sungChars(i, pos.value).toInt() } }
                    BasicText(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = c.lyricOn)) { append(shown.take(sung)) }
                            withStyle(SpanStyle(color = c.ink.copy(alpha = .5f))) { append(shown.drop(sung)) }
                        },
                        style = style, modifier = m,
                    )
                }
                isActive -> BasicText(shown, style = style.copy(color = c.lyricOn, letterSpacing = 4.sp), modifier = m)
                else -> BasicText(shown, style = style.copy(color = c.ink), modifier = m)
            }
        }
    }
}

/** Desvanece solo el borde inferior (para recuadros de letra de alto fijo). */
fun Modifier.fadeBottom() = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(Brush.verticalGradient(0f to Color.Black, .78f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
    }
