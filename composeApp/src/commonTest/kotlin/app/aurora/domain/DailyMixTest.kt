package app.aurora.domain

import app.aurora.data.DailyMixRepository
import app.aurora.data.MixAnswer
import app.aurora.platform.MemoryStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DailyMixTest {
    private val day = 86_400_000L
    private val now = 1_800_000_000_000L

    /** 60 canciones: 10 artistas de rock, pop y salsa (6 de cada uno) y 2 videos. */
    private val library: List<Track> = (0 until 60).map { i ->
        val genre = listOf("Rock", "Pop", "Salsa")[i % 3]
        Track("t$i", "Canción $i", "Artista ${i % 10}", "Álbum", 200, genre = genre, dateAddedMs = i.toLong())
    } + listOf(Track("v1", "Video", "Artista 1", "", 300, MediaType.VIDEO), Track("v2", "Video 2", "Artista 2", "", 300, MediaType.VIDEO))

    @Test fun prioritizesWhatIsHeardMost() {
        // Mucho rock (artistas 0, 3, 6, 9 tocan rock: i % 3 == 0) escuchado hace 10 días.
        val history = { id: String ->
            val i = id.drop(1).toIntOrNull() ?: -1
            if (i >= 0 && i % 3 == 0 && i < 30) PlayHistory(8, now - 10 * day) else PlayHistory(0, null)
        }
        val mix = DailyMix.build(library, history, emptySet(), now, "2026-10-07")
        assertEquals(DailyMix.SIZE, mix.size)
        val rock = mix.count { it.genre == "Rock" }
        assertTrue(rock > mix.size / 2, "la mezcla debe ser sobre todo rock (fue $rock de ${mix.size})")
        assertTrue(mix.none { it.mediaType == MediaType.VIDEO }, "sin videos")
        assertTrue(mix.groupBy { it.artist }.values.all { it.size <= DailyMix.MAX_PER_ARTIST }, "como mucho 3 por artista")
    }

    @Test fun sameDaySameMixNextDayDifferent() {
        val history = { _: String -> PlayHistory(0, null) }
        val a = DailyMix.build(library, history, emptySet(), now, "2026-10-07").map { it.id }
        val b = DailyMix.build(library, history, emptySet(), now, "2026-10-07").map { it.id }
        val c = DailyMix.build(library, history, emptySet(), now + day, "2026-10-08").map { it.id }
        assertEquals(a, b)
        assertNotEquals(a, c)
        assertTrue(a.isNotEmpty(), "sin historial también hay mezcla")
    }

    @Test fun nameFromGenres() {
        fun tracks(vararg g: Pair<String, Int>) = g.flatMap { (genre, n) -> (0 until n).map { Track("$genre$it", "x", "a", "b", 1, genre = genre) } }
        assertEquals("Rock/Pop", DailyMix.name(tracks("Rock" to 18, "Pop" to 3)))
        assertEquals("Rock", DailyMix.name(tracks("Rock" to 24, "Pop" to 1)))
        assertEquals("Latin Music/Salsa/Pop", DailyMix.name(tracks("Latin Music" to 10, "Salsa" to 8, "Pop" to 7)))
        // Sin géneros: los artistas que más aparecen.
        val noGenre = (0 until 10).map { Track("n$it", "x", if (it < 6) "Juan Gabriel" else "Rocío Dúrcal", "b", 1) }
        assertEquals("Juan Gabriel/Rocío Dúrcal", DailyMix.name(noGenre))
        assertEquals("Mezcla variada", DailyMix.name(listOf(Track("a", "x", UNKNOWN_ARTIST, "b", 1))))
    }

    @Test fun repositoryKeepsTheDayAndReplacesTheNext() {
        val repo = DailyMixRepository(MemoryStore())
        val first = repo.ensure("2026-10-07") { listOf("a", "b", "c") }
        assertEquals(MixAnswer.PENDING, first.answer)
        // El mismo día no se vuelve a crear.
        assertEquals(listOf("a", "b", "c"), repo.ensure("2026-10-07") { listOf("x") }.trackIds)
        assertEquals(1, repo.recordPlay("a")); assertEquals(2, repo.recordPlay("b")); assertEquals(2, repo.recordPlay("z"))
        repo.answer(false)
        // Al día siguiente: una nueva, que recibe la anterior para no repetirla.
        var previous: Set<String> = emptySet()
        val next = repo.ensure("2026-10-08") { prev -> previous = prev; listOf("d", "e") }
        assertEquals(setOf("a", "b", "c"), previous)
        assertEquals(listOf("d", "e"), next.trackIds)
        assertEquals(MixAnswer.PENDING, next.answer)
        assertEquals(0, next.played)
    }
}
