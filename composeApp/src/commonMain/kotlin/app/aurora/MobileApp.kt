package app.aurora

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
import app.aurora.components.Backdrop
import app.aurora.components.MiniPlayer
import app.aurora.components.Tab
import app.aurora.components.TabBar
import app.aurora.components.Toast
import app.aurora.components.DialogHost
import app.aurora.components.VideoSurface
import app.aurora.screens.VideoScreen
import app.aurora.screens.menuFor
import androidx.compose.runtime.LaunchedEffect
import app.aurora.data.LibraryState
import app.aurora.domain.Playlist
import app.aurora.player.PlaybackState
import app.aurora.screens.HomeScreen
import app.aurora.screens.LibraryScreen
import app.aurora.screens.PlayerScreen
import app.aurora.screens.PlaylistScreen
import app.aurora.screens.SearchScreen
import app.aurora.screens.SettingsScreen
import app.aurora.screens.VideosScreen
import app.aurora.screens.mobilePadding
import app.aurora.theme.AuroraMotion

/** Lista abierta encima de una pestaña (la pantalla de abajo). */
private sealed interface Overlay {
    data object None : Overlay
    data class OpenPlaylist(val playlist: Playlist) : Overlay
}

/** Capa a pantalla completa encima de todo: la pantalla de abajo sigue viva (y conserva su posición). */
private enum class Layer { Player, Video }

