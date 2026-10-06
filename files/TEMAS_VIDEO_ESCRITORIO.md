# Temas, módulo de video y versión de escritorio

Guía de implementación para **Claude Code**. Proyecto en **Kotlin**.

Este documento describe cómo añadir a la app:

1. **7 temas visuales** que el usuario puede elegir: Aurora, Póster, Pétalo, Seda, Carbono, Estadio y Bruma.
2. **Módulo de video**: video musical sincronizado con la canción.
3. **Versión de escritorio** (PC) con distribución de tres columnas.

Regla que no se puede romper: **la portada de la canción siempre está visible** en todas las pantallas (tarjetas, filas, reproductor, letra, video, mini reproductor y barra de escritorio).

---

## 0. Referencias visuales

Copia estos archivos a `docs/design/` del repositorio. Se abren en cualquier navegador y son interactivos:

| Archivo | Qué muestra |
|---|---|
| `temas-app-musica.html` | Los 7 temas en móvil: Inicio, Lista, Reproductor, Video, Letra y video en pantalla completa |
| `escritorio-app-musica.html` | Versión de escritorio con los 7 temas: Inicio, Lista, Reproduciendo, Video |
| `aurora-app-musica.html` | Aurora completo, con hoja de opciones, información de la pista y color dinámico |
| `aurora-especificaciones.txt` | Especificación detallada de Aurora (animaciones, medidas, datos) |

Cuando haya dudas de medidas, colores o comportamiento, **el HTML es la fuente de verdad**: los valores de este documento salen de su CSS.

---

## 1. Supuestos técnicos

- UI con **Jetpack Compose** (Android).
- Escritorio con **Compose Multiplatform (Desktop JVM)**, compartiendo el código de UI.
- Si el proyecto hoy es solo Android, mover tema, componentes y pantallas a un módulo `shared` (`commonMain`) y crear `desktopApp`.
- Si el proyecto usa Vistas XML en lugar de Compose, **preguntar antes de empezar**: todo el plan cambia.

Dependencias sugeridas (verificar las versiones más recientes antes de añadirlas):

```kotlin
// shared/build.gradle.kts
commonMain.dependencies {
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.components.resources) // fuentes .ttf
    implementation("androidx.datastore:datastore-preferences-core:<versión>") // tema elegido
}
androidMain.dependencies {
    implementation("androidx.media3:media3-exoplayer:<versión>")
    implementation("androidx.media3:media3-ui:<versión>")
    implementation("androidx.media3:media3-session:<versión>")
}
desktopMain.dependencies {
    implementation(compose.desktop.currentOs)
    implementation("uk.co.caprica:vlcj:<versión>") // video en escritorio (requiere VLC instalado o empaquetado)
}
```

---

## 2. Estructura de carpetas

```
shared/src/commonMain/kotlin/<paquete>/
  ui/theme/
    ThemeId.kt            enum con los 7 temas
    AppTheme.kt           modelo (colores, formas, tipografía, rasgos) + CompositionLocal
    Themes.kt             definición de los 7 temas
    Shapes.kt             ArchShape, ParallelogramShape
    Fonts.kt              FontFamily de cada tema
    DynamicColor.kt       moodFromBpm, extractAccent, buildAuroraPalette
    ThemeRepository.kt    guardar y leer el tema elegido
  ui/components/
    Artwork.kt            portada con forma y decoración del tema
    AmbientBackground.kt  decoración de fondo por tema
    ThemedTitle.kt        títulos con mayúsculas/minúsculas según tema
    PlayButton.kt  ProgressBar.kt  SongRow.kt  Chip.kt  Segmented.kt
    MiniPlayer.kt  BottomTabs.kt  Toast.kt  Equalizer.kt  LyricsView.kt
    VideoSurface.kt       expect: superficie de video por plataforma
  ui/screens/
    HomeScreen.kt  PlaylistScreen.kt  PlayerScreen.kt  VideoScreen.kt  LyricsScreen.kt
    settings/ThemePickerScreen.kt
  ui/desktop/
    DesktopShell.kt  Sidebar.kt  NowPlayingPanel.kt  PlayerBar.kt  DesktopViews.kt  Shortcuts.kt
  playback/
    PlaybackController.kt  interfaz común (estado + acciones)
    PlaybackState.kt
shared/src/androidMain/kotlin/<paquete>/playback/ExoPlaybackController.kt
shared/src/desktopMain/kotlin/<paquete>/playback/VlcPlaybackController.kt
shared/src/commonMain/composeResources/font/   archivos .ttf
```

---

## 3. Modelo de tema

