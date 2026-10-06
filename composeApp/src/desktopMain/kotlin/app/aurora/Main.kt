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

fun main() = application {
    val window = rememberWindowState(size = DpSize(1280.dp, 800.dp), position = WindowPosition.Aligned(Alignment.Center))
    Window(onCloseRequest = ::exitApplication, title = "Aurora", state = window, icon = painterResource(Res.drawable.aurora_icon)) {
        this.window.minimumSize = java.awt.Dimension(960, 600)
        App(onState = { state ->
            // Pantalla completa del video = ventana a pantalla completa.
            state.onWindowFullscreen = { on -> window.placement = if (on) WindowPlacement.Fullscreen else WindowPlacement.Floating }
        })
    }
}
