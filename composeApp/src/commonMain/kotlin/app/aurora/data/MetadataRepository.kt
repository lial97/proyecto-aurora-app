package app.aurora.data

import app.aurora.platform.HttpClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.time.Clock

/** Un resultado de MusicBrainz para "Corregir datos". */
data class MetadataCandidate(
    val recordingId: String,
    val title: String,
    val artist: String,
    val album: String?,
    /** Disco elegido (para la portada de Cover Art Archive en la fase siguiente). */
    val releaseId: String?,
    val year: Int?,
    val genre: String?,
    val durationSec: Int?,
    /** Coincidencia de MusicBrainz, 0–100. */
    val score: Int,
)

/** Resultado de una búsqueda: `null` en [candidates] = sin conexión o error del servicio. */
data class MetadataSearch(val candidates: List<MetadataCandidate>?)

/**
 * Corrección inteligente de datos (F5) con MusicBrainz (https://musicbrainz.org, gratis y abierto).
 * Solo se envían el título y el artista escritos por el usuario. MusicBrainz pide un User-Agent propio y
 * como mucho una petición por segundo.
 */
class MetadataRepository(private val http: HttpClient) {
    private val lock = Mutex()
    private var lastRequestMs = 0L

    suspend fun search(title: String, artist: String?, durationSec: Int = 0): MetadataSearch {
        val t = LyricsRepository.cleanTitle(title.trim())
        if (t.isBlank()) return MetadataSearch(emptyList())
        val q = buildString {
            append("recording:\"").append(escape(t)).append('"')
            artist?.trim()?.takeIf { it.isNotEmpty() }?.let { append(" AND artist:\"").append(escape(it)).append('"') }
        }
        val r = request("$BASE/recording?" + LyricsRepository.query("query" to q, "fmt" to "json", "limit" to "25")) ?: return MetadataSearch(null)
        if (r.code != 200) return MetadataSearch(if (r.code in 400..499) emptyList() else null)
        return MetadataSearch(rank(parse(r.body), durationSec))
    }

    /**
     * Portada delantera de un disco en Cover Art Archive (https://coverartarchive.org): 500 px, o 250 px
     * para las miniaturas. `null` si el disco no tiene portada o no hay conexión.
     */
    suspend fun cover(releaseId: String, small: Boolean = false): ByteArray? =
        http.getBytes("https://coverartarchive.org/release/$releaseId/front-${if (small) 250 else 500}", mapOf("User-Agent" to USER_AGENT))

        /** Espera lo necesario para no pasar de 1 petición por segundo; reintenta si el servicio está ocupado. */
    private suspend fun request(url: String): app.aurora.platform.HttpResponse? = lock.withLock {
        repeat(3) { attempt ->
            val wait = lastRequestMs + MIN_GAP_MS - Clock.System.now().toEpochMilliseconds()
            if (wait > 0) delay(wait)
            lastRequestMs = Clock.System.now().toEpochMilliseconds()
            val r = http.get(url, mapOf("User-Agent" to USER_AGENT, "Accept" to "application/json"))
            if (r != null && r.code != 503) return@withLock r
            delay(1500L * (attempt + 1))
        }
        null
    }

    companion object {
        private const val BASE = "https://musicbrainz.org/ws/2"
        private const val USER_AGENT = "Aurora/0.3 ( https://github.com/lial97/proyecto-aurora-app )"
        private const val MIN_GAP_MS = 1100L
        private val json = Json { ignoreUnknownKeys = true }

        /** Comillas y barras invertidas tienen significado en las búsquedas de MusicBrainz (Lucene). */
        private fun escape(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")

        private fun JsonObject.str(k: String) = get(k)?.jsonPrimitive?.contentOrNull

        fun parse(body: String): List<MetadataCandidate> = runCatching {
            val root = json.parseToJsonElement(body) as JsonObject
            (root["recordings"] as? JsonArray).orEmpty().mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                val artist = (o["artist-credit"] as? JsonArray).orEmpty().joinToString("") { c ->
                    val co = c as JsonObject
                    (co.str("name") ?: (co["artist"] as? JsonObject)?.str("name").orEmpty()) + co.str("joinphrase").orEmpty()
                }.trim()
                val release = pickRelease((o["releases"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject })
                val genre = (o["tags"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
                    .maxByOrNull { it["count"]?.jsonPrimitive?.intOrNull ?: 0 }?.str("name")?.replaceFirstChar { it.uppercase() }
                MetadataCandidate(
                    recordingId = o.str("id") ?: return@mapNotNull null,
                    title = o.str("title") ?: return@mapNotNull null,
                    artist = artist.ifEmpty { return@mapNotNull null },
                    album = release?.str("title"),
                    releaseId = release?.str("id"),
                    year = release?.str("date")?.take(4)?.toIntOrNull() ?: o.str("first-release-date")?.take(4)?.toIntOrNull(),
                    genre = genre,
                    durationSec = o["length"]?.jsonPrimitive?.longOrNull?.let { (it / 1000).toInt() },
                    score = o["score"]?.jsonPrimitive?.intOrNull ?: 0,
                )
            }
        }.getOrDefault(emptyList())

        /** El disco más "original": álbum oficial (no recopilatorio) y el más antiguo. */
        private fun pickRelease(releases: List<JsonObject>): JsonObject? {
            fun group(r: JsonObject) = r["release-group"] as? JsonObject
            fun rank(r: JsonObject): Int {
                val type = group(r)?.str("primary-type")
                val secondary = (group(r)?.get("secondary-types") as? JsonArray).orEmpty().map { it.jsonPrimitive.contentOrNull }
                var p = 0
                if (r.str("status") == "Official") p += 4
                if (type == "Album") p += 3 else if (type == "Single" || type == "EP") p += 2
                if ("Compilation" in secondary || "Live" in secondary) p -= 3
                return p
            }
            return releases.sortedWith(compareByDescending<JsonObject> { rank(it) }.thenBy { it.str("date")?.ifEmpty { null } ?: "9999" }).firstOrNull()
        }

        /**
         * Orden final: coincidencia de MusicBrainz, con ventaja si la duración se parece a la del archivo
         * (±5 s) y castigo si es muy distinta. Se quitan repetidos (misma canción, artista y disco).
         */
        fun rank(list: List<MetadataCandidate>, durationSec: Int): List<MetadataCandidate> {
            fun points(c: MetadataCandidate): Int {
                val d = if (durationSec > 0 && c.durationSec != null) kotlin.math.abs(c.durationSec - durationSec) else null
                return c.score + when {
                    d == null -> 0
                    d <= 5 -> 15
                    d <= 15 -> 5
                    d > 60 -> -25
                    else -> -5
                } + (if (c.album != null) 2 else 0)
            }
            return list.sortedByDescending(::points)
                .distinctBy { Triple(it.title.lowercase(), it.artist.lowercase(), it.album?.lowercase()) }
                .take(8)
        }
    }
}
