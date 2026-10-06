package app.aurora

import kotlinx.coroutines.flow.first
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.aurora.components.CoverCache
import app.aurora.data.LibraryRepository
import app.aurora.data.PlaylistRepository
import app.aurora.data.SettingsRepository
import app.aurora.data.sample.SampleData
import app.aurora.domain.MediaType
import app.aurora.domain.Playlist
import app.aurora.domain.Track
import app.aurora.domain.autoMixes
import app.aurora.domain.resolve
import app.aurora.platform.PlatformServices
import app.aurora.player.DefaultPlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Ventanas emergentes de la app (hoja de opciones y diálogos de listas). */
sealed interface AppDialog {
    /** Opciones de una canción; [inPlaylist] si se abrió desde una lista del usuario. */
    data class TrackMenu(
        val track: Track,
        val inPlaylist: Playlist? = null,
        /** Punto de la ventana donde se hizo clic derecho (menú contextual); `null` = hoja/centrado. */
        val anchor: androidx.compose.ui.geometry.Offset? = null,
    ) : AppDialog
    /** Ventana con todos los datos de la pista. */
    data class TrackDetails(val track: Track) : AppDialog
    /** Editar título, artista, álbum, año y género. */
    data class EditTrack(val track: Track) : AppDialog
    data class AddToPlaylist(val tracks: List<Track>) : AppDialog
    /** Crear lista (opcionalmente con canciones ya elegidas). */
    data class NewPlaylist(val tracks: List<Track> = emptyList()) : AppDialog
    data class RenamePlaylist(val playlist: Playlist) : AppDialog
    data class DeletePlaylist(val playlist: Playlist) : AppDialog
    /** Cambiar el nombre del saludo. */
    data object EditName : AppDialog
}

/** Estado compartido por la interfaz móvil y la de escritorio. */
/** Temporizador de apagado: a una hora concreta o al terminar la canción actual. */
data class SleepTimer(val atMs: Long?, val endOfTrack: Boolean, val label: String)

class AppState(private val scope: CoroutineScope, val platform: PlatformServices) {
    val settings = SettingsRepository(platform.store, platform.media.defaultFolders(), legacySkipsWelcome = platform.isDesktop).also { st ->
        // Se olvidan carpetas que ya no sirven (en Android, las rutas de versiones anteriores).
        st.folders.value.filterNot(platform.media::isUsableFolder).forEach(st::removeFolder)
        st.videoFolders.value.filterNot(platform.media::isUsableFolder).forEach(st::removeVideoFolder)
    }
    val prefsRepo = app.aurora.data.PrefsRepository(platform.store)
    private val edits = app.aurora.data.EditsRepository(platform.store)
    val stats = app.aurora.data.PlayStatsRepository(platform.store)
    private val offsets = app.aurora.data.LyricsOffsetRepository(platform.store)
    val library = LibraryRepository(scope, platform.media, settings, edits)
    val player = DefaultPlayerController(scope, platform.mediaEngine, settings.volume)
    val covers = CoverCache { platform.media.loadCover(it) }
    val previews = app.aurora.components.PreviewCache { platform.media.loadPreviewFrames(it) }
    private val lists = PlaylistRepository(platform.store)
    val lyricsRepo = app.aurora.data.LyricsRepository(platform.http, platform.textCache, platform.store)

    /** Letra de la pista actual (archivo, caché o LRCLIB). */
    var lyrics by mutableStateOf<app.aurora.data.LyricsState>(app.aurora.data.LyricsState.None)
    private var lyricsFor: String? = null

    var toast by mutableStateOf<String?>(null)
    var source by mutableStateOf("Tu biblioteca")
    var dialog by mutableStateOf<AppDialog?>(null)

    /** Rectángulo del menú contextual abierto (en coordenadas de la ventana), para cerrar al hacer clic fuera. */
    var contextMenuBounds: androidx.compose.ui.geometry.Rect? = null

    /** Canciones con "Me gusta" (se guardan). */
    var liked by mutableStateOf(lists.liked.value)
        private set

    /** Listas creadas por el usuario (se guardan). */
    var userPlaylists by mutableStateOf(lists.playlists.value)
        private set

    /** Aumenta cada vez que empieza un video: la interfaz abre la vista de video. */
    var videoRequests by mutableIntStateOf(0)
        private set

