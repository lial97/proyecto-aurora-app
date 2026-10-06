package app.aurora.player

import androidx.compose.ui.graphics.ImageBitmap
import app.aurora.domain.MediaType
import app.aurora.domain.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakeEngine : MediaEngine, VideoOutput {
    override val available = true
    var loaded: String? = null
    var lastVolume = -1f
    var videoOn = true
    val seeks = mutableListOf<Long>()
    override fun load(path: String, play: Boolean, startMs: Long, meta: MediaMeta?) { loaded = path }
    override fun play() {}
    override fun pause() {}
    override fun seekTo(ms: Long) { seeks += ms }
    override fun stop() {}
    override fun setVolume(volume: Float) { lastVolume = volume }
    override val video: VideoOutput get() = this
    override var onPosition: (Long) -> Unit = {}
    override var onDuration: (Long) -> Unit = {}
    override var onEnded: () -> Unit = {}
    override var onError: (String) -> Unit = {}
    override var onNotice: (String?) -> Unit = {}
    override fun release() {}
    override val frame: StateFlow<ImageBitmap?> = MutableStateFlow(null)
    override fun attach() {}
    override fun detach() {}
    override fun setEnabled(enabled: Boolean) { videoOn = enabled }
}

class DefaultPlayerControllerTest {
    private val video = Track("/v/clip.mp4", "Clip", "A", "B", 300, mediaType = MediaType.VIDEO, filePath = "/v/clip.mp4")
    private val song = Track("/m/a.mp3", "A", "A", "B", 200, filePath = "/m/a.mp3")

    @Test
    fun audioOnlyKeepsPosition() {
        val e = FakeEngine()
        val p = DefaultPlayerController(CoroutineScope(Dispatchers.Unconfined), e)
        p.play(listOf(video), 0)
        e.onPosition(42_500)
        assertTrue(p.state.value.hasVideo)
        p.setVideoEnabled(false)
        assertEquals(42, p.state.value.positionSec)
        assertFalse(p.state.value.videoEnabled)
        assertFalse(e.videoOn)
        assertEquals("/v/clip.mp4", e.loaded, "no se recarga el archivo")
    }

    @Test
    fun volumeNeverAmplifies() {
        val e = FakeEngine()
        val p = DefaultPlayerController(CoroutineScope(Dispatchers.Unconfined), e)
        p.setVolume(3f)
        assertEquals(1f, p.state.value.volume)
        assertEquals(1f, e.lastVolume)
    }

    @Test
    fun endOfTrackGoesToNext() {
        val e = FakeEngine()
        val p = DefaultPlayerController(CoroutineScope(Dispatchers.Unconfined), e)
        p.play(listOf(song, video), 0)
        e.onEnded()
        assertEquals(1, p.state.value.index)
        assertEquals("/v/clip.mp4", e.loaded)
        assertTrue(p.state.value.videoEnabled, "cada video nuevo empieza con imagen")
    }
}

class QueueEnqueueTest {
    @Test
    fun playNextAndAddToQueue() {
        val a = Track("a", "A", "x", "y", 100); val b = Track("b", "B", "x", "y", 100); val c = Track("c", "C", "x", "y", 100)
        val p = DefaultPlayerController(CoroutineScope(Dispatchers.Unconfined), null)
        p.play(listOf(a, b), 0)
        p.enqueue(c, next = true)
        assertEquals(listOf("a", "c", "b"), p.state.value.queue.map { it.id })
        p.enqueue(c, next = false)
        assertEquals(listOf("a", "c", "b", "c"), p.state.value.queue.map { it.id })
        p.release()
    }
}

class QueueEditTest {
    private fun t(id: String) = Track(id, id.uppercase(), "x", "y", 100)
    private fun player() = DefaultPlayerController(CoroutineScope(Dispatchers.Unconfined), null)

    @Test
    fun reorderKeepsCurrentTrack() {
        val p = player()
        p.play(listOf(t("a"), t("b"), t("c"), t("d")), 1) // suena b
        p.moveInQueue(3, 2)
        assertEquals(listOf("a", "b", "d", "c"), p.state.value.queue.map { it.id })
        assertEquals("b", p.state.value.current?.id)
        p.moveInQueue(0, 3) // mover algo que estaba antes de la actual
        assertEquals("b", p.state.value.current?.id)
        p.release()
    }

    @Test
    fun removeAndClearUpcoming() {
        val p = player()
        p.play(listOf(t("a"), t("b"), t("c"), t("d")), 1)
        p.removeFromQueue(2)
        assertEquals(listOf("a", "b", "d"), p.state.value.queue.map { it.id })
        p.removeFromQueue(1) // la actual no se quita
        assertEquals(3, p.state.value.queue.size)
        p.clearUpcoming()
        assertEquals(listOf("a", "b"), p.state.value.queue.map { it.id })
        p.release()
    }

    @Test
    fun endOfQueueStopsOrRepeats() {
        val e = FakeEngine()
        val songs = listOf(Track("/1", "1", "x", "y", 100, filePath = "/1"), Track("/2", "2", "x", "y", 100, filePath = "/2"))
        val p = DefaultPlayerController(CoroutineScope(Dispatchers.Unconfined), e)
        p.configure(fadeSec = 0, gapless = false, repeatQueue = false)
        p.play(songs, 1)
        e.onEnded()
        assertFalse(p.state.value.isPlaying, "al terminar la cola se detiene")
        p.configure(fadeSec = 0, gapless = false, repeatQueue = true)
        p.play(songs, 1)
        e.onEnded()
        assertEquals(0, p.state.value.index, "con Repetir vuelve a empezar")
        assertTrue(p.state.value.isPlaying)
    }

    @Test
    fun restoreLoadsPausedAtPosition() {
        val e = FakeEngine()
        val p = DefaultPlayerController(CoroutineScope(Dispatchers.Unconfined), e)
        p.restore(listOf(Track("/1", "1", "x", "y", 300, filePath = "/1")), 0, 95)
        assertEquals(95, p.state.value.positionSec)
        assertFalse(p.state.value.isPlaying)
        assertEquals("/1", e.loaded)
    }
}