```kotlin
enum class ThemeId { AURORA, POSTER, PETALO, SEDA, CARBONO, ESTADIO, BRUMA }

enum class TitleCase { NORMAL, LOWER, UPPER }

/** Cómo se decora la portada grande del reproductor. */
enum class HeroStyle { GLOW, THICK_BORDER, WHITE_FRAME, GOLD_RINGS, CORNER_BRACKETS, SKEWED_BLOCK, SOFT_SHADOW }

/** Decoración del fondo de la pantalla. */
enum class BackdropStyle { AURORA_LIGHTS, SPINNING_DOT, PASTEL_BLOBS, GOLD_CIRCLES, GRID, DIAGONAL_STRIPES, SOFT_CIRCLE }

@Immutable
data class ThemeColors(
    val background: Color,
    val surface: Color,
    val surfaceBorder: Color,
    val ink: Color,            // texto principal
    val mute: Color,           // texto secundario
    val accent: Color,         // énfasis: activo, me gusta, canción actual
    val onAccent: Color,
    val playBg: Color,         // botón principal
    val playInk: Color,
    val track: Color,          // fondo de barras de progreso
    val fill: Brush,           // relleno del progreso (color o degradado)
    val chipOn: Color,
    val chipOnInk: Color,
    val lyricOn: Color,        // línea de letra activa
    val isDark: Boolean,
)

@Immutable
data class ThemeShapes(
    val card: Shape,           // tarjetas, filas, chips de superficie, cuadro de video
    val artLarge: Shape,       // portada grande
    val artSmall: Shape,       // portada en filas, mini reproductor, barra de escritorio
    val playButton: Shape,
    val chip: Shape,
    val borderWidth: Dp,       // 0.dp salvo Póster (2.5–3.dp)
)

@Immutable
data class ThemeType(
    val display: FontFamily,
    val body: FontFamily,
    val titleCase: TitleCase,
    val h1: TextStyle,
    val h2: TextStyle,
    val playerTitle: TextStyle,
    val lyric: TextStyle,
    val artist: TextStyle,     // Seda y Estadio lo usan en mayúsculas espaciadas y color de énfasis
)

@Immutable
data class AppTheme(
    val id: ThemeId,
    val displayName: String,
    val description: String,   // texto corto que ve el usuario en el selector
    val colors: ThemeColors,
    val shapes: ThemeShapes,
    val type: ThemeType,
    val hero: HeroStyle,
    val backdrop: BackdropStyle,
    val showTechBadges: Boolean,   // Carbono: BPM, formato, kbps bajo el título
    val centeredPlayerText: Boolean, // Seda
    val leftAlignedHero: Boolean,  // Póster
    val dynamicColor: Boolean,     // solo Aurora
)

val LocalAppTheme = staticCompositionLocalOf<AppTheme> { error("Sin tema") }

@Composable
fun MusicAppTheme(theme: AppTheme, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAppTheme provides theme) {
        // Opcional: mapear a MaterialTheme para componentes de Material
        MaterialTheme(
            colorScheme = if (theme.colors.isDark) darkColorScheme(primary = theme.colors.accent, background = theme.colors.background)
                          else lightColorScheme(primary = theme.colors.accent, background = theme.colors.background),
            content = content
        )
    }
}
```

**Importante:** ningún componente debe usar colores, formas o fuentes fijas. Todo se lee de `LocalAppTheme.current`.

---

## 4. Los 7 temas

### 4.1 Tabla de colores

| Token | Aurora | Póster | Pétalo | Seda | Carbono | Estadio | Bruma |
|---|---|---|---|---|---|---|---|
| background | dinámico (def. `#120E2B`) | `#EDEBFF` | `#FBEEF1` | `#1E0F18` | `#121417` | `#0D1B2E` | `#ECEFEA` |
| surface | blanco 10 % | transparente | `#FFFFFF` | `#E8C9A0` 6 % | `#1B1E22` | `#14263F` | `#FFFFFF` |
| surfaceBorder | blanco 18 % | `#1F2BFF` | `#F4D9E1` | `#E8C9A0` 32 % | `#2C3138` | `#22395A` | `#DCE1DA` |
| ink | `#F2EEFF` | `#1F2BFF` | `#4A2B3A` | `#F6E9DD` | `#E3E7EC` | `#FFFFFF` | `#1F2A24` |
| mute | ink 62 % | `#1F2BFF` 68 % | `#9A7383` | ink 60 % | `#8A929C` | `#9DB0C8` | `#6B776F` |
| accent | dinámico (def. `#FF5FA2`) | `#1F2BFF` | `#E0567A` | `#E8C9A0` | `#FF7A1A` | `#E63946` | `#2F7D6A` |
| onAccent | background | `#EDEBFF` | `#FFFFFF` | `#1E0F18` | `#121417` | `#FFFFFF` | `#FFFFFF` |
| playBg / playInk | `#F2EEFF` / background | `#1F2BFF` / `#EDEBFF` | `#E0567A` / `#FFFFFF` | `#E8C9A0` / `#1E0F18` | `#FF7A1A` / `#121417` | `#E63946` / `#FFFFFF` | `#1F2A24` / `#ECEFEA` |
| track | blanco 20 % | `#D3D0FF` | `#F4D9E1` | `#E8C9A0` 18 % | `#2C3138` | `#22395A` | `#D5DBD3` |
| fill | c1 → accent | `#1F2BFF` | `#B9A3E3` → `#E0567A` | `#F08BA8` → `#E8C9A0` | `#FF7A1A` | `#FFD23F` → `#E63946` | `#2F7D6A` |
| chipOn / chipOnInk | `#F2EEFF` / `#120E2B` | `#1F2BFF` / `#EDEBFF` | `#E0567A` / `#FFFFFF` | `#E8C9A0` / `#1E0F18` | `#FF7A1A` / `#121417` | `#FFD23F` / `#0D1B2E` | `#1F2A24` / `#ECEFEA` |
| lyricOn | ink | ink | `#E0567A` | ink | `#FF7A1A` | `#FFD23F` | ink |
| isDark | sí | no | no | sí | sí | sí | no |

