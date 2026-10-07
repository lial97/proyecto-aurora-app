package app.aurora.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aurora.AppState
import app.aurora.domain.MediaType
import app.aurora.AppDialog
import app.aurora.audio.Eq
import app.aurora.color.buildPalette
import app.aurora.components.Artwork
import app.aurora.components.AuroraIcon
import app.aurora.components.Chip
import app.aurora.components.EqualizerEditor
import app.aurora.components.Icon
import app.aurora.components.IconButton
import app.aurora.components.ScreenTitle
import app.aurora.components.Switch
import app.aurora.components.formatSize
import app.aurora.components.pressable
import app.aurora.components.surface
import app.aurora.components.typingField
import app.aurora.data.AppPrefs
import app.aurora.data.EndOfQueue
import app.aurora.data.LibraryState
import app.aurora.data.sample.SampleData
import app.aurora.theme.AppTheme
import app.aurora.theme.AppThemeProvider
import app.aurora.theme.AuroraDimens
import app.aurora.theme.ThemeId
import app.aurora.theme.Themes
import app.aurora.theme.Ui
import app.aurora.theme.label
import app.aurora.theme.title

/** Las 7 secciones de Ajustes y las palabras con las que el buscador las encuentra. */
enum class SettingsSection(val label: String, val icon: AuroraIcon, val keywords: String) {
    LIBRARY("Biblioteca", AuroraIcon.Folder, "carpetas música videos añadir ruta buscar formatos escanear vigilar cambios"),
    APPEARANCE("Apariencia", AuroraIcon.Palette, "nombre perfil saludo tema aurora póster pétalo seda carbono estadio bruma casete acuarela bosque grafito rockola color canción luces fondo densidad compacta"),
    SOUND("Sonido", AuroraIcon.Equalizer, "ecualizador eq graves agudos preset preamplificador volumen normalizar saturación"),
    PLAYBACK("Reproducción", AuroraIcon.Play, "bloqueo pantalla notificación vlc motor fundido crossfade sin pausas gapless cola terminar reanudar recordar"),
    LYRICS("Letras", AuroraIcon.Lyrics, "letras lrc lrclib descargar sincronizada karaoke borrar caché"),
    ACCESSIBILITY("Accesibilidad", AuroraIcon.Accessibility, "tamaño texto letra grande movimiento contraste foco teclado atajos lector pantalla anunciar"),
    ABOUT("Acerca de", AuroraIcon.Info, "versión licencia gpl código fuente reportar problema"),
}

private fun norm(s: String) = s.lowercase()
    .replace('á', 'a').replace('é', 'e').replace('í', 'i').replace('ó', 'o').replace('ú', 'u').replace('ñ', 'n')

/** Sección que mejor coincide con la búsqueda (o `null`). */
fun searchSettings(query: String): SettingsSection? {
    val q = norm(query.trim()).takeIf { it.length >= 2 } ?: return null
    return SettingsSection.entries.firstOrNull { s -> norm(s.label).contains(q) || norm(s.keywords).split(' ').any { it.startsWith(q) || q.startsWith(it) && it.length >= 4 } }
}

/** Contexto de una fila: la búsqueda actual (para resaltarla) y qué "?" está abierto. */
private class SettingsCtx(val query: String, val openHelp: String?, val onHelp: (String?) -> Unit)

/**
 * Ajustes por secciones. En escritorio: lista vertical a la izquierda (flechas ↑ ↓) y buscador.
 * En móvil: chips de sección arriba. @param onBack `null` en escritorio.
 */
