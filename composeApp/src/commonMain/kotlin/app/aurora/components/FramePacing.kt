package app.aurora.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import kotlin.time.TimeSource

/**
 * Milisegundos entre fotogramas de las animaciones continuas (fondo, onda, ecualizador, karaoke); 0 = cada
 * fotograma de la pantalla. En escritorio se limita (ver Main.kt): a 60–144 Hz cada fotograma redibuja la ventana
 * entera y el fondo, y esas animaciones son lentas, así que a ~30 fps se ven igual con mucho menos CPU.
 */
var animationFrameIntervalMs: Long = 0L

private val epoch = TimeSource.Monotonic.markNow()

/**
 * Espera el siguiente fotograma de animación y devuelve su hora (ns). Todas las animaciones esperan al mismo
 * múltiplo de [animationFrameIntervalMs], así caen en el mismo fotograma y la ventana se redibuja una sola vez.
 */
suspend fun awaitAnimationFrame(): Long {
    val step = animationFrameIntervalMs
    if (step > 0) delay(step - epoch.elapsedNow().inWholeMilliseconds % step)
    return withFrameNanos { it }
}

/** Segundos que avanzan mientras [running] es `true` (al ritmo de [awaitAnimationFrame]). Leerlo al dibujar evita recomponer. */
@Composable
fun rememberAnimationSeconds(running: Boolean): State<Float> {
    val seconds = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = awaitAnimationFrame()
            seconds.floatValue += (now - last) / 1e9f
            last = now
        }
    }
    return seconds
}
