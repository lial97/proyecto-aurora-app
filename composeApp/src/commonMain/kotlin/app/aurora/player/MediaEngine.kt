package app.aurora.player

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.StateFlow

/**
 * Motor multimedia nativo de la plataforma (VLC en escritorio, Media3 en Android, AVPlayer en iOS).
 * Audio y video usan la misma reproducción: cambiar entre "Canción" y "Video" no corta el sonido.
 */
interface MediaEngine {
    /** Si el motor está listo (por ejemplo, VLC encontrado). */
    val available: Boolean

    /** [meta] = datos para la notificación y la pantalla de bloqueo (Android). */
    fun load(path: String, play: Boolean, startMs: Long = 0, meta: MediaMeta? = null)
    fun play()
    fun pause()
    fun seekTo(ms: Long)
    fun stop()

    /** 0..1. 1 = volumen original, sin ganancia (nunca se amplifica: evita la saturación). */
    fun setVolume(volume: Float)

    /** Ecualizador de 10 bandas; `null` o desactivado = sin ecualizador. */
    fun setEqualizer(eq: app.aurora.audio.EqSettings?) {}

    /** Normalizar volumen (se aplica desde la siguiente pista). */
    fun setNormalize(on: Boolean) {}

    /** Salida de video, o `null` si el motor solo hace audio. */
    val video: VideoOutput? get() = null

    var onPosition: (Long) -> Unit
    var onDuration: (Long) -> Unit
    var onEnded: () -> Unit
    var onError: (String) -> Unit
    /** Aviso que no detiene la reproducción (p. ej. falta un decodificador de video); `null` lo borra. */
    var onNotice: (String?) -> Unit

    fun release()

    /**
     * El motor se pausó o reanudó por su cuenta (llamada, otra app con audio, auriculares desconectados,
     * botones de la pantalla de bloqueo). Por defecto no avisa.
     */
    fun setOnPlayingChanged(listener: (Boolean) -> Unit) {}
}

/** Título, artista y portada que muestra el sistema (notificación, pantalla de bloqueo, auriculares). */
data class MediaMeta(
    val title: String, val artist: String, val album: String, val artworkUri: String?,
    /** Pista de la biblioteca (para buscar su portada) y duración conocida. */
    val trackId: String? = null, val durationMs: Long = 0,
)

/** Fotogramas de video listos para dibujar con Compose. */
interface VideoOutput {
    /** El video lo dibuja una vista nativa (Android) en lugar de fotogramas en memoria. */
    val native: Boolean get() = false

    /** Último fotograma, o `null` si no hay video. */
    val frame: StateFlow<ImageBitmap?>

    /** Una superficie empieza a mostrar el video (solo entonces se copian fotogramas). */
    fun attach()
    fun detach()

    /** `false` = solo audio: se desactiva la pista de video para ahorrar recursos. */
    fun setEnabled(enabled: Boolean)

    /** Ancho y alto del video actual (para girar la pantalla en pantalla completa); `null` si no se sabe. */
    val videoSize: StateFlow<Pair<Int, Int>?> get() = kotlinx.coroutines.flow.MutableStateFlow(null)
}
