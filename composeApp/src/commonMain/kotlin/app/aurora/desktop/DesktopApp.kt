package app.aurora.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aurora.AppState
import app.aurora.components.Backdrop
import app.aurora.components.Toast
import app.aurora.components.TypingTracker
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.isSecondaryPressed
import app.aurora.components.DialogHost
import app.aurora.components.VideoSurface
import app.aurora.components.PipVideo
import app.aurora.data.LibraryState
import app.aurora.domain.Playlist
import app.aurora.player.PlaybackState
import app.aurora.screens.SettingsScreen
import app.aurora.theme.AuroraMotion
import app.aurora.theme.DesktopDimens
import app.aurora.theme.Ui

/** Vistas del contenido central de escritorio. */
sealed interface DView {
    data object Home : DView
    data object Videos : DView
    data object Search : DView
    data object Library : DView
    data class PlaylistView(val playlist: Playlist) : DView
    data object NowPlaying : DView
    data object Video : DView
    data object Settings : DView
}

/** Navegación con historial (botones Atrás y Adelante). */
class DesktopNav {
    private val back = mutableStateListOf<DView>()
    private val forward = mutableStateListOf<DView>()
    var current by mutableStateOf<DView>(DView.Home)
        private set
    var query by mutableStateOf("")
    /** Ctrl+K pone el cursor en el buscador. */
    val searchFocus = FocusRequester()

    val canBack get() = back.isNotEmpty()
    val canForward get() = forward.isNotEmpty()

    fun go(v: DView) {
        if (v == current) return
        back += current
        forward.clear()
        current = v
    }

    fun back() {
        if (back.isEmpty()) return
        forward += current
        current = back.removeAt(back.lastIndex)
    }

    fun forward() {
        if (forward.isEmpty()) return
        back += current
        current = forward.removeAt(forward.lastIndex)
    }

    /** Si lo que suena es un video y el usuario no eligió "Canción" (lo pone [DesktopApp]). */
    var showVideo: () -> Boolean = { false }

    /** Entrar al reproductor: la vista de video si suena un video, si no la de la canción. */
    fun openPlayer() = go(if (showVideo()) DView.Video else DView.NowPlaying)

    fun toggleNowPlaying() = if (current == DView.NowPlaying || current == DView.Video) back() else openPlayer()
}

/** Separador entre columnas: 3 dp del color del texto en Póster, línea fina en los demás. */
@Composable
fun separatorColor() = if (Ui.shapes.borderWidth > 0.dp) Ui.colors.ink else Ui.colors.ink.copy(alpha = .08f)

@Composable
fun separatorWidth(): Dp = if (Ui.shapes.borderWidth > 0.dp) 3.dp else 1.dp

/**
 * Interfaz de escritorio (spec de temas §9):
 * - 1280 dp o más: lateral, contenido y panel derecho fijo.
 * - 1024 a 1279: el panel derecho flota sobre el contenido y se abre con su botón.
 * - menos de 1024: el lateral se reduce a íconos.
 */
