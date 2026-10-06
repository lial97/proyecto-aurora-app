package app.aurora.domain

/** Título y artista deducidos del nombre de un archivo sin etiquetas. */
data class NameGuess(val title: String, val artist: String?)

// Etiquetas de calidad al final: "(320)", "[128 kbps]", "(320kbps)", "(1080P_60FPS)".
private val qualityTag = Regex("""\s*[(\[]\s*(\d{3,4}\s*(kbps|k)?|\d{3,4}p[^)\]]*|hd|hq)\s*[)\]]\s*$""", RegexOption.IGNORE_CASE)
private val trackNumber = Regex("""^\d{1,3}\s*[-.]\s+""")

/**
 * Deduce título y artista de nombres como "Abrázame Muy Fuerte - Juan Gabriel (320).mp3".
 * Se asume el orden "Título - Artista". Un nombre solo de números ("00000.mp3") no tiene artista
 * y se marca para corrección (ver [needsCorrection]).
 */
fun guessFromFileName(fileName: String): NameGuess {
    var base = fileName.substringBeforeLast('.', fileName).replace('_', ' ').replace(Regex("\\s+"), " ").trim()
    while (qualityTag.containsMatchIn(base)) base = base.replace(qualityTag, "").trim()
    base = base.replace(trackNumber, "").trim()
    val parts = base.split(" - ", limit = 2).map { it.trim() }
    return if (parts.size == 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) NameGuess(parts[0], parts[1])
    else NameGuess(base.ifEmpty { fileName }, null)
}

/** Pistas cuyo título parece un nombre de archivo sin sentido: candidatas a "corregir información". */
fun Track.needsCorrection(): Boolean =
    title.isBlank() || title.all { it.isDigit() || it == ' ' || it == '_' || it == '-' } ||
        artist.isBlank() || artist.equals(UNKNOWN_ARTIST, ignoreCase = true) || artist.equals("unknown", ignoreCase = true)

const val UNKNOWN_ARTIST = "Artista desconocido"
const val UNKNOWN_ALBUM = "Álbum desconocido"

/**
 * Audios que no son música: notas de voz y audios de chats (WhatsApp, Telegram), grabaciones y llamadas.
 * En Android MediaStore los indexa junto a las canciones; se dejan fuera de la biblioteca.
 */
fun isChatOrVoiceAudio(fileName: String, path: String?): Boolean {
    val name = fileName.lowercase()
    val p = path.orEmpty().lowercase().replace('\\', '/')
    if (Regex("""^(ptt|aud)-\d{8}-wa\d+""").containsMatchIn(name)) return true
    if (name.startsWith("ptt-") || name.startsWith("voice_note") || name.startsWith("call_rec") || name.startsWith("recording_")) return true
    val dirs = listOf("/whatsapp/", "com.whatsapp", "whatsapp voice notes", "whatsapp audio", "/telegram/telegram audio", "/recordings/", "/call/", "/callrecord", "/voice recorder/", "/sound_recorder", "/soundrecorder")
    return dirs.any { it in p }
}

/** Hay un artista real (no vacío ni "Artista desconocido"). */
val Track.hasKnownArtist: Boolean get() =
    artist.isNotBlank() && !artist.equals(UNKNOWN_ARTIST, ignoreCase = true) && !artist.equals("unknown", ignoreCase = true)

/** Título de un video: "Artista, título", o solo el título (o nombre del archivo) si no hay artista. */
fun Track.videoCaption(): String = if (hasKnownArtist) "$artist, $title" else title

/**
 * Si una carpeta puede sugerirse en Ajustes › Biblioteca: nunca las de chats (WhatsApp, Telegram),
 * capturas de pantalla, cámara (DCIM), GIF ni grabaciones; esas tienen audio o video que no es música.
 */
fun isSuggestibleFolder(path: String): Boolean {
    val p = path.lowercase().replace('\\', '/')
    val blocked = listOf(
        "whatsapp", "telegram", "signal", "screenshots", "capturas", "screen recordings", "screenrecord",
        "/dcim", "camera", "cámara", "gifs", "/gif", "recordings", "grabaciones", "voice recorder", "/call/", "callrec", "/android/",
    )
    return blocked.none { it in p }
}
