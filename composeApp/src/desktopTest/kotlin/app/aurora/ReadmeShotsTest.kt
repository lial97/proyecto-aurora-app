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
        }
        s.player.pauseIfPlaying()
        scene.close()
    }
}
