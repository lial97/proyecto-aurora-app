package app.aurora.widget

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** Una canción de "A continuación" (posición en la cola para reproducirla al tocarla). */
data class WidgetNext(val index: Int, val id: String, val title: String, val artist: String)

/**
 * Lo que muestran los widgets. Se guarda (SharedPreferences y la portada en un archivo), así se ven bien
 * con la app cerrada o después de reiniciar el teléfono, sin cargar la biblioteca.
 */
data class WidgetSnapshot(
    val trackId: String? = null,
    val title: String = "",
    val artist: String = "",
    val durationSec: Int = 0,
    val positionSec: Int = 0,
    /** Momento en que se tomó [positionSec] (para calcular la posición si sigue sonando). */
    val savedAtMs: Long = 0,
    val playing: Boolean = false,
    /** No hay nada cargado en el reproductor: se muestra la última canción con "Continuar". */
    val resting: Boolean = false,
    val liked: Boolean = false,
    val shuffle: Boolean = false,
    val next: List<WidgetNext> = emptyList(),
    /** Tema de la app (`ThemeId.name`). */
    val theme: String = "AURORA",
    /** "Colores del fondo de pantalla" (Material You) en Ajustes › Apariencia. */
    val wallpaper: Boolean = false,
    /** Énfasis y fondo oscuro sacados de la portada (ARGB). */
    val accent: Int = DEFAULT_ACCENT,
    val deep: Int = DEFAULT_DEEP,
    /** Cambia con cada portada nueva (para volver a leer el archivo). */
    val coverVersion: Long = 0,
) {
    val empty: Boolean get() = trackId == null

    /** Posición a mostrar ahora: si suena, avanza desde que se guardó. */
    fun positionAt(nowMs: Long, stillPlaying: Boolean): Int {
        val extra = if (playing && stillPlaying) ((nowMs - savedAtMs) / 1000).toInt().coerceAtLeast(0) else 0
        val p = positionSec + extra
        return if (durationSec > 0) p.coerceAtMost(durationSec) else p
    }

    companion object {
        /** Canción de ejemplo de las vistas previas del selector de widgets. */
        const val PREVIEW_ID = "vista_previa"

        fun preview() = WidgetSnapshot(
            trackId = PREVIEW_ID, title = "Luz de neón", artist = "Aurora", durationSec = 214, positionSec = 78,
            savedAtMs = Long.MAX_VALUE, playing = true, liked = true,
            next = listOf(WidgetNext(1, "a", "Marea alta", "Bruma"), WidgetNext(2, "b", "Ciudad dormida", "Estadio")),
        )

        const val DEFAULT_ACCENT = 0xFFFF5FA2.toInt()
        const val DEFAULT_DEEP = 0xFF3A1A5C.toInt()
    }
}

object WidgetStore {
    private const val PREFS = "aurora_widgets"
    private const val SEP = '\u001F'
    private val state = MutableStateFlow<WidgetSnapshot?>(null)

    private fun prefs(context: Context): SharedPreferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Portada de la canción actual: JPEG cuadrado de 512 px (`AuroraRuntime.square`). */
    fun coverFile(context: Context): File = File(context.applicationContext.filesDir, "widget_portada.jpg")

    /** Portadas pequeñas de "A continuación" (posición 0 o 1), con el id de la pista en el nombre. */
    fun nextCoverFile(context: Context, id: String): File = File(context.applicationContext.filesDir, "widget_siguiente_${id.hashCode().toUInt()}.jpg")

    /** Lo último guardado; los widgets abiertos se vuelven a dibujar cuando cambia. */
    fun flow(context: Context): StateFlow<WidgetSnapshot?> {
        if (state.value == null) state.value = read(context)
        return state
    }

    fun current(context: Context): WidgetSnapshot = flow(context).value ?: WidgetSnapshot()

    fun save(context: Context, s: WidgetSnapshot) {
        prefs(context).edit()
            .putString("id", s.trackId).putString("titulo", s.title).putString("artista", s.artist)
            .putInt("duracion", s.durationSec).putInt("posicion", s.positionSec).putLong("guardado", s.savedAtMs)
            .putBoolean("sonando", s.playing).putBoolean("en_reposo", s.resting).putBoolean("me_gusta", s.liked).putBoolean("aleatorio", s.shuffle)
            .putString("siguientes", s.next.joinToString("\n") { listOf(it.index, it.id, it.title, it.artist).joinToString(SEP.toString()) })
            .putString("tema", s.theme).putBoolean("fondo_pantalla", s.wallpaper).putInt("enfasis", s.accent).putInt("fondo", s.deep).putLong("portada", s.coverVersion)
            .apply()
        state.value = s
    }

    private fun read(context: Context): WidgetSnapshot {
        val p = prefs(context)
        return WidgetSnapshot(
            trackId = p.getString("id", null),
            title = p.getString("titulo", "").orEmpty(),
            artist = p.getString("artista", "").orEmpty(),
            durationSec = p.getInt("duracion", 0),
            positionSec = p.getInt("posicion", 0),
            savedAtMs = p.getLong("guardado", 0),
            playing = p.getBoolean("sonando", false),
            resting = p.getBoolean("en_reposo", false),
            liked = p.getBoolean("me_gusta", false),
            shuffle = p.getBoolean("aleatorio", false),
            next = p.getString("siguientes", "").orEmpty().lines().mapNotNull { line ->
                val f = line.split(SEP)
                if (f.size < 4) null else WidgetNext(f[0].toIntOrNull() ?: return@mapNotNull null, f[1], f[2], f[3])
            },
            theme = p.getString("tema", "AURORA") ?: "AURORA",
            wallpaper = p.getBoolean("fondo_pantalla", false),
            accent = p.getInt("enfasis", WidgetSnapshot.DEFAULT_ACCENT),
            deep = p.getInt("fondo", WidgetSnapshot.DEFAULT_DEEP),
            coverVersion = p.getLong("portada", 0),
        )
    }
}
