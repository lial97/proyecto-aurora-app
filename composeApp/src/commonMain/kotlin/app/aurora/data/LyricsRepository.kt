package app.aurora.data

import app.aurora.domain.LyricLine
import app.aurora.domain.MediaType
import app.aurora.domain.Track
import app.aurora.domain.UNKNOWN_ARTIST
import app.aurora.domain.parseLrc
import app.aurora.platform.HttpClient
import app.aurora.platform.KeyValueStore
import app.aurora.platform.TextCache
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock

enum class LyricsSource(val label: String) {
    FILE("del archivo"), LRCLIB("de LRCLIB"), SAMPLE("de ejemplo"),
}

/** Estado de la letra de la pista actual. */
sealed interface LyricsState {
    data object None : LyricsState
    data object Loading : LyricsState
    /** [lines] vacío = solo hay letra simple ([plain]). [lrc] es el texto LRC original (para guardarlo). */
    data class Found(val lines: List<LyricLine>, val plain: String?, val source: LyricsSource, val lrc: String? = null) : LyricsState {
        val synced: Boolean get() = lines.isNotEmpty()
    }
    data object Instrumental : LyricsState
    data class NotFound(val reason: String) : LyricsState
    data object Disabled : LyricsState
}

/** Un resultado de LRCLIB. */
data class LrcCandidate(
    val track: String, val artist: String, val durationSec: Double?, val instrumental: Boolean,
    val synced: String?, val plain: String?,
)

/**
 * Letras sincronizadas: archivo .lrc o etiquetas → caché → LRCLIB (https://lrclib.net, gratis y abierto).
 * Solo se envían título, artista, álbum y duración.
 */
