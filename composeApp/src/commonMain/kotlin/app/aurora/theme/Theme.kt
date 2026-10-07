package app.aurora.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.aurora.color.AuroraPalette
import app.aurora.resources.Res
import app.aurora.resources.archivo
import app.aurora.resources.barlow_medium
import app.aurora.resources.barlow_regular
import app.aurora.resources.barlow_semibold
import app.aurora.resources.bebas_neue
import app.aurora.resources.chakra_petch_semibold
import app.aurora.resources.cormorant_italic
import app.aurora.resources.dm_sans
import app.aurora.resources.fraunces_italic
import app.aurora.resources.inter
import app.aurora.resources.jost
import app.aurora.resources.manrope
import app.aurora.resources.nunito
import app.aurora.resources.syne
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.FontResource

/** Móvil (pantalla de teléfono) o escritorio (PC, ventana ancha). */
enum class FormFactor { MOBILE, DESKTOP }

/** Colores del tema ya animados, más las luces de la canción actual. */
data class LiveColors(
    val background: Color,
    val surface: Color,
    val surfaceBorder: Color,
    val ink: Color,
    val mute: Color,
    val accent: Color,
    val onAccent: Color,
    val playBg: Color,
    val playInk: Color,
    val track: Color,
    val fill: List<Color>,
    val chipOn: Color,
    val chipOnInk: Color,
    val lyricOn: Color,
    val sideBg: Color,
    val barBg: Color,
    val raised: Color,
    val isDark: Boolean,
    /** Luces de la atmósfera por tempo (Aurora y luces del video en todos los temas). */
    val light1: Color,
    val light2: Color,
    val light3: Color,
    /** Multiplicador de duración de animaciones (solo Aurora; 1 en los demás). */
    val speed: Float,
)

data class UiType(
    val h1: TextStyle,
    val h2: TextStyle,
    val playerTitle: TextStyle,
    val panelTitle: TextStyle,
    val nowPlayingTitle: TextStyle,
    val lyric: TextStyle,
    val rowTitle: TextStyle,
    val rowSubtitle: TextStyle,
    val body: TextStyle,
    val caption: TextStyle,
    val label: TextStyle,
    val tab: TextStyle,
    val artist: TextStyle,
)

val LocalLiveColors = compositionLocalOf<LiveColors> { error("Sin tema") }
val LocalUiType = staticCompositionLocalOf<UiType> { error("Sin tema") }
val LocalAppTheme = staticCompositionLocalOf<AppTheme> { error("Sin tema") }
val LocalFormFactor = staticCompositionLocalOf { FormFactor.MOBILE }

/** Preferencias que afectan a cómo se dibuja la interfaz (accesibilidad y apariencia). */
val LocalPrefs = staticCompositionLocalOf { app.aurora.data.AppPrefs() }

/** Acceso corto al tema actual desde cualquier componente. */
object Ui {
    val colors: LiveColors @Composable get() = LocalLiveColors.current
    val type: UiType @Composable get() = LocalUiType.current
    val theme: AppTheme @Composable get() = LocalAppTheme.current
    val shapes: ThemeShapes @Composable get() = LocalAppTheme.current.shapes
    val formFactor: FormFactor @Composable get() = LocalFormFactor.current
    val isDesktop: Boolean @Composable get() = LocalFormFactor.current == FormFactor.DESKTOP
    val prefs: app.aurora.data.AppPrefs @Composable get() = LocalPrefs.current
    /** "Reducir movimiento": sin luces animadas, respiración ni transiciones largas. */
    val reduceMotion: Boolean @Composable get() = LocalPrefs.current.reduceMotion
}

/** Aplica las mayúsculas/minúsculas del tema a un título. */
fun AppTheme.title(text: String): String = when (titleCase) {
    TitleCase.LOWER -> text.lowercase()
    TitleCase.UPPER -> text.uppercase()
    TitleCase.NORMAL -> text
}

/** Aplica las mayúsculas del tema a chips y navegación. */
fun AppTheme.label(text: String): String = if (upperLabels) text.uppercase() else text

@Composable
private fun variable(res: FontResource, weights: List<Int>, style: FontStyle = FontStyle.Normal) = weights.map { w ->
    Font(res, FontWeight(w), style, FontVariation.Settings(FontVariation.weight(w)))
}

@Composable
private fun family(key: FontKey): FontFamily = when (key) {
    FontKey.SYNE -> FontFamily(variable(Res.font.syne, listOf(500, 700, 800)))
    FontKey.DM_SANS -> FontFamily(variable(Res.font.dm_sans, listOf(400, 500, 600, 700)))
    FontKey.ARCHIVO -> FontFamily(variable(Res.font.archivo, listOf(400, 600, 800, 900)))
    FontKey.FRAUNCES_ITALIC -> FontFamily(variable(Res.font.fraunces_italic, listOf(500, 600), FontStyle.Italic))
    FontKey.NUNITO -> FontFamily(variable(Res.font.nunito, listOf(400, 600, 700)))
    FontKey.CORMORANT_ITALIC -> FontFamily(variable(Res.font.cormorant_italic, listOf(500, 600), FontStyle.Italic))
    FontKey.JOST -> FontFamily(variable(Res.font.jost, listOf(400, 500, 600)))
    FontKey.CHAKRA_PETCH -> FontFamily(Font(Res.font.chakra_petch_semibold, FontWeight.SemiBold))
    FontKey.INTER -> FontFamily(variable(Res.font.inter, listOf(400, 500, 600)))
    FontKey.BEBAS_NEUE -> FontFamily(Font(Res.font.bebas_neue, FontWeight.Normal))
    FontKey.BARLOW -> FontFamily(
        Font(Res.font.barlow_regular, FontWeight.Normal),
        Font(Res.font.barlow_medium, FontWeight.Medium),
        Font(Res.font.barlow_semibold, FontWeight.SemiBold),
    )
    FontKey.MANROPE -> FontFamily(variable(Res.font.manrope, listOf(400, 500, 700, 800)))
}

