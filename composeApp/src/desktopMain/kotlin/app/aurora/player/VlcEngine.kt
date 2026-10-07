package app.aurora.player

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import com.sun.jna.Pointer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
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
 *
 * VLC arranca en un hilo propio para que la ventana aparezca enseguida (la primera vez, o tras actualizar, puede
 * tardar mientras rehace el índice de complementos). Las órdenes que llegan antes quedan en fila, en orden.
 */
class VlcEngine : MediaEngine {
    @Volatile private var factory: MediaPlayerFactory? = null
    @Volatile private var mp: EmbeddedMediaPlayer? = null
    private val videoOut = VlcVideoOutput()
    private val started = java.util.concurrent.CountDownLatch(1)
    /** Órdenes recibidas antes de que VLC esté listo; `null` cuando ya arrancó. */
    private var queue: MutableList<() -> Unit>? = mutableListOf()
    private val queueLock = Any()

    /** Ejecuta [action] con VLC listo: ahora mismo o, si aún arranca, en cuanto termine. */
    private fun whenReady(action: () -> Unit) {
        synchronized(queueLock) { queue?.let { it += action; return } }
        action()
    }

    /** Si VLC aún arranca y viene con la app, se da por disponible (las órdenes esperan en la fila). */
    override val available: Boolean get() =
        if (started.count == 0L) mp != null
        else BundledVlc.dir != null || run { started.await(); mp != null }
    override val video: VideoOutput? get() = videoOut

