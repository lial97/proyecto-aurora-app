package app.aurora.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Miniaturas y vista previa de videos: toma fotogramas en varios momentos del video con VLC (sin sonido),
 * los reduce a [WIDTH] px y los guarda como JPEG en la caché para no repetir el trabajo.
 */
class VideoThumbnailer(private val cacheDir: File) {
    private val mutex = Mutex()
    private val factory: MediaPlayerFactory? by lazy {
        runCatching { if (app.aurora.player.BundledVlc.discover()) MediaPlayerFactory("--quiet", "--no-audio", "--no-video-title-show", "--no-sub-autodetect-file") else null }.getOrNull()
    }

    /** Fotogramas al 15, 35, 55 y 75 % del video. */
    suspend fun frames(video: File, durationSec: Int): List<ImageBitmap> = withContext(Dispatchers.IO) {
        val dir = File(cacheDir, key(video))
        cached(dir)?.let { return@withContext it }
        mutex.withLock {
            cached(dir)?.let { return@withLock it }
            val jpgs = runCatching { capture(video, durationSec) }.getOrDefault(emptyList())
            if (jpgs.isNotEmpty()) {
                dir.mkdirs()
                jpgs.forEachIndexed { i, bytes -> File(dir, "$i.jpg").writeBytes(bytes) }
            }
            jpgs.mapNotNull { decode(it) }
        }
    }

    private fun cached(dir: File): List<ImageBitmap>? {
        val files = dir.listFiles { f -> f.extension == "jpg" }?.sortedBy { it.nameWithoutExtension.toIntOrNull() ?: 0 }
        if (files.isNullOrEmpty()) return null
        return files.mapNotNull { decode(it.readBytes()) }.takeIf { it.isNotEmpty() }
    }

    private fun decode(bytes: ByteArray) = runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()

    /** El nombre de caché cambia si el archivo cambia (ruta, tamaño o fecha). */
    private fun key(f: File): String {
        val md = MessageDigest.getInstance("SHA-1").digest("${f.absolutePath}|${f.length()}|${f.lastModified()}".toByteArray())
        return md.joinToString("") { "%02x".format(it) }.take(20)
    }

    private fun capture(video: File, durationSec: Int): List<ByteArray> {
        val f = factory ?: return emptyList()
        val mp = f.mediaPlayers().newEmbeddedMediaPlayer()
        val latest = AtomicReference<ByteArray?>(null)
        val info = AtomicReference<ImageInfo?>(null)
        val rowBytes = java.util.concurrent.atomic.AtomicInteger(0)
        val firstFrame = CountDownLatch(1)
        try {
            mp.videoSurface().set(f.videoSurfaces().newVideoSurface(object : BufferFormatCallback {
                override fun getBufferFormat(w: Int, h: Int): BufferFormat = RV32BufferFormat(w, h)
                override fun newFormatSize(bw: Int, bh: Int, dw: Int, dh: Int) {}
                override fun allocatedBuffers(buffers: Array<out ByteBuffer>) {}
            }, object : RenderCallback {
                override fun lock(m: MediaPlayer) {}
                override fun unlock(m: MediaPlayer) {}
                override fun display(m: MediaPlayer, buffers: Array<out ByteBuffer>, fmt: BufferFormat, dw: Int, dh: Int) {
                    val dim = runCatching { m.video().videoDimension() }.getOrNull()
                    val w = minOf(dw, dim?.width?.takeIf { it > 0 } ?: dw)
                    val h = minOf(dh, dim?.height?.takeIf { it > 0 } ?: dh)
                    info.set(ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.OPAQUE))
                    rowBytes.set(fmt.pitches[0])
                    val b = buffers[0]
                    val arr = ByteArray(fmt.pitches[0] * h)
                    b.rewind(); b.get(arr, 0, minOf(arr.size, b.remaining()))
                    latest.set(arr)
                    firstFrame.countDown()
                }
            }, true))

            val length = durationSec.takeIf { it > 0 }
            val points = listOf(.15, .35, .55, .75)
            val startAt = ((length ?: 60) * points.first()).toInt()
            mp.media().start(video.absolutePath, ":no-audio", ":start-time=$startAt")
            if (!firstFrame.await(6, TimeUnit.SECONDS)) return emptyList()
            val total = length ?: (mp.status().length() / 1000).toInt()
            val out = mutableListOf<ByteArray>()
            points.forEachIndexed { i, p ->
                if (i > 0) {
                    latest.set(null)
                    mp.controls().setTime((total * p * 1000).toLong())
                    // Se espera un fotograma posterior al salto.
                    val until = System.currentTimeMillis() + 3000
                    while (latest.get() == null && System.currentTimeMillis() < until) Thread.sleep(40)
                    Thread.sleep(250)
                }
                val raw = latest.get() ?: return@forEachIndexed
                val i0 = info.get() ?: return@forEachIndexed
                encodeSmall(Image.makeRaster(i0, raw, rowBytes.get()))?.let { out += it }
            }
            return out
        } finally {
            runCatching { mp.controls().stop() }
            runCatching { mp.release() }
        }
    }

    private fun encodeSmall(img: Image): ByteArray? {
        val w = WIDTH
        val h = (img.height * WIDTH.toFloat() / img.width).toInt().coerceAtLeast(1)
        val s = Surface.makeRasterN32Premul(w, h)
        s.canvas.drawImageRect(img, Rect.makeWH(img.width.toFloat(), img.height.toFloat()), Rect.makeWH(w.toFloat(), h.toFloat()), SamplingMode.LINEAR, null, true)
        return s.makeImageSnapshot().encodeToData(EncodedImageFormat.JPEG, 82)?.bytes
    }

    companion object {
        const val WIDTH = 480
    }
}

/** Carpeta de caché: ~/.cache/aurora, %LOCALAPPDATA%\Aurora\cache o ~/Library/Caches/Aurora. */
internal fun cacheDir(): File {
    val os = System.getProperty("os.name").orEmpty().lowercase()
    val home = System.getProperty("user.home")
    return when {
        "win" in os -> File(System.getenv("LOCALAPPDATA") ?: home, "Aurora/cache")
        "mac" in os -> File(home, "Library/Caches/Aurora")
        else -> File(System.getenv("XDG_CACHE_HOME")?.takeIf { it.isNotBlank() } ?: "$home/.cache", "aurora")
    }
}