@Composable
private fun uiType(theme: AppTheme, colors: LiveColors, form: FormFactor): UiType {
    val display = family(theme.fonts.display)
    val body = family(theme.fonts.body)
    val s = theme.scale
    val desk = form == FormFactor.DESKTOP
    val italic = if (theme.fonts.italicDisplay) FontStyle.Italic else FontStyle.Normal
    val base = TextStyle(color = colors.ink, fontFamily = body)
    val disp = base.copy(fontFamily = display, fontStyle = italic)
    val dw = FontWeight(theme.fonts.displayWeight)
    val hw = FontWeight(theme.fonts.headingWeight)
    fun big(size: Float, lh: Float) = disp.copy(fontWeight = dw, fontSize = size.sp, lineHeight = (size * lh).sp)
    val h2Size = if (desk) s.h2Desktop else s.h2
    return UiType(
        h1 = big(if (desk) s.h1Desktop else s.h1, s.h1LineHeight).copy(letterSpacing = s.h1LetterSpacing.em),
        h2 = if (theme.headingMarker) {
            base.copy(fontFamily = display, fontWeight = hw, fontSize = h2Size.sp, letterSpacing = .14.em, color = colors.mute)
        } else disp.copy(fontWeight = hw, fontSize = h2Size.sp),
        playerTitle = big(s.playerTitle, s.titleLineHeight),
        panelTitle = big(s.panelTitle, s.titleLineHeight),
        nowPlayingTitle = big(s.nowPlayingTitle, s.titleLineHeight),
        lyric = disp.copy(fontWeight = if (theme.id == ThemeId.POSTER) FontWeight.Black else hw,
            fontSize = (if (desk) s.lyricDesktop else s.lyric).sp,
            lineHeight = ((if (desk) s.lyricDesktop else s.lyric) * 1.18f).sp),
        rowTitle = base.copy(fontWeight = FontWeight.SemiBold, fontSize = if (desk) 14.sp else 15.sp),
        rowSubtitle = base.copy(fontSize = 12.5.sp, color = colors.mute),
        body = base.copy(fontSize = if (desk) 14.sp else 14.5.sp),
        caption = base.copy(fontSize = 12.sp, color = colors.mute, fontFeatureSettings = "tnum"),
        label = base.copy(fontSize = if (theme.upperLabels) 11.5.sp else 13.sp,
            letterSpacing = if (theme.upperLabels) .08.em else 0.em),
        tab = base.copy(fontSize = 11.sp),
        artist = if (theme.accentArtist) {
            base.copy(fontSize = 12.sp, letterSpacing = .18.em, color = colors.accent, fontWeight = FontWeight.SemiBold)
        } else base.copy(fontSize = 15.sp, color = colors.mute),
    )
}

/**
 * Tema de la app. En Aurora el fondo, el énfasis y el relleno del progreso salen de la canción
 * (paleta dinámica, transición de 1,2 s); los demás temas usan colores fijos (transición de 600 ms).
 */
@Composable
fun AppThemeProvider(
    theme: AppTheme, palette: AuroraPalette, formFactor: FormFactor,
    dynamic: Boolean = true, highContrast: Boolean = false,
    /** Duración del cambio de colores; `null` = la de siempre (1,2 s con color de la canción, 600 ms en el resto). */
    fadeMs: Int? = null,
    content: @Composable () -> Unit,
) {
    val c = theme.colors
    val dyn = theme.dynamicColor && dynamic
    val ms = if (Ui.reduceMotion) 0 else fadeMs ?: if (dyn) AuroraMotion.COLOR_FADE_MS else 600
    @Composable fun anim(target: Color) = animateColorAsState(target, tween(ms, easing = AuroraMotion.Ease)).value

    val bg = anim(if (dyn) palette.background else c.background)
    val acc = anim(if (dyn) palette.accent else c.accent)
    val l1 = anim(palette.light1)
    val l2 = anim(palette.light2)
    val l3 = anim(palette.light3)
    val colors = LiveColors(
        background = bg, surface = anim(c.surface), surfaceBorder = anim(if (highContrast) c.ink.copy(alpha = .6f) else c.surfaceBorder),
        ink = anim(c.ink), mute = anim(if (highContrast) c.ink.copy(alpha = .88f) else c.mute), accent = acc,
        onAccent = if (dyn) bg else anim(c.onAccent),
        playBg = anim(c.playBg), playInk = if (dyn) bg else anim(c.playInk),
        track = anim(c.track),
        fill = if (dyn) listOf(l1, acc) else listOf(anim(c.fill.first()), anim(c.fill.last())),
        chipOn = anim(c.chipOn), chipOnInk = anim(c.chipOnInk), lyricOn = anim(c.lyricOn),
        sideBg = anim(c.sideBg), barBg = anim(c.barBg), raised = anim(c.raised), isDark = c.isDark,
        light1 = l1, light2 = l2, light3 = l3,
        speed = if (dyn) palette.mood.speed else 1f,
    )
    val type = uiType(theme, colors, formFactor)
    CompositionLocalProvider(
        LocalAppTheme provides theme,
        LocalFormFactor provides formFactor,
        LocalLiveColors provides colors,
        LocalUiType provides type,
        LocalTextSelectionColors provides remember(acc) { TextSelectionColors(acc, acc.copy(alpha = .3f)) },
        content = content,
    )
}
