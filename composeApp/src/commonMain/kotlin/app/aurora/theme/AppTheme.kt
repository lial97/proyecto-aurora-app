package app.aurora.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp

/** Los 7 temas visuales (files/TEMAS_VIDEO_ESCRITORIO.md §4). */
enum class ThemeId { AURORA, POSTER, PETALO, SEDA, CARBONO, ESTADIO, BRUMA }

enum class TitleCase { NORMAL, LOWER, UPPER }

/** Decoración de la portada grande. */
enum class HeroStyle { GLOW, THICK_BORDER, WHITE_FRAME, GOLD_RINGS, CORNER_BRACKETS, SKEWED_BLOCK, SOFT_SHADOW }

/** Decoración del fondo. */
enum class BackdropStyle { AURORA_LIGHTS, SPINNING_DOT, PASTEL_BLOBS, GOLD_CIRCLES, GRID, DIAGONAL_STRIPES, SOFT_CIRCLE }

/** Cómo se presenta el bloque de título y progreso bajo la portada del reproductor. */
enum class PlayerPanel { GLASS_OVERLAP, WHITE_OVERLAP, PLAIN }

@Immutable
data class ThemeColors(
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
    /** Relleno del progreso: un color o un degradado de izquierda a derecha. */
    val fill: List<Color>,
    val chipOn: Color,
    val chipOnInk: Color,
    val lyricOn: Color,
    /** Lateral y barra de reproducción en escritorio. */
    val sideBg: Color,
    val barBg: Color,
    /** Fondo de mini reproductor y tarjeta de letra. */
    val raised: Color,
    val isDark: Boolean,
)

@Immutable
data class ThemeShapes(
    val card: Shape,
    val artLarge: Shape,
    val artSmall: Shape,
    val playButton: Shape,
    val chip: Shape,
    /** Borde de portadas, chips y cajas (solo Póster). */
    val borderWidth: Dp,
)

/** Tamaños de texto en sp para móvil y escritorio. */
@Immutable
data class TypeScale(
    val h1: Float, val h1Desktop: Float,
    val h2: Float, val h2Desktop: Float,
    val playerTitle: Float,
    /** Título del panel derecho de escritorio. */
    val panelTitle: Float,
    /** Título de la vista "Reproduciendo" de escritorio. */
    val nowPlayingTitle: Float,
    val lyric: Float, val lyricDesktop: Float,
    val h1LineHeight: Float = 1.05f,
    val titleLineHeight: Float = 1.1f,
    val h1LetterSpacing: Float = -0.02f,
)

enum class FontKey {
    SYNE, DM_SANS, ARCHIVO, FRAUNCES_ITALIC, NUNITO, CORMORANT_ITALIC, JOST,
    CHAKRA_PETCH, INTER, BEBAS_NEUE, BARLOW, MANROPE,
}

@Immutable
data class ThemeFonts(
    val display: FontKey,
    val body: FontKey,
    /** Peso de títulos grandes (h1, reproductor). */
    val displayWeight: Int,
    /** Peso de subtítulos (h2) y letra. */
    val headingWeight: Int,
    val italicDisplay: Boolean = false,
)

@Immutable
data class AppTheme(
    val id: ThemeId,
    val displayName: String,
    val description: String,
    val colors: ThemeColors,
    val shapes: ThemeShapes,
    val fonts: ThemeFonts,
    val scale: TypeScale,
    val titleCase: TitleCase,
    /** Mayúsculas en chips y navegación (Carbono, Estadio). */
    val upperLabels: Boolean,
    /** Artista en mayúsculas espaciadas y color de énfasis (Seda, Estadio). */
    val accentArtist: Boolean,
    val hero: HeroStyle,
    val backdrop: BackdropStyle,
    val playerPanel: PlayerPanel,
    val showTechBadges: Boolean,
    val centeredPlayerText: Boolean,
    val leftAlignedHero: Boolean,
    /** Solo Aurora: fondo y énfasis cambian con cada canción. */
    val dynamicColor: Boolean,
    /** Alto de la barra de progreso en dp. */
    val progressHeight: Float,
    /** Cuadrito de énfasis antes de los subtítulos (Carbono). */
    val headingMarker: Boolean = false,
)