@Composable
fun DesktopApp(state: AppState, lib: LibraryState, playback: PlaybackState) {
    val nav = remember { DesktopNav() }
    var panelWanted by remember { mutableStateOf(true) }
    var floatingOpen by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val player = state.player
    val tracks = lib.tracks
    val playlists = remember(tracks, lib.usingSamples, state.liked, state.userPlaylists) { state.playlists(tracks, lib.usingSamples) }
    val nowPlaying = nav.current == DView.NowPlaying
    // Al empezar un video se abre el modo cine.
    LaunchedEffect(state.videoRequests) { if (state.videoRequests > 0) { state.videoInBackground = false; nav.go(DView.Video) } }
    nav.showVideo = { playback.current?.mediaType == app.aurora.domain.MediaType.VIDEO && !state.videoInBackground }
    // Elegir "Canción" vale mientras se está en el reproductor: al volver a entrar, un video se ve como video.
    LaunchedEffect(nav.current) { if (nav.current != DView.NowPlaying && nav.current != DView.Video) state.videoInBackground = false }
    // Cambio automático: si el reproductor está abierto y la cola llega a un video, se muestra el video;
    // si llega a una canción, se vuelve a portada y letra. Fuera del reproductor aparece el mini video.
    val currentId = playback.current?.id
    val currentIsVideo = playback.current?.mediaType == app.aurora.domain.MediaType.VIDEO
    LaunchedEffect(currentId) {
        if (currentIsVideo && nav.current == DView.NowPlaying && !state.videoInBackground) nav.go(DView.Video)
        if (!currentIsVideo && nav.current == DView.Video) nav.go(DView.NowPlaying)
    }

    val focusManager = LocalFocusManager.current
    BoxWithConstraints(
        Modifier.fillMaxSize()
            .focusRequester(focus)
            .focusable()
            // Un clic fuera de un campo de texto le quita el foco: así vuelven a funcionar los atajos.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        if (e.type == PointerEventType.Press && TypingTracker.focused > 0) {
                            focusManager.clearFocus()
                            runCatching { focus.requestFocus() }
                        }
                        // Menú contextual abierto y clic fuera de él: se cierra. Con clic izquierdo el clic no
                        // llega a lo de abajo (como en el sistema); con clic derecho sí, y abre el menú de esa pista.
                        val menu = state.dialog as? app.aurora.AppDialog.TrackMenu
                        if (e.type == PointerEventType.Press && menu?.anchor != null) {
                            val p = e.changes.first().position
                            val inside = state.contextMenuBounds?.contains(p) == true
                            if (!inside) {
                                state.dialog = null
                                state.contextMenuBounds = null
                                if (!e.buttons.isSecondaryPressed) e.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
            }
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.Escape && state.dialog != null) { state.dialog = null; return@onKeyEvent true }
                if (e.type != KeyEventType.KeyDown || state.dialog != null || TypingTracker.focused > 0) return@onKeyEvent false
                when {
                    e.isCtrlPressed && e.key == Key.Comma -> { nav.go(DView.Settings); true }
                    e.isCtrlPressed && e.key == Key.K -> { runCatching { nav.searchFocus.requestFocus() }; true }
                    e.key == Key.Escape && state.videoFullscreen -> { state.setFullscreen(false); true }
                    e.key == Key.F -> { if (playback.hasVideo) state.setFullscreen(!state.videoFullscreen) else state.show("Pon un video para usar pantalla completa"); true }
                    e.key == Key.Spacebar -> { player.togglePlay(); true }
                    e.key == Key.DirectionRight && e.isCtrlPressed -> { player.next(); true }
                    e.key == Key.DirectionLeft && e.isCtrlPressed -> { player.previous(); true }
                    e.key == Key.DirectionRight -> { player.seekBy(5); true }
                    e.key == Key.DirectionLeft -> { player.seekBy(-5); true }
                    e.key == Key.L -> { nav.toggleNowPlaying(); true }
                    e.key == Key.V -> { nav.go(if (playback.hasVideo) DView.Video else DView.Videos); true }
                    else -> false
                }
            },
    ) {
        val full = maxWidth >= DesktopDimens.FullLayout
        val compact = maxWidth < DesktopDimens.CompactSidebar
        // En Ajustes el panel derecho se oculta para ganar espacio.
        val docked = full && panelWanted && !nowPlaying && nav.current != DView.Settings
        // En pantallas grandes el panel crece para que la letra se lea mejor.
        val panelWidth = when {
            maxWidth >= 1700.dp -> 400.dp
            maxWidth >= 1440.dp -> 350.dp
            else -> DesktopDimens.RightPanel
        }
        val tall = maxHeight >= 900.dp
        val videos = remember(tracks) { tracks.filter { it.mediaType == app.aurora.domain.MediaType.VIDEO } }
        val togglePanel = { if (full) panelWanted = !panelWanted else floatingOpen = !floatingOpen }

        Backdrop(playing = playback.isPlaying, intensity = if (nowPlaying) 1f else .55f) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    Sidebar(state, nav, playlists, compact)
                    Box(Modifier.width(separatorWidth()).fillMaxHeight().background(separatorColor()))
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        Column(Modifier.fillMaxSize()) {
                            TopBar(nav)
                            AnimatedContent(
                                targetState = nav.current,
                                transitionSpec = {
                                    (fadeIn(tween(400, easing = AuroraMotion.Ease)) + slideInVertically(tween(400)) { 24 })
                                        .togetherWith(fadeOut(tween(150)))
                                },
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                            ) { view ->
                                when (view) {
                                    DView.Home -> DesktopHome(state, lib, playlists, playback, nav)
                                    DView.Videos -> DesktopVideos(state, lib)
                                    DView.Search -> DesktopSearch(state, tracks, playback, nav.query)
                                    DView.Library -> DesktopLibrary(state, lib, playlists, playback, nav)
                                    is DView.PlaylistView -> DesktopPlaylist(state, view.playlist, state.tracksOf(view.playlist, tracks), playback, onDeleted = nav::back)
                                    DView.Video -> DesktopVideo(state, playback, onShowSong = { state.videoInBackground = true; nav.go(DView.NowPlaying) })
                                    DView.NowPlaying -> DesktopNowPlaying(state, playback, tracks, onShowVideo = { state.videoInBackground = false; nav.go(DView.Video) }, onOpenSettings = { nav.go(DView.Settings) })
                                    DView.Settings -> SettingsScreen(state, lib, onBack = null, contentPadding = desktopPadding())
                                }
                            }
                        }
                        // Mini video flotante: el video sigue en segundo plano mientras navegas.
                        val showPip = playback.hasVideo && playback.videoEnabled && !docked && !state.videoFullscreen &&
                            nav.current != DView.Video && nav.current != DView.NowPlaying && state.pipClosedFor != currentId
                        androidx.compose.animation.AnimatedVisibility(
                            visible = showPip, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                            enter = fadeIn() + slideInVertically { it / 3 }, exit = fadeOut(),
                        ) {
                            PipVideo(playback, player, onExpand = { nav.go(DView.Video) }, onClose = { state.pipClosedFor = currentId })
                        }
                        // Panel flotante (ventanas de 1024 a 1279 dp, o menos).
                        androidx.compose.animation.AnimatedVisibility(
                            visible = !full && floatingOpen && !nowPlaying,
                            modifier = Modifier.align(Alignment.CenterEnd),
                            enter = slideInHorizontally(tween(300)) { it } + fadeIn(),
                            exit = slideOutHorizontally(tween(250)) { it } + androidx.compose.animation.fadeOut(),
                        ) {
                            RightPanel(
                                state, playback, nav, togglePanel,
                                Modifier.padding(10.dp).shadow(24.dp, Ui.shapes.card).background(Ui.colors.background, Ui.shapes.card), videos,
                            )
                        }
                    }
                    if (docked) {
                        Box(Modifier.width(separatorWidth()).fillMaxHeight().background(separatorColor()))
                        RightPanel(state, playback, nav, togglePanel, Modifier.background(Ui.colors.sideBg), videos, panelWidth, tall)
                    }
                }
                Box(Modifier.fillMaxWidth().height(separatorWidth()).background(separatorColor()))
                PlayerBar(state, playback, nav, togglePanel, panelVisible = if (full) docked else floatingOpen,
                    panelAvailable = !nowPlaying && nav.current != DView.Settings)
            }
            // Pantalla completa: el video ocupa toda la ventana (Esc o F para salir).
            if (state.videoFullscreen && playback.hasVideo) {
                VideoSurface(playback, player, fullscreen = true, onFullscreen = { state.setFullscreen(false) })
            }
            DialogHost(state, tracks)
            Toast(state.toast, Modifier.align(Alignment.BottomCenter))
        }
    }
}

fun desktopPadding() = androidx.compose.foundation.layout.PaddingValues(
    start = DesktopDimens.ContentPadding, end = DesktopDimens.ContentPadding, top = 4.dp, bottom = 30.dp,
)