    /** Aumenta cuando algo de fuera de la app (un widget) pide abrir el reproductor. */
    var playerRequests by mutableIntStateOf(0)
        private set

    /** Con la app recién abierta la cola tarda un momento en volver: se espera hasta 10 s. */
    fun requestPlayer() {
        scope.launch {
            kotlinx.coroutines.withTimeoutOrNull(10_000) { player.state.first { it.current != null } } ?: return@launch
            playerRequests++
        }
    }

    /** Pista de video para la que el usuario cerró el mini video flotante (el sonido sigue). */
    var pipClosedFor by mutableStateOf<String?>(null)

    /** El usuario eligió "Canción" con un video sonando: los videos siguientes no se abren solos. */
    var videoInBackground by mutableStateOf(false)

    /** Video a pantalla completa. */
    var videoFullscreen by mutableStateOf(false)
        private set

    /** Atrás en Inicio: Android manda la app a segundo plano (la música sigue). */
    var onExitToBackground: () -> Unit = {}

    /** Lo conecta la ventana de escritorio para poner la ventana en pantalla completa. */
    var onWindowFullscreen: (Boolean) -> Unit = {}

    /** Hay motor real (VLC en escritorio). */
    val hasRealAudio: Boolean get() = platform.mediaEngine?.available == true
    val canPlayVideo: Boolean get() = platform.mediaEngine?.video != null && hasRealAudio

    /** Desfase de la letra de la pista actual (ms; positivo = la letra va adelantada y se retrasa). */
    var lyricsOffsetMs by mutableStateOf(0L)
        private set

    var sleep by mutableStateOf<SleepTimer?>(null)
        private set

    /** Reproduciendo › Enfoque: solo la letra. */
    var nowPlayingFocus by mutableStateOf(false)
    private var sleepJob: kotlinx.coroutines.Job? = null

    /** Para refrescar la ficha tras reproducir o editar (las estadísticas no son estado de Compose). */
    var statsVersion by mutableIntStateOf(0)
        private set

    init {
        library.rescan()
        // Las preferencias se aplican al instante y cada vez que cambian.
        scope.launch {
            prefsRepo.prefs.collect { p ->
                player.configure(p.fadeSec, p.gapless, p.endOfQueue == app.aurora.data.EndOfQueue.REPEAT)
                player.setEqualizer(p.eq)
                player.setNormalize(p.normalize)
                lyricsRepo.preferFile = p.lrcFirst
                library.setIncludeVideos(p.includeVideos)
            }
        }
        scope.launch {
            var watching: Boolean? = null
            prefsRepo.prefs.collect { p -> if (p.watchFolders != watching) { watching = p.watchFolders; library.setWatching(p.watchFolders) } }
        }
        scope.launch { settings.folders.collect { if (prefsRepo.prefs.value.watchFolders) library.setWatching(true) } }
        // Esto no depende de la pantalla: en Android sigue funcionando con la app cerrada
        // (el servicio de reproducción mantiene vivo este estado).
        scope.launch {
            var lastSec = -1; var lastId: String? = null
            player.state.collect { p ->
                if (p.positionSec == lastSec && p.current?.id == lastId) return@collect
                lastSec = p.positionSec; lastId = p.current?.id
                onProgress(p)
                if (p.positionSec % 5 == 0) saveResumePoint(p)
            }
        }
        scope.launch { library.state.collect { l -> if (l.scannedOnce && !l.usingSamples) restoreIfNeeded(l.tracks) } }
        // El modo aleatorio se recuerda (notificación, widgets y app lo comparten).
        if (platform.store.get(KEY_SHUFFLE) == "1" && !player.state.value.shuffle) player.toggleShuffle()
        scope.launch {
            var last: Boolean? = null
            player.state.collect { p -> if (p.shuffle != last) { if (last != null) platform.store.put(KEY_SHUFFLE, if (p.shuffle) "1" else "0"); last = p.shuffle } }
        }
        // La letra se busca al empezar cada canción (archivo .lrc, letras guardadas o LRCLIB), también con la
        // app cerrada (widgets, notificación): así queda guardada para la próxima vez.
        scope.launch {
            var last: String? = null
            player.state.collect { p -> if (p.current?.id != last) { last = p.current?.id; loadLyrics(p.current) } }
        }
        // Última canción (para mostrarla aunque la app esté cerrada, p. ej. en los widgets).
        scope.launch {
            var last: String? = null
            player.state.collect { p -> val t = p.current ?: return@collect; if (t.id != last) { last = t.id; saveLastTrack(t) } }
        }
    }

