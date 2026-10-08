package app.aurora.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.mutableIntStateOf
import app.aurora.domain.needsCorrection
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.aurora.AppDialog
import app.aurora.AppState
import app.aurora.domain.Playlist
import app.aurora.domain.Track
import app.aurora.theme.Ui
import app.aurora.theme.title
import app.aurora.theme.label
import androidx.compose.foundation.layout.Spacer
import kotlinx.datetime.toLocalDateTime

/** Muestra la ventana emergente activa de [AppState.dialog]. */
@Composable
fun DialogHost(state: AppState, tracks: List<Track>) {
    val d = state.dialog
    val dismiss = { state.dialog = null }
    // El menú contextual (clic derecho) no oscurece la pantalla: solo cierra al hacer clic fuera.
    val contextMenu = d is AppDialog.TrackMenu && d.anchor != null && Ui.isDesktop
    // Con el menú contextual no hay capa encima: los clics fuera los gestiona la ventana (ver DesktopApp),
    // así un clic derecho sobre otra canción abre su menú directamente.
    AnimatedVisibility(d != null && !contextMenu, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier.fillMaxSize().background(Color(0x8C07060F))
                .clickable(remember { MutableInteractionSource() }, null, onClick = dismiss)
                .onPreviewKeyEvent { if (it.key == Key.Escape) { dismiss(); true } else false },
        )
    }
    // El contenido se recuerda mientras se anima la salida.
    var last by remember { mutableStateOf<AppDialog?>(null) }
    if (d != null) last = d
    val anchored = (last as? AppDialog.TrackMenu)?.anchor?.takeIf { Ui.isDesktop }
    if (anchored != null) {
        AnimatedVisibility(d != null, Modifier.fillMaxSize(), enter = fadeIn(), exit = fadeOut()) {
            AnchoredLayout(anchored) {
                Box(Modifier.onGloballyPositioned { state.contextMenuBounds = it.boundsInRoot() }) {
                    ContextMenu(state, last as AppDialog.TrackMenu, dismiss)
                }
            }
        }
        return
    }
    AnimatedVisibility(
        d != null, Modifier.fillMaxSize(),
        enter = fadeIn() + slideInVertically { it / 6 }, exit = fadeOut() + slideOutVertically { it / 6 },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = if (Ui.isDesktop) Alignment.Center else Alignment.BottomCenter) {
            when (val dlg = last) {
                is AppDialog.TrackMenu -> TrackMenu(state, dlg, dismiss)
                is AppDialog.TrackDetails -> TrackDetails(state, dlg.track, dismiss)
                is AppDialog.EditTrack -> EditTrackDialog(state, dlg.track, dismiss)
                is AppDialog.FixMetadata -> FixMetadataDialog(state, dlg.track, dismiss)
                AppDialog.TracksToFix -> Card(maxHeight = 640.dp) {
                    val list = remember(state.statsVersion) { state.tracksNeedingFix() }
                    Title("Canciones con datos dudosos")
                    BasicText(if (list.isEmpty()) "No queda ninguna. ¡Todo en orden!" else "Toca una para corregirla.", style = Ui.type.rowSubtitle, modifier = Modifier.padding(bottom = 8.dp))
                    list.forEach { t ->
                        Row(
                            Modifier.fillMaxWidth().pressable(t.title, .99f, hoverBg = true) { state.dialog = AppDialog.FixMetadata(t) }.padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RowArt(t, 40.dp)
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                BasicText(t.title, style = Ui.type.rowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                BasicText(t.filePath?.substringAfterLast('/') ?: t.artist, style = Ui.type.rowSubtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(AuroraIcon.Sparkle, Ui.colors.accent, size = 18.dp)
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) { Chip("Cerrar", false, dismiss) }
                }
                is AppDialog.AddToPlaylist -> AddToPlaylist(state, dlg.tracks, dismiss)
                is AppDialog.NewPlaylist -> NameDialog("Nueva lista", "Crear", "", dismiss) { name ->
                    state.createPlaylist(name, dlg.tracks); dismiss()
                }
                is AppDialog.RenamePlaylist -> NameDialog("Renombrar lista", "Guardar", dlg.playlist.name, dismiss) { name ->
                    state.renamePlaylist(dlg.playlist, name); dismiss()
                }
                AppDialog.EditName -> NameDialog(
                    "¿Cómo te llamamos?", "Guardar", state.settings.userName.value, dismiss,
                    placeholder = "Tu nombre o apodo", maxLength = app.aurora.data.SettingsRepository.MAX_NAME, allowEmpty = true,
                    hint = "Aparece en el saludo de Inicio. Déjalo vacío para no usar nombre.",
                ) { name -> state.settings.setUserName(name); dismiss() }
                is AppDialog.DeletePlaylist -> Card {
                    Title("¿Borrar \"${dlg.playlist.name}\"?")
                    BasicText("Las canciones no se borran de tu dispositivo, solo la lista.", style = Ui.type.rowSubtitle)
                    Buttons(dismiss, "Borrar") { state.deletePlaylist(dlg.playlist); dismiss() }
                }
                AppDialog.DailyMixFeedback -> Card {
                    val mix = state.dailyMix
                    Title("¿Te gustó la mezcla de hoy?")
                    BasicText(
                        "${mix?.playlist?.name ?: "Tu mezcla"}: si te gustó, la guardamos en tus listas. Si no, mañana tendrás otra.",
                        style = Ui.type.rowSubtitle,
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                        Chip("No", false, { state.answerDailyMix(false); dismiss() })
                        Chip("Sí, guardarla", true, { state.answerDailyMix(true); dismiss() })
                    }
                }
                null -> {}
            }
        }
    }
}

/** Tarjeta centrada (escritorio) u hoja inferior (móvil). */
@Composable
private fun BoxScope.Card(width: androidx.compose.ui.unit.Dp = 440.dp, maxHeight: androidx.compose.ui.unit.Dp = 560.dp, content: @Composable () -> Unit) {
    val c = Ui.colors
    val desk = Ui.isDesktop
    val shape = if (desk) Ui.shapes.card else RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    Column(
        Modifier
            .then(if (desk) Modifier.width(width) else Modifier.fillMaxWidth())
            .heightIn(max = maxHeight)
            .shadow(30.dp, shape)
            .background(c.background, shape)
            .background(c.raised, shape)
            .clickable(remember { MutableInteractionSource() }, null) {} // no cierra al tocar dentro
            .navigationBarsPadding()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        if (!desk) Box(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp).width(40.dp).background(c.mute, RoundedCornerShape(3.dp)).padding(vertical = 2.dp))
        content()
    }
}