@Composable
fun SettingsScreen(state: AppState, lib: LibraryState, onBack: (() -> Unit)?, contentPadding: PaddingValues) {
    var section by rememberSaveable { mutableStateOf(SettingsSection.LIBRARY) }
    var query by rememberSaveable { mutableStateOf("") }
    var help by remember { mutableStateOf<String?>(null) }
    val ctx = SettingsCtx(query, help) { help = it }
    val desk = Ui.isDesktop

    // Cada sección recuerda su propio desplazamiento (antes compartían uno y se abrían a media página).
    val scrolls = SettingsSection.entries.associateWith { rememberScrollState() }
    val content: @Composable (Modifier) -> Unit = { m ->
        // El desplazamiento ocupa todo el ancho (la rueda funciona en cualquier parte); el contenido, como mucho 760 dp.
        Column(m.verticalScroll(scrolls.getValue(section))) {
            Column(Modifier.widthIn(max = 760.dp)) {
                when (section) {
                    SettingsSection.LIBRARY -> LibrarySection(state, lib, ctx)
                    SettingsSection.APPEARANCE -> AppearanceSection(state, ctx)
                    SettingsSection.SOUND -> SoundSection(state, ctx)
                    SettingsSection.PLAYBACK -> PlaybackSection(state, ctx)
                    SettingsSection.LYRICS -> LyricsSettingsSection(state, ctx)
                    SettingsSection.ACCESSIBILITY -> AccessibilitySection(state, ctx)
                    SettingsSection.ABOUT -> AboutSection(state)
                }
                Spacer(Modifier.height(if (desk) 24.dp else 40.dp))
            }
        }
    }

    val searchBox: @Composable (Modifier) -> Unit = { m ->
        Row(m.surface(Ui.shapes.chip).padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(AuroraIcon.Search, Ui.colors.mute, size = 16.dp)
            BasicTextField(
                query, { q -> query = q; searchSettings(q)?.let { section = it } }, singleLine = true,
                textStyle = Ui.type.body, cursorBrush = SolidColor(Ui.colors.accent),
                modifier = Modifier.padding(start = 8.dp).fillMaxWidth().typingField(),
                decorationBox = { inner ->
                    if (query.isEmpty()) BasicText("Buscar un ajuste", style = Ui.type.body.copy(color = Ui.colors.mute))
                    inner()
                },
            )
        }
    }

    if (desk) {
        Row(Modifier.fillMaxSize().padding(contentPadding)) {
            // Lista de secciones (↑ ↓ para moverse).
            Column(
                Modifier.width(200.dp).fillMaxHeight()
                    .focusable()
                    .onKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                        val i = section.ordinal
                        when (e.key) {
                            Key.DirectionDown -> { section = SettingsSection.entries[(i + 1) % 7]; true }
                            Key.DirectionUp -> { section = SettingsSection.entries[(i + 6) % 7]; true }
                            else -> false
                        }
                    },
            ) {
                ScreenTitle("Ajustes", style = Ui.type.h1.copy(fontSize = Ui.type.h1.fontSize * .8f))
                Spacer(Modifier.height(14.dp))
                searchBox(Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                SettingsSection.entries.forEach { s -> SectionItem(s, s == section) { section = s; help = null } }
                BasicText("↑ ↓ para moverse", style = Ui.type.caption, modifier = Modifier.padding(start = 10.dp, top = 10.dp))
            }
            Spacer(Modifier.width(24.dp))
            content(Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        // Móvil: lista de secciones con buscador; cada sección abre su subpágina.
        var open by rememberSaveable { mutableStateOf<SettingsSection?>(null) }
        val o = open
        @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
        androidx.compose.ui.backhandler.BackHandler(enabled = o != null) { open = null; help = null }
        if (o != null) {
            Column(Modifier.fillMaxSize().padding(contentPadding)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(AuroraIcon.Back, "Volver a Ajustes", { open = null; help = null })
                    BasicText("Ajustes", style = Ui.type.caption, modifier = Modifier.padding(start = 4.dp))
                }
                content(Modifier.weight(1f).fillMaxWidth())
            }
        } else {
            val prefs by state.prefsRepo.prefs.collectAsState()
            val folders by state.settings.folders.collectAsState()
            val videoFolders by state.settings.videoFolders.collectAsState()
            val q = norm(query.trim())
            val hits = if (q.length < 2) emptyList() else SettingsSection.entries.filter { s ->
                norm(s.label).contains(q) || norm(s.keywords).split(' ').any { it.startsWith(q) }
            }
            Column(Modifier.fillMaxSize().padding(contentPadding).verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onBack != null) { IconButton(AuroraIcon.Back, "Volver", onBack); Spacer(Modifier.width(6.dp)) }
                    ScreenTitle("Ajustes")
                }
                searchBox(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 14.dp))
                val name by state.settings.userName.collectAsState()
                if (q.length < 2) Row(
                    Modifier.padding(bottom = 12.dp).fillMaxWidth().surface().pressable("Tu nombre", .98f, hoverBg = true) { state.dialog = AppDialog.EditName }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(Ui.colors.accent.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                        Icon(AuroraIcon.Edit, Ui.colors.accent, size = 17.dp)
                    }
                    BasicText("Tu nombre", style = Ui.type.rowTitle, modifier = Modifier.weight(1f).padding(start = 12.dp))
                    BasicText(name.ifEmpty { "Sin nombre" }, style = Ui.type.caption, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = 6.dp).widthIn(max = 160.dp))
                    Icon(AuroraIcon.Forward, Ui.colors.mute, size = 16.dp)
                }
                val themeName = Ui.theme.displayName
                val value: (SettingsSection) -> String? = { s ->
                    when (s) {
                        SettingsSection.LIBRARY -> (folders + videoFolders).distinct().size.let { n -> "$n ${if (n == 1) "carpeta" else "carpetas"}" }
                        SettingsSection.APPEARANCE -> themeName
                        SettingsSection.SOUND -> if (prefs.eq.enabled) "EQ " + (if (prefs.eq.isCustom) "propio" else Eq.presets[prefs.eq.preset].name) else "EQ apagado"
                        SettingsSection.LYRICS -> "LRCLIB"
                        SettingsSection.ABOUT -> "v0.3.0"
                        else -> null
                    }
                }
                val shown = if (q.length >= 2) hits else SettingsSection.entries
                (if (q.length >= 2) listOf(shown) else listOf(shown.take(3), shown.drop(3))).filter { it.isNotEmpty() }.forEachIndexed { gi, group ->
                    Column(Modifier.padding(top = if (gi > 0) 12.dp else 0.dp).fillMaxWidth().surface()) {
                        group.forEachIndexed { i, s ->
                            if (i > 0) Box(Modifier.padding(start = 56.dp).fillMaxWidth().height(1.dp).background(Ui.colors.ink.copy(alpha = .07f)))
                            Row(
                                Modifier.fillMaxWidth().pressable(s.label, .98f, hoverBg = true) { open = s; section = s }.padding(horizontal = 14.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(Ui.colors.accent.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                                    Icon(s.icon, Ui.colors.accent, size = 17.dp)
                                }
                                BasicText(s.label, style = Ui.type.rowTitle, modifier = Modifier.weight(1f).padding(start = 12.dp))
                                value(s)?.let { BasicText(it, style = Ui.type.caption, maxLines = 1, modifier = Modifier.padding(end = 6.dp)) }
                                Icon(AuroraIcon.Forward, Ui.colors.mute, size = 16.dp)
                            }
                        }
                    }
                }
                if (q.length >= 2 && hits.isEmpty()) BasicText("Sin resultados", style = Ui.type.rowSubtitle, modifier = Modifier.padding(8.dp))
            }
        }
    }
}