Colores extra: Pétalo lila `#B9A3E3` y melocotón `#FFD9C7`; Seda rosa `#F08BA8`; Estadio amarillo `#FFD23F`.

### 4.2 Formas

| Tema | card | artLarge | artSmall | playButton | chip | Borde |
|---|---|---|---|---|---|---|
| Aurora | Redondeado 16 | Redondeado 26 | Redondeado 11 | Círculo | Píldora | 0 |
| Póster | Recto | Recto | Recto | Recto | Recto | 3.dp (`#1F2BFF`) |
| Pétalo | Redondeado 22 | **Arco** (arriba 50 %, abajo 28) | Arco (abajo 10) | Círculo | Píldora | 0 |
| Seda | Redondeado 14 | **Círculo** | Círculo | Círculo | Redondeado 2 | 0 |
| Carbono | Redondeado 3 | **Corte** 20 (arriba izq. y abajo der.) | Corte 6 | Corte 12 | Redondeado 2 | 0 |
| Estadio | Redondeado 6 | Redondeado 6 | Redondeado 4 | **Paralelogramo** 16 % | Redondeado 4 inclinado −8° | 0 |
| Bruma | Redondeado 16 | Redondeado 20 | Redondeado 10 | Círculo | Píldora | 0 |

```kotlin
// Shapes.kt
val ArchShape = RoundedCornerShape(
    topStart = CornerSize(50), topEnd = CornerSize(50),          // porcentaje: semicírculo
    bottomEnd = CornerSize(28.dp), bottomStart = CornerSize(28.dp)
)
val ArchShapeSmall = RoundedCornerShape(CornerSize(50), CornerSize(50), CornerSize(10.dp), CornerSize(10.dp))

val CarbonArtLarge = CutCornerShape(topStart = 20.dp, bottomEnd = 20.dp)
val CarbonArtSmall = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp)
val CarbonPlay = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp)

/** Botón principal de Estadio: paralelogramo inclinado. */
class ParallelogramShape(private val skewFraction: Float = 0.16f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val dx = size.width * skewFraction
        return Outline.Generic(Path().apply {
            moveTo(dx, 0f); lineTo(size.width, 0f); lineTo(size.width - dx, size.height); lineTo(0f, size.height); close()
        })
    }
}
```

### 4.3 Tipografía

Todas las fuentes son de Google Fonts (licencia OFL). Descargar los `.ttf` y ponerlos en `composeResources/font/`.

| Tema | Display (títulos) | Body (texto) | Mayúsculas |
|---|---|---|---|
| Aurora | Syne 700/800 | DM Sans 400/500/700 | Normal |
| Póster | Archivo 900 | Archivo 400/600 | **minúsculas** en títulos y letra |
| Pétalo | Fraunces 600 *cursiva* | Nunito 400/600/700 | Normal |
| Seda | Cormorant Garamond 500/600 *cursiva* | Jost 400/500/600 | Normal; artista en MAYÚSCULAS espaciadas (0.18em) |
| Carbono | Chakra Petch 600 | Inter 400/500/600 | **MAYÚSCULAS** en títulos, chips y navegación |
| Estadio | Bebas Neue 400 | Barlow 400/500/600 | **MAYÚSCULAS** |
| Bruma | Manrope 800 | Manrope 400/500/700 | Normal |

Tamaños en **móvil** (sp):

| Estilo | Aurora | Póster | Pétalo | Seda | Carbono | Estadio | Bruma |
|---|---|---|---|---|---|---|---|
| h1 | 30 / 800 | 46 / 900, interlineado 0.88, espaciado −0.05em | 32 / 600 cursiva | 36 / 500 cursiva | 27 / 600 | 46, interlineado 0.9 | 29 / 800 |
| h2 | 18 / 700 | 20 / 900 | 18 / 600 cursiva | 22 / 600 cursiva | 13 / 600, espaciado 0.14em, color mute, cuadrito naranja 8×8 antes | 26 | 17 / 700 |
| playerTitle | 24 / 800 | 52 / 900, interlineado 0.86 | 25 / 600 | 32 / 600 cursiva | 22 / 600 | 42, interlineado 0.92 | 24 / 800 |
| lyric | 24 / 700 | 30 / 900 | 24 / 600 cursiva | 29 / 600 cursiva | 21 / 600 | 34 | 24 / 800 |

Tamaños en **escritorio**: h1 Aurora 40, Póster 68, Pétalo 44, Seda 52, Carbono 36, Estadio 62, Bruma 40. Letra: 34 por defecto, Póster 44, Seda 40, Carbono 28, Estadio 48. Título de "Reproduciendo": 34 por defecto, Póster 60, Seda 46, Estadio 60.

```kotlin
@Composable
fun ThemedTitle(text: String, style: TextStyle, modifier: Modifier = Modifier, color: Color = LocalAppTheme.current.colors.ink) {
    val t = LocalAppTheme.current
    val shown = when (t.type.titleCase) {
        TitleCase.LOWER -> text.lowercase()
        TitleCase.UPPER -> text.uppercase()
        TitleCase.NORMAL -> text
    }
    Text(shown, style = style, color = color, modifier = modifier)
}
```

