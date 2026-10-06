package app.aurora.platform

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.Size
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.aurora.domain.MediaType
import app.aurora.domain.Track
import app.aurora.domain.UNKNOWN_ALBUM
import app.aurora.domain.UNKNOWN_ARTIST
import app.aurora.domain.guessFromFileName
import app.aurora.domain.isChatOrVoiceAudio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.datastore.preferences.core.edit
import java.io.File

/** Se llama desde MainActivity antes de mostrar la interfaz. */
object AndroidPlatform {
    @SuppressLint("StaticFieldLeak")
    internal lateinit var context: Context
    fun init(context: Context) { this.context = context.applicationContext }

    /** Un solo DataStore por archivo en todo el proceso (la actividad puede recrearse). */
    internal val store: KeyValueStore by lazy { DataStoreStore(context) }

    /** Lo conecta MainActivity: abre el selector de carpetas del sistema y devuelve la carpeta elegida. */
    var folderPicker: (suspend () -> Uri?)? = null
}

actual fun createPlatformServices(): PlatformServices = PlatformServices(
    name = "Android",
    isDesktop = false,
    store = AndroidPlatform.store,
    media = MediaStoreSource(AndroidPlatform.context),
    mediaEngine = app.aurora.player.Media3Engine(AndroidPlatform.context),
    http = HttpClient { url, headers ->
        withContext(Dispatchers.IO) {
            runCatching {
                val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                c.connectTimeout = 8000; c.readTimeout = 12000
                headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
                val code = c.responseCode
                val body = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
                HttpResponse(code, body)
            }.getOrNull()
        }
    },
    textCache = object : TextCache {
        // En filesDir, no en cacheDir: Android borra la caché cuando falta espacio y las letras se volvían a descargar.
        private val dir = File(AndroidPlatform.context.filesDir, "letras").also { d ->
            val old = File(AndroidPlatform.context.cacheDir, "letras")
            if (old.isDirectory) runCatching { d.mkdirs(); old.listFiles()?.forEach { f -> f.copyTo(File(d, f.name), overwrite = false); f.delete() }; old.delete() }
        }
        private fun f(key: String) = File(dir, key.hashCode().toUInt().toString(16) + ".txt")
        override fun get(key: String) = f(key).takeIf { it.isFile }?.readText()
        override fun put(key: String, value: String) { runCatching { dir.mkdirs(); f(key).writeText(value) } }
        override fun stats() = (dir.listFiles()?.size ?: 0) to (dir.listFiles()?.sumOf { it.length() } ?: 0L)
        override fun clear() { dir.listFiles()?.forEach { it.delete() } }
    },
)

/**
 * Ajustes guardados con DataStore. Se leen una vez al abrir (en memoria para leer sin esperar)
 * y cada cambio se escribe en orden en segundo plano. Migra lo que había en SharedPreferences.
 */
class DataStoreStore(context: Context) : KeyValueStore {
    private val ds = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
        migrations = listOf(androidx.datastore.preferences.SharedPreferencesMigration(context, "aurora")),
        produceFile = { java.io.File(context.filesDir, "datastore/aurora.preferences_pb") },
    )
    private val cache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val writes = kotlinx.coroutines.channels.Channel<Pair<String, String?>>(kotlinx.coroutines.channels.Channel.UNLIMITED)

    init {
        kotlinx.coroutines.runBlocking {
            ds.data.first().asMap().forEach { (k, v) -> cache[k.name] = v.toString() }
        }
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO).launch {
            for ((k, v) in writes) {
                val key = androidx.datastore.preferences.core.stringPreferencesKey(k)
                runCatching { ds.edit { if (v == null) it.remove(key) else it[key] = v } }
            }
        }
    }

    override fun get(key: String): String? = cache[key]
    override fun put(key: String, value: String?) {
        if (value == null) cache.remove(key) else cache[key] = value
        writes.trySend(key to value)
    }
}

/**
 * Biblioteca en Android: solo las carpetas que el usuario elige con el selector del sistema
 * (ACTION_OPEN_DOCUMENT_TREE, permiso guardado con takePersistableUriPermission). No se recorre
 * la galería ni el resto del almacenamiento.
 *
 * Los archivos se listan con DocumentsContract (con subcarpetas). Los datos (título, artista, portada…)
 * se toman del índice de Android si el archivo está en él; si no, se leen del propio archivo.
 */
class MediaStoreSource(private val context: Context) : MediaSource {
    override fun defaultFolders(): List<String> = emptyList()

    override val canPickFolder: Boolean = true

    override suspend fun pickFolder(): String? {
        val uri = AndroidPlatform.folderPicker?.invoke() ?: return null
        // Lectura y escritura (para guardar las letras .lrc junto a las canciones); si no se puede, solo lectura.
        val rw = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(uri, rw) }
            .onFailure { runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
        return uri.toString()
    }

