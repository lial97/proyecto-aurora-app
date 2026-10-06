package app.aurora.color

import kotlin.test.Test
import kotlin.test.assertEquals

class MoodTest {
    @Test
    fun slowSongsAreBlueAndCalm() {
        val m = moodFromBpm(70)
        assertEquals(0f, m.energy)
        assertEquals(225f, m.hue)
        assertEquals(MoodLabel.CALM, m.label)
        assertEquals(1.45f, m.speed, 1e-4f)
    }

    @Test
    fun fastSongsAreRedAndUpbeat() {
        val m = moodFromBpm(140)
        assertEquals(1f, m.energy)
        assertEquals(360f, m.hue)
        assertEquals(MoodLabel.UPBEAT, m.label)
        assertEquals(0.60f, m.speed, 1e-4f)
    }

    @Test
    fun catalogExamplesMatchSpec() {
        // Tabla de la spec §3.1
        assertEquals(MoodLabel.CALM, moodFromBpm(92).label)
        assertEquals(MoodLabel.MEDIUM, moodFromBpm(106).label)
        assertEquals(MoodLabel.MEDIUM, moodFromBpm(112).label)
        assertEquals(MoodLabel.UPBEAT, moodFromBpm(118).label)
    }

    @Test
    fun missingBpmFallsBackToMediumTempo() {
        val m = moodFromBpm(null)
        assertEquals(DEFAULT_BPM, m.bpm)
        assertEquals(MoodLabel.MEDIUM, m.label)
    }
}