    private fun saveLastTrack(t: Track) {
        platform.store.put(KEY_LAST, listOf(t.id, t.title, t.artist, t.album, t.durationSec.toString(), t.coverUri.orEmpty(), t.mediaType.name, t.filePath.orEmpty()).joinToString("\u001F"))
    }

    /** Última canción que sonó (aunque la biblioteca aún no esté cargada), o `null` si nunca sonó nada. */
    fun lastTrack(): Track? {
        val f = platform.store.get(KEY_LAST)?.split('\u001F') ?: return null
        if (f.size < 8) return null
        return Track(f[0], f[1], f[2], f[3], f[4].toIntOrNull() ?: 0, runCatching { MediaType.valueOf(f[6]) }.getOrDefault(MediaType.AUDIO),
            coverUri = f[5].ifEmpty { null }, filePath = f[7].ifEmpty { null })
    }

    companion object {
        const val KEY_SHUFFLE = "aleatorio"
        const val KEY_LAST = "ultima_cancion"
    }

    // --- Estadísticas y "recordar dónde quedé" ---

    private var countedFor: String? = null

    /** Cuenta una reproducción cuando la pista lleva 30 s (o la mitad, si es corta). */
    fun onProgress(p: app.aurora.player.PlaybackState) {
        val t = p.current ?: return
        val threshold = minOf(30, maxOf(5, (p.durationSec.takeIf { it > 0 } ?: t.durationSec) / 2))
        if (countedFor != t.id && p.isPlaying && p.positionSec >= threshold) {
            countedFor = t.id
            stats.record(t.id, kotlin.time.Clock.System.now().toEpochMilliseconds())
            statsVersion++
        }
        if (p.positionSec < 2) countedFor = countedFor.takeIf { it == t.id && p.positionSec > 0 }
    }

    fun saveResumePoint(p: app.aurora.player.PlaybackState) {
        if (!prefsRepo.prefs.value.resume || p.current?.filePath == null) return
        platform.store.put("resume", (listOf(p.index.toString(), p.positionSec.toString()) + p.queue.map { it.id }).joinToString("\n"))
    }

    private var restored = false

    /** Al terminar el primer escaneo, retoma la cola y el segundo donde se quedó (en pausa). */
    fun restoreIfNeeded(tracks: List<Track>) {
        if (restored || player.state.value.current != null || tracks.isEmpty()) return
        restored = true
        if (!prefsRepo.prefs.value.resume) return
        val lines = platform.store.get("resume")?.split('\n') ?: return
        val index = lines.getOrNull(0)?.toIntOrNull() ?: return
        val pos = lines.getOrNull(1)?.toIntOrNull() ?: 0
        val byId = tracks.associateBy { it.id }
        val ids = lines.drop(2)
        val queue = ids.mapNotNull { byId[it] }
        val cur = ids.getOrNull(index)?.let { byId[it] } ?: return
        player.restore(queue, queue.indexOf(cur), pos)
        source = "Donde lo dejaste"
    }

    // --- Temporizador de apagado ---

    /** [minutes] > 0: pausa en ese tiempo; -1: al terminar la canción; 0: cancelar. */
    fun setSleep(minutes: Int) {
        sleepJob?.cancel()
        sleep = null
        if (minutes == 0) { show("Temporizador cancelado"); return }
        if (minutes < 0) {
            val id = player.state.value.current?.id
            sleep = SleepTimer(null, true, "Al terminar la canción")
            sleepJob = scope.launch {
                player.state.collect { s -> if (s.current?.id != id) { player.pauseIfPlaying(); sleep = null; show("Pausado: terminó la canción"); sleepJob?.cancel() } }
            }
            show("Se pausará al terminar la canción")
            return
        }
        val at = kotlin.time.Clock.System.now().toEpochMilliseconds() + minutes * 60_000L
        sleep = SleepTimer(at, false, if (minutes >= 60) "${minutes / 60} h" else "$minutes min")
        sleepJob = scope.launch {
            kotlinx.coroutines.delay(minutes * 60_000L)
            player.pauseIfPlaying(); sleep = null; show("Pausado por el temporizador")
        }
        show("Se pausará en ${sleep!!.label}")
    }

