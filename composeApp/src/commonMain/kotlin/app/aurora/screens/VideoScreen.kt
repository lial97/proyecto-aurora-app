package app.aurora.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.aurora.AppDialog
import app.aurora.AppState
import app.aurora.components.Artwork
import app.aurora.components.AuroraIcon
import app.aurora.components.Chip
import app.aurora.components.IconButton
import app.aurora.components.SectionTitle
import app.aurora.components.VideoSurface
import app.aurora.components.VideoThumb
import app.aurora.components.pressable
import app.aurora.domain.Track
import app.aurora.player.PlaybackState
import app.aurora.theme.AuroraDimens
import app.aurora.theme.Ui

/** Pantalla de video del móvil: cuadro 16:9, portada y título debajo, acciones y "Más videos". */
@Composable
fun VideoScreen(state: AppState, playback: PlaybackState, videos: List<Track>, onClose: () -> Unit, onShowSong: () -> Unit = {}) {
    val t = playback.current ?: return
    val liked = t.id in state.liked
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(AuroraIcon.Down, "Cerrar video", onClose)
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    app.aurora.components.SongVideoSwitch(true, true, onSong = onShowSong, onVideo = {})
                }
                IconButton(AuroraIcon.More, "Opciones del video", { state.menuFor(t) })
            }
            VideoSurface(playback, state.player, onFullscreen = { state.setFullscreen(true) }, shape = androidx.compose.ui.graphics.RectangleShape)
            Row(Modifier.padding(AuroraDimens.ScreenPadding), verticalAlignment = Alignment.CenterVertically) {
                Artwork(t, 44.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    BasicText(t.title, style = Ui.type.rowTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    BasicText(t.artist, style = Ui.type.rowSubtitle, maxLines = 1)
                }
                IconButton(if (liked) AuroraIcon.HeartFilled else AuroraIcon.Heart, "Me gusta", { state.toggleLike(t) },
                    tint = if (liked) Ui.colors.accent else Ui.colors.ink)
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = AuroraDimens.ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Chip("Añadir a una lista", false, { state.dialog = AppDialog.AddToPlaylist(listOf(t)) }, icon = AuroraIcon.ListAdd)
                Chip("Solo audio", !playback.videoEnabled, { state.player.setVideoEnabled(!playback.videoEnabled) }, icon = AuroraIcon.Music)
                Chip("Pantalla completa", false, { state.setFullscreen(true) }, icon = AuroraIcon.Expand)
                Chip("Compartir", false, { state.show("Enlace copiado") }, icon = AuroraIcon.Share)
            }
            Box(Modifier.padding(horizontal = AuroraDimens.ScreenPadding)) { SectionTitle("Más videos") }
        }
        itemsIndexed(videos, key = { _, v -> v.id }) { i, v ->
            if (v.id == t.id) return@itemsIndexed
            Row(
                Modifier.fillMaxWidth().pressable(v.title, .98f, hoverBg = true) { state.play(videos, i, "Videos") }
                    .padding(horizontal = AuroraDimens.ScreenPadding, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(118.dp)) { VideoThumb(v) }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    BasicText(v.title, style = Ui.type.rowTitle.copy(color = if (v.id == t.id) Ui.colors.accent else Ui.colors.ink), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    BasicText(v.artist, style = Ui.type.rowSubtitle, maxLines = 1)
                }
                IconButton(AuroraIcon.More, "Opciones de ${v.title}", { state.menuFor(v) }, tint = Ui.colors.mute, size = 32.dp, iconSize = 18.dp)
            }
        }
    }
}

/** Abre la hoja de opciones de una canción. */
fun AppState.menuFor(track: Track, playlist: app.aurora.domain.Playlist? = null) { dialog = AppDialog.TrackMenu(track, playlist?.takeIf { it.isUser }) }
