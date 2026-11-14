package app.aurora.data

import app.aurora.domain.MediaType
import app.aurora.domain.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LibraryCacheTest {
    private val folders = listOf("AUDIO:/m", "VIDEO:/v")
    private val song = Track(
        "a", "Canción \"con\" comillas", "Artista", "Álbum", 237, MediaType.AUDIO, bpm = 120, genre = "Pop", year = 2009,
        releaseDate = "2009-03-01", dateAddedMs = 1_700_000_000_000L, filePath = "/m/a.mp3", coverUri = "embedded",
        format = "MP3", bitrateKbps = 320, sampleRateHz = 44100, bitsPerSample = 16, plainLyrics = "línea 1\nlínea 2",
    )
    private val video = Track("v", "Clip", "Artista desconocido", "Álbum desconocido", 106, MediaType.VIDEO, filePath = "/v/clip.mp4")

    @Test fun roundTripKeepsTheFields() {
        val back = LibraryCache.decode(LibraryCache.encode(listOf(song, video), folders), folders)
        assertEquals(listOf(song, video), back)
    }

    @Test fun otherFoldersIgnoreTheCache() =
        assertNull(LibraryCache.decode(LibraryCache.encode(listOf(song), folders), listOf("AUDIO:/m")))

    @Test fun damagedOrOldFilesAreIgnored() {
        assertNull(LibraryCache.decode("{no es json", folders))
        assertNull(LibraryCache.decode("""{"v":0,"carpetas":["AUDIO:/m","VIDEO:/v"],"pistas":[]}""", folders))
        assertNull(LibraryCache.decode("""{"v":1,"carpetas":["AUDIO:/m","VIDEO:/v"],"pistas":[{"id":"x"}]}""", folders))
    }
}
