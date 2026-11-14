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
import app.aurora.screens.PlaylistSelector
import app.aurora.screens.PlaylistsScreen
import app.aurora.screens.SearchScreen
import app.aurora.screens.SettingsScreen
import app.aurora.screens.VideosScreen
import app.aurora.screens.mobilePadding
import app.aurora.theme.AuroraMotion

/** Pantalla abierta encima de una pestaña (la de abajo conserva su estado): una lista o Ajustes. */
private sealed interface Overlay {
    data object None : Overlay
    data class OpenPlaylist(val playlist: Playlist) : Overlay
    /** Ajustes ya no es una pestaña: se abre con el engranaje de arriba y Atrás vuelve a la pestaña. */
    data object Settings : Overlay
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
    // Pista a la que debe desplazarse la lista abierta (al minimizar un video de esa lista).
    var playlistFocusId by remember { mutableStateOf<String?>(null) }
    fun openPlaylist(p: Playlist, focusTrackId: String? = null) {
        playlistFocusId = focusTrackId
        overlay = Overlay.OpenPlaylist(p)
    }
    fun closePlaylist() {
        overlay = Overlay.None
        playlistFocusId = null
    }
    fun selectTab(t: Tab) {
        if (t != tab) tabHistory = (tabHistory - t.name + tab.name).takeLast(10)
        tab = t; closePlaylist()
    }
    val tracks = lib.tracks
    val playlists = remember(tracks, lib.usingSamples, state.liked, state.userPlaylists, state.dailyMix) { state.playlists(tracks, lib.usingSamples) }
    val current = playback.current
    val immersive = layer != null
    val videos = remember(tracks) { tracks.filter { it.mediaType == app.aurora.domain.MediaType.VIDEO } }
    val currentId = current?.id
    val currentIsVideo = current?.mediaType == app.aurora.domain.MediaType.VIDEO
    // Lista desde la que suena la cola: solo cuenta si la pista actual sigue en ella.
    val playingPlaylist = remember(state.playingPlaylistId, playlists) {
        state.playingPlaylistId?.let { id -> playlists.firstOrNull { it.id == id } }
    }
    val playlistTracks = remember(playingPlaylist, tracks) {
        playingPlaylist?.let { state.tracksOf(it, tracks) }.orEmpty()
    }
    val playlistContext = playingPlaylist?.takeIf {
        currentId != null && playlistTracks.any { t -> t.id == currentId }
    }
    val menu: (app.aurora.domain.Track) -> Unit = { state.menuFor(it) }
    // Al empezar un video se abre su pantalla.
    LaunchedEffect(state.videoRequests) { if (state.videoRequests > 0) { state.videoInBackground = false; layer = Layer.Video } }
    // Un widget pidió abrir el reproductor (o el video, si suena uno).
    LaunchedEffect(state.playerRequests) {
        if (state.playerRequests > 0) layer = if (current?.mediaType == app.aurora.domain.MediaType.VIDEO && !state.videoInBackground) Layer.Video else Layer.Player
    }
    // Cambio automático entre canción y video cuando el reproductor está abierto.
    // En una lista, un video que empieza tras una canción se abre maximizado por defecto.
    var lastWasVideo by remember { mutableStateOf(currentIsVideo) }
    LaunchedEffect(currentId) {
        val wasVideo = lastWasVideo
        lastWasVideo = currentIsVideo
        if (currentIsVideo) {
            if (layer == Layer.Player && !state.videoInBackground) layer = Layer.Video
            else if (!wasVideo && layer == null && playlistContext != null && !state.videoInBackground) layer = Layer.Video
        } else if (layer == Layer.Video) {
            layer = Layer.Player
        }
    }
    // Los mini reproductores (audio y video) vuelven al cambiar de pista o al reanudar la reproducción.
    LaunchedEffect(currentId) { state.revealMiniPlayers() }
    LaunchedEffect(playback.isPlaying) { if (playback.isPlaying) state.revealMiniPlayers() }
    // Si deja de sonar un video (pasa a una canción), se sale de pantalla completa y vuelven las barras.
    LaunchedEffect(playback.hasVideo) { if (!playback.hasVideo && state.videoFullscreen) state.setFullscreen(false) }
    val userName by state.settings.userName.collectAsState()
    // Relleno inferior de todas las listas: lo que mide de verdad el bloque de abajo (mini reproductor,
    // pestañas y la barra de navegación del sistema, sea de gestos o de 3 botones) + 16 dp.
    // Cambia solo si el mini reproductor aparece o desaparece.
    val density = androidx.compose.ui.platform.LocalDensity.current
    var barsHeight by remember { mutableStateOf(0.dp) }
    val bottomPadding = barsHeight + 16.dp
    val openSettings = { overlay = Overlay.Settings; playlistFocusId = null }