@Composable
private fun Title(text: String) = BasicText(Ui.theme.title(text), style = Ui.type.h2.copy(color = Ui.colors.ink), modifier = Modifier.padding(bottom = 10.dp))

@Composable
private fun Option(icon: AuroraIcon, label: String, tint: Color = Ui.colors.ink, compact: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().semantics { role = Role.Button }.pressable(label, .98f, hoverBg = true, onClick = onClick)
            .padding(horizontal = if (compact) 10.dp else 6.dp, vertical = if (compact) 7.dp else 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, tint, size = 20.dp)
        BasicText(label, style = Ui.type.body.copy(color = tint), modifier = Modifier.padding(start = 14.dp))
    }
}

@Composable
private fun Buttons(onCancel: () -> Unit, confirm: String, enabled: Boolean = true, onConfirm: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
        Chip("Cancelar", false, onCancel)
        Chip(confirm, true, { if (enabled) onConfirm() })
    }
}

/** Opciones de una pista: las mismas en la hoja (móvil/⋯) y en el menú contextual (clic derecho). */
@Composable
private fun TrackOptions(state: AppState, d: AppDialog.TrackMenu, dismiss: () -> Unit, compact: Boolean) {
    val t = d.track
    val liked = t.id in state.liked
    val desk = state.platform.isDesktop
    val sep = @Composable { if (compact) Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(Ui.colors.ink.copy(alpha = .08f))) }
    Option(AuroraIcon.Play, "Reproducir", compact = compact) { state.play(listOf(t), 0, "Selección"); dismiss() }
    Option(AuroraIcon.Next, "Reproducir a continuación", compact = compact) { state.playNext(t); dismiss() }
    Option(AuroraIcon.Queue, "Añadir a la cola", compact = compact) { state.addToQueue(t); dismiss() }
    sep()
    Option(if (liked) AuroraIcon.HeartFilled else AuroraIcon.Heart, if (liked) "Quitar de Me gusta" else "Añadir a Me gusta",
        if (liked) Ui.colors.accent else Ui.colors.ink, compact) { state.toggleLike(t); dismiss() }
    Option(AuroraIcon.ListAdd, "Añadir a una lista…", compact = compact) { state.dialog = AppDialog.AddToPlaylist(listOf(t)) }
    val p = d.inPlaylist?.let { state.latest(it) }
    if (p != null) {
        val i = p.trackIds.indexOf(t.id)
        if (i > 0) Option(AuroraIcon.ArrowUp, "Mover arriba", compact = compact) { state.moveInPlaylist(p, i, i - 1); dismiss() }
        if (i in 0 until p.trackIds.lastIndex) Option(AuroraIcon.ArrowDown, "Mover abajo", compact = compact) { state.moveInPlaylist(p, i, i + 1); dismiss() }
        Option(AuroraIcon.Trash, "Quitar de ${p.name}", compact = compact) { state.removeFromPlaylist(p, t); dismiss() }
    }
    sep()
    Option(AuroraIcon.Info, "Ver detalles", compact = compact) { state.dialog = AppDialog.TrackDetails(t) }
    if (t.filePath != null && t.mediaType == app.aurora.domain.MediaType.AUDIO) {
        Option(AuroraIcon.Sparkle, "Corregir datos…", if (t.needsCorrection()) Ui.colors.accent else Ui.colors.ink, compact = compact) { state.dialog = AppDialog.FixMetadata(t) }
        Option(AuroraIcon.Edit, "Editar datos…", compact = compact) { state.dialog = AppDialog.EditTrack(t) }
    }
    if (desk && t.filePath != null) Option(AuroraIcon.Folder, "Mostrar en la carpeta", compact = compact) { state.revealInFolder(t); dismiss() }
}

