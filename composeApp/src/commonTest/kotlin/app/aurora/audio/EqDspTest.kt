package app.aurora.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EqDspTest {
    private val rate = 44100

    /** Seno estéreo de [hz] a amplitud [amp]; devuelve el nivel (dB) del canal izquierdo tras ecualizar. */
    private fun levelDb(eq: EqSettings?, hz: Double, amp: Float = .25f): Double {
        val dsp = TenBandEq(rate, 2).apply { settings = eq }
        val n = rate // 1 s
        val buf = FloatArray(n * 2) { i -> (amp * sin(2 * PI * hz * (i / 2) / rate)).toFloat() }
        dsp.process(buf)
        // Se mide la segunda mitad (ya estable).
        var sum = 0.0; var k = 0
        for (i in n until n * 2 step 2) { sum += buf[i] * buf[i]; k++ }
        val rms = sqrt(sum / k)
        return 20 * log10(rms / (amp / sqrt(2.0)))
    }

    @Test fun offOrFlatLeavesTheSoundUntouched() {
        assertEquals(0.0, levelDb(null, 1000.0), .01)
        assertEquals(0.0, levelDb(EqSettings(enabled = false, bands = List(10) { 6f }), 1000.0), .01)
        assertEquals(0.0, levelDb(EqSettings(enabled = true), 1000.0), .01)
    }

    @Test fun boostingOneBandRaisesThatFrequency() {
        // +6 dB en 1 kHz sin protección: un seno de 1 kHz sube ~6 dB y uno de 62 Hz casi no cambia.
        val eq = EqSettings(enabled = true, protect = false, bands = List(10) { if (it == 5) 6f else 0f })
        assertTrue(abs(levelDb(eq, 1000.0) - 6.0) < .5, "1 kHz sube 6 dB")
        assertTrue(abs(levelDb(eq, 62.0)) < .5, "62 Hz no cambia")
    }

    @Test fun cuttingABandLowersIt() {
        val eq = EqSettings(enabled = true, bands = List(10) { if (it == 1) -10f else 0f })
        assertTrue(levelDb(eq, 62.0) < -8.5)
    }

    @Test fun neverClipsEvenWithEveryBandAtMax() {
        val eq = EqSettings(enabled = true, protect = false, bands = List(10) { 20f })
        val dsp = TenBandEq(rate, 2).apply { settings = eq }
        val buf = FloatArray(rate * 2) { i -> (.9 * sin(2 * PI * 100.0 * (i / 2) / rate)).toFloat() }
        dsp.process(buf)
        assertTrue(buf.all { it in -1f..1f })
    }

    @Test fun protectionKeepsAPresetBelowFullScale() {
        // Con protección, el preset más fuerte en graves no pasa de 0 dBFS con un seno grave a −6 dBFS.
        val i = Eq.presets.indices.maxBy { Eq.presets[it].bands.take(3).max() }
        val eq = EqSettings(enabled = true).withPreset(i)
        val dsp = TenBandEq(rate, 2).apply { settings = eq }
        val buf = FloatArray(rate * 2) { k -> (.5 * sin(2 * PI * 62.0 * (k / 2) / rate)).toFloat() }
        dsp.process(buf)
        assertTrue(buf.maxOf { abs(it) } < .999f, "sin recorte: ${buf.maxOf { abs(it) }}")
    }
}
