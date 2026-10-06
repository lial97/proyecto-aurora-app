package app.aurora.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import app.aurora.audio.EqSettings
import app.aurora.audio.TenBandEq
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Ajuste del ecualizador que comparten la app y el servicio de reproducción (mismo proceso). */
object AndroidEqualizer {
    @Volatile var settings: EqSettings? = null
        set(value) { field = value; version++ }
    @Volatile var version = 0
        private set
}

/**
 * Ecualizador de 10 bandas dentro del reproductor de Media3: recibe el audio decodificado (PCM de 16 bits
 * o float) y le aplica [TenBandEq] antes de enviarlo al altavoz. Con el ecualizador apagado no toca nada.
 */
@UnstableApi
class EqAudioProcessor : BaseAudioProcessor() {
    private var dsp: TenBandEq? = null
    private var seen = -1
    private var floats = FloatArray(0)

    override fun onConfigure(input: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (input.encoding != C.ENCODING_PCM_16BIT && input.encoding != C.ENCODING_PCM_FLOAT) throw AudioProcessor.UnhandledAudioFormatException(input)
        dsp = TenBandEq(input.sampleRate, input.channelCount)
        seen = -1
        return input
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val eq = dsp ?: return
        // Un ajuste nuevo de la app se aplica desde el siguiente bloque de audio.
        if (seen != AndroidEqualizer.version) { seen = AndroidEqualizer.version; eq.settings = AndroidEqualizer.settings }
        val bytes = inputBuffer.remaining()
        if (bytes == 0) return
        val out = replaceOutputBuffer(bytes)
        val float = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        val n = if (float) bytes / 4 else bytes / 2
        if (!eq.active) { out.put(inputBuffer); out.flip(); return }
        if (floats.size < n) floats = FloatArray(n)
        val src = inputBuffer.order(ByteOrder.nativeOrder())
        if (float) for (i in 0 until n) floats[i] = src.getFloat()
        else for (i in 0 until n) floats[i] = src.getShort() / 32768f
        eq.process(floats, n)
        if (float) for (i in 0 until n) out.putFloat(floats[i])
        else for (i in 0 until n) out.putShort((floats[i] * 32767f).toInt().coerceIn(-32768, 32767).toShort())
        out.flip()
    }

    override fun onFlush() { dsp?.reset() }
    override fun onReset() { dsp = null }
}
