package app.aurora.player

import app.aurora.domain.hasKnownArtist

import app.aurora.domain.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Reproductor con cola. Las pistas con archivo suenan con el [engine] de la plataforma;
 * las pistas sin archivo (catálogo de ejemplo) o sin motor disponible avanzan con un reloj simulado.
 */
class DefaultPlayerController(
    private val scope: CoroutineScope,
    private val engine: MediaEngine?,
    initialVolume: Float = 0.8f,
) : PlayerController {
    private val _state = MutableStateFlow(PlaybackState(volume = initialVolume))
    override val state: StateFlow<PlaybackState> = _state
    private var ticker: Job? = null
    private var fadeSec = 0
    private var gapless = true
    private var repeatQueue = false
    @kotlin.concurrent.Volatile private var advancedEarly = false
    private var fadeFactor = 1f

    init {
        engine?.setVolume(initialVolume)
        engine?.onPosition = { ms ->
            // Se publica cada ~250 ms (VLC avisa más seguido): suficiente para el karaoke, que interpola.
            val last = _state.value.positionMs
            if (ms < last || ms - last >= 250) _state.update { it.copy(positionSec = (ms / 1000).toInt(), positionMs = ms) }
            applyFade(ms)
            // Sin pausas: se pasa a la siguiente ~0,3 s antes del final, para no oír el corte de VLC al cambiar.
            val durMs = _state.value.durationSec * 1000L
            if (gapless && fadeSec == 0 && durMs > 5000 && ms >= durMs - 300 && !advancedEarly) {
                advancedEarly = true
                scope.launch { onTrackEnded() }
            }
        }
        engine?.onDuration = { ms -> _state.update { it.copy(durationSec = (ms / 1000).toInt()) } }
        // Los avisos del motor llegan desde otro hilo: se procesan en el scope de la app.
        engine?.onEnded = { scope.launch { if (!advancedEarly) onTrackEnded() } }
        engine?.onError = { msg -> _state.update { it.copy(error = msg, isPlaying = false) } }
        engine?.onNotice = { msg -> _state.update { it.copy(videoProblem = msg) } }
        engine?.setOnPlayingChanged { playing ->
            if (_state.value.current != null && _state.value.isPlaying != playing) _state.update { it.copy(isPlaying = playing) }
        }
    }

    private fun usesEngine(t: Track?) = t?.filePath != null && engine?.available == true

    override fun play(queue: List<Track>, startIndex: Int) {
        _state.update { it.copy(queue = queue, index = startIndex, error = null) }
        startCurrent(play = true)
    }

    private fun startCurrent(play: Boolean, startSec: Int = 0) {
        val t = _state.value.current ?: return
        advancedEarly = false
        fadeFactor = if (fadeSec > 0) 0f else 1f
        if (fadeSec > 0) engine?.setVolume(0f) else engine?.setVolume(_state.value.volume)
        val real = usesEngine(t)
        _state.update {
            it.copy(positionSec = startSec, positionMs = startSec * 1000L, durationSec = t.durationSec, isPlaying = play, realAudio = real, error = null, videoEnabled = true)
        }
        ticker?.cancel()
        if (real) {
            engine!!.load(t.filePath!!, play, startSec * 1000L, metaOf(t))
        } else {
            engine?.stop()
            if (play) startTicker()
        }
    }

    override fun togglePlay() {
        val s = _state.value
        if (s.current == null) return
        val playing = !s.isPlaying
        _state.update { it.copy(isPlaying = playing) }
        if (s.realAudio) {
            if (playing) engine?.play() else engine?.pause()
        } else {
            if (playing) startTicker() else ticker?.cancel()
        }
    }

    override fun next() {
        val s = _state.value
        if (s.queue.isEmpty()) return
        _state.update { it.copy(index = QueueRules.nextIndex(it.index, it.queue.size, it.shuffle)) }
        startCurrent(play = true)
    }

    /** Al terminar la última pista: se detiene, o vuelve a empezar si "Al terminar la cola" = Repetir. */
    private fun advanceAtEnd() {
        val s = _state.value
        val last = !s.shuffle && s.index >= s.queue.lastIndex
        if (last && !repeatQueue) {
            ticker?.cancel()
            engine?.stop()
            _state.update { it.copy(isPlaying = false, positionSec = 0, positionMs = 0) }
            return
        }
        next()
    }

    /** Fundido: sube el volumen al empezar y lo baja en los últimos [fadeSec] segundos. */
    private fun applyFade(ms: Long) {
        if (fadeSec <= 0) return
        val durMs = _state.value.durationSec * 1000L
        val f = fadeSec * 1000f
        val inF = (ms / f).coerceIn(0f, 1f)
        val outF = if (durMs > f * 2) ((durMs - ms) / f).coerceIn(0f, 1f) else 1f
        val target = minOf(inF, outF)
        if (kotlin.math.abs(target - fadeFactor) >= .04f || target == 0f || target == 1f && fadeFactor != 1f) {
            fadeFactor = target
            engine?.setVolume(_state.value.volume * target)
        }
    }

    override fun moveInQueue(from: Int, to: Int) {
        val s = _state.value
        if (from !in s.queue.indices || to !in s.queue.indices || from == to) return
        val q = s.queue.toMutableList()
        q.add(to, q.removeAt(from))
        // La pista actual sigue siendo la misma aunque cambie de posición.
        val cur = s.index
        val newIndex = when {
            from == cur -> to
            from < cur && to >= cur -> cur - 1
            from > cur && to <= cur -> cur + 1
            else -> cur
        }
        _state.update { it.copy(queue = q, index = newIndex) }
    }

    override fun removeFromQueue(index: Int) {
        val s = _state.value
        if (index !in s.queue.indices || index == s.index) return
        val q = s.queue.toMutableList().also { it.removeAt(index) }
        _state.update { it.copy(queue = q, index = if (index < s.index) s.index - 1 else s.index) }
    }

    override fun clearUpcoming() {
        val s = _state.value
        if (s.index < 0) return
        _state.update { it.copy(queue = s.queue.take(s.index + 1)) }
    }

    override fun restore(queue: List<Track>, index: Int, positionSec: Int) {
        if (queue.isEmpty() || index !in queue.indices) return
        _state.update { it.copy(queue = queue, index = index) }
        startCurrent(play = false, startSec = positionSec)
    }

    override fun configure(fadeSec: Int, gapless: Boolean, repeatQueue: Boolean) {
        this.fadeSec = fadeSec; this.gapless = gapless; this.repeatQueue = repeatQueue
        if (fadeSec == 0 && fadeFactor != 1f) { fadeFactor = 1f; engine?.setVolume(_state.value.volume) }
    }

    override fun setEqualizer(eq: app.aurora.audio.EqSettings) { engine?.setEqualizer(eq) }

    override fun setNormalize(on: Boolean) { engine?.setNormalize(on) }

    override fun previous() {
        val s = _state.value
        if (s.queue.isEmpty()) return
        val (i, _) = QueueRules.previous(s.index, s.queue.size, s.positionSec)
        if (i == s.index) seekTo(0) else {
            _state.update { it.copy(index = i) }
            startCurrent(play = true)
        }
    }

    override fun seekTo(positionSec: Int) {
        val s = _state.value
        val dur = s.durationSec.takeIf { it > 0 } ?: s.current?.durationSec ?: 0
        val p = positionSec.coerceIn(0, maxOf(dur - 1, 0))
        _state.update { it.copy(positionSec = p, positionMs = p * 1000L) }
        if (s.realAudio) engine?.seekTo(p * 1000L)
    }

    override fun enqueue(track: Track, next: Boolean) {
        val s = _state.value
        if (s.current == null) { play(listOf(track), 0); return }
        val q = s.queue.toMutableList()
        if (next) q.add(s.index + 1, track) else q.add(track)
        _state.update { it.copy(queue = q) }
    }

    override fun toggleShuffle() = _state.update { it.copy(shuffle = !it.shuffle) }

    override fun toggleRepeat() = _state.update { it.copy(repeatOne = !it.repeatOne) }

    override fun setVolume(volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        _state.update { it.copy(volume = v) }
        engine?.setVolume(v * fadeFactor)
    }

    override val video: VideoOutput? get() = engine?.video

    override fun setVideoEnabled(enabled: Boolean) {
        _state.update { it.copy(videoEnabled = enabled) }
        engine?.video?.setEnabled(enabled)
    }

    private fun metaOf(t: Track) = MediaMeta(
        t.title,
        // Un video sin artista muestra solo su nombre (sin "Artista desconocido").
        if (t.hasKnownArtist) t.artist else "", t.album.takeUnless { it == app.aurora.domain.UNKNOWN_ALBUM }.orEmpty(),
        t.coverUri?.takeIf { it.startsWith("content://") }, trackId = t.id, durationMs = t.durationSec * 1000L,
    )

    override fun updateTrack(track: Track) {
        val before = _state.value
        if (before.queue.none { it.id == track.id }) return
        _state.update { s -> s.copy(queue = s.queue.map { if (it.id == track.id) track else it }) }
        if (before.current?.id == track.id && before.realAudio) track.filePath?.let { engine?.updateMeta(it, metaOf(track)) }
    }

    override fun release() {
        ticker?.cancel()
        engine?.release()
    }

    private fun onTrackEnded() {
        if (_state.value.repeatOne) startCurrent(play = true) else advanceAtEnd()
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                delay(1000)
                val s = _state.value
                if (!s.isPlaying) continue
                val duration = s.durationSec.takeIf { it > 0 } ?: continue
                if (s.positionSec + 1 < duration) _state.update { it.copy(positionSec = it.positionSec + 1, positionMs = (it.positionSec + 1) * 1000L) }
                else onTrackEnded()
            }
        }
    }
}
