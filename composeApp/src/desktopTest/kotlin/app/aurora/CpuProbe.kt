package app.aurora

import androidx.compose.ui.awt.ComposeWindow
import app.aurora.data.SettingsRepository
import app.aurora.domain.MediaType
import app.aurora.platform.DesktopMediaSource
import app.aurora.platform.JavaHttp
import app.aurora.platform.MediaSource
import app.aurora.platform.MemoryStore
import app.aurora.platform.PlatformServices
import app.aurora.player.VlcEngine
import java.lang.management.ManagementFactory
import javax.swing.SwingUtilities
import kotlin.test.Test

/**
 * Diagnóstico: CPU de la app en una ventana real (con la tarjeta gráfica y la frecuencia del monitor) mientras
 * suena una canción en silencio. Mide Inicio y el reproductor a pantalla completa (letra o portada).
 * No toca la configuración del usuario. Solo corre con:
 *   AURORA_CPU_PROBE=1 AURORA_MUSIC=<carpeta> AURORA_VLC_DIR=<composeApp/resources/<sistema>> (opcional)
 *   ./gradlew :composeApp:desktopTest --tests app.aurora.CpuProbe --rerun -i   (resultados en las líneas "PROBE")
 * Opcionales: AURORA_FPS=1 (fotogramas por segundo de Skiko), AURORA_TRACE=1 (estados que más cambian y quién los
 * escribe: lo que obliga a redibujar).
 */
class CpuProbe {
    @Test fun probe() {
        if (System.getenv("AURORA_CPU_PROBE") == null) return
        val music = System.getenv("AURORA_MUSIC") ?: return
        System.getenv("AURORA_VLC_DIR")?.let { System.setProperty("compose.application.resources.dir", it) }
        configureDesktop()
        if (System.getenv("AURORA_FPS") != null) { System.setProperty("skiko.fps.enabled", "true"); System.setProperty("skiko.fps.periodSeconds", "5") }
        val store = MemoryStore().apply { put(SettingsRepository.KEY_ONBOARDING, "1"); put(SettingsRepository.KEY_FOLDERS, music) }
        // Sin análisis de tempo: decodifica la biblioteca en segundo plano y ensuciaría la medición.
        val media = object : MediaSource by DesktopMediaSource() { override val canDecodeForTempo = false }
        val platform = PlatformServices("Probe", true, store, media, VlcEngine(), http = JavaHttp())
        var st: AppState? = null
        lateinit var window: ComposeWindow
        SwingUtilities.invokeAndWait {
            window = ComposeWindow().apply {
                setSize(1280, 800)
                setLocationRelativeTo(null)
                setContent { App(platform) { st = it } }
                isVisible = true
            }
        }
        val deadline = System.currentTimeMillis() + 120_000
        while (st?.library?.state?.value?.scannedOnce != true && System.currentTimeMillis() < deadline) Thread.sleep(200)
        val s = checkNotNull(st) { "La app no terminó de cargar la biblioteca" }
        val songs = s.library.state.value.tracks.filter { it.mediaType == MediaType.AUDIO }
        check(songs.isNotEmpty()) { "No hay canciones en $music" }
        SwingUtilities.invokeAndWait { s.player.setVolume(0f); s.play(songs, 0) }
        Thread.sleep(6000)
        println("PROBE renderApi=${window.renderApi} os=${System.getProperty("os.name")} interval=${app.aurora.components.animationFrameIntervalMs}")
        if (System.getenv("AURORA_TRACE") != null) {
            // Qué estados cambian y cuántas veces: lo que obliga a redibujar.
            val counts = java.util.concurrent.ConcurrentHashMap<Int, Int>()
            val sample = java.util.concurrent.ConcurrentHashMap<Int, String>()
            var applies = 0
            val h = androidx.compose.runtime.snapshots.Snapshot.registerApplyObserver { changed, _ ->
                applies++
                changed.forEach { o ->
                    val id = System.identityHashCode(o)
                    counts.merge(id, 1, Int::plus)
                    sample[id] = o.toString().take(110)
                }
            }
            // Quién escribe los estados que más cambian (una pila por estado).
            val stacks = java.util.concurrent.ConcurrentHashMap<Int, String>()
            val w = androidx.compose.runtime.snapshots.Snapshot.registerGlobalWriteObserver { o ->
                stacks.computeIfAbsent(System.identityHashCode(o)) {
                    Thread.currentThread().stackTrace.map { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
                        .filter { "aurora" in it.lowercase() || "Animat" in it || "Lazy" in it }.take(6).joinToString(" < ")
                }
            }
            Thread.sleep(5000)
            h.dispose()
            w.dispose()
            counts.entries.sortedByDescending { it.value }.take(12).forEach { println("STACK ${sample[it.key]?.take(40)} :: ${stacks[it.key]}") }
            println("TRACE ${applies / 5} cambios aplicados/s")
            counts.entries.sortedByDescending { it.value }.take(12).forEach { println("TRACE ${it.value / 5}/s ${sample[it.key]}") }
        }
        measure("inicio")
        SwingUtilities.invokeAndWait { s.nowPlayingFocus = true }
        Thread.sleep(3000)
        measure("reproductor")
        SwingUtilities.invokeAndWait { s.player.pauseIfPlaying() }
        Thread.sleep(3000)
        measure("pausa")
        SwingUtilities.invokeAndWait { window.dispose() }
    }

    private fun measure(name: String, seconds: Int = 15) {
        val os = ManagementFactory.getOperatingSystemMXBean() as com.sun.management.OperatingSystemMXBean
        val threads = ManagementFactory.getThreadMXBean()
        fun uiThreads() = threads.allThreadIds.associateWith { threads.getThreadInfo(it)?.threadName.orEmpty() }
            .filterValues { it.startsWith("AWT-EventQueue") || it.startsWith("skiko") }
            .mapValues { threads.getThreadCpuTime(it.key) }
        val p0 = os.processCpuTime
        val u0 = uiThreads()
        val j0 = threads.allThreadIds.sumOf { maxOf(0L, threads.getThreadCpuTime(it)) }
        Thread.sleep(seconds * 1000L)
        val p1 = os.processCpuTime
        val u1 = uiThreads()
        val ui = u1.entries.sumOf { (k, v) -> v - (u0[k] ?: v) }
        val java = threads.allThreadIds.sumOf { maxOf(0L, threads.getThreadCpuTime(it)) } - j0
        val pct = (p1 - p0) / 1e7 / seconds
        println("PROBE %-12s proceso %5.1f %% de un núcleo · ventana+render %5.1f %% · Java %5.1f %%".format(name, pct, ui / 1e7 / seconds, java / 1e7 / seconds))
    }
}
