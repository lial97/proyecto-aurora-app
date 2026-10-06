package app.aurora.data

import app.aurora.audio.EqSettings
import app.aurora.platform.MemoryStore
import kotlin.test.Test
import kotlin.test.assertEquals

class PrefsTest {
    @Test
    fun prefsSurviveRestart() {
        val store = MemoryStore()
        PrefsRepository(store).update {
            it.copy(fadeSec = 4, endOfQueue = EndOfQueue.REPEAT, textScale = 1.2f, reduceMotion = true, eq = EqSettings(enabled = true).withPreset(13))
        }
        val p = PrefsRepository(store).prefs.value
        assertEquals(4, p.fadeSec)
        assertEquals(EndOfQueue.REPEAT, p.endOfQueue)
        assertEquals(1.2f, p.textScale)
        assertEquals(true, p.reduceMotion)
        assertEquals(13, p.eq.preset)
        assertEquals(true, p.eq.enabled)
    }

    @Test
    fun statsAndOffsets() {
        val store = MemoryStore()
        val s = PlayStatsRepository(store)
        s.record("a", 1000); s.record("a", 5000)
        assertEquals(PlayStatsRepository.Stats(2, 5000), s.get("a"))
        val o = LyricsOffsetRepository(store)
        o.set("a", -500)
        assertEquals(-500, LyricsOffsetRepository(store).get("a"))
    }
}
