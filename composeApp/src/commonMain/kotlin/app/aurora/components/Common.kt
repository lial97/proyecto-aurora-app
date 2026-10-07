package app.aurora.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.Image
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.aurora.data.LibraryState
import app.aurora.domain.Track
import app.aurora.theme.Ui
import app.aurora.theme.title
import app.aurora.theme.label

/** Título grande de pantalla con las mayúsculas/minúsculas del tema. */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier, style: TextStyle = Ui.type.h1) {
    BasicText(Ui.theme.title(text), style = style, modifier = modifier)
}

/** Abre el selector de carpetas de música (lo da App.kt; en pruebas no hace nada). */
val LocalPickFolders = androidx.compose.runtime.staticCompositionLocalOf<() -> Unit> { {} }

/** Aviso de la biblioteca: buscando, sin carpetas, catálogo de ejemplo o error. Nada si no hay que avisar. */
@Composable
fun LibraryBanner(lib: LibraryState, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    if (lib.noFolders && !lib.scanning) { NoFoldersState(modifier); return }
    val (title, detail) = when {
        lib.scanning -> "Buscando tu música…" to (lib.progress?.let { p ->
            if (p.total != null && p.total > 0) "${p.done} de ${p.total} archivos" else "Revisando tus carpetas"
        } ?: "")
        lib.error != null -> "No se pudo leer tu música" to lib.error
        lib.usingSamples -> "Estás viendo canciones de ejemplo" to "Añade las carpetas donde guardas tu música y tus videos."
        else -> return
    }
    Row(
        modifier.fillMaxWidth().padding(top = 14.dp).surface().padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (lib.scanning) AuroraIcon.Refresh else AuroraIcon.Folder, Ui.colors.accent)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            BasicText(title, style = Ui.type.rowTitle)
            BasicText(detail, style = Ui.type.rowSubtitle)
        }
        if (!lib.scanning) Chip("Ajustes", selected = true, onClick = onOpenSettings, icon = AuroraIcon.Settings)
    }
}

/** Sin carpetas elegidas ("Lo haré después" en la configuración inicial). */
@Composable
private fun NoFoldersState(modifier: Modifier) {
    val c = Ui.colors
    val pick = LocalPickFolders.current
    Column(modifier.fillMaxWidth().padding(top = 14.dp).surface().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(AuroraIcon.Folder, c.accent, size = 32.dp)
        BasicText("Aún no hay música", style = Ui.type.rowTitle, modifier = Modifier.padding(top = 10.dp))
        BasicText(
            "Elige las carpetas donde guardas tus canciones. Aurora solo lee las que elijas.",
            style = Ui.type.rowSubtitle.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center), modifier = Modifier.padding(top = 4.dp),
        )
        Chip("Elegir carpetas de música", selected = true, onClick = pick, modifier = Modifier.padding(top = 14.dp), icon = AuroraIcon.Plus)
    }
}

/**
 * Miniatura 16:9 de video (un fotograma del propio video). Al pasar el cursor muestra la vista previa:
 * recorre varios momentos del video con una barra de segmentos arriba.
 */
@Composable
fun VideoThumb(track: Track, modifier: Modifier = Modifier, preview: Boolean = true, small: Boolean = false) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val frames = rememberPreviewFrames(track, preview && hovered)
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(hovered, frames.size) {
        index = 0
        if (hovered && frames.size > 1) while (true) { delay(850); index = (index + 1) % frames.size }
    }
    Box(modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(if (small) Ui.shapes.artSmall else Ui.shapes.card).background(Color.Black).hoverable(hover)) {
        val frame = frames.getOrNull(index)
        if (hovered && frame != null) {
            Image(frame, "Vista previa de ${track.title}", Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                frames.indices.forEach { i ->
                    Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp))
                        .background(if (i <= index) Color.White else Color.White.copy(alpha = .35f)))
                }
            }
        } else {
            Artwork(track, 1000.dp, Modifier.matchParentSize(), shape = androidx.compose.ui.graphics.RectangleShape, border = false)
        }
        if (!small) {
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = .55f))))
            Box(Modifier.align(Alignment.BottomStart).padding(8.dp)) { Icon(AuroraIcon.Play, Color.White, size = 18.dp) }
        }
        if (track.durationSec > 0) {
            BasicText(
                formatTime(track.durationSec),
                style = Ui.type.caption.copy(color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = if (small) 9.sp else 12.sp),
                modifier = Modifier.align(Alignment.BottomEnd).padding(if (small) 3.dp else 6.dp)
                    .background(Color.Black.copy(alpha = .6f), RoundedCornerShape(5.dp)).padding(horizontal = if (small) 3.dp else 6.dp, vertical = 1.dp),
            )
        }
    }
}

/** Tira de fotogramas: varios momentos del video para saber de qué trata (útil en móvil, sin cursor). */
@Composable
fun VideoStoryboard(track: Track, modifier: Modifier = Modifier) {
    val frames = rememberPreviewFrames(track, true)
    if (frames.size < 2) return
    Row(modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        frames.forEach { f ->
            Image(f, null, Modifier.weight(1f).aspectRatio(16f / 9f).clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.Crop)
        }
    }
}

