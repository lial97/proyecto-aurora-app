package app.aurora.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aurora.components.AuroraIcon
import app.aurora.components.Icon
import app.aurora.components.ScreenTitle
import app.aurora.components.SongRow
import app.aurora.components.surface
import app.aurora.domain.Track
import app.aurora.components.typingField
import app.aurora.theme.Ui

/** Busca en título, artista, álbum y género (audio y video). */
fun searchTracks(tracks: List<Track>, query: String): List<Track> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return emptyList()
    return tracks.filter { t -> listOfNotNull(t.title, t.artist, t.album, t.genre).any { q in it.lowercase() } }
}

@Composable
fun SearchField(query: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, fieldModifier: Modifier = Modifier) {
    Row(modifier.surface(Ui.shapes.chip).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(AuroraIcon.Search, Ui.colors.mute, size = 18.dp)
        BasicTextField(
            query, onChange, singleLine = true, textStyle = Ui.type.body,
            cursorBrush = SolidColor(Ui.colors.accent),
            modifier = Modifier.padding(start = 10.dp).fillMaxWidth().then(fieldModifier).typingField(),
            decorationBox = { inner ->
                if (query.isEmpty()) BasicText("Busca canciones, artistas o videos", style = Ui.type.body.copy(color = Ui.colors.mute))
                inner()
            },
        )
    }
}

@Composable
fun SearchScreen(tracks: List<Track>, bottomPadding: Dp, onPlay: (List<Track>, Int) -> Unit, onMore: (Track) -> Unit = {}) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(tracks, query) { searchTracks(tracks, query) }
    LazyColumn(Modifier.statusBarsPadding(), contentPadding = mobilePadding(bottomPadding)) {
        item {
            ScreenTitle("Buscar")
            SearchField(query, { query = it }, Modifier.padding(vertical = 14.dp).fillMaxWidth())
            if (query.isNotBlank() && results.isEmpty()) BasicText("Sin resultados para \"$query\"", style = Ui.type.rowSubtitle)
        }
        items(results, key = { it.id }) { t -> SongRow(t, { onPlay(results, results.indexOf(t)) }, onMore = { onMore(t) }) }
    }
}
