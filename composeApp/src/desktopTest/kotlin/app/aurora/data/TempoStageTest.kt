package app.aurora.data

import app.aurora.domain.MediaType
import app.aurora.domain.Pcm
import app.aurora.domain.Track
import app.aurora.platform.BlobStore
import app.aurora.platform.MediaSource
import app.aurora.platform.MemoryStore
import app.aurora.platform.ScanProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Etapas de preparar la biblioteca y el tempo calculado (que se guarda y no se repite). */
class TempoStageTest {
    private class Source : MediaSource {
        var decoded = 0
        override fun defaultFolders() = emptyList<String>()
        override val canPickFolder = false
        override suspend fun pickFolder(): String? = null
        override suspend fun candidateFolders() = emptyList<String>()
        override suspend fun scan(folders: List<String>, onProgress: (ScanProgress) -> Unit): List<Track> {
            onProgress(ScanProgress(1, 3))
            return listOf(
                Track("a", "A", "X", "Y", 200, MediaType.AUDIO, filePath = "/m/a.mp3"),
                Track("b", "B", "X", "Y", 200, MediaType.AUDIO, filePath = "/m/b.mp3", bpm = 99),
                Track("c", "C", "X", "Y", 200, MediaType.AUDIO, filePath = "/m/c.mp3"),
            )
        }
        override suspend fun loadCover(track: Track) = null
        override val canDecodeForTempo = true
        override suspend fun decodeForTempo(track: Track): Pcm { decoded++; return beats(120.0) }

        private fun beats(bpm: Double, rate: Int = 11025): Pcm {
            val s = FloatArray(20 * rate)
            var t = 0.0
            while (t < 20) {
                val start = (t * rate).toInt()
                for (i in 0 until rate / 10) if (start + i < s.size) s[start + i] += sin(2 * PI * 60 * i / rate).toFloat() * (1f - i / (rate / 10f))
                t += 60.0 / bpm
            }
            return Pcm(s, rate)
        }
    }

    private suspend fun untilReady(lib: LibraryRepository) = withTimeout(20_000) {
        while (lib.state.value.stage != null || !lib.state.value.scannedOnce) delay(20)
    }

    @Test fun stagesEndReadyAndTempoIsCachedAndApplied() = runBlocking {
        val files = BlobStore.Memory()
        val store = MemoryStore().apply { put(SettingsRepository.KEY_FOLDERS, "/m") }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val src = Source()
        val lib = LibraryRepository(scope, src, SettingsRepository(store, emptyList()), files = files)
        lib.scanFolder(LibraryFolder("/m", MediaType.AUDIO))
        delay(5)
        untilReady(lib)
        val byId = lib.state.value.scanned.associateBy { it.id }
        assertEquals(120, byId["a"]?.bpm)
        assertEquals(99, byId["b"]?.bpm, "el BPM de la etiqueta se respeta")
        assertEquals(2, src.decoded, "solo las que no traen BPM")
        assertTrue(files.read("tempos")!!.decodeToString().contains("a\t120"))
        scope.cancel()

        // Otra vez (como al reabrir la app): el tempo sale del archivo y no se vuelve a calcular.
        val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val src2 = Source()
        val lib2 = LibraryRepository(scope2, src2, SettingsRepository(store, emptyList()), files = files)
        lib2.rescan()
        delay(5)
        untilReady(lib2)
        assertEquals(120, lib2.state.value.scanned.first { it.id == "c" }.bpm)
        assertEquals(0, src2.decoded)
        scope2.cancel()
    }
}
