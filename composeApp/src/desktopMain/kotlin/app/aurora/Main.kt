package app.aurora

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.aurora.resources.Res
import app.aurora.resources.aurora_icon
import org.jetbrains.compose.resources.painterResource

/**
 * Tamaño inicial: 1280×800 o, si no cabe, el 85 % del área útil de la pantalla (sin la barra de tareas).
 * Con la escala de Windows al 125 % una pantalla de 1920×1080 mide 1536×864 y 1280×800 la llenaba casi entera.
 */
private fun initialSize(): DpSize = runCatching {
    val env = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
    val gc = env.defaultScreenDevice.defaultConfiguration
    val b = gc.bounds
    val i = java.awt.Toolkit.getDefaultToolkit().getScreenInsets(gc)
    val w = b.width - i.left - i.right
    val h = b.height - i.top - i.bottom
    DpSize(minOf(1280, (w * .85f).toInt()).dp, minOf(800, (h * .85f).toInt()).dp)
}.getOrDefault(DpSize(1280.dp, 800.dp))

/** Ventana de la configuración inicial (la primera vez que se abre la app). */
private val SetupSize = DpSize(1000.dp, 700.dp)

fun main() {
    configureDesktop()
    runApp()
}

/** Ajustes de rendimiento del escritorio; van antes de abrir la ventana (también los usa `CpuProbe`). */
internal fun configureDesktop() {
    // Animaciones continuas (fondo, onda, ecualizador, karaoke) a ~30 fps en lugar de la frecuencia del monitor.
    app.aurora.components.animationFrameIntervalMs = 33
    if (System.getProperty("os.name").orEmpty().lowercase().contains("win")) {
        // Temporizadores imprecisos y pantallas de 120–165 Hz: sin esto la ventana se redibujaba ~120 veces por segundo.
        app.aurora.components.strictPacing = true
    }
}

private fun runApp() = application {
    val platform = remember { app.aurora.platform.createPlatformServices() }
    val firstRun = remember { !app.aurora.data.SettingsRepository(platform.store, emptyList()).onboardingDone }
    val window = rememberWindowState(size = if (firstRun) SetupSize else initialSize(), position = WindowPosition.Aligned(Alignment.Center))
    var appState by remember { mutableStateOf<AppState?>(null) }
    Window(onCloseRequest = ::exitApplication, title = "Aurora", state = window, icon = painterResource(Res.drawable.aurora_icon)) {
        // Configuración inicial: 1000×700 (mínimo 900×620). Al terminar, el tamaño normal de la app. Siempre centrada.
        val setup = appState?.onboarding ?: firstRun
        LaunchedEffect(setup) {
            window.placement = WindowPlacement.Floating
            if (setup) {
                this@Window.window.minimumSize = java.awt.Dimension(900, 620)
                window.size = SetupSize
            } else {
                this@Window.window.minimumSize = java.awt.Dimension(800, 540)
                window.size = initialSize()
            }
            delay(60) // a que la ventana tome el tamaño nuevo
            this@Window.window.setLocationRelativeTo(null)
        }
        App(platform = platform, onState = { state ->
            appState = state
            // Pantalla completa del video = ventana a pantalla completa.
            state.onWindowFullscreen = { on -> window.placement = if (on) WindowPlacement.Fullscreen else WindowPlacement.Floating }
        })
    }
}