    // --- Desfase de la letra ---

    fun adjustLyricsOffset(track: Track?, deltaMs: Long) {
        val t = track ?: return
        lyricsOffsetMs = (lyricsOffsetMs + deltaMs).coerceIn(-10_000, 10_000)
        offsets.set(t.id, lyricsOffsetMs)
    }

    // --- Editar datos ---

    fun editTrack(track: Track, e: app.aurora.domain.TrackEdit, writeToFile: Boolean) {
        edits.set(track.id, e)
        library.applyEdit(track.id, e)
        statsVersion++
        scope.launch {
            val ok = writeToFile && platform.media.writeTags(track, e)
            show(if (writeToFile) (if (ok) "Datos guardados en la app y en el archivo" else "Guardado en la app (no se pudo escribir el archivo)") else "Datos guardados")
            loadLyrics(e.applyTo(track), force = true)
        }
    }

    fun show(message: String) { toast = message }

    fun play(queue: List<Track>, index: Int, from: String = source) {
        val track = queue.getOrNull(index) ?: return
        if (track.mediaType == MediaType.VIDEO && !canPlayVideo) {
            show(if (platform.isDesktop) "Para ver videos instala VLC" else "El video en ${platform.name} llega en la próxima fase")
            return
        }
        source = from
        player.play(queue, index)
        if (track.mediaType == MediaType.VIDEO) videoRequests++
    }

    /** Busca la letra de la pista actual. [force] vuelve a preguntar a LRCLIB aunque antes no estuviera. */
    fun loadLyrics(track: Track?, force: Boolean = false) {
        if (track == null) { lyrics = app.aurora.data.LyricsState.None; lyricsFor = null; return }
        lyricsOffsetMs = offsets.get(track.id)
        if (!force && lyricsFor == track.id && lyrics !is app.aurora.data.LyricsState.Loading) return
        lyricsFor = track.id
        lyrics = app.aurora.data.LyricsState.Loading
        scope.launch {
            val result = lyricsRepo.load(track, force)
            if (lyricsFor == track.id) lyrics = result
            autoSaveLrc(track, result)
        }
    }

    private var warnedNoWrite = false

    /**
     * Letra sincronizada recién descargada de LRCLIB → archivo .lrc junto a la canción (si está activado en
     * Ajustes › Letras). Si Android no deja escribir en la carpeta, se avisa una sola vez.
     */
    private suspend fun autoSaveLrc(track: Track, result: app.aurora.data.LyricsState) {
        val found = result as? app.aurora.data.LyricsState.Found ?: return
        val lrc = found.lrc ?: return
        if (found.source != app.aurora.data.LyricsSource.LRCLIB || track.lyrics.isNotEmpty() || track.filePath == null) return
        if (!prefsRepo.prefs.value.autoSaveLrc) return
        if (platform.media.saveLyricsFile(track, lrc)) return
        if (!warnedNoWrite && !platform.isDesktop) {
            warnedNoWrite = true
            show("Para guardar las letras como .lrc, da permiso en Ajustes › Letras")
        }
    }

    /** Vuelve a pedir la carpeta con permiso de escritura (las elegidas antes solo tenían lectura). */
    fun grantLyricsWrite() {
        scope.launch {
            platform.media.pickFolder() ?: return@launch
            show(if (platform.media.foldersWithoutWrite(settings.folders.value).isEmpty()) "Listo: las letras se guardarán como .lrc" else "Falta dar permiso a otras carpetas")
        }
    }

    /** Guarda la letra sincronizada como .lrc junto a la canción. */
    fun saveLyricsFile(track: Track) {
        val lrc = (lyrics as? app.aurora.data.LyricsState.Found)?.lrc ?: return show("No hay letra sincronizada para guardar")
        scope.launch { show(if (platform.media.saveLyricsFile(track, lrc)) "Letra guardada junto a la canción (.lrc)" else "No se pudo guardar el archivo .lrc") }
    }

    fun playNext(track: Track) { player.enqueue(track, next = true); show("Sonará a continuación: ${track.title}") }
    fun addToQueue(track: Track) { player.enqueue(track, next = false); show("Añadida a la cola") }
    fun revealInFolder(track: Track) {
        scope.launch { if (!platform.media.revealInFolder(track)) show("No se pudo abrir la carpeta") }
    }

