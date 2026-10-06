package app.aurora.data

import app.aurora.domain.Playlist
import app.aurora.domain.Track
import app.aurora.platform.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.random.Random
import kotlin.time.Clock

/** Listas del usuario y canciones con "Me gusta", guardadas en el almacén de ajustes. */
class PlaylistRepository(private val store: KeyValueStore) {
    private val _playlists = MutableStateFlow(PlaylistCodec.decode(store.get(KEY_LISTS)))
    val playlists: StateFlow<List<Playlist>> = _playlists

    private val _liked = MutableStateFlow(store.get(KEY_LIKED)?.split('\n')?.filter { it.isNotBlank() }?.toSet() ?: emptySet())
    val liked: StateFlow<Set<String>> = _liked

    fun get(id: String): Playlist? = _playlists.value.firstOrNull { it.id == id }

    fun create(name: String, tracks: List<Track> = emptyList()): Playlist {
        val n = _playlists.value.size
        val p = Playlist(
            id = "user-${Clock.System.now().toEpochMilliseconds()}-${Random.nextInt(1000, 9999)}",
            name = name.trim().ifEmpty { "Nueva lista" },
            trackIds = tracks.map { it.id }.distinct(),
            colors = COLORS[n % COLORS.size],
            isUser = true,
        )
        save(_playlists.value + p)
        return p
    }

    fun rename(id: String, name: String) = update(id) { it.copy(name = name.trim().ifEmpty { it.name }) }

    fun delete(id: String) = save(_playlists.value.filterNot { it.id == id })

    /** Añade sin repetir; devuelve cuántas canciones nuevas entraron. */
    fun add(id: String, tracks: List<Track>): Int {
        var added = 0
        update(id) { p ->
            val fresh = tracks.map { it.id }.distinct().filter { it !in p.trackIds }
            added = fresh.size
            p.copy(trackIds = p.trackIds + fresh)
        }
        return added
    }

    fun remove(id: String, trackId: String) = update(id) { it.copy(trackIds = it.trackIds - trackId) }

    /** Mueve una canción dentro de la lista (para reordenar). */
    fun move(id: String, from: Int, to: Int) = update(id) { p ->
        if (from !in p.trackIds.indices || to !in p.trackIds.indices) return@update p
        val list = p.trackIds.toMutableList()
        list.add(to, list.removeAt(from))
        p.copy(trackIds = list)
    }

    /** @return `true` si quedó con "Me gusta". */
    fun toggleLike(trackId: String): Boolean {
        val now = if (trackId in _liked.value) _liked.value - trackId else _liked.value + trackId
        _liked.value = now
        store.put(KEY_LIKED, now.joinToString("\n"))
        return trackId in now
    }

    private fun update(id: String, f: (Playlist) -> Playlist) = save(_playlists.value.map { if (it.id == id) f(it) else it })

    private fun save(list: List<Playlist>) {
        _playlists.value = list
        store.put(KEY_LISTS, PlaylistCodec.encode(list))
    }

    companion object {
        const val KEY_LISTS = "listas"
        const val KEY_LIKED = "me_gusta"
        private val COLORS = listOf(
            listOf(0xFF9B5CFF, 0xFFFF5FA2, 0xFF1B1640),
            listOf(0xFF38E1C6, 0xFF5B8CFF, 0xFFF2EEFF),
            listOf(0xFFFFB547, 0xFFFF5F5F, 0xFFFF5FA2),
            listOf(0xFF5B8CFF, 0xFFFFB547, 0xFF9B5CFF),
            listOf(0xFF2F7D6A, 0xFFB9A3E3, 0xFFFFD9C7),
        )
    }
}

/**
 * Formato de texto simple y estable: una lista por línea, campos separados por tabulador
 * (id, nombre, colores, canciones) y canciones separadas por U+001F. Se escapan %, tab, salto y U+001F.
 */
object PlaylistCodec {
    private const val SEP = '\u001F'

    private fun esc(s: String) = buildString {
        s.forEach { c ->
            when (c) {
                '%' -> append("%25"); '\t' -> append("%09"); '\n' -> append("%0A"); '\r' -> append("%0D"); SEP -> append("%1F")
                else -> append(c)
            }
        }
    }

    private fun unesc(s: String) = s.replace("%09", "\t").replace("%0A", "\n").replace("%0D", "\r").replace("%1F", SEP.toString()).replace("%25", "%")

    fun encode(list: List<Playlist>): String = list.joinToString("\n") { p ->
        listOf(esc(p.id), esc(p.name), p.colors.joinToString(",") { it.toString(16) }, p.trackIds.joinToString(SEP.toString()) { esc(it) })
            .joinToString("\t")
    }

    fun decode(raw: String?): List<Playlist> = raw.orEmpty().split('\n').filter { it.isNotBlank() }.mapNotNull { line ->
        val f = line.split('\t')
        if (f.size < 4) return@mapNotNull null
        Playlist(
            id = unesc(f[0]),
            name = unesc(f[1]),
            colors = f[2].split(',').mapNotNull { it.toLongOrNull(16) },
            trackIds = if (f[3].isEmpty()) emptyList() else f[3].split(SEP).map(::unesc),
            isUser = true,
        )
    }
}
