package app.aurora.player

import com.sun.jna.Library
import com.sun.jna.Native
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.factory.discovery.strategy.BaseNativeDiscoveryStrategy
import uk.co.caprica.vlcj.factory.discovery.strategy.LinuxNativeDiscoveryStrategy
import uk.co.caprica.vlcj.factory.discovery.strategy.OsxNativeDiscoveryStrategy
import uk.co.caprica.vlcj.factory.discovery.strategy.WindowsNativeDiscoveryStrategy
import java.io.File

/**
 * VLC que viene dentro del instalador (carpeta `vlc` de los recursos de la app): así no hace falta instalar
 * VLC aparte. Si no está (p. ej. al compilar sin él), se busca el VLC instalado en el sistema.
 */
object BundledVlc {
    private val windows = System.getProperty("os.name").orEmpty().lowercase().contains("win")

    /** `<recursos de la app>/vlc` (Compose pone la ruta en `compose.application.resources.dir`). */
    val dir: File? = System.getProperty("compose.application.resources.dir")
        ?.let { File(it, "vlc") }
        ?.takeIf { d -> d.isDirectory && (d.listFiles()?.any { it.name.startsWith("libvlc.") } == true) }

    private var found: Boolean? = null

    /** Busca libvlc (primero el de la app) y prepara la ruta de los complementos. Se hace una sola vez. */
    @Synchronized
    fun discover(): Boolean = found ?: runCatching {
        NativeDiscovery(Strategy(), LinuxNativeDiscoveryStrategy(), WindowsNativeDiscoveryStrategy(), OsxNativeDiscoveryStrategy()).discover()
    }.getOrDefault(false).also { found = it }

    /*
     * Índice de complementos (plugins/plugins.dat). Guarda la fecha y el tamaño de cada complemento; si no
     * coinciden, VLC abre los ~240 complementos en cada arranque y, la primera vez, el antivirus revisa cada uno
     * (más de un minuto en Windows; con el índice válido, unos 5 s).
     * - Windows: preparar-vlc.ps1 pone a los complementos una fecha fija y arma el índice con ella. Gradle y el
     *   instalador cambian las fechas al copiar: aquí se restaura la fecha fija (no se lee el contenido, así que
     *   el antivirus no los revisa) y el índice vuelve a valer.
     * - Si eso no se puede (carpeta de solo lectura, otro sistema, índice vacío de una versión vieja), VLC lo
     *   rehace y lo guarda una vez (`--reset-plugins-cache`); una marca por ubicación evita repetirlo.
     */
    private val pluginsDat: File? get() = dir?.let { File(it, "plugins/plugins.dat") }
    /** Fecha fija de los complementos en Windows (la misma que pone preparar-vlc.ps1): 2020-01-01 00:00 UTC. */
    private const val FIXED_MTIME = 1_577_836_800_000L

    /** Una marca por ubicación (la versión instalada y la portable no se pisan). */
    private val stampFile: File get() =
        File(app.aurora.platform.configDir(), "vlc-indice-${Integer.toHexString(dir?.absolutePath.hashCode())}.txt")
    private fun stamp(): String? = pluginsDat?.let { "${it.absolutePath}|${it.length()}|${it.lastModified()}" }

    /** Devuelve la fecha fija a los complementos de Windows. `true` si quedaron como los describe el índice. */
    private fun restoreFixedDates(): Boolean {
        if (!windows) return false
        val dat = pluginsDat?.takeIf { it.length() > 10_000 } ?: return false
        val dlls = dat.parentFile.walkTopDown().filter { it.isFile && it.name.endsWith(".dll") }.toList()
        if (dlls.isEmpty()) return false
        if (dlls.all { it.lastModified() == FIXED_MTIME }) return true
        return dlls.all { it.lastModified() == FIXED_MTIME || it.setLastModified(FIXED_MTIME) }
    }

    /** Opciones extra para el primer `MediaPlayerFactory`: regenerar el índice solo si hace falta. */
    fun cacheArgs(): Array<String> {
        if (runCatching { restoreFixedDates() }.getOrDefault(false)) return emptyArray()
        val s = stamp() ?: return emptyArray()
        val saved = runCatching { stampFile.readText() }.getOrNull()
        return if (saved == s) emptyArray() else arrayOf("--reset-plugins-cache")
    }

    /** VLC ya arrancó (con el índice al día si se pudo escribir). Las miniaturas esperan a esto. */
    private val cacheReady = java.util.concurrent.CountDownLatch(1)

    fun cacheDone() {
        stamp()?.let { s -> runCatching { stampFile.parentFile.mkdirs(); stampFile.writeText(s) } }
        cacheReady.countDown()
    }

    /** Espera a que el reproductor deje listo el índice (para no abrir todos los complementos dos veces a la vez). */
    fun awaitCache() { runCatching { cacheReady.await(3, java.util.concurrent.TimeUnit.MINUTES) } }

    private class Strategy : BaseNativeDiscoveryStrategy(
        if (windows) arrayOf("libvlc\\.dll", "libvlccore\\.dll") else arrayOf("libvlc\\.so(?:\\.\\d)*", "libvlccore\\.so(?:\\.\\d)*"),
        arrayOf("%s/plugins"),
    ) {
        override fun supported() = dir != null
        override fun discoveryDirectories(): List<String> = listOfNotNull(dir?.absolutePath)

        /** VLC lee sus complementos de VLC_PLUGIN_PATH; se cambia en el propio proceso (Java no puede hacerlo). */
        override fun setPluginPath(path: String): Boolean = runCatching {
            if (windows) Native.load("msvcrt", MsvcRt::class.java)._putenv("$PLUGIN_ENV_NAME=$path") == 0
            else Native.load("c", LibC::class.java).setenv(PLUGIN_ENV_NAME, path, 1) == 0
        }.getOrDefault(false)
    }

    @Suppress("FunctionName")
    private interface MsvcRt : Library { fun _putenv(assignment: String): Int }
    private interface LibC : Library { fun setenv(name: String, value: String, overwrite: Int): Int }
}
