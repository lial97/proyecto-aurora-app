package app.aurora.data

import app.aurora.domain.MediaType
import app.aurora.domain.Track
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/**
 * Biblioteca guardada entre sesiones: al abrir la app se muestra al instante lo de la vez anterior mientras el
 * escaneo de siempre corre callado de fondo (antes había ~3 s con la lista vacía en el A35, con 525 pistas).
 *
 * Solo vale para las mismas carpetas: si cambiaron, o el archivo es de otro formato o está dañado, [decode]
 * devuelve `null` y se escanea como antes. No guarda las letras .lrc interpretadas (pesadas) ni las portadas de
 * ejemplo: llegan con el escaneo de fondo.
 */
object LibraryCache {
    private const val VERSION = 1

    fun encode(tracks: List<Track>, folders: List<String>): String = buildJsonObject {
        put("v", VERSION)
        put("carpetas", buildJsonArray { folders.forEach { add(JsonPrimitive(it)) } })
        put("pistas", buildJsonArray {
            tracks.forEach { t ->
                add(buildJsonObject {
                    put("id", t.id); put("t", t.title); put("a", t.artist); put("al", t.album)
                    put("d", t.durationSec)
                    if (t.mediaType != MediaType.AUDIO) put("m", t.mediaType.name)
                    t.bpm?.let { put("bpm", it) }
                    t.genre?.let { put("g", it) }
                    t.year?.let { put("y", it) }
                    t.releaseDate?.let { put("rd", it) }
                    if (t.dateAddedMs != 0L) put("add", t.dateAddedMs)
                    t.filePath?.let { put("f", it) }
                    t.coverUri?.let { put("c", it) }
                    t.format?.let { put("fmt", it) }
                    t.bitrateKbps?.let { put("kbps", it) }
                    t.sampleRateHz?.let { put("hz", it) }
                    t.bitsPerSample?.let { put("bits", it) }
                    t.plainLyrics?.let { put("pl", it) }
                })
            }
        })
    }.toString()

    /** Pistas guardadas, o `null` si no sirven para estas [folders] (o el texto no se puede leer). */
    fun decode(text: String, folders: List<String>): List<Track>? = runCatching {
        val root = Json.parseToJsonElement(text).jsonObject
        if (root["v"]?.jsonPrimitive?.intOrNull != VERSION) return null
        val saved = root["carpetas"]?.jsonArray?.map { it.jsonPrimitive.content } ?: return null
        if (saved != folders) return null
        (root["pistas"] as? JsonArray ?: return null).map { e ->
            val o = e.jsonObject
            Track(
                id = o.str("id")!!, title = o.str("t")!!, artist = o.str("a")!!, album = o.str("al")!!,
                durationSec = o["d"]!!.jsonPrimitive.int,
                mediaType = o.str("m")?.let { runCatching { MediaType.valueOf(it) }.getOrNull() } ?: MediaType.AUDIO,
                bpm = o.int("bpm"), genre = o.str("g"), year = o.int("y"), releaseDate = o.str("rd"),
                dateAddedMs = o["add"]?.jsonPrimitive?.long ?: 0L,
                filePath = o.str("f"), coverUri = o.str("c"), format = o.str("fmt"),
                bitrateKbps = o.int("kbps"), sampleRateHz = o.int("hz"), bitsPerSample = o.int("bits"),
                plainLyrics = o.str("pl"),
            )
        }
    }.getOrNull()

    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.int(k: String) = (this[k] as? JsonPrimitive)?.intOrNull
}
