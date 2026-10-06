package app.aurora.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aurora.components.Avatar
import app.aurora.components.Chip
import app.aurora.components.CoverCard
import app.aurora.components.LibraryBanner
import app.aurora.components.MixSwatch
import app.aurora.components.ScreenTitle
import app.aurora.components.SectionTitle
import app.aurora.components.SongRow
import app.aurora.components.VideoCard
import app.aurora.components.pressable
import app.aurora.components.surface
import app.aurora.data.LibraryState
import app.aurora.domain.Playlist
import app.aurora.domain.SortOrder
import app.aurora.domain.Track
import app.aurora.domain.songs
import app.aurora.domain.sortedBy
import app.aurora.domain.videos
import app.aurora.player.PlaybackState
import app.aurora.theme.AuroraDimens
import app.aurora.theme.Ui

@Composable
fun HomeScreen(
    lib: LibraryState,
    playlists: List<Playlist>,
    playback: PlaybackState,
    bottomPadding: Dp,
    onPlay: (List<Track>, Int) -> Unit,
    onOpenPlaylist: (Playlist) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenVideos: () -> Unit,
    onMore: (Track) -> Unit = {},
    userName: String = "",
    onEditName: () -> Unit = {},
    /** Última vez que sonó cada pista (ms), para "Sigue escuchando". */
    lastPlayed: (Track) -> Long? = { null },
) {
    val type = Ui.type
    val songs = remember(lib.tracks) { lib.tracks.songs() }
    val recent = remember(songs) { songs.sortedBy(SortOrder.DATE_ADDED) }
    val videos = remember(lib.tracks) { lib.tracks.videos() }
    val continueList = remember(songs, recent) {
        val played = songs.filter { lastPlayed(it) != null }.sortedByDescending { lastPlayed(it) }
        (played + recent.filter { it !in played }).take(12)
    }

    LazyColumn(Modifier.statusBarsPadding(), contentPadding = mobilePadding(bottomPadding)) {
        item {
            BasicText(todayLabel(), style = type.caption)
            app.aurora.components.GreetingTitle(greeting(userName), onEditName, Modifier.fillMaxWidth())
            LibraryBanner(lib, onOpenSettings)
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
        if (videos.isNotEmpty()) item {
            SectionTitle("Videos musicales", trailing = "Ver todo".takeIf { videos.size > 3 }, modifier = Modifier.pressable("Ver todos los videos", .98f, onClick = onOpenVideos))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(videos.take(10), key = { it.id }) { v -> VideoCard(v, { onPlay(videos, videos.indexOf(v)) }, Modifier.width(210.dp), onMore = { onMore(v) }) }
            }
        }
        item { SectionTitle("Añadidas hace poco") }
        items(recent.take(4), key = { it.id }) { t ->
            SongRow(t, onClick = { onPlay(recent, recent.indexOf(t)) }, isCurrent = playback.current?.id == t.id, playing = playback.isPlaying, onMore = { onMore(t) })
        }
    }
}

@Composable
private fun MixTile(p: Playlist, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.pressable(p.name, .97f, onClick = onClick).surface().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        MixSwatch(p.colors, 40.dp)
        BasicText(
            p.name, style = Ui.type.rowTitle.copy(fontWeight = FontWeight.Bold, fontSize = 13.5.sp), maxLines = 2,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}
