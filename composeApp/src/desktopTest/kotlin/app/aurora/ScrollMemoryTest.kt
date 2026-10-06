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
import app.aurora.domain.MediaType
import app.aurora.domain.Track
import app.aurora.platform.MediaSource
import app.aurora.platform.MemoryStore
import app.aurora.platform.PlatformServices
import app.aurora.platform.ScanProgress
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Móvil: la lista vuelve a la misma canción tras abrir el reproductor, cambiar de pestaña o de categoría. */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class ScrollMemoryTest {
    private object Big : MediaSource {
        override fun defaultFolders() = listOf("/musica")
        override val canPickFolder = false
        override suspend fun pickFolder(): String? = null
        override suspend fun candidateFolders() = emptyList<String>()
        override suspend fun scan(folders: List<String>, onProgress: (ScanProgress) -> Unit) = (1..2000).map { i ->
            Track("t$i", "Canción %04d".format(i), "Artista ${i % 37}", "Álbum ${i % 151}", 200, MediaType.AUDIO, year = 1980 + i % 40)
        }
        override suspend fun loadCover(track: Track) = null
    }

    private val out: File? = System.getProperty("aurora.shots")?.let { File(it).apply { mkdirs() } }

    @Test
    fun listKeepsPosition() {
        val d = 2f
        var st: AppState? = null
        val store = MemoryStore().apply { put("bienvenida_lista", "1"); put("carpetas", "/musica"); put("carpetas_video", "") }
        val scene = ImageComposeScene((400 * d).toInt(), (800 * d).toInt(), Density(d)) { App(PlatformServices("Prueba", false, store, Big, null)) { st = it } }
        var t = 0L
        fun frames(n: Int = 20) = repeat(n) { scene.render(t); t += 16_000_000; Thread.sleep(3) }
        fun tap(x: Float, y: Float) {
            scene.sendPointerEvent(PointerEventType.Press, Offset(x * d, y * d), buttons = PointerButtons(isPrimaryPressed = true), button = PointerButton.Primary); frames(3)
            scene.sendPointerEvent(PointerEventType.Release, Offset(x * d, y * d), buttons = PointerButtons(), button = PointerButton.Primary); frames(50)
        }
        fun tab(i: Int) = tap(40f + 80f * i, 768f)
        fun snap(name: String): Bitmap {
            // El cursor fuera de la lista, para que ninguna fila quede resaltada.
            scene.sendPointerEvent(PointerEventType.Move, Offset(-50f, -50f)); scene.sendPointerEvent(PointerEventType.Exit, Offset(-50f, -50f))
            frames(20); val img = scene.render(t)
            out?.let { File(it, "$name.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes) }
            return Bitmap.makeFromImage(img)
        }
        /** Zona de la lista (sin barra superior, mini reproductor ni pestañas). */
        fun same(a: Bitmap, b: Bitmap): Boolean {
            var diff = 0; var n = 0
            for (y in (260 * d).toInt() until (600 * d).toInt() step 5) for (x in 0 until a.width step 5) { n++; if (a.getColor(x, y) != b.getColor(x, y)) diff++ }
            return diff < n / 200
        }
        frames(80)
        val s = st!!
        s.prefsRepo.update { it.copy(backgroundLights = false) }
        tab(3)
        s.play(s.library.state.value.tracks, 0); s.player.togglePlay(); frames(30)
        repeat(40) { scene.sendPointerEvent(PointerEventType.Scroll, Offset(200f * d, 400f * d), scrollDelta = Offset(0f, 100f)); frames(1) }
        val before = snap("1-antes")

        tap(150f, 700f); snap("2-reproductor")
        tap(38f, 28f); frames(60)             // flecha "Cerrar reproductor"
        assertTrue(same(before, snap("3-tras-reproductor")), "tras cerrar el reproductor la lista debe seguir en la misma canción")

        tab(0); tab(3)
        assertTrue(same(before, snap("4-tras-pestanas")), "al volver a Biblioteca la lista debe seguir en la misma canción")

        // Otra categoría y de vuelta: Canciones conserva su lugar.
        tap(200f, 30f); snap("5-albumes")
        tap(70f, 87f)
        assertTrue(same(before, snap("6-canciones-otra-vez")), "al volver a Canciones la lista debe seguir en la misma canción")
        scene.close()
    }
}
