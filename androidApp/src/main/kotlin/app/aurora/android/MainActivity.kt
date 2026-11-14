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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

class MainActivity : ComponentActivity() {
    override fun onDestroy() {
        // La pantalla se va, pero el estado sigue (la música no se corta): se sueltan los ganchos de esta actividad.
        app.aurora.AuroraRuntime.get(this).let { it.onExitToBackground = {}; it.onWindowFullscreen = {}; it.inSystemPip = false }
        runCatching { unregisterReceiver(pipReceiver) }
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
                // Atrás en Inicio: con un video sonando pasa a la ventanita (como el botón Inicio); si no, la app
                // se va a segundo plano y la música sigue.
                st.onExitToBackground = { if (!enterPip()) moveTaskToBack(true) }
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
        // Animaciones continuas (fondo, onda, barra, ecualizador, karaoke) a ~30 fps, como en escritorio: son lentas
        // (unos píxeles por fotograma) y se ven igual que a 60/120 Hz con una fracción de la batería.
        app.aurora.components.animationFrameIntervalMs = 33
        AndroidPlatform.folderPicker = { initial ->
            pendingFolder?.complete(null)
            val d = kotlinx.coroutines.CompletableDeferred<android.net.Uri?>()
            pendingFolder = d
            // `initial` (p. ej. la carpeta Music) va como EXTRA_INITIAL_URI: el selector se abre ya ubicado ahí.
            pickTree.launch(initial)
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
        androidx.core.content.ContextCompat.registerReceiver(
            this, pipReceiver, android.content.IntentFilter(PIP_TOGGLE), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        watchPip()
    }

    // --- Video en segundo plano: imagen en imagen (PiP) del sistema, como YouTube ---

    /** Hay un video sonando y con imagen: al salir de la app pasa a la ventanita. En pausa, la app se va normal. */
    private var pipEligible = false
    private var pipPlaying = false
    private var pipSize: Pair<Int, Int>? = null
    /** Se apagó la imagen del video al ir a segundo plano sin ventanita (solo se oye): se enciende al volver. */
    private var videoOffInBackground = false

    /** Botón reproducir/pausa de la ventanita. */
    private val pipReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
            app.aurora.AuroraRuntime.get(this@MainActivity).player.togglePlay()
        }
    }

    private fun pipParams(): android.app.PictureInPictureParams {
        val b = android.app.PictureInPictureParams.Builder()
        // Proporción del video (Android solo admite entre 1:2,39 y 2,39:1).
        pipSize?.let { (w, h) ->
            if (w > 0 && h > 0) {
                val r = (w.toFloat() / h).coerceIn(1 / 2.38f, 2.38f)
                b.setAspectRatio(android.util.Rational((r * 1000).toInt(), 1000))
            }
        }
        val label = if (pipPlaying) "Pausar" else "Reproducir"
        val icon = android.graphics.drawable.Icon.createWithResource(
            this, if (pipPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
        )
        val toggle = android.app.PendingIntent.getBroadcast(
            this, 0, android.content.Intent(PIP_TOGGLE).setPackage(packageName),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        b.setActions(listOf(android.app.RemoteAction(icon, label, label, toggle)))
        if (Build.VERSION.SDK_INT >= 31) {
            // Android 12+: entra solo al salir (botón o gesto de Inicio), únicamente con un video sonando.
            b.setAutoEnterEnabled(pipEligible)
            b.setSeamlessResizeEnabled(true)
        }
        return b.build()
    }

    private fun updatePip() {
        if (!packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        runCatching { setPictureInPictureParams(pipParams()) }
    }

    /** Entra en la ventanita si hay un video sonando. Devuelve si entró. */
    private fun enterPip(): Boolean {
        if (!pipEligible || !packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) return false
        return runCatching { enterPictureInPictureMode(pipParams()) }.getOrDefault(false)
    }

    /** Android 8 a 11 no tienen entrada automática: se entra al pulsar Inicio. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < 31) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        val st = app.aurora.AuroraRuntime.get(this)
        st.inSystemPip = isInPictureInPictureMode
        // Se cerró la ventanita con su ×: la actividad ya está detenida. Como en YouTube, el video se pausa.
        if (!isInPictureInPictureMode && lifecycle.currentState == androidx.lifecycle.Lifecycle.State.CREATED) {
            st.player.pauseIfPlaying()
        }
    }

    override fun onStop() {
        super.onStop()
        // En segundo plano sin ventanita (p. ej. con el PiP de Aurora apagado en Ajustes del sistema) un video sigue
        // sonando: se apaga su imagen para no decodificarla sin pantalla (ahorra batería) y se enciende al volver.
        if (isInPictureInPictureMode) return
        val st = app.aurora.AuroraRuntime.get(this)
        val s = st.player.state.value
        if (s.hasVideo && s.videoEnabled) {
            st.player.video?.setEnabled(false)
            videoOffInBackground = true
        }
    }

    override fun onStart() {
        super.onStart()
        if (videoOffInBackground) {
            videoOffInBackground = false
            val st = app.aurora.AuroraRuntime.get(this)
            if (st.player.state.value.videoEnabled) st.player.video?.setEnabled(true)
        }
    }

    /** Sigue el estado del reproductor para que la ventanita sepa si debe entrar y qué botón mostrar. */
    private fun watchPip() {
        val st = app.aurora.AuroraRuntime.get(this)
        val size = st.player.video?.videoSize ?: kotlinx.coroutines.flow.MutableStateFlow(null)
        lifecycleScope.launch {
            kotlinx.coroutines.flow.combine(st.player.state, size) { s, sz ->
                Triple(s.hasVideo && s.isPlaying && s.videoEnabled, s.isPlaying, sz)
            }.distinctUntilChanged().collect { (eligible, playing, sz) ->
                pipEligible = eligible; pipPlaying = playing; pipSize = sz
                updatePip()
            }
        }
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

/** Acción del botón reproducir/pausa de la ventanita de imagen en imagen. */
private const val PIP_TOGGLE = "app.aurora.music.PIP_TOGGLE"
