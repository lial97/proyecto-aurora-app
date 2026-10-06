package app.aurora.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MixesTest {
    private fun t(id: String, artist: String, added: Long, type: MediaType = MediaType.AUDIO) =
        Track(id, "T$id", artist, "A", 100, mediaType = type, dateAddedMs = added)

    @Test
    fun buildsMixesOnlyFromSongs() {
        val lib = listOf(t("1", "Ana", 1), t("2", "Ana", 2), t("3", "Luis", 3), t("v", "Ana", 9, MediaType.VIDEO))
        val mixes = autoMixes(lib, liked = listOf("3"))
        assertEquals("Me gusta", mixes.first().name)
        assertEquals(listOf("3"), mixes.first().trackIds)
        assertEquals(listOf("3", "2", "1"), mixes.first { it.id == "mix-recent" }.trackIds)
        assertTrue(mixes.any { it.name == "Lo mejor de Ana" })
        assertTrue(mixes.none { "v" in it.trackIds })
    }

    @Test
    fun emptyLibraryHasNoMixes() {
        assertEquals(emptyList(), autoMixes(emptyList(), emptyList()))
    }
}
