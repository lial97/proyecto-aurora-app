package app.aurora

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.unit.Density
import app.aurora.domain.MediaType
import app.aurora.domain.Track
import app.aurora.platform.MediaSource
import app.aurora.platform.MemoryStore
import app.aurora.platform.PlatformServices
import app.aurora.platform.ScanProgress
import app.aurora.screens.OnboardingModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Configuración inicial en escritorio: pasos con el teclado, retomar y terminar. */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class OnboardingFlowTest {
    private object Few : MediaSource {
        override fun defaultFolders() = emptyList<String>()
        override val canPickFolder = true
        override suspend fun pickFolder(): String? = null
        override suspend fun candidateFolders() = emptyList<String>()
        override suspend fun scan(folders: List<String>, onProgress: (ScanProgress) -> Unit) =
            (1..5).map { Track("t$it", "Canción $it", "Artista", "Álbum", 200, MediaType.AUDIO) }
        override suspend fun loadCover(track: Track) = null
    }

    @Test fun keyboardWalksTheStepsAndFinishes() {
        val store = MemoryStore()
        var st: AppState? = null
        val scene = ImageComposeScene(1000, 700, Density(1f)) { App(PlatformServices("Prueba", true, store, Few, null)) { st = it } }
        var t = 0L
        fun frames(n: Int = 40) = repeat(n) { scene.render(t); t += 16_000_000; Thread.sleep(2) }
        fun key(k: Key) { scene.sendKeyEvent(KeyEvent(k, KeyEventType.KeyDown)); scene.sendKeyEvent(KeyEvent(k, KeyEventType.KeyUp)); frames() }
        fun step() = store.get("bienvenida_paso")?.toIntOrNull() ?: 0
        frames(60)
        val s = st!!
        assertTrue(s.onboarding)

        key(Key.Enter)                       // Bienvenida → Nombre
        assertEquals(OnboardingModel.NAME, step())
        key(Key.Tab)                         // del título al campo del nombre
        key(Key.Enter)                       // Enter en el campo: "Continuar sin nombre" → Música (un solo paso)
        assertEquals(OnboardingModel.MUSIC, step(), "Enter en el campo avanza un solo paso")
        key(Key.Enter)                       // Música sin carpetas: "Continuar" desactivado
        assertEquals(OnboardingModel.MUSIC, step(), "sin carpetas, Enter no avanza")
        key(Key.Escape)                      // Esc vuelve
        assertEquals(OnboardingModel.NAME, step())
        key(Key.Enter); key(Key.Enter)       // Nombre → Música (sigue desactivado)

        // Con una carpeta (ya contada), Continuar se activa.
        s.addFolder("/musica", MediaType.AUDIO, quiet = true); frames(80)
        key(Key.Enter)
        assertEquals(OnboardingModel.VIDEOS, step())
        key(Key.Enter)                       // Saltar este paso
        assertEquals(OnboardingModel.THEME, step())
        assertFalse(s.settings.onboardingDone)

        // Tarjetas de tema: Tab entra en la primera (Aurora), → pasa a Póster y Enter la elige sin avanzar.
        key(Key.Tab); key(Key.DirectionRight); key(Key.Enter)
        assertEquals(app.aurora.theme.ThemeId.POSTER, s.settings.theme.value.id)
        assertEquals(OnboardingModel.THEME, step(), "Enter sobre una tarjeta elige el tema, no avanza")
        key(Key.Escape); key(Key.Enter)       // y el foco vuelve al título del paso: Enter avanza otra vez
        assertEquals(OnboardingModel.THEME, step())

        key(Key.Enter)                       // "Empezar": se guarda todo y queda "Todo listo"
        assertTrue(s.settings.onboardingDone)
        assertTrue(s.onboarding, "Todo listo sigue en pantalla")
        key(Key.Escape)                      // desde Todo listo no se vuelve
        assertTrue(s.onboarding)
        frames(120)
        key(Key.Enter)                       // "Ir a Aurora"
        assertFalse(s.onboarding)
        assertEquals(listOf("/musica"), s.settings.folders.value)
        scene.close()
    }

    @Test fun resumesAtTheSameStepWithWhatWasChosen() {
        val store = MemoryStore()
        var st: AppState? = null
        val first = ImageComposeScene(1000, 700, Density(1f)) { App(PlatformServices("Prueba", true, store, Few, null)) { st = it } }
        var t = 0L
        repeat(40) { first.render(t); t += 16_000_000 }
        st!!.settings.setUserName("Lila")
        st!!.addFolder("/musica", MediaType.AUDIO, quiet = true)
        store.put("bienvenida_paso", "3")
        first.close()

        var again: AppState? = null
        val second = ImageComposeScene(1000, 700, Density(1f)) { App(PlatformServices("Prueba", true, store, Few, null)) { again = it } }
        repeat(40) { second.render(t); t += 16_000_000 }
        assertTrue(again!!.onboarding, "con carpeta elegida a mitad, la configuración sigue")
        assertEquals("Lila", again!!.settings.userName.value)
        assertEquals(3, again!!.settings.onboardingStep)
        second.close()
    }
}