class LyricsRepository(
    private val http: HttpClient,
    private val cache: TextCache,
    private val store: KeyValueStore,
) {
    private val _auto = MutableStateFlow(store.get(KEY_AUTO) != "no")
    /** Descargar letras automáticamente (se puede desactivar en Ajustes). */
    val autoDownload: StateFlow<Boolean> = _auto

    fun setAutoDownload(on: Boolean) {
        _auto.value = on
        store.put(KEY_AUTO, if (on) "si" else "no")
    }

    /** @param force ignora un "no encontrada" guardado y vuelve a preguntar a LRCLIB. */
    /** "Preferir archivo .lrc": si está apagado, se busca primero en LRCLIB aunque la canción traiga letra. */
    var preferFile: Boolean = true

    fun cacheStats(): Pair<Int, Long> = cache.stats()
    fun clearCache() = cache.clear()

    suspend fun load(track: Track, force: Boolean = false): LyricsState {
        if (track.lyrics.isNotEmpty() && (preferFile || !_auto.value)) {
            return LyricsState.Found(track.lyrics, track.plainLyrics, if (track.filePath == null) LyricsSource.SAMPLE else LyricsSource.FILE)
        }
        val key = cacheKey(track)
        if (!force) cache.get(key)?.let { decodeCache(it)?.let { s -> return s } }
        if (!_auto.value && !force) {
            return track.plainLyrics?.let { LyricsState.Found(emptyList(), it, LyricsSource.FILE) } ?: LyricsState.Disabled
        }
        if (track.artist == UNKNOWN_ARTIST || track.artist.isBlank()) {
            return track.plainLyrics?.let { LyricsState.Found(emptyList(), it, LyricsSource.FILE) }
                ?: LyricsState.NotFound(if (track.mediaType == MediaType.VIDEO) "Este video no tiene artista para buscar su letra" else "Falta el artista para buscar la letra")
        }
        val fileLyrics = track.lyrics.takeIf { it.isNotEmpty() }?.let { LyricsState.Found(it, track.plainLyrics, LyricsSource.FILE) }
        val found = fetch(track) ?: return fileLyrics ?: LyricsState.NotFound("No hay conexión con LRCLIB. Se intentará de nuevo más tarde.")
        if (fileLyrics != null && found.synced.isNullOrBlank()) return fileLyrics
        val state = when {
            found.instrumental -> LyricsState.Instrumental
            !found.synced.isNullOrBlank() -> LyricsState.Found(parseLrc(found.synced), found.plain, LyricsSource.LRCLIB, found.synced)
            !found.plain.isNullOrBlank() -> LyricsState.Found(emptyList(), found.plain, LyricsSource.LRCLIB)
            track.plainLyrics != null -> LyricsState.Found(emptyList(), track.plainLyrics, LyricsSource.FILE)
            else -> LyricsState.NotFound("LRCLIB no tiene la letra de esta canción")
        }
        cache.put(key, encodeCache(found, state))
        return state
    }

    /** `null` = error de red; un candidato vacío = no existe. */
    private suspend fun fetch(t: Track): LrcCandidate? {
        val title = cleanTitle(t.title)
        val artist = t.artist.substringBefore(" & ").substringBefore(", ").substringBefore(" feat").trim()
        // 1) Coincidencia exacta con duración.
        val exact = request("$BASE/get?" + query("track_name" to t.title, "artist_name" to t.artist, "album_name" to t.album,
            "duration" to t.durationSec.takeIf { it > 0 }?.toString()))
        when (exact?.code) {
            200 -> parseOne(exact.body)?.let { if (!it.synced.isNullOrBlank() || it.instrumental) return it }
            null -> return null
        }
        // 2) Búsqueda: se elige la versión sincronizada con la duración más parecida.
        val searches = listOf(
            query("track_name" to title, "artist_name" to artist),
            query("q" to "$title $artist"),
        )
        var anyResponse = exact != null
        var bestPlain: LrcCandidate? = exact?.takeIf { it.code == 200 }?.let { parseOne(it.body) }
        for (q in searches) {
            val r = request("$BASE/search?$q") ?: continue
            anyResponse = true
            if (r.code != 200) continue
            val list = parseList(r.body)
            pickBest(list, t.durationSec, title)?.let { best ->
                if (!best.synced.isNullOrBlank()) return best
                if (bestPlain == null) bestPlain = best
            }
        }
        return bestPlain ?: if (anyResponse) LrcCandidate("", "", null, false, null, null) else null
    }

    /** Reintenta cuando LRCLIB está ocupado (503) o hay límite de peticiones (429). */
    private suspend fun request(url: String): app.aurora.platform.HttpResponse? {
        repeat(3) { attempt ->
            val r = http.get(url, mapOf("User-Agent" to USER_AGENT, "Accept" to "application/json"))
            if (r != null && r.code != 503 && r.code != 429) return r
            delay(800L * (attempt + 1) * (attempt + 1))
        }
        return null
    }

    private fun encodeCache(c: LrcCandidate, s: LyricsState): String = when (s) {
        is LyricsState.Found -> if (s.synced) "synced\n" + (c.synced ?: "") else "plain\n" + (s.plain ?: "")
        LyricsState.Instrumental -> "instrumental\n"
        else -> "none\n" + Clock.System.now().toEpochMilliseconds()
    }

    private fun decodeCache(raw: String): LyricsState? {
        val kind = raw.substringBefore('\n')
        val body = raw.substringAfter('\n', "")
        return when (kind) {
            "synced" -> LyricsState.Found(parseLrc(body), null, LyricsSource.LRCLIB, body)
            "plain" -> LyricsState.Found(emptyList(), body, LyricsSource.LRCLIB)
            "instrumental" -> LyricsState.Instrumental
            // "No encontrada" se vuelve a intentar a los 3 días.
            "none" -> body.toLongOrNull()?.takeIf { Clock.System.now().toEpochMilliseconds() - it < 3 * 86_400_000L }
                ?.let { LyricsState.NotFound("LRCLIB no tiene la letra de esta canción") }
            else -> null
        }
    }

    companion object {
        const val KEY_AUTO = "letras_auto"
        private const val BASE = "https://lrclib.net/api"
        private const val USER_AGENT = "Aurora/0.3 (reproductor de musica de codigo abierto)"
        private val json = Json { ignoreUnknownKeys = true }

        fun cacheKey(t: Track) = "lrc|${t.artist.lowercase().trim()}|${t.title.lowercase().trim()}|${t.durationSec / 3}"

        /** Quita adornos que estorban la búsqueda: "(320)", "(En Vivo…)", "- Remastered 2011", "feat. X"… */
        fun cleanTitle(title: String): String = title
            .replace(Regex("""\s*[(\[][^)\]]*(remaster|en vivo|live|versión|version|versão|edit|mix|feat|ft\.|official|oficial|audio|video|lyric|letra|\d{3}\s*kbps|\b\d{3}\b)[^)\]]*[)\]]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+-\s+.*(remaster|live|en vivo|version|versión).*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+(feat\.?|ft\.)\s+.*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+"), " ").trim().ifEmpty { title }

        fun query(vararg p: Pair<String, String?>) = p.filter { it.second != null }.joinToString("&") { (k, v) -> "$k=" + urlEncode(v!!) }

        private fun urlEncode(s: String) = buildString {
            s.encodeToByteArray().forEach { b ->
                val c = b.toInt() and 0xFF
                if (c.toChar().isLetterOrDigit() && c < 128 || c.toChar() in "-_.~") append(c.toChar()) else append("%" + c.toString(16).uppercase().padStart(2, '0'))
            }
        }

        private fun JsonObject.str(k: String) = get(k)?.jsonPrimitive?.contentOrNull

        private fun candidate(o: JsonObject) = LrcCandidate(
            track = o.str("trackName").orEmpty(), artist = o.str("artistName").orEmpty(),
            durationSec = o["duration"]?.jsonPrimitive?.doubleOrNull,
            instrumental = o["instrumental"]?.jsonPrimitive?.booleanOrNull == true,
            synced = o.str("syncedLyrics"), plain = o.str("plainLyrics"),
        )

        fun parseOne(body: String): LrcCandidate? = runCatching { candidate(json.parseToJsonElement(body) as JsonObject) }.getOrNull()

        fun parseList(body: String): List<LrcCandidate> =
            runCatching { (json.parseToJsonElement(body) as JsonArray).map { candidate(it as JsonObject) } }.getOrDefault(emptyList())

        /**
         * Mejor resultado: con letra sincronizada, duración a menos de 8 s de la del archivo y título parecido.
         * Si ninguno está sincronizado, el más cercano con letra simple.
         */
        fun pickBest(list: List<LrcCandidate>, durationSec: Int, title: String): LrcCandidate? {
            val norm = { s: String -> s.lowercase().filter { it.isLetterOrDigit() } }
            val wanted = norm(cleanTitle(title))
            val similar = list.filter { c ->
                val n = norm(cleanTitle(c.track))
                n.isNotEmpty() && (n == wanted || n.contains(wanted) || wanted.contains(n))
            }.ifEmpty { list }
            fun diff(c: LrcCandidate) = if (durationSec > 0 && c.durationSec != null) kotlin.math.abs(c.durationSec - durationSec) else 0.0
            val near = similar.filter { durationSec <= 0 || it.durationSec == null || diff(it) <= 8.0 }
            return near.filter { !it.synced.isNullOrBlank() }.minByOrNull(::diff)
                ?: near.filter { !it.plain.isNullOrBlank() }.minByOrNull(::diff)
        }

    }
}
