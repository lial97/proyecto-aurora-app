package app.aurora

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.unit.Density
import app.aurora.data.SettingsRepository
import app.aurora.domain.MediaType
import app.aurora.platform.DesktopMediaSource
import app.aurora.platform.JavaHttp
import app.aurora.platform.MemoryStore
import app.aurora.platform.PlatformServices
import app.aurora.platform.VideoThumbnailer
import app.aurora.player.VlcEngine
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import kotlin.test.Test

/**
 * Capturas del README con una biblioteca real, VLC de verdad (en silencio) y la ventana de escritorio de 1280×800.
 * No toca la configuración del usuario: los ajustes van en memoria. Solo corre con estas variables:
 *   AURORA_README=/ruta/salida AURORA_MUSIC=~/Music AURORA_VIDEOS=~/Videos
 *   AURORA_VLC_DIR=<carpeta "resources" con el VLC de la app> (opcional; si no, el VLC del sistema)
 *   ./gradlew :composeApp:desktopTest --tests app.aurora.ReadmeShotsTest
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class ReadmeShotsTest {
    private fun click(scene: ImageComposeScene, x: Float, y: Float) {
        val p = androidx.compose.ui.geometry.Offset(x, y)
        scene.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Press, p,
            buttons = androidx.compose.ui.input.pointer.PointerButtons(isPrimaryPressed = true), button = androidx.compose.ui.input.pointer.PointerButton.Primary)
        scene.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Release, p,
            buttons = androidx.compose.ui.input.pointer.PointerButtons(), button = androidx.compose.ui.input.pointer.PointerButton.Primary)
    }

    /** Toca el elemento cuya descripción de accesibilidad es [label]. */
    private fun tapLabel(scene: ImageComposeScene, label: String, tap: (Float, Float) -> Unit) {
        val node = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = false) }
            .lastOrNull { label in (it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription) ?: emptyList()) }
            ?: error("No se encontró \"$label\"")
        val c = node.boundsInRoot.center
        tap(c.x, c.y)
    }

    @Test fun shots() {
        val out = System.getenv("AURORA_README")?.let { File(it).apply { mkdirs() } } ?: return
        val music = System.getenv("AURORA_MUSIC") ?: return
        val videos = System.getenv("AURORA_VIDEOS").orEmpty()
        System.getenv("AURORA_VLC_DIR")?.let { System.setProperty("compose.application.resources.dir", it) }
        val store = MemoryStore().apply {
            put(SettingsRepository.KEY_ONBOARDING, "1"); put(SettingsRepository.KEY_FOLDERS, music); put("carpetas_video", videos)
        }
        val platform = PlatformServices(
            "Linux", true, store, DesktopMediaSource(VideoThumbnailer(File(out, ".miniaturas"))), VlcEngine(), http = JavaHttp(),
        )
        var st: AppState? = null
        val scene = ImageComposeScene(1280, 800, Density(1f)) { App(platform) { st = it } }
        var t = 0L
        // Tiempo real: VLC, las portadas y la letra llegan por su cuenta.
        fun wait(ms: Long) { val end = System.currentTimeMillis() + ms; while (System.currentTimeMillis() < end) { scene.render(t); t += 33_000_000; Thread.sleep(33) } }
        fun snap(name: String) = File(out, "$name.png").writeBytes(scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        wait(1500)
        val s = st!!
        s.player.setVolume(0f)
        val deadline = System.currentTimeMillis() + 120_000
        while (!s.library.state.value.scannedOnce && System.currentTimeMillis() < deadline) wait(500)
        val songs = s.library.state.value.tracks.filter { it.mediaType == MediaType.AUDIO }
        val first = songs.indexOfFirst { it.title.startsWith("Abrázame") }.coerceAtLeast(0)
        s.play(songs, first); wait(2500); s.player.seekTo(39); wait(6000)
        snap("escritorio-inicio")

        val vids = s.library.state.value.tracks.filter { it.mediaType == MediaType.VIDEO }
        if (vids.isNotEmpty()) {
            s.play(vids, vids.indexOfFirst { it.title.contains("BASH") }.coerceAtLeast(0)); wait(4000)
            s.player.seekTo(607); wait(6000)
            snap("escritorio-video")
            if (System.getenv("AURORA_EXTRA") == "1") {
                // Elegir "Canción" con un video: la vista de audio con el selector Canción | Video.
                tapLabel(scene, "Canción") { x, y -> click(scene, x, y) }; wait(1500)
                snap("extra-1-cancion-con-video")
                // Salir a Inicio y volver a entrar (tecla L): debe abrir la vista de video.
                tapLabel(scene, "Inicio") { x, y -> click(scene, x, y) }; wait(1500)
                snap("extra-2-inicio")
                scene.sendKeyEvent(androidx.compose.ui.input.key.KeyEvent(androidx.compose.ui.input.key.Key.L, androidx.compose.ui.input.key.KeyEventType.KeyDown))
                scene.sendKeyEvent(androidx.compose.ui.input.key.KeyEvent(androidx.compose.ui.input.key.Key.L, androidx.compose.ui.input.key.KeyEventType.KeyUp))
                wait(2000)
                snap("extra-3-volver-a-entrar")
            }
        }
        s.player.pauseIfPlaying()
        scene.close()
    }
}
