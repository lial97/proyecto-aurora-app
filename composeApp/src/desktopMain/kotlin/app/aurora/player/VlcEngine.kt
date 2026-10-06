package app.aurora.player

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger

/**
 * Audio y video con VLC (vlcj): el que viene con la app (ver [BundledVlc]) o el instalado; si no se encuentra, [available] es `false`
 * y la app sigue funcionando con el reloj simulado.
 */
class VlcEngine : MediaEngine {
    private val factory: MediaPlayerFactory? = runCatching {
        if (BundledVlc.discover()) MediaPlayerFactory(*VLC_ARGS) else null
    }.getOrNull()?.also { f ->
        // Identidad propia ante PulseAudio/PipeWire: así el sistema no aplica a Aurora el volumen
        // guardado para la app VLC (que puede estar por encima del 100 % y saturar el sonido).
        runCatching {
            f.application().setUserAgent("Aurora", "Aurora/0.3")
            f.application().setApplicationId("app.aurora", "0.3", "audio-x-generic")
        }
    }
    private val mp: EmbeddedMediaPlayer? = factory?.mediaPlayers()?.newEmbeddedMediaPlayer()
    private val videoOut = mp?.let { VlcVideoOutput(it, factory!!) }

    override val available: Boolean get() = mp != null
    override val video: VideoOutput? get() = videoOut

    override var onPosition: (Long) -> Unit = {}
    override var onDuration: (Long) -> Unit = {}
    override var onEnded: () -> Unit = {}
    override var onError: (String) -> Unit = {}
    override var onNotice: (String?) -> Unit = {}
    private var volume = 80
    private val checker = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "aurora-video-check").apply { isDaemon = true } }

    init {
        mp?.events()?.addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) = onPosition(newTime)
            override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) = onDuration(newLength)
            override fun finished(mediaPlayer: MediaPlayer) = onEnded()
            override fun error(mediaPlayer: MediaPlayer) = onError("VLC no pudo reproducir este archivo")
            override fun playing(mediaPlayer: MediaPlayer) {
                // VLC olvida el volumen al cambiar de archivo; nunca se pasa de 100 (sin ganancia).
                // El sistema de sonido puede restaurar otro volumen al crear el flujo y VLC no lo reenvía
                // si cree que ya es el mismo: se fuerza con un valor distinto y luego el real.
                // Se vuelve a aplicar al arrancar y poco después, cuando el sistema ya restauró lo suyo.
                for (delay in longArrayOf(0, 250, 1000)) {
                    checker.schedule({ mediaPlayer.submit { applyVolume(mediaPlayer) } }, delay, java.util.concurrent.TimeUnit.MILLISECONDS)
                }
                // Si hay pista de video pero VLC nunca pidió el formato de imagen, falta el decodificador.
                checker.schedule({
                    val out = videoOut ?: return@schedule
                    val hasVideoTrack = runCatching { mediaPlayer.video().trackCount() > 0 }.getOrDefault(false)
                    onNotice(if (hasVideoTrack && !out.formatSeen && out.wanted) MISSING_DECODER else null)
                }, 2500, java.util.concurrent.TimeUnit.MILLISECONDS)
            }
        })
    }

    @Volatile private var normalize = false

    override fun setNormalize(on: Boolean) { normalize = on }

    override fun setEqualizer(eq: app.aurora.audio.EqSettings?) {
        val f = factory ?: return
        val p = mp ?: return
        if (eq == null || !eq.enabled) { p.audio().setEqualizer(null); return }
        val e = f.equalizer().newEqualizer()
        e.setPreamp(eq.vlcPreamp)
        eq.bands.forEachIndexed { i, v -> if (i < e.bandCount()) e.setAmp(i, v.coerceIn(-20f, 20f)) }
        p.audio().setEqualizer(e)
    }

    override fun load(path: String, play: Boolean, startMs: Long, meta: MediaMeta?) {
        val opts = buildList {
            if (startMs > 0) add(":start-time=${startMs / 1000.0}")
            // Normalizador de VLC: iguala el volumen entre canciones.
            if (normalize) add(":audio-filter=normvol")
        }.toTypedArray()
        videoOut?.reset()
        onNotice(null)
        if (play) mp?.media()?.play(path, *opts) else mp?.media()?.startPaused(path, *opts)
    }

    private fun applyVolume(p: MediaPlayer) {
        if (p.audio().volume() == volume) return
        p.audio().setVolume(if (volume > 0) volume - 1 else 1)
        p.audio().setVolume(volume)
    }

    override fun play() { mp?.controls()?.play() }
    override fun pause() { mp?.controls()?.setPause(true) }
    override fun seekTo(ms: Long) { mp?.controls()?.setTime(ms) }
    override fun stop() { mp?.controls()?.stop(); videoOut?.reset() }

    override fun setVolume(volume: Float) {
        this.volume = (volume.coerceIn(0f, 1f) * 100).toInt()
        mp?.audio()?.setVolume(this.volume)
    }

    override fun release() {
        checker.shutdownNow()
        runCatching { mp?.release() }
        runCatching { factory?.release() }
    }

    companion object {
        const val MISSING_DECODER = "VLC no puede decodificar este video (falta el complemento ffmpeg). " +
            "En Arch: sudo pacman -S vlc-plugin-ffmpeg · En Debian/Ubuntu: sudo apt install vlc-plugin-base"

        /** Calidad de audio: remuestreo de máxima calidad y sin estirar el tiempo (evita artefactos). */
        val VLC_ARGS = arrayOf(
            "--quiet",
            "--no-video-title-show",
            "--no-audio-time-stretch",
            "--speex-resampler-quality=10",
            "--audio-resampler=speex_resampler",
            "--no-sub-autodetect-file",
        )
    }
}

