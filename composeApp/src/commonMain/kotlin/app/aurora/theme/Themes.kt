package app.aurora.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp

/** Definición de los 7 temas. Valores de files/TEMAS_VIDEO_ESCRITORIO.md §4 y del CSS de los prototipos. */
object Themes {
    private val White = Color.White
    private val Pill = RoundedCornerShape(50)

    val aurora = AppTheme(
        id = ThemeId.AURORA, displayName = "Aurora",
        description = "Cambia con cada canción",
        colors = ThemeColors(
            background = Color(0xFF120E2B), surface = White.copy(alpha = .10f), surfaceBorder = White.copy(alpha = .18f),
            ink = Color(0xFFF2EEFF), mute = Color(0xFFF2EEFF).copy(alpha = .62f),
            accent = Color(0xFFFF5FA2), onAccent = Color(0xFF120E2B),
            playBg = Color(0xFFF2EEFF), playInk = Color(0xFF120E2B), track = White.copy(alpha = .20f),
            fill = listOf(Color(0xFFFF5FA2), Color(0xFFFF5FA2)), // en vivo: luz 1 -> énfasis
            chipOn = Color(0xFFF2EEFF), chipOnInk = Color(0xFF120E2B), lyricOn = Color(0xFFF2EEFF),
            sideBg = White.copy(alpha = .035f), barBg = White.copy(alpha = .07f), raised = White.copy(alpha = .10f),
            isDark = true,
        ),
        shapes = ThemeShapes(RoundedCornerShape(16.dp), RoundedCornerShape(26.dp), RoundedCornerShape(11.dp), CircleShape, Pill, 0.dp),
        fonts = ThemeFonts(FontKey.SYNE, FontKey.DM_SANS, displayWeight = 800, headingWeight = 700),
        scale = TypeScale(h1 = 30f, h1Desktop = 40f, h2 = 18f, h2Desktop = 20f, playerTitle = 24f, panelTitle = 22f,
            nowPlayingTitle = 34f, lyric = 24f, lyricDesktop = 34f),
        titleCase = TitleCase.NORMAL, upperLabels = false, accentArtist = false,
        hero = HeroStyle.GLOW, backdrop = BackdropStyle.AURORA_LIGHTS, playerPanel = PlayerPanel.GLASS_OVERLAP,
        showTechBadges = false, centeredPlayerText = false, leftAlignedHero = false, dynamicColor = true, progressHeight = 5f,
    )

    private val posterBlue = Color(0xFF1F2BFF)
    private val posterBg = Color(0xFFEDEBFF)
    val poster = AppTheme(
        id = ThemeId.POSTER, displayName = "Póster",
        description = "Gráfico y directo",
        colors = ThemeColors(
            background = posterBg, surface = Color.Transparent, surfaceBorder = posterBlue,
            ink = posterBlue, mute = posterBlue.copy(alpha = .68f), accent = posterBlue, onAccent = posterBg,
            playBg = posterBlue, playInk = posterBg, track = Color(0xFFD3D0FF), fill = listOf(posterBlue, posterBlue),
            chipOn = posterBlue, chipOnInk = posterBg, lyricOn = posterBlue,
            sideBg = posterBg, barBg = posterBg, raised = posterBg, isDark = false,
        ),
        shapes = ThemeShapes(RectangleShape, RectangleShape, RectangleShape, RectangleShape, RectangleShape, 3.dp),
        fonts = ThemeFonts(FontKey.ARCHIVO, FontKey.ARCHIVO, displayWeight = 900, headingWeight = 900),
        scale = TypeScale(h1 = 46f, h1Desktop = 68f, h2 = 20f, h2Desktop = 20f, playerTitle = 52f, panelTitle = 34f,
            nowPlayingTitle = 60f, lyric = 30f, lyricDesktop = 44f, h1LineHeight = .88f, titleLineHeight = .86f, h1LetterSpacing = -.05f),
        titleCase = TitleCase.LOWER, upperLabels = false, accentArtist = false,
        hero = HeroStyle.THICK_BORDER, backdrop = BackdropStyle.SPINNING_DOT, playerPanel = PlayerPanel.PLAIN,
        showTechBadges = false, centeredPlayerText = false, leftAlignedHero = true, dynamicColor = false, progressHeight = 12f,
    )

