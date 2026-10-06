package app.aurora.player

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.snapshotFlow
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import app.aurora.AppState
import app.aurora.AuroraRuntime
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Servicio de reproducción: mantiene ExoPlayer vivo en segundo plano y publica la MediaSession
 * (notificación, pantalla de bloqueo, auriculares Bluetooth, coche). Media3 dibuja la notificación
 * (DefaultMediaNotificationProvider): Aurora pone datos, portada, la cola y dos botones propios,
 * Me gusta (izquierda) y Aleatorio (derecha).
 */
@UnstableApi
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private var sessionPlayer: AuroraSessionPlayer? = null
    private val scope = MainScope()
    private lateinit var app: AppState

    override fun onCreate() {
        super.onCreate()
        app = AuroraRuntime.get(this)
        // El audio pasa por el ecualizador de 10 bandas de la app antes de sonar.
        val renderers = object : androidx.media3.exoplayer.DefaultRenderersFactory(this) {
            override fun buildAudioSink(context: android.content.Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean) =
                androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(EqAudioProcessor()))
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
        }
        val exo = ExoPlayer.Builder(this, renderers)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                /* handleAudioFocus = */ true,
            )
            // Pausa si se desconectan los auriculares.
            .setHandleAudioBecomingNoisy(true)
            .build()
        val player = AuroraSessionPlayer(exo, app)
        sessionPlayer = player
        // En pausa la notificación se puede descartar enseguida (Media3 la dejaba fija 10 minutos).
        setForegroundServiceTimeoutMs(0)
        setMediaNotificationProvider(LockScreenAwareProvider(DefaultMediaNotificationProvider.Builder(this).build()))
        // Tocar la notificación abre la app.
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        session = MediaSession.Builder(this, player)
            .setCallback(Callback())
            .setMediaButtonPreferences(buttons())
            .apply { if (open != null) setSessionActivity(open) }
            .build()
        player.fromApp = { session?.controllerForCurrentRequest?.connectionHints?.getBoolean(APP_CONTROLLER) == true }

        // Cola, aleatorio y Me gusta cambian en la app → la notificación cambia al instante.
        scope.launch {
            app.player.state.map { Triple(it.current?.id, it.shuffle, it.queue.size) }.distinctUntilChanged()
                .combine(snapshotFlow { app.liked }) { a, liked -> a to (a.first in liked) }
                .distinctUntilChanged()
                .collect { player.refresh(); session?.setMediaButtonPreferences(buttons()) }
        }
        // Al cambiar "Mostrar controles en la pantalla de bloqueo" se vuelve a dibujar la notificación.
        scope.launch {
            app.prefsRepo.prefs.map { it.lockScreenControls }.distinctUntilChanged().collect { triggerNotificationUpdate() }
        }
    }

    /**
     * Me gusta a la izquierda y Aleatorio a la derecha de la tarjeta multimedia. La tarjeta del sistema
     * (Android 13+, bloqueo) solo toma como acciones propias los botones que admiten SLOT_OVERFLOW.
     */
    private fun buttons(): ImmutableList<CommandButton> {
        val q = app.player.state.value
        val liked = q.current?.id?.let { it in app.liked } == true
        val like = CommandButton.Builder(if (liked) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED)
            .setDisplayName(if (liked) "Quitar de Me gusta" else "Me gusta")
            .setSessionCommand(LIKE).setSlots(CommandButton.SLOT_BACK_SECONDARY, CommandButton.SLOT_OVERFLOW).build()
        val shuffle = CommandButton.Builder(if (q.shuffle) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF)
            .setDisplayName(if (q.shuffle) "Desactivar aleatorio" else "Aleatorio")
            .setSessionCommand(SHUFFLE).setSlots(CommandButton.SLOT_FORWARD_SECONDARY, CommandButton.SLOT_OVERFLOW).build()
        return ImmutableList.of(like, shuffle)
    }

    private inner class Callback : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult =
            MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().add(LIKE).add(SHUFFLE).build())
                .setMediaButtonPreferences(buttons())
                .build()

        override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                LIKE.customAction -> app.toggleLike(app.player.state.value.current)
                SHUFFLE.customAction -> app.player.toggleShuffle()
            }
            // El botón cambia al instante, también si la app no tiene pantalla abierta.
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
            session.setMediaButtonPreferences(buttons())
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    /**
     * La notificación de Media3, pero privada si en Ajustes se apagó "Mostrar controles en la pantalla de bloqueo":
     * con el teléfono bloqueado solo se ve "Aurora · Reproduciendo".
     */
    private inner class LockScreenAwareProvider(private val inner: DefaultMediaNotificationProvider) : MediaNotification.Provider by inner {
        override fun createNotification(
            mediaSession: MediaSession, mediaButtonPreferences: ImmutableList<CommandButton>,
            actionFactory: MediaNotification.ActionFactory, onNotificationChangedCallback: MediaNotification.Provider.Callback,
        ): MediaNotification {
            val n = inner.createNotification(mediaSession, mediaButtonPreferences, actionFactory, onNotificationChangedCallback)
            val notification = n.notification
            if (app.prefsRepo.prefs.value.lockScreenControls) {
                notification.visibility = Notification.VISIBILITY_PUBLIC
                notification.publicVersion = null
            } else {
                notification.visibility = Notification.VISIBILITY_PRIVATE
                notification.publicVersion = Notification.Builder(this@PlaybackService, notification.channelId)
                    .setSmallIcon(notification.smallIcon).setContentTitle("Aurora").setContentText("Reproduciendo").build()
            }
            return n
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Si se cierra la app desde Recientes y no está sonando, se detiene el servicio. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }

    companion object {
        val LIKE = SessionCommand("app.aurora.ME_GUSTA", Bundle.EMPTY)
        val SHUFFLE = SessionCommand("app.aurora.ALEATORIO", Bundle.EMPTY)

        /** Señal de conexión del MediaController de la propia app. */
        const val APP_CONTROLLER = "app.aurora.CONTROLADOR_DE_LA_APP"
    }
}
