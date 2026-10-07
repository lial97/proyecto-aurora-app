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
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import app.aurora.data.SettingsRepository
import app.aurora.domain.Track
import app.aurora.platform.MediaSource
import app.aurora.platform.MemoryStore
import app.aurora.platform.PlatformServices
import app.aurora.platform.ScanProgress
import app.aurora.theme.Themes
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * Capturas de Inicio, Reproductor y Letra de cada tema, en móvil (400×860) y escritorio (1280×800), con las
 * canciones de ejemplo. Solo corre con AURORA_SHOTS=/ruta (y opcionalmente AURORA_THEMES=GRAFITO,BOSQUE):
 *   AURORA_SHOTS=/tmp/temas ./gradlew :composeApp:desktopTest --tests app.aurora.ThemeShotsTest
 * Sirve para revisar los temas y para comparar píxel a píxel antes y después de un cambio.
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class ThemeShotsTest {
    /** Una carpeta vacía: la app muestra las canciones de ejemplo (con portadas dibujadas y letra). */
    private object Empty : MediaSource {
        override fun defaultFolders() = listOf("/musica")
        override val canPickFolder = false
        override suspend fun pickFolder(): String? = null
        override suspend fun candidateFolders() = emptyList<String>()
        override suspend fun scan(folders: List<String>, onProgress: (ScanProgress) -> Unit) = emptyList<Track>()
        override suspend fun loadCover(track: Track) = null
    }

    private val out: File? = System.getProperty("aurora.shots")?.let { File(it).apply { mkdirs() } }
    private val only: Set<String>? = System.getenv("AURORA_THEMES")?.split(',')?.map { it.trim().uppercase() }?.toSet()

    @Test fun shots() {
        val dir = out ?: return
        Themes.all.filter { only == null || it.id.name in only }.forEach { theme ->
            // Compose de escritorio falla a veces al cerrar una escena ("LayoutNode not found in RectList"): se reintenta.
            retry { mobile(theme.id.name, dir) }
            retry { desktop(theme.id.name, dir) }
        }
    }

    /** Selector de temas con todos los temas: paso "Elige tu estilo" (móvil y escritorio) y Ajustes › Apariencia. */
    @Test fun selector() {
        val dir = out ?: return
        retry { selectorScene(dir, mobile = true) }
        retry { selectorScene(dir, mobile = false) }
    }

    private fun selectorScene(dir: File, mobile: Boolean) {
        val d = if (mobile) 2f else 1f
        val (w, h) = if (mobile) 400 to 860 else 1000 to 700
        val store = MemoryStore().apply {
            put(SettingsRepository.KEY_ONBOARDING, "0"); put(SettingsRepository.KEY_STEP, "4"); put("carpetas", "/musica"); put("carpetas_video", "")
        }
        val scene = ImageComposeScene((w * d).toInt(), (h * d).toInt(), Density(d)) { App(PlatformServices("Prueba", !mobile, store, Empty, null)) }
        var t = 0L
        fun frames(n: Int = 30) = repeat(n) { scene.render(t); t += 16_000_000; Thread.sleep(2) }
        val tag = if (mobile) "movil" else "escritorio"
        fun snap(name: String) { frames(40); File(dir, "selector-$tag-$name.png").writeBytes(scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes) }
        frames(80)
        snap("1-estilo")
        // Hasta el final de la grilla: debe poder desplazarse si no cabe.
        repeat(30) { scene.sendPointerEvent(PointerEventType.Scroll, Offset(w * d / 2, h * d / 2), scrollDelta = Offset(0f, 3f)); frames(1) }
        snap("2-estilo-abajo")
        scene.close()
    }

    private fun retry(block: () -> Unit) {
        repeat(2) { runCatching(block).onSuccess { return } }
        block()
    }

    private fun store(theme: String) = MemoryStore().apply {
        put(SettingsRepository.KEY_THEME, theme); put("bienvenida_lista", "1"); put("carpetas", "/musica"); put("carpetas_video", "")
    }

    private fun mobile(theme: String, dir: File) {
        val d = 2f
        var st: AppState? = null
        val scene = ImageComposeScene((400 * d).toInt(), (860 * d).toInt(), Density(d)) {
            App(PlatformServices("Prueba", false, store(theme), Empty, null)) { st = it }
        }
        var t = 0L
        fun frames(n: Int = 30) = repeat(n) { scene.render(t); t += 16_000_000; Thread.sleep(2) }
        fun tap(x: Float, y: Float) {
            scene.sendPointerEvent(PointerEventType.Press, Offset(x * d, y * d), buttons = PointerButtons(isPrimaryPressed = true), button = PointerButton.Primary); frames(3)
            scene.sendPointerEvent(PointerEventType.Release, Offset(x * d, y * d), buttons = PointerButtons(), button = PointerButton.Primary); frames(60)
        }
        fun snap(name: String) {
            frames(40)
            File(dir, "$theme-movil-$name.png").writeBytes(scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
        frames(80)
        val s = st!!
        val tracks = s.library.state.value.tracks
        s.play(tracks, 0); s.player.togglePlay(); s.player.seekTo(30); frames(30)
        snap("1-inicio")
        s.requestPlayer(); frames(60)
        snap("2-reproductor")
        tapLabel(scene, "Letra") { x, y -> tap(x / d, y / d) }
        snap("3-letra")
        // Con la música sonando 6 s (medio ciclo de la gota de Acuarela; carretes y vinilo giran).
        tapLabel(scene, "Cerrar panel") { x, y -> tap(x / d, y / d) }
        s.player.togglePlay(); frames(375)
        snap("4-sonando")
        scene.close()
    }

    /** Toca el elemento cuya descripción de accesibilidad es [label] (la posición cambia con cada tema). */
    private fun tapLabel(scene: ImageComposeScene, label: String, tap: (Float, Float) -> Unit) {
        val node = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = false) }
            .lastOrNull { label in (it.config.getOrNull(SemanticsProperties.ContentDescription) ?: emptyList()) }
            ?: error("No se encontró \"$label\"")
        val c = node.boundsInRoot.center
        tap(c.x, c.y)
    }

    private fun desktop(theme: String, dir: File) {
        var st: AppState? = null
        val scene = ImageComposeScene(1280, 800, Density(1f)) { App(PlatformServices("Prueba", true, store(theme), Empty, null)) { st = it } }
        var t = 0L
        fun frames(n: Int = 30) = repeat(n) { scene.render(t); t += 16_000_000; Thread.sleep(2) }
        fun snap(name: String) {
            frames(40)
            File(dir, "$theme-escritorio-$name.png").writeBytes(scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
        frames(80)
        val s = st!!
        s.play(s.library.state.value.tracks, 0); s.player.togglePlay(); s.player.seekTo(30); frames(30)
        snap("1-inicio")
        scene.sendKeyEvent(KeyEvent(Key.L, KeyEventType.KeyDown)); scene.sendKeyEvent(KeyEvent(Key.L, KeyEventType.KeyUp)); frames(60)
        snap("2-reproduciendo")
        scene.close()
    }
}