    private val petaloPink = Color(0xFFE0567A)
    val petalo = AppTheme(
        id = ThemeId.PETALO, displayName = "Pétalo",
        description = "Suave, rosas y lilas",
        colors = ThemeColors(
            background = Color(0xFFFBEEF1), surface = White, surfaceBorder = Color(0xFFF4D9E1),
            ink = Color(0xFF4A2B3A), mute = Color(0xFF9A7383), accent = petaloPink, onAccent = White,
            playBg = petaloPink, playInk = White, track = Color(0xFFF4D9E1), fill = listOf(Color(0xFFB9A3E3), petaloPink),
            chipOn = petaloPink, chipOnInk = White, lyricOn = petaloPink,
            sideBg = White.copy(alpha = .55f), barBg = White, raised = White, isDark = false,
        ),
        shapes = ThemeShapes(RoundedCornerShape(22.dp), ArchShape, ArchShapeSmall, CircleShape, Pill, 0.dp),
        fonts = ThemeFonts(FontKey.FRAUNCES_ITALIC, FontKey.NUNITO, displayWeight = 600, headingWeight = 600, italicDisplay = true),
        scale = TypeScale(h1 = 32f, h1Desktop = 44f, h2 = 18f, h2Desktop = 20f, playerTitle = 25f, panelTitle = 22f,
            nowPlayingTitle = 34f, lyric = 24f, lyricDesktop = 34f, h1LetterSpacing = 0f),
        titleCase = TitleCase.NORMAL, upperLabels = false, accentArtist = false,
        hero = HeroStyle.WHITE_FRAME, backdrop = BackdropStyle.PASTEL_BLOBS, playerPanel = PlayerPanel.WHITE_OVERLAP,
        showTechBadges = false, centeredPlayerText = false, leftAlignedHero = false, dynamicColor = false, progressHeight = 5f,
    )

    private val gold = Color(0xFFE8C9A0)
    private val plum = Color(0xFF1E0F18)
    val seda = AppTheme(
        id = ThemeId.SEDA, displayName = "Seda",
        description = "Ciruela y dorado",
        colors = ThemeColors(
            background = plum, surface = gold.copy(alpha = .06f), surfaceBorder = gold.copy(alpha = .32f),
            ink = Color(0xFFF6E9DD), mute = Color(0xFFF6E9DD).copy(alpha = .6f), accent = gold, onAccent = plum,
            playBg = gold, playInk = plum, track = gold.copy(alpha = .18f), fill = listOf(Color(0xFFF08BA8), gold),
            chipOn = gold, chipOnInk = plum, lyricOn = Color(0xFFF6E9DD),
            sideBg = Color(0xFF1A0D15), barBg = Color(0xFF24131D), raised = Color(0xFF2A1621), isDark = true,
        ),
        shapes = ThemeShapes(RoundedCornerShape(14.dp), CircleShape, CircleShape, CircleShape, RoundedCornerShape(2.dp), 0.dp),
        fonts = ThemeFonts(FontKey.CORMORANT_ITALIC, FontKey.JOST, displayWeight = 600, headingWeight = 600, italicDisplay = true),
        scale = TypeScale(h1 = 36f, h1Desktop = 52f, h2 = 22f, h2Desktop = 24f, playerTitle = 32f, panelTitle = 28f,
            nowPlayingTitle = 46f, lyric = 29f, lyricDesktop = 40f, h1LetterSpacing = 0f),
        titleCase = TitleCase.NORMAL, upperLabels = false, accentArtist = true,
        hero = HeroStyle.GOLD_RINGS, backdrop = BackdropStyle.GOLD_CIRCLES, playerPanel = PlayerPanel.PLAIN,
        showTechBadges = false, centeredPlayerText = true, leftAlignedHero = false, dynamicColor = false, progressHeight = 2f,
    )

    private val orange = Color(0xFFFF7A1A)
    val carbono = AppTheme(
        id = ThemeId.CARBONO, displayName = "Carbono",
        description = "Técnico y preciso",
        colors = ThemeColors(
            background = Color(0xFF121417), surface = Color(0xFF1B1E22), surfaceBorder = Color(0xFF2C3138),
            ink = Color(0xFFE3E7EC), mute = Color(0xFF8A929C), accent = orange, onAccent = Color(0xFF121417),
            playBg = orange, playInk = Color(0xFF121417), track = Color(0xFF2C3138), fill = listOf(orange, orange),
            chipOn = orange, chipOnInk = Color(0xFF121417), lyricOn = orange,
            sideBg = Color(0xFF0F1114), barBg = Color(0xFF16191C), raised = Color(0xFF1B1E22), isDark = true,
        ),
        shapes = ThemeShapes(RoundedCornerShape(3.dp), CarbonArtLarge, CarbonArtSmall, CarbonPlay, RoundedCornerShape(2.dp), 0.dp),
        fonts = ThemeFonts(FontKey.CHAKRA_PETCH, FontKey.INTER, displayWeight = 600, headingWeight = 600),
        scale = TypeScale(h1 = 27f, h1Desktop = 36f, h2 = 13f, h2Desktop = 13f, playerTitle = 22f, panelTitle = 20f,
            nowPlayingTitle = 34f, lyric = 21f, lyricDesktop = 28f, h1LetterSpacing = .02f),
        titleCase = TitleCase.UPPER, upperLabels = true, accentArtist = false,
        hero = HeroStyle.CORNER_BRACKETS, backdrop = BackdropStyle.GRID, playerPanel = PlayerPanel.PLAIN,
        showTechBadges = true, centeredPlayerText = false, leftAlignedHero = false, dynamicColor = false, progressHeight = 4f,
        headingMarker = true,
    )

