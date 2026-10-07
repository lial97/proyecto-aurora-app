package app.aurora.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp

/** Los temas visuales (files/TEMAS_VIDEO_ESCRITORIO.md §4 y las maquetas de files/). */
enum class ThemeId { AURORA, POSTER, PETALO, SEDA, CARBONO, ESTADIO, BRUMA, CASETE, ACUARELA, BOSQUE, GRAFITO, ROCKOLA }

enum class TitleCase { NORMAL, LOWER, UPPER }

/** Decoración de la portada grande. */
enum class HeroStyle {
    GLOW, THICK_BORDER, WHITE_FRAME, GOLD_RINGS, CORNER_BRACKETS, SKEWED_BLOCK, SOFT_SHADOW,
    /** Casete: carátula en su estuche y debajo la cinta con carretes. */
    CASSETTE,
    /** Acuarela: forma de gota que cambia mientras suena, sobre una mancha difuminada. */
    WATERCOLOR,
    /** Bosque: marco de cobre separado de la portada. */
    COPPER_FRAME,
    /** Grafito: sombra profunda y un reflejo azul debajo. */
    DEEP_SHADOW,
    /** Rockola: la portada es la etiqueta de un vinilo que gira. */
    VINYL,
}

/** Decoración del fondo. */
enum class BackdropStyle {
    AURORA_LIGHTS, SPINNING_DOT, PASTEL_BLOBS, GOLD_CIRCLES, GRID, DIAGONAL_STRIPES, SOFT_CIRCLE,
    /** Casete: franja diagonal de 5 bandas en una esquina y grano de papel. */
    RETRO_STRIPE,
    /** Acuarela: 3 manchas lila, durazno y menta que flotan despacio. */
    WATERCOLOR_BLOBS,
    /** Bosque: curvas de nivel de un mapa topográfico. */
    CONTOUR_LINES,
    /** Grafito: sin decoración. */
    NONE,
    /** Rockola: franja de cuadros encima de las pestañas y una estrella. */
    CHECKER_BAND,
}

/** Controles de reproducción: botones del tema o teclas de grabadora (Casete). */
enum class ControlStyle { ROUND, KEYS }

/** Cómo se muestra el tiempo actual: texto normal o contador de cinta en una pastilla (Casete). */
enum class TimeStyle { PLAIN, TAPE_COUNTER }

/** Marco de las portadas pequeñas (filas, tarjetas y mini reproductor). */
enum class SmallCoverStyle {
    NONE,
    /** Casete: borde crema y sombra dura. */
    CREAM_FRAME,
    /** Rockola: aro negro, como un disco. */
    VINYL_RING,
    /** Bosque: contorno fino. */
    OUTLINE,
}

/** Decoración del botón principal. */
enum class PlayDecoration {
    NONE,
    /** Aurora: halo grande del color de énfasis. */
    GLOW,
    /** Pétalo y Acuarela: sombra suave del color de énfasis. */
    SHADOW,
    /** Seda: aro fino separado del botón. */
    RING,
    /** Rockola: aro crema, aro cromo y sombra cereza. */
    CHROME_RING,
    /** Casete: sombra dura sin difuminar. */
    HARD_SHADOW,
}

/** Adorno de los subtítulos (h2). */
enum class HeadingDecoration {
    NONE,
    /** Carbono: cuadrito de énfasis delante y texto en mayúsculas espaciadas. */
    SQUARE_BEFORE,
    /** Bosque: línea de cobre a la derecha. */
    LINE_AFTER,
}

/** Lado de la portada grande en dp: reproductor del móvil, vista "Reproduciendo" y panel derecho de escritorio. */
@Immutable
data class HeroSizes(val player: Float = 268f, val nowPlaying: Float = 270f, val panel: Float = 246f)

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
    SHRIKHAND, DM_SERIF_DISPLAY, QUICKSAND, ZILLA_SLAB, IBM_PLEX_SANS, FIGTREE, LOBSTER, RUBIK,
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
    val headingDecoration: HeadingDecoration = HeadingDecoration.NONE,
    val controlStyle: ControlStyle = ControlStyle.ROUND,
    val timeStyle: TimeStyle = TimeStyle.PLAIN,
    /** La letra usa la fuente de texto (Casete y Rockola: sus títulos son muy gruesos o cursivos para leer muchas líneas). */
    val lyricUsesBody: Boolean = false,
    val smallCoverStyle: SmallCoverStyle = SmallCoverStyle.NONE,
    val playDecoration: PlayDecoration = PlayDecoration.NONE,
    val heroSizes: HeroSizes = HeroSizes(),
    /** Subtítulos (h2) en el color de énfasis (Rockola). */
    val accentHeadings: Boolean = false,
)