### 4.4 Rasgos propios de cada tema

**Aurora** (para todos, nocturno): fondo con 3 luces difusas que flotan (sección 5). Panel de vidrio sobre la portada en el reproductor (se monta −34.dp encima, fondo blanco 10 %, borde blanco 18 %). Portada con halo del color c1. La portada "respira" al sonar. Botón principal con halo del color de énfasis. **Único tema con color dinámico.**

**Póster** (para todos, gráfico): portada alineada a la izquierda (176.dp en móvil) con borde de 3.dp. Título gigante en minúsculas debajo. Controles dentro de una caja con borde de 3.dp, botón principal rectangular de 90×58. Barra de progreso de 12.dp de alto, recta. Fondo: círculo de 130.dp relleno al 75 % que gira (8 s por vuelta) mientras suena.

**Pétalo** (tiende a gustar más a mujeres): portada en arco de 224×270 con marco blanco de 6.dp y sombra rosada. Panel blanco con sombra que se monta −28.dp sobre la portada. Fondo con tres manchas suaves (rosa `#F7C3D2`, lila `#DDD0F6`, melocotón `#FFD9C7`). Mini reproductor y tarjeta de letra blancos con sombra suave.

**Seda** (tiende a gustar más a mujeres): portada circular de 232.dp con dos aros dorados (1.dp a 6.dp de distancia y 1.dp al 30 % a 15.dp). Texto del reproductor centrado; el botón de me gusta queda a la derecha. Barra de progreso de 2.dp. Fondo con un círculo fino y otro punteado que gira muy lento (60 s por vuelta).

**Carbono** (tiende a gustar más a hombres): esquinas cortadas en portadas, miniaturas de video y botón principal. Dos escuadras naranjas de 22.dp en las esquinas opuestas de la portada grande. Etiquetas técnicas bajo el título (`118 BPM`, `FLAC 24 bit`, `1411 kbps`; la primera en naranja). Fondo con retícula de 22.dp de líneas blancas al 3,5 %.

**Estadio** (tiende a gustar más a hombres): bloque rojo inclinado −10° detrás de la portada, desplazado a la derecha. Botón principal en paralelogramo de 88 de ancho. Chips inclinados −8°. Fondo con franja diagonal de rayas rojas al 16 % y una línea amarilla, ambas giradas −18°.

**Bruma** (neutral): sin decoraciones salvo un círculo grande muy suave arriba a la izquierda. Portada con sombra suave. Prioriza legibilidad y contraste.

### 4.5 Esqueleto de `Themes.kt`

```kotlin
object Themes {
    val aurora = AppTheme(
        id = ThemeId.AURORA, displayName = "Aurora",
        description = "Oscuro e inmersivo. El color cambia con cada canción.",
        colors = ThemeColors(
            background = Color(0xFF120E2B), surface = Color.White.copy(alpha = .10f), surfaceBorder = Color.White.copy(alpha = .18f),
            ink = Color(0xFFF2EEFF), mute = Color(0xFFF2EEFF).copy(alpha = .62f), accent = Color(0xFFFF5FA2), onAccent = Color(0xFF120E2B),
            playBg = Color(0xFFF2EEFF), playInk = Color(0xFF120E2B), track = Color.White.copy(alpha = .20f),
            fill = SolidColor(Color(0xFFFF5FA2)), // se reemplaza en tiempo real por el degradado c1 → accent
            chipOn = Color(0xFFF2EEFF), chipOnInk = Color(0xFF120E2B), lyricOn = Color(0xFFF2EEFF), isDark = true
        ),
        shapes = ThemeShapes(RoundedCornerShape(16.dp), RoundedCornerShape(26.dp), RoundedCornerShape(11.dp), CircleShape, CircleShape, 0.dp),
        type = /* Syne + DM Sans, tamaños de la tabla 4.3 */ TODO(),
        hero = HeroStyle.GLOW, backdrop = BackdropStyle.AURORA_LIGHTS,
        showTechBadges = false, centeredPlayerText = false, leftAlignedHero = false, dynamicColor = true
    )
    // poster, petalo, seda, carbono, estadio, bruma: mismos campos con los valores de las tablas 4.1 a 4.4

    val all = listOf(aurora /*, poster, petalo, seda, carbono, estadio, bruma */)
    fun byId(id: ThemeId) = all.first { it.id == id }
}
```

Completar los `TODO()` con los valores de las tablas. No inventar valores nuevos.

---

## 5. Color dinámico (Aurora y luces del video)

Dos capas:

- **Atmósfera por tempo**: canciones lentas en azules, movidas en rojos.
- **Énfasis por portada**: el color vivo dominante de la portada. Si no hay color vivo, se usa el del tempo.

En **Aurora** ambas capas pintan toda la interfaz. En los demás temas los colores son fijos, pero **las luces del video** usan el énfasis de la portada y la luz c1 del tempo en todos los temas.