@Composable
private fun SectionItem(s: SettingsSection, on: Boolean, onClick: () -> Unit) {
    val c = Ui.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 1.dp).pressable(s.label, .98f, hoverBg = !on, onClick = onClick)
            .clip(Ui.shapes.card).background(if (on) c.ink.copy(alpha = .08f) else Color.Transparent)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(s.icon, if (on) c.accent else c.mute, size = 18.dp)
        BasicText(Ui.theme.label(s.label), style = Ui.type.body.copy(color = if (on) c.ink else c.mute, fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(start = 12.dp))
    }
}

// ---------- Piezas comunes ----------

@Composable
private fun Header(title: String, lead: String) {
    BasicText(Ui.theme.title(title), style = Ui.type.h1.copy(fontSize = Ui.type.h2.fontSize * 1.5f, color = Ui.colors.ink))
    BasicText(lead, style = Ui.type.rowSubtitle.copy(fontSize = 14.sp), modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
}

@Composable
private fun Group(label: String, trailing: String? = null, content: @Composable () -> Unit) {
    Row(Modifier.padding(top = 22.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(Ui.theme.label(label).uppercase(), style = Ui.type.caption.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp))
        if (trailing != null) BasicText(" · $trailing", style = Ui.type.caption)
    }
    Column(Modifier.fillMaxWidth().surface().padding(vertical = 4.dp)) { content() }
}

/** Fila de ajuste: ícono, nombre corto, pista de una línea, "?" opcional y control a la derecha. */
@Composable
private fun SettingRow(
    ctx: SettingsCtx, icon: AuroraIcon, label: String, hint: String,
    help: String? = null, enabled: Boolean = true,
    /** Control ancho (deslizador, opciones): en el móvil va debajo del nombre para no apretarlo. */
    wide: Boolean = false,
    control: (@Composable () -> Unit)? = null,
) {
    val c = Ui.colors
    val below = wide && !Ui.isDesktop
    val q = norm(ctx.query.trim())
    val hit = q.length >= 2 && (norm(label).contains(q) || norm(hint).contains(q))
    val bg by animateColorAsState(if (hit) c.accent.copy(alpha = .16f) else Color.Transparent, tween(300))
    Column(Modifier.fillMaxWidth().background(bg).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, if (enabled) c.accent else c.mute, size = 20.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText(label, style = Ui.type.rowTitle.copy(color = if (enabled) c.ink else c.mute))
                    if (help != null) {
                        val open = ctx.openHelp == label
                        Box(
                            Modifier.padding(start = 6.dp).size(18.dp).pressable("Más información sobre $label") { ctx.onHelp(if (open) null else label) }
                                .clip(CircleShape).background(if (open) c.accent else c.ink.copy(alpha = .12f)),
                            contentAlignment = Alignment.Center,
                        ) { BasicText("?", style = Ui.type.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (open) c.onAccent else c.ink)) }
                    }
                }
                BasicText(hint, style = Ui.type.rowSubtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!below) control?.invoke()
        }
        if (below && control != null) Box(Modifier.padding(start = 32.dp, top = 8.dp)) { control() }
        if (help != null && ctx.openHelp == label) {
            BasicText(help, style = Ui.type.body.copy(color = c.ink), modifier = Modifier.padding(start = 32.dp, top = 8.dp, end = 8.dp)
                .clip(RoundedCornerShape(10.dp)).background(c.ink.copy(alpha = .06f)).padding(12.dp))
        }
    }
}

@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { i, o -> Chip(o, i == selected, { onSelect(i) }) }
    }
}

/** Deslizador horizontal (ratón y teclado). */
@Composable
private fun ValueSlider(value: Int, min: Int, max: Int, step: Int, label: String, format: (Int) -> String, onChange: (Int) -> Unit) {
    val c = Ui.colors
    val cur by rememberUpdatedState(value)
    val set by rememberUpdatedState(onChange)
    fun pick(x: Float, w: Int) { set(((min + (x / w).coerceIn(0f, 1f) * (max - min)) / step).let { kotlin.math.round(it).toInt() * step }.coerceIn(min, max)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.width(150.dp).height(22.dp)
                .semantics { contentDescription = label; stateDescription = format(value) }
                .focusable()
                .onKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (e.key) {
                        Key.DirectionRight, Key.DirectionUp -> { set((cur + step).coerceAtMost(max)); true }
                        Key.DirectionLeft, Key.DirectionDown -> { set((cur - step).coerceAtLeast(min)); true }
                        else -> false
                    }
                }
                .pointerInput(Unit) { detectTapGestures { pick(it.x, size.width) } }
                .pointerInput(Unit) { detectHorizontalDragGestures { ch, _ -> pick(ch.position.x, size.width) } }
                .drawBehind {
                    val h = 4.dp.toPx(); val y = size.height / 2 - h / 2
                    val f = (value - min).toFloat() / (max - min)
                    drawRoundRect(c.track, Offset(0f, y), Size(size.width, h), CornerRadius(h))
                    drawRoundRect(c.accent, Offset(0f, y), Size(size.width * f, h), CornerRadius(h))
                    drawCircle(c.ink, 7.dp.toPx(), Offset(size.width * f, size.height / 2))
                },
        )
        BasicText(format(value), style = Ui.type.body.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.width(56.dp).padding(start = 10.dp))
    }
}

private fun AppState.prefs(f: (AppPrefs) -> AppPrefs) = prefsRepo.update(f)

// ---------- Secciones ----------

