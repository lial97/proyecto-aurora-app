package app.aurora.color

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExtractAccentTest {
    private fun solid(rgb: Int, n: Int = 48 * 48) = IntArray(n) { (0xFF shl 24) or rgb }

    @Test
    fun grayCoverHasNoAccent() {
        assertNull(extractAccent(solid(0x808080)))
    }

    @Test
    fun veryDarkCoverHasNoAccent() {
        assertNull(extractAccent(solid(0x0A0A12)))
    }

    @Test
    fun vividPinkIsDetectedAndAdjustedForDarkBackground() {
        val acc = assertNotNull(extractAccent(solid(0xFF3D8B)))
        assertTrue(hueDistance(acc.h, 335f) < 4f, "tono ${acc.h}")
        assertTrue(acc.s >= 0.65f)
        assertTrue(acc.l in 0.58f..0.72f)
    }

    @Test
    fun dominantVividHueWinsOverSmallAccent() {
        val px = solid(0x202020)
        for (i in 0 until 1200) px[i] = (0xFF shl 24) or 0x3DE0C0 // menta, mayoría viva
        for (i in 1200 until 1300) px[i] = (0xFF shl 24) or 0xFF3D3D // rojo, poco
        val acc = assertNotNull(extractAccent(px))
        assertTrue(hueDistance(acc.h, 168f) < 8f, "tono ${acc.h}")
    }

    @Test
    fun tooFewVividPixelsReturnsNull() {
        val px = solid(0x808080)
        px[0] = (0xFF shl 24) or 0xFF0000
        assertNull(extractAccent(px))
    }

    @Test
    fun rgbToHslRoundTripsPrimaries() {
        assertEquals(0f, rgbToHsl(255, 0, 0).h)
        assertEquals(120f, rgbToHsl(0, 255, 0).h)
        assertEquals(240f, rgbToHsl(0, 0, 255).h)
        assertEquals(0.5f, rgbToHsl(255, 0, 0).l)
    }
}
