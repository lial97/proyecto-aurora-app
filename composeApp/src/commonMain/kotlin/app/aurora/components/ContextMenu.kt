package app.aurora.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import app.aurora.domain.Playlist
import app.aurora.domain.Track

/** Abre el menú de una pista: (pista, lista donde está, punto de la ventana). Lo provee la app. */
val LocalTrackMenu = staticCompositionLocalOf<((Track, Playlist?, Offset?) -> Unit)?> { null }

/**
 * Clic derecho sobre una canción o video: abre el menú contextual en la posición del cursor
 * (añadir a una lista, Me gusta, ver detalles…). [playlist] = la lista del usuario donde aparece, si aplica.
 */
@Composable
fun Modifier.trackContextMenu(track: Track?, playlist: Playlist? = null): Modifier {
    val open = LocalTrackMenu.current ?: return this
    if (track == null) return this
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    return this
        .onGloballyPositioned { coords = it }
        .pointerInput(track.id, playlist?.id) {
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent(PointerEventPass.Main)
                    if (e.type == PointerEventType.Press && e.buttons.isSecondaryPressed) {
                        val local = e.changes.first().position
                        val root = coords?.takeIf { it.isAttached }?.localToRoot(local)
                        e.changes.forEach { it.consume() }
                        open(track, playlist, root)
                    }
                }
            }
        }
}

/** Coloca [content] en [anchor] y lo empuja hacia dentro si se saldría de la ventana. */
@Composable
fun AnchoredLayout(anchor: Offset, content: @Composable () -> Unit) {
    Layout(content) { measurables, constraints ->
        val p = measurables.first().measure(Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight))
        val margin = 8.dp.roundToPx()
        val x = anchor.x.toInt().coerceAtMost(constraints.maxWidth - p.width - margin).coerceAtLeast(margin)
        // Si no cabe hacia abajo, se abre hacia arriba del cursor.
        val below = anchor.y.toInt()
        val y = (if (below + p.height + margin > constraints.maxHeight) below - p.height else below)
            .coerceAtLeast(margin).coerceAtMost((constraints.maxHeight - p.height - margin).coerceAtLeast(margin))
        layout(constraints.maxWidth, constraints.maxHeight) { p.place(x, y) }
    }
}
