package app.aurora.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aurora.AppDialog
import app.aurora.AppState
import app.aurora.components.VideoSurface
import app.aurora.components.trackContextMenu
import androidx.compose.runtime.LaunchedEffect
import app.aurora.components.Artwork
import app.aurora.components.AuroraIcon
import app.aurora.components.Chip
import app.aurora.components.CoverCard
import app.aurora.components.EmptyState
import app.aurora.components.Equalizer
import app.aurora.components.Hero
import app.aurora.components.Icon
import app.aurora.components.IconButton
import app.aurora.components.LibraryBanner
import app.aurora.components.MixSwatch
import app.aurora.components.PlayButton
import app.aurora.components.ScreenTitle
import app.aurora.components.SectionTitle
import app.aurora.components.TechBadges
import app.aurora.components.VideoCard
import app.aurora.components.formatTime
import app.aurora.components.pressable
import app.aurora.components.surface
import app.aurora.data.LibraryState
import app.aurora.domain.Playlist
import app.aurora.domain.SortOrder
import app.aurora.domain.Track
import app.aurora.domain.TrackGroup
import app.aurora.domain.songs
import app.aurora.domain.sortedBy
import app.aurora.domain.videos
import app.aurora.player.PlaybackState
import app.aurora.screens.LibrarySection
import app.aurora.screens.LyricsView
import app.aurora.screens.greeting
import app.aurora.screens.groupDetail
import app.aurora.screens.groupsFor
import app.aurora.screens.heroScale
import app.aurora.screens.searchTracks
import app.aurora.screens.todayLabel
import app.aurora.theme.DesktopDimens
import app.aurora.theme.ThemeId
import app.aurora.theme.Ui
import app.aurora.theme.label
import app.aurora.theme.title

private val Gap = 16.dp

/** Columnas opcionales de la tabla de canciones. */
enum class Col(val label: String, val width: Dp) {
    ALBUM("Álbum", 0.dp), GENRE("Género", 130.dp), TEMPO("Tempo", 80.dp), YEAR("Año", 60.dp), DURATION("Duración", 70.dp),
}

private fun Col.text(t: Track) = when (this) {
    Col.ALBUM -> t.album
    Col.GENRE -> t.genre ?: "—"
    Col.TEMPO -> t.bpm?.let { "$it BPM" } ?: "—"
    Col.YEAR -> t.year?.toString() ?: "—"
    Col.DURATION -> if (t.durationSec > 0) formatTime(t.durationSec) else "—"
}

/** Tabla de canciones: #, Título (portada + nombre + artista) y columnas elegidas. */
fun LazyListScope.trackTable(
    tracks: List<Track>, cols: List<Col>, playback: PlaybackState,
    onMore: ((Track) -> Unit)? = null, playlist: Playlist? = null, onPlay: (Int) -> Unit,
) {
    item { TableHeader(cols, onMore != null) }
    items(tracks.size, key = { "${tracks[it].id}#$it" }) { i -> TrackTableRow(tracks[i], i + 1, cols, playback, onMore, playlist) { onPlay(i) } }
}

@Composable
private fun TableHeader(cols: List<Col>, more: Boolean) {
    val theme = Ui.theme
    val style = Ui.type.caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = if (theme.upperLabels) 1.sp else 0.sp)
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText("#", style = style, modifier = Modifier.width(34.dp))
            BasicText(theme.label("Título"), style = style, modifier = Modifier.weight(1.4f))
            cols.forEach { c ->
                BasicText(theme.label(c.label), style = style, modifier = if (c.width == 0.dp) Modifier.weight(1f) else Modifier.width(c.width))
            }
            if (more) Spacer(Modifier.width(32.dp))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Ui.colors.ink.copy(alpha = .1f)))
    }
}

