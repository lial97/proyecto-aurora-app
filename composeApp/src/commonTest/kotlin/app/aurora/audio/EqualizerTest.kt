package app.aurora.audio

import kotlin.test.Test
import kotlin.test.assertEquals

class EqualizerTest {
    @Test
    fun eighteenVlcPresetsWithTenBands() {
        assertEquals(18, Eq.presets.size)
        Eq.presets.forEach { assertEquals(10, it.bands.size, it.name) }
        assertEquals(0f, Eq.presets.first().preamp, "Plano = volumen original")
    }

    @Test
    fun protectionPreventsClipping() {
        val rock = EqSettings(enabled = true).withPreset(Eq.presets.indexOfFirst { it.name == "Rock" })
        assertEquals(-7f, rock.preamp)        // VLC: 5 - 12
        // Graves +8 (×2,5 = 20), agudos +11,2 (×1,5 = 16,8), 3 vecinas 11,2×3 (×1,6 = 53,8): se limita al rango de VLC.
        assertEquals(-20f, rock.vlcPreamp, 0.001f)
        assertEquals(25f, rock.protectionDb, 0.001f) // -7 → -32 (límite de VLC)
        val live = EqSettings(enabled = true).withPreset(Eq.presets.indexOfFirst { it.name == "En vivo" })
        // En vivo: 5,6 + 5,6 + 5,6 = 16,8 × 1,6 = 26,9 dB.
        assertEquals(-26.88f, live.effectivePreamp, 0.01f)
        assertEquals(-7f, rock.copy(protect = false).effectivePreamp)
    }

    @Test
    fun movingABandMakesItCustomAndRoundTrips() {
        val e = EqSettings(enabled = true).withPreset(3).withBand(2, 4.26f)
        assertEquals(-1, e.preset)
        assertEquals(4.3f, e.bands[2])
        assertEquals(e, EqSettings.decode(e.encode()))
        assertEquals(EqSettings(), EqSettings.decode("basura"))
    }
}
