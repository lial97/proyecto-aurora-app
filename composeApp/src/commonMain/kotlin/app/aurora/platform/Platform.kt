package app.aurora.platform

import androidx.compose.ui.graphics.ImageBitmap
import app.aurora.domain.Track
import app.aurora.player.MediaEngine

/** Almacén clave-valor para ajustes (tema, carpetas, volumen). */
interface KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String?)
}

/** Progreso del escaneo: archivos procesados y total (si se conoce). */
data class ScanProgress(val done: Int, val total: Int?)

/** Cómo se muestra una carpeta: nombre ("Rock") y ruta ("Almacenamiento interno/Music/Rock"). */
data class FolderLabel(val name: String, val path: String)

/** Acceso a los archivos multimedia del dispositivo. */
interface MediaSource {
    /** Carpetas que se escanean si el usuario aún no eligió ninguna. */
    fun defaultFolders(): List<String>

    /** Si la plataforma puede abrir un selector de carpetas del sistema. */
    val canPickFolder: Boolean

    /** Abre el selector de carpetas; `null` si se cancela. */
    suspend fun pickFolder(): String?

    /** Si una carpeta guardada sigue sirviendo (Android solo acepta las elegidas con el selector del sistema). */
    fun isUsableFolder(folder: String): Boolean = true

    /** Nombre corto y ruta legible de una carpeta guardada (en Android las carpetas son URIs del sistema). */
    fun describeFolder(folder: String): FolderLabel =
        FolderLabel(folder.trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\').ifEmpty { folder }, folder)

    /** Carpetas con música o video detectadas por el sistema (para elegir desde la app). */
    suspend fun candidateFolders(): List<String>

    /** Busca audio y video dentro de [folders] (incluye subcarpetas). */
    suspend fun scan(folders: List<String>, onProgress: (ScanProgress) -> Unit): List<Track>

    /** Avisa cuando cambian archivos multimedia dentro de [folders] (para "Vigilar cambios"). */
    fun watch(folders: List<String>): kotlinx.coroutines.flow.Flow<Unit> = kotlinx.coroutines.flow.emptyFlow()

    /** Escribe título, artista, álbum, año y género en las etiquetas del archivo. */
    suspend fun writeTags(track: Track, edit: app.aurora.domain.TrackEdit): Boolean = false

    /** Abre la carpeta del archivo en el explorador del sistema. */
    suspend fun revealInFolder(track: Track): Boolean = false

    /** Guarda la letra como archivo .lrc junto a la canción (para que otros reproductores también la usen). */
    suspend fun saveLyricsFile(track: Track, lrc: String): Boolean = false

    /** Carpetas de la biblioteca donde Aurora no puede escribir (en Android, elegidas antes con permiso solo de lectura). */
    fun foldersWithoutWrite(folders: List<String>): List<String> = emptyList()

    /** Carga la portada de una pista (incrustada en el archivo o imagen de la carpeta). */
    suspend fun loadCover(track: Track): ImageBitmap?

    /**
     * Fotogramas de vista previa de un video (varios momentos del video, en orden).
     * El primero sirve de miniatura. Vacío si la plataforma no puede generarlos.
     */
    suspend fun loadPreviewFrames(track: Track): List<ImageBitmap> = emptyList()
}

/** Respuesta HTTP mínima. */
data class HttpResponse(val code: Int, val body: String)

/** Cliente HTTP para servicios abiertos (LRCLIB). `null` = sin conexión o error de red. */
fun interface HttpClient {
    suspend fun get(url: String, headers: Map<String, String>): HttpResponse?

    /** Descarga un archivo (portadas). `null` = error de red o respuesta distinta de 200. */
    suspend fun getBytes(url: String, headers: Map<String, String>): ByteArray? = null
}

/** Archivos guardados por la app (portadas descargadas). Permanentes: no es una caché que el sistema pueda borrar. */
interface BlobStore {
    fun read(key: String): ByteArray?
    fun write(key: String, bytes: ByteArray): Boolean
    fun delete(key: String)

    /** En memoria (pruebas y plataformas sin almacenamiento). */
    class Memory : BlobStore {
        private val map = mutableMapOf<String, ByteArray>()
        override fun read(key: String) = map[key]
        override fun write(key: String, bytes: ByteArray): Boolean { map[key] = bytes; return true }
        override fun delete(key: String) { map.remove(key) }
    }
}

/** Caché de textos pequeños en disco (letras descargadas). */
interface TextCache {
    fun get(key: String): String?
    fun put(key: String, value: String)
    /** Cantidad de entradas y bytes ocupados. */
    fun stats(): Pair<Int, Long> = 0 to 0L
    fun clear() {}
}

class PlatformServices(
    val name: String,
    /** Es un PC: se usa la interfaz de escritorio. */
    val isDesktop: Boolean,
    val store: KeyValueStore,
    val media: MediaSource,
    /** Motor de audio real, o `null` si la plataforma aún no lo tiene (se simula). */
    val mediaEngine: MediaEngine?,
    val http: HttpClient = HttpClient { _, _ -> null },
    val textCache: TextCache = MemoryTextCache(),
    val files: BlobStore = BlobStore.Memory(),
)

class MemoryTextCache : TextCache {
    private val map = mutableMapOf<String, String>()
    override fun get(key: String) = map[key]
    override fun put(key: String, value: String) { map[key] = value }
    override fun stats() = map.size to map.values.sumOf { it.length.toLong() }
    override fun clear() = map.clear()
}

expect fun createPlatformServices(): PlatformServices

/** Almacén en memoria (pruebas y plataformas sin persistencia). */
class MemoryStore : KeyValueStore {
    private val map = mutableMapOf<String, String>()
    override fun get(key: String) = map[key]
    override fun put(key: String, value: String?) {
        if (value == null) map.remove(key) else map[key] = value
    }
}
