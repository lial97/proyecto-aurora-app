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
    /** No hay ninguna carpeta elegida ("Lo haré después"): Inicio y Biblioteca quedan vacías. */
    val noFolders: Boolean = false,
    /** Etapa del trabajo en segundo plano (`null` = todo listo) y su avance; [stageTotal] 0 = sin total conocido. */
    val stage: ScanStage? = null,
    val stageDone: Int = 0,
    val stageTotal: Int = 0,
) {
    /** Con carpetas pero sin archivos propios se muestra el catálogo de ejemplo. */
    val usingSamples: Boolean get() = scanned.isEmpty() && !scanning && !noFolders
    val tracks: List<Track> get() = (if (usingSamples) SampleData.tracks else scanned).let { all ->
        if (hideVideos) all.filter { it.mediaType != MediaType.VIDEO } else all
    }
    val songCount: Int get() = scanned.count { it.mediaType == MediaType.AUDIO }
    val videoCount: Int get() = scanned.count { it.mediaType == MediaType.VIDEO }
}

/** Etapas de preparar la biblioteca, en orden (se muestran en "Todo listo" de la configuración inicial). */
enum class ScanStage {
    /** Leyendo etiquetas, portadas y letras de cada archivo. */
    READING,
    /** Juntando y ordenando lo encontrado. */
    SORTING,
    /** Calculando el tempo de las canciones que no lo traen en sus etiquetas. */
    TEMPO,
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
    /** Donde se guardan los tempos calculados (un solo archivo "tempos"); `null` = solo en memoria. */
    private val files: app.aurora.platform.BlobStore? = null,
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

    private val _state = MutableStateFlow(LibraryState(noFolders = settings.folders.value.isEmpty() && settings.videoFolders.value.isEmpty()))
    val state: StateFlow<LibraryState> = _state

    init {
        scope.launch {
            kotlinx.coroutines.flow.combine(settings.folders, settings.videoFolders) { a, v -> a.isEmpty() && v.isEmpty() }
                .collect { none -> _state.update { it.copy(noFolders = none) } }
        }
    }

    private val _counts = MutableStateFlow<Map<LibraryFolder, Int>>(emptyMap())
    /** Pistas encontradas en cada carpeta (falta la clave mientras se busca). */
    val counts: StateFlow<Map<LibraryFolder, Int>> = _counts

    private val byFolder = LinkedHashMap<LibraryFolder, List<Track>>()
    private val lock = kotlinx.coroutines.sync.Mutex()
    private var job: Job? = null
    private val folderJobs = mutableMapOf<LibraryFolder, Job>()

    // --- Etapas y tempo ---

    /** Avance de lectura de cada carpeta que se está escaneando. */
    private val reading = MutableStateFlow<Map<LibraryFolder, ScanProgress>>(emptyMap())
    private var tempoJob: Job? = null

    /**
     * Tempos calculados por pista (0 = no se pudo), leídos una vez del archivo "tempos". Mapa inmutable: solo lo
     * reemplaza el cálculo de tempo (de a tandas), así se puede leer desde cualquier hilo.
     */
    @kotlin.concurrent.Volatile private var tempoMap: Map<String, Int>? = null

    private fun tempos(): Map<String, Int> = tempoMap ?: run {
        val text = runCatching { files?.read(TEMPO_KEY)?.decodeToString() }.getOrNull().orEmpty()
        text.lineSequence().mapNotNull { line ->
            val tab = line.lastIndexOf('\t')
            if (tab <= 0) null else line.substring(0, tab) to (line.substring(tab + 1).toIntOrNull() ?: return@mapNotNull null)
        }.toMap().also { tempoMap = it }
    }

    private fun saveTempos() {
        val f = files ?: return
        val text = tempos().entries.joinToString("\n") { "${it.key}\t${it.value}" }
        runCatching { f.write(TEMPO_KEY, text.encodeToByteArray()) }
    }

    private fun withTempo(t: Track): Track =
        if (t.bpm != null || t.mediaType != MediaType.AUDIO) t
        else tempos()[t.id]?.takeIf { it > 0 }?.let { t.copy(bpm = it) } ?: t

    /** Empieza (o sigue) la lectura de [f]: cancela el cálculo de tempo hasta que terminen las lecturas. */
    private fun startReading(f: LibraryFolder) {
        tempoJob?.cancel()
        reading.update { it + (f to ScanProgress(0, null)) }
        showReading()
    }

    private fun readProgress(f: LibraryFolder, p: ScanProgress) {
        reading.update { if (f in it) it + (f to p) else it }
        showReading()
    }

    private fun showReading() {
        val all = reading.value.values
        val total = if (all.any { it.total == null || it.total == 0 }) 0 else all.sumOf { it.total ?: 0 }
        _state.update { it.copy(stage = ScanStage.READING, stageDone = all.sumOf { p -> p.done }, stageTotal = total) }
    }

