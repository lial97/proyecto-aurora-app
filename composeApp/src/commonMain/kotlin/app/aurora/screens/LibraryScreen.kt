package app.aurora.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import app.aurora.components.CoverCard
import app.aurora.components.VideoThumb
import app.aurora.components.formatTime
import app.aurora.components.surface
import app.aurora.components.trackContextMenu
import app.aurora.domain.LibrarySort
import app.aurora.domain.groupFor
import app.aurora.domain.sortedFor
import app.aurora.domain.videos
import app.aurora.theme.label
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aurora.components.Artwork
import app.aurora.components.AuroraIcon
import app.aurora.components.Chip
import app.aurora.components.Icon
import app.aurora.components.IconButton
import app.aurora.components.LibraryBanner
import app.aurora.components.MixSwatch
import app.aurora.components.ScreenTitle
import app.aurora.components.SongRow
import app.aurora.components.pressable
import app.aurora.data.LibraryState
import app.aurora.domain.Playlist
import app.aurora.domain.SortOrder
import app.aurora.domain.Track
import app.aurora.domain.TrackGroup
import app.aurora.domain.albums
import app.aurora.domain.artists
import app.aurora.domain.genres
import app.aurora.domain.songs
import app.aurora.domain.sortedBy
import app.aurora.player.PlaybackState
import app.aurora.theme.Ui

enum class LibrarySection(val label: String) {
    Songs("Canciones"), Albums("Álbumes"), Artists("Artistas"), Genres("Géneros"), Playlists("Listas"),
}

/** Agrupa la biblioteca según la sección. */
fun groupsFor(tracks: List<Track>, section: LibrarySection, order: SortOrder): List<TrackGroup> = when (section) {
    LibrarySection.Albums -> tracks.albums(order)
    LibrarySection.Artists -> tracks.artists(order)
    LibrarySection.Genres -> tracks.genres(order)
    else -> emptyList()
}

fun groupDetail(g: TrackGroup, section: LibrarySection) = buildString {
    append("${g.tracks.size} ${if (g.tracks.size == 1) "canción" else "canciones"}")
    g.year?.let { append(" · $it") }
    if (section == LibrarySection.Albums) append(" · ${g.tracks.first().artist}")
}

/** Categorías de la biblioteca móvil. */
enum class MobileCategory(val label: String) { SONGS("Canciones"), ALBUMS("Álbumes"), ARTISTS("Artistas"), PLAYLISTS("Listas"), VIDEOS("Videos") }

/**
 * Biblioteca móvil, ordenada para una mano: chips de categoría, menú "Ordenar" (7 criterios y dirección),
 * canciones agrupadas con índice lateral para saltar, vista de lista o cuadrícula.
 */
