package app.aurora.screens

import androidx.compose.animation.core.animateFloatAsState
import app.aurora.components.rememberAnimationSeconds
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import app.aurora.components.Artwork
import app.aurora.components.Icon
import app.aurora.theme.label
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aurora.color.moodFromBpm
import app.aurora.components.AuroraIcon
import app.aurora.components.Dot
import app.aurora.components.Hero
import app.aurora.components.IconButton
import app.aurora.components.PlayButton
import app.aurora.components.ProgressBar
import app.aurora.components.TechBadges
import app.aurora.components.formatTime
import app.aurora.components.pressable
import app.aurora.components.surface
import app.aurora.domain.Track
import app.aurora.domain.activeIndexAt
import app.aurora.player.PlaybackState
import app.aurora.player.PlayerController
import app.aurora.theme.AuroraDimens
import app.aurora.theme.AuroraMotion
import app.aurora.theme.HeroStyle
import app.aurora.theme.PlayerPanel
import app.aurora.theme.ThemeId
import app.aurora.theme.Ui
import app.aurora.theme.title

/** Escala de la portada: 0.9 en pausa, sube a 1 con rebote y "respira" en Aurora (spec §7). */
@Composable
fun heroScale(playing: Boolean): Float {
    val base by animateFloatAsState(if (playing) 1f else .9f, tween(700, easing = AuroraMotion.Bounce))
    val breathe = Ui.theme.hero == HeroStyle.GLOW && !Ui.reduceMotion
    // El "respiro" solo existe mientras suena y el tema lo usa: si no, redibujaría la ventana sin parar.
    // Va al ritmo de las animaciones continuas (en escritorio, ~30 fps): sube 2,5 % en 2 s × velocidad y baja.
    val clock = rememberAnimationSeconds(playing && breathe)
    val breath = if (playing && breathe) 1f + .0125f * (1f - kotlin.math.cos(kotlin.math.PI.toFloat() * clock.value / (2f * Ui.colors.speed))) else 1f
    return base * breath
}

/** Línea de letra que suena ahora (o un aviso si no hay letra). */
fun currentLyric(lyrics: app.aurora.data.LyricsState, positionSec: Int): String {
    val f = lyrics as? app.aurora.data.LyricsState.Found ?: return lyricsStatus(lyrics).ifEmpty { "Sin letra" }
    if (!f.synced) return "Letra disponible (sin sincronizar)"
    return f.lines.getOrNull(f.lines.activeIndexAt(positionSec))?.text ?: "♪"
}