    /** Terminó la lectura de [f]; si no queda ninguna, se ordena y se pasa al tempo. */
    private suspend fun doneReading(f: LibraryFolder) {
        reading.update { it - f }
        if (reading.value.isNotEmpty()) { showReading(); return }
        _state.update { it.copy(stage = ScanStage.SORTING, stageDone = 0, stageTotal = 0) }
        publish(done = true)
        startTempo()
    }

    /**
     * Calcula, una por una y con poca prioridad, el tempo de las canciones que no lo traen. Lo ya calculado se
     * guarda y no se repite. Si la plataforma no sabe decodificar audio, se salta.
     */
    private fun startTempo() {
        tempoJob?.cancel()
        tempoJob = scope.launch {
            val known = withContext(Dispatchers.Default) { tempos() }
            val can = withContext(Dispatchers.Default) { media.canDecodeForTempo } // en escritorio arranca VLC
            val todo = if (!can) emptyList() else _state.value.scanned.filter {
                it.mediaType == MediaType.AUDIO && it.bpm == null && it.filePath != null && it.id !in known
            }
            if (todo.isEmpty()) { _state.update { it.copy(stage = null, stageDone = 0, stageTotal = 0) }; return@launch }
            _state.update { it.copy(stage = ScanStage.TEMPO, stageDone = 0, stageTotal = todo.size) }
            val found = HashMap<String, Int>()
            fun flush() {
                if (found.isEmpty()) return
                val batch = HashMap(found); found.clear()
                tempoMap = tempos() + batch
                val good = batch.filterValues { it > 0 }
                if (good.isNotEmpty()) _state.update { s -> s.copy(scanned = s.scanned.map { t -> good[t.id]?.let { b -> t.copy(bpm = b) } ?: t }) }
            }
            try {
                todo.forEachIndexed { i, t ->
                    val bpm = withContext(tempoDispatcher) {
                        runCatching { media.decodeForTempo(t)?.let { pcm -> app.aurora.domain.estimateBpm(pcm) } }
                            .getOrElse { if (it is kotlinx.coroutines.CancellationException) throw it; null } ?: 0
                    }
                    found[t.id] = bpm
                    _state.update { it.copy(stageDone = i + 1) }
                    if ((i + 1) % 20 == 0) { flush(); withContext(Dispatchers.Default) { saveTempos() } }
                }
            } finally {
                flush()
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Default) { saveTempos() }
            }
            _state.update { it.copy(stage = null, stageDone = 0, stageTotal = 0) }
        }
    }

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
        }.map(::withTempo)
        _counts.value = lock.withLock { byFolder.mapValues { it.value.size } }
        _state.update { it.copy(scanned = merged, scannedOnce = it.scannedOnce || done) }
    }

    fun rescan(quiet: Boolean = false) {
        job?.cancel()
        val folders = allFolders()
        if (!quiet) _state.update { it.copy(scanning = true, progress = ScanProgress(0, null), error = null) }
        val all = LibraryFolder("", MediaType.AUDIO) // marca de "volver a escanear todo" en las etapas
        startReading(all)
        job = scope.launch {
            val result = runCatching {
                folders.forEachIndexed { i, f ->
                    val tracks = scanOne(f) { p ->
                        readProgress(all, p)
                        if (!quiet) _state.update { it.copy(progress = ScanProgress(i, folders.size).takeIf { folders.size > 1 } ?: p) }
                    }
                    lock.withLock { byFolder[f] = tracks }
                }
            }
            if (result.exceptionOrNull() is kotlinx.coroutines.CancellationException) return@launch
            _state.update {
                it.copy(scanning = false, progress = null, error = result.exceptionOrNull()?.let { e -> "No se pudo escanear: ${e.message}" })
            }
            doneReading(all)
        }
    }

    /** Busca solo en una carpeta recién añadida, en segundo plano (la bienvenida sigue mientras tanto). */
    fun scanFolder(f: LibraryFolder) {
        folderJobs[f]?.cancel()
        _counts.update { it - f }
        startReading(f)
        folderJobs[f] = scope.launch {
            val tracks = runCatching { scanOne(f) { p -> readProgress(f, p) } }.getOrElse { if (it is kotlinx.coroutines.CancellationException) throw it; emptyList() }
            lock.withLock { byFolder[f] = tracks }
            publish(done = true)
            doneReading(f)
        }
    }

    /** Quita una carpeta sin volver a escanear las demás. */
    fun dropFolder(f: LibraryFolder) {
        folderJobs.remove(f)?.cancel()
        scope.launch { if (f in reading.value) doneReading(f) else publish(done = true) }
    }

    private companion object {
        const val TEMPO_KEY = "tempos"
        /** El tempo se calcula de a una canción, para no competir con la interfaz ni con la reproducción. */
        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        val tempoDispatcher = Dispatchers.Default.limitedParallelism(1)
    }
}

