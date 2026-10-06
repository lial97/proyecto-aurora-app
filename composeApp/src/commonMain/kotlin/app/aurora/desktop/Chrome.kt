package app.aurora.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aurora.AppDialog
import app.aurora.AppState
import app.aurora.components.VideoSurface
import app.aurora.components.trackContextMenu
import app.aurora.components.Artwork
import app.aurora.components.AuroraIcon
import app.aurora.components.Avatar
import app.aurora.components.Hero
import app.aurora.components.Icon
import app.aurora.components.IconButton
import app.aurora.components.MixSwatch
import app.aurora.components.PlayButton
import app.aurora.components.ProgressBar
import app.aurora.components.TechBadges
import app.aurora.components.formatTime
import app.aurora.components.pressable
import app.aurora.components.surface
import app.aurora.domain.Playlist
import app.aurora.player.PlaybackState
import app.aurora.screens.SearchField
import app.aurora.screens.currentLyric
import app.aurora.screens.heroScale
import app.aurora.theme.DesktopDimens
import app.aurora.theme.ThemeId
import app.aurora.theme.Ui
import app.aurora.theme.label
import app.aurora.theme.title

private data class NavItem(val view: DView, val label: String, val icon: AuroraIcon)

private val navItems = listOf(
    NavItem(DView.Home, "Inicio", AuroraIcon.Home),
    NavItem(DView.Videos, "Videos", AuroraIcon.Video),
    NavItem(DView.Search, "Buscar", AuroraIcon.Search),
    NavItem(DView.Library, "Biblioteca", AuroraIcon.Library),
)