@Composable
private fun TrackTableRow(t: Track, n: Int, cols: List<Col>, playback: PlaybackState, onMore: ((Track) -> Unit)?, playlist: Playlist?, onClick: () -> Unit) {
    val cur = playback.current?.id == t.id
    val mute = Ui.type.caption.copy(fontSize = 13.5.sp)
    Row(
        Modifier.fillMaxWidth().trackContextMenu(t, playlist).pressable(t.title, .995f, hoverBg = true, onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(34.dp)) {
            if (cur) Equalizer(playback.isPlaying)
            else BasicText("$n", style = if (Ui.theme.id == ThemeId.ESTADIO) Ui.type.h2.copy(fontSize = 20.sp, color = Ui.colors.mute) else mute)
        }
        Row(Modifier.weight(1.4f), verticalAlignment = Alignment.CenterVertically) {
            app.aurora.components.RowArt(t, 40.dp)
            Column(Modifier.padding(start = 12.dp)) {
                if (t.mediaType == app.aurora.domain.MediaType.VIDEO) app.aurora.components.VideoBadge(Modifier.padding(bottom = 2.dp))
                BasicText(t.title, style = Ui.type.rowTitle.copy(color = if (cur) Ui.colors.accent else Ui.colors.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicText(t.artist, style = Ui.type.rowSubtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        cols.forEach { c ->
            BasicText(c.text(t), style = mute, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = (if (c.width == 0.dp) Modifier.weight(1f) else Modifier.width(c.width)).padding(end = 8.dp))
        }
        if (onMore != null) IconButton(AuroraIcon.More, "Opciones de ${t.title}", { onMore(t) }, tint = Ui.colors.mute, size = 32.dp, iconSize = 18.dp)
    }
}

/** Cuadrícula de [cols] columnas que reparte el ancho disponible. */
@Composable
fun <T> GridRows(items: List<T>, cols: Int, gap: Dp = Gap, cell: @Composable (T, Dp) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val w = (maxWidth - gap * (cols - 1)) / cols
        Column(verticalArrangement = Arrangement.spacedBy(gap + 4.dp)) {
            items.chunked(cols).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { Box(Modifier.width(w)) { cell(it, w) } }
                }
            }
        }
    }
}

@Composable
fun DesktopHome(state: AppState, lib: LibraryState, playlists: List<Playlist>, playback: PlaybackState, nav: DesktopNav) {
    val songs = remember(lib.tracks) { lib.tracks.songs() }
    val recent = remember(songs) { songs.sortedBy(SortOrder.DATE_ADDED) }
    var chip by remember { mutableStateOf(0) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = desktopPadding()) {
        item {
            BasicText(todayLabel(), style = Ui.type.caption.copy(fontSize = 13.sp))
            val userName by state.settings.userName.collectAsState()
            app.aurora.components.GreetingTitle(greeting(userName), { state.dialog = AppDialog.EditName }, Modifier.fillMaxWidth(.75f))
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Los videos tienen su propia sección (Videos): Inicio es para la música.
                listOf("Todo", "Música", "Mezclas").forEachIndexed { k, c ->
                    Chip(c, k == chip, { chip = k; if (k == 1) nav.go(DView.Library) })
                }
            }
            LibraryBanner(lib, { nav.go(DView.Settings) }, Modifier.fillMaxWidth(.7f))
            app.aurora.components.DailyMixCard(state, { nav.go(DView.PlaylistView(it)) }, Modifier.padding(top = 18.dp).fillMaxWidth(.7f))
            SectionTitle("Sigue escuchando", trailing = "Ver todo")
            GridRows(recent.take(5), 5) { t, w ->
                CoverCard(t, t.title, t.artist, { state.play(recent, recent.indexOf(t), "Inicio") }, size = w, menuTrack = t)
            }
        }
        if (playlists.isNotEmpty()) item {
            SectionTitle("Tus mezclas")
            GridRows(playlists.take(4), 4) { p, _ ->
                Row(Modifier.pressable(p.name, .97f) { nav.go(DView.PlaylistView(p)) }.surface().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MixSwatch(p.colors, 44.dp)
                    BasicText(p.name, style = Ui.type.rowTitle.copy(fontWeight = FontWeight.Bold), maxLines = 2, modifier = Modifier.padding(start = 10.dp))
                }
            }
            SectionTitle("Escuchado hace poco")
        }
        trackTable(recent.take(6), listOf(Col.ALBUM, Col.DURATION), playback, { state.dialog = AppDialog.TrackMenu(it) }) { i -> state.play(recent, i, "Inicio") }
    }
}

@Composable
fun DesktopLibrary(state: AppState, lib: LibraryState, playlists: List<Playlist>, playback: PlaybackState, nav: DesktopNav) {
    var section by rememberSaveable { mutableStateOf(LibrarySection.Songs) }
    var order by rememberSaveable { mutableStateOf(SortOrder.TITLE) }
    var group by remember { mutableStateOf<TrackGroup?>(null) }
    val songs = remember(lib.tracks, order) { lib.tracks.songs().sortedBy(order) }
    val groups = remember(lib.tracks, section, order) { groupsFor(lib.tracks, section, order) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = desktopPadding()) {
        item {
            val g = group
            if (g != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(AuroraIcon.Back, "Volver", { group = null })
                    Artwork(g.tracks.first(), 64.dp, Modifier.padding(horizontal = 12.dp))
                    Column {
                        BasicText(Ui.theme.label(section.label.dropLast(if (section == LibrarySection.Albums) 2 else 1)), style = Ui.type.caption)
                        ScreenTitle(g.name, style = Ui.type.h1.copy(fontSize = Ui.type.h1.fontSize * .8f))
                        BasicText(groupDetail(g, section), style = Ui.type.rowSubtitle)
                    }
                    Spacer(Modifier.weight(1f))
                    PlayButton(playback.isPlaying && g.tracks.any { it.id == playback.current?.id }, 54.dp, { state.play(g.tracks, 0, g.name) })
                }
                Spacer(Modifier.height(16.dp))
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ScreenTitle("Biblioteca", Modifier.weight(1f))
                    BasicText("${lib.tracks.songs().size} canciones", style = Ui.type.rowSubtitle)
                }
                LibraryBanner(lib, { nav.go(DView.Settings) }, Modifier.fillMaxWidth(.7f))
                Row(Modifier.padding(top = 14.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    LibrarySection.entries.forEach { s -> Chip(s.label, section == s, { section = s }) }
                    if (section != LibrarySection.Playlists) {
                        Spacer(Modifier.width(16.dp))
                        Icon(AuroraIcon.Sort, Ui.colors.mute, size = 18.dp)
                        SortOrder.entries.forEach { o -> Chip(o.label, order == o, { order = o }) }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        val g = group
        when {
            g != null -> trackTable(g.tracks, listOf(Col.ALBUM, Col.YEAR, Col.DURATION), playback, { state.dialog = AppDialog.TrackMenu(it) }) { i -> state.play(g.tracks, i, g.name) }
            section == LibrarySection.Songs -> trackTable(songs, listOf(Col.ALBUM, Col.GENRE, Col.YEAR, Col.DURATION), playback, { state.dialog = AppDialog.TrackMenu(it) }) { i ->
                state.play(songs, i, "Tu biblioteca")
            }
            section == LibrarySection.Playlists -> item {
                Row(Modifier.padding(bottom = 16.dp)) { Chip("Nueva lista", true, { state.dialog = AppDialog.NewPlaylist() }, icon = AuroraIcon.Plus) }
                GridRows(playlists, 4) { p, w ->
                    Column(Modifier.pressable(p.name, .97f) { nav.go(DView.PlaylistView(p)) }) {
                        MixSwatch(p.colors, w)
                        BasicText(p.name, style = Ui.type.rowTitle, modifier = Modifier.padding(top = 8.dp), maxLines = 1)
                        BasicText("${p.trackIds.size} canciones", style = Ui.type.rowSubtitle)
                    }
                }
            }
            else -> item {
                if (groups.isEmpty()) EmptyState("Nada por aquí", "Añade carpetas con música en Ajustes.")
                GridRows(groups, 6) { gr, w -> CoverCard(gr.tracks.first(), gr.name, groupDetail(gr, section), { group = gr }, size = w) }
            }
        }
    }
}

@Composable
fun DesktopPlaylist(state: AppState, playlist: Playlist, tracks: List<Track>, playback: PlaybackState, onDeleted: () -> Unit) {
    val p = state.latest(playlist)
    if (p == null) { LaunchedEffect(Unit) { onDeleted() }; return }
    val minutes = tracks.sumOf { it.durationSec } / 60
    val playingThis = playback.isPlaying && tracks.any { it.id == playback.current?.id }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = desktopPadding()) {
        item {
            Row(Modifier.padding(top = 6.dp, bottom = 18.dp), verticalAlignment = Alignment.Bottom) {
                if (tracks.isEmpty()) MixSwatch(p.colors, 200.dp) else Hero(tracks.first(), 200.dp)
                Column(Modifier.padding(start = 26.dp)) {
                    BasicText(Ui.theme.label(if (p.isUser) "Tu lista" else "Mezcla"), style = Ui.type.caption.copy(fontSize = 13.sp))
                    ScreenTitle(p.name, style = Ui.type.h1.copy(fontSize = Ui.type.h1.fontSize * 1.3f, lineHeight = Ui.type.h1.lineHeight * 1.3f))
                    BasicText("${p.description?.let { "$it. " } ?: ""}${tracks.size} canciones, $minutes min.", style = Ui.type.rowSubtitle.copy(fontSize = 14.sp),
                        modifier = Modifier.padding(top = 8.dp))
                }
            }
            Row(Modifier.padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlayButton(playingThis, 54.dp, { if (playingThis) state.player.togglePlay() else state.play(tracks, 0, p.name) })
                IconButton(AuroraIcon.Shuffle, "Aleatorio", state.player::toggleShuffle, tint = if (playback.shuffle) Ui.colors.accent else Ui.colors.ink)
                if (p.isUser) {
                    IconButton(AuroraIcon.Edit, "Renombrar lista", { state.dialog = AppDialog.RenamePlaylist(p) })
                    IconButton(AuroraIcon.Trash, "Borrar lista", { state.dialog = AppDialog.DeletePlaylist(p) })
                } else {
                    IconButton(AuroraIcon.ListAdd, "Guardar como lista propia", { state.dialog = AppDialog.NewPlaylist(tracks) })
                }
            }
            if (tracks.isEmpty()) EmptyState("Esta lista está vacía", "Usa ⋯ en cualquier canción y elige \"Añadir a una lista\".")
        }
        trackTable(tracks, listOf(Col.ALBUM, Col.TEMPO, Col.DURATION), playback, { state.dialog = AppDialog.TrackMenu(it, p.takeIf { p.isUser }) }, p) { i ->
            state.play(tracks, i, p.name)
        }
    }
}

/** Modo cine: video a todo el ancho y, debajo, portada, título y acciones. */
@Composable
fun DesktopVideo(state: AppState, playback: PlaybackState, onShowSong: () -> Unit) {
    val t = playback.current
    if (t == null || t.mediaType != app.aurora.domain.MediaType.VIDEO) {
        EmptyState("Ningún video sonando", "Elige un video en la sección Videos.")
        return
    }
    val liked = t.id in state.liked
    Column(Modifier.fillMaxSize().padding(horizontal = DesktopDimens.ContentPadding).padding(bottom = 16.dp)) {
        app.aurora.components.SongVideoSwitch(true, true, onSong = onShowSong, onVideo = {}, modifier = Modifier.padding(bottom = 12.dp))
        BoxWithConstraints(Modifier.weight(1f, fill = false)) {
            val w = minOf(maxWidth, maxHeight * 16f / 9f)
            Box(Modifier.width(w)) { VideoSurface(playback, state.player, onFullscreen = { state.setFullscreen(true) }) }
        }
        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Artwork(t, 48.dp)
            Column(Modifier.weight(1f)) {
                BasicText(t.title, style = Ui.type.h2.copy(color = Ui.colors.ink), maxLines = 2, overflow = TextOverflow.Ellipsis)
                BasicText(t.artist, style = Ui.type.rowSubtitle)
            }
        }
        Row(Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(if (liked) "Te gusta" else "Me gusta", false, { state.toggleLike(t) }, icon = if (liked) AuroraIcon.HeartFilled else AuroraIcon.Heart)
            Chip("Añadir a una lista", false, { state.dialog = AppDialog.AddToPlaylist(listOf(t)) }, icon = AuroraIcon.ListAdd)
            Chip("Solo audio", !playback.videoEnabled, { state.player.setVideoEnabled(!playback.videoEnabled) }, icon = AuroraIcon.Music)
            Chip("Pantalla completa", false, { state.setFullscreen(true) }, icon = AuroraIcon.Expand)
        }
    }
}

@Composable
fun DesktopVideos(state: AppState, lib: LibraryState) {
    val videos = remember(lib.tracks) { lib.tracks.videos() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = desktopPadding()) {
        item {
            ScreenTitle("Videos")
            BasicText("${videos.size} videos", style = Ui.type.rowSubtitle, modifier = Modifier.padding(top = 4.dp, bottom = 18.dp))
            if (videos.isEmpty()) EmptyState("No hay videos", "Añade en Ajustes la carpeta donde guardas tus videos.")
            GridRows(videos, 3) { v, _ -> VideoCard(v, { state.play(videos, videos.indexOf(v), "Videos") }, onMore = { state.dialog = AppDialog.TrackMenu(v) }) }
        }
    }
}

@Composable
fun DesktopSearch(state: AppState, tracks: List<Track>, playback: PlaybackState, query: String) {
    val results = remember(tracks, query) { searchTracks(tracks, query) }
    val songs = results.songs()
    val videos = results.videos()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = desktopPadding()) {
        item {
            ScreenTitle("Buscar")
            BasicText(
                if (query.isBlank()) "Escribe arriba para buscar en tu biblioteca" else "${results.size} resultados para \"$query\"",
                style = Ui.type.rowSubtitle, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
            )
            if (videos.isNotEmpty()) {
                SectionTitle("Videos")
                GridRows(videos.take(6), 3) { v, _ -> VideoCard(v, { state.play(videos, videos.indexOf(v), "Búsqueda") }, onMore = { state.dialog = AppDialog.TrackMenu(v) }) }
            }
            if (songs.isNotEmpty()) SectionTitle("Canciones")
        }
        trackTable(songs, listOf(Col.ALBUM, Col.DURATION), playback, { state.dialog = AppDialog.TrackMenu(it) }) { i -> state.play(songs, i, "Búsqueda") }
    }
}
