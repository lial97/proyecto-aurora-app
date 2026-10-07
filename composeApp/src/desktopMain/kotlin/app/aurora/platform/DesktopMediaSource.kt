package app.aurora.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import app.aurora.domain.LyricLine
import app.aurora.domain.MediaType
import app.aurora.domain.MetadataSource
import app.aurora.domain.Track
import app.aurora.domain.UNKNOWN_ALBUM
import app.aurora.domain.UNKNOWN_ARTIST
import app.aurora.domain.guessFromFileName
import app.aurora.domain.parseLrc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.awaitClose
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import kotlin.coroutines.coroutineContext

/** Escaneo de carpetas locales con etiquetas leídas por jaudiotagger. */
class DesktopMediaSource(
    private val thumbnails: VideoThumbnailer = VideoThumbnailer(java.io.File(cacheDir(), "miniaturas")),
) : MediaSource {
    init {
        // jaudiotagger escribe muchísimo en el registro. Se guarda la referencia: si no, Java descarta el
        // Logger y el ajuste se pierde.
        quietLogger.level = Level.OFF
    }

    override fun defaultFolders(): List<String> {
        val home = System.getProperty("user.home")
        val fromXdg = listOf("MUSIC", "VIDEOS").mapNotNull { xdgUserDir(it) }
        val guesses = listOf("Música", "Music", "Vídeos", "Videos").map { "$home/$it" }
        return (fromXdg + guesses).distinct().filter { File(it).isDirectory && File(it).absolutePath != home }
    }

    override val canPickFolder: Boolean = true

    override suspend fun pickFolder(): String? = pickFolder(null)

    /**
     * Selector nativo con FileKit: en Linux por xdg-desktop-portal (el de GNOME o KDE, según el escritorio) y en
     * Windows el del sistema. Si falla (sin portal, por ejemplo), zenity y por último JFileChooser.
     */
    override suspend fun pickFolder(initial: String?): String? {
        val native = runCatching {
            io.github.vinceglb.filekit.FileKit.openDirectoryPicker(
                directory = (initial ?: System.getProperty("user.home"))?.let { io.github.vinceglb.filekit.PlatformFile(File(it)) },
                dialogSettings = io.github.vinceglb.filekit.dialogs.FileKitDialogSettings(title = "Elige una carpeta con música o videos"),
            )
        }
        native.getOrNull()?.let { return it.file.absolutePath }
        // Se canceló en el selector nativo: no abrir otro.
        if (native.isSuccess) return null
        return withContext(Dispatchers.IO) { zenityPick(initial) ?: swingPick(initial) }
    }

    override suspend fun candidateFolders(): List<String> = defaultFolders()

    private val tempo = TempoDecoder()
    override val canDecodeForTempo: Boolean get() = tempo.available

    override suspend fun decodeForTempo(track: Track): app.aurora.domain.Pcm? = withContext(Dispatchers.IO) {
        val f = track.filePath?.let(::File)?.takeIf { it.isFile } ?: return@withContext null
        tempo.decode(f, track.durationSec)
    }

    /** Conteos ya hechos (carpeta y tipo → archivos), para no repetir la búsqueda al volver a mostrar las sugerencias. */
    private val suggestionCounts = java.util.concurrent.ConcurrentHashMap<Pair<String, MediaType>, Int>()

    /**
     * Carpetas habituales (Música, Descargas, Vídeos y discos montados) con su número de audios o videos.
     * Se cuentan todas a la vez con un tope de 2 s en total: si una es enorme, se muestra lo contado hasta ahí.
     */
    override suspend fun suggestFolders(type: MediaType, chosen: List<String>): List<FolderSuggestion> = withContext(Dispatchers.IO) {
        val home = System.getProperty("user.home")
        val user = System.getProperty("user.name").orEmpty()
        val xdg = listOf("MUSIC", "DOWNLOAD", "VIDEOS").mapNotNull { xdgUserDir(it) }
        val guesses = listOf("Música", "Music", "Descargas", "Downloads", "Vídeos", "Videos").map { "$home/$it" }
        fun children(dir: String) = File(dir).listFiles { f -> f.isDirectory && !f.isHidden }?.map { it.absolutePath }.orEmpty()
        val mounts = children("/run/media/$user") + children("/media/$user") + children("/media").filter { File(it).name != user }
        val chosenFiles = chosen.map { File(it).absoluteFile }
        val candidates = (xdg + guesses + mounts).map { File(it).absoluteFile }.distinctBy { it.canonicalPathOrSelf() }
            .filter { f ->
                f.isDirectory && f.absolutePath != home && app.aurora.domain.isSuggestibleFolder(f.absolutePath + "/") &&
                    // Ni las ya elegidas ni las que están dentro de una elegida.
                    chosenFiles.none { c -> f == c || f.startsWith(c) }
            }
        val deadline = System.nanoTime() + 2_000_000_000L
        val counted = kotlinx.coroutines.coroutineScope {
            candidates.map { f -> async(Dispatchers.IO) { f to countMedia(f, type, deadline) } }.map { it.await() }
        }
        counted.filter { it.second > 0 }.map { (f, n) -> FolderSuggestion(f.name, f.absolutePath, n) }
    }

    private fun File.canonicalPathOrSelf() = runCatching { canonicalPath }.getOrDefault(absolutePath)

    /** Archivos de [type] dentro de [dir] (con subcarpetas, sin las ocultas) hasta [deadline]. */
    private fun countMedia(dir: File, type: MediaType, deadline: Long): Int {
        val key = dir.absolutePath to type
        suggestionCounts[key]?.let { return it }
        var n = 0
        var complete = true
        for (f in dir.walkTopDown().onEnter { !it.name.startsWith(".") && it.name !in skippedDirs }) {
            if (System.nanoTime() > deadline) { complete = false; break }
            if (f.isFile && MediaType.fromFileName(f.name) == type) n++
        }
        if (complete) suggestionCounts[key] = n
        return n
    }

    override suspend fun scan(folders: List<String>, onProgress: (ScanProgress) -> Unit): List<Track> {
        val files = folders.asSequence()
            .map { File(it) }
            .filter { it.isDirectory }
            .flatMap { root ->
                root.walkTopDown()
                    .onEnter { dir -> dir == root || (!dir.name.startsWith(".") && dir.name !in skippedDirs) }
                    .filter { it.isFile && MediaType.fromFileName(it.name) != null }
            }
            .distinctBy { it.absolutePath }
            .toList()
        onProgress(ScanProgress(0, files.size))
        return files.mapIndexed { i, f ->
            coroutineContext.ensureActive()
            if (i % 8 == 0) onProgress(ScanProgress(i, files.size))
            readTrack(f)
        }.also { onProgress(ScanProgress(files.size, files.size)) }
    }

    override suspend fun loadCover(track: Track): ImageBitmap? = withContext(Dispatchers.IO) {
        val uri = track.coverUri ?: return@withContext null
        if (uri == VIDEO_THUMB) return@withContext loadPreviewFrames(track).firstOrNull()
        val bytes = runCatching {
            if (uri == EMBEDDED) AudioFileIO.read(File(track.filePath!!)).tag?.firstArtwork?.binaryData
            else File(uri).readBytes()
        }.getOrNull() ?: return@withContext null
        // Las imágenes intermedias se cierran ya: viven fuera de la memoria de Java y el recolector tarda en verlas.
        runCatching {
            Image.makeFromEncoded(bytes).use { src ->
                val img = downscale(src, MAX_COVER_PX)
                try { img.toComposeImageBitmap() } finally { if (img !== src) img.close() }
            }
        }.getOrNull()
    }

    override fun watch(folders: List<String>): kotlinx.coroutines.flow.Flow<Unit> = kotlinx.coroutines.flow.callbackFlow {
        val ws = java.nio.file.FileSystems.getDefault().newWatchService()
        val kinds = arrayOf(java.nio.file.StandardWatchEventKinds.ENTRY_CREATE, java.nio.file.StandardWatchEventKinds.ENTRY_DELETE, java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY)
        folders.map { File(it) }.filter { it.isDirectory }.forEach { root ->
            root.walkTopDown().onEnter { !it.name.startsWith(".") && it.name !in skippedDirs }.filter { it.isDirectory }
                .forEach { d -> runCatching { d.toPath().register(ws, *kinds) } }
        }
        val t = Thread({
            try {
                while (true) {
                    val key = ws.take()
                    val relevant = key.pollEvents().any { ev ->
                        val name = ev.context()?.toString().orEmpty()
                        MediaType.fromFileName(name) != null || !name.contains('.')
                    }
                    key.reset()
                    if (relevant) trySend(Unit)
                }
            } catch (_: Exception) {}
        }, "aurora-vigilar").apply { isDaemon = true; start() }
        awaitClose { runCatching { ws.close() }; t.interrupt() }
    }

    override suspend fun writeTags(track: Track, edit: app.aurora.domain.TrackEdit): Boolean = withContext(Dispatchers.IO) {
        val f = track.filePath?.let { File(it) } ?: return@withContext false
        runCatching {
            val audio = AudioFileIO.read(f)
            val tag = audio.tagOrCreateAndSetDefault
            edit.title?.let { tag.setField(FieldKey.TITLE, it) }
            edit.artist?.let { tag.setField(FieldKey.ARTIST, it) }
            edit.album?.let { tag.setField(FieldKey.ALBUM, it) }
            edit.year?.let { tag.setField(FieldKey.YEAR, it.toString()) }
            edit.genre?.let { tag.setField(FieldKey.GENRE, it) }
            audio.commit()
            true
        }.getOrDefault(false)
    }

    override suspend fun revealInFolder(track: Track): Boolean = withContext(Dispatchers.IO) {
        val f = track.filePath?.let { File(it) } ?: return@withContext false
        val os = System.getProperty("os.name").orEmpty().lowercase()
        val cmd = when {
            "win" in os -> listOf("explorer", "/select,", f.absolutePath)
            "mac" in os -> listOf("open", "-R", f.absolutePath)
            else -> listOf("xdg-open", f.parentFile.absolutePath)
        }
        runCatching { ProcessBuilder(cmd).start(); true }.getOrDefault(false)
    }

    override suspend fun saveLyricsFile(track: Track, lrc: String): Boolean = withContext(Dispatchers.IO) {
        val f = track.filePath?.let { File(it) } ?: return@withContext false
        runCatching { File(f.parentFile, f.nameWithoutExtension + ".lrc").writeText(lrc, Charsets.UTF_8); true }.getOrDefault(false)
    }

    override suspend fun loadPreviewFrames(track: Track): List<ImageBitmap> {
        if (track.mediaType != MediaType.VIDEO) return emptyList()
        val f = track.filePath?.let { File(it) }?.takeIf { it.isFile } ?: return emptyList()
        return thumbnails.frames(f, track.durationSec)
    }

    /** Las portadas incrustadas suelen ser de 1000-1500 px: se reducen para no llenar la memoria. */
    private fun downscale(img: Image, max: Int): Image {
        if (img.width <= max && img.height <= max) return img
        val k = max.toFloat() / maxOf(img.width, img.height)
        val w = (img.width * k).toInt()
        val h = (img.height * k).toInt()
        return Surface.makeRasterN32Premul(w, h).use { surface ->
            surface.canvas.drawImageRect(img, Rect.makeWH(img.width.toFloat(), img.height.toFloat()), Rect.makeWH(w.toFloat(), h.toFloat()),
                SamplingMode.LINEAR, null, true)
            surface.makeImageSnapshot()
        }
    }

    private fun readTrack(f: File): Track {
        val type = MediaType.fromFileName(f.name)!!
        val guess = guessFromFileName(f.name)
        val audio = runCatching { AudioFileIO.read(f) }.getOrNull()
        val tag: Tag? = audio?.tag
        fun field(k: FieldKey) = runCatching { tag?.getFirst(k) }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }

        val title = field(FieldKey.TITLE)
        val artist = field(FieldKey.ARTIST) ?: field(FieldKey.ALBUM_ARTIST)
        val hasEmbeddedCover = runCatching { tag?.firstArtwork != null }.getOrDefault(false)
        val header = audio?.audioHeader
        val lrc = File(f.parentFile, f.nameWithoutExtension + ".lrc").takeIf { it.isFile }
        val lyrics: List<LyricLine> = lrc?.let { runCatching { parseLrc(it.readText()) }.getOrNull() } ?: emptyList()
        val tagLyrics = field(FieldKey.LYRICS)

        return Track(
            id = f.absolutePath,
            title = title ?: guess.title,
            artist = artist ?: guess.artist ?: UNKNOWN_ARTIST,
            album = field(FieldKey.ALBUM) ?: UNKNOWN_ALBUM,
            durationSec = header?.trackLength?.takeIf { it > 0 } ?: Mp4.durationSec(f) ?: 0,
            mediaType = type,
            bpm = field(FieldKey.BPM)?.toDoubleOrNull()?.toInt()?.takeIf { it in 30..300 },
            key = field(FieldKey.KEY),
            genre = field(FieldKey.GENRE)?.let(::cleanGenre),
            year = field(FieldKey.YEAR)?.let { Regex("""\d{4}""").find(it)?.value?.toInt() },
            releaseDate = field(FieldKey.YEAR),
            dateAddedMs = f.lastModified(),
            filePath = f.absolutePath,
            fileSizeBytes = f.length(),
            coverUri = when {
                type == MediaType.VIDEO -> VIDEO_THUMB
                hasEmbeddedCover -> EMBEDDED
                else -> folderImage(f.parentFile)?.absolutePath
            },
            composers = field(FieldKey.COMPOSER),
            lyrics = if (lyrics.isEmpty() && tagLyrics != null && tagLyrics.contains('[')) parseLrc(tagLyrics) else lyrics,
            plainLyrics = tagLyrics?.takeIf { !it.contains('[') },
            format = header?.format?.substringBefore(' ')?.uppercase() ?: f.extension.uppercase(),
            bitrateKbps = header?.bitRateAsNumber?.toInt()?.takeIf { it > 0 },
            sampleRateHz = header?.sampleRateAsNumber?.takeIf { it > 0 },
            bitsPerSample = header?.bitsPerSample?.takeIf { it > 0 },
            trackNumber = field(FieldKey.TRACK)?.substringBefore('/')?.toIntOrNull(),
            trackTotal = field(FieldKey.TRACK_TOTAL)?.toIntOrNull() ?: field(FieldKey.TRACK)?.substringAfter('/', "")?.toIntOrNull(),
            metadataSource = if (title != null) MetadataSource.FILE_TAGS else MetadataSource.USER,
        )
    }

    private fun folderImage(dir: File?): File? {
        dir ?: return null
        val names = listOf("cover", "folder", "front", "album", "portada")
        return dir.listFiles()?.firstOrNull { f ->
            f.isFile && f.nameWithoutExtension.lowercase() in names && f.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp")
        }
    }

    /** ID3v1 guarda géneros como "(17)"; se dejan solo los nombres legibles. */
    private fun cleanGenre(g: String): String? = g.replace(Regex("""^\(\d+\)"""), "").trim().takeIf { it.isNotEmpty() && !it.all(Char::isDigit) }

    private fun xdgUserDir(kind: String): String? = runCatching {
        val p = ProcessBuilder("xdg-user-dir", kind).redirectErrorStream(true).start()
        p.inputStream.bufferedReader().readText().trim().takeIf { p.waitFor() == 0 && it.isNotEmpty() }
    }.getOrNull()

    /** Selector nativo de GNOME/GTK (zenity) si está instalado, abierto en [initial] si se da. */
    private fun zenityPick(initial: String? = null): String? = runCatching {
        if (!System.getProperty("os.name").orEmpty().lowercase().contains("linux")) return null
        val args = mutableListOf("zenity", "--file-selection", "--directory", "--title=Elige una carpeta con música o videos")
        if (initial != null) args += "--filename=${initial.trimEnd('/')}/"
        val p = ProcessBuilder(args).start()
        val out = p.inputStream.bufferedReader().readText().trim()
        if (p.waitFor() == 0 && out.isNotEmpty()) out else null
    }.getOrNull()

    private fun swingPick(initial: String? = null): String? {
        var result: String? = null
        SwingUtilities.invokeAndWait {
            val chooser = JFileChooser(initial ?: System.getProperty("user.home")).apply {
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                dialogTitle = "Elige una carpeta con música o videos"
            }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) result = chooser.selectedFile.absolutePath
        }
        return result
    }

    companion object {
        const val EMBEDDED = "embedded"
        private val quietLogger: Logger = Logger.getLogger("org.jaudiotagger")
        /** Miniatura sacada del propio video. */
        const val VIDEO_THUMB = "video-thumb"
        const val MAX_COVER_PX = 512
        private val skippedDirs = setOf("node_modules", "build", "__pycache__", "target")
    }
}
