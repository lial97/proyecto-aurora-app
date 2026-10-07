package app.aurora.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp

/** Definición de los temas. Valores de files/TEMAS_VIDEO_ESCRITORIO.md §4 y del CSS de los prototipos. */
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
        playDecoration = PlayDecoration.GLOW,
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
        heroSizes = HeroSizes(player = 200f),
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
        playDecoration = PlayDecoration.SHADOW, heroSizes = HeroSizes(player = 232f, nowPlaying = 220f, panel = 210f),
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
        playDecoration = PlayDecoration.RING, heroSizes = HeroSizes(player = 236f, nowPlaying = 220f, panel = 220f),
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
        headingDecoration = HeadingDecoration.SQUARE_BEFORE,
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
        heroSizes = HeroSizes(nowPlaying = 236f, panel = 218f),
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

    private val tape = Color(0xFF3B2416)
    private val mustard = Color(0xFFE9B949)
    private val cream = Color(0xFFF6E7C8)
    private val teal = Color(0xFF1D524F)
    val casete = AppTheme(
        id = ThemeId.CASETE, displayName = "Casete",
        description = "Retro, como una cinta",
        colors = ThemeColors(
            background = mustard, surface = cream, surfaceBorder = tape,
            ink = tape, mute = Color(0xFF5A3D22), accent = teal, onAccent = cream,
            playBg = teal, playInk = cream, track = tape.copy(alpha = .22f), fill = listOf(tape, tape),
            chipOn = tape, chipOnInk = mustard, lyricOn = tape,
            sideBg = Color(0xFFE0AC3B), barBg = Color(0xFFE0AC3B), raised = cream, isDark = false,
        ),
        shapes = ThemeShapes(RoundedCornerShape(12.dp), RoundedCornerShape(8.dp), RoundedCornerShape(6.dp), RoundedCornerShape(10.dp),
            RoundedCornerShape(8.dp), 0.dp),
        fonts = ThemeFonts(FontKey.SHRIKHAND, FontKey.DM_SANS, displayWeight = 400, headingWeight = 400),
        scale = TypeScale(h1 = 30f, h1Desktop = 44f, h2 = 19f, h2Desktop = 22f, playerTitle = 26f, panelTitle = 24f,
            nowPlayingTitle = 36f, lyric = 21f, lyricDesktop = 30f, h1LetterSpacing = 0f),
        titleCase = TitleCase.NORMAL, upperLabels = false, accentArtist = false,
        hero = HeroStyle.CASSETTE, backdrop = BackdropStyle.RETRO_STRIPE, playerPanel = PlayerPanel.PLAIN,
        showTechBadges = false, centeredPlayerText = false, leftAlignedHero = false, dynamicColor = false, progressHeight = 8f,
        controlStyle = ControlStyle.KEYS, timeStyle = TimeStyle.TAPE_COUNTER, lyricUsesBody = true,
        smallCoverStyle = SmallCoverStyle.CREAM_FRAME, playDecoration = PlayDecoration.HARD_SHADOW,
        heroSizes = HeroSizes(player = 262f, nowPlaying = 250f, panel = 230f),
    )

    private val ink = Color(0xFF3D2C5E)
    private val violet = Color(0xFF7B57D1)
    val acuarela = AppTheme(
        id = ThemeId.ACUARELA, displayName = "Acuarela",
        description = "Lila, durazno y menta",
        colors = ThemeColors(
            background = Color(0xFFFBF7F3), surface = White, surfaceBorder = Color(0xFFEFE4EC),
            ink = ink, mute = Color(0xFF6E5F88), accent = violet, onAccent = White,
            playBg = violet, playInk = White, track = Color(0xFFEBE2F5), fill = listOf(Color(0xFFF4A68A), Color(0xFFB79AF0), violet),
            chipOn = ink, chipOnInk = Color(0xFFFBF7F3), lyricOn = violet,
            sideBg = White.copy(alpha = .6f), barBg = White, raised = White, isDark = false,
        ),
        shapes = ThemeShapes(RoundedCornerShape(22.dp), OrganicBlobShape(), OrganicBlobShape(), OrganicBlobShape(), Pill, 0.dp),
        fonts = ThemeFonts(FontKey.DM_SERIF_DISPLAY, FontKey.QUICKSAND, displayWeight = 400, headingWeight = 400),
        scale = TypeScale(h1 = 31f, h1Desktop = 44f, h2 = 19f, h2Desktop = 22f, playerTitle = 28f, panelTitle = 24f,
            nowPlayingTitle = 40f, lyric = 24f, lyricDesktop = 34f, h1LetterSpacing = -.01f),
        titleCase = TitleCase.NORMAL, upperLabels = false, accentArtist = false,
        hero = HeroStyle.WATERCOLOR, backdrop = BackdropStyle.WATERCOLOR_BLOBS, playerPanel = PlayerPanel.PLAIN,
        showTechBadges = false, centeredPlayerText = false, leftAlignedHero = false, dynamicColor = false, progressHeight = 6f,
        playDecoration = PlayDecoration.SHADOW,
    )

    private val forest = Color(0xFF122019)
    private val copper = Color(0xFFE09452)
    val bosque = AppTheme(
        id = ThemeId.BOSQUE, displayName = "Bosque",
        description = "Verde profundo y cobre",
        colors = ThemeColors(
            background = forest, surface = Color(0xFF1A2C23), surfaceBorder = Color(0xFF2A4236),
            ink = Color(0xFFE7EFE6), mute = Color(0xFF9DB5A6), accent = copper, onAccent = forest,
            playBg = copper, playInk = forest, track = Color(0xFF2A4236), fill = listOf(Color(0xFF6E9B6A), copper),
            chipOn = copper, chipOnInk = forest, lyricOn = copper,
            sideBg = Color(0xFF0E1A14), barBg = Color(0xFF0E1A14), raised = Color(0xFF1A2C23), isDark = true,
        ),
        shapes = ThemeShapes(RoundedCornerShape(12.dp), RoundedCornerShape(10.dp), RoundedCornerShape(6.dp), RoundedCornerShape(16.dp),
            RoundedCornerShape(8.dp), 0.dp),
        fonts = ThemeFonts(FontKey.ZILLA_SLAB, FontKey.IBM_PLEX_SANS, displayWeight = 700, headingWeight = 700),
        scale = TypeScale(h1 = 31f, h1Desktop = 44f, h2 = 19f, h2Desktop = 22f, playerTitle = 28f, panelTitle = 24f,
            nowPlayingTitle = 40f, lyric = 24f, lyricDesktop = 34f, h1LetterSpacing = 0f),
        titleCase = TitleCase.NORMAL, upperLabels = false, accentArtist = false,
        hero = HeroStyle.COPPER_FRAME, backdrop = BackdropStyle.CONTOUR_LINES, playerPanel = PlayerPanel.PLAIN,
        showTechBadges = false, centeredPlayerText = false, leftAlignedHero = false, dynamicColor = false, progressHeight = 5f,
        smallCoverStyle = SmallCoverStyle.OUTLINE, headingDecoration = HeadingDecoration.LINE_AFTER,
    )

    private val graphite = Color(0xFF141517)
    private val paper = Color(0xFFF1F2F4)
    val grafito = AppTheme(
        id = ThemeId.GRAFITO, displayName = "Grafito",
        description = "Oscuro neutro y nítido",
        colors = ThemeColors(
            background = graphite, surface = Color(0xFF1E2023), surfaceBorder = Color(0xFF2B2E33),
            ink = paper, mute = Color(0xFFA0A4AC), accent = Color(0xFF8AB4FF), onAccent = graphite,
            playBg = paper, playInk = graphite, track = Color(0xFF2E3136), fill = listOf(Color(0xFF8AB4FF), Color(0xFF8AB4FF)),
            chipOn = paper, chipOnInk = graphite, lyricOn = paper,
            sideBg = graphite, barBg = Color(0xFF1A1B1E), raised = Color(0xFF1E2023), isDark = true,
        ),
        shapes = ThemeShapes(RoundedCornerShape(16.dp), RoundedCornerShape(18.dp), RoundedCornerShape(10.dp), CircleShape, Pill, 0.dp),
        fonts = ThemeFonts(FontKey.FIGTREE, FontKey.FIGTREE, displayWeight = 800, headingWeight = 700),
        scale = TypeScale(h1 = 30f, h1Desktop = 40f, h2 = 18f, h2Desktop = 20f, playerTitle = 26f, panelTitle = 22f,
            nowPlayingTitle = 36f, lyric = 24f, lyricDesktop = 34f, h1LetterSpacing = -.02f),
        titleCase = TitleCase.NORMAL, upperLabels = false, accentArtist = false,
        hero = HeroStyle.DEEP_SHADOW, backdrop = BackdropStyle.NONE, playerPanel = PlayerPanel.PLAIN,
        showTechBadges = false, centeredPlayerText = false, leftAlignedHero = false, dynamicColor = false, progressHeight = 4f,
    )

    private val cherry = Color(0xFFC81E35)
    private val diner = Color(0xFFFFF4E2)
    val rockola = AppTheme(
        id = ThemeId.ROCKOLA, displayName = "Rockola",
        description = "Cereza, menta y vinilo",
        colors = ThemeColors(
            background = diner, surface = White, surfaceBorder = Color(0xFFF0DCC0),
            ink = Color(0xFF2A1B1F), mute = Color(0xFF7A5F58), accent = cherry, onAccent = diner,
            playBg = cherry, playInk = diner, track = Color(0xFFF0DCC0), fill = listOf(Color(0xFF5DB8A4), cherry),
            chipOn = cherry, chipOnInk = diner, lyricOn = cherry,
            sideBg = White, barBg = White, raised = White, isDark = false,
        ),
        shapes = ThemeShapes(RoundedCornerShape(18.dp), CircleShape, CircleShape, Pill, Pill, 0.dp),
        fonts = ThemeFonts(FontKey.LOBSTER, FontKey.RUBIK, displayWeight = 400, headingWeight = 400),
        scale = TypeScale(h1 = 31f, h1Desktop = 46f, h2 = 22f, h2Desktop = 24f, playerTitle = 30f, panelTitle = 26f,
            nowPlayingTitle = 44f, lyric = 24f, lyricDesktop = 34f, h1LetterSpacing = 0f),
        titleCase = TitleCase.NORMAL, upperLabels = false, accentArtist = false,
        hero = HeroStyle.VINYL, backdrop = BackdropStyle.CHECKER_BAND, playerPanel = PlayerPanel.PLAIN,
        showTechBadges = false, centeredPlayerText = false, leftAlignedHero = false, dynamicColor = false, progressHeight = 7f,
        lyricUsesBody = true, smallCoverStyle = SmallCoverStyle.VINYL_RING, playDecoration = PlayDecoration.CHROME_RING,
        heroSizes = HeroSizes(player = 262f, nowPlaying = 262f, panel = 236f), accentHeadings = true,
    )

    val all: List<AppTheme> = listOf(aurora, poster, petalo, seda, carbono, estadio, bruma, casete, acuarela, bosque, grafito, rockola)

    fun byId(id: ThemeId): AppTheme = all.first { it.id == id }

    fun byKey(key: String?): AppTheme = all.firstOrNull { it.id.name == key } ?: aurora
}