```kotlin
// DynamicColor.kt
data class Mood(val c1: Color, val c2: Color, val c3: Color, val background: Color, val accent: Color, val speed: Float, val energy: Float)

private const val BPM_LOW = 72f
private const val BPM_HIGH = 138f

/** 225° (azul) para lentas → 360° (rojo) para movidas, pasando por violeta y magenta. */
fun moodFromBpm(bpm: Int?): Mood {
    val e = (((bpm ?: 100) - BPM_LOW) / (BPM_HIGH - BPM_LOW)).coerceIn(0f, 1f)
    val h = 225f + e * 135f
    fun hsl(hue: Float, s: Float, l: Float) = Color.hsl(((hue % 360f) + 360f) % 360f, s, l)
    return Mood(
        c1 = hsl(h, .85f, .62f),
        c2 = hsl(h + 28f, .90f, .64f),
        c3 = hsl(h - 32f, .80f, .58f),
        background = hsl(h, .48f, .10f),
        accent = hsl(h + 10f, .95f, .67f),
        speed = 1.45f - e * .85f,   // multiplicador de duración: lentas más pausadas
        energy = e
    )
}

/** Recibe la portada; devuelve el color de énfasis o null si no tiene color vivo. */
fun extractAccent(cover: ImageBitmap): Color? {
    val n = 48
    val small = ImageBitmap(n, n)
    Canvas(small).drawImageRect(cover, dstSize = IntSize(n, n), paint = Paint().apply { filterQuality = FilterQuality.Low })
    val px = IntArray(n * n)
    small.readPixels(px)

    val weight = DoubleArray(24); val rs = DoubleArray(24); val gs = DoubleArray(24); val bs = DoubleArray(24)
    for (p in px) {
        val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
        val (h, s, l) = rgbToHsl(r, g, b)
        if (l < .15 || l > .92 || s < .25) continue            // descarta negros, blancos y grises
        val w = s * s * (1 - kotlin.math.abs(l - .55))          // premia colores vivos de luz media
        val k = (h / 15).toInt() % 24                            // 24 cajas de 15°
        weight[k] += w; rs[k] += r * w; gs[k] += g * w; bs[k] += b * w
    }
    val best = weight.indices.maxByOrNull { weight[it] } ?: return null
    if (weight[best] < 4.0) return null
    val (h, s, l) = rgbToHsl((rs[best] / weight[best]).toInt(), (gs[best] / weight[best]).toInt(), (bs[best] / weight[best]).toInt())
    return Color.hsl(h.toFloat(), maxOf(s, .65).toFloat(), l.coerceIn(.58, .72).toFloat()) // legible sobre fondo oscuro
}

fun rgbToHsl(r: Int, g: Int, b: Int): Triple<Double, Double, Double> {
    val rf = r / 255.0; val gf = g / 255.0; val bf = b / 255.0
    val mx = maxOf(rf, gf, bf); val mn = minOf(rf, gf, bf); val d = mx - mn
    val l = (mx + mn) / 2
    if (d == 0.0) return Triple(0.0, 0.0, l)
    val s = if (l > .5) d / (2 - mx - mn) else d / (mx + mn)
    var h = when (mx) { rf -> (gf - bf) / d + (if (gf < bf) 6 else 0); gf -> (bf - rf) / d + 2; else -> (rf - gf) / d + 4 }
    h *= 60
    return Triple(h, s, l)
}
```

Reglas:

- Calcular `extractAccent` **una vez por portada** fuera del hilo principal (`Dispatchers.Default`) y guardarlo en caché por `coverUrl`. Mejor aún: calcularlo en el servidor al subir la portada y enviarlo junto a la canción.
- Si falta el BPM, usar 100 (ritmo medio).
- En Aurora, animar cada color con `animateColorAsState(target, tween(1200))`.
- El multiplicador `speed` escala la duración de las luces, la respiración de la portada y el ecualizador.

```kotlin
@Composable
fun rememberAuroraColors(bpm: Int?, coverAccent: Color?): AuroraColors {
    val m = remember(bpm) { moodFromBpm(bpm) }
    val spec = tween<Color>(1200)
    return AuroraColors(
        c1 = animateColorAsState(m.c1, spec).value,
        c2 = animateColorAsState(m.c2, spec).value,
        c3 = animateColorAsState(m.c3, spec).value,
        background = animateColorAsState(m.background, spec).value,
        accent = animateColorAsState(coverAccent ?: m.accent, tween(800)).value,
        speed = m.speed
    )
}
```

Las luces de Aurora: usar `Brush.radialGradient(color → transparente)` en lugar de `Modifier.blur` (blur solo funciona en Android 12+ y cuesta más). Tres círculos de 260/240/220.dp en móvil (520/480/420 en escritorio), opacidad 0.7, que se desplazan y escalan (1 → 1.15 → 0.95) en 18/22/26 s × `speed`, ida y vuelta, y **se pausan cuando la música está en pausa**. Opacidad del conjunto: 0.55 en Inicio y Lista, 1.0 en Reproductor, Letra y Video.

---

## 6. Pantallas (móvil)

Todas las pantallas existen en los 7 temas con la misma estructura.

**Inicio**: fecha pequeña + "Buenas noches" (h1) + avatar. Chips: Todo, Música, Videos, Descargas. "Sigue escuchando" (carrusel de portadas 132.dp). **"Videos musicales"** (carrusel de miniaturas 16:9 de 210.dp con duración y ícono de reproducir). "Escuchado hace poco" (filas). Abajo, mini reproductor y pestañas.