/** Hoja de opciones de una canción (spec §2.6), desde ⋯ o en el móvil. */
@Composable
private fun BoxScope.TrackMenu(state: AppState, d: AppDialog.TrackMenu, dismiss: () -> Unit) {
    val t = d.track
    Card {
        Row(Modifier.padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            RowArt(t, 52.dp)
            Column(Modifier.padding(start = 12.dp)) {
                BasicText(t.title, style = Ui.type.rowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicText("${t.artist}, ${t.album}", style = Ui.type.rowSubtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        TrackOptions(state, d, dismiss, compact = false)
    }
}

/** Menú contextual compacto junto al cursor (clic derecho en escritorio). */
@Composable
private fun ContextMenu(state: AppState, d: AppDialog.TrackMenu, dismiss: () -> Unit) {
    val c = Ui.colors
    val shape = Ui.shapes.card
    Column(
        Modifier.width(270.dp)
            .shadow(24.dp, shape)
            .background(c.background, shape)
            .background(c.raised, shape)
            .border(1.dp, c.surfaceBorder, shape)
            .clickable(remember { MutableInteractionSource() }, null) {}
            .padding(vertical = 6.dp),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            RowArt(d.track, 36.dp)
            Column(Modifier.padding(start = 10.dp)) {
                BasicText(d.track.title, style = Ui.type.rowTitle.copy(fontSize = Ui.type.caption.fontSize * 1.1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicText(d.track.artist, style = Ui.type.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(c.ink.copy(alpha = .08f)))
        TrackOptions(state, d, dismiss, compact = true)
    }
}

@Composable
private fun BoxScope.AddToPlaylist(state: AppState, tracks: List<Track>, dismiss: () -> Unit) {
    Card {
        Title(if (tracks.size == 1) "Añadir a una lista" else "Añadir ${tracks.size} canciones a una lista")
        Option(AuroraIcon.Plus, "Nueva lista…", Ui.colors.accent) { state.dialog = AppDialog.NewPlaylist(tracks) }
        if (state.userPlaylists.isEmpty()) {
            BasicText("Aún no tienes listas. Crea la primera.", style = Ui.type.rowSubtitle, modifier = Modifier.padding(6.dp))
        }
        state.userPlaylists.forEach { p -> PlaylistOption(p) { state.addToPlaylist(p, tracks); dismiss() } }
    }
}

@Composable
private fun PlaylistOption(p: Playlist, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().pressable(p.name, .98f, hoverBg = true, onClick = onClick).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
        MixSwatch(p.colors, 40.dp)
        Column(Modifier.padding(start = 12.dp)) {
            BasicText(p.name, style = Ui.type.rowTitle, maxLines = 1)
            BasicText("${p.trackIds.size} canciones", style = Ui.type.caption)
        }
    }
}

/** Pide un nombre (crear o renombrar lista). Enter confirma y Esc cancela. */
@Composable
private fun BoxScope.NameDialog(
    title: String, confirm: String, initial: String, dismiss: () -> Unit,
    placeholder: String = "Nombre de la lista", maxLength: Int = 80, allowEmpty: Boolean = false, hint: String? = null,
    onDone: (String) -> Unit,
) {
    var value by remember(initial) { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val ok = allowEmpty || value.text.isNotBlank()
    Card {
        Title(title)
        if (hint != null) BasicText(hint, style = Ui.type.rowSubtitle, modifier = Modifier.padding(bottom = 10.dp))
        Box(Modifier.fillMaxWidth().surface(Ui.shapes.chip).padding(horizontal = 14.dp, vertical = 12.dp)) {
            BasicTextField(
                value, { if (it.text.length <= maxLength) value = it }, singleLine = true,
                textStyle = Ui.type.body, cursorBrush = SolidColor(Ui.colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (ok) onDone(value.text) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).typingField().onPreviewKeyEvent {
                    if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent it.key == Key.Enter || it.key == Key.Escape
                    when (it.key) {
                        Key.Enter -> { if (ok) onDone(value.text); true }
                        Key.Escape -> { dismiss(); true }
                        else -> false
                    }
                },
                decorationBox = { inner ->
                    if (value.text.isEmpty()) BasicText(placeholder, style = Ui.type.body.copy(color = Ui.colors.mute))
                    inner()
                },
            )
        }
        Spacer(Modifier.padding(2.dp))
        Buttons(dismiss, confirm, ok) { onDone(value.text) }
    }
}

/** Ventana de detalles: todo lo que se sabe de la pista. */
@Composable
private fun BoxScope.TrackDetails(state: AppState, t: Track, dismiss: () -> Unit) {
    val c = Ui.colors
    val liked = t.id in state.liked
    val video = t.mediaType == app.aurora.domain.MediaType.VIDEO
    Card(width = 640.dp, maxHeight = 680.dp) {
        Row(verticalAlignment = Alignment.Top) {
            if (video) Column(Modifier.width(260.dp)) { VideoThumb(t); VideoStoryboard(t) }
            else Artwork(t, 170.dp, large = true)
            Column(Modifier.weight(1f).padding(start = 18.dp)) {
                if (video) VideoBadge(Modifier.padding(bottom = 4.dp))
                BasicText(t.title, style = Ui.type.h2.copy(color = c.ink), maxLines = 3, overflow = TextOverflow.Ellipsis)
                BasicText(t.artist, style = Ui.type.body.copy(color = c.mute), modifier = Modifier.padding(top = 4.dp))
                androidx.compose.foundation.layout.FlowRow(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Reproducir", true, { state.play(listOf(t), 0, "Detalles"); dismiss() }, icon = AuroraIcon.Play)
                    Chip(if (liked) "Te gusta" else "Me gusta", false, { state.toggleLike(t) }, icon = if (liked) AuroraIcon.HeartFilled else AuroraIcon.Heart)
                }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Añadir a una lista", false, { state.dialog = AppDialog.AddToPlaylist(listOf(t)) }, icon = AuroraIcon.ListAdd)
                }
            }
            IconButton(AuroraIcon.Close, "Cerrar", dismiss, tint = c.mute)
        }
        Box(Modifier.fillMaxWidth().padding(vertical = 16.dp).height(1.dp).background(c.ink.copy(alpha = .1f)))
        val rows = trackDetailRows(t)
        rows.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                pair.forEach { (k, v) -> DetailField(k, v, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        t.filePath?.let { path ->
            DetailField("Ubicación", path, Modifier.fillMaxWidth())
            if (state.platform.isDesktop) Row(Modifier.padding(top = 8.dp)) {
                Chip("Mostrar en la carpeta", false, { state.revealInFolder(t) }, icon = AuroraIcon.Folder)
            }
        }
    }
}

@Composable
private fun DetailField(label: String, value: String, modifier: Modifier) {
    Column(modifier.padding(end = 12.dp)) {
        BasicText(Ui.theme.label(label), style = Ui.type.caption)
        androidx.compose.foundation.text.selection.SelectionContainer {
            BasicText(value, style = Ui.type.body, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** Datos que se muestran en Detalles (solo los que existen). */
fun trackDetailRows(t: Track): List<Pair<String, String>> = buildList {
    add("Tipo" to if (t.mediaType == app.aurora.domain.MediaType.VIDEO) "Video" else "Canción")
    add("Duración" to if (t.durationSec > 0) formatTime(t.durationSec) else "—")
    add("Álbum" to t.album)
    add("Artista" to t.artist)
    t.year?.let { add("Año" to it.toString()) }
    t.genre?.let { add("Género" to it) }
    t.bpm?.let { add("Tempo" to "$it BPM") }
    t.key?.let { add("Tonalidad" to it) }
    t.format?.let { add("Formato" to it) }
    t.bitrateKbps?.let { add("Calidad" to "$it kbps") }
    t.fileSizeBytes?.let { add("Tamaño" to formatSize(it)) }
    if (t.dateAddedMs > 0) add("Añadida" to formatDate(t.dateAddedMs))
    t.composers?.let { add("Compositores" to it) }
    t.producers?.let { add("Producción" to it) }
    add("Información" to when (t.metadataSource) {
        app.aurora.domain.MetadataSource.FILE_TAGS -> "Etiquetas del archivo"
        app.aurora.domain.MetadataSource.USER -> "Deducida del nombre del archivo"
        app.aurora.domain.MetadataSource.MUSICBRAINZ -> "MusicBrainz"
    })
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "${((bytes / 1_073_741_824.0) * 10).toLong() / 10.0} GB"
    bytes >= 1_048_576 -> "${((bytes / 1_048_576.0) * 10).toLong() / 10.0} MB"
    else -> "${bytes / 1024} KB"
}

private val monthsShort = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

fun formatDate(epochMs: Long): String {
    val d = kotlin.time.Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
    return "${d.day} ${monthsShort[d.month.ordinal]} ${d.year}"
}

/** Editar datos de la pista. Se guarda en la app y, si se elige, también en las etiquetas del archivo. */
@Composable
private fun BoxScope.EditTrackDialog(state: AppState, t: Track, dismiss: () -> Unit) {
    var title by remember { mutableStateOf(t.title) }
    var artist by remember { mutableStateOf(t.artist) }
    var album by remember { mutableStateOf(t.album) }
    var year by remember { mutableStateOf(t.year?.toString().orEmpty()) }
    var genre by remember { mutableStateOf(t.genre.orEmpty()) }
    val canWrite = state.platform.isDesktop && t.filePath != null && t.mediaType == app.aurora.domain.MediaType.AUDIO
    var writeFile by remember { mutableStateOf(false) }
    Card(width = 520.dp) {
        Title("Editar datos")
        Field("Título", title) { title = it }
        Field("Artista", artist) { artist = it }
        Field("Álbum", album) { album = it }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) { Field("Año", year) { year = it.filter(Char::isDigit).take(4) } }
            Box(Modifier.weight(2f)) { Field("Género", genre) { genre = it } }
        }
        if (canWrite) Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Switch(writeFile, "Guardar también en el archivo", { writeFile = it })
            Column(Modifier.padding(start = 10.dp)) {
                BasicText("Guardar también en el archivo", style = Ui.type.rowTitle)
                BasicText("Así otros reproductores verán los cambios", style = Ui.type.caption)
            }
        }
        Buttons(dismiss, "Guardar", title.isNotBlank() && artist.isNotBlank()) {
            fun ch(new: String, old: String?) = new.trim().takeIf { it.isNotEmpty() && it != old }
            state.editTrack(t, app.aurora.domain.TrackEdit(
                title = ch(title, t.title), artist = ch(artist, t.artist), album = ch(album, t.album),
                year = year.toIntOrNull()?.takeIf { it != t.year }, genre = ch(genre, t.genre),
            ), writeFile)
            dismiss()
        }
    }
}

/**
 * Corregir datos (F5): se escribe (o se acepta la sugerencia) el título y el artista, se busca en MusicBrainz
 * y se elige el resultado correcto. Se guarda en la app y, si se elige, en las etiquetas del archivo.
 */
@Composable
private fun BoxScope.FixMetadataDialog(state: AppState, t: Track, dismiss: () -> Unit) {
    val c = Ui.colors
    // Datos dudosos: la sugerencia sale del nombre del archivo ("Título - Artista (320).mp3").
    val guess = remember(t.id) {
        if (t.needsCorrection()) app.aurora.domain.guessFromFileName(t.filePath?.substringAfterLast('/') ?: t.title).let { it.title to it.artist.orEmpty() }
        else t.title to t.artist.takeUnless { it == app.aurora.domain.UNKNOWN_ARTIST }.orEmpty()
    }
    var title by remember { mutableStateOf(guess.first) }
    var artist by remember { mutableStateOf(guess.second) }
    var searching by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<app.aurora.data.MetadataSearch?>(null) }
    var selected by remember { mutableStateOf<app.aurora.data.MetadataCandidate?>(null) }
    var query by remember { mutableIntStateOf(0) }
    val canWrite = state.platform.isDesktop && t.mediaType == app.aurora.domain.MediaType.AUDIO
    var writeFile by remember { mutableStateOf(false) }
    var useCover by remember { mutableStateOf(true) }
    // Miniaturas ya descargadas (por disco), para no pedirlas otra vez al volver a dibujar.
    val thumbs = remember { androidx.compose.runtime.mutableStateMapOf<String, androidx.compose.ui.graphics.ImageBitmap?>() }
    // Busca al abrir y cada vez que se pulsa "Buscar".
    LaunchedEffect(query) {
        if (title.isBlank()) return@LaunchedEffect
        searching = true; selected = null
        result = state.metadata.search(title, artist.ifBlank { null }, t.durationSec)
        selected = result?.candidates?.firstOrNull()
        searching = false
    }
    Card(width = 560.dp, maxHeight = 640.dp) {
        Title("Corregir datos")
        BasicText("¿Cómo se llama esta canción y quién la canta? Aurora busca los datos correctos en MusicBrainz.", style = Ui.type.rowSubtitle)
        t.filePath?.let { BasicText("Archivo: " + it.substringAfterLast('/'), style = Ui.type.caption, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp)) }
        Field("Título", title) { title = it }
        Field("Artista (opcional, mejora la búsqueda)", artist) { artist = it }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.End) {
            Chip(if (searching) "Buscando…" else "Buscar", false, { if (!searching && title.isNotBlank()) query++ }, icon = AuroraIcon.Search)
        }
        val list = result?.candidates
        when {
            searching -> BasicText("Buscando en MusicBrainz…", style = Ui.type.caption, modifier = Modifier.padding(vertical = 14.dp))
            result == null -> Unit
            list == null -> BasicText("No hay conexión con MusicBrainz. Inténtalo de nuevo en un momento.", style = Ui.type.caption.copy(color = c.accent), modifier = Modifier.padding(vertical = 14.dp))
            list.isEmpty() -> BasicText("No se encontró nada. Revisa el título o prueba sin el artista.", style = Ui.type.caption, modifier = Modifier.padding(vertical = 14.dp))
            else -> {
                BasicText(Ui.theme.label("Elige el correcto"), style = Ui.type.caption.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                list.forEach { m ->
                    val on = m == selected
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(Ui.shapes.chip)
                            .background(if (on) c.accent.copy(alpha = .14f) else Color.Transparent)
                            .semantics { role = Role.RadioButton }
                            .pressable("${m.title}, ${m.artist}", .99f, hoverBg = true) { selected = m }
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Portada del disco (Cover Art Archive); un recuadro con ♪ si no tiene.
                        val rid = m.releaseId
                        if (rid != null && rid !in thumbs) LaunchedEffect(rid) { thumbs[rid] = state.coverThumbnail(rid) }
                        val img = rid?.let { thumbs[it] }
                        Box(Modifier.size(44.dp).clip(Ui.shapes.artSmall).background(c.surface), contentAlignment = Alignment.Center) {
                            if (img != null) androidx.compose.foundation.Image(img, "Portada de ${m.album ?: m.title}", Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                            else Icon(AuroraIcon.Music, c.mute, size = 18.dp)
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            BasicText(m.title, style = Ui.type.rowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            BasicText(listOfNotNull(m.artist, m.album, m.year?.toString()).joinToString(" · "), style = Ui.type.rowSubtitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        m.durationSec?.let { d ->
                            // La duración ayuda a distinguir versiones (en vivo, remix…).
                            val near = t.durationSec <= 0 || kotlin.math.abs(d - t.durationSec) <= 5
                            BasicText(formatTime(d), style = Ui.type.caption.copy(color = if (near) c.ink else c.mute), modifier = Modifier.padding(start = 8.dp))
                        }
                        if (on) Icon(AuroraIcon.Check, c.accent, size = 18.dp, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        // La portada solo se ofrece si el resultado elegido tiene una (ya se ve su miniatura).
        val selectedCover = selected?.releaseId?.let { thumbs[it] } != null
        if (selectedCover) Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Switch(useCover, "Usar la portada del disco", { useCover = it })
            Column(Modifier.padding(start = 10.dp)) {
                BasicText("Usar la portada del disco", style = Ui.type.rowTitle)
                BasicText("Se descarga y se ve en la app, los widgets y la notificación", style = Ui.type.caption)
            }
        }
        if (canWrite) Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Switch(writeFile, "Guardar también en el archivo", { writeFile = it })
            Column(Modifier.padding(start = 10.dp)) {
                BasicText("Guardar también en el archivo", style = Ui.type.rowTitle)
                BasicText("Así otros reproductores verán los cambios", style = Ui.type.caption)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            Chip("Editar a mano", false, { state.dialog = AppDialog.EditTrack(t) }, icon = AuroraIcon.Edit)
            Spacer(Modifier.weight(1f))
            Chip("Cancelar", false, dismiss)
            Chip("Aplicar", true, { selected?.let { state.applyMetadata(t, it, writeFile, withCover = useCover && selectedCover); dismiss() } })
        }
        BasicText("Datos de MusicBrainz y portadas de Cover Art Archive. Solo se envían el título y el artista.", style = Ui.type.caption.copy(color = c.mute), modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    Column(Modifier.padding(top = 8.dp)) {
        BasicText(label, style = Ui.type.caption, modifier = Modifier.padding(bottom = 4.dp))
        Box(Modifier.fillMaxWidth().surface(Ui.shapes.chip).padding(horizontal = 12.dp, vertical = 9.dp)) {
            BasicTextField(value, onChange, singleLine = true, textStyle = Ui.type.body, cursorBrush = SolidColor(Ui.colors.accent),
                modifier = Modifier.fillMaxWidth().typingField())
        }
    }
}
