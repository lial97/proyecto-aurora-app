package app.aurora.platform

import app.aurora.player.VlcEngine
import java.io.File
import java.util.Properties

actual fun createPlatformServices(): PlatformServices = PlatformServices(
    name = System.getProperty("os.name") ?: "Escritorio",
    isDesktop = true,
    store = PropertiesStore(File(configDir(), "ajustes.properties")),
    media = DesktopMediaSource(),
    mediaEngine = VlcEngine(),
    http = JavaHttp(),
    textCache = FileTextCache(File(cacheDir(), "letras")),
    files = FileBlobStore(File(configDir(), "portadas")),
    // `./gradlew :composeApp:run` lo activa; los instaladores no.
    isDebug = System.getProperty("aurora.debug") == "true",
)

/** HTTP con el cliente de Java (sigue redirecciones, 12 s de espera). */
class JavaHttp : HttpClient {
    private val client = java.net.http.HttpClient.newBuilder()
        .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
        .connectTimeout(java.time.Duration.ofSeconds(8)).build()

    override suspend fun get(url: String, headers: Map<String, String>): HttpResponse? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val req = java.net.http.HttpRequest.newBuilder(java.net.URI(url)).timeout(java.time.Duration.ofSeconds(12)).GET()
                headers.forEach { (k, v) -> req.header(k, v) }
                val res = client.send(req.build(), java.net.http.HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
                HttpResponse(res.statusCode(), res.body())
            }.getOrNull()
        }

    override suspend fun getBytes(url: String, headers: Map<String, String>): ByteArray? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val req = java.net.http.HttpRequest.newBuilder(java.net.URI(url)).timeout(java.time.Duration.ofSeconds(15)).GET()
                headers.forEach { (k, v) -> req.header(k, v) }
                val res = client.send(req.build(), java.net.http.HttpResponse.BodyHandlers.ofByteArray())
                res.body().takeIf { res.statusCode() == 200 }
            }.getOrNull()
        }
}

/** Portadas descargadas: un archivo por clave, en la carpeta de configuración (no se borra con la caché). */
class FileBlobStore(private val dir: File) : app.aurora.platform.BlobStore {
    private fun f(key: String) = File(dir, key.filter { it.isLetterOrDigit() || it == '_' })
    override fun read(key: String) = runCatching { f(key).takeIf { it.isFile }?.readBytes() }.getOrNull()
    override fun write(key: String, bytes: ByteArray) = runCatching { dir.mkdirs(); f(key).writeBytes(bytes); true }.getOrDefault(false)
    override fun delete(key: String) { f(key).delete() }
}

/** Un archivo por clave (nombre seguro derivado de la clave). */
class FileTextCache(private val dir: File) : TextCache {
    private fun file(key: String): File {
        val h = java.security.MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir, h.take(24) + ".txt")
    }
    override fun get(key: String): String? = file(key).takeIf { it.isFile }?.readText(Charsets.UTF_8)
    override fun put(key: String, value: String) {
        runCatching { dir.mkdirs(); file(key).writeText(value, Charsets.UTF_8) }
    }
    override fun stats(): Pair<Int, Long> {
        val files = dir.listFiles { f -> f.extension == "txt" } ?: return 0 to 0L
        return files.size to files.sumOf { it.length() }
    }
    override fun clear() { dir.listFiles { f -> f.extension == "txt" }?.forEach { it.delete() } }
}

/** Carpeta de configuración según el sistema: ~/.config/aurora, %APPDATA%\Aurora o ~/Library/Application Support/Aurora. */
internal fun configDir(): File {
    val os = System.getProperty("os.name").orEmpty().lowercase()
    val home = System.getProperty("user.home")
    return when {
        "win" in os -> File(System.getenv("APPDATA") ?: home, "Aurora")
        "mac" in os -> File(home, "Library/Application Support/Aurora")
        else -> File(System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() } ?: "$home/.config", "aurora")
    }
}

/** Ajustes en un archivo .properties legible. */
class PropertiesStore(private val file: File) : KeyValueStore {
    private val props = Properties().apply {
        if (file.exists()) runCatching { file.reader(Charsets.UTF_8).use { load(it) } }
    }

    override fun get(key: String): String? = props.getProperty(key)

    @Synchronized
    override fun put(key: String, value: String?) {
        if (value == null) props.remove(key) else props.setProperty(key, value)
        runCatching {
            file.parentFile?.mkdirs()
            file.writer(Charsets.UTF_8).use { props.store(it, "Ajustes de Aurora") }
        }
    }
}
