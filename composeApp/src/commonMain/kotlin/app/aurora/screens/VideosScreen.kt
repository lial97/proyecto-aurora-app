package app.aurora.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aurora.components.AuroraIcon
import app.aurora.components.EmptyState
import app.aurora.components.IconButton
import app.aurora.components.ScreenTitle
import app.aurora.components.VideoThumb
import app.aurora.components.pressable
import app.aurora.components.trackContextMenu
import app.aurora.data.LibraryState
import app.aurora.domain.Track
import app.aurora.domain.videos
import app.aurora.theme.AuroraDimens
import app.aurora.theme.Ui

/** Sección de videos: separada de la música para no mezclar mp3 con mp4. */
@Composable
fun VideosScreen(
    lib: LibraryState,
    bottomPadding: Dp,
    onPlay: (List<Track>, Int) -> Unit,
    onChooseVideoFolder: () -> Unit = {},
    onMore: (Track) -> Unit = {},
    onOpenSettings: () -> Unit = onChooseVideoFolder,
) {
    val videos = remember(lib.tracks) { lib.tracks.videos() }
    var query by rememberSaveable { mutableStateOf("") }
    val searching = query.isNotBlank()
    val results = remember(videos, query) { searchTracks(videos, query, SearchScope.VIDEO_ONLY) }
    val shown = if (searching) results else videos
    // Una posición para la lista completa y otra para los resultados: limpiar la búsqueda no mueve la lista.
    val allState = rememberLazyListState()
    val resultsState = rememberLazyListState()
    val listState = if (searching) resultsState else allState

    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    androidx.compose.ui.backhandler.BackHandler(enabled = searching) { query = "" }

    // La cabecera (título, buscador y contador) queda fija fuera de la lista: así el campo no se recrea al
    // empezar o terminar una búsqueda (antes se cerraba el teclado con la primera letra) ni sale de la
    // pantalla al bajar por los resultados.
    Column(Modifier.statusBarsPadding()) {
        Column(Modifier.padding(start = AuroraDimens.ScreenPadding, end = AuroraDimens.ScreenPadding, top = 20.dp)) {
            app.aurora.components.TabHeader("Videos", onOpenSettings)
            SearchField(
                query, { query = it }, Modifier.padding(top = 10.dp, bottom = 12.dp).fillMaxWidth(),
                placeholder = "Buscar en tus videos",
            )
            BasicText(
                if (searching) "${results.size} ${if (results.size == 1) "resultado" else "resultados"}"
                else "${videos.size} ${if (videos.size == 1) "video" else "videos"}",
                style = Ui.type.rowSubtitle, modifier = Modifier.padding(top = 2.dp, bottom = 14.dp),
            )
        }
        // Una lista por estado de desplazamiento (todos / resultados): limpiar la búsqueda no mueve la lista.
        androidx.compose.runtime.key(searching) {
            LazyColumn(
                Modifier.weight(1f).imePadding(), state = listState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = AuroraDimens.ScreenPadding, end = AuroraDimens.ScreenPadding, bottom = bottomPadding,
                ),
            ) {
                if (videos.isEmpty()) item {
                    EmptyState(
                        "No hay videos",
                        "Añade en Ajustes la carpeta donde guardas tus videos.",
                        actionLabel = "Elegir carpeta de videos",
                        onAction = onChooseVideoFolder,
                        icon = AuroraIcon.Video,
                    )
                } else if (searching && results.isEmpty()) item {
                    BasicText("No encontramos videos con «$query»", style = Ui.type.rowSubtitle)
                }
                items(shown, key = { it.id }) { v ->
                    VideoRow(v, { onPlay(shown, shown.indexOf(v)) }, onMore = { onMore(v) })
                }
            }
        }
    }
}

/**
 * Fila compacta de video, como la lista de la referencia: miniatura 16:9 de 128 × 72 dp,
 * título en dos líneas y botón de opciones de 28 × 36 dp.
 */
@Composable
private fun VideoRow(track: Track, onClick: () -> Unit, onMore: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .trackContextMenu(track)
            .pressable(track.title, .98f, hoverBg = true, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.width(128.dp)) {
            VideoThumb(track, preview = false, small = true)
        }
        Column(Modifier.weight(1f)) {
            BasicText(
                track.title, style = Ui.type.rowTitle,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                track.artist.ifBlank { "Video" }, style = Ui.type.rowSubtitle,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(
            AuroraIcon.More, "Opciones de ${track.title}", onMore,
            tint = Ui.colors.mute, size = 36.dp, iconSize = 18.dp,
        )
    }
}
