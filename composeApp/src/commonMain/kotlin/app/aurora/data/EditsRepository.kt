package app.aurora.data

import app.aurora.domain.Track
import app.aurora.domain.TrackEdit
import app.aurora.platform.KeyValueStore

/** Correcciones de datos hechas en la app (se aplican sobre lo que traen los archivos). */
class EditsRepository(private val store: KeyValueStore) {
    private fun esc(s: String) = s.replace("%", "%25").replace("\t", "%09").replace("\n", "%0A")
    private fun unesc(s: String) = s.replace("%09", "\t").replace("%0A", "\n").replace("%25", "%")

    fun get(id: String): TrackEdit? {
        val f = store.get("edit|$id")?.split('\t') ?: return null
        fun v(i: Int) = f.getOrNull(i)?.takeIf { it.isNotEmpty() }?.let(::unesc)
        return TrackEdit(v(0), v(1), v(2), v(3)?.toIntOrNull(), v(4), v(5) == "1")
    }

    fun set(id: String, e: TrackEdit) = store.put(
        "edit|$id",
        listOf(e.title, e.artist, e.album, e.year?.toString(), e.genre, if (e.customCover) "1" else null).joinToString("\t") { esc(it.orEmpty()) },
    )

    fun apply(tracks: List<Track>): List<Track> = tracks.map { t -> get(t.id)?.applyTo(t) ?: t }
}