@Composable
private fun LibrarySection(state: AppState, lib: LibraryState, ctx: SettingsCtx) {
    val folders by state.settings.folders.collectAsState()
    val videoFolders by state.settings.videoFolders.collectAsState()
    val p by state.prefsRepo.prefs.collectAsState()
    val media = state.platform.media
    Header("Biblioteca", "Dónde busca Aurora tu música y tus videos.")
    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(lib.songCount to "canciones", lib.videoCount to "videos", (folders + videoFolders).distinct().size to "carpetas").forEach { (n, l) ->
            Column(Modifier.weight(1f).surface().padding(14.dp)) {
                BasicText("$n", style = Ui.type.h2.copy(color = Ui.colors.ink, fontSize = 24.sp))
                BasicText(l, style = Ui.type.caption)
            }
        }
    }
    val status = when {
        lib.scanning -> lib.progress?.let { pr -> if (pr.total != null) "buscando ${pr.done} de ${pr.total}" else "buscando…" }
        lib.error != null -> lib.error
        // El tempo se calcula en segundo plano: solo se informa el avance.
        lib.stage == app.aurora.data.ScanStage.TEMPO && lib.stageTotal > 0 -> "tempo ${lib.stageDone} de ${lib.stageTotal}"
        else -> null
    }
    FolderGroup(state, MediaType.AUDIO, "Carpetas de música", status, folders)
    FolderGroup(state, MediaType.VIDEO, "Carpetas de videos", null, videoFolders)
    BasicText("Solo los videos de estas carpetas aparecen en la app.", style = Ui.type.caption, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
    Group("Opciones") {
        SettingRow(ctx, AuroraIcon.Refresh, "Vigilar cambios", "Detecta archivos nuevos al instante") {
            Switch(p.watchFolders, "Vigilar cambios", { v -> state.prefs { it.copy(watchFolders = v) } })
        }
        SettingRow(ctx, AuroraIcon.Video, "Incluir videos", "Se muestran en la pestaña Videos") {
            Switch(p.includeVideos, "Incluir videos", { v -> state.prefs { it.copy(includeVideos = v) } })
        }
        SettingRow(ctx, AuroraIcon.Music, "Formatos", "Audio y video compatibles",
            help = "Aurora busca en estas carpetas y en todas sus subcarpetas. Las canciones van a Biblioteca y los videos a Videos.") {}
        Row(Modifier.padding(start = 46.dp, end = 14.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("mp3", "flac", "m4a", "ogg", "opus", "wav").forEach { FormatChip(it, false) }
                listOf("mp4", "mkv", "webm").forEach { FormatChip(it, true) }
            }
        }
    }
    // Corrección inteligente de datos (F5).
    val toFix = remember(lib.tracks, state.statsVersion) { state.tracksNeedingFix() }
    Group("Datos de las canciones") {
        SettingRow(ctx, AuroraIcon.Sparkle, "Corregir datos", if (toFix.isEmpty()) "Todas tus canciones tienen título y artista" else "${toFix.size} con datos dudosos (\"00000.mp3\", sin artista…)",
            help = "Aurora busca el nombre correcto en MusicBrainz y actualiza título, artista, álbum, año y género. También puedes hacerlo desde el menú de cada canción.") {
            if (toFix.isNotEmpty()) Chip("Revisar", false, { state.dialog = app.aurora.AppDialog.TracksToFix })
        }
    }
}

/** Carpetas de un tipo: nombre, ruta y cuántos archivos encontró; añadir, escribir ruta y sugeridas. */
@Composable
private fun FolderGroup(state: AppState, type: MediaType, title: String, status: String?, folders: List<String>) {
    val media = state.platform.media
    val counts by state.library.counts.collectAsState()
    var typing by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf("") }
    val desk = Ui.isDesktop
    val candidates by produceState(emptyList<String>(), folders) { value = if (desk) media.candidateFolders().filter { it !in folders && app.aurora.domain.isSuggestibleFolder(it) } else emptyList() }
    val video = type == MediaType.VIDEO
    Group(title, status) {
        if (folders.isEmpty()) {
            BasicText(if (video) "Sin carpetas de videos. Los videos personales no se muestran." else "No hay carpetas. Añade una para encontrar tu música.",
                style = Ui.type.body.copy(color = Ui.colors.mute), modifier = Modifier.padding(14.dp))
        }
        folders.forEach { path ->
            val label = media.describeFolder(path)
            val n = counts[app.aurora.data.LibraryFolder(path, type)]
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (video) AuroraIcon.Video else AuroraIcon.Folder, Ui.colors.accent, size = 20.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    BasicText(label.name, style = Ui.type.rowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BasicText(label.path, style = Ui.type.rowSubtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                BasicText(foundLabel(n, video), style = Ui.type.caption, modifier = Modifier.padding(end = 4.dp))
                IconButton(AuroraIcon.Trash, "Quitar carpeta ${label.name}", { state.removeFolder(path, type) }, tint = Ui.colors.mute)
            }
        }
        if (typing) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp).fillMaxWidth().surface(Ui.shapes.chip).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(manual, { manual = it }, singleLine = true, textStyle = Ui.type.body, cursorBrush = SolidColor(Ui.colors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (manual.isNotBlank()) { state.addFolder(manual, type); manual = ""; typing = false } }),
                    modifier = Modifier.weight(1f).typingField(),
                    decorationBox = { inner -> if (manual.isEmpty()) BasicText(if (video) "/home/usuario/Videos" else "/home/usuario/Música", style = Ui.type.body.copy(color = Ui.colors.mute)); inner() })
                Chip("Añadir", true, { if (manual.isNotBlank()) { state.addFolder(manual, type); manual = ""; typing = false } })
            }
        }
        androidx.compose.foundation.layout.FlowRow(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (media.canPickFolder) Chip("Añadir carpeta", true, { state.pickAndAddFolder(type) }, icon = AuroraIcon.Plus)
            if (!video) Chip(if (state.library.state.value.scanning) "Buscando…" else "Volver a buscar", false, { state.library.rescan() }, icon = AuroraIcon.Refresh)
            if (desk) Chip("Escribir ruta", false, { typing = !typing }, icon = AuroraIcon.Edit)
        }
        if (candidates.isNotEmpty()) {
            Row(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                BasicText("Sugeridas:", style = Ui.type.caption)
                candidates.take(8).forEach { cnd -> Chip(cnd.substringAfterLast('/'), false, { state.addFolder(cnd, type) }, icon = AuroraIcon.Plus) }
            }
        }
    }
}