**Lista**: volver, portada 180.dp con la forma del tema, título, descripción, "8 canciones, 29 min". Acciones: descargar, guardar, aleatorio, reproducir. Pistas numeradas; la actual muestra ecualizador y título en color de énfasis. Las filas entran en cascada.

**Reproductor**: arriba, cerrar, **selector "Canción | Video"** y menú. Portada grande (forma y decoración del tema). Título, artista, me gusta, progreso y tiempos. Controles: aleatorio, anterior, reproducir, siguiente, repetir. Tarjeta con la línea de letra actual.

**Video**: arriba, volver, selector "Canción | Video" y menú. Cuadro de video 16:9. Debajo: **portada pequeña** + título + artista y vistas + me gusta. Chips: Solo audio, Compartir, Guardar, 1080p. "Más videos": filas con miniatura 118.dp, título, artista y vistas.

**Letra**: arriba, cerrar, portada pequeña con título y artista, y botón para ver el video. Líneas grandes; la activa se centra al 36 % del alto. Futuras al 28 % de opacidad, pasadas al 45 %, activa al 100 % con el color `lyricOn`. Tocar una línea salta a ese momento. Abajo: progreso y controles.

**Pestañas** (4): Inicio, **Videos**, Buscar, Biblioteca. El mini reproductor tiene un botón para abrir el video de la canción actual.

**Selector de tema** (Ajustes > Apariencia): cuadrícula con una vista previa pequeña de cada tema (portada con su forma + colores), nombre y descripción corta. Guardar con DataStore. Aplicar al instante con transición de color de 600 ms.

> En la interfaz **no mostrar** etiquetas como "para mujeres" o "para hombres". Esas etiquetas solo orientaron el diseño. El usuario ve nombre y descripción.

Descripciones para el selector:

| Tema | Descripción visible |
|---|---|
| Aurora | Oscuro e inmersivo. El color cambia con cada canción. |
| Póster | Gráfico y directo, con títulos enormes. |
| Pétalo | Suave y cálido, en rosas y lilas. |
| Seda | Elegante, en ciruela y dorado. |
| Carbono | Técnico y preciso, con datos a la vista. |
| Estadio | Deportivo y enérgico, con letras grandes. |
| Bruma | Tranquilo, limpio y fácil de leer. |

---

## 7. Módulo de video

### Comportamiento

- **Una sola reproducción.** Audio y video son el mismo `PlaybackController`. Cambiar entre "Canción" y "Video" **no reinicia ni corta** el audio; solo muestra u oculta la superficie de video.
- **Solo audio**: deshabilitar la pista de video para ahorrar datos y batería.
- Tocar el cuadro de video: pausa o reanuda. El botón central se ve en pausa o al pasar el cursor.
- Barra inferior del video: progreso (relleno con el color de énfasis), tiempo, insignia HD y pantalla completa.
- **Pantalla completa**: en Android, girar a horizontal y modo inmersivo; en escritorio, el video ocupa toda la ventana. Salir con el botón o con `Esc`.
- **Portada siempre visible**: junto al título bajo el video, en las miniaturas de "Más videos" (si el video no trae miniatura propia, usar la portada) y en el mini reproductor.
- Si una canción no tiene video, el selector "Video" se muestra deshabilitado con el texto "Sin video".

### Interfaz común

```kotlin
data class PlaybackState(
    val song: Song?, val isPlaying: Boolean, val positionMs: Long, val durationMs: Long,
    val showVideo: Boolean, val hasVideo: Boolean, val shuffle: Boolean, val repeatOne: Boolean,
)

interface PlaybackController {
    val state: StateFlow<PlaybackState>
    fun play(song: Song); fun toggle(); fun next(); fun previous() // anterior: si van más de 3 s, reinicia
    fun seekTo(ms: Long); fun setShuffle(on: Boolean); fun setRepeatOne(on: Boolean)
    fun setVideoEnabled(enabled: Boolean) // false = solo audio
}

@Composable expect fun VideoSurface(controller: PlaybackController, modifier: Modifier)
```

### Android (Media3)

- `ExoPlayer` dentro de un `MediaSessionService` para que siga sonando en segundo plano.
- Solo audio: `player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true).build()`.
- `VideoSurface`: `AndroidView` con `PlayerView` (sin controles propios: `useController = false`; los controles son de la app).
- Pantalla completa: `requestedOrientation = SCREEN_ORIENTATION_SENSOR_LANDSCAPE` + `WindowInsetsControllerCompat.hide(systemBars())`.

### Escritorio

- `vlcj` con `EmbeddedMediaPlayerComponent` dentro de `SwingPanel`, o renderizado a `ImageBitmap` con `CallbackVideoSurface` si se necesitan esquinas redondeadas o recortes del tema.
- Documentar que VLC debe estar instalado o empaquetarse con la app.

---

## 8. Animaciones (Compose)

`speed` = multiplicador del tempo (solo Aurora; en los demás temas vale 1).

