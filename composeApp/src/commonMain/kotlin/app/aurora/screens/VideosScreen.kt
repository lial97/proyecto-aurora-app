package app.aurora.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aurora.components.AuroraIcon
import app.aurora.components.EmptyState
import app.aurora.components.IconButton
import app.aurora.components.ScreenTitle
import app.aurora.components.VideoCard
import app.aurora.data.LibraryState
import app.aurora.domain.Track
import app.aurora.domain.videos
import app.aurora.theme.Ui

/** Sección de videos: separada de la música para no mezclar mp3 con mp4. */
@Composable
fun VideosScreen(lib: LibraryState, bottomPadding: Dp, onPlay: (List<Track>, Int) -> Unit, onOpenSettings: () -> Unit, onMore: (Track) -> Unit = {}) {
    val videos = remember(lib.tracks) { lib.tracks.videos() }
    LazyColumn(Modifier.statusBarsPadding(), contentPadding = mobilePadding(bottomPadding)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScreenTitle("Videos", Modifier.weight(1f))
                IconButton(AuroraIcon.Settings, "Ajustes y carpetas", onOpenSettings)
            }
            BasicText("${videos.size} videos", style = Ui.type.rowSubtitle, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
            if (videos.isEmpty()) EmptyState("No hay videos", "Añade en Ajustes la carpeta donde guardas tus videos.")
        }
        items(videos, key = { it.id }) { v ->
            VideoCard(v, { onPlay(videos, videos.indexOf(v)) }, Modifier.padding(bottom = 22.dp), onMore = { onMore(v) }, storyboard = true)
        }
    }
}
