package app.aurora

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.aurora.color.buildPalette
import app.aurora.components.ProgressBar
import app.aurora.theme.AppThemeProvider
import app.aurora.theme.FormFactor
import app.aurora.theme.Themes
import java.io.File
import kotlin.test.Test

/** Barra con onda del reproductor: sonando (onda) y en pausa (recta), en todos los temas. Guarda capturas con AURORA_SHOTS. */
class WaveProgressTest {
    @Test
    fun waveInEveryTheme() {
        val dir = System.getProperty("aurora.shots")?.let(::File)
        for (theme in Themes.all) for (playing in listOf(true, false)) {
            val scene = ImageComposeScene(360 * 2, 80 * 2, Density(2f)) {
                AppThemeProvider(theme, buildPalette(null, null), FormFactor.MOBILE) {
                    Column(Modifier.fillMaxSize().background(theme.colors.background).padding(20.dp)) {
                        ProgressBar(78, 214, {}, wavePlaying = playing)
                    }
                }
            }
            // Medio segundo de animación (la onda sube de 0 a 1 en 450 ms).
            var t = 0L
            repeat(40) { scene.render(t); t += 16_000_000 }
            val img = scene.render(t)
            dir?.let { d -> d.mkdirs(); File(d, "onda-${theme.id.name.lowercase()}-${if (playing) "sonando" else "pausa"}.png").writeBytes(img.encodeToData()!!.bytes) }
            scene.close()
        }
    }
}
