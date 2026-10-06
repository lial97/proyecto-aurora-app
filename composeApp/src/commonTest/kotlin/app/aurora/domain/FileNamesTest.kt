package app.aurora.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileNamesTest {
    @Test
    fun titleThenArtistWithQualityTag() {
        assertEquals(NameGuess("Abrázame Muy Fuerte", "Juan Gabriel"), guessFromFileName("Abrázame Muy Fuerte - Juan Gabriel (320).mp3"))
    }

    @Test
    fun keepsMeaningfulParentheses() {
        assertEquals(
            NameGuess("Agora Eu Sei (Versão Remasterizada)", "Roberto Carlos"),
            guessFromFileName("Agora Eu Sei (Versão Remasterizada) - Roberto Carlos (320).mp3"),
        )
    }

    @Test
    fun stripsVideoQualityAndUnderscores() {
        assertEquals(
            NameGuess("Curso Completo de Java desde Cero para Principiantes", null),
            guessFromFileName("Curso Completo de Java desde Cero para Principiantes(1080P_HD).mp4"),
        )
    }

    @Test
    fun numericNameNeedsCorrection() {
        val g = guessFromFileName("00000.mp3")
        assertEquals(NameGuess("00000", null), g)
        assertTrue(Track("x", g.title, g.artist ?: UNKNOWN_ARTIST, UNKNOWN_ALBUM, 0).needsCorrection())
        assertFalse(Track("y", "Amigo", "Roberto Carlos", "Álbum", 0).needsCorrection())
    }

    @Test
    fun dropsLeadingTrackNumber() {
        assertEquals(NameGuess("Satélite", "Mar Abierto"), guessFromFileName("03 - Satélite - Mar Abierto.flac"))
    }
}

class FormatTimeTest {
    @Test
    fun minutesAndHours() {
        assertEquals("3:48", app.aurora.components.formatTime(228))
        assertEquals("0:05", app.aurora.components.formatTime(5))
        assertEquals("5:51:25", app.aurora.components.formatTime(21085))
    }
}