    // Atrás (lo más general; lo que está encima registra el suyo después y gana):
    // lista abierta → pestaña anterior → Inicio → la app pasa a segundo plano (la música sigue).
    BackHandler(enabled = !immersive) {
        when {
            overlay != Overlay.None -> closePlaylist()
            // Una pestaña guardada que ya no existe (p. ej. "Settings" de la 0.3) vuelve a Inicio.
            tabHistory.isNotEmpty() -> { tab = Tab.entries.firstOrNull { it.name == tabHistory.last() } ?: Tab.Home; tabHistory = tabHistory.dropLast(1) }
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
            val key = when (o) {
                is Overlay.OpenPlaylist -> "lista:${o.playlist.id}"
                Overlay.Settings -> "ajustes"
                Overlay.None -> "pestaña:${t.name}"
            }
            saved.SaveableStateProvider(key) {
            when {
                o is Overlay.OpenPlaylist -> {
                    val p = state.latest(o.playlist)
                    if (p == null) LaunchedEffect(Unit) { closePlaylist() }
                    else {
                        val list = state.tracksOf(p, tracks)
                        PlaylistScreen(
                            p, list, playback, bottomPadding,
                            onBack = { closePlaylist() },
                            onPlay = { q, i -> state.play(q, i, p.name, p) },
                            onShuffle = player::toggleShuffle, onToast = state::show,
                            onMore = { state.menuFor(it, p) },
                            onRename = if (p.isUser) ({ state.dialog = AppDialog.RenamePlaylist(p) }) else null,
                            onDelete = if (p.isUser) ({ state.dialog = AppDialog.DeletePlaylist(p) }) else null,
                            onSaveAsList = if (!p.isUser) ({ state.dialog = AppDialog.NewPlaylist(list) }) else null,
                            onAddTracks = if (p.isUser) ({ state.playlistSelector = PlaylistSelectorState(p.name, target = p, initialIds = p.trackIds.toSet()) }) else null,
                            focusTrackId = playlistFocusId,
                        )
                    }
                }
                o == Overlay.Settings -> Box(Modifier.statusBarsPadding()) {
                    SettingsScreen(
                        state, lib, onBack = { closePlaylist() }, contentPadding = mobilePadding(bottomPadding),
                        openLibraryRequest = state.settingsLibraryRequests,
                    )
                }
                t == Tab.Playlists -> PlaylistsScreen(
                    tracks, playlists, bottomPadding,
                    onOpenPlaylist = { openPlaylist(it) },
                    onCreatePlaylist = { state.dialog = AppDialog.NewPlaylist() },
                    onPlaylistMenu = { state.dialog = AppDialog.PlaylistMenu(it) },
                    onOpenSettings = openSettings,
                )
                t == Tab.Home -> HomeScreen(
                    lib, playlists, bottomPadding,
                    onPlay = { q, i -> state.play(q, i, "Inicio") },
                    onOpenPlaylist = { openPlaylist(it) },
                    onOpenSettings = openSettings,
                    onCreatePlaylist = { state.dialog = AppDialog.NewPlaylist() },
                    onShowAllLists = { selectTab(Tab.Playlists) },
                    onPlaylistMenu = { state.dialog = AppDialog.PlaylistMenu(it) },
                    lastPlayed = { state.stats.get(it.id).lastMs },
                    userName = userName,
                    onEditName = { state.dialog = AppDialog.EditName },
                    dailyMix = { app.aurora.components.DailyMixCard(state, { openPlaylist(it) }, Modifier.padding(top = 16.dp)) },
                )
                t == Tab.Search -> SearchScreen(tracks, bottomPadding, { q, i -> state.play(q, i, "Búsqueda") }, menu, onOpenSettings = openSettings)
                t == Tab.Library -> LibraryScreen(
                    lib, playback, bottomPadding,
                    onPlay = { q, i -> state.play(q, i, "Tu biblioteca") },
                    onOpenSettings = openSettings,
                    onMore = menu,
                    plays = { state.stats.get(it.id).count },
                )
                else -> VideosScreen(
                    lib, bottomPadding, { q, i -> state.play(q, i, "Videos") },
                    onChooseVideoFolder = { state.requestSettingsLibrary(); openSettings() },
                    onMore = menu,
                    onOpenSettings = openSettings,
                )
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
                if (current != null && state.miniPlayerHiddenFor != current.id) {
                    MiniPlayer(
                        current, playback.isPlaying,
                        progress = app.aurora.components.rememberProgress(playback.totalSec),
                        // Si suena un video, se abre la vista de video (no la de la canción).
                        onOpen = { layer = if (currentIsVideo) Layer.Video else Layer.Player },
                        onToggle = player::togglePlay,
                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                        onOpenVideo = if (playback.hasVideo) ({ layer = Layer.Video }) else null,
                        onDismiss = { state.dismissMiniPlayers() },
                    )
                }
                TabBar(tab.takeIf { overlay == Overlay.None }, { selectTab(it) })
            }
        }

        // Reproductor y video: capa que entra desde abajo (500 ms) encima de la pantalla actual.
        // Minimizar un video de una lista deja a la vista esa lista (con sus siguientes elementos).
        val closeLayer = {
            val fromVideo = layer == Layer.Video
            layer = null
            if (fromVideo && playlistContext != null) openPlaylist(playlistContext, currentId)
        }
        BackHandler(enabled = immersive && !state.videoFullscreen) { closeLayer() }
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
                Layer.Video -> VideoScreen(
                    state, playback, videos,
                    onClose = closeLayer,
                    onShowSong = { state.videoInBackground = true; layer = Layer.Player },
                    playlist = playlistContext,
                )
                null -> Box(Modifier)
            }
        }

