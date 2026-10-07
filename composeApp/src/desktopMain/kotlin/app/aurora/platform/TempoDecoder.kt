package app.aurora.platform

import app.aurora.domain.Pcm
import com.sun.jna.Pointer
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.base.callback.AudioCallbackAdapter
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Fragmento de una canción para calcular su tempo, con VLC: el audio va a memoria (no suena) y se reproduce a
 * ×[SPEED] sin conservar el tono, así 20 s de canción tardan ~7 s. Con [SPEED] la frecuencia efectiva es
 * [RATE] / [SPEED] (≈10,7 kHz), suficiente para encontrar los golpes.
 */
class TempoDecoder(private val speed: Float = SPEED) {
    private val factory: MediaPlayerFactory? get() = app.aurora.player.BundledVlc.backgroundFactory

    val available: Boolean get() = factory != null

    fun decode(file: File, durationSec: Int): Pcm? {
        val f = factory ?: return null
        val want = (SECONDS * RATE / speed).toInt()
        val buf = ShortArray(want)
        var n = 0
        val done = CountDownLatch(1)
        val mp = f.mediaPlayers().newMediaPlayer()
        return try {
            mp.audio().callback("S16N", RATE, 1, object : AudioCallbackAdapter() {
                override fun play(mediaPlayer: MediaPlayer, samples: Pointer, sampleCount: Int, pts: Long) {
                    if (n >= want) return
                    val take = minOf(sampleCount, want - n)
                    samples.read(0, buf, n, take)
                    n += take
                    if (n >= want) done.countDown()
                }
            })
            mp.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
                override fun finished(mediaPlayer: MediaPlayer) = done.countDown()
                override fun error(mediaPlayer: MediaPlayer) = done.countDown()
            })
            val start = (durationSec / 2 - SECONDS / 2).coerceAtLeast(0)
            if (!mp.media().start(file.absolutePath, ":start-time=$start", ":no-video", ":rate=$speed")) return null
            done.await((SECONDS / speed + 6).toLong(), TimeUnit.SECONDS)
            val got = n
            if (got < 6 * RATE / speed) null else Pcm(FloatArray(got) { buf[it] / 32768f }, (RATE / speed).toInt())
        } finally {
            runCatching { mp.controls().stop() }
            runCatching { mp.release() }
        }
    }

    private companion object {
        const val RATE = 32000
        const val SPEED = 3f
        const val SECONDS = 20
    }
}