/** "12 canciones", "1 video", o "buscando…" mientras se escanea la carpeta. */
fun foundLabel(n: Int?, video: Boolean): String = when {
    n == null -> "buscando…"
    video -> "$n ${if (n == 1) "video" else "videos"}"
    else -> "$n ${if (n == 1) "canción" else "canciones"}"
}

@Composable
private fun FormatChip(ext: String, video: Boolean) {
    val c = Ui.colors
    BasicText(ext, style = Ui.type.caption.copy(color = if (video) c.accent else c.ink, fontWeight = FontWeight.SemiBold),
        maxLines = 1, softWrap = false,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).border(1.dp, if (video) c.accent else c.surfaceBorder, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp))
}

@Composable
private fun AppearanceSection(state: AppState, ctx: SettingsCtx) {
    val theme by state.settings.theme.collectAsState()
    val p by state.prefsRepo.prefs.collectAsState()
    Header("Apariencia", "El cambio se aplica al instante.")
    val name by state.settings.userName.collectAsState()
    Group("Perfil") {
        SettingRow(ctx, AuroraIcon.Edit, "Tu nombre", name.ifEmpty { "Sin nombre" } + " · aparece en el saludo") {
            Chip("Cambiar", false, { state.dialog = AppDialog.EditName })
        }
    }
    BasicText(Ui.theme.label("Tema").uppercase(), style = Ui.type.caption.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp), modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
    val cols = if (Ui.isDesktop) 4 else 2
    Column(Modifier.selectableGroup()) { Themes.all.chunked(cols).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { t -> ThemePreview(t, t.id == theme.id, Modifier.weight(1f)) { state.settings.setTheme(t) } }
            repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
        }
    } }
    Group("Detalles") {
        val aurora = theme.id == ThemeId.AURORA
        SettingRow(ctx, AuroraIcon.Palette, "Color según la canción", if (aurora) "Tempo y portada pintan la app" else "Solo disponible en Aurora", enabled = aurora,
            help = "Las canciones lentas tiñen la app de azul y las movidas de rojo. El color de énfasis sale de la portada.") {
            Switch(p.dynamicColor, "Color según la canción", { v -> state.prefs { it.copy(dynamicColor = v) } }, enabled = aurora)
        }
        SettingRow(ctx, AuroraIcon.Sparkle, "Luces de fondo", "Decoración animada del tema") {
            Switch(p.backgroundLights, "Luces de fondo", { v -> state.prefs { it.copy(backgroundLights = v) } })
        }
        SettingRow(ctx, AuroraIcon.Panel, "Densidad", "Espacio entre elementos", wide = true) {
            Segmented(listOf("Cómoda", "Compacta"), if (p.compact) 1 else 0) { i -> state.prefs { it.copy(compact = i == 1) } }
        }
    }
    if (!state.platform.isDesktop) Group("Widgets") {
        SettingRow(ctx, AuroraIcon.Palette, "Estilo de los widgets", "Colores de los widgets de la pantalla de inicio", wide = true,
            help = "\"Colores del fondo de pantalla\" usa Material You (Android 12 o superior); en versiones anteriores se ve como el tema Aurora.") {
            Segmented(listOf("Igual que la app", "Colores del fondo de pantalla"), if (p.widgetsWallpaper) 1 else 0) { i -> state.prefs { it.copy(widgetsWallpaper = i == 1) } }
        }
    }
}

