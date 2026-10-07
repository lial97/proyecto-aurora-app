package app.aurora.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.time.TimeSource

/**
 * Milisegundos entre fotogramas de las animaciones continuas (fondo, onda, ecualizador, karaoke); 0 = cada
 * fotograma de la pantalla. En escritorio se limita (ver Main.kt): a 60–144 Hz cada fotograma redibuja la ventana
 * entera y el fondo, y esas animaciones son lentas, así que a ~30 fps se ven igual con mucho menos CPU.
 */
var animationFrameIntervalMs: Long = 0L

/**
 * Ritmo estricto (Windows, ver Main.kt), para que la ventana se redibuje de verdad ~30 veces por segundo:
 * - Un solo temporizador para todas las animaciones. Con un `delay` por animación, en Windows (temporizadores
 *   de ~15 ms de precisión) cada una despertaba en un momento distinto y, en una pantalla de 165 Hz, caía en
 *   su propio fotograma: 4 animaciones a 30 fps eran ~120 redibujos por segundo.
 * - La barra de progreso también va a este ritmo ([animatePacedFloat]). Su animación (900 ms por cada segundo
 *   de canción) corre casi todo el tiempo que suena y, con `animateFloatAsState`, iba a la frecuencia del monitor.
 * En las demás plataformas se mantiene el comportamiento de siempre.
 */
var strictPacing: Boolean = false

private val epoch = TimeSource.Monotonic.markNow()

/** Próximo fotograma de animación compartido (solo con [strictPacing]); completo = ya pasó. */
@Volatile private var tick: CompletableDeferred<Unit>? = null

/**
 * Espera el siguiente fotograma de animación y llama a [onFrame] con su hora (ns). Todas las animaciones esperan al
 * mismo múltiplo de [animationFrameIntervalMs], así caen en el mismo fotograma y la ventana se redibuja una sola vez.
 * Los estados se cambian dentro de [onFrame]: así entran en ese fotograma y no piden otro.
 */
@OptIn(DelicateCoroutinesApi::class)
suspend fun <R> awaitAnimationFrame(onFrame: (frameTimeNanos: Long) -> R): R {
    val step = animationFrameIntervalMs
    if (step > 0) {
        val wait = step - epoch.elapsedNow().inWholeMilliseconds % step
        if (strictPacing) {
            val t = tick?.takeIf { !it.isCompleted } ?: CompletableDeferred<Unit>().also { d ->
                tick = d
                GlobalScope.launch(Dispatchers.Default) { delay(wait); d.complete(Unit) }
            }
            t.await()
        } else delay(wait)
    }
    return withFrameNanos(onFrame)
}

/** Como `animateFloatAsState(target, tween(durationMs, LinearEasing))`, pero a ritmo de [awaitAnimationFrame]. */
@Composable
fun animatePacedFloat(target: Float, durationMs: Int): State<Float> {
    val value = remember { mutableFloatStateOf(target) }
    LaunchedEffect(target) {
        val from = value.floatValue
        val t0 = withFrameNanos { it }
        while (true) {
            val f = awaitAnimationFrame { now ->
                ((now - t0) / 1e6f / durationMs).coerceAtMost(1f).also { value.floatValue = from + (target - from) * it }
            }
            if (f >= 1f) break
        }
    }
    return value
}

/** Segundos que avanzan mientras [running] es `true` (al ritmo de [awaitAnimationFrame]). Leerlo al dibujar evita recomponer. */
@Composable
fun rememberAnimationSeconds(running: Boolean): State<Float> {
    val seconds = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            awaitAnimationFrame { now ->
                seconds.floatValue += (now - last) / 1e9f
                last = now
            }
        }
    }
    return seconds
}
