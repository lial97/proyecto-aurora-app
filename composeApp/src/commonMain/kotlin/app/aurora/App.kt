package app.aurora

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aurora.color.Hsl
import app.aurora.color.buildPalette
import app.aurora.components.LocalCoverCache
import app.aurora.components.accentOf
import app.aurora.desktop.DesktopApp
import app.aurora.platform.PlatformServices
import app.aurora.platform.createPlatformServices
import app.aurora.theme.AppThemeProvider
import app.aurora.theme.FormFactor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Decide la interfaz según el dispositivo:
 * - PC: interfaz de escritorio, salvo que la ventana sea más estrecha que [FormFactorRules.MOBILE_MAX_WIDTH].
 * - Móvil: interfaz de teléfono, salvo pantallas grandes en horizontal (tabletas).
 */
object FormFactorRules {
    /** Una ventana de PC más estrecha que esto usa el diseño de teléfono. */
    val MOBILE_MAX_WIDTH = 720.dp
    /** Una pantalla móvil al menos así de ancha (tableta en horizontal) usa el diseño de escritorio. */
    val TABLET_DESKTOP_WIDTH = 1000.dp

    fun decide(isDesktopPlatform: Boolean, width: Dp, height: Dp): FormFactor = when {
        isDesktopPlatform && width >= MOBILE_MAX_WIDTH -> FormFactor.DESKTOP
        !isDesktopPlatform && width >= TABLET_DESKTOP_WIDTH && height >= 600.dp -> FormFactor.DESKTOP
        else -> FormFactor.MOBILE
    }
}

@Composable
fun App(
    platform: PlatformServices? = null,
    /** Estado creado fuera de la pantalla (Android: vive con el proceso y el servicio de reproducción). */
    external: AppState? = null,
    onState: (AppState) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val state = remember { (external ?: AppState(scope, platform ?: createPlatformServices())).also(onState) }
    // Solo se libera el reproductor si el estado es de esta pantalla (en Android sigue sonando).
    DisposableEffect(Unit) { onDispose { if (external == null) state.player.release() } }

    val theme by state.settings.theme.collectAsState()
    val playback by state.player.state.collectAsState()
    val lib by state.library.state.collectAsState()
    LaunchedEffect(state.toast) { if (state.toast != null) { delay(2200); state.toast = null } }
    LaunchedEffect(playback.error) { playback.error?.let { state.show(it) } }

    // Énfasis de la portada actual, calculado una vez por pista fuera del hilo principal.
    val current = playback.current
    val accent by produceState<Hsl?>(null, current?.id) {
        value = when {
            current == null -> null
            current.coverRecipe != null -> accentOf(current.coverRecipe)
            current.coverUri != null -> state.covers.get(current)?.let { img -> withContext(Dispatchers.Default) { accentOf(img) } }
            else -> null
        }
    }
    val palette = remember(current?.bpm, accent) { buildPalette(current?.bpm, accent) }

    BoxWithConstraints {
        val form = FormFactorRules.decide(state.platform.isDesktop, maxWidth, maxHeight)
        val prefs by state.prefsRepo.prefs.collectAsState()
        val base = androidx.compose.ui.platform.LocalDensity.current
        // Tamaño del texto (90-150 %) y densidad compacta (todo un 10 % más chico).
        val density = androidx.compose.ui.unit.Density(
            base.density * (if (prefs.compact) .9f else 1f),
            base.fontScale * prefs.textScale,
        )
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalDensity provides density,
            app.aurora.theme.LocalPrefs provides prefs,
        ) {
        val themed: @Composable (app.aurora.theme.AppTheme, @Composable () -> Unit) -> Unit = { t, content ->
            AppThemeProvider(t, palette, form, dynamic = prefs.dynamicColor, highContrast = prefs.highContrast) {
                CompositionLocalProvider(
                    LocalCoverCache provides state.covers,
                    app.aurora.components.LocalPreviewCache provides state.previews,
                    app.aurora.components.LocalTrackMenu provides { tr, p, anchor -> state.dialog = AppDialog.TrackMenu(tr, p?.takeIf { it.isUser }, anchor) },
                ) { content() }
            }
        }
        if (state.onboarding) {
            // Bienvenida: al elegir un tema, toda la pantalla pasa a ese tema en 600 ms.
            val model = remember { app.aurora.screens.OnboardingModel(state) }
            androidx.compose.animation.Crossfade(theme, animationSpec = androidx.compose.animation.core.tween(if (prefs.reduceMotion) 0 else 600)) { t ->
                themed(t) {
                    app.aurora.components.Backdrop(playing = false, intensity = .55f) { app.aurora.screens.OnboardingScreen(state, model) }
                }
            }
        } else themed(theme) {
            if (form == FormFactor.DESKTOP) DesktopApp(state, lib, playback) else MobileApp(state, lib, playback)
        }
        }
    }
}