@Composable
fun LibraryScreen(
    lib: LibraryState,
    playlists: List<Playlist>,
    playback: PlaybackState,
    bottomPadding: Dp,
    onPlay: (List<Track>, Int) -> Unit,
    onOpenPlaylist: (Playlist) -> Unit,
    onOpenSettings: () -> Unit,
    onMore: (Track) -> Unit = {},
    onNewPlaylist: () -> Unit = {},
    onSearch: () -> Unit = {},
    plays: (Track) -> Int = { 0 },
) {
    val c = Ui.colors
    var category by rememberSaveable { mutableStateOf(MobileCategory.SONGS) }
    var sort by rememberSaveable { mutableStateOf(LibrarySort.TITLE) }
    var reversed by rememberSaveable { mutableStateOf(false) }
    var grid by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    // Álbum o artista abierto (por nombre, para sobrevivir a girar o a que Android cierre la app).
    var openName by rememberSaveable { mutableStateOf<String?>(null) }
    val now = remember { kotlin.time.Clock.System.now().toEpochMilliseconds() }

    val songs = remember(lib.tracks, sort, reversed) { lib.tracks.songs().sortedFor(sort, reversed, plays) }
    val videos = remember(lib.tracks) { lib.tracks.videos() }
    val albums = remember(lib.tracks) { lib.tracks.albums() }
    val artists = remember(lib.tracks) { lib.tracks.artists() }
    // Filas: encabezados de grupo y canciones (para poder saltar al grupo desde el índice).
    val rows = remember(songs, sort) {
        buildList<Any> {
            var last: String? = null
            songs.forEach { t -> val g = t.groupFor(sort, now); if (g != null && g != last) { add(g); last = g }; add(t) }
        }
    }
    val groups = rows.filterIsInstance<String>()
    val openGroup: TrackGroup? = openName?.let { n -> (if (category == MobileCategory.ALBUMS) albums else artists).firstOrNull { it.name == n } }
    // Cada categoría recuerda su propia posición; el álbum o artista abierto, la suya.
    val songsState = rememberLazyListState()
    val albumsState = rememberLazyListState()
    val artistsState = rememberLazyListState()
    val playlistsState = rememberLazyListState()
    val videosState = rememberLazyListState()
    val groupState = rememberSaveable(openName, saver = androidx.compose.foundation.lazy.LazyListState.Saver) { androidx.compose.foundation.lazy.LazyListState() }
    val listState = when {
        openGroup != null -> groupState
        category == MobileCategory.SONGS -> songsState
        category == MobileCategory.ALBUMS -> albumsState
        category == MobileCategory.ARTISTS -> artistsState
        category == MobileCategory.PLAYLISTS -> playlistsState
        else -> videosState
    }
    val scope = rememberCoroutineScope()
    // El índice lateral deja el grupo justo debajo de las categorías fijas.
    val chipsPx = with(androidx.compose.ui.platform.LocalDensity.current) { 60.dp.roundToPx() }
    // Cambiar el orden (o la dirección) sí vuelve arriba; volver a la pantalla no.
    var sortSeen by rememberSaveable { mutableStateOf("${sort.name}/$reversed") }
    LaunchedEffect(sort, reversed) {
        val now = "${sort.name}/$reversed"
        if (now != sortSeen) { sortSeen = now; songsState.scrollToItem(0) }
    }
    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    androidx.compose.ui.backhandler.BackHandler(enabled = openName != null) { openName = null }

    val counts = mapOf(
        MobileCategory.SONGS to songs.size, MobileCategory.ALBUMS to albums.size, MobileCategory.ARTISTS to artists.size,
        MobileCategory.PLAYLISTS to playlists.size, MobileCategory.VIDEOS to videos.size,
    )
    val chips: @Composable (Modifier) -> Unit = { m ->
        Row(m.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 12.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MobileCategory.entries.forEach { cat -> Chip("${cat.label}  ${counts[cat]}", category == cat, { category = cat; openName = null }) }
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Una lista por categoría (y por álbum o artista): así cada una conserva su posición sin mezclarse.
        // Cada lista lee una copia fija de su categoría: si leyera la actual, al cambiar de categoría
        // la lista vieja se mediría una vez con los datos nuevos y perdería su posición.
        val shown = category
        val shownGroup = openGroup
        androidx.compose.runtime.key(openName ?: shown.name) {
        LazyColumn(Modifier.statusBarsPadding(), state = listState, contentPadding = mobilePadding(bottomPadding)) {
            item {
                val g = shownGroup
                if (g != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(AuroraIcon.Back, "Volver", { openName = null })
                        Column(Modifier.padding(start = 6.dp).weight(1f)) {
                            ScreenTitle(g.name, style = Ui.type.h1.copy(fontSize = Ui.type.h2.fontSize * 1.4f))
                            BasicText(groupDetail(g, if (shown == MobileCategory.ALBUMS) LibrarySection.Albums else LibrarySection.Artists), style = Ui.type.rowSubtitle)
                        }
                    }
                    PlayAllRow({ onPlay(g.tracks, 0) }, { onPlay(g.tracks.shuffled(), 0) })
                    return@item
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ScreenTitle("Biblioteca", Modifier.weight(1f))
                    IconButton(AuroraIcon.Search, "Buscar en la biblioteca", onSearch)
                    IconButton(AuroraIcon.Plus, "Nueva lista", onNewPlaylist)
                }
                LibraryBanner(lib, onOpenSettings)
            }
            if (shownGroup == null) item(key = "categorias") { chips(Modifier) }
            if (shownGroup == null) item(key = "herramientas") {
                if (shown == MobileCategory.SONGS) {
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            Row(
                                Modifier.pressable("Ordenar por ${sort.label}", .97f) { menuOpen = true }.surface(Ui.shapes.chip)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(AuroraIcon.Sort, c.mute, size = 16.dp)
                                BasicText("Ordenar: ", style = Ui.type.label.copy(color = c.mute), modifier = Modifier.padding(start = 6.dp))
                                BasicText(sort.label, style = Ui.type.label.copy(fontWeight = FontWeight.Bold))
                                Icon(AuroraIcon.Down, c.mute, size = 14.dp, modifier = Modifier.padding(start = 4.dp))
                            }
                            if (menuOpen) SortMenu(sort, reversed, { sort = it }, { reversed = it }, { menuOpen = false })
                        }
                        IconButton(if (reversed) AuroraIcon.ArrowUp else AuroraIcon.ArrowDown, "Cambiar dirección: ${if (reversed) sort.ascLabel else sort.descLabel}", { reversed = !reversed })
                        Spacer(Modifier.weight(1f))
                        IconButton(if (grid) AuroraIcon.Queue else AuroraIcon.Panel, if (grid) "Ver como lista" else "Ver como cuadrícula", { grid = !grid })
                    }
                    PlayAllRow({ onPlay(songs, 0) }, { onPlay(songs.shuffled(), 0) })
                }
            }
            val g = shownGroup
            when {
                g != null -> items(g.tracks, key = { it.id }) { t ->
                    SongRow(t, { onPlay(g.tracks, g.tracks.indexOf(t)) }, isCurrent = playback.current?.id == t.id, playing = playback.isPlaying,
                        subtitle = if (shown == MobileCategory.ALBUMS) t.artist else t.album, onMore = { onMore(t) })
                }
                shown == MobileCategory.SONGS && grid -> items(songs.chunked(2)) { pair ->
                    Row(Modifier.padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { t ->
                            BoxWithConstraints(Modifier.weight(1f)) {
                                CoverCard(t, t.title, t.artist, { onPlay(songs, songs.indexOf(t)) }, size = maxWidth, menuTrack = t)
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                shown == MobileCategory.SONGS -> items(rows, key = { r -> if (r is Track) r.id else "g:$r" }) { r ->
                    if (r is String) {
                        BasicText(r, style = Ui.type.h2.copy(fontSize = 15.sp, color = c.accent), modifier = Modifier.padding(top = 14.dp, bottom = 4.dp, start = 6.dp))
                    } else {
                        val t = r as Track
                        SongRow(
                            t, { onPlay(songs, songs.indexOf(t)) }, isCurrent = playback.current?.id == t.id, playing = playback.isPlaying,
                            subtitle = t.artist, trailing = metaFor(t, sort, plays), onMore = { onMore(t) },
                            modifier = Modifier.padding(end = if (groups.size > 3) 18.dp else 0.dp),
                        )
                    }
                }
                shown == MobileCategory.ALBUMS -> items(albums.chunked(2)) { pair ->
                    Row(Modifier.padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { a ->
                            BoxWithConstraints(Modifier.weight(1f)) {
                                CoverCard(a.tracks.first(), a.name, "${a.tracks.first().artist}${a.year?.let { " · $it" } ?: ""}", { openName = a.name }, size = maxWidth)
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                shown == MobileCategory.ARTISTS -> items(artists, key = { it.name }) { a ->
                    GroupRow(a.name, "${a.tracks.size} ${if (a.tracks.size == 1) "canción" else "canciones"}", a.tracks.first(), round = true) { openName = a.name }
                }
                shown == MobileCategory.PLAYLISTS -> items(playlists, key = { it.id }) { p ->
                    GroupRow(p.name, "${if (p.isUser) "Tu lista · " else ""}${p.trackIds.size} canciones", null, p.colors) { onOpenPlaylist(p) }
                }
                else -> items(videos, key = { it.id }) { v ->
                    Row(
                        Modifier.fillMaxWidth().trackContextMenu(v).pressable(v.title, .98f, hoverBg = true) { onPlay(videos, videos.indexOf(v)) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.width(132.dp)) { VideoThumb(v) }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            BasicText(v.title, style = Ui.type.rowTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            BasicText(v.artist, style = Ui.type.rowSubtitle, maxLines = 1)
                        }
                        IconButton(AuroraIcon.More, "Opciones de ${v.title}", { onMore(v) }, tint = c.mute, size = 32.dp, iconSize = 18.dp)
                    }
                }
            }
        }
        }
        // Al bajar, las categorías quedan fijas arriba (fuera de la lista: tocarlas no la mueve).
        val pinned by remember(listState) { androidx.compose.runtime.derivedStateOf { listState.firstVisibleItemIndex >= 2 } }
        if (openGroup == null && pinned) {
            Box(Modifier.fillMaxWidth().background(c.background).statusBarsPadding().padding(horizontal = app.aurora.theme.AuroraDimens.ScreenPadding)) { chips(Modifier) }
        }
        // Índice lateral: salta al grupo (A, B, C… o años).
        if (openGroup == null && category == MobileCategory.SONGS && !grid && groups.size > 3 && sort in setOf(LibrarySort.TITLE, LibrarySort.YEAR, LibrarySort.ARTIST)) {
            Column(
                Modifier.align(Alignment.CenterEnd).padding(end = 2.dp, top = 120.dp, bottom = bottomPadding).statusBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(1.dp), horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                groups.forEach { gname ->
                    val short = when (sort) { LibrarySort.YEAR -> "'" + gname.takeLast(2); else -> gname.take(1) }
                    BasicText(
                        short, style = Ui.type.caption.copy(fontSize = 10.5.sp, color = c.accent, fontWeight = FontWeight.Bold),
                        modifier = Modifier.pressable("Ir a $gname", .9f) {
                            val idx = rows.indexOf(gname)
                            if (idx >= 0) scope.launch { listState.animateScrollToItem(idx + HEADER_ITEMS, -chipsPx) }
                        }.padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

/** Elementos antes de las canciones: título, categorías y herramientas. */
private const val HEADER_ITEMS = 3

/** Dato a la derecha de cada fila según el orden (duración, año, veces o pista). */
private fun metaFor(t: Track, sort: LibrarySort, plays: (Track) -> Int): String? = when (sort) {
    LibrarySort.YEAR -> t.year?.toString() ?: "—"
    LibrarySort.MOST_PLAYED -> "${plays(t)} ${if (plays(t) == 1) "vez" else "veces"}"
    LibrarySort.ALBUM -> t.trackNumber?.let { "Pista $it" } ?: formatTime(t.durationSec)
    else -> formatTime(t.durationSec).takeIf { t.durationSec > 0 }
}

@Composable
private fun PlayAllRow(onPlay: () -> Unit, onShuffle: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f)) { WideButton("Reproducir", AuroraIcon.Play, true, onPlay) }
        Box(Modifier.weight(1f)) { WideButton("Aleatorio", AuroraIcon.Shuffle, false, onShuffle) }
    }
}

@Composable
private fun WideButton(label: String, icon: AuroraIcon, primary: Boolean, onClick: () -> Unit) {
    val c = Ui.colors
    Row(
        Modifier.fillMaxWidth().pressable(label, .97f, onClick = onClick).clip(Ui.shapes.chip)
            .background(if (primary) c.playBg else c.surface).border(1.dp, if (primary) c.playBg else c.surfaceBorder, Ui.shapes.chip)
            .padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, if (primary) c.playInk else c.ink, size = 18.dp)
        BasicText(Ui.theme.label(label), style = Ui.type.label.copy(color = if (primary) c.playInk else c.ink, fontWeight = FontWeight.Bold), modifier = Modifier.padding(start = 8.dp))
    }
}

/** Menú desplegable "Ordenar por" con la dirección abajo. Esc o tocar fuera lo cierra. */
@Composable
private fun SortMenu(sort: LibrarySort, reversed: Boolean, onSort: (LibrarySort) -> Unit, onReverse: (Boolean) -> Unit, onDismiss: () -> Unit) {
    val c = Ui.colors
    androidx.compose.ui.window.Popup(
        alignment = Alignment.TopStart, offset = androidx.compose.ui.unit.IntOffset(0, 110),
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.PopupProperties(focusable = true),
    ) {
        Column(
            Modifier.width(250.dp).shadow(20.dp, Ui.shapes.card).background(c.background, Ui.shapes.card).background(c.raised, Ui.shapes.card)
                .border(1.dp, c.surfaceBorder, Ui.shapes.card).padding(vertical = 8.dp),
        ) {
            BasicText(Ui.theme.label("Ordenar por").uppercase(), style = Ui.type.caption.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp), modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            LibrarySort.entries.forEach { o ->
                val on = o == sort
                Row(
                    Modifier.fillMaxWidth().pressable(o.label, .98f, hoverBg = true) { onSort(o); onDismiss() }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(o.label, style = Ui.type.body.copy(color = if (on) c.accent else c.ink, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal), modifier = Modifier.weight(1f))
                    if (on) Icon(AuroraIcon.Check, c.accent, size = 16.dp)
                }
            }
            Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).height(1.dp).background(c.ink.copy(alpha = .1f)))
            Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(sort.ascLabel, !reversed, { onReverse(false) })
                Chip(sort.descLabel, reversed, { onReverse(true) })
            }
        }
    }
}

@Composable
private fun GroupRow(title: String, detail: String, cover: Track?, colors: List<Long> = emptyList(), round: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressable(title, .98f, hoverBg = true, onClick = onClick).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            cover != null && round -> Artwork(cover, 52.dp, shape = androidx.compose.foundation.shape.RoundedCornerShape(50))
            cover != null || colors.isEmpty() -> Artwork(cover, 52.dp)
            else -> MixSwatch(colors, 52.dp)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            BasicText(title, style = Ui.type.rowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(detail, style = Ui.type.rowSubtitle, maxLines = 1)
        }
        Icon(AuroraIcon.Forward, Ui.colors.mute, size = 16.dp)
    }
}