/** Interfaz de teléfono: pestañas, mini reproductor y pantallas a pantalla completa. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun MobileApp(state: AppState, lib: LibraryState, playback: PlaybackState) {
    val player = state.player
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    // Pestañas visitadas, para que Atrás vuelva a la anterior.
    var tabHistory by rememberSaveable { mutableStateOf(listOf<String>()) }
    var overlay by remember { mutableStateOf<Overlay>(Overlay.None) }
    var layer by rememberSaveable { mutableStateOf<Layer?>(null) }
    fun selectTab(t: Tab) {
        if (t != tab) tabHistory = (tabHistory - t.name + tab.name).takeLast(10)
        tab = t; overlay = Overlay.None
    }
    val tracks = lib.tracks
    val playlists = remember(tracks, lib.usingSamples, state.liked, state.userPlaylists) { state.playlists(tracks, lib.usingSamples) }
    val current = playback.current
    val immersive = layer != null
    val videos = remember(tracks) { tracks.filter { it.mediaType == app.aurora.domain.MediaType.VIDEO } }
    val menu: (app.aurora.domain.Track) -> Unit = { state.menuFor(it) }
    // Al empezar un video se abre su pantalla.
    LaunchedEffect(state.videoRequests) { if (state.videoRequests > 0) { state.videoInBackground = false; layer = Layer.Video } }
    // Un widget pidió abrir el reproductor (o el video, si suena uno).
    LaunchedEffect(state.playerRequests) {
        if (state.playerRequests > 0) layer = if (current?.mediaType == app.aurora.domain.MediaType.VIDEO && !state.videoInBackground) Layer.Video else Layer.Player
    }
    // Cambio automático entre canción y video cuando el reproductor está abierto.
    val currentId = current?.id
    val currentIsVideo = current?.mediaType == app.aurora.domain.MediaType.VIDEO
    LaunchedEffect(currentId) {
        if (currentIsVideo && layer == Layer.Player && !state.videoInBackground) layer = Layer.Video
        if (!currentIsVideo && layer == Layer.Video) layer = Layer.Player
    }
    // Si deja de sonar un video (pasa a una canción), se sale de pantalla completa y vuelven las barras.
    LaunchedEffect(playback.hasVideo) { if (!playback.hasVideo && state.videoFullscreen) state.setFullscreen(false) }
    val userName by state.settings.userName.collectAsState()
    // Relleno inferior de todas las listas: lo que mide de verdad el bloque de abajo (mini reproductor,
    // pestañas y la barra de navegación del sistema, sea de gestos o de 3 botones) + 16 dp.
    // Cambia solo si el mini reproductor aparece o desaparece.
    val density = androidx.compose.ui.platform.LocalDensity.current
    var barsHeight by remember { mutableStateOf(0.dp) }
    val bottomPadding = barsHeight + 16.dp
    val openSettings = { selectTab(Tab.Settings) }

    // Atrás (lo más general; lo que está encima registra el suyo después y gana):
    // lista abierta → pestaña anterior → Inicio → la app pasa a segundo plano (la música sigue).
    BackHandler(enabled = !immersive) {
        when {
            overlay != Overlay.None -> overlay = Overlay.None
            tabHistory.isNotEmpty() -> { tab = Tab.valueOf(tabHistory.last()); tabHistory = tabHistory.dropLast(1) }
            tab != Tab.Home -> tab = Tab.Home
            else -> state.onExitToBackground()
        }
    }

    // Cada pestaña y cada lista guardan su estado (posición de las listas, categoría, búsqueda…).
    val saved = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    Backdrop(playing = playback.isPlaying, intensity = if (immersive) 1f else .55f, veil = 0f) {
        val contentAlpha by animateFloatAsState(if (immersive) 0f else 1f, tween(400))
        AnimatedContent(
            modifier = Modifier.graphicsLayer { alpha = contentAlpha },
            targetState = overlay to tab,
            transitionSpec = {
                // Sin animación de salida: la pantalla anterior se va al instante (y su estado queda guardado).
                (fadeIn(tween(420, easing = AuroraMotion.Ease)) + slideInVertically(tween(420)) { 14 } + scaleIn(tween(420), .985f))
                    .togetherWith(androidx.compose.animation.ExitTransition.None)
            },
        ) { (o, t) ->
            val key = if (o is Overlay.OpenPlaylist) "lista:${o.playlist.id}" else "pestaña:${t.name}"
            saved.SaveableStateProvider(key) {
            when {
                o is Overlay.OpenPlaylist -> {
                    val p = state.latest(o.playlist)
                    if (p == null) LaunchedEffect(Unit) { overlay = Overlay.None }
                    else {
                        val list = state.tracksOf(p, tracks)
                        PlaylistScreen(
                            p, list, playback, bottomPadding,
                            onBack = { overlay = Overlay.None },
                            onPlay = { q, i -> state.play(q, i, p.name) },
                            onShuffle = player::toggleShuffle, onToast = state::show,
                            onMore = { state.menuFor(it, p) },
                            onRename = if (p.isUser) ({ state.dialog = AppDialog.RenamePlaylist(p) }) else null,
                            onDelete = if (p.isUser) ({ state.dialog = AppDialog.DeletePlaylist(p) }) else null,
                            onSaveAsList = if (!p.isUser) ({ state.dialog = AppDialog.NewPlaylist(list) }) else null,
                        )
                    }
                }
                t == Tab.Settings -> Box(Modifier.statusBarsPadding()) {
                    SettingsScreen(state, lib, onBack = null, contentPadding = mobilePadding(bottomPadding))
                }
                t == Tab.Home -> HomeScreen(
                    lib, playlists, playback, bottomPadding,
                    onPlay = { q, i -> state.play(q, i, "Inicio") },
                    onOpenPlaylist = { overlay = Overlay.OpenPlaylist(it) },
                    onOpenSettings = openSettings,
                    onMore = menu,
                    lastPlayed = { state.stats.get(it.id).lastMs },
                    userName = userName,
                    onEditName = { state.dialog = AppDialog.EditName },
                    dailyMix = { app.aurora.components.DailyMixCard(state, { overlay = Overlay.OpenPlaylist(it) }, Modifier.padding(top = 16.dp)) },
                )
                t == Tab.Search -> SearchScreen(tracks, bottomPadding, { q, i -> state.play(q, i, "Búsqueda") }, menu)
                t == Tab.Library -> LibraryScreen(
                    lib, playlists, playback, bottomPadding,
                    onPlay = { q, i -> state.play(q, i, "Tu biblioteca") },
                    onOpenPlaylist = { overlay = Overlay.OpenPlaylist(it) },
                    onOpenSettings = openSettings,
                    onMore = menu,
                    onNewPlaylist = { state.dialog = AppDialog.NewPlaylist() },
                    onSearch = { selectTab(Tab.Search) },
                    plays = { state.stats.get(it.id).count },
                )
                else -> VideosScreen(lib, bottomPadding, { q, i -> state.play(q, i, "Videos") }, openSettings, menu)
            }
            }
        }

        // Mini reproductor y pestañas: se deslizan fuera al abrir el reproductor o el video.
        AnimatedVisibility(
            visible = !immersive,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(450)) { it },
            exit = slideOutVertically(tween(450)) { it },
        ) {
            // Fondo sólido en todo el bloque (también entre el mini reproductor y las pestañas).
            Column(
                Modifier.fillMaxWidth().background(app.aurora.theme.Ui.colors.background)
                    .onSizeChanged { barsHeight = with(density) { it.height.toDp() } },
            ) {
                if (current != null) {
                    MiniPlayer(
                        current, playback.isPlaying,
                        progress = playback.progress,
                        // Si suena un video, se abre la vista de video (no la de la canción).
                        onOpen = { layer = if (currentIsVideo) Layer.Video else Layer.Player },
                        onToggle = player::togglePlay,
                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                        onOpenVideo = if (playback.hasVideo) ({ layer = Layer.Video }) else null,
                    )
                }
                TabBar(tab.takeIf { overlay == Overlay.None }, { selectTab(it) })
            }
        }

        // Reproductor y video: capa que entra desde abajo (500 ms) encima de la pantalla actual.
        BackHandler(enabled = immersive && !state.videoFullscreen) { layer = null }
        // Elegir "Canción" vale mientras se está en el reproductor: al volver a entrar, un video se ve como video.
        LaunchedEffect(layer) { if (layer == null) state.videoInBackground = false }
        AnimatedContent(
            targetState = layer,
            transitionSpec = {
                (fadeIn(tween(500)) + slideInVertically(tween(500, easing = AuroraMotion.Ease)) { 160 }).togetherWith(fadeOut(tween(250)))
            },
            modifier = Modifier.fillMaxSize(),
        ) { l ->
            when (l) {
                Layer.Player -> PlayerScreen(
                    state, playback,
                    onClose = { layer = null },
                    onOpenVideo = { state.videoInBackground = false; layer = Layer.Video },
                )
                Layer.Video -> VideoScreen(state, playback, videos, onClose = { layer = null },
                    onShowSong = { state.videoInBackground = true; layer = Layer.Player })
                null -> Box(Modifier)
            }
        }

        // Mini video flotante: el video sigue en segundo plano mientras navegas.
        androidx.compose.animation.AnimatedVisibility(
            visible = playback.hasVideo && playback.videoEnabled && !immersive && !state.videoFullscreen && state.pipClosedFor != currentId,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = bottomPadding),
            enter = fadeIn() + slideInVertically { it / 3 }, exit = fadeOut(),
        ) {
            app.aurora.components.PipVideo(playback, player, onExpand = { layer = Layer.Video }, onClose = { state.pipClosedFor = currentId }, width = 200.dp)
        }
        if (state.videoFullscreen && playback.hasVideo) {
            // Atrás sale de pantalla completa (no cierra el video).
            BackHandler { state.setFullscreen(false) }
            app.aurora.components.FullscreenVideo(playback, player, onExit = { state.setFullscreen(false) })
        }
        // Diálogos y hojas de opciones: Atrás los cierra antes que todo lo demás.
        BackHandler(enabled = state.dialog != null) { state.dialog = null }
        DialogHost(state, tracks)
        Toast(state.toast, Modifier.align(Alignment.TopCenter))
    }
}