    override var onPosition: (Long) -> Unit = {}
    override var onDuration: (Long) -> Unit = {}
    override var onEnded: () -> Unit = {}
    override var onError: (String) -> Unit = {}
    override var onNotice: (String?) -> Unit = {}
    private var volume = 80
    private val checker = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "aurora-video-check").apply { isDaemon = true } }

    init {
        Thread({
            try {
                val f = runCatching {
                    if (BundledVlc.discover()) MediaPlayerFactory(*VLC_ARGS, *BundledVlc.cacheArgs()) else null
                }.getOrNull()?.also { f ->
                    // Identidad propia ante PulseAudio/PipeWire: así el sistema no aplica a Aurora el volumen
                    // guardado para la app VLC (que puede estar por encima del 100 % y saturar el sonido).
                    runCatching {
                        f.application().setUserAgent("Aurora", "Aurora/0.3")
                        f.application().setApplicationId("app.aurora", "0.3", "audio-x-generic")
                    }
                }
                val p = runCatching { f?.mediaPlayers()?.newEmbeddedMediaPlayer() }.getOrNull()
                if (f != null && p != null) {
                    listen(p)
                    videoOut.bind(p, f)
                }
                factory = f
                mp = p
            } finally {
                started.countDown()
                BundledVlc.cacheDone()
                // Dentro del candado: una orden nueva no puede adelantarse a las que esperaban.
                synchronized(queueLock) { queue?.forEach { runCatching { it() } }; queue = null }
            }
        }, "aurora-vlc-arranque").apply { isDaemon = true; start() }
    }

    private fun listen(player: EmbeddedMediaPlayer) {
        player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) = onPosition(newTime)
            override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) = onDuration(newLength)
            override fun finished(mediaPlayer: MediaPlayer) { SoundCardWake.mark(false); onEnded() }
            override fun paused(mediaPlayer: MediaPlayer) = SoundCardWake.mark(false)
            override fun stopped(mediaPlayer: MediaPlayer) = SoundCardWake.mark(false)
            override fun error(mediaPlayer: MediaPlayer) = onError("VLC no pudo reproducir este archivo")
            override fun playing(mediaPlayer: MediaPlayer) {
                SoundCardWake.mark(true)
                // VLC olvida el volumen al cambiar de archivo; nunca se pasa de 100 (sin ganancia).
                // El sistema de sonido puede restaurar otro volumen al crear el flujo y VLC no lo reenvía
                // si cree que ya es el mismo: se fuerza con un valor distinto y luego el real.
                // Se vuelve a aplicar al arrancar y poco después, cuando el sistema ya restauró lo suyo.
                for (delay in longArrayOf(0, 250, 1000)) {
                    checker.schedule({ mediaPlayer.submit { applyVolume(mediaPlayer) } }, delay, java.util.concurrent.TimeUnit.MILLISECONDS)
                }
                // Si hay pista de video pero VLC nunca pidió el formato de imagen, falta el decodificador.
                checker.schedule({
                    val out = videoOut
                    val hasVideoTrack = runCatching { mediaPlayer.video().trackCount() > 0 }.getOrDefault(false)
                    onNotice(if (hasVideoTrack && !out.formatSeen && out.wanted) MISSING_DECODER else null)
                }, 2500, java.util.concurrent.TimeUnit.MILLISECONDS)
            }
        })
    }

    @Volatile private var normalize = false

    override fun setNormalize(on: Boolean) { normalize = on }

    override fun setEqualizer(eq: app.aurora.audio.EqSettings?) = whenReady {
        val f = factory ?: return@whenReady
        val p = mp ?: return@whenReady
        if (eq == null || !eq.enabled) { p.audio().setEqualizer(null); return@whenReady }
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
        videoOut.reset()
        onNotice(null)
        onVlc { p ->
            if (play) { SoundCardWake.wakeIfIdle(); p.media().play(path, *opts) } else p.media().startPaused(path, *opts)
        }
    }

    private fun applyVolume(p: MediaPlayer) {
        if (p.audio().volume() == volume) return
        p.audio().setVolume(if (volume > 0) volume - 1 else 1)
        p.audio().setVolume(volume)
    }

    /**
     * Órdenes de reproducción en el hilo de VLC, en el orden en que llegan: despertar la tarjeta de sonido
     * ([SoundCardWake]) tarda ~0,5 s y no debe trabar la ventana, y una pausa no puede adelantarse a un "reproducir".
     */
    private fun onVlc(action: (EmbeddedMediaPlayer) -> Unit) = whenReady { mp?.let { p -> p.submit { action(p) } } }

    override fun play() = onVlc { p -> SoundCardWake.wakeIfIdle(); p.controls().play() }
    override fun pause() = onVlc { p -> p.controls().setPause(true) }
    /** Último salto pedido; si llegan varios seguidos, solo se ejecuta el más nuevo. */
    private val pendingSeek = java.util.concurrent.atomic.AtomicLong(-1)

    // `setTime` puede tardar (VLC vacía búferes): va al hilo de VLC, nunca al de la ventana.
    override fun seekTo(ms: Long) = whenReady {
        val p = mp ?: return@whenReady
        if (pendingSeek.getAndSet(ms) == -1L) p.submit { p.controls().setTime(pendingSeek.getAndSet(-1)) }
    }
    override fun stop() { videoOut.reset(); onVlc { p -> p.controls().stop() } }

    override fun setVolume(volume: Float) {
        this.volume = (volume.coerceIn(0f, 1f) * 100).toInt()
        whenReady { mp?.audio()?.setVolume(this.volume) }
    }

    override fun release() = whenReady {
        checker.shutdownNow()
        runCatching { mp?.release() }
        runCatching { factory?.release() }
    }

    companion object {
        const val MISSING_DECODER = "VLC no puede decodificar este video (falta el complemento ffmpeg). " +
            "En Arch: sudo pacman -S vlc-plugin-ffmpeg · En Debian/Ubuntu: sudo apt install vlc-plugin-base"

        /**
         * Calidad de audio: remuestreo de máxima calidad y sin estirar el tiempo (evita artefactos).
         * Decodificación por CPU: con la de la tarjeta gráfica (p. ej. D3D11 en Windows) VLC no logra pasar los
         * fotogramas a memoria, reintenta sin parar, se atrasa y el audio se entrecorta.
         *
         * Remuestreador: en Windows, samplerate. Allí la tarjeta suele ir a 96 o 192 kHz y, tras cada salto, VLC
         * corrige el desfase cambiando la frecuencia; con speex la salida (WASAPI) quedaba ~1,3 s atrasada, VLC
         * vaciaba el búfer y descartaba audio una y otra vez (la canción "se trababa"). Con samplerate, ninguno.
         */
        val VLC_ARGS = arrayOf(
            "--quiet",
            "--avcodec-hw=none",
            "--no-video-title-show",
            "--no-audio-time-stretch",
            "--speex-resampler-quality=10",
            if (System.getProperty("os.name").orEmpty().lowercase().contains("win")) "--audio-resampler=samplerate,any"
            else "--audio-resampler=speex_resampler",
            "--no-sub-autodetect-file",
        )
    }
}

/**
 * Recibe los fotogramas de VLC en memoria (formato RV32/BGRA) y los publica como ImageBitmap.
 * Dibujarlos con Compose permite esquinas, recortes y controles del tema encima del video.
 *
 * Cada fotograma se copia una sola vez, del búfer de VLC directo a la memoria de un Bitmap de Skia (sin pasar
 * por la memoria de Java), y se libera en cuanto la pantalla deja de mostrarlo ([recycle]). Los videos más
 * grandes que la pantalla (p. ej. 4K en un monitor 1080p) los reduce VLC: así cada copia es hasta 4 veces menor.
 */
/** Existe desde el principio; se conecta a VLC con [bind] cuando este termina de arrancar. */
private class VlcVideoOutput : VideoOutput {
    @Volatile private var mp: EmbeddedMediaPlayer? = null
    private val _frame = MutableStateFlow<ImageBitmap?>(null)
    override val frame: StateFlow<ImageBitmap?> = _frame
    private val viewers = AtomicInteger(0)
    /** Tamaño real del video (sin el relleno que VLC añade a la imagen decodificada); `null` si VLC la reduce. */
    @Volatile private var visible: java.awt.Dimension? = null
    /** VLC llegó a pedir el formato de imagen (si no, no pudo decodificar el video). */
    @Volatile var formatSeen = false

