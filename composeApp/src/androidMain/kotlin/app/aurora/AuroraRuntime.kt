package app.aurora

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.ui.graphics.asAndroidBitmap
import app.aurora.domain.Track
import app.aurora.platform.AndroidPlatform
import app.aurora.platform.createPlatformServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Estado de Aurora para todo el proceso de Android. No depende de la pantalla: lo usan la actividad,
 * el servicio de reproducción (notificación, bloqueo, auriculares) y los widgets.
 * Así, cerrar la app desde Recientes no detiene la cola ni los botones de la notificación.
 */
object AuroraRuntime {
    @Volatile private var current: AppState? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Crea el estado la primera vez (siempre en el hilo principal). */
    fun get(context: Context): AppState {
        current?.let { return it }
        AndroidPlatform.init(context)
        val s = AppState(scope, createPlatformServices())
        current = s
        (s.platform.mediaEngine as? app.aurora.player.Media3Engine)?.artworkLoader = { id -> artworkFor(s, id) }
        app.aurora.widget.WidgetUpdater.start(context, s, scope)
        return s
    }

    /** El estado, solo si ya existe (los widgets lo usan para saber si algo puede estar sonando). */
    fun peek(): AppState? = current

    /** Lado de las portadas que se envían al sistema (Android toma de ahí los colores de la tarjeta). */
    const val ARTWORK_PX = 512

    /** Portada cuadrada de 512 px en JPEG para la notificación y el bloqueo; la de Aurora si la pista no tiene. */
    private suspend fun artworkFor(state: AppState, trackId: String?): ByteArray {
        val track: Track? = trackId?.let { id -> state.player.state.value.queue.firstOrNull { it.id == id } ?: state.library.state.value.tracks.firstOrNull { it.id == id } }
        val img = track?.let { runCatching { state.covers.get(it) }.getOrNull() }
        return withContext(Dispatchers.Default) {
            img?.let { square(it.asAndroidBitmap()) }?.let(::jpeg) ?: defaultArtwork
        }
    }

    /** Recorte cuadrado centrado, escalado a [ARTWORK_PX]. */
    fun square(src: Bitmap, px: Int = ARTWORK_PX): Bitmap {
        val side = minOf(src.width, src.height)
        val crop = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        return if (side == px) crop else Bitmap.createScaledBitmap(crop, px, px, true)
    }

    fun jpeg(b: Bitmap, quality: Int = 88): ByteArray = ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()

    /** Imagen por defecto de Aurora (nunca un hueco vacío): degradado del tema y una nota. */
    val defaultArtwork: ByteArray by lazy {
        val px = ARTWORK_PX
        val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawRect(0f, 0f, px.toFloat(), px.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, px.toFloat(), px.toFloat(), intArrayOf(0xFF2A1659.toInt(), 0xFF7B2FBF.toInt(), 0xFFFF5FA2.toInt()), null, Shader.TileMode.CLAMP)
        })
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
        // Nota musical: cabeza, plica y bandera.
        c.drawOval(px * .30f, px * .56f, px * .50f, px * .72f, white)
        c.drawRect(px * .465f, px * .26f, px * .50f, px * .65f, white)
        c.drawRect(px * .465f, px * .26f, px * .68f, px * .33f, white)
        jpeg(b, 90)
    }
}
