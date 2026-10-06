package app.aurora.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.graphics.ImageBitmap
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Motor de Android con Media3. Se conecta al [PlaybackService] mediante un MediaController, así la
 * reproducción sigue con la app en segundo plano o la pantalla apagada.
 */
class Media3Engine(private val context: Context) : MediaEngine {
    private var controller: MediaController? = null
    private val pending = ArrayDeque<(MediaController) -> Unit>()
    private val main = Handler(Looper.getMainLooper())
    private var volume = 1f

    override val available: Boolean = true
    override var onPosition: (Long) -> Unit = {}
    override var onDuration: (Long) -> Unit = {}
    override var onEnded: () -> Unit = {}
    override var onError: (String) -> Unit = {}
    override var onNotice: (String?) -> Unit = {}

    private val output = Media3Video()

    /** Lo conecta AuroraRuntime: portada en JPEG (512 px) para la notificación y el bloqueo. */
    var artworkLoader: (suspend (trackId: String?) -> ByteArray)? = null
    private val scope = kotlinx.coroutines.MainScope()
    private var artworkJob: kotlinx.coroutines.Job? = null
    override val video: VideoOutput get() = output

    init {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        // La señal APP_CONTROLLER le dice a la sesión que estas órdenes son de la app (ver AuroraSessionPlayer.fromApp).
        val future = MediaController.Builder(context, token)
            .setConnectionHints(android.os.Bundle().apply { putBoolean(PlaybackService.APP_CONTROLLER, true) })
            .buildAsync()
        future.addListener({
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            output.player.value = c
            c.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_READY -> c.duration.takeIf { it != C.TIME_UNSET && it > 0 }?.let(onDuration)
                        Player.STATE_ENDED -> onEnded()
                    }
                }
                override fun onPlayerError(error: PlaybackException) = onError("No se pudo reproducir este archivo")
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = playingListener(playWhenReady)
                override fun onVideoSizeChanged(size: androidx.media3.common.VideoSize) {
                    // El ancho visible tiene en cuenta los píxeles no cuadrados.
                    output.size.value = if (size.width > 0 && size.height > 0) (size.width * size.pixelWidthHeightRatio).toInt() to size.height else null
                }
            })
            c.volume = volume
            while (pending.isNotEmpty()) pending.removeFirst()(c)
        }, { r -> main.post(r) })
        // ExoPlayer no avisa de la posición: se consulta 4 veces por segundo.
        main.post(object : Runnable {
            override fun run() {
                controller?.takeIf { it.isPlaying }?.let { onPosition(it.currentPosition) }
                main.postDelayed(this, 250)
            }
        })
    }

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) block(c) else pending.addLast(block)
    }

    override fun load(path: String, play: Boolean, startMs: Long, meta: MediaMeta?) {
        val uri = if (path.startsWith("content://")) Uri.parse(path) else Uri.fromFile(File(path))
        fun item(artwork: ByteArray?) = MediaItem.Builder().setUri(uri).setMediaId(path).setMediaMetadata(
            MediaMetadata.Builder().apply {
                meta?.let { m ->
                    setTitle(m.title)
                    if (m.artist.isNotBlank()) setArtist(m.artist)
                    if (m.album.isNotBlank()) setAlbumTitle(m.album)
                    if (m.durationMs > 0) setDurationMs(m.durationMs)
                }
                if (artwork != null) setArtworkData(artwork, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                else meta?.artworkUri?.let { setArtworkUri(Uri.parse(it)) }
            }.build(),
        ).build()
        withController { c ->
            // Cada video empieza con imagen (aunque el anterior estuviera en "Solo audio").
            c.trackSelectionParameters = c.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false).build()
            c.setMediaItem(item(null), startMs)
            c.prepare()
            c.playWhenReady = play
        }
        // La portada (o la de Aurora) llega un momento después y se cambia sin cortar el sonido.
        artworkJob?.cancel()
        val loader = artworkLoader ?: return
        artworkJob = scope.launch {
            val art = runCatching { loader(meta?.trackId) }.getOrNull() ?: return@launch
            withController { c ->
                if (c.mediaItemCount > 0 && c.currentMediaItem?.mediaId == path) c.replaceMediaItem(0, item(art))
            }
        }
    }

    /** Nuevos datos (y portada) para la pista que suena, sin cortar el sonido ("Corregir datos"). */
    override fun updateMeta(path: String, meta: MediaMeta) {
        val loader = artworkLoader
        artworkJob?.cancel()
        artworkJob = scope.launch {
            val art = loader?.let { runCatching { it(meta.trackId) }.getOrNull() }
            withController { c ->
                val item = c.currentMediaItem ?: return@withController
                if (c.mediaItemCount == 0 || item.mediaId != path) return@withController
                val md = MediaMetadata.Builder().setTitle(meta.title).setArtist(meta.artist.ifBlank { null }).setAlbumTitle(meta.album.ifBlank { null })
                    .apply { if (meta.durationMs > 0) setDurationMs(meta.durationMs); if (art != null) setArtworkData(art, MediaMetadata.PICTURE_TYPE_FRONT_COVER) }.build()
                c.replaceMediaItem(0, item.buildUpon().setMediaMetadata(md).build())
            }
        }
    }

    private var playingListener: (Boolean) -> Unit = {}
    override fun setOnPlayingChanged(listener: (Boolean) -> Unit) { playingListener = listener }

    override fun play() = withController { it.play() }
    override fun pause() = withController { it.pause() }
    override fun seekTo(ms: Long) = withController { it.seekTo(ms) }
    override fun stop() = withController { it.stop(); it.clearMediaItems() }

    override fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
        withController { it.volume = this.volume }
    }

    override fun release() {
        artworkJob?.cancel()
        main.removeCallbacksAndMessages(null)
        controller?.release()
        controller = null
    }

    /** El video lo dibuja la vista nativa de Media3 (ver NativeVideoView). */
    inner class Media3Video : VideoOutput {
        val player = MutableStateFlow<Player?>(null)
        override val native: Boolean = true
        override val frame: StateFlow<ImageBitmap?> = MutableStateFlow(null)
        val size = MutableStateFlow<Pair<Int, Int>?>(null)
        override val videoSize: StateFlow<Pair<Int, Int>?> = size
        override fun attach() {}
        override fun detach() {}
        override fun setEnabled(enabled: Boolean) = withController { c ->
            c.trackSelectionParameters = c.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, !enabled).build()
        }
    }
}
