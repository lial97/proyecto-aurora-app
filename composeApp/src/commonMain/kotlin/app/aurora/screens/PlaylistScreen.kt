package app.aurora.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aurora.components.AuroraIcon
import app.aurora.components.Hero
import app.aurora.components.IconButton
import app.aurora.components.PlayButton
import app.aurora.components.ScreenTitle
import app.aurora.components.SongRow
import app.aurora.domain.Playlist
import app.aurora.domain.Track
import app.aurora.player.PlaybackState
import app.aurora.theme.AuroraDimens
import app.aurora.theme.AuroraMotion
import app.aurora.theme.Ui
import kotlinx.coroutines.delay

@Composable
fun PlaylistScreen(
    playlist: Playlist,
    tracks: List<Track>,
    playback: PlaybackState,
    bottomPadding: Dp,
    onBack: () -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    onShuffle: () -> Unit,
    onToast: (String) -> Unit,
    onMore: (Track) -> Unit = {},
    onRename: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onSaveAsList: (() -> Unit)? = null,
) {
    val type = Ui.type
    val minutes = tracks.sumOf { it.durationSec } / 60
    val playingThis = playback.isPlaying && tracks.any { it.id == playback.current?.id }

    LazyColumn(
        Modifier.statusBarsPadding(),
        contentPadding = PaddingValues(start = AuroraDimens.ScreenPadding, end = AuroraDimens.ScreenPadding, top = 8.dp, bottom = bottomPadding),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(AuroraIcon.Back, "Volver", onBack)
                BasicText("Tu biblioteca", style = type.rowTitle.copy(textAlign = TextAlign.Center), modifier = Modifier.weight(1f))
                IconButton(AuroraIcon.More, "Más opciones", { onToast("Opciones de la lista: próximamente") })
            }
            Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = if (Ui.theme.leftAlignedHero) Alignment.CenterStart else Alignment.Center) {
                if (tracks.isEmpty()) app.aurora.components.MixSwatch(playlist.colors, AuroraDimens.CoverList) else Hero(tracks.first(), AuroraDimens.CoverList)
            }
            ScreenTitle(playlist.name)
            playlist.description?.let { BasicText(it, style = type.rowSubtitle, modifier = Modifier.padding(top = 4.dp)) }
            BasicText("${tracks.size} canciones, $minutes min", style = type.caption, modifier = Modifier.padding(top = 4.dp))
            if (tracks.isEmpty()) BasicText("Lista vacía: usa ⋯ en una canción y elige \"Añadir a una lista\".", style = type.rowSubtitle, modifier = Modifier.padding(top = 10.dp))
            Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (onRename != null) IconButton(AuroraIcon.Edit, "Renombrar lista", onRename)
                if (onDelete != null) IconButton(AuroraIcon.Trash, "Borrar lista", onDelete)
                if (onSaveAsList != null) IconButton(AuroraIcon.ListAdd, "Guardar como lista propia", onSaveAsList)
                Spacer(Modifier.weight(1f))
                IconButton(AuroraIcon.Shuffle, "Aleatorio", onShuffle, tint = if (playback.shuffle) Ui.colors.accent else Ui.colors.ink)
                PlayButton(playingThis, AuroraDimens.PlayButtonList, { onPlay(tracks, 0) })
            }
        }
        itemsIndexed(tracks, key = { _, t -> t.id }) { i, t ->
            // Entrada en cascada: desde 16 dp a la derecha, 450 ms, 40 ms entre filas (spec §7).
            val anim = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                delay(120L + minOf(i, 12) * 40L)
                anim.animateTo(1f, tween(450, easing = AuroraMotion.Ease))
            }
            SongRow(
                t, { onPlay(tracks, i) },
                modifier = Modifier.graphicsLayer { alpha = anim.value; translationX = (1f - anim.value) * 16.dp.toPx() },
                number = i + 1, isCurrent = playback.current?.id == t.id, playing = playback.isPlaying, subtitle = t.artist,
                onMore = { onMore(t) },
            )
        }
    }
}
