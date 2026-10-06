package app.aurora.color

import kotlin.test.Test
import kotlin.test.assertEquals

class PaletteTest {
    @Test
    fun coverAccentWinsWhenPresent() {
        val cover = Hsl(150f, 0.7f, 0.6f)
        val p = buildPalette(118, cover)
        assertEquals(AccentSource.COVER, p.accentSource)
        assertEquals(cover.toColor(), p.accent)
    }

    @Test
    fun tempoAccentIsUsedWithoutCover() {
        val p = buildPalette(70, null)
        assertEquals(AccentSource.TEMPO, p.accentSource)
        assertEquals(Hsl(235f, 0.95f, 0.67f).toColor(), p.accent)
    }

    @Test
    fun atmosphereAlwaysComesFromTempo() {
        val a = buildPalette(80, null)
        val b = buildPalette(80, Hsl(10f, 0.9f, 0.6f))
        assertEquals(a.background, b.background)
        assertEquals(a.light1, b.light1)
        assertEquals(a.light2, b.light2)
        assertEquals(a.light3, b.light3)
    }
}
