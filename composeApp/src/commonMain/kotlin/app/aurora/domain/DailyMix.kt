package app.aurora.domain

import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/** Lo que se sabe de una pista para recomendar: cuántas veces sonó y la última vez (ms), si sonó. */
data class PlayHistory(val count: Int, val lastMs: Long?)

/**
 * "Tu mezcla de hoy": canciones elegidas según lo que más se escucha.
 *
 * 1. Peso de cada canción escuchada: veces × caída por antigüedad (vale la mitad cada [HALF_LIFE_DAYS] días), más un
 *    extra si es Me gusta.
 * 2. Afinidad: ese peso sumado por artista y por género (normalizados de 0 a 1).
 * 3. Puntaje de cada canción: 0,45 × afinidad de su género + 0,35 × afinidad de su artista + 0,20 × novedad (lo que
 *    no sonó hace tiempo). Lo que sonó hoy se deja fuera y lo de la mezcla anterior pesa menos. Un poco de azar con
 *    la fecha como semilla: cada día cambia, pero el mismo día da la misma mezcla.
 * 4. Las [SIZE] mejores, como mucho [MAX_PER_ARTIST] por artista, sin videos, en orden mezclado.
 * Sin historial (biblioteca nueva): Me gusta, lo añadido hace poco y azar.
 */
object DailyMix {
    const val SIZE = 25
    const val MAX_PER_ARTIST = 3
    private const val HALF_LIFE_DAYS = 30.0
    private const val DAY_MS = 86_400_000L

    /** Géneros de una pista, normalizados ("Pop & Rock" → pop, rock). */
    fun genresOf(t: Track): List<String> =
        t.genre.orEmpty().split('&', ',', '/', ';').map { it.trim().lowercase() }.filter { it.isNotEmpty() }

    fun build(
        tracks: List<Track>,
        history: (String) -> PlayHistory,
        liked: Set<String>,
        nowMs: Long,
        dateKey: String,
        previous: Set<String> = emptySet(),
    ): List<Track> {
        val songs = tracks.filter { it.mediaType == MediaType.AUDIO }
        if (songs.isEmpty()) return emptyList()
        val rnd = Random(dateKey.hashCode())

        fun weight(t: Track): Double {
            val h = history(t.id)
            val age = h.lastMs?.let { (nowMs - it).coerceAtLeast(0) / DAY_MS.toDouble() } ?: HALF_LIFE_DAYS
            return h.count * 0.5.pow(age / HALF_LIFE_DAYS) + (if (t.id in liked) 3.0 else 0.0)
        }
        val weights = songs.associate { it.id to weight(it) }
        val artistAff = HashMap<String, Double>()
        val genreAff = HashMap<String, Double>()
        for (t in songs) {
            val w = weights.getValue(t.id).takeIf { it > 0 } ?: continue
            artistAff.merge(t.artist.lowercase(), w, Double::plus)
            val g = genresOf(t)
            for (name in g) genreAff.merge(name, w / g.size, Double::plus)
        }
        val maxArtist = artistAff.values.maxOrNull() ?: 0.0
        val maxGenre = genreAff.values.maxOrNull() ?: 0.0

        fun novelty(t: Track): Double {
            val last = history(t.id).lastMs ?: return 1.0
            val days = (nowMs - last) / DAY_MS.toDouble()
            return if (days < 3) 0.2 else min(1.0, days / 14)
        }
        val heardToday = songs.filter { s -> history(s.id).lastMs?.let { nowMs - it < DAY_MS } == true }.map { it.id }.toSet()
        // Lo de hoy se deja fuera, salvo que sin ello no alcance para la mezcla.
        val pool = songs.filter { it.id !in heardToday }.takeIf { it.size >= SIZE } ?: songs

        val scored = if (maxArtist == 0.0 && maxGenre == 0.0) {
            // Sin historial: Me gusta, añadidas hace poco y azar.
            val newest = pool.maxOf { it.dateAddedMs }.coerceAtLeast(1)
            pool.map { t -> t to ((if (t.id in liked) 1.0 else 0.0) + 0.6 * t.dateAddedMs / newest + rnd.nextDouble()) }
        } else pool.map { t ->
            val g = genresOf(t).maxOfOrNull { genreAff[it] ?: 0.0 } ?: 0.0
            val a = artistAff[t.artist.lowercase()] ?: 0.0
            var s = 0.45 * (if (maxGenre > 0) g / maxGenre else 0.0) + 0.35 * (if (maxArtist > 0) a / maxArtist else 0.0) +
                0.20 * novelty(t) + 0.15 * rnd.nextDouble()
            if (t.id in previous) s -= 0.3
            t to s
        }

        val perArtist = HashMap<String, Int>()
        val chosen = ArrayList<Track>()
        for ((t, _) in scored.sortedByDescending { it.second }) {
            val key = t.artist.lowercase()
            if ((perArtist[key] ?: 0) >= MAX_PER_ARTIST) continue
            perArtist[key] = (perArtist[key] ?: 0) + 1
            chosen += t
            if (chosen.size == SIZE) break
        }
        return chosen.shuffled(rnd)
    }

    /**
     * Nombre según los géneros de la mezcla: los que tienen al menos el 10 % de las canciones, los más presentes
     * primero y como mucho 3 ("Rock/Pop" con 18 de rock y 3 de pop). Si las canciones no traen género (pasa en
     * Android con muchos archivos), los artistas que más aparecen, con la misma regla. Si no, "Mezcla variada".
     */
    fun name(mix: List<Track>): String {
        val genres = HashMap<String, Int>()
        for (t in mix) genresOf(t).firstOrNull()?.let { genres.merge(it, 1, Int::plus) }
        if (genres.isNotEmpty()) return top(genres, 3) { e -> e.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } } }
        val artists = HashMap<String, Int>()
        for (t in mix) t.artist.takeIf { it.isNotBlank() && it != UNKNOWN_ARTIST }?.let { artists.merge(it, 1, Int::plus) }
        return if (artists.isEmpty()) "Mezcla variada" else top(artists, 2) { it }
    }

    /** Los que tienen al menos el 10 % del total, de mayor a menor, como mucho [max], unidos con "/". */
    private fun top(counts: Map<String, Int>, max: Int, show: (String) -> String): String {
        val total = counts.values.sum()
        return counts.entries.sortedByDescending { it.value }.filter { it.value * 10 >= total }.take(max).joinToString("/") { show(it.key) }
    }
}