    override fun foldersWithoutWrite(folders: List<String>): List<String> {
        val writable = context.contentResolver.persistedUriPermissions.filter { it.isWritePermission }.map { it.uri.toString() }.toSet()
        return folders.filter { it.startsWith("content://") && it !in writable }
    }

    /** Guarda "<canción>.lrc" en la misma carpeta, con el permiso de la carpeta elegida (reemplaza el que haya). */
    override suspend fun saveLyricsFile(track: Track, lrc: String): Boolean = withContext(Dispatchers.IO) {
        val path = track.filePath ?: return@withContext false
        val docId = docIdOf(path) ?: return@withContext false
        val parentId = docId.substringBeforeLast('/', "").ifEmpty { return@withContext false }
        val name = path.substringAfterLast('/').substringBeforeLast('.') + ".lrc"
        val tree = context.contentResolver.persistedUriPermissions.filter { it.isWritePermission }.map { it.uri }.firstOrNull { t ->
            val root = runCatching { DocumentsContract.getTreeDocumentId(t) }.getOrNull() ?: return@firstOrNull false
            parentId == root || parentId.startsWith(root.trimEnd('/') + "/") || (root.endsWith(":") && parentId.startsWith(root))
        } ?: return@withContext false
        runCatching {
            val resolver = context.contentResolver
            val existing = DocumentsContract.buildDocumentUriUsingTree(tree, "$parentId/$name")
            val target = if (runCatching { resolver.query(existing, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)?.use { it.moveToFirst() } }.getOrNull() == true) existing
            // "application/octet-stream" para que el sistema no le agregue ".txt" al nombre.
            else DocumentsContract.createDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, parentId), "application/octet-stream", name) ?: return@runCatching false
            resolver.openOutputStream(target, "wt")?.use { it.write(lrc.encodeToByteArray()) } ?: return@runCatching false
            true
        }.getOrDefault(false)
    }

    /** "/storage/emulated/0/Music/a.mp3" → "primary:Music/a.mp3"; "/storage/1234-ABCD/x" → "1234-ABCD:x". */
    private fun docIdOf(path: String): String? = when {
        path.startsWith("/storage/emulated/0/") -> "primary:" + path.removePrefix("/storage/emulated/0/")
        Regex("^/storage/([0-9A-Fa-f]{4}-[0-9A-Fa-f]{4})/(.*)$").matchEntire(path) != null ->
            Regex("^/storage/([0-9A-Fa-f]{4}-[0-9A-Fa-f]{4})/(.*)$").matchEntire(path)!!.destructured.let { (v, rel) -> "$v:$rel" }
        else -> null
    }

    override suspend fun candidateFolders(): List<String> = emptyList()

    override fun isUsableFolder(folder: String) = folder.startsWith("content://")

    override fun describeFolder(folder: String): FolderLabel {
        val docId = runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(folder)) }.getOrNull() ?: return super.describeFolder(folder)
        val volume = docId.substringBefore(':')
        val rel = docId.substringAfter(':', "").trim('/')
        val root = if (volume == "primary") "Almacenamiento interno" else if (volume == "home") "Documentos" else "Tarjeta SD"
        return FolderLabel(rel.substringAfterLast('/').ifEmpty { root }, if (rel.isEmpty()) root else "$root/$rel")
    }

    override suspend fun scan(folders: List<String>, onProgress: (ScanProgress) -> Unit): List<Track> = withContext(Dispatchers.IO) {
        val files = folders.filter { it.startsWith("content://") }.flatMap { listTree(Uri.parse(it)) }.distinctBy { it.uri }
        onProgress(ScanProgress(0, files.size))
        // Índice de Android (solo para completar datos de estos archivos).
        val index by lazy {
            runCatching { (query(MediaType.AUDIO) + query(MediaType.VIDEO)).filter { it.filePath != null }.associateBy { it.filePath!! } }.getOrDefault(emptyMap())
        }
        files.mapIndexedNotNull { i, f ->
            if (i % 25 == 0) onProgress(ScanProgress(i, files.size))
            val known = f.path?.let { index[it] }
            val track = when {
                known != null && known.mediaType == f.type -> known
                else -> runCatching { readFile(f) }.getOrNull()
            }
            // La letra .lrc que esté junto a la canción (mismo nombre).
            val lrc = f.lrc?.let { u -> runCatching { context.contentResolver.openInputStream(u)?.use { it.readBytes().decodeToString() } }.getOrNull() }
            if (track != null && lrc != null) track.copy(lyrics = runCatching { app.aurora.domain.parseLrc(lrc) }.getOrDefault(emptyList())) else track
        }.also { onProgress(ScanProgress(files.size, files.size)) }
    }

    /** Archivo multimedia encontrado al recorrer una carpeta elegida. */
    private class DocFile(val uri: Uri, val name: String, val type: MediaType, val path: String?, val modified: Long, var lrc: Uri? = null) {
        var parentKey: String = ""
    }

    /** Recorre la carpeta y sus subcarpetas con DocumentsContract (más rápido que DocumentFile). */
    private fun listTree(tree: Uri): List<DocFile> {
        val out = mutableListOf<DocFile>()
        // Letras .lrc encontradas: "carpeta|nombre sin extensión" → documento.
        val lrcs = HashMap<String, Uri>()
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: return out
        val pending = ArrayDeque(listOf(rootId))
        val cols = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        while (pending.isNotEmpty()) {
            val parent = pending.removeFirst()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
            runCatching {
                context.contentResolver.query(children, cols, null, null, null)?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getString(0) ?: continue
                        val name = c.getString(1) ?: continue
                        val mime = c.getString(2).orEmpty()
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (!name.startsWith(".")) pending.addLast(id)
                            continue
                        }
                        if (name.endsWith(".lrc", ignoreCase = true)) {
                            lrcs["$parent|${name.dropLast(4).lowercase()}"] = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                            continue
                        }
                        val type = MediaType.fromFileName(name)
                            ?: if (mime.startsWith("audio/")) MediaType.AUDIO else if (mime.startsWith("video/")) MediaType.VIDEO else null
                        if (type == null) continue
                        val path = pathOf(id)
                        if (type == MediaType.AUDIO && isChatOrVoiceAudio(name, path)) continue
                        out += DocFile(DocumentsContract.buildDocumentUriUsingTree(tree, id), name, type, path, c.getLong(3)).also { it.parentKey = "$parent|${name.substringBeforeLast('.').lowercase()}" }
                    }
                }
            }
        }
        out.forEach { f -> f.lrc = lrcs[f.parentKey] }
        return out
    }

    /** "primary:Music/a.mp3" → "/storage/emulated/0/Music/a.mp3"; "1234-ABCD:x" → "/storage/1234-ABCD/x". */
    private fun pathOf(docId: String): String? {
        val volume = docId.substringBefore(':', "").ifEmpty { return null }
        val rel = docId.substringAfter(':')
        return if (volume == "primary") "/storage/emulated/0/$rel" else if (volume.matches(Regex("[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}"))) "/storage/$volume/$rel" else null
    }

    /** Datos leídos del propio archivo (cuando Android no lo tiene indexado). */
    private fun readFile(f: DocFile): Track {
        val guess = guessFromFileName(f.name)
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, f.uri)
            fun meta(k: Int) = r.extractMetadata(k)?.takeIf { it.isNotBlank() }
            return Track(
                id = f.uri.toString(),
                title = meta(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: guess.title,
                artist = meta(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: meta(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST) ?: guess.artist ?: UNKNOWN_ARTIST,
                album = meta(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: UNKNOWN_ALBUM,
                durationSec = ((meta(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) / 1000).toInt(),
                mediaType = f.type,
                year = meta(MediaMetadataRetriever.METADATA_KEY_YEAR)?.take(4)?.toIntOrNull()?.takeIf { it > 0 },
                genre = meta(MediaMetadataRetriever.METADATA_KEY_GENRE),
                dateAddedMs = f.modified,
                filePath = f.path,
                coverUri = if (f.type == MediaType.VIDEO) VIDEO_THUMB else EMBEDDED,
            )
        } finally { runCatching { r.release() } }
    }

    override suspend fun loadCover(track: Track): ImageBitmap? = withContext(Dispatchers.IO) {
        val uri = track.coverUri ?: return@withContext null
        if (uri == VIDEO_THUMB) return@withContext videoThumb(track)
        if (uri == EMBEDDED) return@withContext embeddedCover(track)
        runCatching {
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
        }.getOrNull()
    }

    /** Portada incrustada en el archivo, reducida a unos 512 px. */
    private fun embeddedCover(track: Track): ImageBitmap? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, Uri.parse(track.id))
            val bytes = r.embeddedPicture ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 512) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
        } catch (_: Exception) { null } finally { runCatching { r.release() } }
    }

    /** Miniatura del video: la del sistema (Android 10+) o un cuadro al 10 %. */
    private fun videoThumb(track: Track): ImageBitmap? {
        val uri = Uri.parse(track.id)
        if (Build.VERSION.SDK_INT >= 29) runCatching { return context.contentResolver.loadThumbnail(uri, Size(512, 288), null).asImageBitmap() }
        return frameAt(uri, track.durationSec * 100_000L)?.asImageBitmap()
    }

    /** Cuadros repartidos por el video para la vista previa al pasar por encima o en la lista. */
    override suspend fun loadPreviewFrames(track: Track): List<ImageBitmap> = withContext(Dispatchers.IO) {
        if (track.mediaType != MediaType.VIDEO || track.durationSec <= 0) return@withContext emptyList()
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, Uri.parse(track.id))
            listOf(.1, .3, .5, .7, .9).mapNotNull { k ->
                val us = (track.durationSec * 1_000_000L * k).toLong()
                runCatching { scaled(r, us) }.getOrNull()?.asImageBitmap()
            }
        } catch (_: Exception) { emptyList() } finally { runCatching { r.release() } }
    }

    private fun frameAt(uri: Uri, us: Long): Bitmap? {
        val r = MediaMetadataRetriever()
        return try { r.setDataSource(context, uri); scaled(r, us) } catch (_: Exception) { null } finally { runCatching { r.release() } }
    }

    private fun scaled(r: MediaMetadataRetriever, us: Long): Bitmap? =
        if (Build.VERSION.SDK_INT >= 27) r.getScaledFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 384, 216)
        else r.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { Bitmap.createScaledBitmap(it, 384, 384 * it.height / maxOf(1, it.width), true) }

    @Suppress("DEPRECATION") // DATA sigue siendo la forma más simple de obtener la ruta para filtrar por carpeta.
    private fun query(type: MediaType): List<Track> {
        val audio = type == MediaType.AUDIO
        val collection = if (audio) MediaStore.Audio.Media.EXTERNAL_CONTENT_URI else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = buildList {
            add(MediaStore.MediaColumns._ID); add(MediaStore.MediaColumns.DISPLAY_NAME); add(MediaStore.MediaColumns.TITLE)
            add(MediaStore.MediaColumns.DURATION); add(MediaStore.MediaColumns.DATE_ADDED); add(MediaStore.MediaColumns.DATA)
            add(MediaStore.MediaColumns.ARTIST); add(MediaStore.MediaColumns.ALBUM)
            if (audio) { add(MediaStore.Audio.Media.YEAR); add(MediaStore.Audio.Media.ALBUM_ID) }
        }.toTypedArray()
        val out = mutableListOf<Track>()
        // Fuera tonos, notificaciones, alarmas y grabaciones (Android 12+).
        val selection = if (!audio) null else buildList {
            add("${MediaStore.Audio.Media.IS_RINGTONE} = 0"); add("${MediaStore.Audio.Media.IS_NOTIFICATION} = 0"); add("${MediaStore.Audio.Media.IS_ALARM} = 0")
            if (Build.VERSION.SDK_INT >= 31) add("${MediaStore.Audio.Media.IS_RECORDING} = 0")
        }.joinToString(" AND ")
        context.contentResolver.query(collection, projection, selection, null, null)?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val name = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val title = c.getColumnIndexOrThrow(MediaStore.MediaColumns.TITLE)
            val dur = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
            val added = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val data = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            val artist = c.getColumnIndexOrThrow(MediaStore.MediaColumns.ARTIST)
            val album = c.getColumnIndexOrThrow(MediaStore.MediaColumns.ALBUM)
            val year = if (audio) c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR) else -1
            val albumId = if (audio) c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID) else -1
            while (c.moveToNext()) {
                val fileName = c.getString(name) ?: continue
                if (audio && isChatOrVoiceAudio(fileName, c.getString(data))) continue
                val guess = guessFromFileName(fileName)
                val uri = ContentUris.withAppendedId(collection, c.getLong(id))
                val artistTag = c.getString(artist)?.takeIf { it.isNotBlank() && it != "<unknown>" }
                out += Track(
                    id = uri.toString(),
                    title = c.getString(title)?.takeIf { it.isNotBlank() && it != fileName.substringBeforeLast('.') } ?: guess.title,
                    artist = artistTag ?: guess.artist ?: UNKNOWN_ARTIST,
                    album = c.getString(album)?.takeIf { it.isNotBlank() } ?: UNKNOWN_ALBUM,
                    durationSec = (c.getLong(dur) / 1000).toInt(),
                    mediaType = type,
                    year = if (year >= 0) c.getInt(year).takeIf { it > 0 } else null,
                    dateAddedMs = c.getLong(added) * 1000,
                    filePath = c.getString(data),
                    coverUri = if (albumId >= 0) "content://media/external/audio/albumart/${c.getLong(albumId)}" else VIDEO_THUMB,
                )
            }
        }
        return out
    }

    private companion object {
        const val VIDEO_THUMB = "video-thumb"
        /** Portada dentro del archivo (se lee con MediaMetadataRetriever). */
        const val EMBEDDED = "embedded"
    }
}
