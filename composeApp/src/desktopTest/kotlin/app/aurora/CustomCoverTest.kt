package app.aurora

import app.aurora.data.EditsRepository
import app.aurora.data.MetadataCandidate
import app.aurora.domain.MediaType
import app.aurora.domain.Track
import app.aurora.domain.TrackEdit
import app.aurora.platform.BlobStore
import app.aurora.platform.HttpClient
import app.aurora.platform.HttpResponse
import app.aurora.platform.MediaSource
import app.aurora.platform.MemoryStore
import app.aurora.platform.PlatformServices
import app.aurora.platform.ScanProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Portadas de "Corregir datos" (F5, fase 2): se descargan, se guardan y la caché las usa. */
class CustomCoverTest {
    private object Empty : MediaSource {
        override fun defaultFolders() = emptyList<String>()
        override val canPickFolder = false
        override suspend fun pickFolder(): String? = null
        override suspend fun candidateFolders() = emptyList<String>()
        override suspend fun scan(folders: List<String>, onProgress: (ScanProgress) -> Unit) = emptyList<Track>()
        override suspend fun loadCover(track: Track) = null
    }

    private fun png(): ByteArray {
        val s = Surface.makeRasterN32Premul(40, 40)
        s.canvas.clear(0xFF7B2FBF.toInt())
        return s.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes
    }

    @Test
    fun editsAddUpAndRememberTheCover() {
        val repo = EditsRepository(MemoryStore())
        repo.set("a", TrackEdit(title = "Árbol sin hojas"))
        repo.set("a", TrackEdit(album = "Vivi en do", customCover = true).mergedOnto(repo.get("a")))
        val e = repo.get("a")!!
        assertEquals("Árbol sin hojas", e.title)
        assertEquals("Vivi en do", e.album)
        assertTrue(e.customCover)
        val t = e.applyTo(Track("a", "00000", "?", "", 199, MediaType.AUDIO))
        assertEquals(TrackEdit.CUSTOM_COVER, t.coverUri)
    }

    @Test
    fun appliedCoverIsSavedAndUsed(): Unit = runBlocking {
        var asked = ""
        val http = object : HttpClient {
            override suspend fun get(url: String, headers: Map<String, String>): HttpResponse? = null
            override suspend fun getBytes(url: String, headers: Map<String, String>): ByteArray? { asked = url; return png() }
        }
        val files = BlobStore.Memory()
        val state = AppState(CoroutineScope(SupervisorJob() + Dispatchers.Default),
            PlatformServices("Prueba", true, MemoryStore(), Empty, null, http = http, files = files))
        val track = Track("x", "00000", "Artista desconocido", "", 199, MediaType.AUDIO, filePath = "/m/x.mp3")
        val c = MetadataCandidate("r1", "Árbol sin hojas", "Dread Mar-I", "Vivi en do", "rel-1", 2010, null, 199, 100)
        state.applyMetadata(track, c, writeToFile = false, withCover = true)
        withTimeout(5_000) { while (state.covers.version == 0) delay(20) }
        assertEquals("https://coverartarchive.org/release/rel-1/front-500", asked)
        val fixed = TrackEdit(customCover = true).applyTo(track)
        assertNotNull(state.covers.get(fixed), "la caché debe devolver la portada descargada")
    }
}
