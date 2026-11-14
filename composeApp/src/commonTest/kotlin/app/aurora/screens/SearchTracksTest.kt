package app.aurora.screens

import app.aurora.domain.MediaType
import app.aurora.domain.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchTracksTest {
    private val song = Track(
        "a1", "Neón en la piel", "Cielo Prisma", "Luz de madrugada", 200,
        genre = "Synthpop", filePath = "/música/Neon En La Piel.flac",
    )
    private val hiddenSong = Track(
        "a2", "Título sin pista", "Otra artista", "Otro disco", 180,
        filePath = "/música/Pista Secreta.mp3",
    )
    private val video = Track(
        "v1", "Neón en la piel (video oficial)", "Cielo Prisma", "Luz de madrugada", 236,
        mediaType = MediaType.VIDEO, filePath = "/videos/neon_video.mp4",
    )
    private val hiddenVideo = Track(
        "v2", "Un video cualquiera", "Alguien", "Varios", 100,
        mediaType = MediaType.VIDEO, filePath = "/videos/grabacion_casa.mkv",
    )
    private val tracks = listOf(song, hiddenSong, video, hiddenVideo)

    @Test
    fun allBuscaMusicaYVideoSinMirarElArchivo() {
        assertEquals(listOf(song, video), searchTracks(tracks, "neón"))
        assertEquals(listOf(song, video), searchTracks(tracks, "CIELO"))
        assertEquals(listOf(song, video), searchTracks(tracks, "madrugada"))
        assertTrue(searchTracks(tracks, "synthpop").single() === song)
        assertEquals(emptyList(), searchTracks(tracks, "secreta"))
        assertEquals(emptyList(), searchTracks(tracks, "grabacion"))
    }

    @Test
    fun musicOnlyNuncaDevuelveVideosYBuscaElArchivo() {
        assertEquals(listOf(song), searchTracks(tracks, "neón", SearchScope.MUSIC_ONLY))
        assertEquals(listOf(hiddenSong), searchTracks(tracks, "pista secreta", SearchScope.MUSIC_ONLY))
        assertTrue(searchTracks(tracks, "neon en la piel.flac", SearchScope.MUSIC_ONLY).single() === song)
        assertEquals(emptyList(), searchTracks(tracks, "grabacion", SearchScope.MUSIC_ONLY))
    }

    @Test
    fun videoOnlyNuncaDevuelveMusicaYBuscaElArchivo() {
        assertEquals(listOf(video), searchTracks(tracks, "neón", SearchScope.VIDEO_ONLY))
        assertEquals(listOf(hiddenVideo), searchTracks(tracks, "grabacion_casa", SearchScope.VIDEO_ONLY))
        assertTrue(searchTracks(tracks, "neon_video.mp4", SearchScope.VIDEO_ONLY).single() === video)
        assertEquals(emptyList(), searchTracks(tracks, "synthpop", SearchScope.VIDEO_ONLY))
    }

    @Test
    fun unaBusquedaVaciaNoDevuelveNadaEnNingunModo() {
        SearchScope.entries.forEach { scope ->
            assertEquals(emptyList(), searchTracks(tracks, "", scope))
            assertEquals(emptyList(), searchTracks(tracks, "   ", scope))
        }
    }
}
