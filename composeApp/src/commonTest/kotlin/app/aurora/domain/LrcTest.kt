package app.aurora.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LrcTest {
    @Test
    fun parsesTimedLinesAndInstrumentalGaps() {
        val lrc = """
            [ar:Cielo Prisma]
            [00:00.00]
            [00:08.20]Se apagan las ventanas de la avenida
            [01:05.5]tu sombra baila sobre la pared
        """.trimIndent()
        assertEquals(
            listOf(
                LyricLine(0, LyricLine.INSTRUMENTAL),
                LyricLine(8_200, "Se apagan las ventanas de la avenida"),
                LyricLine(65_500, "tu sombra baila sobre la pared"),
            ),
            parseLrc(lrc),
        )
    }

    @Test
    fun repeatedChorusWithSeveralTimestamps() {
        val lines = parseLrc("[00:29.00][01:17.00]Neón en la piel, no me sueltes")
        assertEquals(listOf(29, 77), lines.map { it.startSec })
    }

    @Test
    fun offsetShiftsAllLines() {
        val lines = parseLrc("[offset:+500]\n[00:10.00]hola")
        assertEquals(9_500, lines.single().startMs)
    }

    @Test
    fun enhancedLrcKeepsWordTimes() {
        val l = parseLrc("[00:12.00]<00:12.00>Abrázame <00:12.80>muy <00:13.20>fuerte").single()
        assertEquals("Abrázame muy fuerte", l.text)
        assertEquals(listOf(12_000L, 12_800L, 13_200L), l.words.map { it.startMs })
    }

    @Test
    fun activeLineFollowsPosition() {
        val lines = listOf(LyricLine(0, "♪"), LyricLine(8_000, "a"), LyricLine(13_000, "b"))
        assertEquals(0, lines.activeIndexAt(5))
        assertEquals(1, lines.activeIndexAt(8))
        assertEquals(2, lines.activeIndexAt(100))
        assertEquals(-1, listOf(LyricLine(3_000, "x")).activeIndexAt(1))
    }

    @Test
    fun karaokeFillsLineProgressively() {
        val lines = listOf(LyricLine(1_000, "hola mundo"), LyricLine(5_000, "otra"))
        assertEquals(0f, lines.sungChars(0, 900))
        val mid = lines.sungChars(0, 1_400)
        assertTrue(mid > 0f && mid < 10f, "a mitad: $mid")
        assertEquals(10f, lines.sungChars(0, 4_900))
    }

    @Test
    fun karaokeUsesWordTimesWhenPresent() {
        val lines = parseLrc("[00:10.00]<00:10.00>uno <00:11.00>dos\n[00:14.00]fin")
        // A los 11,0 s ya se cantó "uno " y empieza "dos".
        val c = lines.sungChars(0, 11_000)
        assertTrue(c >= 3f && c < 5f, "c=$c")
    }
}
