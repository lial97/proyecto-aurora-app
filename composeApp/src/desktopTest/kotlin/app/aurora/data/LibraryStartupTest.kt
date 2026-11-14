package app.aurora.data

import app.aurora.domain.MediaType
import app.aurora.domain.Track
import app.aurora.platform.BlobStore
import app.aurora.platform.MediaSource
import app.aurora.platform.MemoryStore
import app.aurora.platform.ScanProgress
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Al abrir, la biblioteca guardada se ve al instante y el escaneo de fondo la reemplaza callado. */
class LibraryStartupTest {
    private class Source(val tracks: List<Track>, val gate: CompletableDeferred<Unit>? = null) : MediaSource {
        override fun defaultFolders() = emptyList<String>()
        override val canPickFolder = false
        override suspend fun pickFolder(): String? = null
        override suspend fun candidateFolders() = emptyList<String>()
        override suspend fun scan(folders: List<String>, onProgress: (ScanProgress) -> Unit): List<Track> { gate?.await(); return tracks }
        override suspend fun loadCover(track: Track) = null
    }

    private suspend fun untilScanned(lib: LibraryRepository) = withTimeout(10_000) { while (!lib.state.value.scannedOnce) delay(10) }

    @Test fun cachedLibraryShowsAtOnceAndTheScanReplacesIt() = runBlocking {
        val files = BlobStore.Memory()
        val store = MemoryStore().apply { put(SettingsRepository.KEY_FOLDERS, "/m"); put(SettingsRepository.KEY_VIDEO_FOLDERS, "") }
        val a = Track("a", "A", "X", "Y", 200, filePath = "/m/a.mp3")
        val b = Track("b", "B", "X", "Y", 200, filePath = "/m/b.mp3")

        // Primera vez: sin nada guardado, se escanea como siempre y se guarda.
        val s1 = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val lib1 = LibraryRepository(s1, Source(listOf(a, b)), SettingsRepository(store, emptyList()), files = files)
        assertFalse(lib1.state.value.fromCache)
        lib1.startupScan()
        untilScanned(lib1)
        withTimeout(5_000) { while (files.read("biblioteca") == null) delay(10) }
        s1.cancel()

        // Al reabrir: la lista guardada está desde el primer momento, sin "Buscando…", aunque el escaneo tarde.
        val gate = CompletableDeferred<Unit>()
        val s2 = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val lib2 = LibraryRepository(s2, Source(listOf(a), gate), SettingsRepository(store, emptyList()), files = files)
        assertTrue(lib2.state.value.fromCache)
        assertEquals(listOf("a", "b"), lib2.state.value.scanned.map { it.id })
        lib2.startupScan()
        delay(50)
        assertFalse(lib2.state.value.scanning, "escaneo callado: sin aviso de búsqueda")
        assertEquals(2, lib2.state.value.tracks.size, "no se muestra el catálogo de ejemplo ni una lista vacía")

        // Termina el escaneo: "b" ya no está en la carpeta y desaparece.
        gate.complete(Unit)
        untilScanned(lib2)
        assertEquals(listOf("a"), lib2.state.value.scanned.map { it.id })
        assertFalse(lib2.state.value.fromCache)
        s2.cancel()

        // Con otras carpetas, lo guardado no se usa.
        store.put(SettingsRepository.KEY_FOLDERS, "/otra")
        val s3 = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val lib3 = LibraryRepository(s3, Source(emptyList()), SettingsRepository(store, emptyList()), files = files)
        assertFalse(lib3.state.value.fromCache)
        assertTrue(lib3.state.value.scanned.isEmpty())
        s3.cancel()
    }
}
