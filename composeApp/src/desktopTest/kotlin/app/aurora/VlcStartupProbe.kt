package app.aurora

import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.log.LogLevel
import java.io.File
import kotlin.test.Test

/**
 * Diagnóstico (no es una prueba normal): qué pasa con el sonido de la primera canción cuando, al abrir la app,
 * arranca el análisis de tempo. Solo corre con AURORA_VLC_DIR (carpeta "resources" que contiene "vlc") y
 * AURORA_SONG (un archivo de audio):
 *   AURORA_VLC_DIR=.../lib/app/resources AURORA_SONG=~/Music/x.mp3 ./gradlew :composeApp:desktopTest --tests app.aurora.VlcStartupProbe
 * Imprime cuánto tarda en crearse cada VLC y los avisos de VLC sobre el audio que llega tarde o se descarta.
 */
class VlcStartupProbe {
    @Test fun probe() {
        val dir = System.getenv("AURORA_VLC_DIR") ?: return
        val song = System.getenv("AURORA_SONG")?.let(::File)?.takeIf { it.isFile } ?: return
        val parallel = System.getenv("AURORA_PARALLEL")?.toIntOrNull() ?: 4
        System.setProperty("compose.application.resources.dir", dir)
        val t0 = System.nanoTime()
        fun ms() = (System.nanoTime() - t0) / 1_000_000
        check(app.aurora.player.BundledVlc.discover()) { "no se encontró VLC en $dir" }

        val extra = System.getenv("AURORA_VLC_ARGS")?.split(' ')?.filter { it.isNotBlank() } ?: listOf("--audio-resampler=speex_resampler")
        val main = MediaPlayerFactory(*(listOf("--avcodec-hw=none", "--no-video", "--no-audio-time-stretch") + extra).toTypedArray())
        println("[${ms()} ms] VLC del reproductor creado")
        val log = main.application().newLog()
        log.level = LogLevel.WARNING
        log.addLogListener { level, module, _, _, _, _, _, message ->
            if (Regex("late|underrun|discontinu|drop|deadlock|buffer", RegexOption.IGNORE_CASE).containsMatchIn(message))
                println("[${ms()} ms] VLC $level $module: $message")
        }
        app.aurora.player.BundledVlc.cacheDone()
        if (System.getenv("AURORA_WARM") == "app") {
            val w = System.nanoTime()
            app.aurora.player.SoundCardWake.wakeIfIdle()
            println("[${ms()} ms] tarjeta despierta (SoundCardWake) en ${(System.nanoTime() - w) / 1_000_000} ms")
        }
        if (System.getenv("AURORA_WARM") == "1") {
            // Despertar la tarjeta de sonido: un poco de silencio y esperar a que termine de sonar.
            val w = System.nanoTime()
            val fmt = javax.sound.sampled.AudioFormat(48000f, 16, 2, true, false)
            val line = javax.sound.sampled.AudioSystem.getSourceDataLine(fmt)
            line.open(fmt, 48000); line.start()
            line.write(ByteArray(48000 * 4 / 5), 0, 48000 * 4 / 5) // 200 ms
            line.drain(); line.close()
            println("[${ms()} ms] tarjeta despierta en ${(System.nanoTime() - w) / 1_000_000} ms")
        }
        val mp = main.mediaPlayers().newMediaPlayer()
        mp.media().start(song.absolutePath)
        mp.audio().isMute = true
        println("[${ms()} ms] suena la canción")
        Thread.sleep(1500)

        // Lo que hace la app al terminar de leer la biblioteca: el VLC de fondo y varias canciones a la vez.
        val bg = System.nanoTime()
        val decoder = app.aurora.platform.TempoDecoder()
        println("[${ms()} ms] VLC de fondo ${if (decoder.available) "creado" else "NO disponible"} en ${(System.nanoTime() - bg) / 1_000_000} ms")
        val songs = song.parentFile.walkTopDown().filter { it.isFile && it.extension.lowercase() in setOf("mp3", "flac", "m4a", "ogg") }.take(parallel * 2).toList()
        val workers = (0 until parallel).map { w ->
            Thread {
                songs.filterIndexed { i, _ -> i % parallel == w }.forEach { f ->
                    val s = System.nanoTime()
                    val pcm = decoder.decode(f, 200)
                    println("[${ms()} ms] tempo ${f.name.take(30)}: ${pcm?.samples?.size ?: 0} muestras en ${(System.nanoTime() - s) / 1_000_000} ms")
                }
            }.apply { start() }
        }
        workers.forEach { it.join() }
        if (parallel == 0) Thread.sleep(13_000) // línea base: la canción sola, sin análisis
        Thread.sleep(1000)
        println("[${ms()} ms] fin; la canción va por ${mp.status().time()} ms")
        mp.release(); log.release(); main.release()
    }
}
