package app.aurora

import androidx.compose.ui.Alignment
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

fun main() = application {
    val window = rememberWindowState(size = initialSize(), position = WindowPosition.Aligned(Alignment.Center))
    Window(onCloseRequest = ::exitApplication, title = "Aurora", state = window, icon = painterResource(Res.drawable.aurora_icon)) {
        this.window.minimumSize = java.awt.Dimension(800, 540)
        App(onState = { state ->
            // Pantalla completa del video = ventana a pantalla completa.
            state.onWindowFullscreen = { on -> window.placement = if (on) WindowPlacement.Fullscreen else WindowPlacement.Floating }
        })
    }
}
