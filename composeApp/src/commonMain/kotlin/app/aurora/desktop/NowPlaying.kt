package app.aurora.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.aurora.AppDialog
import app.aurora.AppState
import app.aurora.audio.Eq
import app.aurora.color.moodFromBpm
import app.aurora.components.Artwork
import app.aurora.components.AuroraIcon
import app.aurora.components.Chip
import app.aurora.components.EqSpark
import app.aurora.components.Equalizer
import app.aurora.components.Hero
import app.aurora.components.Icon
import app.aurora.components.IconButton
import app.aurora.components.RowArt
import app.aurora.components.Switch
import app.aurora.components.formatDate
import app.aurora.components.formatSize
import app.aurora.components.formatTime
import app.aurora.components.pressable
import app.aurora.components.surface
import app.aurora.components.trackContextMenu
import app.aurora.data.LyricsSource
import app.aurora.data.LyricsState
import app.aurora.domain.MediaType
import app.aurora.domain.Track
import app.aurora.domain.albums
import app.aurora.domain.songs
import app.aurora.player.PlaybackState
import app.aurora.screens.LyricsView
import app.aurora.screens.heroScale
import app.aurora.screens.lyricsStatus
import app.aurora.theme.ThemeId
import app.aurora.theme.Ui
import app.aurora.theme.label
import app.aurora.theme.title

/** "hace 3 min", "hace 2 días"… */
fun relativeTime(ms: Long?): String {
    ms ?: return "nunca"
    val d = (kotlin.time.Clock.System.now().toEpochMilliseconds() - ms) / 1000
    return when {
        d < 60 -> "ahora"
        d < 3600 -> "hace ${d / 60} min"
        d < 86_400 -> "hace ${d / 3600} h"
        d < 86_400 * 2 -> "ayer"
        d < 86_400 * 30 -> "hace ${d / 86_400} días"
        else -> formatDate(ms)
    }
}

private enum class RTab(val label: String) { QUEUE("Cola"), INFO("Info"), ARTIST("Artista") }

/**
 * Reproduciendo, sin espacio vacío: ficha de la canción a la izquierda, letra karaoke al centro y
 * Cola / Info / Artista a la derecha. Enfoque deja solo la letra.
 * Ventana ≥ 1440 dp: tres columnas; 1200-1439: el panel derecho pasa a un botón;
 * menos de 1200: la ficha se reduce a portada y título encima de la letra.
 */
@Composable
fun DesktopNowPlaying(state: AppState, playback: PlaybackState, tracks: List<Track>, onShowVideo: () -> Unit, onOpenSettings: () -> Unit) {
    val track = playback.current
    if (track == null) {
        app.aurora.components.EmptyState("Nada sonando", "Elige una canción para ver aquí su letra.")
        return
    }
    var tab by remember { mutableStateOf(RTab.QUEUE) }
    var lyricScale by remember { mutableFloatStateOf(1f) }
    var rightOpen by remember { mutableStateOf(false) }
    val focus = state.nowPlayingFocus
    BoxWithConstraints(Modifier.fillMaxSize().padding(start = 28.dp, end = 20.dp, bottom = 12.dp)) {
        // Este ancho ya descuenta el lateral (232) y los márgenes (48): ventana ≥ 1440 → tres columnas;
        // 1200-1439 → el panel derecho pasa a un botón; < 1200 → ficha reducida encima de la letra.
        val three = maxWidth >= 1140.dp
        val compact = maxWidth < 900.dp
        Row(Modifier.fillMaxSize()) {
            if (!focus && !compact) {
                SongCard(state, track, playback, Modifier.width(300.dp).fillMaxHeight(), onShowVideo, onOpenSettings) { tab = it; rightOpen = true }
                Spacer(Modifier.width(28.dp))
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                if (compact && !focus) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
                        Artwork(track, 72.dp, large = true)
                        Column(Modifier.padding(start = 14.dp)) {
                            BasicText(Ui.theme.title(track.title), style = Ui.type.panelTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            BasicText(track.artist, style = Ui.type.rowSubtitle)
                        }
                    }
                }
                LyricsToolbar(state, track, lyricScale, { lyricScale = it }, showPanelButton = !three && !focus, panelOpen = rightOpen) { rightOpen = !rightOpen }
                LyricsView(
                    state.lyrics, playback, state.player::seekTo, Modifier.weight(1f).fillMaxWidth(),
                    onRetry = { state.loadLyrics(track, force = true) },
                    offsetMs = state.lyricsOffsetMs, textScale = lyricScale * (if (focus) 1.15f else 1f), showHeader = false,
                )
            }
            if (!focus && three) {
                Spacer(Modifier.width(24.dp))
                RightTabs(state, playback, tracks, tab, { tab = it }, Modifier.width(330.dp).fillMaxHeight())
            }
        }
        // Ventanas medianas: el panel derecho se abre encima con su botón.
        if (!focus && !three && rightOpen) {
            RightTabs(
                state, playback, tracks, tab, { tab = it },
                Modifier.align(Alignment.CenterEnd).width(330.dp).fillMaxHeight().padding(vertical = 4.dp)
                    .shadow(24.dp, Ui.shapes.card).background(Ui.colors.background, Ui.shapes.card).padding(10.dp),
            )
        }
    }
}