    fun setFullscreen(on: Boolean) {
        videoFullscreen = on
        onWindowFullscreen(on)
    }

    // --- Me gusta y listas ---

    fun toggleLike(track: Track?) {
        val id = track?.id ?: return
        val now = lists.toggleLike(id)
        liked = lists.liked.value
        show(if (now) "Añadida a Me gusta" else "Quitada de Me gusta")
    }

    /** Listas del usuario primero, luego las automáticas (o las de ejemplo). */
    fun playlists(tracks: List<Track>, usingSamples: Boolean): List<Playlist> =
        userPlaylists + if (usingSamples) SampleData.playlists else autoMixes(tracks, liked)

    fun tracksOf(playlist: Playlist, tracks: List<Track>): List<Track> {
        val live = if (playlist.isUser) lists.get(playlist.id) ?: playlist else playlist
        return live.resolve(tracks.associateBy { it.id })
    }

    /** La versión más reciente de una lista (tras renombrar o editar). */
    fun latest(playlist: Playlist): Playlist? = if (playlist.isUser) lists.get(playlist.id) else playlist

    fun createPlaylist(name: String, tracks: List<Track> = emptyList()): Playlist {
        val p = lists.create(name, tracks)
        userPlaylists = lists.playlists.value
        show(if (tracks.isEmpty()) "Lista \"${p.name}\" creada" else "Guardada en ${p.name}")
        return p
    }

    fun addToPlaylist(playlist: Playlist, tracks: List<Track>) {
        val n = lists.add(playlist.id, tracks)
        userPlaylists = lists.playlists.value
        show(
            when {
                n == 0 -> "Ya estaba en ${playlist.name}"
                n == 1 -> "Guardada en ${playlist.name}"
                else -> "$n canciones guardadas en ${playlist.name}"
            },
        )
    }

    fun removeFromPlaylist(playlist: Playlist, track: Track) {
        lists.remove(playlist.id, track.id)
        userPlaylists = lists.playlists.value
        show("Quitada de ${playlist.name}")
    }

    fun moveInPlaylist(playlist: Playlist, from: Int, to: Int) {
        lists.move(playlist.id, from, to)
        userPlaylists = lists.playlists.value
    }

    fun renamePlaylist(playlist: Playlist, name: String) {
        lists.rename(playlist.id, name)
        userPlaylists = lists.playlists.value
    }

    fun deletePlaylist(playlist: Playlist) {
        lists.delete(playlist.id)
        userPlaylists = lists.playlists.value
        show("Lista \"${playlist.name}\" borrada")
    }

    fun setVolume(v: Float) {
        player.setVolume(v)
        settings.saveVolume(v)
    }

    // --- Carpetas ---

    /** Añade una carpeta de música ([MediaType.AUDIO]) o de videos ([MediaType.VIDEO]) y la busca en segundo plano. */
    fun addFolder(path: String, type: MediaType = MediaType.AUDIO, quiet: Boolean = false) {
        val added = if (type == MediaType.VIDEO) settings.addVideoFolder(path) else settings.addFolder(path)
        if (added) {
            if (!quiet) show(if (type == MediaType.VIDEO) "Carpeta añadida: buscando videos…" else "Carpeta añadida: buscando canciones…")
            library.scanFolder(app.aurora.data.LibraryFolder(path.trim().trimEnd('/', '\\'), type))
        } else show("Esa carpeta ya está en la lista")
    }

    /** Abre el selector de carpetas del sistema. */
    fun pickAndAddFolder(type: MediaType = MediaType.AUDIO, quiet: Boolean = false) {
        scope.launch {
            val path = platform.media.pickFolder() ?: return@launch
            addFolder(path, type, quiet)
        }
    }

    fun removeFolder(path: String, type: MediaType = MediaType.AUDIO) {
        if (type == MediaType.VIDEO) settings.removeVideoFolder(path) else settings.removeFolder(path)
        show("Carpeta quitada")
        library.dropFolder(app.aurora.data.LibraryFolder(path, type))
    }

    // --- Nombre y bienvenida ---

    /** La bienvenida está abierta (primera vez que se abre la app). */
    var onboarding by mutableStateOf(!settings.onboardingDone)
        private set

    fun finishOnboarding() {
        settings.finishOnboarding()
        onboarding = false
    }
}
