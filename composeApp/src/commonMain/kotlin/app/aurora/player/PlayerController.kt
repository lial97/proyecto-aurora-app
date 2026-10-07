package app.aurora.player

import app.aurora.domain.Track
import kotlinx.coroutines.flow.StateFlow

data class PlaybackState(
    val queue: List<Track> = emptyList(),
    val index: Int = -1,
    val positionSec: Int = 0,
    /** Posición exacta en ms del último aviso del motor (la interfaz la interpola entre avisos). */
    val positionMs: Long = 0,
    /** Duración real informada por el motor (puede corregir la de las etiquetas). */
    val durationSec: Int = 0,
    val isPlaying: Boolean = false,
    val shuffle: Boolean = false,
    val repeatOne: Boolean = false,
    val volume: Float = 0.8f,
    /** La pista actual suena con el motor real (no simulada). */
    val realAudio: Boolean = false,
    /** `false` = "Solo audio": la pista de video está desactivada. */
    val videoEnabled: Boolean = true,
    val error: String? = null,
    /** Problema con la imagen del video (el audio sigue sonando). */
    val videoProblem: String? = null,
) {
    val current: Track? get() = queue.getOrNull(index)
    /** La pista actual es un video que se está reproduciendo con el motor real. */
    val hasVideo: Boolean get() = realAudio && current?.mediaType == app.aurora.domain.MediaType.VIDEO
    /** Avance de la canción de 0 a 1 (con la duración del motor o, si aún no la dio, la de las etiquetas). */
    val progress: Float get() =
        (positionSec.toFloat() / (durationSec.takeIf { it > 0 } ?: current?.durationSec ?: 0).coerceAtLeast(1)).coerceIn(0f, 1f)
}

/** Contrato del reproductor: cola, reglas de la spec §8 y control del motor. */
interface PlayerController {
    val state: StateFlow<PlaybackState>
    fun play(queue: List<Track>, startIndex: Int)
    fun togglePlay()
    fun pauseIfPlaying() { if (state.value.isPlaying) togglePlay() }
    fun next()
    fun previous()
    fun seekTo(positionSec: Int)
    fun seekBy(deltaSec: Int) = seekTo(state.value.positionSec + deltaSec)
    /** Reordenar la cola (índices absolutos de la cola). */
    fun moveInQueue(from: Int, to: Int)
    fun removeFromQueue(index: Int)
    /** Quita todo lo que viene después de la pista actual. */
    fun clearUpcoming()
    /** Carga una cola en pausa en la posición indicada (para "Recordar dónde quedé"). */
    fun restore(queue: List<app.aurora.domain.Track>, index: Int, positionSec: Int)
    /** Opciones de reproducción de Ajustes. */
    fun configure(fadeSec: Int, gapless: Boolean, repeatQueue: Boolean)
    fun setEqualizer(eq: app.aurora.audio.EqSettings)
    fun setNormalize(on: Boolean)

    /** Pone la pista justo después de la actual ([next]) o al final de la cola. */
    fun enqueue(track: Track, next: Boolean)
    fun toggleShuffle()
    fun toggleRepeat()
    fun setVolume(volume: Float)
    /** Cambiar entre "Canción" y "Video" no reinicia la reproducción: solo activa o desactiva la imagen. */
    fun setVideoEnabled(enabled: Boolean)
    /** Fotogramas del video actual (null si el motor no hace video). */
    val video: VideoOutput?
    fun release()

    /** Se corrigieron los datos de una pista: se reemplaza en la cola (y en la notificación si es la que suena). */
    fun updateTrack(track: Track) {}
}