/** Tarjeta de video con título, artista, opciones y (opcional) tira de vista previa. */
@Composable
fun VideoCard(track: Track, onClick: () -> Unit, modifier: Modifier = Modifier, onMore: (() -> Unit)? = null, storyboard: Boolean = false) {
    Column(modifier.trackContextMenu(track)) {
        Box(Modifier.pressable(track.title, .97f, onClick = onClick)) { VideoThumb(track) }
        if (storyboard) VideoStoryboard(track)
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).pressable(track.title, .99f, onClick = onClick)) {
                BasicText(track.title, style = Ui.type.rowTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                BasicText(track.artist, style = Ui.type.rowSubtitle, maxLines = 1)
            }
            if (onMore != null) IconButton(AuroraIcon.More, "Opciones de ${track.title}", onMore, tint = Ui.colors.mute, size = 30.dp, iconSize = 18.dp)
        }
    }
}

/** Etiqueta "VIDEO" para distinguir videos dentro de listas mixtas. */
@Composable
fun VideoBadge(modifier: Modifier = Modifier) {
    BasicText(
        Ui.theme.label("Video"),
        style = Ui.type.caption.copy(color = Ui.colors.onAccent, fontWeight = FontWeight.Bold, fontSize = 9.5.sp),
        modifier = modifier.clip(RoundedCornerShape(3.dp)).background(Ui.colors.accent).padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/** Miniatura de una pista en filas: portada cuadrada para canciones y 16:9 para videos. */
@Composable
fun RowArt(track: Track, size: androidx.compose.ui.unit.Dp) {
    if (track.mediaType == app.aurora.domain.MediaType.VIDEO) {
        Box(Modifier.width(size * 1.5f)) { VideoThumb(track, small = true) }
    } else Artwork(track, size)
}

/** Tarjeta cuadrada de canción o álbum. */
@Composable
fun CoverCard(
    track: Track, title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp,
    /** La tarjeta representa esta pista (no un álbum o artista): permite clic derecho. */
    menuTrack: Track? = null,
) {
    Column(modifier.trackContextMenu(menuTrack).pressable(title, .96f, onClick = onClick)) {
        Artwork(track, size, height = if (Ui.theme.id == app.aurora.theme.ThemeId.PETALO) size * 1.14f else size)
        BasicText(title, style = Ui.type.rowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        BasicText(subtitle, style = Ui.type.rowSubtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Mosaico de mezcla con sus 3 colores. */
@Composable
fun MixSwatch(colors: List<Long>, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(Ui.shapes.artSmall).background(Brush.linearGradient(colors.map { Color(it) })))
}

/** Punto de color pequeño. */
@Composable
fun Dot(color: Color, modifier: Modifier = Modifier) = Box(modifier.size(7.dp).background(color, CircleShape))

@Composable
fun EmptyState(title: String, detail: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(AuroraIcon.Music, Ui.colors.mute, size = 36.dp)
        BasicText(title, style = Ui.type.rowTitle, modifier = Modifier.padding(top = 12.dp))
        BasicText(detail, style = Ui.type.rowSubtitle.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center), modifier = Modifier.padding(top = 4.dp))
    }
}

/** Cuántos campos de texto tienen el foco: mientras se escribe, los atajos de teclado se ignoran. */
object TypingTracker {
    var focused by androidx.compose.runtime.mutableIntStateOf(0)
        private set

    fun changed(focusedNow: Boolean, wasFocused: Boolean) {
        if (focusedNow && !wasFocused) focused++
        if (!focusedNow && wasFocused) focused = maxOf(0, focused - 1)
    }
}

/** Marca un campo de texto para que los atajos (Espacio, flechas, L, V, F) no interfieran al escribir. */
@Composable
fun Modifier.typingField(): Modifier {
    val was = androidx.compose.runtime.remember { booleanArrayOf(false) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { if (was[0]) TypingTracker.changed(false, true) } }
    return this.onFocusChanged { st ->
        TypingTracker.changed(st.isFocused, was[0]); was[0] = st.isFocused
    }
}

/**
 * Saludo de Inicio en una sola línea ("Buenas noches, Lila"). Si no cabe, achica la letra
 * hasta un 60 % y después corta con "…", sin empujar lo demás. Tocarlo permite cambiar el nombre.
 */
@Composable
fun GreetingTitle(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, style: TextStyle = Ui.type.h1) {
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val shown = Ui.theme.title(text)
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.pressable("Cambiar tu nombre", .98f, onClick = onClick)) {
        val width = constraints.maxWidth
        val fitted = remember(shown, style, width) {
            listOf(1f, .9f, .8f, .7f, .6f).map { k -> style.copy(fontSize = style.fontSize * k) }
                .firstOrNull { s -> measurer.measure(shown, s, maxLines = 1, softWrap = false).size.width <= width }
                ?: style.copy(fontSize = style.fontSize * .6f)
        }
        BasicText(shown, style = fitted, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

/**
 * Texto en una sola línea que se achica (hasta [minScale]) para caber en su espacio y, si aun así
 * no cabe, termina en "…". Para etiquetas cortas como las de las pestañas.
 */
@Composable
fun FitText(text: String, style: TextStyle, modifier: Modifier = Modifier, minScale: Float = .7f) {
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    androidx.compose.foundation.layout.BoxWithConstraints(modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
        val width = constraints.maxWidth
        val fitted = remember(text, style, width) {
            generateSequence(1f) { it - .05f }.takeWhile { it >= minScale }.map { k -> style.copy(fontSize = style.fontSize * k) }
                .firstOrNull { s -> measurer.measure(text, s, maxLines = 1, softWrap = false).size.width <= width }
                ?: style.copy(fontSize = style.fontSize * minScale)
        }
        BasicText(text, style = fitted, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}
