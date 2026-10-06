package app.aurora.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class LibraryTest {
    private fun t(id: String, title: String, artist: String, album: String, year: Int?, added: Long, genre: String? = null, type: MediaType = MediaType.AUDIO) =
        Track(id, title, artist, album, 200, mediaType = type, year = year, dateAddedMs = added, genre = genre)

    private val lib = listOf(
        t("1", "beta", "B", "Uno", 2024, 30, "Rock"),
        t("2", "Alfa", "A", "Dos", 2026, 10, "Pop"),
        t("3", "Gama", "A", "Dos", null, 20, null),
        t("v", "Video", "A", "Dos", 2026, 99, type = MediaType.VIDEO),
    )

    @Test
    fun videosNeverAppearInMusic() {
        assertEquals(listOf("1", "2", "3"), lib.songs().map { it.id })
        assertEquals(listOf("v"), lib.videos().map { it.id })
        assertEquals(2, lib.albums().first { it.name == "Dos" }.tracks.size)
    }

    @Test
    fun sortsByTitleIgnoringCase() {
        assertEquals(listOf("Alfa", "beta", "Gama"), lib.songs().sortedBy(SortOrder.TITLE).map { it.title })
    }

    @Test
    fun sortsByYearNewestFirstWithUnknownLast() {
        assertEquals(listOf("2", "1", "3"), lib.songs().sortedBy(SortOrder.YEAR).map { it.id })
    }

    @Test
    fun sortsByDateAddedNewestFirst() {
        assertEquals(listOf("1", "3", "2"), lib.songs().sortedBy(SortOrder.DATE_ADDED).map { it.id })
    }

    @Test
    fun groupsByArtistAndGenre() {
        assertEquals(listOf("A", "B"), lib.artists().map { it.name })
        assertEquals(listOf("Pop", "Rock", "Sin género"), lib.genres().map { it.name })
    }

    @Test
    fun classifiesFilesByExtension() {
        assertEquals(MediaType.AUDIO, MediaType.fromFileName("00000.MP3"))
        assertEquals(MediaType.AUDIO, MediaType.fromFileName("tema.flac"))
        assertEquals(MediaType.VIDEO, MediaType.fromFileName("clip.mp4"))
        assertEquals(MediaType.VIDEO, MediaType.fromFileName("peli.mkv"))
        assertNull(MediaType.fromFileName("notas.txt"))
        assertNull(MediaType.fromFileName("sin_extension"))
    }
}

class LibrarySortTest {
    private fun t(id: String, title: String, artist: String, year: Int?, dur: Int, added: Long) =
        Track(id, title, artist, "Álbum", dur, year = year, dateAddedMs = added)
    private val lib = listOf(t("1", "Ángel", "Zoe", 2001, 300, 10), t("2", "beso", "Ana", 2020, 120, 30), t("3", "¿Cuándo?", "Ana", null, 200, 20))

    @Test
    fun titleIgnoresAccentsAndPunctuation() {
        assertEquals(listOf("1", "2", "3"), lib.sortedFor(LibrarySort.TITLE, false).map { it.id })
        assertEquals(listOf("3", "2", "1"), lib.sortedFor(LibrarySort.TITLE, true).map { it.id })
        assertEquals(listOf("A", "B", "C"), lib.sortedFor(LibrarySort.TITLE, false).map { it.groupFor(LibrarySort.TITLE) })
    }

    @Test
    fun otherCriteria() {
        assertEquals(listOf("2", "1", "3"), lib.sortedFor(LibrarySort.YEAR, false).map { it.id })
        assertEquals(listOf("2", "3", "1"), lib.sortedFor(LibrarySort.DURATION, false).map { it.id })
        assertEquals(listOf("2", "3", "1"), lib.sortedFor(LibrarySort.ADDED, false).map { it.id })
        assertEquals(listOf("3", "1", "2"), lib.sortedFor(LibrarySort.MOST_PLAYED, false) { if (it.id == "3") 9 else if (it.id == "1") 2 else 0 }.map { it.id })
        assertEquals("Sin año", lib[2].groupFor(LibrarySort.YEAR))
    }
}

class ChatAudioTest {
    @Test fun whatsappAndVoiceNotesAreSkipped() {
        assertTrue(isChatOrVoiceAudio("AUD-20250114-WA0003.opus", "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Audio/AUD-20250114-WA0003.opus"))
        assertTrue(isChatOrVoiceAudio("PTT-20250114-WA0001.opus", null))
        assertTrue(isChatOrVoiceAudio("Grabación 3.m4a", "/storage/emulated/0/Recordings/Grabación 3.m4a"))
    }
    @Test fun regularMusicIsKept() {
        assertFalse(isChatOrVoiceAudio("01 - Neón en la piel.mp3", "/storage/emulated/0/Music/Cielo Prisma/01 - Neón en la piel.mp3"))
        assertFalse(isChatOrVoiceAudio("audio.mp3", "/storage/emulated/0/Download/audio.mp3"))
    }
}

class VideoCaptionTest {
    @Test fun unknownArtistShowsOnlyTheTitle() {
        val t = Track("1", "VID-20260724-WA0082", UNKNOWN_ARTIST, UNKNOWN_ALBUM, 20, MediaType.VIDEO)
        assertEquals("VID-20260724-WA0082", t.videoCaption())
        assertEquals("Reik, Sabes", t.copy(artist = "Reik", title = "Sabes").videoCaption())
    }
}

class SuggestedFoldersTest {
    @Test fun chatCameraAndGifFoldersAreNeverSuggested() {
        listOf(
            "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Animated Gifs",
            "/storage/emulated/0/Telegram/Telegram Video", "/storage/emulated/0/DCIM/Camera",
            "/storage/emulated/0/Pictures/Screenshots", "/home/u/Imágenes/GIFs",
        ).forEach { assertFalse(isSuggestibleFolder(it), it) }
    }
    @Test fun musicFoldersAreSuggested() {
        listOf("/home/u/Música", "/storage/emulated/0/Music", "/storage/emulated/0/Download", "/home/u/Videos/Conciertos", "/home/u/Música/Calle 13")
            .forEach { assertTrue(isSuggestibleFolder(it), it) }
    }
}