/** Herramientas de la letra: origen, sincronía (±0,5 s), tamaño (A− A+), Enfoque y Guardar .lrc. */
@Composable
private fun LyricsToolbar(
    state: AppState, track: Track, scale: Float, onScale: (Float) -> Unit,
    showPanelButton: Boolean, panelOpen: Boolean, onPanel: () -> Unit,
) {
    val c = Ui.colors
    val l = state.lyrics
    val synced = l is LyricsState.Found && l.synced
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val src = when {
            l is LyricsState.Found -> if (l.source == LyricsSource.LRCLIB) "LRCLIB" else if (l.source == LyricsSource.FILE) "Archivo" else "Ejemplo"
            else -> null
        }
        if (src != null) Row(Modifier.surface(Ui.shapes.chip).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(if (synced) c.accent else c.mute, RoundedCornerShape(50)))
            BasicText(src + if (synced) "" else " · sin sincronizar", style = Ui.type.caption.copy(color = c.ink, fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(start = 6.dp))
        } else BasicText(lyricsStatus(l), style = Ui.type.caption, maxLines = 1)
        Spacer(Modifier.weight(1f))
        if (l is LyricsState.NotFound || (l is LyricsState.Found && !l.synced) || l == LyricsState.Disabled) {
            Chip("Buscar de nuevo", false, { state.loadLyrics(track, force = true) }, icon = AuroraIcon.Refresh)
        }
        if (synced) {
            val off = state.lyricsOffsetMs / 1000f
            Stepper("−", "+", (if (off > 0) "+" else if (off < 0) "−" else "") + (kotlin.math.abs(off)).toString().replace('.', ',') + " s",
                "Atrasar letra medio segundo", "Adelantar letra medio segundo",
                { state.adjustLyricsOffset(track, -500) }, { state.adjustLyricsOffset(track, 500) })
        }
        Stepper("A−", "A+", "${(scale * 100).toInt()} %", "Letra más pequeña", "Letra más grande",
            { onScale((scale - .1f).coerceAtLeast(.7f)) }, { onScale((scale + .1f).coerceAtMost(1.6f)) })
        Chip("Enfoque", state.nowPlayingFocus, { state.nowPlayingFocus = !state.nowPlayingFocus }, icon = if (state.nowPlayingFocus) AuroraIcon.Shrink else AuroraIcon.Expand)
        if (synced && (l as LyricsState.Found).source == LyricsSource.LRCLIB && track.filePath != null) {
            IconButton(AuroraIcon.Download, "Guardar archivo .lrc", { state.saveLyricsFile(track) })
        }
        if (showPanelButton) IconButton(AuroraIcon.Panel, "Cola, información y artista", onPanel, tint = if (panelOpen) c.accent else c.ink)
    }
}

