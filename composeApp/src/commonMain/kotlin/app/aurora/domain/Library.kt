package app.aurora.domain

enum class SortOrder(val label: String) {
    TITLE("Título"),
    YEAR("Año"),
    DATE_ADDED("Fecha de añadido"),
}

/** Agrupación de pistas bajo un nombre (álbum, artista o género). */
data class TrackGroup(val name: String, val tracks: List<Track>) {
    val year: Int? get() = tracks.mapNotNull { it.year }.maxOrNull()
    val latestAddedMs: Long get() = tracks.maxOf { it.dateAddedMs }
}

private val titleComparator = compareBy<String> { it.lowercase() }

fun List<Track>.sortedBy(order: SortOrder): List<Track> = when (order) {
    SortOrder.TITLE -> sortedWith(compareBy(titleComparator) { it.title })
    // Más recientes primero; sin año al final.
    SortOrder.YEAR -> sortedWith(compareByDescending<Track> { it.year ?: Int.MIN_VALUE }.thenBy { it.title.lowercase() })
    SortOrder.DATE_ADDED -> sortedByDescending { it.dateAddedMs }
}

fun List<TrackGroup>.sortedGroupsBy(order: SortOrder): List<TrackGroup> = when (order) {
    SortOrder.TITLE -> sortedWith(compareBy(titleComparator) { it.name })
    SortOrder.YEAR -> sortedWith(compareByDescending<TrackGroup> { it.year ?: Int.MIN_VALUE }.thenBy { it.name.lowercase() })
    SortOrder.DATE_ADDED -> sortedByDescending { it.latestAddedMs }
}

private fun List<Track>.groupInto(order: SortOrder, key: (Track) -> String): List<TrackGroup> =
    groupBy(key).map { (name, tracks) -> TrackGroup(name, tracks.sortedBy(order)) }.sortedGroupsBy(order)

/** Solo audio: los videos nunca se mezclan con la música. */
fun List<Track>.songs(): List<Track> = filter { it.mediaType == MediaType.AUDIO }

fun List<Track>.videos(): List<Track> = filter { it.mediaType == MediaType.VIDEO }

fun List<Track>.albums(order: SortOrder = SortOrder.TITLE) = songs().groupInto(order) { it.album }

fun List<Track>.artists(order: SortOrder = SortOrder.TITLE) = songs().groupInto(order) { it.artist }

fun List<Track>.genres(order: SortOrder = SortOrder.TITLE) = songs().groupInto(order) { it.genre ?: "Sin género" }

/** Criterios del menú "Ordenar" de la biblioteca (móvil), con el texto de cada dirección. */
enum class LibrarySort(val label: String, val ascLabel: String, val descLabel: String) {
    TITLE("Título", "A–Z", "Z–A"),
    ARTIST("Artista", "A–Z", "Z–A"),
    ALBUM("Álbum", "A–Z", "Z–A"),
    YEAR("Año", "Recientes", "Antiguos"),
    ADDED("Añadidas", "Recientes", "Antiguas"),
    MOST_PLAYED("Más escuchadas", "Más", "Menos"),
    DURATION("Duración", "Cortas", "Largas"),
}

private fun key(s: String) = s.lowercase()
    .replace('á', 'a').replace('é', 'e').replace('í', 'i').replace('ó', 'o').replace('ú', 'u').replace('ñ', 'n')
    .trimStart('¿', '¡', '(', '[', '"', '\'', ' ')

/**
 * Ordena las canciones. La dirección "natural" de cada criterio es la primera etiqueta
 * (A–Z, Recientes, Más, Cortas); [reversed] la invierte. [plays] da las veces reproducida.
 */
fun List<Track>.sortedFor(sort: LibrarySort, reversed: Boolean, plays: (Track) -> Int = { 0 }): List<Track> {
    val c: Comparator<Track> = when (sort) {
        LibrarySort.TITLE -> compareBy { key(it.title) }
        LibrarySort.ARTIST -> compareBy<Track> { key(it.artist) }.thenBy { key(it.title) }
        LibrarySort.ALBUM -> compareBy<Track> { key(it.album) }.thenBy { it.trackNumber ?: 0 }.thenBy { key(it.title) }
        LibrarySort.YEAR -> compareByDescending<Track> { it.year ?: Int.MIN_VALUE }.thenBy { key(it.title) }
        LibrarySort.ADDED -> compareByDescending { it.dateAddedMs }
        LibrarySort.MOST_PLAYED -> compareByDescending<Track> { plays(it) }.thenBy { key(it.title) }
        LibrarySort.DURATION -> compareBy<Track> { it.durationSec }.thenBy { key(it.title) }
    }
    return sortedWith(c).let { if (reversed) it.reversed() else it }
}

/** Grupo de una canción según el orden (encabezados A, B, C…, artista, álbum o año); `null` = sin grupos. */
fun Track.groupFor(sort: LibrarySort, nowMs: Long = 0): String? = when (sort) {
    LibrarySort.TITLE -> key(title).firstOrNull()?.let { if (it.isLetter()) it.uppercase() else "#" } ?: "#"
    LibrarySort.ARTIST -> artist
    LibrarySort.ALBUM -> album
    LibrarySort.YEAR -> year?.toString() ?: "Sin año"
    LibrarySort.ADDED -> if (nowMs <= 0) null else {
        val days = (nowMs - dateAddedMs) / 86_400_000
        when { days < 7 -> "Esta semana"; days < 31 -> "Este mes"; else -> "Antes" }
    }
    else -> null
}