| Elemento | Especificación | Compose |
|---|---|---|
| Cambio de tema | Colores en 600 ms | `animateColorAsState(tween(600))` |
| Color de Aurora al cambiar canción | 1200 ms (énfasis 800 ms) | `animateColorAsState` |
| Entrada de pantalla | Opacidad 0→1 y subir 14.dp, 420 ms | `AnimatedContent` + `fadeIn` + `slideInVertically` |
| Reproductor y Letra | Suben 60.dp como hoja, 500 ms | `slideInVertically { 60.dp }` |
| Curva estándar | cubic-bezier(.2,.8,.2,1) | `CubicBezierEasing(.2f, .8f, .2f, 1f)` |
| Portada al reproducir | Escala 0.9 → 1 con rebote, 700 ms | `spring(dampingRatio = .55f, stiffness = 300f)` |
| Portada "respira" (Aurora) | 1 → 1.025, 4 s × speed | `rememberInfiniteTransition` |
| Filas de lista | Desde 16.dp a la derecha, 450 ms, 40 ms entre filas | `LaunchedEffect` con retraso por índice |
| Letra | Desplazamiento 700 ms; cambio de estado de línea 500 ms | `animateScrollToItem` con offset + `animateFloatAsState` |
| Me gusta | Escala 1 → 1.45 → 0.9 → 1, 450 ms | `Animatable` con `keyframes` |
| Ecualizador | 3 barras, escala vertical 0.3 → 1, 0.6/0.8/1.0 s × speed | `infiniteRepeatable` |
| Luces Aurora | 18/22/26 s × speed, pausadas en pausa | `infiniteRepeatable(RepeatMode.Reverse)` |
| Círculo de Póster | 1 vuelta en 8 s, solo al sonar | rotación infinita |
| Aro de Seda | 1 vuelta en 60 s, solo al sonar | rotación infinita |
| Video simulado | No aplica: en producción es video real | — |
| Mini reproductor y pestañas | Salen 160.dp / 100.dp hacia abajo en Reproductor y Letra, 450 ms | `AnimatedVisibility` |
| Aviso (toast) | Baja 20.dp y aparece, 400 ms, visible 2 s | `AnimatedVisibility` |

**Reducir movimiento:** si el sistema lo pide (Android: `Settings.Global.ANIMATOR_DURATION_SCALE == 0`), desactivar animaciones infinitas y usar transiciones instantáneas.

---

## 9. Versión de escritorio

### Distribución (ventana de referencia 1280 × 800)

```
┌──────────────────────────── barra de título (36) ────────────────────────────┐
│ Lateral 232     │ Contenido principal (flexible)        │ Panel derecho 300   │
│ Logo            │ Atrás/Adelante · Búsqueda · Avatar    │ "Reproduciendo"     │
│ Inicio          │                                       │ Portada 252         │
│ Videos          │  (vista actual)                       │ Título · Me gusta   │
│ Buscar          │                                       │ Tarjeta de letra    │
│ Biblioteca      │                                       │ A continuación (4)  │
│ Tus listas      │                                       │                     │
├──────────────────────── barra de reproducción (86) ──────────────────────────┤
│ Portada 56 · título · ♥ │ ⤨ ⏮ ▶ ⏭ ↻  +  progreso con tiempos │ Letra Video Panel Cola Volumen │
└──────────────────────────────────────────────────────────────────────────────┘
```

- La **barra de reproducción** está siempre visible y siempre muestra la portada.
- Tocar la portada o el título de la barra abre "Reproduciendo".
- El botón "Panel" muestra u oculta el panel derecho.

### Vistas

| Vista | Contenido central | Panel derecho |
|---|---|---|
| Inicio | Saludo, chips, "Sigue escuchando" (5 columnas), "Videos musicales" (3 columnas 16:9), tabla "Escuchado hace poco" | Reproduciendo |
| Lista | Cabecera con portada 200 y título grande, acciones, tabla con columnas #, Título (portada + nombre + artista), Álbum, Tempo, Duración | Reproduciendo |
| Reproduciendo | Dos columnas: portada 340 + título + artista + "Me gusta" y "Ver video"; a la derecha, la letra (34 sp) | Oculto |
| Video | Modo cine: video 16:9 a todo el ancho; debajo portada 48, título, vistas y chips | **Más videos** |

### Tamaños de ventana

| Ancho | Comportamiento |
|---|---|
| 1280 o más | Tres columnas completas |
| 1024 a 1279 | Panel derecho flotante sobre el contenido, se abre con su botón |
| menos de 1024 | Lateral reducido a íconos (72) |
| Mínimo | 960 × 600 |

### Atajos de teclado

| Tecla | Acción |
|---|---|
| Espacio | Reproducir o pausar |
| ← / → | Retroceder o avanzar 5 s |
| Ctrl + ← / → | Canción anterior o siguiente |
| L | Abrir o cerrar la letra |
| V | Abrir el video |
| F | Video en pantalla completa |
| Esc | Salir de pantalla completa |

