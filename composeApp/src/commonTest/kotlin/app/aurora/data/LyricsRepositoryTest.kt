package app.aurora.data

import app.aurora.domain.Track
import app.aurora.platform.HttpClient
import app.aurora.platform.HttpResponse
import app.aurora.platform.MemoryStore
import app.aurora.platform.MemoryTextCache
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LyricsRepositoryTest {
    private val song = Track("/m/a.mp3", "Acompáñame a Estar Solo", "Ricardo Arjona", "Adentro", 273, filePath = "/m/a.mp3")

    private fun candidate(dur: Double, synced: String?, name: String = "Acompáñame a estar solo") =
        """{"trackName":"$name","artistName":"Ricardo Arjona","duration":$dur,"instrumental":false,"plainLyrics":"letra","syncedLyrics":${synced?.let { "\"$it\"" } ?: "null"}}"""

    @Test
    fun cleansTitlesForSearch() {
        assertEquals("Por Amor", LyricsRepository.cleanTitle("Por Amor (Versão Remasterizada)"))
        assertEquals("Así Fue", LyricsRepository.cleanTitle("Así Fue (En Vivo Desde Bellas Artes)"))
        assertEquals("Yesterday", LyricsRepository.cleanTitle("Yesterday - Remastered 2009"))
        assertEquals("Amigos", LyricsRepository.cleanTitle("Amigos feat. Alex Campos"))
    }

    @Test
    fun picksSyncedVersionWithClosestDuration() {
        val list = LyricsRepository.parseList("[" + listOf(
            candidate(367.0, "[00:01.00]larga"),
            candidate(275.0, "[00:01.00]cercana"),
            candidate(273.5, null),
            candidate(272.6, "[00:01.00]la mejor"),
        ).joinToString(",") + "]")
        assertEquals(272.6, LyricsRepository.pickBest(list, 273, song.title)!!.durationSec)
    }

    @Test
    fun fallsBackToSearchWhenExactMatchMissingAndCaches() = runTest {
        val calls = mutableListOf<String>()
        val http = HttpClient { url, _ ->
            calls += url
            when {
                "/get?" in url -> HttpResponse(404, "{}")
                "/search?" in url -> HttpResponse(200, "[" + candidate(273.0, "[00:01.93]Acompáñame a estar solo\\n[00:06.52]A purgarme los fantasmas") + "]")
                else -> null
            }
        }
        val cache = MemoryTextCache()
        val repo = LyricsRepository(http, cache, MemoryStore())
        val st = repo.load(song)
        assertIs<LyricsState.Found>(st)
        assertTrue(st.synced)
        assertEquals(2, st.lines.size)
        assertEquals(1_930, st.lines.first().startMs)
        // La segunda vez sale de la caché, sin red.
        val n = calls.size
        assertIs<LyricsState.Found>(repo.load(song))
        assertEquals(n, calls.size)
    }

    @Test
    fun noNetworkIsNotCachedAsMissing() = runTest {
        val repo = LyricsRepository(HttpClient { _, _ -> null }, MemoryTextCache(), MemoryStore())
        val st = repo.load(song)
        assertIs<LyricsState.NotFound>(st)
        assertTrue("conexión" in st.reason)
    }

    @Test
    fun respectsAutoDownloadSwitch() = runTest {
        val repo = LyricsRepository(HttpClient { _, _ -> error("no debe llamar") }, MemoryTextCache(), MemoryStore())
        repo.setAutoDownload(false)
        assertIs<LyricsState.Disabled>(repo.load(song))
    }
}
