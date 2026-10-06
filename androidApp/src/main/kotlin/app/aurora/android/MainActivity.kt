package app.aurora.android

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import app.aurora.App
import app.aurora.platform.AndroidPlatform

class MainActivity : ComponentActivity() {
    override fun onDestroy() {
        // La pantalla se va, pero el estado sigue (la música no se corta): se sueltan los ganchos de esta actividad.
        app.aurora.AuroraRuntime.get(this).let { it.onExitToBackground = {}; it.onWindowFullscreen = {} }
        if (AndroidPlatform.folderPicker != null) AndroidPlatform.folderPicker = null
        pendingFolder?.complete(null)
        super.onDestroy()
    }

    // La interfaz se muestra después de pedir permiso para leer la música y los videos,
    // así el primer escaneo ya encuentra los archivos.
    private val askPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        setContent {
            // El estado vive con el proceso (lo comparte el servicio de reproducción): la pantalla solo lo muestra.
            App(external = app.aurora.AuroraRuntime.get(this), onState = { st ->
                st.onExitToBackground = { moveTaskToBack(true) }
                st.onWindowFullscreen = { on -> setImmersive(on, st.player.video?.videoSize?.value) }
            })
        }
    }

    // Selector de carpetas del sistema (bienvenida y Ajustes › Biblioteca).
    private var pendingFolder: kotlinx.coroutines.CompletableDeferred<android.net.Uri?>? = null
    private val pickTree = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        pendingFolder?.complete(uri); pendingFolder = null
    }

    /**
     * Pantalla completa del video: oculta la barra de estado y los botones de navegación
     * (vuelven un momento al deslizar desde el borde) y gira a horizontal si el video es horizontal.
     */
    private fun setImmersive(on: Boolean, videoSize: Pair<Int, Int>?) {
        val bars = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        if (on) {
            bars.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            bars.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            val landscape = videoSize != null && videoSize.first > videoSize.second
            requestedOrientation = if (landscape) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            bars.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AndroidPlatform.init(this)
        AndroidPlatform.folderPicker = {
            pendingFolder?.complete(null)
            val d = kotlinx.coroutines.CompletableDeferred<android.net.Uri?>()
            pendingFolder = d
            pickTree.launch(null)
            d.await()
        }
        val perms = if (Build.VERSION.SDK_INT >= 33) {
            // La notificación de reproducción (sesión multimedia) no necesita POST_NOTIFICATIONS en Android 13+:
            // comprobado en Android 16 con el permiso negado.
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        askPermissions.launch(perms)
        openFromWidget(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        openFromWidget(intent)
    }

    /** La portada o el título de un widget abren el reproductor. */
    private fun openFromWidget(intent: android.content.Intent?) {
        if (intent?.getStringExtra(app.aurora.widget.EXTRA_OPEN) != app.aurora.widget.OPEN_PLAYER) return
        intent.removeExtra(app.aurora.widget.EXTRA_OPEN)
        app.aurora.AuroraRuntime.get(this).requestPlayer()
    }
}