    private val red = Color(0xFFE63946)
    private val yellow = Color(0xFFFFD23F)
    val estadio = AppTheme(
        id = ThemeId.ESTADIO, displayName = "Estadio",
        description = "Deportivo, letras grandes",
        colors = ThemeColors(
            background = Color(0xFF0D1B2E), surface = Color(0xFF14263F), surfaceBorder = Color(0xFF22395A),
            ink = White, mute = Color(0xFF9DB0C8), accent = red, onAccent = White,
            playBg = red, playInk = White, track = Color(0xFF22395A), fill = listOf(yellow, red),
            chipOn = yellow, chipOnInk = Color(0xFF0D1B2E), lyricOn = yellow,
            sideBg = Color(0xFF0A1626), barBg = Color(0xFF102239), raised = Color(0xFF14263F), isDark = true,
        ),
        shapes = ThemeShapes(RoundedCornerShape(6.dp), RoundedCornerShape(6.dp), RoundedCornerShape(4.dp),
            ParallelogramShape(.16f), ParallelogramShape(.14f, byHeight = true), 0.dp),
        fonts = ThemeFonts(FontKey.BEBAS_NEUE, FontKey.BARLOW, displayWeight = 400, headingWeight = 400),
        scale = TypeScale(h1 = 46f, h1Desktop = 62f, h2 = 26f, h2Desktop = 28f, playerTitle = 42f, panelTitle = 34f,
            nowPlayingTitle = 60f, lyric = 34f, lyricDesktop = 48f, h1LineHeight = .9f, titleLineHeight = .92f, h1LetterSpacing = .01f),
        titleCase = TitleCase.UPPER, upperLabels = true, accentArtist = true,
        hero = HeroStyle.SKEWED_BLOCK, backdrop = BackdropStyle.DIAGONAL_STRIPES, playerPanel = PlayerPanel.PLAIN,
        showTechBadges = false, centeredPlayerText = false, leftAlignedHero = false, dynamicColor = false, progressHeight = 5f,
    )

    val bruma = AppTheme(
        id = ThemeId.BRUMA, displayName = "Bruma",
        description = "Tranquilo y legible",
        colors = ThemeColors(
            background = Color(0xFFECEFEA), surface = White, surfaceBorder = Color(0xFFDCE1DA),
            ink = Color(0xFF1F2A24), mute = Color(0xFF6B776F), accent = Color(0xFF2F7D6A), onAccent = White,
            playBg = Color(0xFF1F2A24), playInk = Color(0xFFECEFEA), track = Color(0xFFD5DBD3),
            fill = listOf(Color(0xFF2F7D6A), Color(0xFF2F7D6A)),
            chipOn = Color(0xFF1F2A24), chipOnInk = Color(0xFFECEFEA), lyricOn = Color(0xFF1F2A24),
            sideBg = Color(0xFFE4E8E1), barBg = White, raised = White, isDark = false,
        ),
        shapes = ThemeShapes(RoundedCornerShape(16.dp), RoundedCornerShape(20.dp), RoundedCornerShape(10.dp), CircleShape, Pill, 0.dp),
        fonts = ThemeFonts(FontKey.MANROPE, FontKey.MANROPE, displayWeight = 800, headingWeight = 700),
        scale = TypeScale(h1 = 29f, h1Desktop = 40f, h2 = 17f, h2Desktop = 20f, playerTitle = 24f, panelTitle = 22f,
            nowPlayingTitle = 34f, lyric = 24f, lyricDesktop = 34f),
        titleCase = TitleCase.NORMAL, upperLabels = false, accentArtist = false,
        hero = HeroStyle.SOFT_SHADOW, backdrop = BackdropStyle.SOFT_CIRCLE, playerPanel = PlayerPanel.PLAIN,
        showTechBadges = false, centeredPlayerText = false, leftAlignedHero = false, dynamicColor = false, progressHeight = 5f,
    )

    val all: List<AppTheme> = listOf(aurora, poster, petalo, seda, carbono, estadio, bruma)

    fun byId(id: ThemeId): AppTheme = all.first { it.id == id }

    fun byKey(key: String?): AppTheme = all.firstOrNull { it.id.name == key } ?: aurora
}
