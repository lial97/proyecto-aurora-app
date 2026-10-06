package app.aurora.data

import app.aurora.data.sample.SampleData
import app.aurora.domain.MediaType
import app.aurora.domain.Track
import app.aurora.platform.MediaSource
import app.aurora.platform.ScanProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock

data class LibraryState(
    /** Pistas encontradas en las carpetas del usuario. */
    val scanned: List<Track> = emptyList(),
    val scanning: Boolean = false,
    val progress: ScanProgress? = null,
    /** Ya terminó al menos un escaneo. */
    val scannedOnce: Boolean = false,
    val error: String? = null,
    /** Ajustes › Biblioteca › Incluir videos = no. */
    val hideVideos: Boolean = false,
) {
    /** Sin archivos propios se muestra el catálogo de ejemplo. */
    val usingSamples: Boolean get() = scanned.isEmpty() && !scanning
    val tracks: List<Track> get() = (if (usingSamples) SampleData.tracks else scanned).let { all ->
        if (hideVideos) all.filter { it.mediaType != MediaType.VIDEO } else all
    }
    val songCount: Int get() = scanned.count { it.mediaType == MediaType.AUDIO }
    val videoCount: Int get() = scanned.count { it.mediaType == MediaType.VIDEO }
}

/** Carpeta de la biblioteca: de música (solo audio) o de videos (solo video). */
data class LibraryFolder(val path: String, val type: MediaType)

/**
 * Biblioteca: escanea cada carpeta elegida por separado y publica el resultado.
 * De las carpetas de música solo se toma audio y de las de videos solo video,
 * así los videos personales de otras carpetas no aparecen.
 */
class LibraryRepository(
    private val scope: CoroutineScope,
    private val media: MediaSource,
    private val settings: SettingsRepository,
    private val edits: EditsRepository? = null,
) {
    private var watchJob: Job? = null

    private fun allFolders() = settings.folders.value.map { LibraryFolder(it, MediaType.AUDIO) } +
        settings.videoFolders.value.map { LibraryFolder(it, MediaType.VIDEO) }

    /** "Vigilar cambios": vuelve a buscar (sin borrar la lista actual) cuando cambian los archivos. */
    fun setWatching(on: Boolean) {
        watchJob?.cancel()
        if (!on) return
        watchJob = scope.launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            media.watch((settings.folders.value + settings.videoFolders.value).distinct()).debounce(2000).collect { rescan(quiet = true) }
        }
    }

    fun setIncludeVideos(on: Boolean) = _state.update { it.copy(hideVideos = !on) }

    /** Aplica una corrección de datos sin volver a escanear. */
    fun applyEdit(id: String, e: app.aurora.domain.TrackEdit) {
        _state.update { s -> s.copy(scanned = s.scanned.map { if (it.id == id) e.applyTo(it) else it }) }
        byFolder.replaceAll { _, list -> list.map { if (it.id == id) e.applyTo(it) else it } }
    }

    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state

    private val _counts = MutableStateFlow<Map<LibraryFolder, Int>>(emptyMap())
    /** Pistas encontradas en cada carpeta (falta la clave mientras se busca). */
    val counts: StateFlow<Map<LibraryFolder, Int>> = _counts

    private val byFolder = LinkedHashMap<LibraryFolder, List<Track>>()
    private val lock = kotlinx.coroutines.sync.Mutex()
    private var job: Job? = null
    private val folderJobs = mutableMapOf<LibraryFolder, Job>()

    private suspend fun scanOne(f: LibraryFolder, onProgress: (ScanProgress) -> Unit): List<Track> = withContext(Dispatchers.Default) {
        val found = media.scan(listOf(f.path), onProgress).filter { it.mediaType == f.type }
        edits?.apply(found) ?: found
    }

    /** Junta lo encontrado en todas las carpetas vigentes (sin repetir pistas). */
    private suspend fun publish(done: Boolean) {
        val current = allFolders().toSet()
        val merged = lock.withLock {
            byFolder.keys.retainAll(current)
            byFolder.values.flatten().distinctBy { it.id }
        }
        _counts.value = lock.withLock { byFolder.mapValues { it.value.size } }
        _state.update { it.copy(scanned = merged, scannedOnce = it.scannedOnce || done) }
    }

    fun rescan(quiet: Boolean = false) {
        job?.cancel()
        val folders = allFolders()
        if (!quiet) _state.update { it.copy(scanning = true, progress = ScanProgress(0, null), error = null) }
        job = scope.launch {
            val result = runCatching {
                folders.forEachIndexed { i, f ->
                    val tracks = scanOne(f) { p -> if (!quiet) _state.update { it.copy(progress = ScanProgress(i, folders.size).takeIf { folders.size > 1 } ?: p) } }
                    lock.withLock { byFolder[f] = tracks }
                }
            }
            if (result.exceptionOrNull() is kotlinx.coroutines.CancellationException) return@launch
            publish(done = true)
            _state.update {
                it.copy(scanning = false, progress = null, error = result.exceptionOrNull()?.let { e -> "No se pudo escanear: ${e.message}" })
            }
        }
    }

    /** Busca solo en una carpeta recién añadida, en segundo plano (la bienvenida sigue mientras tanto). */
    fun scanFolder(f: LibraryFolder) {
        folderJobs[f]?.cancel()
        _counts.update { it - f }
        folderJobs[f] = scope.launch {
            val tracks = runCatching { scanOne(f) {} }.getOrElse { if (it is kotlinx.coroutines.CancellationException) throw it; emptyList() }
            lock.withLock { byFolder[f] = tracks }
            publish(done = true)
        }
    }

    /** Quita una carpeta sin volver a escanear las demás. */
    fun dropFolder(f: LibraryFolder) {
        folderJobs.remove(f)?.cancel()
        scope.launch { publish(done = true) }
    }
}

