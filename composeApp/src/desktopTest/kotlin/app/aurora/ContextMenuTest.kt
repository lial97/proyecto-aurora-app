package app.aurora

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import app.aurora.platform.MediaSource
import app.aurora.platform.MemoryStore
import app.aurora.platform.PlatformServices
import app.aurora.platform.ScanProgress
import app.aurora.domain.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Menú contextual: clic derecho sobre otra pista lo vuelve a abrir allí; clic izquierdo fuera o Esc lo cierran. */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class ContextMenuTest {
    private object Empty : MediaSource {
        override fun defaultFolders() = emptyList<String>()
        override val canPickFolder = false
        override suspend fun pickFolder(): String? = null
        override suspend fun candidateFolders() = emptyList<String>()
        override suspend fun scan(folders: List<String>, onProgress: (ScanProgress) -> Unit) = emptyList<Track>()
        override suspend fun loadCover(track: Track) = null
    }

    @Test
    fun rightClickElsewhereReopensMenu() {
        var st: AppState? = null
        val scene = ImageComposeScene(1280, 800, Density(1f)) { App(PlatformServices("Prueba", true, MemoryStore().apply { put("bienvenida_lista", "1") }, Empty, null)) { st = it } }
        var t = 0L
        fun frames(n: Int = 20) = repeat(n) { scene.render(t); t += 16_000_000; Thread.sleep(5) }
        fun right(x: Float, y: Float) {
            scene.sendPointerEvent(PointerEventType.Press, Offset(x, y), buttons = PointerButtons(isSecondaryPressed = true), button = PointerButton.Secondary); frames(4)
            scene.sendPointerEvent(PointerEventType.Release, Offset(x, y), buttons = PointerButtons(), button = PointerButton.Secondary); frames()
        }
        fun left(x: Float, y: Float) {
            scene.sendPointerEvent(PointerEventType.Press, Offset(x, y), buttons = PointerButtons(isPrimaryPressed = true), button = PointerButton.Primary); frames(4)
            scene.sendPointerEvent(PointerEventType.Release, Offset(x, y), buttons = PointerButtons(), button = PointerButton.Primary); frames()
        }
        fun menuTrack() = (st!!.dialog as? AppDialog.TrackMenu)?.track?.title
        frames(60)
        // Tarjetas de "Sigue escuchando" (5 columnas): primera y cuarta.
        right(320f, 390f)
        val first = menuTrack()
        assertTrue(first != null, "el primer clic derecho debe abrir el menú")
        right(745f, 390f)
        val second = menuTrack()
        assertTrue(second != null, "un clic derecho sobre otra canción debe abrir su menú")
        assertNotEquals(first, second)
        // Clic izquierdo fuera: cierra y no reproduce nada.
        left(700f, 120f)
        assertNull(st!!.dialog)
        assertNull(st!!.player.state.value.current, "el clic que cierra el menú no debe activar lo de abajo")
        // Esc también cierra.
        right(320f, 390f)
        assertEquals(first, menuTrack())
        scene.sendKeyEvent(KeyEvent(Key.Escape, KeyEventType.KeyDown)); frames()
        assertNull(st!!.dialog)
        scene.close()
    }
}