@Composable
private fun SoundSection(state: AppState, ctx: SettingsCtx) {
    val p by state.prefsRepo.prefs.collectAsState()
    val eq = p.eq
    Header("Sonido", if (state.platform.isDesktop) "Ecualizador de 10 bandas de VLC." else "Ecualizador de 10 bandas.")
    Group("Ecualizador") {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Switch(eq.enabled, "Activar ecualizador", { v -> state.prefs { it.copy(eq = it.eq.copy(enabled = v)) } })
            BasicText(if (eq.enabled) "Activado" else "Desactivado", style = Ui.type.rowTitle, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 10.dp).weight(1f))
            Chip("Restablecer", false, { state.prefs { it.copy(eq = it.eq.withPreset(0)) } }, icon = AuroraIcon.Refresh)
        }
        if (!state.hasRealAudio) BasicText(if (state.platform.isDesktop) "Sin VLC el ecualizador no suena (la reproducción es simulada)." else "El ecualizador no suena: la reproducción es simulada.", style = Ui.type.caption, modifier = Modifier.padding(horizontal = 14.dp))
        EqualizerEditor(eq, { e -> state.prefs { it.copy(eq = e.copy(enabled = it.eq.enabled)) } }, Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
        BasicText(Ui.theme.label("Presets").uppercase(), style = Ui.type.caption.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp), modifier = Modifier.padding(start = 14.dp, top = 6.dp, bottom = 8.dp))
        // Los presets pasan a la línea siguiente si no caben (cada uno en una sola línea).
        androidx.compose.foundation.layout.FlowRow(
            Modifier.padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Eq.presets.forEachIndexed { i, pr ->
                Chip(pr.name, !eq.isCustom && eq.preset == i, { state.prefs { it.copy(eq = it.eq.withPreset(i).copy(enabled = true)) } })
            }
            if (eq.isCustom) Chip("Personalizado", true, {})
        }
        Spacer(Modifier.height(8.dp))
        SettingRow(ctx, AuroraIcon.Check, "Evitar saturación", "Baja el volumen general lo justo si una banda sube",
            help = "Algunos presets suben bandas hasta +16 dB. Sin protección eso recorta el sonido (medido en VLC: el preset Rock recorta el 21,8 % de las muestras). Con esta opción el volumen general baja lo justo y no se recorta nada.") {
            Switch(eq.protect, "Evitar saturación", { v -> state.prefs { it.copy(eq = it.eq.copy(protect = v)) } })
        }
    }
    Group("Volumen") {
        SettingRow(ctx, AuroraIcon.Volume, "Normalizar volumen", "Todas las canciones suenan parejo (desde la siguiente)") {
            Switch(p.normalize, "Normalizar volumen", { v -> state.prefs { it.copy(normalize = v) } })
        }
    }
}

@Composable
private fun PlaybackSection(state: AppState, ctx: SettingsCtx) {
    val p by state.prefsRepo.prefs.collectAsState()
    Header("Reproducción", "Cómo pasa Aurora de una canción a otra.")
    Group("Motor") {
        SettingRow(ctx, AuroraIcon.Play, if (state.platform.isDesktop) "Audio con VLC" else "Audio con Media3", if (state.hasRealAudio) "Funcionando correctamente" else if (state.platform.isDesktop) "No se encontró VLC: instálalo para escuchar" else "Simulado en ${state.platform.name} por ahora") {
            BasicText(if (state.hasRealAudio) "● Activo" else "○ No disponible", style = Ui.type.caption.copy(color = if (state.hasRealAudio) Ui.colors.accent else Ui.colors.mute, fontWeight = FontWeight.Bold))
        }
    }
    if (!state.platform.isDesktop) Group("Pantalla de bloqueo") {
        SettingRow(ctx, AuroraIcon.Lock, "Mostrar controles en la pantalla de bloqueo", "Canción, portada y botones con el teléfono bloqueado",
            help = "Si lo apagas, la notificación es privada: con el teléfono bloqueado solo se ve \"Aurora · Reproduciendo\".") {
            Switch(p.lockScreenControls, "Mostrar controles en la pantalla de bloqueo", { v -> state.prefs { it.copy(lockScreenControls = v) } })
        }
    }
    Group("Transiciones") {
        SettingRow(ctx, AuroraIcon.Volume, "Fundido", "Baja el final y sube el inicio", wide = true) {
            ValueSlider(p.fadeSec, 0, 12, 1, "Duración del fundido", { if (it == 0) "No" else "$it s" }) { v -> state.prefs { it.copy(fadeSec = v) } }
        }
        SettingRow(ctx, AuroraIcon.Next, "Sin pausas", "Pasa a la siguiente sin el corte del final", enabled = p.fadeSec == 0,
            help = "Empieza la siguiente canción justo antes de que termine la actual, para no oír el pequeño silencio al cambiar de archivo. Útil en álbumes en vivo o mezclados. Con fundido activado no se usa.") {
            Switch(p.gapless, "Reproducción sin pausas", { v -> state.prefs { it.copy(gapless = v) } }, enabled = p.fadeSec == 0)
        }
    }
    Group("Al abrir y al terminar") {
        SettingRow(ctx, AuroraIcon.Refresh, "Recordar dónde quedé", "Retoma la canción y el segundo") {
            Switch(p.resume, "Recordar dónde quedé", { v -> state.prefs { it.copy(resume = v) } })
        }
        SettingRow(ctx, AuroraIcon.Queue, "Al terminar la cola", "Qué hacer después", wide = true) {
            Segmented(listOf("Detener", "Repetir"), if (p.endOfQueue == EndOfQueue.REPEAT) 1 else 0) { i ->
                state.prefs { it.copy(endOfQueue = if (i == 1) EndOfQueue.REPEAT else EndOfQueue.STOP) }
            }
        }
    }
}