/** Lateral: logo, navegación, tus listas y ajustes. Reducido a íconos (72 dp) en ventanas estrechas. */
@Composable
fun Sidebar(state: AppState, nav: DesktopNav, playlists: List<Playlist>, compact: Boolean) {
    val c = Ui.colors
    val theme = Ui.theme
    Column(
        Modifier.width(if (compact) DesktopDimens.SidebarCompact else DesktopDimens.Sidebar).fillMaxHeight()
            .background(c.sideBg).padding(horizontal = 12.dp, vertical = 18.dp),
    ) {
        Row(Modifier.padding(start = if (compact) 9.dp else 10.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).clip(Ui.shapes.artSmall).background(c.accent), contentAlignment = Alignment.Center) {
                Icon(AuroraIcon.Music, c.onAccent, size = 16.dp)
            }
            if (!compact) BasicText(theme.title("Aurora"), style = Ui.type.h2.copy(fontSize = 20.sp, color = c.ink), modifier = Modifier.padding(start = 10.dp))
        }
        navItems.forEach { item -> NavRow(item.label, item.icon, nav.current == item.view, compact) { nav.go(item.view) } }
        NavRow("Ajustes", AuroraIcon.Settings, nav.current == DView.Settings, compact) { nav.go(DView.Settings) }
        if (!compact) {
            Row(Modifier.padding(start = 10.dp, top = 14.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(theme.label("Tus listas"), style = Ui.type.caption.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                IconButton(AuroraIcon.Plus, "Nueva lista", { state.dialog = AppDialog.NewPlaylist() }, tint = Ui.colors.mute, size = 30.dp, iconSize = 18.dp)
            }
            LazyColumn(Modifier.weight(1f)) {
                items(playlists, key = { it.id }) { p ->
                    Row(
                        Modifier.fillMaxWidth().pressable(p.name, .98f, hoverBg = true) { nav.go(DView.PlaylistView(p)) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MixSwatch(p.colors, 40.dp)
                        Column(Modifier.padding(start = 10.dp)) {
                            BasicText(p.name, style = Ui.type.rowTitle.copy(fontSize = 13.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            BasicText("${p.trackIds.size} canciones", style = Ui.type.caption)
                        }
                    }
                }
            }
        }
    }
}

/** Elemento del menú con el estilo activo de cada tema. */
@Composable
private fun NavRow(label: String, icon: AuroraIcon, active: Boolean, compact: Boolean, onClick: () -> Unit) {
    val c = Ui.colors
    val theme = Ui.theme
    val shape = Ui.shapes.card
    val solid = active && (theme.id == ThemeId.POSTER || theme.id == ThemeId.ESTADIO)
    val bg = when {
        !active -> Color.Transparent
        solid -> c.accent
        theme.id == ThemeId.PETALO -> Color.White
        theme.id == ThemeId.CARBONO -> Color.Transparent
        else -> c.ink.copy(alpha = .08f)
    }
    val ink = when {
        solid -> c.onAccent
        active -> c.ink
        else -> c.mute
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 1.dp)
            .pressable(label, .97f, hoverBg = !active, onClick = onClick)
            .then(if (active && theme.id == ThemeId.PETALO) Modifier.shadow(8.dp, shape, ambientColor = Color(0x224A2B3A), spotColor = Color(0x224A2B3A)) else Modifier)
            .clip(shape).background(bg)
            .drawBehind { if (active && theme.id == ThemeId.CARBONO) drawRect(c.accent, size = Size(3.dp.toPx(), size.height)) }
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (compact) Arrangement.Center else Arrangement.Start,
    ) {
        Icon(icon, if (active && !solid) c.accent else ink, size = 20.dp)
        if (!compact) BasicText(theme.label(label), style = Ui.type.body.copy(color = ink, fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(start = 12.dp))
    }
}

/** Atrás / Adelante, búsqueda y acceso a Ajustes. */
@Composable
fun TopBar(nav: DesktopNav, onOpenSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 14.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(AuroraIcon.Back, "Atrás", nav::back, tint = if (nav.canBack) Ui.colors.ink else Ui.colors.mute)
        IconButton(AuroraIcon.Forward, "Adelante", nav::forward, tint = if (nav.canForward) Ui.colors.ink else Ui.colors.mute)
        SearchField(nav.query, { q -> nav.query = q; if (q.isNotBlank()) nav.go(DView.Search) }, Modifier.padding(start = 8.dp).widthIn(max = 380.dp).weight(1f, fill = false),
            fieldModifier = Modifier.focusRequester(nav.searchFocus))
        Spacer(Modifier.weight(1f))
        IconButton(AuroraIcon.Settings, "Ajustes", onOpenSettings)
    }
}

/** Barra de reproducción fija (86 dp): portada y título · controles y progreso · letra, panel y volumen. */
@Composable
fun PlayerBar(state: AppState, playback: PlaybackState, nav: DesktopNav, onTogglePanel: () -> Unit, panelVisible: Boolean) {
    val c = Ui.colors
    val type = Ui.type
    val player = state.player
    val track = playback.current
    val duration = playback.durationSec.takeIf { it > 0 } ?: track?.durationSec ?: 0
    Row(
        Modifier.fillMaxWidth().height(DesktopDimens.PlayerBar).background(c.background).background(c.barBg).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(Modifier.width(300.dp).trackContextMenu(track).pressable("Abrir Reproduciendo", .99f) { if (track != null) nav.go(DView.NowPlaying) }, verticalAlignment = Alignment.CenterVertically) {
            Artwork(track, 56.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                BasicText(track?.title ?: "Nada sonando", style = type.rowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    // "Anunciar canciones": los lectores de pantalla leen el cambio de canción.
                    modifier = if (Ui.prefs.announce) Modifier.semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite; contentDescription = "Sonando: ${track?.title ?: "nada"}, de ${track?.artist ?: ""}" } else Modifier)
                BasicText(track?.artist ?: "Elige una canción", style = type.rowSubtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (track != null) {
                val liked = track.id in state.liked
                IconButton(if (liked) AuroraIcon.HeartFilled else AuroraIcon.Heart, "Me gusta", { state.toggleLike(track) }, tint = if (liked) c.accent else c.ink)
                IconButton(AuroraIcon.ListAdd, "Añadir a una lista", { state.dialog = AppDialog.AddToPlaylist(listOf(track)) })
            }
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconButton(AuroraIcon.Shuffle, "Aleatorio", player::toggleShuffle, tint = if (playback.shuffle) c.accent else c.ink)
                IconButton(AuroraIcon.Previous, "Anterior", player::previous)
                PlayButton(playback.isPlaying, 42.dp, player::togglePlay)
                IconButton(AuroraIcon.Next, "Siguiente", player::next)
                IconButton(AuroraIcon.Repeat, "Repetir", player::toggleRepeat, tint = if (playback.repeatOne) c.accent else c.ink)
            }
            Row(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicText(formatTime(playback.positionSec), style = type.caption, modifier = Modifier.width(40.dp), maxLines = 1)
                ProgressBar(playback.positionSec, duration, player::seekTo, Modifier.weight(1f), barHeight = if (Ui.theme.id == ThemeId.POSTER) 8.dp else if (Ui.theme.id == ThemeId.SEDA) 2.dp else 4.dp)
                BasicText(formatTime(duration), style = type.caption.copy(textAlign = TextAlign.End), modifier = Modifier.width(40.dp), maxLines = 1)
            }
        }
        Row(Modifier.width(300.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            IconButton(AuroraIcon.Lyrics, "Letra (L)", nav::toggleNowPlaying, tint = if (nav.current == DView.NowPlaying) c.accent else c.ink)
            IconButton(AuroraIcon.Video, "Video (V)", { nav.go(if (playback.hasVideo) DView.Video else DView.Videos) },
                tint = if (nav.current == DView.Video) c.accent else c.ink)
            IconButton(AuroraIcon.Panel, "Mostrar u ocultar panel", onTogglePanel, tint = if (panelVisible) c.accent else c.ink)
            IconButton(AuroraIcon.Volume, "Volumen", { state.setVolume(if (playback.volume > 0f) 0f else .8f) })
            VolumeSlider(playback.volume, state::setVolume)
        }
    }
}

@Composable
private fun VolumeSlider(volume: Float, onChange: (Float) -> Unit) {
    val c = Ui.colors
    Box(
        Modifier.width(96.dp).height(18.dp)
            .pointerInput(Unit) { detectTapGestures { onChange(it.x / size.width) } }
            .pointerInput(Unit) { detectHorizontalDragGestures { ch, _ -> onChange((ch.position.x / size.width).coerceIn(0f, 1f)) } }
            .drawBehind {
                val h = 4.dp.toPx()
                val y = size.height / 2 - h / 2
                drawRoundRect(c.track, Offset(0f, y), Size(size.width, h), CornerRadius(h))
                drawRoundRect(c.ink, Offset(0f, y), Size(size.width * volume, h), CornerRadius(h))
            },
    )
}

/** Panel derecho "Reproduciendo": portada, título, letra actual y lo que sigue. */
@Composable
fun RightPanel(
    state: AppState, playback: PlaybackState, nav: DesktopNav, onTogglePanel: () -> Unit,
    modifier: Modifier = Modifier, videos: List<app.aurora.domain.Track> = emptyList(),
    width: androidx.compose.ui.unit.Dp = DesktopDimens.RightPanel,
    /** Ventana alta: el recuadro de letra crece. */
    tall: Boolean = false,
) {
    val c = Ui.colors
    val theme = Ui.theme
    val track = playback.current
    val centered = theme.centeredPlayerText
    if (nav.current == DView.Video) {
        MoreVideos(state, playback, videos, onTogglePanel, modifier.width(width))
        return
    }
    val k = width / DesktopDimens.RightPanel
    Column(modifier.width(width).fillMaxHeight().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(theme.label("Reproduciendo"), style = Ui.type.rowTitle.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
            IconButton(AuroraIcon.Panel, "Ocultar panel", onTogglePanel)
        }
        val heroSize = when (theme.id) {
            ThemeId.SEDA -> 220.dp
            ThemeId.PETALO -> 210.dp
            ThemeId.ESTADIO -> 218.dp
            else -> 252.dp - 6.dp
        }.let { minOf(it * (if (k > 1.15f) 1.1f else 1f), width - 50.dp) }
        if (playback.hasVideo) {
            Column(Modifier.pressable("Abrir video", .99f) { nav.go(DView.Video) }) {
                VideoSurface(playback, state.player, controls = false)
            }
        } else {
            Box(Modifier.fillMaxWidth(), contentAlignment = if (theme.leftAlignedHero) Alignment.CenterStart else Alignment.Center) {
                Hero(track, heroSize, scale = heroScale(playback.isPlaying))
            }
        }
        if (track == null) {
            BasicText("Elige una canción para empezar", style = Ui.type.rowSubtitle, modifier = Modifier.padding(top = 16.dp))
            return@Column
        }
        Box(Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Column(
                Modifier.fillMaxWidth().padding(end = if (centered) 0.dp else 40.dp).padding(horizontal = if (centered) 34.dp else 0.dp),
                horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
            ) {
                BasicText(theme.title(track.title), style = Ui.type.panelTitle.copy(textAlign = if (centered) TextAlign.Center else TextAlign.Start), maxLines = 2, overflow = TextOverflow.Ellipsis)
                BasicText(if (theme.accentArtist) track.artist.uppercase() else track.artist, style = Ui.type.artist.copy(fontSize = if (theme.accentArtist) 11.5.sp else 14.sp),
                    modifier = Modifier.padding(top = 3.dp), maxLines = 1)
                if (theme.showTechBadges) TechBadges(track.bpm, track.format ?: "FLAC 24 bit", track.bitrateKbps)
            }
            val liked = track.id in state.liked
            IconButton(if (liked) AuroraIcon.HeartFilled else AuroraIcon.Heart, "Me gusta", { state.toggleLike(track) },
                Modifier.align(Alignment.TopEnd), tint = if (liked) c.accent else c.ink)
        }
        // Letra grande con karaoke: aprovecha el alto del panel de escritorio.
        Column(
            Modifier.padding(top = 16.dp).fillMaxWidth().pressable("Abrir letra", .99f) { nav.go(DView.NowPlaying) }
                .surface().padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(theme.label("Letra"), style = Ui.type.caption.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                Icon(AuroraIcon.Expand, c.mute, size = 14.dp)
            }
            app.aurora.screens.MiniKaraoke(
                state.lyrics, playback,
                Modifier.padding(top = 10.dp).fillMaxWidth().height(if (tall) 300.dp else 220.dp),
                visibleAfter = if (tall) 5 else 3, textScale = minOf(k, 1.25f),
            )
        }
        BasicText(theme.title("A continuación"), style = Ui.type.h2.copy(fontSize = 16.sp), modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
        val q = playback.queue
        if (q.size > 1) {
            (1..minOf(4, q.size - 1)).map { (playback.index + it) % q.size }.forEach { i ->
                val t = q[i]
                Row(
                    Modifier.fillMaxWidth().trackContextMenu(t).pressable(t.title, .98f, hoverBg = true) { state.play(q, i) }.padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(t, 40.dp)
                    Column(Modifier.padding(start = 10.dp)) {
                        BasicText(t.title, style = Ui.type.rowTitle.copy(fontSize = 13.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        BasicText(t.artist, style = Ui.type.caption, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Panel "Más videos" del modo cine. La miniatura usa la portada si el video no trae imagen propia. */
@Composable
private fun MoreVideos(state: AppState, playback: PlaybackState, videos: List<app.aurora.domain.Track>, onTogglePanel: () -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(Ui.theme.label("Más videos"), style = Ui.type.rowTitle.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
            IconButton(AuroraIcon.Panel, "Ocultar panel", onTogglePanel)
        }
        videos.forEachIndexed { i, v ->
            val cur = v.id == playback.current?.id
            Row(
                Modifier.fillMaxWidth().trackContextMenu(v).pressable(v.title, .98f, hoverBg = true) { state.play(videos, i, "Videos") }.padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(120.dp)) { app.aurora.components.VideoThumb(v) }
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    BasicText(v.title, style = Ui.type.rowTitle.copy(fontSize = 13.5.sp, color = if (cur) Ui.colors.accent else Ui.colors.ink), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    BasicText(v.artist, style = Ui.type.caption, maxLines = 1)
                }
                IconButton(AuroraIcon.More, "Opciones de ${v.title}", { state.dialog = AppDialog.TrackMenu(v) }, tint = Ui.colors.mute, size = 28.dp, iconSize = 16.dp)
            }
        }
    }
}
