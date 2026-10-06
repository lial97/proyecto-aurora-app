package app.aurora.data

import app.aurora.domain.Track
import app.aurora.platform.MemoryStore
import kotlin.test.Test
import kotlinx.coroutines.launch
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaylistRepositoryTest {
    private fun t(id: String) = Track(id, "T$id", "A", "B", 100)

    @Test
    fun createAddRemoveAndSurviveRestart() {
        val store = MemoryStore()
        val repo = PlaylistRepository(store)
        val p = repo.create("Para correr", listOf(t("/m/a.mp3")))
        assertEquals(2, repo.add(p.id, listOf(t("/m/a.mp3"), t("/m/b.mp3"), t("/m/c.mp3"))))
        repo.remove(p.id, "/m/b.mp3")
        repo.rename(p.id, "Correr")
        val again = PlaylistRepository(store).get(p.id)!!
        assertEquals("Correr", again.name)
        assertEquals(listOf("/m/a.mp3", "/m/c.mp3"), again.trackIds)
        assertTrue(again.isUser)
    }

    @Test
    fun reorderAndDelete() {
        val repo = PlaylistRepository(MemoryStore())
        val p = repo.create("X", listOf(t("1"), t("2"), t("3")))
        repo.move(p.id, 0, 2)
        assertEquals(listOf("2", "3", "1"), repo.get(p.id)!!.trackIds)
        repo.delete(p.id)
        assertEquals(emptyList(), repo.playlists.value)
    }

    @Test
    fun strangeCharactersRoundTrip() {
        val repo = PlaylistRepository(MemoryStore())
        val weird = "C:\\Música\\100% éxitos\tvol 1\n.mp3"
        val p = repo.create("Lista\tcon %\nraros", listOf(t(weird)))
        val decoded = PlaylistCodec.decode(PlaylistCodec.encode(repo.playlists.value)).single()
        assertEquals(p.name, decoded.name)
        assertEquals(listOf(weird), decoded.trackIds)
    }

    @Test
    fun likesArePersisted() {
        val store = MemoryStore()
        val repo = PlaylistRepository(store)
        assertTrue(repo.toggleLike("/m/a.mp3"))
        assertTrue(PlaylistRepository(store).liked.value.contains("/m/a.mp3"))
        assertFalse(repo.toggleLike("/m/a.mp3"))
        assertFalse(PlaylistRepository(store).liked.value.contains("/m/a.mp3"))
    }
}

class MixedPlaylistTest {
    @Test
    fun songsAndVideosLiveTogetherInOrder() {
        val song = app.aurora.domain.Track("/m/a.mp3", "Canción", "A", "B", 200)
        val video = app.aurora.domain.Track("/v/b.mp4", "Video", "A", "B", 300, mediaType = app.aurora.domain.MediaType.VIDEO)
        val song2 = app.aurora.domain.Track("/m/c.mp3", "Otra", "A", "B", 180)
        val repo = PlaylistRepository(MemoryStore())
        val p = repo.create("Mixta", listOf(song))
        repo.add(p.id, listOf(video, song2))
        val resolved = repo.get(p.id)!!.let { pl ->
            pl.trackIds.mapNotNull { id -> listOf(song, video, song2).associateBy { it.id }[id] }
        }
        assertEquals(listOf("Canción", "Video", "Otra"), resolved.map { it.title })
        assertEquals(app.aurora.domain.MediaType.VIDEO, resolved[1].mediaType)
    }
}

class CoverCacheConcurrencyTest {
    @Test
    fun manyThreadsAtOnce() = kotlinx.coroutines.test.runTest {
        val cache = app.aurora.components.CoverCache(capacity = 5) { kotlinx.coroutines.yield(); null }
        val tracks = (0 until 50).map { app.aurora.domain.Track("t$it", "T", "A", "B", 1, coverUri = "x") }
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val jobs = (0 until 400).map { i -> launch { cache.get(tracks[i % 50]) } }
            jobs.forEach { it.join() }
        }
    }
}