```kotlin
// desktopApp/main.kt
fun main() = application {
    val windowState = rememberWindowState(size = DpSize(1280.dp, 800.dp))
    Window(onCloseRequest = ::exitApplication, state = windowState, title = "Música",
        onPreviewKeyEvent = { e -> Shortcuts.handle(e, controller, navigator) }) {
        window.minimumSize = java.awt.Dimension(960, 600)
        MusicAppTheme(currentTheme) { DesktopShell() }
    }
}

// Shortcuts.kt
object Shortcuts {
    fun handle(e: KeyEvent, c: PlaybackController, nav: DesktopNavigator): Boolean {
        if (e.type != KeyEventType.KeyDown) return false
        return when {
            e.key == Key.Spacebar -> { c.toggle(); true }
            e.key == Key.DirectionRight && e.isCtrlPressed -> { c.next(); true }
            e.key == Key.DirectionLeft && e.isCtrlPressed -> { c.previous(); true }
            e.key == Key.DirectionRight -> { c.seekTo(c.state.value.positionMs + 5_000); true }
            e.key == Key.DirectionLeft -> { c.seekTo((c.state.value.positionMs - 5_000).coerceAtLeast(0)); true }
            e.key == Key.L -> { nav.toggleLyrics(); true }
            e.key == Key.V -> { nav.open(DesktopView.VIDEO); true }
            e.key == Key.F -> { nav.toggleFullscreen(); true }
            e.key == Key.Escape -> nav.exitFullscreen()
            else -> false
        }
    }
}
```

Detalles por tema en escritorio (además de lo de la sección 4):

- **Póster**: separadores de 3.dp en `#1F2BFF` entre lateral, panel y barra. Elemento activo del menú con fondo azul y texto claro.
- **Pétalo**: menú activo con fondo blanco y sombra suave; barra inferior blanca.
- **Seda**: texto del panel derecho centrado; aros dorados en la portada del panel y en "Reproduciendo".
- **Carbono**: menú activo con línea naranja de 3.dp a la izquierda; títulos de tabla en mayúsculas espaciadas.
- **Estadio**: menú activo con fondo rojo; números de pista en Bebas Neue.
- **Aurora**: lateral con blanco al 3,5 %; luces de 420–520.dp detrás de toda la ventana.

---

## 10. Accesibilidad

- Toda portada lleva `contentDescription = "Portada de <título>"`.
- Botones de ícono con descripción en español ("Reproducir o pausar", "Me gusta", "Pantalla completa"…).
- Área táctil mínima de 48.dp en móvil.
- Contraste: el énfasis extraído ya se fuerza a luminosidad 58–72 %. Revisar Póster y Bruma con verificador de contraste en textos pequeños.
- En escritorio, foco visible en todos los controles y navegación completa con Tab.
- Anunciar el cambio de canción a lectores de pantalla (`liveRegion` en el título del reproductor).

---

## 11. Plan de trabajo sugerido

Trabajar por fases y abrir un PR (o commit) por fase:

1. **Tema base.** Crear `ThemeId`, `AppTheme`, `LocalAppTheme`, formas y fuentes. Migrar los componentes existentes para que lean del tema. Implementar solo **Aurora** y **Bruma** primero (uno oscuro dinámico y uno claro simple).
2. **Color dinámico.** `moodFromBpm`, `extractAccent` con pruebas unitarias, caché por portada y animación de colores en Aurora.
3. **Los otros 5 temas.** Póster, Pétalo, Seda, Carbono y Estadio con sus formas, decoraciones y rasgos.
4. **Selector de tema** en Ajustes, con vista previa, persistencia y transición.
5. **Módulo de video** en Android: controlador común, superficie, selector Canción/Video, pantalla completa, "Solo audio" y pestaña Videos.
6. **Escritorio.** Módulo `desktopApp`, `DesktopShell`, barra de reproducción, panel derecho, vistas, atajos y video con vlcj.
7. **Pulido.** Animaciones de la sección 8, reducir movimiento y accesibilidad.

### Pruebas mínimas

- `moodFromBpm(70)` da tono azul (≈225°), `moodFromBpm(140)` da rojo (≈360°/0°), `moodFromBpm(null)` usa 100 BPM.
- `extractAccent` de una imagen gris devuelve `null`; de una imagen roja devuelve un rojo con luminosidad entre 0.58 y 0.72.
- Cambiar de "Canción" a "Video" no cambia `positionMs`.
- El tema elegido sobrevive a reiniciar la app.
- Prueba de captura (screenshot test) de Reproductor y Video en los 7 temas.

### Lista de verificación final

- [ ] Los 7 temas se pueden elegir y se guardan.
- [ ] Ningún componente tiene colores, fuentes o formas fijas.
- [ ] La portada se ve en Inicio, Lista, Reproductor, Video, Letra, mini reproductor y barra de escritorio.
- [ ] Aurora cambia de color con el tempo y la portada.
- [ ] El video comparte la reproducción con el audio y tiene pantalla completa.
- [ ] Escritorio: tres columnas, panel ocultable, atajos de teclado y tamaños de ventana.
- [ ] Reducir movimiento desactiva las animaciones infinitas.

---

## 12. Mensaje para iniciar en Claude Code

```
Lee docs/design/TEMAS_VIDEO_ESCRITORIO.md y abre como referencia
docs/design/temas-app-musica.html y docs/design/escritorio-app-musica.html.

Primero revisa la estructura actual del proyecto y dime:
1) si la UI está en Jetpack Compose, 2) cómo está hecho el reproductor hoy,
3) qué cambiarías del plan de la sección 11 para adaptarlo a este código.

No escribas código hasta que te confirme. Después trabaja fase por fase,
empezando por la fase 1, y al terminar cada fase muéstrame qué cambió.
Mantén todos los textos de la interfaz en español.
```
