package app.aurora.player

/**
 * Linux: despierta la tarjeta de sonido antes de que VLC empiece a sonar.
 *
 * Con el ahorro de energía del controlador (`snd_hda_intel power_save`, 10 s en muchos equipos), la tarjeta se
 * apaga tras unos segundos de silencio y tarda ~0,5 s en volver. Si VLC arranca en ese momento, su audio llega
 * tarde, VLC vacía el búfer varias veces y la primera canción se corta unos 5 s ("playback way too late:
 * flushing buffers"). Medido en Arch con PipeWire: tras 70 s de silencio, 3 vaciados en cada prueba; despertando
 * antes la tarjeta (200 ms de silencio y esperar a que suenen), ninguno.
 */
internal object SoundCardWake {
    private val linux = System.getProperty("os.name").orEmpty().lowercase().contains("linux")
    /** Menos que los 10 s del ahorro de energía: si sonó algo hace menos, la tarjeta sigue despierta. */
    private const val IDLE_NS = 8_000_000_000L

    @Volatile private var playing = false
    @Volatile private var lastSoundNs = 0L

    /** Lo avisa el reproductor al empezar a sonar y al pausar o terminar. */
    fun mark(nowPlaying: Boolean) {
        playing = nowPlaying
        lastSoundNs = System.nanoTime()
    }

    /** Si hace rato que no suena nada, despierta la tarjeta (bloquea ~0,5 s; llamar fuera del hilo de la ventana). */
    fun wakeIfIdle() {
        if (!linux || playing) return
        if (lastSoundNs != 0L && System.nanoTime() - lastSoundNs < IDLE_NS) return
        runCatching {
            val format = javax.sound.sampled.AudioFormat(48_000f, 16, 2, true, false)
            val line = javax.sound.sampled.AudioSystem.getSourceDataLine(format)
            try {
                line.open(format, 48_000)
                line.start()
                val silence = ByteArray(48_000 * 4 / 5) // 200 ms
                line.write(silence, 0, silence.size)
                line.drain()
            } finally {
                line.close()
            }
        }
        lastSoundNs = System.nanoTime()
    }
}