/** Título, artista, me gusta y etiquetas del tema. */
@Composable
fun TrackHeadline(track: Track, liked: Boolean, onLike: () -> Unit, titleStyle: androidx.compose.ui.text.TextStyle, centered: Boolean, modifier: Modifier = Modifier) {
    val theme = Ui.theme
    val c = Ui.colors
    Box(modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(end = if (centered) 0.dp else 44.dp).padding(horizontal = if (centered) 40.dp else 0.dp),
            horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            BasicText(
                theme.title(track.title), style = titleStyle.copy(textAlign = if (centered) TextAlign.Center else TextAlign.Start),
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                if (theme.accentArtist) track.artist.uppercase() else track.artist, style = Ui.type.artist,
                modifier = Modifier.padding(top = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (theme.showTechBadges) TechBadges(track.bpm, track.format, track.bitrateKbps)
            if (theme.dynamicColor) {
                val mood = moodFromBpm(track.bpm)
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Dot(c.accent)
                    BasicText("${mood.label.text}, ${mood.bpm} BPM", style = Ui.type.caption, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
        IconButton(
            if (liked) AuroraIcon.HeartFilled else AuroraIcon.Heart, if (liked) "Quitar de Me gusta" else "Me gusta", onLike,
            Modifier.align(Alignment.TopEnd), tint = if (liked) c.accent else c.ink,
        )
    }
}

/** Pestañas del panel deslizable del reproductor móvil. */
enum class PlayerPanelTab(val label: String, val icon: AuroraIcon) { LYRICS("Letra", AuroraIcon.Lyrics), QUEUE("Cola", AuroraIcon.Queue), INFO("Info", AuroraIcon.Info) }

/**
 * Reproductor móvil: portada, tarjeta con "Desde…", título, me gusta y etiquetas, progreso y controles.
 * Abajo, Letra · Cola · Info abren un panel que sube desde abajo.
 */
@Composable
fun PlayerScreen(
    state: app.aurora.AppState,
    playback: PlaybackState,
    onClose: () -> Unit,
    onOpenVideo: () -> Unit = {},
) {
    val track = playback.current ?: return
    val player = state.player
    val type = Ui.type
    val c = Ui.colors
    val theme = Ui.theme
    val prefs by state.prefsRepo.prefs.collectAsState()
    val liked = track.id in state.liked
    var panel by rememberSaveable { mutableStateOf<PlayerPanelTab?>(null) }
    // Atrás cierra primero el panel Letra/Cola/Info.
    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    androidx.compose.ui.backhandler.BackHandler(enabled = panel != null) { panel = null }
    val duration = playback.durationSec.takeIf { it > 0 } ?: track.durationSec
    val heroSize: Dp = theme.heroSizes.player.dp

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = AuroraDimens.ScreenPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Barra superior: cerrar, Canción | Video y opciones.
            Box(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                IconButton(AuroraIcon.Down, "Cerrar reproductor", onClose, Modifier.align(Alignment.CenterStart))
                app.aurora.components.SongVideoSwitch(false, playback.hasVideo, onSong = {}, onVideo = onOpenVideo, modifier = Modifier.align(Alignment.Center))
                IconButton(AuroraIcon.More, "Más opciones", { state.menuFor(track) }, Modifier.align(Alignment.CenterEnd))
            }

            Box(
                Modifier.fillMaxWidth().padding(top = 22.dp),
                contentAlignment = if (theme.leftAlignedHero) Alignment.TopStart else Alignment.TopCenter,
            ) { Hero(track, heroSize, scale = heroScale(playback.isPlaying), playing = playback.isPlaying, progress = playback.progress) }

            // Tarjeta: origen, título, me gusta, etiquetas y progreso.
            val cardShape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp)
            val card = when (theme.playerPanel) {
                PlayerPanel.GLASS_OVERLAP -> Modifier.surface(cardShape, c.background.copy(alpha = .55f))
                PlayerPanel.WHITE_OVERLAP -> Modifier.shadow(18.dp, cardShape, ambientColor = Color(0x334A2B3A), spotColor = Color(0x334A2B3A))
                    .clip(cardShape).background(Color.White)
                PlayerPanel.PLAIN -> Modifier
            }
            val pad = if (theme.playerPanel == PlayerPanel.PLAIN) 0.dp else 16.dp
            Column(Modifier.padding(top = 18.dp).fillMaxWidth().then(card).padding(horizontal = pad, vertical = if (pad > 0.dp) 14.dp else 0.dp)) {
                BasicText("Desde ${state.source}", style = type.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        BasicText(theme.title(track.title), style = type.playerTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        BasicText(
                            if (theme.accentArtist) track.artist.uppercase() else track.artist, style = type.artist,
                            modifier = Modifier.padding(top = 3.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(
                        if (liked) AuroraIcon.HeartFilled else AuroraIcon.Heart, if (liked) "Quitar de Me gusta" else "Me gusta",
                        { state.toggleLike(track) }, tint = if (liked) c.accent else c.ink,
                    )
                }
                PlayerTags(track, prefs.eq, state.sleep != null, Modifier.padding(top = 10.dp))
                ProgressBar(playback.positionSec, duration, player::seekTo, Modifier.padding(top = 14.dp), wavePlaying = playback.isPlaying)
                Row(Modifier.fillMaxWidth()) {
                    app.aurora.components.PositionText(playback.positionSec)
                    Spacer(Modifier.weight(1f))
                    BasicText("-" + formatTime(duration - playback.positionSec), style = type.caption)
                }
            }

            val boxed = theme.id == ThemeId.POSTER
            Row(
                Modifier.fillMaxWidth().padding(vertical = 14.dp)
                    .then(if (boxed) Modifier.border(3.dp, c.ink).padding(vertical = 8.dp) else Modifier),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(AuroraIcon.Shuffle, "Aleatorio", {
                    player.toggleShuffle(); state.show(if (!playback.shuffle) "Aleatorio activado" else "Aleatorio desactivado")
                }, tint = if (playback.shuffle) c.accent else c.ink, key = true)
                IconButton(AuroraIcon.Previous, "Anterior", player::previous, size = 48.dp, key = true)
                if (boxed) PlayButton(playback.isPlaying, 58.dp, player::togglePlay, width = 90.dp)
                else PlayButton(playback.isPlaying, AuroraDimens.PlayButtonPlayer, player::togglePlay)
                IconButton(AuroraIcon.Next, "Siguiente", player::next, size = 48.dp, key = true)
                IconButton(AuroraIcon.Repeat, "Repetir", {
                    player.toggleRepeat(); state.show(if (!playback.repeatOne) "Repetir esta canción" else "Repetir desactivado")
                }, tint = if (playback.repeatOne) c.accent else c.ink, key = true)
            }

            // Accesos al panel: Letra · Cola · Info.
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlayerPanelTab.entries.forEach { t ->
                    Row(
                        Modifier.weight(1f).pressable(t.label, .96f) { panel = t }.surface(Ui.shapes.chip).padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(t.icon, c.ink, size = 17.dp)
                        BasicText(theme.label(t.label), style = type.label.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }

        // Panel deslizable.
        androidx.compose.animation.AnimatedVisibility(
            visible = panel != null, enter = fadeIn(tween(200)), exit = fadeOut(tween(200)),
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .35f)).pointerInput(Unit) { detectTapGestures { panel = null } })
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = panel != null, modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(if (Ui.reduceMotion) 0 else 380, easing = AuroraMotion.Ease)) { it },
            exit = slideOutVertically(tween(if (Ui.reduceMotion) 0 else 260)) { it },
        ) {
            var last by remember { mutableStateOf(PlayerPanelTab.LYRICS) }
            panel?.let { last = it }
            PlayerSheet(state, playback, last, { panel = it }, { panel = null })
        }
    }
}

/** Etiquetas bajo el título: ánimo y BPM, formato, tonalidad, ecualizador activo y temporizador. */
@Composable
private fun PlayerTags(track: Track, eq: app.aurora.audio.EqSettings, sleeping: Boolean, modifier: Modifier = Modifier) {
    val c = Ui.colors
    val mood = moodFromBpm(track.bpm)
    val tags = listOfNotNull(
        (if (track.bpm != null) "${mood.label.text} · ${mood.bpm} BPM" else mood.label.text) to true,
        track.format?.let { it to false },
        track.key?.let { it to false },
        if (eq.enabled) ("EQ " + if (eq.isCustom) "propio" else app.aurora.audio.Eq.presets[eq.preset].name) to false else null,
    )
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        tags.forEach { (t, acc) ->
            BasicText(
                t, style = Ui.type.caption.copy(color = if (acc) c.accent else c.ink, fontWeight = FontWeight.SemiBold), maxLines = 1,
                modifier = Modifier.clip(Ui.shapes.chip).background(if (acc) c.accent.copy(alpha = .14f) else c.ink.copy(alpha = .07f)).padding(horizontal = 9.dp, vertical = 4.dp),
            )
        }
        if (sleeping) Box(Modifier.clip(Ui.shapes.chip).background(c.ink.copy(alpha = .07f)).padding(horizontal = 7.dp, vertical = 3.dp)) {
            Icon(AuroraIcon.Timer, c.ink, size = 14.dp)
        }
    }
}

/** Panel que sube desde el reproductor: letra karaoke, cola reordenable o información con EQ y temporizador. */
@Composable
private fun PlayerSheet(state: app.aurora.AppState, playback: PlaybackState, tab: PlayerPanelTab, onTab: (PlayerPanelTab) -> Unit, onClose: () -> Unit) {
    val track = playback.current ?: return
    val c = Ui.colors
    val type = Ui.type
    val player = state.player
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    var drag by remember { mutableStateOf(0f) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().height(maxHeight * .86f).offset { androidx.compose.ui.unit.IntOffset(0, drag.coerceAtLeast(0f).toInt()) }
                .shadow(24.dp, shape).clip(shape).background(c.background).background(c.raised)
                .pointerInput(Unit) { detectTapGestures { } }
                .navigationBarsPadding().padding(horizontal = AuroraDimens.ScreenPadding),
        ) {
            // Asa: arrastrar hacia abajo cierra.
            Box(
                Modifier.fillMaxWidth().pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = { if (drag > 140f) onClose(); drag = 0f },
                        onDragCancel = { drag = 0f },
                    ) { ch, d -> ch.consume(); drag += d }
                }.padding(top = 10.dp, bottom = 6.dp),
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.width(40.dp).height(4.dp).clip(Ui.shapes.chip).background(c.ink.copy(alpha = .25f))) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Artwork(track, 40.dp)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    BasicText(track.title, style = type.rowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BasicText(track.artist, style = type.caption, maxLines = 1)
                }
                IconButton(AuroraIcon.Down, "Cerrar panel", onClose)
            }
            Row(Modifier.padding(top = 10.dp).fillMaxWidth().surface(Ui.shapes.chip).padding(3.dp)) {
                PlayerPanelTab.entries.forEach { t ->
                    Box(
                        Modifier.weight(1f).pressable(t.label, .97f) { onTab(t) }.clip(Ui.shapes.chip)
                            .background(if (t == tab) c.chipOn else Color.Transparent).padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) { BasicText(Ui.theme.label(t.label), style = type.label.copy(color = if (t == tab) c.chipOnInk else c.ink, fontWeight = FontWeight.SemiBold)) }
                }
            }
            Spacer(Modifier.height(12.dp))
            when (tab) {
                PlayerPanelTab.LYRICS -> SheetLyrics(state, playback, track, Modifier.weight(1f))
                PlayerPanelTab.QUEUE -> app.aurora.desktop.QueueTab(state, playback, Modifier.weight(1f))
                PlayerPanelTab.INFO -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    app.aurora.desktop.InfoTab(state, track, Modifier.heightIn(max = 2000.dp))
                    SheetEqAndTimer(state)
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun SheetLyrics(state: app.aurora.AppState, playback: PlaybackState, track: Track, modifier: Modifier) {
    val player = state.player
    val l = state.lyrics
    val synced = l is app.aurora.data.LyricsState.Found && l.synced
    var scale by rememberSaveable { mutableStateOf(1f) }
    val duration = playback.durationSec.takeIf { it > 0 } ?: track.durationSec
    val c = Ui.colors
    val sheet = Ui.theme.hero == app.aurora.theme.HeroStyle.CASSETTE
    // Casete: la letra va en una hoja crema rayada (renglones verde petróleo al 18 %), como la lista de canciones
    // escrita a mano en una carátula.
    Column(modifier.then(if (sheet) Modifier.drawBehind {
        val step = 34.dp.toPx()
        var y = step
        while (y < size.height) { drawLine(c.accent.copy(alpha = .18f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx()); y += step }
    } else Modifier)) {
        if (sheet) BasicText(
            "Lado A · letra", style = Ui.type.h2.copy(fontSize = 14.sp, color = Color(0xFFD9542B)),
            modifier = Modifier.padding(bottom = 6.dp).graphicsLayer { rotationZ = -2f },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (synced) {
                val off = state.lyricsOffsetMs / 1000f
                app.aurora.desktop.Stepper("−", "+", (if (off > 0) "+" else if (off < 0) "−" else "") + kotlin.math.abs(off).toString().replace('.', ',') + " s",
                    "Atrasar letra medio segundo", "Adelantar letra medio segundo",
                    { state.adjustLyricsOffset(track, -500) }, { state.adjustLyricsOffset(track, 500) })
            }
            app.aurora.desktop.Stepper("A−", "A+", "${(scale * 100).toInt()} %", "Letra más pequeña", "Letra más grande",
                { scale = (scale - .1f).coerceAtLeast(.7f) }, { scale = (scale + .1f).coerceAtMost(1.6f) })
        }
        LyricsView(
            l, playback, player::seekTo, Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp),
            onRetry = { state.loadLyrics(track, force = true) },
            onSave = track.takeIf { it.filePath != null }?.let { t -> { state.saveLyricsFile(t) } },
            offsetMs = state.lyricsOffsetMs, textScale = scale,
        )
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(AuroraIcon.Previous, "Anterior", player::previous, key = true)
            PlayButton(playback.isPlaying, 46.dp, player::togglePlay)
            IconButton(AuroraIcon.Next, "Siguiente", player::next, key = true)
            ProgressBar(playback.positionSec, duration, player::seekTo, Modifier.weight(1f).padding(start = 8.dp))
        }
    }
}

/** Ecualizador rápido (curva y presets) y temporizador "Apagar en". */
@Composable
private fun SheetEqAndTimer(state: app.aurora.AppState) {
    val prefs by state.prefsRepo.prefs.collectAsState()
    val eq = prefs.eq
    app.aurora.desktop.MiniCard("Ecualizador") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            app.aurora.components.Switch(eq.enabled, "Activar ecualizador", { on -> state.prefsRepo.update { it.copy(eq = it.eq.copy(enabled = on)) } })
            Spacer(Modifier.width(10.dp))
            app.aurora.components.EqSpark(eq.bands)
        }
        Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            app.aurora.audio.Eq.presets.take(10).forEachIndexed { i, p ->
                app.aurora.components.Chip(p.name, eq.enabled && !eq.isCustom && eq.preset == i, {
                    state.prefsRepo.update { it.copy(eq = it.eq.withPreset(i).copy(enabled = true)) }
                })
            }
        }
    }
    val s = state.sleep
    app.aurora.desktop.MiniCard("Apagar en", action = s?.let { "Cancelar" to { state.setSleep(0) } }) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(15 to "15 min", 30 to "30 min", 60 to "1 h", -1 to "Al terminar").forEach { (m, l) ->
                val on = s != null && s.label == (if (m < 0) "Al terminar la canción" else if (m >= 60) "1 h" else "$m min")
                app.aurora.components.Chip(l, on, { state.setSleep(if (on) 0 else m) }, icon = if (m < 0) AuroraIcon.Timer else null)
            }
        }
        if (s != null) BasicText("Se pausará: ${s.label}", style = Ui.type.caption, modifier = Modifier.padding(top = 6.dp))
    }
}
