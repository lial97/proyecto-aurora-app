package app.aurora.screens

import androidx.compose.foundation.layout.Arrangement
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
import app.aurora.components.Chip
import app.aurora.components.Icon
import app.aurora.components.IconButton
import app.aurora.components.ScreenTitle
import app.aurora.components.SongRow
import app.aurora.components.surface
import app.aurora.domain.MediaType
import app.aurora.domain.Track
import app.aurora.components.typingField
import app.aurora.theme.Ui

/** Qué parte de la biblioteca busca un campo de búsqueda. */
enum class SearchScope { ALL, MUSIC_ONLY, VIDEO_ONLY }

/**
 * Busca en título, artista, álbum y género. En los buscadores separados (música o videos)
 * también mira el nombre del archivo, que es lo que se ve en la carpeta.
 */
fun searchTracks(tracks: List<Track>, query: String, scope: SearchScope = SearchScope.ALL): List<Track> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return emptyList()
    return tracks.filter { t ->
        val typeMatches = when (scope) {
            SearchScope.ALL -> true
            SearchScope.MUSIC_ONLY -> t.mediaType == MediaType.AUDIO
            SearchScope.VIDEO_ONLY -> t.mediaType == MediaType.VIDEO
        }
        if (!typeMatches) return@filter false
        val fields = listOfNotNull(t.title, t.artist, t.album, t.genre)
        val fileNames = if (scope == SearchScope.ALL) emptyList() else listOfNotNull(
            t.filePath?.substringAfterLast('/')?.substringAfterLast('\\'),
            t.filePath?.substringAfterLast('/')?.substringAfterLast('\\')?.substringBeforeLast('.', ""),
        )
        (fields + fileNames).any { q in it.lowercase() }
    }
}

@Composable
fun SearchField(
    query: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
    placeholder: String = "Busca canciones, artistas o videos",
) {
    Row(modifier.surface(Ui.shapes.chip).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(AuroraIcon.Search, Ui.colors.mute, size = 18.dp)
        BasicTextField(
            query, onChange, singleLine = true, textStyle = Ui.type.body,
            cursorBrush = SolidColor(Ui.colors.accent),
            modifier = Modifier.padding(start = 10.dp).weight(1f).then(fieldModifier).typingField(),
            decorationBox = { inner ->
                if (query.isEmpty()) BasicText(placeholder, style = Ui.type.body.copy(color = Ui.colors.mute))
                inner()
            },
        )
        if (query.isNotEmpty()) {
            IconButton(AuroraIcon.Close, "Borrar búsqueda", { onChange("") }, tint = Ui.colors.mute, size = 30.dp, iconSize = 16.dp)
        }
    }
}

@Composable
fun SearchScreen(tracks: List<Track>, bottomPadding: Dp, onPlay: (List<Track>, Int) -> Unit, onMore: (Track) -> Unit = {}) {
    var query by rememberSaveable { mutableStateOf("") }
    var scope by rememberSaveable { mutableStateOf(SearchScope.ALL) }
    val results = remember(tracks, query, scope) { searchTracks(tracks, query, scope) }
    LazyColumn(Modifier.statusBarsPadding(), contentPadding = mobilePadding(bottomPadding)) {
        item {
            ScreenTitle("Buscar")
            SearchField(query, { query = it }, Modifier.padding(vertical = 14.dp).fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Todo", scope == SearchScope.ALL, { scope = SearchScope.ALL })
                Chip("Canciones", scope == SearchScope.MUSIC_ONLY, { scope = SearchScope.MUSIC_ONLY })
                Chip("Videos", scope == SearchScope.VIDEO_ONLY, { scope = SearchScope.VIDEO_ONLY })
            }
            if (query.isNotBlank() && results.isEmpty()) {
                val what = when (scope) {
                    SearchScope.ALL -> "resultados"
                    SearchScope.MUSIC_ONLY -> "canciones"
                    SearchScope.VIDEO_ONLY -> "videos"
                }
                BasicText(
                    "No encontramos $what con «$query»",
                    style = Ui.type.rowSubtitle, modifier = Modifier.padding(top = 14.dp),
                )
            }
        }
        items(results, key = { it.id }) { t -> SongRow(t, { onPlay(results, results.indexOf(t)) }, onMore = { onMore(t) }) }
    }
}
