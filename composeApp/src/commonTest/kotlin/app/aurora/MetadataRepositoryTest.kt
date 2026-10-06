package app.aurora

import app.aurora.data.MetadataRepository
import app.aurora.domain.MediaType
import app.aurora.domain.Track
import app.aurora.domain.UNKNOWN_ARTIST
import app.aurora.domain.needsCorrection
import app.aurora.platform.HttpClient
import app.aurora.platform.HttpResponse
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Corregir datos (F5): lectura de MusicBrainz, elección del disco, orden por duración y canciones dudosas. */
class MetadataRepositoryTest {
    // Respuesta real de MusicBrainz (recortada) para "Arbol Sin Hojas" de Dread Mar I.
    private val body = """
        {"recordings":[
          {"id":"r1","score":100,"title":"Árbol sin hojas","length":199000,
           "artist-credit":[{"name":"Dread Mar-I","joinphrase":""}],
           "releases":[
             {"id":"live","title":"En vivo","date":"2012-03-01","status":"Official","release-group":{"primary-type":"Album","secondary-types":["Live"]}},
             {"id":"vivi","title":"Vivi en do","date":"2010-01-05","status":"Official","release-group":{"primary-type":"Album"}}
           ]},
          {"id":"r2","score":100,"title":"Árbol sin hojas","length":260000,
           "artist-credit":[{"name":"Dread Mar I","joinphrase":" & "},{"name":"Otro","joinphrase":""}],
           "releases":[{"id":"diez","title":"10 años","date":"2016-05-06","status":"Official","release-group":{"primary-type":"Album","secondary-types":["Compilation"]}}],
           "tags":[{"name":"reggae","count":3},{"name":"latin","count":1}]}
        ]}
    """.trimIndent()

    @Test
    fun readsTitleArtistAlbumAndYear() {
        val list = MetadataRepository.parse(body)
        assertEquals(2, list.size)
        val a = list[0]
        assertEquals("Árbol sin hojas", a.title)
        assertEquals("Dread Mar-I", a.artist)
        // Se prefiere el álbum original al disco en vivo.
        assertEquals("Vivi en do", a.album)
        assertEquals("vivi", a.releaseId)
        assertEquals(2010, a.year)
        assertEquals(199, a.durationSec)
        assertNull(a.genre)
        // Varios artistas con su unión ("&"), y el género más votado.
        assertEquals("Dread Mar I & Otro", list[1].artist)
        assertEquals("Reggae", list[1].genre)
    }

    @Test
    fun durationCloseToTheFileGoesFirst() {
        val list = MetadataRepository.parse(body)
        assertEquals("r2", MetadataRepository.rank(list, 258).first().recordingId)
        assertEquals("r1", MetadataRepository.rank(list, 200).first().recordingId)
    }

    @Test
    fun noConnectionIsNotTheSameAsNoResults() = runTest {
        val offline = MetadataRepository(HttpClient { _, _ -> null })
        assertNull(offline.search("Algo", null).candidates)
        val empty = MetadataRepository(HttpClient { _, _ -> HttpResponse(200, """{"recordings":[]}""") })
        assertEquals(emptyList(), empty.search("Algo", null).candidates)
    }

    @Test
    fun searchSendsTitleAndArtist() = runTest {
        var url = ""
        MetadataRepository(HttpClient { u, _ -> url = u; HttpResponse(200, body) }).search("Arbol Sin Hojas (320)", "Dread Mar I", 199)
        assertTrue("musicbrainz.org/ws/2/recording" in url)
        assertTrue("fmt=json" in url)
        // El "(320)" del nombre del archivo no se envía.
        assertFalse("320" in url)
        assertTrue("Dread%20Mar%20I" in url)
    }

    @Test
    fun suspiciousTracks() {
        fun t(title: String, artist: String) = Track("1", title, artist, "", 200, MediaType.AUDIO)
        assertTrue(t("00000", "Alguien").needsCorrection())
        assertTrue(t("Canción", UNKNOWN_ARTIST).needsCorrection())
        assertFalse(t("Árbol sin hojas", "Dread Mar I").needsCorrection())
    }
}