        // Mini video flotante: el video sigue en segundo plano mientras navegas.
        androidx.compose.animation.AnimatedVisibility(
            visible = playback.hasVideo && playback.videoEnabled && !immersive && !state.videoFullscreen && state.pipClosedFor != currentId && state.pipHiddenFor != currentId,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = bottomPadding),
            enter = fadeIn() + slideInVertically { it / 3 }, exit = fadeOut(),
        ) {
            app.aurora.components.PipVideo(
                playback, player,
                onExpand = { layer = Layer.Video },
                onClose = { state.pipClosedFor = currentId },
                width = 200.dp,
                // Deslizarlo quita también el mini reproductor (y al revés): van en conjunto.
                onSwipeDismiss = { state.dismissMiniPlayers() },
            )
        }
        if (state.videoFullscreen && playback.hasVideo) {
            // Atrás sale de pantalla completa (no cierra el video).
            BackHandler { state.setFullscreen(false) }
            app.aurora.components.FullscreenVideo(playback, player, onExit = { state.setFullscreen(false) })
        }
        // Selector de lista: capa a pantalla completa; Atrás lo cierra antes que la pantalla de abajo.
        val draft = state.playlistSelector
        if (draft != null) {
            PlaylistSelector(
                name = draft.name,
                tracks = tracks,
                initialIds = draft.initialIds,
                plays = { state.stats.get(it.id).count },
                allowEmpty = draft.target == null,
                onCancel = { state.playlistSelector = null },
                onDone = { selected ->
                    state.playlistSelector = null
                    val target = draft.target
                    if (target != null) {
                        state.latest(target)?.let { state.syncPlaylist(it, selected) }
                    } else {
                        val created = state.createPlaylist(draft.name, selected)
                        state.show(
                            "Lista «${created.name}» creada con ${selected.size} ${if (selected.size == 1) "elemento" else "elementos"}",
                            actionLabel = "Abrir",
                            action = { openPlaylist(created) },
                        )
                    }
                },
            )
        }
        // Diálogos y hojas de opciones: Atrás los cierra antes que todo lo demás.
        BackHandler(enabled = state.dialog != null) { state.dialog = null }
        DialogHost(
            state, tracks,
            onNewPlaylist = { name, initial ->
                state.playlistSelector = PlaylistSelectorState(name, initialIds = initial.map { it.id }.toSet())
            },
        )
        Toast(
            state.toast, Modifier.align(Alignment.TopCenter),
            actionLabel = state.toastActionLabel,
            onAction = {
                state.toast = null
                state.toastAction?.invoke()
                state.toastAction = null
                state.toastActionLabel = null
            },
        )
    }
}
