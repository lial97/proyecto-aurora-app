package app.aurora.player

import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import app.aurora.AppState
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlin.math.abs

/**
 * Lo que ve la sesión multimedia (notificación, pantalla de bloqueo, auriculares, coche, widgets).
 * ExoPlayer solo tiene la canción actual; la cola, el aleatorio y "anterior" (reinicia si van más de 3 s)
 * son de Aurora, así que estos comandos se reenvían al controlador de la app. Así la app, la notificación
 * y los widgets nunca se contradicen.
 */
@UnstableApi
class AuroraSessionPlayer(player: Player, private val app: AppState) : ForwardingSimpleBasePlayer(player) {
    private val controller get() = app.player

    /**
     * La orden que se está atendiendo viene de la propia app (el MediaController de [Media3Engine]).
     * Esas no se reenvían al controlador de la app: ya salen de él, y reenviarlas creaba un bucle sin fin
     * (reproducir → pausa → reproducir…) cuando el estado de la app y el de ExoPlayer no coincidían.
     */
    var fromApp: () -> Boolean = { false }
    private val queueCommands = intArrayOf(
        Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
        Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, Player.COMMAND_SET_SHUFFLE_MODE,
    )

    override fun getState(): State {
        val s = super.getState()
        val q = controller.state.value
        if (q.queue.isEmpty()) return s
        val commands = s.availableCommands.buildUpon().addAll(*queueCommands).build()
        return s.buildUpon().setAvailableCommands(commands).setShuffleModeEnabled(q.shuffle).build()
    }

    /** Reproducir/pausa desde fuera de la app (bloqueo, auriculares) pasa por el controlador de Aurora. */
    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        val q = controller.state.value
        if (!fromApp() && q.current != null && q.isPlaying != playWhenReady) { controller.togglePlay(); return Futures.immediateVoidFuture() }
        return super.handleSetPlayWhenReady(playWhenReady)
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        if (fromApp()) return super.handleSeek(mediaItemIndex, positionMs, seekCommand)
        when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> { controller.next(); return Futures.immediateVoidFuture() }
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> { controller.previous(); return Futures.immediateVoidFuture() }
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM -> {
                // Mover la barra desde la notificación: se avisa al controlador (que vuelve a pedir este mismo salto).
                val q = controller.state.value
                if (q.current != null && positionMs >= 0 && abs(positionMs - q.positionMs) > 1500) {
                    controller.seekTo((positionMs / 1000).toInt()); return Futures.immediateVoidFuture()
                }
            }
        }
        return super.handleSeek(mediaItemIndex, positionMs, seekCommand)
    }

    override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> {
        if (fromApp()) return super.handleSetShuffleModeEnabled(shuffleModeEnabled)
        if (controller.state.value.shuffle != shuffleModeEnabled) controller.toggleShuffle()
        return Futures.immediateVoidFuture()
    }

    /** La cola o el aleatorio cambiaron en la app: la sesión vuelve a leer el estado. */
    fun refresh() = invalidateState()
}