@Composable
internal fun Stepper(minus: String, plus: String, value: String, minusLabel: String, plusLabel: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    val c = Ui.colors
    Row(Modifier.surface(Ui.shapes.chip), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.pressable(minusLabel, .92f, onClick = onMinus).padding(horizontal = 10.dp, vertical = 5.dp)) {
            BasicText(minus, style = Ui.type.body.copy(fontWeight = FontWeight.Bold))
        }
        BasicText(value, style = Ui.type.caption.copy(color = c.ink, fontFeatureSettings = "tnum"), modifier = Modifier.width(46.dp), maxLines = 1)
        Box(Modifier.pressable(plusLabel, .92f, onClick = onPlus).padding(horizontal = 10.dp, vertical = 5.dp)) {
            BasicText(plus, style = Ui.type.body.copy(fontWeight = FontWeight.Bold))
        }
    }
}

/** Ficha de la canción: portada, enlaces, acciones, datos, ecualizador y temporizador. */
@Composable
private fun SongCard(
    state: AppState, track: Track, playback: PlaybackState, modifier: Modifier,
    onShowVideo: () -> Unit, onOpenSettings: () -> Unit, onTab: (RTab) -> Unit,
) {
    val c = Ui.colors
    val theme = Ui.theme
    val liked = track.id in state.liked
    val prefs by state.prefsRepo.prefs.collectAsState()
    @Suppress("UNUSED_VARIABLE") val v = state.statsVersion
    val stats = state.stats.get(track.id)
    Column(modifier.verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth(), contentAlignment = if (theme.leftAlignedHero) Alignment.CenterStart else Alignment.Center) {
            Hero(track, when (theme.id) { ThemeId.PETALO, ThemeId.SEDA -> 220.dp; ThemeId.ESTADIO -> 236.dp; else -> 270.dp }, Modifier.trackContextMenu(track), scale = heroScale(playback.isPlaying))
        }
        BasicText(theme.title(track.title), style = Ui.type.panelTitle.copy(fontSize = Ui.type.panelTitle.fontSize * 1.15f), modifier = Modifier.padding(top = 16.dp), maxLines = 3, overflow = TextOverflow.Ellipsis)
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Link(track.artist) { onTab(RTab.ARTIST) }
            BasicText(" · ", style = Ui.type.rowSubtitle)
            Link(track.album) { onTab(RTab.INFO) }
            track.year?.let { BasicText(" · $it", style = Ui.type.rowSubtitle) }
        }
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(if (liked) "Te gusta" else "Me gusta", liked, { state.toggleLike(track) }, icon = if (liked) AuroraIcon.HeartFilled else AuroraIcon.Heart)
            Chip("Lista", false, { state.dialog = AppDialog.AddToPlaylist(listOf(track)) }, icon = AuroraIcon.ListAdd)
            if (playback.hasVideo) Chip("Video", false, onShowVideo, icon = AuroraIcon.Video)
            IconButton(AuroraIcon.Share, "Compartir", { state.show("Enlace copiado") })
        }
        MiniCard("Esta canción") {
            val mood = moodFromBpm(track.bpm)
            val tags = listOfNotNull(
                "${mood.label.text} · ${mood.bpm} BPM" to true,
                track.key?.let { it to false },
                track.format?.let { it to false },
                track.sampleRateHz?.let { "${(it / 100) / 10.0} kHz".replace(".0 ", " ").replace('.', ',') to false },
                track.bitsPerSample?.let { "$it bit" to false },
                track.bitrateKbps?.let { "$it kbps" to false },
            )
            FlowTags(tags)
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Stat("${stats.count}", if (stats.count == 1) "vez" else "veces")
                Stat(relativeTime(stats.lastMs), "última vez")
            }
        }
        MiniCard("Ecualizador", action = "Ajustes" to onOpenSettings) {
            val eq = prefs.eq
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(eq.enabled, "Activar ecualizador", { on -> state.prefsRepo.update { it.copy(eq = it.eq.copy(enabled = on)) } })
                Spacer(Modifier.width(10.dp))
                EqSpark(eq.bands)
            }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                val i = eq.preset
                IconButton(AuroraIcon.Back, "Preset anterior", { val n = if (i <= 0) Eq.presets.lastIndex else i - 1; state.prefsRepo.update { it.copy(eq = it.eq.withPreset(n).copy(enabled = true)) } }, size = 30.dp, iconSize = 16.dp)
                BasicText(if (eq.isCustom) "Personalizado" else Eq.presets[i].name, style = Ui.type.rowTitle, modifier = Modifier.weight(1f), maxLines = 1)
                IconButton(AuroraIcon.Forward, "Preset siguiente", { val n = if (i < 0 || i >= Eq.presets.lastIndex) 0 else i + 1; state.prefsRepo.update { it.copy(eq = it.eq.withPreset(n).copy(enabled = true)) } }, size = 30.dp, iconSize = 16.dp)
            }
        }
        MiniCard("Apagar en", action = state.sleep?.let { "Cancelar" to { state.setSleep(0) } }) {
            val s = state.sleep
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(listOf(15 to "15 min", 30 to "30 min", 60 to "1 h"), listOf(-1 to "Al terminar la canción")).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { (m, l) ->
                            val on = s != null && s.label == (if (m < 0) "Al terminar la canción" else if (m >= 60) "1 h" else "$m min")
                            Chip(l, on, { state.setSleep(if (on) 0 else m) }, icon = if (m < 0) AuroraIcon.Timer else null)
                        }
                    }
                }
            }
            if (s != null) BasicText("Se pausará: ${s.label}", style = Ui.type.caption, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun Link(text: String, onClick: () -> Unit) {
    BasicText(text, style = Ui.type.rowSubtitle.copy(color = Ui.colors.ink, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline),
        modifier = Modifier.pressable(text, .97f, onClick = onClick), maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
internal fun MiniCard(title: String, action: Pair<String, () -> Unit>? = null, content: @Composable () -> Unit) {
    Column(Modifier.padding(top = 14.dp).fillMaxWidth().surface().padding(14.dp)) {
        Row(Modifier.padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(Ui.theme.label(title).uppercase(), style = Ui.type.caption.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp), modifier = Modifier.weight(1f))
            if (action != null) BasicText(action.first, style = Ui.type.caption.copy(color = Ui.colors.accent, fontWeight = FontWeight.Bold),
                modifier = Modifier.pressable(action.first, .95f, onClick = action.second))
        }
        content()
    }
}

@Composable
private fun FlowTags(tags: List<Pair<String, Boolean>>) {
    val c = Ui.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        var row = mutableListOf<Pair<String, Boolean>>()
        val rows = mutableListOf<List<Pair<String, Boolean>>>()
        var len = 0
        tags.forEach { t -> if (len + t.first.length > 26 && row.isNotEmpty()) { rows += row; row = mutableListOf(); len = 0 }; row += t; len += t.first.length + 3 }
        if (row.isNotEmpty()) rows += row
        rows.forEach { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                r.forEach { (t, acc) ->
                    BasicText(t, style = Ui.type.caption.copy(color = if (acc) c.accent else c.ink, fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.border(1.dp, if (acc) c.accent else c.surfaceBorder, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column {
        BasicText(value, style = Ui.type.rowTitle.copy(fontSize = 17.sp))
        BasicText(label, style = Ui.type.caption)
    }
}

/** Panel derecho de Reproduciendo: Cola, Info y Artista. */
@Composable
private fun RightTabs(state: AppState, playback: PlaybackState, tracks: List<Track>, tab: RTab, onTab: (RTab) -> Unit, modifier: Modifier) {
    val c = Ui.colors
    Column(modifier) {
        Row(Modifier.fillMaxWidth().surface(Ui.shapes.chip).padding(3.dp)) {
            RTab.entries.forEach { t ->
                Box(
                    Modifier.weight(1f).pressable(t.label, .97f) { onTab(t) }.clip(Ui.shapes.chip)
                        .background(if (t == tab) c.chipOn else Color.Transparent).padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) { BasicText(Ui.theme.label(t.label), style = Ui.type.label.copy(color = if (t == tab) c.chipOnInk else c.ink, fontWeight = FontWeight.SemiBold)) }
            }
        }
        Spacer(Modifier.height(12.dp))
        when (tab) {
            RTab.QUEUE -> QueueTab(state, playback, Modifier.weight(1f))
            RTab.INFO -> InfoTab(state, playback.current!!, Modifier.weight(1f))
            RTab.ARTIST -> ArtistTab(state, playback, tracks, Modifier.weight(1f))
        }
    }
}

/**
 * Cola: la actual y "A continuación". Es una lista perezosa: con una cola de miles de canciones (reproducir desde
 * la biblioteca) solo se componen las filas visibles, así el panel abre al instante.
 */
@Composable
internal fun QueueTab(state: AppState, playback: PlaybackState, modifier: Modifier) {
    val c = Ui.colors
    val q = playback.queue
    val cur = playback.index
    val upcoming = (q.size - cur - 1).coerceAtLeast(0)
    val rowH = 56.dp
    var dragging by remember { mutableIntStateOf(-1) }
    var dy by remember { mutableFloatStateOf(0f) }
    LazyColumn(modifier) {
        item(key = "cabecera") {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                BasicText("Desde ", style = Ui.type.caption)
                BasicText(state.source, style = Ui.type.caption.copy(color = c.ink, fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f), maxLines = 1)
                if (upcoming > 0) BasicText("Limpiar", style = Ui.type.caption.copy(color = c.accent, fontWeight = FontWeight.Bold),
                    modifier = Modifier.pressable("Limpiar la cola", .95f) { state.player.clearUpcoming(); state.show("Cola limpiada") })
            }
        }
        playback.current?.let { t ->
            item(key = "actual") {
                Row(Modifier.fillMaxWidth().clip(Ui.shapes.card).background(c.ink.copy(alpha = .07f)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RowArt(t, 40.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        BasicText(t.title, style = Ui.type.rowTitle.copy(color = c.accent, fontSize = 13.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        BasicText(t.artist, style = Ui.type.caption, maxLines = 1)
                    }
                    Equalizer(playback.isPlaying)
                }
            }
        }
        item(key = "titulo") {
            Column {
                BasicText(Ui.theme.label("A continuación · $upcoming"), style = Ui.type.caption.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
                if (upcoming == 0) BasicText(
                    if (state.prefsRepo.prefs.value.endOfQueue == app.aurora.data.EndOfQueue.REPEAT) "La cola está vacía. Al terminar vuelve a empezar." else "La cola está vacía. Al terminar se detiene.",
                    style = Ui.type.rowSubtitle, modifier = Modifier.padding(6.dp),
                )
            }
        }
        items(upcoming) { k ->
            val abs = cur + 1 + k
            val t = q[abs]
            val isDrag = dragging == abs
            Row(
                Modifier.fillMaxWidth().height(rowH).zIndex(if (isDrag) 1f else 0f)
                    .graphicsLayer { translationY = if (isDrag) dy else 0f; shadowElevation = if (isDrag) 12f else 0f }
                    .trackContextMenu(t)
                    .pressable(t.title, .99f, hoverBg = true) { state.player.moveInQueue(abs, cur + 1); state.player.next() }
                    .background(if (isDrag) c.background else Color.Transparent, Ui.shapes.card)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Asa para arrastrar y reordenar.
                Box(
                    Modifier.size(24.dp, 40.dp).pointerInput(abs, cur, q.size) {
                        detectDragGestures(
                            onDragStart = { dragging = abs; dy = 0f },
                            onDragEnd = {
                                val shift = kotlin.math.round(dy / rowH.toPx()).toInt()
                                val to = (abs + shift).coerceIn(cur + 1, q.lastIndex)
                                if (to != abs) state.player.moveInQueue(abs, to)
                                dragging = -1; dy = 0f
                            },
                            onDragCancel = { dragging = -1; dy = 0f },
                        ) { ch, d -> ch.consume(); dy += d.y }
                    },
                    contentAlignment = Alignment.Center,
                ) { Icon(AuroraIcon.Grip, c.mute, size = 16.dp) }
                RowArt(t, 36.dp)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    BasicText(t.title, style = Ui.type.rowTitle.copy(fontSize = 13.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BasicText(t.artist, style = Ui.type.caption, maxLines = 1)
                }
                if (t.durationSec > 0) BasicText(formatTime(t.durationSec), style = Ui.type.caption)
                IconButton(AuroraIcon.Close, "Quitar de la cola", { state.player.removeFromQueue(abs) }, tint = c.mute, size = 28.dp, iconSize = 14.dp)
            }
        }
    }
}

@Composable
internal fun InfoTab(state: AppState, t: Track, modifier: Modifier) {
    @Suppress("UNUSED_VARIABLE") val v = state.statsVersion
    val stats = state.stats.get(t.id)
    val rows = listOfNotNull(
        "Álbum" to t.album,
        t.trackNumber?.let { "Pista" to (if (t.trackTotal != null) "$it de ${t.trackTotal}" else "$it") },
        t.year?.let { "Año" to "$it" },
        t.genre?.let { "Género" to it },
        "Duración" to formatTime(t.durationSec),
        t.bpm?.let { "Tempo" to ("$it BPM" + (t.key?.let { k -> " · $k" } ?: "")) },
        t.format?.let { f -> "Formato" to listOfNotNull(f, t.sampleRateHz?.let { "${it / 1000.0} kHz".replace(".0 ", " ").replace('.', ',') }, t.bitsPerSample?.let { "$it bit" }).joinToString(" · ") },
        t.bitrateKbps?.let { "Tasa" to "$it kbps" },
        t.fileSizeBytes?.let { "Tamaño" to formatSize(it) },
        t.filePath?.let { "Archivo" to it },
        if (t.dateAddedMs > 0) "Añadida" to formatDate(t.dateAddedMs) else null,
        "Reproducida" to "${stats.count} ${if (stats.count == 1) "vez" else "veces"}",
    )
    Column(modifier.verticalScroll(rememberScrollState())) {
        rows.forEach { (k, value) ->
            Row(Modifier.padding(vertical = 5.dp)) {
                BasicText(k, style = Ui.type.caption, modifier = Modifier.width(92.dp))
                SelectionContainer { BasicText(value, style = Ui.type.body.copy(fontSize = 13.5.sp, fontFamily = if (k == "Archivo") androidx.compose.ui.text.font.FontFamily.Monospace else null)) }
            }
        }
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (t.filePath != null && state.platform.isDesktop) Chip("Abrir carpeta", false, { state.revealInFolder(t) }, icon = AuroraIcon.Folder)
            Chip("Editar datos", false, { state.dialog = AppDialog.EditTrack(t) }, icon = AuroraIcon.Edit)
        }
    }
}

@Composable
private fun ArtistTab(state: AppState, playback: PlaybackState, tracks: List<Track>, modifier: Modifier) {
    val t = playback.current!!
    val c = Ui.colors
    val mine = remember(tracks, t.artist) { tracks.songs().filter { it.artist == t.artist } }
    val albums = remember(mine) { mine.albums() }
    Column(modifier.verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Artwork(t, 54.dp, shape = RoundedCornerShape(50))
            Column(Modifier.padding(start = 12.dp)) {
                BasicText(t.artist, style = Ui.type.rowTitle.copy(fontSize = 16.sp))
                BasicText("${mine.size} ${if (mine.size == 1) "canción" else "canciones"} en tu biblioteca", style = Ui.type.caption)
            }
        }
        Row(Modifier.padding(top = 12.dp)) {
            Chip("Mezcla de este artista", true, {
                if (mine.isNotEmpty()) state.play(mine.shuffled(), 0, "Mezcla de ${t.artist}")
            }, icon = AuroraIcon.Shuffle)
        }
        BasicText(Ui.theme.label("Canciones"), style = Ui.type.caption.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
        mine.forEach { x ->
            Row(
                Modifier.fillMaxWidth().trackContextMenu(x).pressable(x.title, .99f, hoverBg = true) { state.play(mine, mine.indexOf(x), t.artist) }.padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RowArt(x, 36.dp)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    BasicText(x.title, style = Ui.type.rowTitle.copy(fontSize = 13.5.sp, color = if (x.id == t.id) c.accent else c.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BasicText(x.album, style = Ui.type.caption, maxLines = 1)
                }
                BasicText(formatTime(x.durationSec), style = Ui.type.caption)
            }
        }
        if (albums.isNotEmpty()) {
            BasicText(Ui.theme.label("Álbumes"), style = Ui.type.caption.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
            albums.chunked(2).forEach { row ->
                Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { a ->
                        Column(Modifier.weight(1f).pressable(a.name, .97f) { state.play(a.tracks, 0, a.name) }) {
                            Artwork(a.tracks.first(), 140.dp)
                            BasicText(a.name, style = Ui.type.rowTitle.copy(fontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                            BasicText(a.year?.toString() ?: "${a.tracks.size} canciones", style = Ui.type.caption)
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

