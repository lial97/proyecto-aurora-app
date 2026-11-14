package app.aurora.screens

import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.aurora.components.Artwork
import app.aurora.components.AuroraIcon
import app.aurora.components.Chip
import app.aurora.components.FitText
import app.aurora.components.Icon
import app.aurora.components.IconButton
import app.aurora.components.RowArt
import app.aurora.components.SectionTitle
import app.aurora.components.VideoBadge
import app.aurora.components.formatTime
import app.aurora.components.pressable
import app.aurora.components.surface
import app.aurora.domain.MediaType
import app.aurora.domain.Track
import app.aurora.theme.AuroraDimens
import app.aurora.theme.Ui

private enum class SelectorConfirm { EMPTY, DISCARD }

/**
 * Selector a pantalla completa para crear una lista o añadir canciones a una existente.
 * La selección se guarda aparte del filtro: cambiar la búsqueda o el chip no desmarca nada.
 */
@Composable
fun PlaylistSelector(
    name: String,
    tracks: List<Track>,
    initialIds: Set<String>,
    plays: (Track) -> Int,
    /** Crear permite guardar la lista vacía; añadir a una lista existente necesita algo marcado. */
    allowEmpty: Boolean,
    onCancel: () -> Unit,
    onDone: (List<Track>) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var scope by rememberSaveable { mutableStateOf(SearchScope.ALL) }
    val byId = remember(tracks) { tracks.associateBy { it.id } }
    // Solo lo que sigue en la biblioteca: una lista puede guardar pistas que ya no están (archivo borrado o
    // filtrado) y el contador las contaba sin poder mostrarlas.
    val initial = remember(initialIds, byId) { initialIds.filter { it in byId }.toSet() }
    var selectedOrder by rememberSaveable { mutableStateOf(initial.toList()) }
    var confirm by remember { mutableStateOf<SelectorConfirm?>(null) }
    val selected = selectedOrder.toSet()
    val selectedTracks = selectedOrder.mapNotNull(byId::get)
    val searching = query.isNotBlank()
    val results = remember(tracks, query, scope) { searchTracks(tracks, query, scope) }
    // Sin búsqueda, los chips también filtran los dos bloques (antes "Videos" seguía mostrando canciones).
    val inScope = remember(tracks, scope) {
        when (scope) {
            SearchScope.ALL -> tracks
            SearchScope.MUSIC_ONLY -> tracks.filter { it.mediaType != MediaType.VIDEO }
            SearchScope.VIDEO_ONLY -> tracks.filter { it.mediaType == MediaType.VIDEO }
        }
    }
    val recent = remember(inScope) { inScope.sortedByDescending { it.dateAddedMs }.take(20) }
    // Las reproducciones se leen una vez por pista (no en cada comparación) y solo cuenta lo que sonó alguna vez.
    val mostPlayed = remember(inScope) {
        inScope.map { it to plays(it) }.filter { it.second > 0 }.sortedByDescending { it.second }.take(20).map { it.first }
    }
    val visible = if (searching) results else (recent + mostPlayed).distinctBy { it.id }

    fun toggle(t: Track) {
        selectedOrder = if (t.id in selected) selectedOrder.filterNot { it == t.id } else selectedOrder + t.id
    }

    fun finish() {
        if (selectedOrder.isEmpty()) {
            // Crear una lista vacía se confirma; en "añadir" solo se guarda si se desmarcó lo que ya estaba.
            if (allowEmpty) confirm = SelectorConfirm.EMPTY
            else if (initial.isNotEmpty()) onDone(emptyList())
        } else onDone(selectedTracks)
    }

    fun requestClose() {
        // Solo pregunta si hay cambios sin guardar respecto a lo que ya estaba marcado.
        if (selected == initial) onCancel() else confirm = SelectorConfirm.DISCARD
    }

    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    androidx.compose.ui.backhandler.BackHandler {
        // Atrás cierra la confirmación; si no hay, cierra el selector con la misma regla que ←.
        if (confirm != null) confirm = null else requestClose()
    }

    // El fondo recibe los toques: si no, los que caen fuera de un botón llegaban a la pantalla de abajo
    // (mini reproductor, pestañas) con el selector abierto.
    Box(
        Modifier.fillMaxSize().background(Ui.colors.background)
            .clickable(remember { MutableInteractionSource() }, null) {},
    ) {
        Column(Modifier.fillMaxSize().imePadding()) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = AuroraDimens.ScreenPadding, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(AuroraIcon.Back, "Cerrar selector", { requestClose() })
                FitText(name, Ui.type.h2, Modifier.weight(1f).padding(horizontal = 8.dp), minScale = .6f)
                Chip("Listo (${selectedOrder.size})", true, { finish() })
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
                item(key = "buscar") {
                    SearchField(
                        query, { query = it },
                        Modifier.padding(horizontal = AuroraDimens.ScreenPadding, vertical = 8.dp).fillMaxWidth(),
                        placeholder = "Buscar canciones o videos",
                    )
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                            .padding(horizontal = AuroraDimens.ScreenPadding, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Chip("Todo", scope == SearchScope.ALL, { scope = SearchScope.ALL })
                        Chip("Canciones", scope == SearchScope.MUSIC_ONLY, { scope = SearchScope.MUSIC_ONLY })
                        Chip("Videos", scope == SearchScope.VIDEO_ONLY, { scope = SearchScope.VIDEO_ONLY })
                    }
                }
                if (searching) {
                    if (results.isEmpty()) {
                        item(key = "sin-resultados") {
                            BasicText(
                                "No encontramos resultados con «$query»",
                                style = Ui.type.rowSubtitle,
                                modifier = Modifier.padding(start = AuroraDimens.ScreenPadding, end = AuroraDimens.ScreenPadding, top = 14.dp),
                            )
                        }
                    } else {
                        items(results, key = { it.id }) { t ->
                            SelectorRow(t, t.id in selected, { toggle(t) })
                        }
                    }
                } else {
                    item(key = "recientes-titulo") { SectionTitle("Añadidas hace poco", Modifier.padding(horizontal = AuroraDimens.ScreenPadding)) }
                    items(recent, key = { "recent:${it.id}" }) { t ->
                        SelectorRow(t, t.id in selected, { toggle(t) })
                    }
                    item(key = "escuchadas-titulo") { SectionTitle("Más escuchadas", Modifier.padding(horizontal = AuroraDimens.ScreenPadding)) }
                    items(mostPlayed, key = { "played:${it.id}" }) { t ->
                        SelectorRow(t, t.id in selected, { toggle(t) })
                    }
                }
            }
            // Barra inferior fija: contador, portadas elegidas y acciones.
            Column(
                Modifier.fillMaxWidth().background(Ui.colors.barBg).navigationBarsPadding()
                    .padding(horizontal = AuroraDimens.ScreenPadding, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText(
                        "${selectedOrder.size} ${if (selectedOrder.size == 1) "seleccionada" else "seleccionadas"}",
                        style = Ui.type.label.copy(color = Ui.colors.accent, fontWeight = FontWeight.Bold),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    Spacer(Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        selectedTracks.take(5).forEach { t ->
                            Artwork(
                                t, 28.dp,
                                Modifier.pressable("Quitar «${t.title}»", .9f) { toggle(t) },
                            )
                        }
                        if (selectedTracks.size > 5) {
                            BasicText("+${selectedTracks.size - 5}", style = Ui.type.caption.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Chip(
                        "Seleccionar todo lo visible", false,
                        { selectedOrder = (selectedOrder + visible.map { it.id }).distinct() },
                    )
                    Chip("Limpiar", false, { selectedOrder = emptyList() })
                }
            }
        }
        val c = confirm
        if (c != null) {
            val empty = c == SelectorConfirm.EMPTY
            ConfirmSelector(
                title = if (empty) "¿Crear la lista vacía?" else "¿Descartar la lista?",
                detail = if (empty) "Puedes añadir canciones o videos más tarde." else "Los elementos marcados no se guardarán.",
                confirmLabel = if (empty) "Crear" else "Descartar",
                onDismiss = { confirm = null },
                onConfirm = {
                    confirm = null
                    if (empty) onDone(emptyList()) else onCancel()
                },
            )
        }
    }
}

@Composable
private fun SelectorRow(track: Track, checked: Boolean, onToggle: () -> Unit) {
    val c = Ui.colors
    Row(
        Modifier.fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics { contentDescription = "Marcar «${track.title}»" }
            .padding(horizontal = AuroraDimens.ScreenPadding, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RowArt(track, AuroraDimens.CoverRow)
        Column(Modifier.weight(1f)) {
            if (track.mediaType == MediaType.VIDEO) VideoBadge(Modifier.padding(bottom = 2.dp))
            BasicText(track.title, style = Ui.type.rowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(
                if (track.album.isBlank()) track.artist else "${track.artist} · ${track.album}",
                style = Ui.type.rowSubtitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (track.durationSec > 0) BasicText(formatTime(track.durationSec), style = Ui.type.caption)
        // Casilla: rol Checkbox y estado marcado los aporta toggleable.
        Box(
            Modifier.size(22.dp).clip(Ui.shapes.artSmall)
                .background(if (checked) c.accent else c.surface)
                .border(1.5.dp, if (checked) c.accent else c.surfaceBorder, Ui.shapes.artSmall),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(AuroraIcon.Check, c.onAccent, size = 15.dp)
        }
    }
}

@Composable
private fun ConfirmSelector(
    title: String,
    detail: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val c = Ui.colors
    Box(
        Modifier.fillMaxSize().background(c.background.copy(alpha = .86f))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(24.dp).widthIn(max = 360.dp).surface().padding(20.dp)
                .clickable(remember { MutableInteractionSource() }, null) {},
        ) {
            BasicText(title, style = Ui.type.h2)
            BasicText(detail, style = Ui.type.rowSubtitle, modifier = Modifier.padding(top = 8.dp))
            Row(
                Modifier.fillMaxWidth().padding(top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                Chip("Seguir eligiendo", false, onDismiss)
                Chip(confirmLabel, true, onConfirm)
            }
        }
    }
}
