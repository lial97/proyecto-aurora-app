package app.aurora.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aurora.components.Artwork
import app.aurora.components.AuroraIcon
import app.aurora.components.CoverCard
import app.aurora.components.Icon
import app.aurora.components.LibraryBanner
import app.aurora.components.MixSwatch
import app.aurora.components.ScreenTitle
import app.aurora.components.SectionTitle
import app.aurora.components.pressableLong
import app.aurora.data.LibraryState
import app.aurora.domain.MediaType
import app.aurora.domain.Playlist
import app.aurora.domain.SortOrder
import app.aurora.domain.Track
import app.aurora.domain.songs
import app.aurora.domain.sortedBy
import app.aurora.theme.AuroraDimens
import app.aurora.theme.Ui

@Composable
fun HomeScreen(
    lib: LibraryState,
    playlists: List<Playlist>,
    bottomPadding: Dp,
    onPlay: (List<Track>, Int) -> Unit,
    onOpenPlaylist: (Playlist) -> Unit,
    onOpenSettings: () -> Unit,
    onCreatePlaylist: () -> Unit = {},
    onShowAllLists: () -> Unit = {},
    onPlaylistMenu: (Playlist) -> Unit = {},
    userName: String = "",
    onEditName: () -> Unit = {},
    /** Última vez que sonó cada pista (ms), para "Sigue escuchando". */
    lastPlayed: (Track) -> Long? = { null },
    /** "Tu mezcla de hoy" (arriba, debajo del saludo). */
    dailyMix: @Composable () -> Unit = {},
) {
    val type = Ui.type
    val songs = remember(lib.tracks) { lib.tracks.songs() }
    val recent = remember(songs) { songs.sortedBy(SortOrder.DATE_ADDED) }
    val continueList = remember(songs, recent) {
        val played = songs.filter { lastPlayed(it) != null }.sortedByDescending { lastPlayed(it) }
        (played + recent.filter { it !in played }).take(12)
    }
    val byId = remember(lib.tracks) { lib.tracks.associateBy { it.id } }

    LazyColumn(Modifier.statusBarsPadding(), contentPadding = mobilePadding(bottomPadding)) {
        item {
            BasicText(todayLabel(), style = type.caption)
            app.aurora.components.GreetingTitle(greeting(userName), onEditName, Modifier.fillMaxWidth())
            LibraryBanner(lib, onOpenSettings)
            dailyMix()
            SectionTitle("Sigue escuchando")
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(continueList.size) { i ->
                    val t = continueList[i]
                    CoverCard(t, t.title, t.artist, { onPlay(continueList, i) }, Modifier.width(AuroraDimens.CoverCard), AuroraDimens.CoverCard, menuTrack = t)
                }
            }
        }
        item { SectionTitle("Tus listas", trailing = "Ver todas", onTrailingClick = onShowAllLists) }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                item(key = "crear-lista") {
                    CreatePlaylistCard(hasLists = playlists.isNotEmpty(), onClick = onCreatePlaylist)
                }
                items(playlists, key = { it.id }) { p ->
                    val list = remember(p, byId) { p.trackIds.mapNotNull(byId::get) }
                    PlaylistCard(
                        p, list,
                        onClick = { onOpenPlaylist(p) },
                        onLongClick = if (p.isUser) ({ onPlaylistMenu(p) }) else null,
                    )
                }
            }
        }
    }
}

/** Tarjeta fija "+ Crear lista": borde punteado del tema y altura idéntica al resto. */
@Composable
private fun CreatePlaylistCard(hasLists: Boolean, onClick: () -> Unit) {
    Column(Modifier.width(AuroraDimens.CoverCard).pressableLong("Crear lista", onClick)) {
        Box(
            Modifier.size(AuroraDimens.CoverCard)
                .clip(Ui.shapes.artSmall)
                .background(Ui.colors.surface)
                .dashedBorder(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AuroraIcon.Plus, Ui.colors.accent, size = 34.dp)
        }
        BasicText(
            "Crear lista", style = Ui.type.rowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        BasicText(
            if (hasLists) "Música y videos" else "Tu primera lista",
            style = Ui.type.rowSubtitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Tarjeta de una lista: carátula (collage), nombre en una línea y cuántas canciones o elementos tiene. */
@Composable
private fun PlaylistCard(
    playlist: Playlist,
    tracks: List<Track>,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    Column(Modifier.width(AuroraDimens.CoverCard).pressableLong(playlist.name, onClick, onLongClick)) {
        PlaylistCover(tracks, playlist.colors, AuroraDimens.CoverCard)
        BasicText(
            playlist.name, style = Ui.type.rowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        BasicText(countLabel(playlist, tracks), style = Ui.type.rowSubtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PlaylistCover(tracks: List<Track>, colors: List<Long>, size: Dp) {
    val c = Ui.colors
    Box(Modifier.size(size).clip(Ui.shapes.artSmall).background(c.surface).clearAndSetSemantics {}) {
        when {
            tracks.isEmpty() -> MixSwatch(colors, size)
            // Con menos de 4 portadas el collage quedaba con huecos: se usa la primera entera.
            tracks.size < 4 -> Artwork(tracks.first(), size, shape = RectangleShape, border = false)
            else -> {
                val cell = size / 2
                Column(Modifier.size(size)) {
                    repeat(2) { row ->
                        Row {
                            repeat(2) { col ->
                                val t = tracks.getOrNull(row * 2 + col)
                                if (t != null) Artwork(t, cell, shape = RectangleShape, border = false)
                                else Box(Modifier.size(cell).background(c.surface))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun countLabel(p: Playlist, tracks: List<Track>): String {
    // Lo que se puede mostrar (una lista puede guardar pistas que ya no están en la biblioteca).
    val n = tracks.size
    return if (tracks.any { it.mediaType == MediaType.VIDEO }) {
        "$n ${if (n == 1) "elemento" else "elementos"}"
    } else {
        "$n ${if (n == 1) "canción" else "canciones"}"
    }
}

/** Borde punteado con el color de borde del tema (sin colores fijos). */
@Composable
private fun Modifier.dashedBorder(): Modifier {
    val color = Ui.colors.surfaceBorder
    val width = 1.5.dp
    return drawBehind {
        drawRoundRect(
            color = color,
            cornerRadius = CornerRadius(10.dp.toPx()),
            style = Stroke(
                width = width.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 7.dp.toPx())),
            ),
        )
    }
}
