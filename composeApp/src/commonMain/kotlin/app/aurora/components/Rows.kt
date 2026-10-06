package app.aurora.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.aurora.domain.Track
import app.aurora.theme.AuroraDimens
import app.aurora.theme.ThemeId
import app.aurora.theme.Ui
import app.aurora.theme.label

/** Fila de canción. La actual muestra el ecualizador y el título en color de énfasis. */
@Composable
fun SongRow(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    number: Int? = null,
    isCurrent: Boolean = false,
    playing: Boolean = false,
    subtitle: String = "${track.artist} · ${track.album}",
    trailing: String? = formatTime(track.durationSec).takeIf { track.durationSec > 0 },
    onMore: (() -> Unit)? = null,
    playlist: app.aurora.domain.Playlist? = null,
) {
    val type = Ui.type
    Row(
        modifier
            .fillMaxWidth()
            .trackContextMenu(track, playlist)
            .pressable(track.title, .98f, hoverBg = true, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (number != null) {
            Box(Modifier.width(22.dp), contentAlignment = Alignment.Center) {
                if (isCurrent) Equalizer(playing) else BasicText("$number", style = type.caption)
            }
        }
        RowArt(track, AuroraDimens.CoverRow)
        Column(Modifier.weight(1f)) {
            if (track.mediaType == app.aurora.domain.MediaType.VIDEO) VideoBadge(Modifier.padding(bottom = 2.dp))
            BasicText(
                track.title,
                style = type.rowTitle.copy(color = if (isCurrent) Ui.colors.accent else Ui.colors.ink),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            BasicText(subtitle, style = type.rowSubtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) BasicText(trailing, style = type.caption)
        if (onMore != null) IconButton(AuroraIcon.More, "Opciones de ${track.title}", onMore, tint = Ui.colors.mute, size = 32.dp, iconSize = 18.dp)
    }
}

/** Mini reproductor (móvil): portada, título, artista, reproducir/pausa y línea de progreso de 2 dp. */
@Composable
fun MiniPlayer(
    track: Track, playing: Boolean, progress: Float, onOpen: () -> Unit, onToggle: () -> Unit,
    modifier: Modifier = Modifier, onOpenVideo: (() -> Unit)? = null,
) {
    val c = Ui.colors
    val type = Ui.type
    val shape = Ui.shapes.card
    val soft = Ui.theme.id == ThemeId.PETALO || Ui.theme.id == ThemeId.BRUMA
    Box(
        modifier
            .padding(horizontal = 10.dp)
            .fillMaxWidth()
            .height(AuroraDimens.MiniPlayerHeight)
            .pressable("Abrir reproductor", .98f, onClick = onOpen)
            .then(if (soft) Modifier.shadow(14.dp, shape, ambientColor = Color(0x22000000), spotColor = Color(0x22000000)) else Modifier)
            .clip(shape)
            .background(c.background)
            .background(c.raised)
            .border(if (Ui.shapes.borderWidth > 0.dp) 2.5.dp else 1.dp, c.surfaceBorder, shape),
    ) {
        Row(Modifier.padding(horizontal = 10.dp).align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
            Artwork(track, AuroraDimens.CoverXs)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                BasicText(track.title, style = type.rowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicText(track.artist, style = type.rowSubtitle, maxLines = 1)
            }
            if (onOpenVideo != null) IconButton(AuroraIcon.Video, "Ver video", onOpenVideo)
            IconButton(if (playing) AuroraIcon.Pause else AuroraIcon.Play, if (playing) "Pausar" else "Reproducir", onToggle)
        }
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(progress.coerceIn(0f, 1f)).height(2.dp).background(c.accent))
    }
}

/** Pestañas del móvil: Inicio, Videos, Buscar, Biblioteca y Ajustes. */
enum class Tab(val label: String, val icon: AuroraIcon) {
    Home("Inicio", AuroraIcon.Home),
    Videos("Videos", AuroraIcon.Video),
    Search("Buscar", AuroraIcon.Search),
    Library("Biblioteca", AuroraIcon.Library),
    Settings("Ajustes", AuroraIcon.Settings),
}

@Composable
fun TabBar(selected: Tab?, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val c = Ui.colors
    val theme = Ui.theme
    Column(modifier.fillMaxWidth().background(c.background).background(c.barBg)) {
        if (Ui.shapes.borderWidth > 0.dp) Box(Modifier.fillMaxWidth().height(3.dp).background(c.ink))
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(64.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.forEach { tab ->
                val tint = if (tab == selected) c.accent else c.mute
                Column(Modifier.weight(1f).pressable(tab.label) { onSelect(tab) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(tab.icon, tint)
                    // Siempre en una línea: con texto grande se achica un poco en vez de partirse ("BIBLIOTE CA").
                    FitText(theme.label(tab.label), Ui.type.tab.copy(color = tint), Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp).fillMaxWidth())
                }
            }
        }
    }
}

/** Aviso: píldora arriba (móvil) o abajo (escritorio), visible 2 s. */
@Composable
fun Toast(message: String?, modifier: Modifier = Modifier) {
    val c = Ui.colors
    AnimatedVisibility(
        visible = message != null,
        modifier = modifier.statusBarsPadding().padding(top = 12.dp, bottom = 104.dp),
        enter = fadeIn() + slideInVertically { -it / 2 },
        exit = fadeOut() + slideOutVertically { -it / 2 },
    ) {
        BasicText(
            message.orEmpty(),
            style = Ui.type.body.copy(color = c.background, fontWeight = FontWeight.SemiBold),
            modifier = Modifier.clip(Ui.shapes.chip).background(c.ink).padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}