@Composable
private fun LyricsSettingsSection(state: AppState, ctx: SettingsCtx) {
    val p by state.prefsRepo.prefs.collectAsState()
    val auto by state.lyricsRepo.autoDownload.collectAsState()
    var version by remember { mutableStateOf(0) }
    val (count, bytes) = remember(version) { state.lyricsRepo.cacheStats() }
    Header("Letras", "Letra sincronizada, tipo karaoke.")
    Group("Descarga") {
        SettingRow(ctx, AuroraIcon.Download, "Descargar letras automáticamente", "Al empezar cada canción, desde LRCLIB (gratis)",
            help = "Solo se envían el título, el artista, el álbum y la duración. Las letras se guardan en Aurora para usarlas sin conexión y no volver a descargarlas.") {
            Switch(auto, "Descargar letras automáticamente", { state.lyricsRepo.setAutoDownload(it) })
        }
        SettingRow(ctx, AuroraIcon.Lyrics, "Guardar como archivo .lrc", if (auto) "Junto a la canción, con el mismo nombre" else "Necesita la descarga automática", enabled = auto,
            help = "La letra sincronizada se guarda como \"canción.lrc\" en la carpeta de la canción. Así otros reproductores también la usan.") {
            Switch(p.autoSaveLrc, "Guardar como archivo lrc", { v -> state.prefs { it.copy(autoSaveLrc = v) } }, enabled = auto)
        }
        // Se vuelve a mirar al cambiar la opción y tras "Dar permiso" (que avisa con un mensaje).
        val noWrite = remember(p.autoSaveLrc, state.toast) { state.platform.media.foldersWithoutWrite(state.settings.folders.value) }
        if (auto && p.autoSaveLrc && noWrite.isNotEmpty()) {
            SettingRow(ctx, AuroraIcon.Lock, "Permiso para guardar", "Vuelve a elegir tu carpeta de música para permitir guardar ahí",
                help = "Las carpetas elegidas antes solo tenían permiso de lectura. Elige de nuevo la misma carpeta en el selector.") {
                Chip("Dar permiso", false, { state.grantLyricsWrite() })
            }
        }
        SettingRow(ctx, AuroraIcon.Lyrics, "Preferir archivo .lrc", "Si está junto a la canción o dentro del archivo") {
            Switch(p.lrcFirst, "Preferir archivo lrc", { v -> state.prefs { it.copy(lrcFirst = v) } })
        }
    }
    Group("Guardadas") {
        SettingRow(ctx, AuroraIcon.Trash, "Letras guardadas", "$count letras · ${formatSize(bytes)}") {
            Chip("Borrar", false, { state.lyricsRepo.clearCache(); version++; state.show("Letras guardadas borradas") })
        }
    }
}

