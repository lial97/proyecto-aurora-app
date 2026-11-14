package app.aurora.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aurora.components.TabHeader
import app.aurora.domain.Playlist
import app.aurora.domain.Track
import app.aurora.theme.Ui

/**
 * Pestaña "Listas" (móvil): cuadrícula de dos columnas con "+ Crear lista", las listas propias, la mezcla del día
 * y las mezclas automáticas. Mantener pulsada una lista propia abre su menú (Renombrar / Eliminar).
 */
@Composable
fun PlaylistsScreen(
    tracks: List<Track>,
    playlists: List<Playlist>,
    bottomPadding: Dp,
    onOpenPlaylist: (Playlist) -> Unit,
    onCreatePlaylist: () -> Unit,
    onPlaylistMenu: (Playlist) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val byId = remember(tracks) { tracks.associateBy { it.id } }
    val own = playlists.count { it.isUser }
    // Primera celda: "+ Crear lista"; después, cada lista. `null` = la tarjeta de crear.
    val cells: List<Playlist?> = remember(playlists) { listOf<Playlist?>(null) + playlists }
    LazyColumn(Modifier.statusBarsPadding(), contentPadding = mobilePadding(bottomPadding)) {
        item(key = "cabecera") {
            TabHeader("Listas", onOpenSettings)
            BasicText(
                when (own) {
                    0 -> "Aún no tienes listas propias"
                    1 -> "1 lista tuya y ${playlists.size - 1} automáticas"
                    else -> "$own listas tuyas y ${playlists.size - own} automáticas"
                },
                style = Ui.type.rowSubtitle, modifier = Modifier.padding(top = 2.dp, bottom = 16.dp),
            )
        }
        items(cells.chunked(2), key = { row -> row.joinToString("|") { it?.id ?: "crear" } }) { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { p ->
                    BoxWithConstraints(Modifier.weight(1f)) {
                        if (p == null) {
                            CreatePlaylistCard(hasLists = own > 0, onClick = onCreatePlaylist, size = maxWidth)
                        } else {
                            val list = remember(p, byId) { p.trackIds.mapNotNull(byId::get) }
                            PlaylistCard(
                                p, list,
                                onClick = { onOpenPlaylist(p) },
                                onLongClick = if (p.isUser) ({ onPlaylistMenu(p) }) else null,
                                size = maxWidth,
                            )
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
