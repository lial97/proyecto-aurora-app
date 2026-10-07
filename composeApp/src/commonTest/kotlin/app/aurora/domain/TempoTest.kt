package app.aurora.domain

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TempoTest {
    /** Golpes de bombo (seno grave que se apaga) a [bpm] durante [seconds], con un poco de ruido. */
    private fun beats(bpm: Double, seconds: Int = 20, rate: Int = 11025, offbeat: Boolean = false): Pcm {
        val rnd = Random(7)
        val s = FloatArray(seconds * rate) { (rnd.nextFloat() - .5f) * .02f }
        val period = 60.0 / bpm
        var t = 0.0
        var k = 0
        while (t < seconds) {
            val start = (t * rate).toInt()
            val loud = if (offbeat && k % 2 == 1) .4f else 1f
            for (i in 0 until rate / 10) {
                val idx = start + i
                if (idx >= s.size) break
                s[idx] += loud * sin(2 * PI * 60 * i / rate).toFloat() * (1f - i / (rate / 10f))
            }
            t += period; k++
        }
        return Pcm(s, rate)
    }

    private fun near(expected: Int, actual: Int?) = assertTrue(actual != null && abs(actual - expected) <= 2, "esperado ~$expected, salió $actual")

    @Test fun findsCommonTempos() {
        near(120, estimateBpm(beats(120.0)))
        near(90, estimateBpm(beats(90.0)))
        near(128, estimateBpm(beats(128.0)))
        near(100, estimateBpm(beats(100.0, offbeat = true)))
    }

    @Test fun slowAndFastAreFoldedIntoUsualRange() {
        near(130, estimateBpm(beats(65.0)))   // 65 → 130
        near(105, estimateBpm(beats(210.0)))  // 210 → 105
    }

    @Test fun silenceOrShortAudioGivesNothing() {
        assertNull(estimateBpm(Pcm(FloatArray(11025 * 20), 11025)))
        assertNull(estimateBpm(beats(120.0, seconds = 3)))
    }
}
