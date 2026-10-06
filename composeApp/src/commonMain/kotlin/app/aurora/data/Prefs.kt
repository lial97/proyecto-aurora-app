package app.aurora.data

import app.aurora.audio.EqSettings
import app.aurora.platform.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Qué hacer al terminar la cola. */
enum class EndOfQueue { STOP, REPEAT }

/** Preferencias de Ajustes (todas se guardan y se aplican al instante). */
data class AppPrefs(
    // Biblioteca
    val watchFolders: Boolean = true,
    val includeVideos: Boolean = true,
    // Apariencia
    val dynamicColor: Boolean = true,
    val backgroundLights: Boolean = true,
    val compact: Boolean = false,
    /** Android: los widgets toman los colores del fondo de pantalla (Material You) en vez de los del tema. */
    val widgetsWallpaper: Boolean = false,
    // Sonido
    val eq: EqSettings = EqSettings(),
    val normalize: Boolean = false,
    // Reproducción
    val fadeSec: Int = 0,
    val gapless: Boolean = true,
    val resume: Boolean = true,
    val endOfQueue: EndOfQueue = EndOfQueue.STOP,
    /** Android: controles y datos de la canción en la pantalla de bloqueo (si no, solo "Aurora · Reproduciendo"). */
    val lockScreenControls: Boolean = true,
    // Letras
    val lrcFirst: Boolean = true,
    /** Guardar la letra descargada como archivo .lrc junto a la canción (así no se vuelve a descargar). */
    val autoSaveLrc: Boolean = true,
    // Accesibilidad
    val textScale: Float = 1f,
    val bigLyrics: Boolean = false,
    val highContrast: Boolean = false,
    val reduceMotion: Boolean = false,
    val strongFocus: Boolean = false,
    val announce: Boolean = true,
)

class PrefsRepository(private val store: KeyValueStore) {
    private val _prefs = MutableStateFlow(read())
    val prefs: StateFlow<AppPrefs> = _prefs

    fun update(f: (AppPrefs) -> AppPrefs) {
        val p = f(_prefs.value)
        _prefs.value = p
        write(p)
    }

    private fun b(k: String, d: Boolean) = store.get("pref.$k")?.let { it == "1" } ?: d

    private fun read(): AppPrefs {
        val d = AppPrefs()
        return AppPrefs(
            watchFolders = b("vigilar", d.watchFolders),
            includeVideos = b("videos", d.includeVideos),
            dynamicColor = b("color_cancion", d.dynamicColor),
            backgroundLights = b("luces", d.backgroundLights),
            compact = b("compacta", d.compact),
            widgetsWallpaper = b("widgets_fondo", d.widgetsWallpaper),
            eq = EqSettings.decode(store.get("pref.eq")),
            normalize = b("normalizar", d.normalize),
            fadeSec = store.get("pref.fundido")?.toIntOrNull()?.coerceIn(0, 12) ?: d.fadeSec,
            gapless = b("sin_pausas", d.gapless),
            resume = b("recordar", d.resume),
            endOfQueue = if (store.get("pref.fin_cola") == "repetir") EndOfQueue.REPEAT else EndOfQueue.STOP,
            lockScreenControls = b("bloqueo", d.lockScreenControls),
            lrcFirst = b("lrc_primero", d.lrcFirst),
            autoSaveLrc = b("guardar_lrc", d.autoSaveLrc),
            textScale = store.get("pref.texto")?.toFloatOrNull()?.coerceIn(.9f, 1.5f) ?: d.textScale,
            bigLyrics = b("letra_grande", d.bigLyrics),
            highContrast = b("contraste", d.highContrast),
            reduceMotion = b("menos_movimiento", d.reduceMotion),
            strongFocus = b("foco", d.strongFocus),
            announce = b("anunciar", d.announce),
        )
    }

    private fun write(p: AppPrefs) {
        fun put(k: String, v: Boolean) = store.put("pref.$k", if (v) "1" else "0")
        put("vigilar", p.watchFolders); put("videos", p.includeVideos)
        put("color_cancion", p.dynamicColor); put("luces", p.backgroundLights); put("compacta", p.compact)
        put("widgets_fondo", p.widgetsWallpaper)
        store.put("pref.eq", p.eq.encode()); put("normalizar", p.normalize)
        store.put("pref.fundido", p.fadeSec.toString()); put("sin_pausas", p.gapless); put("recordar", p.resume)
        store.put("pref.fin_cola", if (p.endOfQueue == EndOfQueue.REPEAT) "repetir" else "detener")
        put("bloqueo", p.lockScreenControls)
        put("lrc_primero", p.lrcFirst); put("guardar_lrc", p.autoSaveLrc)
        store.put("pref.texto", p.textScale.toString()); put("letra_grande", p.bigLyrics); put("contraste", p.highContrast)
        put("menos_movimiento", p.reduceMotion); put("foco", p.strongFocus); put("anunciar", p.announce)
    }
}

/** Veces reproducida y última vez, por pista. */
class PlayStatsRepository(private val store: KeyValueStore) {
    data class Stats(val count: Int, val lastMs: Long?)

    fun get(id: String): Stats {
        val raw = store.get("stats|$id")?.split(';')
        return Stats(raw?.getOrNull(0)?.toIntOrNull() ?: 0, raw?.getOrNull(1)?.toLongOrNull())
    }

    fun record(id: String, nowMs: Long) {
        val s = get(id)
        store.put("stats|$id", "${s.count + 1};$nowMs")
    }
}

/** Desfase de la letra por canción (cuando la letra de LRCLIB va adelantada o atrasada). */
class LyricsOffsetRepository(private val store: KeyValueStore) {
    fun get(id: String): Long = store.get("lrcoff|$id")?.toLongOrNull() ?: 0L
    fun set(id: String, ms: Long) = store.put("lrcoff|$id", if (ms == 0L) null else ms.toString())
}