/**
 * Recibe los fotogramas de VLC en memoria (formato RV32/BGRA) y los publica como ImageBitmap.
 * Dibujarlos con Compose permite esquinas, recortes y controles del tema encima del video.
 */
private class VlcVideoOutput(private val mp: EmbeddedMediaPlayer, factory: MediaPlayerFactory) : VideoOutput {
    private val _frame = MutableStateFlow<ImageBitmap?>(null)
    override val frame: StateFlow<ImageBitmap?> = _frame
    private val viewers = AtomicInteger(0)
    @Volatile private var info: ImageInfo? = null
    /** Tamaño real del video (sin el relleno que VLC añade a la imagen decodificada). */
    @Volatile private var visible: java.awt.Dimension? = null
    /** VLC llegó a pedir el formato de imagen (si no, no pudo decodificar el video). */
    @Volatile var formatSeen = false
    private var bytes = ByteArray(0)

    init {
        val format = object : BufferFormatCallback {
            override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
                formatSeen = true
                visible = runCatching { mp.video().videoDimension() }.getOrNull()
                info = ImageInfo(sourceWidth, sourceHeight, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
                return RV32BufferFormat(sourceWidth, sourceHeight)
            }
            override fun newFormatSize(bufferWidth: Int, bufferHeight: Int, displayWidth: Int, displayHeight: Int) {}
            override fun allocatedBuffers(buffers: Array<out ByteBuffer>) {}
        }
        val render = object : RenderCallback {
            override fun lock(mediaPlayer: MediaPlayer) {}
            override fun unlock(mediaPlayer: MediaPlayer) {}
            override fun display(mediaPlayer: MediaPlayer, nativeBuffers: Array<out ByteBuffer>, bufferFormat: BufferFormat, displayWidth: Int, displayHeight: Int) {
                if (viewers.get() <= 0 || !wanted) return
                if (info == null) return
                // VLC puede rellenar el alto (p. ej. 1090 en un video de 1080): se usa el tamaño visible.
                val v = visible
                val w = minOf(displayWidth, v?.width?.takeIf { it > 0 } ?: displayWidth)
                val h = minOf(displayHeight, v?.height?.takeIf { it > 0 } ?: displayHeight)
                val i = ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
                val buf = nativeBuffers[0]
                val rowBytes = bufferFormat.pitches[0]
                val size = rowBytes * bufferFormat.lines[0]
                if (bytes.size != size) bytes = ByteArray(size)
                buf.rewind(); buf.get(bytes, 0, minOf(size, buf.remaining()))
                val img = runCatching { Image.makeRaster(i, bytes, rowBytes).toComposeImageBitmap() }.getOrNull()
                if (wanted && viewers.get() > 0) _frame.value = img
            }
        }
        mp.videoSurface().set(factory.videoSurfaces().newVideoSurface(format, render, true))
    }

    fun reset() { _frame.value = null; formatSeen = false }

    override fun attach() { viewers.incrementAndGet() }
    override fun detach() { viewers.decrementAndGet() }

    /** El usuario quiere ver la imagen (no "Solo audio"). */
    @Volatile var wanted = true

    override fun setEnabled(enabled: Boolean) {
        wanted = enabled
        mp.submit {
            if (!enabled) mp.video().setTrack(-1)
            else mp.video().trackDescriptions().firstOrNull { it.id() != -1 }?.let { mp.video().setTrack(it.id()) }
        }
        if (!enabled) reset()
    }
}
