package app.aurora.domain

import kotlin.random.Random

private val mixColors = listOf(
    listOf(0xFF9B5CFF, 0xFFFF5FA2, 0xFF1B1640),
    listOf(0xFF38E1C6, 0xFF5B8CFF, 0xFFF2EEFF),
    listOf(0xFFFFB547, 0xFFFF5F5F, 0xFFFF5FA2),
    listOf(0xFF5B8CFF, 0xFFFFB547, 0xFF9B5CFF),
)

/**
 * Mezclas automáticas a partir de la biblioteca real: añadidas hace poco, descubrimientos,
 * tus artistas con más canciones y Me gusta.
 */
fun autoMixes(tracks: List<Track>, liked: Collection<String>, seed: Int = 7): List<Playlist> {
    val songs = tracks.songs()
    if (songs.isEmpty()) return emptyList()
    val out = mutableListOf<Playlist>()
    if (liked.isNotEmpty()) {
        out += Playlist("mix-liked", "Me gusta", songs.filter { it.id in liked }.map { it.id }, mixColors[0], "Las canciones que marcaste")
    }
    out += Playlist("mix-recent", "Añadidas hace poco", songs.sortedBy(SortOrder.DATE_ADDED).take(30).map { it.id }, mixColors[1],
        "Lo último que llegó a tu biblioteca")
    out += Playlist("mix-discover", "Descubrimientos", songs.shuffled(Random(seed)).take(25).map { it.id }, mixColors[3],
        "Una selección al azar de tu música")
    songs.artists().filter { it.name != UNKNOWN_ARTIST && it.tracks.size >= 2 }
        .sortedByDescending { it.tracks.size }.take(3)
        .forEachIndexed { i, g ->
            out += Playlist("mix-artist-$i", "Lo mejor de ${g.name}", g.tracks.map { it.id }, mixColors[(i + 2) % 4], "${g.tracks.size} canciones")
        }
    return out.filter { it.trackIds.isNotEmpty() }
}

fun Playlist.resolve(byId: Map<String, Track>): List<Track> = trackIds.mapNotNull { byId[it] }