    fun bind(player: EmbeddedMediaPlayer, factory: MediaPlayerFactory) {
        mp = player
        val format = object : BufferFormatCallback {
            override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
                formatSeen = true
                val (w, h) = fitToScreen(sourceWidth, sourceHeight)
                visible = if (w == sourceWidth && h == sourceHeight) runCatching { player.video().videoDimension() }.getOrNull() else null
                return RV32BufferFormat(w, h)
            }
            override fun newFormatSize(bufferWidth: Int, bufferHeight: Int, displayWidth: Int, displayHeight: Int) {}
            override fun allocatedBuffers(buffers: Array<out ByteBuffer>) {}
        }
        val render = object : RenderCallback {
            override fun lock(mediaPlayer: MediaPlayer) {}
            override fun unlock(mediaPlayer: MediaPlayer) {}
            override fun display(mediaPlayer: MediaPlayer, nativeBuffers: Array<out ByteBuffer>, bufferFormat: BufferFormat, displayWidth: Int, displayHeight: Int) {
                if (viewers.get() <= 0 || !wanted) return
                // VLC puede rellenar el alto (p. ej. 1090 en un video de 1080): se usa el tamaño visible.
                val v = visible
                val w = minOf(displayWidth, bufferFormat.width, v?.width?.takeIf { it > 0 } ?: displayWidth)
                val h = minOf(displayHeight, bufferFormat.height, v?.height?.takeIf { it > 0 } ?: displayHeight)
                val img = runCatching { copyFrame(nativeBuffers[0], bufferFormat.pitches[0], w, h) }.getOrNull() ?: return
                if (wanted && viewers.get() > 0) _frame.value = img else img.asSkiaBitmap().close()
            }
        }
        player.videoSurface().set(factory.videoSurfaces().newVideoSurface(format, render, true))
    }

    /** Copia [h] filas del búfer de VLC a un Bitmap nuevo (de solo lectura: Compose lo dibuja sin copiarlo otra vez). */
    private fun copyFrame(src: ByteBuffer, rowBytes: Int, w: Int, h: Int): ImageBitmap? {
        val bmp = Bitmap()
        val ok = bmp.allocPixels(ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.OPAQUE), rowBytes) && run {
            val px = bmp.peekPixels() ?: return@run false
            try {
                val size = rowBytes.toLong() * h
                val dst = Pointer(px.addr).getByteBuffer(0, size)
                dst.put(src.duplicate().apply { rewind(); limit(minOf(capacity().toLong(), size).toInt()) })
                true
            } finally {
                px.close()
            }
        }
        if (!ok) { bmp.close(); return null }
        bmp.setImmutable()
        return bmp.asComposeImageBitmap()
    }

    /** Libera un fotograma que ya no se muestra (salvo el actual: otra superficie puede empezar a mostrarlo). */
    override fun recycle(frame: ImageBitmap) {
        if (frame !== _frame.value) runCatching { frame.asSkiaBitmap().close() }
    }

    fun reset() { _frame.value = null; formatSeen = false }

    override fun attach() { viewers.incrementAndGet() }
    override fun detach() { viewers.decrementAndGet() }

    /** El usuario quiere ver la imagen (no "Solo audio"). */
    @Volatile var wanted = true

    override fun setEnabled(enabled: Boolean) {
        wanted = enabled
        val p = mp
        p?.submit {
            if (!enabled) p.video().setTrack(-1)
            else p.video().trackDescriptions().firstOrNull { it.id() != -1 }?.let { p.video().setTrack(it.id()) }
        }
        if (!enabled) reset()
    }

    private companion object {
        /**
         * Tamaño de la imagen que se pide a VLC: el del video o, si es más grande que la pantalla más grande
         * (en píxeles reales), reducido para caber en ella con la misma proporción.
         */
        fun fitToScreen(w: Int, h: Int): Pair<Int, Int> {
            if (w <= 0 || h <= 0) return w to h
            val screens = runCatching {
                java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map { it.displayMode.width to it.displayMode.height }
            }.getOrDefault(emptyList())
            val maxW = screens.maxOfOrNull { it.first }?.takeIf { it > 0 } ?: return w to h
            val maxH = screens.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: return w to h
            if (w <= maxW && h <= maxH) return w to h
            val k = minOf(maxW.toDouble() / w, maxH.toDouble() / h)
            // Medidas pares: algunos convertidores de VLC no aceptan impares.
            return ((w * k).toInt() and 1.inv()).coerceAtLeast(2) to ((h * k).toInt() and 1.inv()).coerceAtLeast(2)
        }
    }
}
