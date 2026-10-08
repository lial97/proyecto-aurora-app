package app.aurora.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aurora.AppState
import app.aurora.data.MixAnswer
import app.aurora.domain.Playlist
import app.aurora.domain.resolve
import app.aurora.theme.Ui
import app.aurora.theme.label
import app.aurora.theme.title

/**
 * "Tu mezcla de hoy" en Inicio: mosaico con 4 portadas, el nombre por géneros, reproducir y, mientras no haya
 * respuesta, "¿Te gustó?" Sí / No (lo mismo que pregunta el diálogo tras escuchar 3 canciones).
 */
@Composable
fun DailyMixCard(state: AppState, onOpen: (Playlist) -> Unit, modifier: Modifier = Modifier) {
    val mix = state.dailyMix ?: return
    val c = Ui.colors
    val tracks = mix.playlist.resolve(state.library.state.value.tracks.associateBy { it.id })
    if (tracks.isEmpty()) return
    val art = if (Ui.isDesktop) 48.dp else 44.dp
    Row(
        modifier.fillMaxWidth().pressable("Tu mezcla de hoy: ${mix.playlist.name}", .99f) { onOpen(mix.playlist) }.surface().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Mosaico de 2 × 2 portadas.
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            tracks.take(4).chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { row.forEach { Artwork(it, art) } }
            }
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            BasicText(Ui.theme.label("Tu mezcla de hoy").uppercase(), style = Ui.type.caption.copy(color = c.accent, fontWeight = FontWeight.Bold, letterSpacing = 1.sp))
            BasicText(Ui.theme.title(mix.playlist.name), style = Ui.type.h2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            BasicText("${tracks.size} canciones · según lo que más escuchas", style = Ui.type.rowSubtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            androidx.compose.foundation.layout.FlowRow(
                Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Chip("Reproducir", true, { state.play(tracks, 0, "Tu mezcla de hoy") }, icon = AuroraIcon.Play)
                when (mix.answer) {
                    MixAnswer.PENDING -> {
                        Spacer(Modifier.width(4.dp))
                        BasicText("¿Te gustó?", style = Ui.type.caption)
                        Chip("Sí", false, { state.answerDailyMix(true) })
                        Chip("No", false, { state.answerDailyMix(false) })
                    }
                    MixAnswer.YES -> BasicText("Guardada en tus listas", style = Ui.type.caption)
                    MixAnswer.NO -> BasicText("Mañana tendrás otra", style = Ui.type.caption)
                }
            }
        }
    }
}