@Composable
private fun AccessibilitySection(state: AppState, ctx: SettingsCtx) {
    val p by state.prefsRepo.prefs.collectAsState()
    Header("Accesibilidad", "Ajusta la app a tu forma de ver y usar.")
    Group("Lectura") {
        SettingRow(ctx, AuroraIcon.Edit, "Tamaño del texto", "Afecta a toda la app", wide = true) {
            ValueSlider((p.textScale * 100).toInt(), 90, 150, 10, "Tamaño del texto", { "$it %" }) { v -> state.prefs { it.copy(textScale = v / 100f) } }
        }
        Column(Modifier.padding(start = 46.dp, end = 14.dp, bottom = 10.dp).surface().padding(12.dp)) {
            BasicText("Neón en la piel", style = Ui.type.rowTitle)
            BasicText("Cielo Prisma · así se verá el texto", style = Ui.type.rowSubtitle)
        }
        SettingRow(ctx, AuroraIcon.Lyrics, "Letra más grande", "En la vista Reproduciendo") {
            Switch(p.bigLyrics, "Letra más grande", { v -> state.prefs { it.copy(bigLyrics = v) } })
        }
        SettingRow(ctx, AuroraIcon.Sparkle, "Alto contraste", "Textos y bordes más marcados") {
            Switch(p.highContrast, "Alto contraste", { v -> state.prefs { it.copy(highContrast = v) } })
        }
    }
    Group("Movimiento") {
        SettingRow(ctx, AuroraIcon.Refresh, "Reducir movimiento", "Quita luces animadas y transiciones",
            help = "Detiene las luces del fondo, la respiración de la portada y los desplazamientos suaves de la letra.") {
            Switch(p.reduceMotion, "Reducir movimiento", { v -> state.prefs { it.copy(reduceMotion = v) } })
        }
    }
    Group("Navegación") {
        SettingRow(ctx, AuroraIcon.Check, "Foco reforzado", "Contorno grueso al usar teclado") {
            Switch(p.strongFocus, "Foco reforzado", { v -> state.prefs { it.copy(strongFocus = v) } })
        }
        SettingRow(ctx, AuroraIcon.Volume, "Anunciar canciones", "Para lectores de pantalla") {
            Switch(p.announce, "Anunciar cambios de canción", { v -> state.prefs { it.copy(announce = v) } })
        }
        SettingRow(ctx, AuroraIcon.Info, "Atajos de teclado", "Funcionan en toda la app") {}
        val keys = listOf(
            "Reproducir o pausar" to "Espacio", "Avanzar / retroceder 5 s" to "→ ←", "Siguiente / anterior" to "Ctrl → ←",
            "Letra" to "L", "Video" to "V", "Pantalla completa" to "F", "Ajustes" to "Ctrl ,", "Buscar" to "Ctrl K",
        )
        Column(Modifier.padding(start = 46.dp, end = 14.dp, bottom = 12.dp)) {
            keys.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    row.forEach { (what, key) ->
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            BasicText(what, style = Ui.type.body, modifier = Modifier.weight(1f))
                            BasicText(key, style = Ui.type.caption.copy(color = Ui.colors.ink, fontWeight = FontWeight.SemiBold),
                                modifier = Modifier.padding(end = 16.dp).border(1.dp, Ui.colors.surfaceBorder, RoundedCornerShape(5.dp)).padding(horizontal = 7.dp, vertical = 2.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutSection(state: AppState) {
    var licenses by remember { mutableStateOf(false) }
    val c = Ui.colors
    Header("Acerca de", "Información de la app.")
    Column(Modifier.padding(top = 16.dp).fillMaxWidth().surface().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(Ui.shapes.artSmall).background(c.accent), contentAlignment = Alignment.Center) { Icon(AuroraIcon.Music, c.onAccent) }
            Column(Modifier.padding(start = 12.dp)) {
                BasicText("Aurora", style = Ui.type.h2.copy(color = c.ink))
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                    listOf("Versión 0.3", "GPL-3.0", state.platform.name).forEach { t ->
                        BasicText(t, style = Ui.type.caption.copy(color = c.ink), maxLines = 1, softWrap = false, modifier = Modifier.border(1.dp, c.surfaceBorder, RoundedCornerShape(20.dp)).padding(horizontal = 9.dp, vertical = 2.dp))
                    }
                }
            }
        }
        androidx.compose.foundation.layout.FlowRow(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Código fuente", false, { state.show("El repositorio público se publicará pronto") }, icon = AuroraIcon.Music)
            Chip("Reportar un problema", false, { state.show("Los reportes se harán en el repositorio público") }, icon = AuroraIcon.Info)
            Chip("Licencias", licenses, { licenses = !licenses }, icon = AuroraIcon.Lyrics)
            // Solo en compilaciones de depuración, para probar la configuración inicial desde cero.
            if (state.platform.isDebug) Chip("Repetir configuración inicial", false, { state.restartOnboarding() }, icon = AuroraIcon.Refresh)
        }
        if (licenses) {
            Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "Aurora" to "GPL-3.0",
                    "VLC / libVLC" to "LGPL-2.1 (vlcj: GPL-3.0)",
                    "jaudiotagger" to "LGPL-2.1",
                    "Kotlin, Compose Multiplatform, kotlinx" to "Apache-2.0",
                    "Letras: LRCLIB" to "Servicio libre y gratuito",
                    "Fuentes de los temas (Syne, DM Sans, Archivo, Fraunces, Nunito, Cormorant, Jost, Chakra Petch, Inter, Bebas Neue, Barlow, Manrope)" to "SIL Open Font License",
                ).forEach { (what, lic) ->
                    Row { BasicText(what, style = Ui.type.body, modifier = Modifier.weight(1f)); BasicText(lic, style = Ui.type.caption.copy(color = c.ink)) }
                }
            }
        }
    }
}

/** Vista previa de un tema dibujada con sus propios colores, formas y fuentes. */
/** Alto de cada tarjeta del selector de tema: igual para todas, en cualquier pantalla y tamaño de texto. */
val ThemeCardHeight = 104.dp

/**
 * Tarjeta del selector de tema (Ajustes › Apariencia y bienvenida). Todas miden lo mismo y ponen todo en
 * la misma posición: cada tema usa su fuente, pero con tamaño y alto de línea fijos (Fraunces, Cormorant y
 * Bebas Neue tienen alturas muy distintas) y sin el relleno extra de la fuente. Nombre y descripción en
 * 1 línea, con "…".
 */
@Composable
internal fun ThemePreview(theme: AppTheme, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val outer = Ui.colors
    // Todas las tarjetas usan la forma de la app (la del tema se ve en la portada de muestra).
    val cardShape = Ui.shapes.card
    val sample = SampleData.tracks.first()
    val palette = remember { buildPalette(sample.bpm, null) }
    // El tamaño del texto de la app (Accesibilidad) no debe cambiar el alto de la tarjeta.
    val fixed = androidx.compose.ui.unit.Density(androidx.compose.ui.platform.LocalDensity.current.density, 1f)
    val trim = androidx.compose.ui.text.style.LineHeightStyle(
        androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center, androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
    )
    Box(
        modifier
            .height(ThemeCardHeight)
            // Grupo de opciones: el lector de pantalla dice "Aurora, elegido, opción 1 de 7".
            .semantics { this.selected = selected; role = androidx.compose.ui.semantics.Role.RadioButton }
            .pressable("${theme.displayName}. ${theme.description}", .97f, onClick = onClick)
            .clip(cardShape)
            .border(if (selected) 2.5.dp else 1.dp, if (selected) outer.accent else outer.surfaceBorder, cardShape),
    ) {
        AppThemeProvider(theme, palette, Ui.formFactor) {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides fixed) {
                val c = Ui.colors
                Column(Modifier.padding(3.dp).fillMaxSize().clip(cardShape).background(c.background).padding(horizontal = 12.dp, vertical = 8.dp)) {
                    // 104 dp = 6 de borde + 16 de margen + 30 portada + 6 + 22 nombre + 2 + 16 (una línea) + 6 de aire.
                    Row(Modifier.height(30.dp), verticalAlignment = Alignment.CenterVertically) {
                        Artwork(sample, 30.dp)
                        Spacer(Modifier.width(10.dp))
                        listOf(c.accent, c.ink, c.surfaceBorder).forEach { col ->
                            Box(Modifier.padding(end = 4.dp).size(12.dp).background(col, Ui.shapes.chip))
                        }
                        Spacer(Modifier.weight(1f))
                        if (selected) Box(Modifier.size(22.dp).background(c.accent, Ui.shapes.playButton), contentAlignment = Alignment.Center) {
                            Icon(AuroraIcon.Check, c.onAccent, size = 15.dp)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    BasicText(
                        theme.title(theme.displayName),
                        style = Ui.type.h2.copy(color = c.ink, fontSize = 17.sp, lineHeight = 22.sp, lineHeightStyle = trim, letterSpacing = 0.sp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.height(22.dp),
                    )
                    BasicText(
                        theme.description,
                        style = Ui.type.rowSubtitle.copy(fontSize = 12.sp, lineHeight = 16.sp, lineHeightStyle = trim),
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

/** Relleno estándar de pantallas móviles con espacio para mini reproductor y pestañas. */
fun mobilePadding(bottom: Dp) = PaddingValues(start = AuroraDimens.ScreenPadding, end = AuroraDimens.ScreenPadding, top = 20.dp, bottom = bottom)
