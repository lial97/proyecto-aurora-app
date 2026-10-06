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
