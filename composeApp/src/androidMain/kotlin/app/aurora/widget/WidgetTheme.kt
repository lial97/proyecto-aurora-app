package app.aurora.widget

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceTheme
import androidx.glance.text.FontFamily
import androidx.glance.text.FontStyle
import androidx.glance.text.FontWeight
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.aurora.shared.R

/** Forma de la portada (Glance no recorta imágenes: se recorta el bitmap antes de enviarlo). */
enum class CoverShape {
    ROUNDED, SQUARE, ARCH, CIRCLE, CUT,
    /** Casete: esquinas suaves y borde crema (la carátula en su estuche). */
    CREAM_FRAME,
    /** Acuarela: gota irregular. */
    BLOB,
    /** Bosque: marco de cobre separado de la portada por una línea del color de fondo. */
    COPPER_FRAME,
    /** Rockola: círculo con aro negro, como un disco. */
    VINYL,
}

/** Forma del botón principal: un fondo que se tiñe con el color del tema. [widthRatio] = ancho / alto. */
enum class PlayShape(val drawable: Int, val widthRatio: Float) {
    CIRCLE(R.drawable.widget_circle, 1f),
    SQUARE(R.drawable.widget_play_square, 1f),
    CUT(R.drawable.widget_play_cut, 1f),
    PARALLELOGRAM(R.drawable.widget_play_parallelogram, 54f / 44f),
}

/**
 * Cómo se ve un widget en un tema (maqueta: clases `.w.aurora`, `.w.poster`, etc.). Los widgets solo
 * pueden usar fuentes del sistema: se elige la más parecida a la de cada tema.
 */
class WidgetStyle(
    /** Identifica el estilo (para no volver a recortar portadas si no cambia). */
    val key: String,
    /** Fondo liso, o `null` para el degradado de Aurora desde el color de la portada. */
    val background: ColorProvider?,
    val border: ColorProvider? = null,
    val borderWidth: Dp = 0.dp,
    val ink: ColorProvider,
    val muted: ColorProvider,
    val accent: ColorProvider,
    val play: ColorProvider,
    val playInk: ColorProvider,
    val track: ColorProvider,
    val divider: ColorProvider,
    val corner: Dp,
    val coverShape: CoverShape,
    /** Radio de la portada grande (o el corte en [CoverShape.CUT], o el radio de abajo en [CoverShape.ARCH]). */
    val coverRadius: Dp,
    val playShape: PlayShape = PlayShape.CIRCLE,
    val font: FontFamily = FontFamily.SansSerif,
    val italic: Boolean = false,
    val case: app.aurora.theme.TitleCase = app.aurora.theme.TitleCase.NORMAL,
) {
    /** Título con las mayúsculas o minúsculas del tema. */
    fun title(text: String): String = when (case) {
        app.aurora.theme.TitleCase.LOWER -> text.lowercase()
        app.aurora.theme.TitleCase.UPPER -> text.uppercase()
        app.aurora.theme.TitleCase.NORMAL -> text
    }

    fun titleStyle(size: TextUnit, color: ColorProvider = ink) =
        TextStyle(color = color, fontSize = size, fontWeight = FontWeight.Bold, fontFamily = font, fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal)

    fun mutedStyle(size: TextUnit, color: ColorProvider = muted) = TextStyle(color = color, fontSize = size)

    fun bodyStyle(size: TextUnit) = TextStyle(color = ink, fontSize = size)
}

private fun c(argb: Long) = ColorProvider(Color(argb))

/** El estilo del widget según el tema de la app, o Material You si se eligió en Ajustes (Android 12+). */
@Composable
fun widgetStyle(s: WidgetSnapshot): WidgetStyle {
    if (s.wallpaper && Build.VERSION.SDK_INT >= 31) {
        val m = GlanceTheme.colors
        return WidgetStyle(
            key = "you", background = m.primaryContainer, ink = m.onPrimaryContainer, muted = m.onSurfaceVariant,
            accent = m.primary, play = m.primary, playInk = m.onPrimary, track = m.inversePrimary, divider = m.inversePrimary,
            corner = 28.dp, coverShape = CoverShape.ROUNDED, coverRadius = 18.dp,
        )
    }
    val condensed = FontFamily("sans-serif-condensed")
    // Material You pedido en un Android sin colores dinámicos (11 o anterior): se ve como Aurora.
    return when (if (s.wallpaper) "AURORA" else s.theme) {
        "POSTER" -> WidgetStyle(
            key = "poster", background = c(0xFFEDEBFF), border = c(0xFF1F2BFF), borderWidth = 2.5.dp,
            ink = c(0xFF1F2BFF), muted = c(0xB31F2BFF), accent = c(0xFF1F2BFF), play = c(0xFF1F2BFF), playInk = c(0xFFEDEBFF),
            track = c(0xFFD3D0FF), divider = c(0x1F1F2BFF), corner = 4.dp, coverShape = CoverShape.SQUARE, coverRadius = 0.dp,
            playShape = PlayShape.SQUARE, font = FontFamily("sans-serif-black"), case = app.aurora.theme.TitleCase.LOWER,
        )
        "PETALO" -> WidgetStyle(
            key = "petalo", background = c(0xFFFFFFFF), ink = c(0xFF4A2B3A), muted = c(0xFF9A7383), accent = c(0xFFE0567A),
            play = c(0xFFE0567A), playInk = c(0xFFFFFFFF), track = c(0xFFF4D9E1), divider = c(0x1F4A2B3A),
            corner = 26.dp, coverShape = CoverShape.ARCH, coverRadius = 12.dp, font = FontFamily.Serif, italic = true,
        )
        "SEDA" -> WidgetStyle(
            key = "seda", background = c(0xFF1E0F18), border = c(0x59E8C9A0), borderWidth = 1.dp,
            ink = c(0xFFF6E9DD), muted = c(0x99F6E9DD), accent = c(0xFFE8C9A0), play = c(0xFFE8C9A0), playInk = c(0xFF1E0F18),
            track = c(0x33E8C9A0), divider = c(0x1FF6E9DD), corner = 20.dp, coverShape = CoverShape.CIRCLE, coverRadius = 0.dp,
            font = FontFamily.Serif, italic = true,
        )
        "CARBONO" -> WidgetStyle(
            key = "carbono", background = c(0xFF16191C), ink = c(0xFFE3E7EC), muted = c(0xFF8A929C), accent = c(0xFFFF7A1A),
            play = c(0xFFFF7A1A), playInk = c(0xFF121417), track = c(0xFF2C3138), divider = c(0x1FE3E7EC),
            corner = 8.dp, coverShape = CoverShape.CUT, coverRadius = 10.dp, playShape = PlayShape.CUT,
            font = condensed, case = app.aurora.theme.TitleCase.UPPER,
        )
        "ESTADIO" -> WidgetStyle(
            key = "estadio", background = c(0xFF0D1B2E), ink = c(0xFFFFFFFF), muted = c(0xFF9DB0C8), accent = c(0xFFE63946),
            play = c(0xFFE63946), playInk = c(0xFFFFFFFF), track = c(0xFF22395A), divider = c(0x1FFFFFFF),
            corner = 10.dp, coverShape = CoverShape.ROUNDED, coverRadius = 6.dp, playShape = PlayShape.PARALLELOGRAM,
            font = condensed, case = app.aurora.theme.TitleCase.UPPER,
        )
        "BRUMA" -> WidgetStyle(
            key = "bruma", background = c(0xFFF4F6F2), ink = c(0xFF1F2A24), muted = c(0xFF6B776F), accent = c(0xFF2F7D6A),
            play = c(0xFF1F2A24), playInk = c(0xFFECEFEA), track = c(0xFFD5DBD3), divider = c(0x1F1F2A24),
            corner = 24.dp, coverShape = CoverShape.ROUNDED, coverRadius = 14.dp,
        )
        "CASETE" -> WidgetStyle(
            key = "casete", background = c(0xFFE9B949), border = c(0xFF3B2416), borderWidth = 1.5.dp,
            ink = c(0xFF3B2416), muted = c(0xFF5A3D22), accent = c(0xFF1D524F), play = c(0xFF1D524F), playInk = c(0xFFF6E7C8),
            track = c(0x383B2416), divider = c(0x1F3B2416), corner = 12.dp, coverShape = CoverShape.CREAM_FRAME, coverRadius = 8.dp,
            playShape = PlayShape.SQUARE, font = FontFamily("sans-serif-black"),
        )
        "ACUARELA" -> WidgetStyle(
            key = "acuarela", background = c(0xFFFBF7F3), ink = c(0xFF3D2C5E), muted = c(0xFF6E5F88), accent = c(0xFF7B57D1),
            play = c(0xFF7B57D1), playInk = c(0xFFFFFFFF), track = c(0xFFEBE2F5), divider = c(0x1F3D2C5E),
            corner = 26.dp, coverShape = CoverShape.BLOB, coverRadius = 0.dp, font = FontFamily.Serif,
        )
        "BOSQUE" -> WidgetStyle(
            key = "bosque", background = c(0xFF122019), border = c(0x59E09452), borderWidth = 1.dp,
            ink = c(0xFFE7EFE6), muted = c(0xFF9DB5A6), accent = c(0xFFE09452), play = c(0xFFE09452), playInk = c(0xFF122019),
            track = c(0xFF2A4236), divider = c(0x1FE7EFE6), corner = 14.dp, coverShape = CoverShape.COPPER_FRAME, coverRadius = 10.dp,
            font = FontFamily.Serif,
        )
        "GRAFITO" -> WidgetStyle(
            key = "grafito", background = c(0xFF1A1B1E), ink = c(0xFFF1F2F4), muted = c(0xFFA0A4AC), accent = c(0xFF8AB4FF),
            play = c(0xFFF1F2F4), playInk = c(0xFF141517), track = c(0xFF2E3136), divider = c(0x1FF1F2F4),
            corner = 22.dp, coverShape = CoverShape.ROUNDED, coverRadius = 18.dp, font = FontFamily("sans-serif-medium"),
        )
        "ROCKOLA" -> WidgetStyle(
            key = "rockola", background = c(0xFFFFF4E2), ink = c(0xFF2A1B1F), muted = c(0xFF7A5F58), accent = c(0xFFC81E35),
            play = c(0xFFC81E35), playInk = c(0xFFFFF4E2), track = c(0xFFF0DCC0), divider = c(0x1F2A1B1F),
            corner = 24.dp, coverShape = CoverShape.VINYL, coverRadius = 0.dp, font = FontFamily.Cursive,
        )
        // Aurora: el énfasis y el fondo salen de la portada.
        else -> WidgetStyle(
            key = "aurora", background = null, ink = c(0xFFF2EEFF), muted = c(0xA6F2EEFF), accent = ColorProvider(Color(s.accent)),
            play = c(0xFFF2EEFF), playInk = c(0xFF1B1640), track = c(0x33FFFFFF), divider = c(0x1FF2EEFF),
            corner = 22.dp, coverShape = CoverShape.ROUNDED, coverRadius = 14.dp,
        )
    }
}

/**
 * Recorta [src] con la forma del tema. [radius] y [side] en dp: el radio se escala al tamaño del bitmap
 * (así una portada de 118 dp y una de 28 dp guardan la misma proporción que en la maqueta).
 */
fun shapeCover(src: Bitmap, shape: CoverShape, radius: Dp, side: Dp): Bitmap {
    if (shape == CoverShape.SQUARE) return src
    val w = src.width.toFloat(); val h = src.height.toFloat()
    val k = minOf(w, h) / side.value
    val r = radius.value * k
    val path = Path()
    when (shape) {
        CoverShape.ROUNDED -> path.addRoundRect(RectF(0f, 0f, w, h), r, r, Path.Direction.CW)
        CoverShape.CIRCLE -> path.addOval(RectF(0f, 0f, w, h), Path.Direction.CW)
        // Arco: arriba un medio círculo, abajo esquinas suaves.
        CoverShape.ARCH -> path.addRoundRect(RectF(0f, 0f, w, h), floatArrayOf(w / 2, w / 2, w / 2, w / 2, r, r, r, r), Path.Direction.CW)
        // Esquinas cortadas arriba a la izquierda y abajo a la derecha.
        CoverShape.CUT -> path.apply { moveTo(r, 0f); lineTo(w, 0f); lineTo(w, h - r); lineTo(w - r, h); lineTo(0f, h); lineTo(0f, r); close() }
        CoverShape.CREAM_FRAME, CoverShape.COPPER_FRAME -> path.addRoundRect(RectF(0f, 0f, w, h), r, r, Path.Direction.CW)
        CoverShape.VINYL -> path.addOval(RectF(0f, 0f, w, h), Path.Direction.CW)
        // La misma gota que la portada de Acuarela en la app.
        CoverShape.BLOB -> {
            val o = app.aurora.theme.OrganicBlobShape().createOutline(
                androidx.compose.ui.geometry.Size(w, h), androidx.compose.ui.unit.LayoutDirection.Ltr, androidx.compose.ui.unit.Density(1f),
            ) as androidx.compose.ui.graphics.Outline.Generic
            path.set(o.path.asAndroidPath())
        }
        CoverShape.SQUARE -> Unit
    }
    val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val cover = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
    // La portada reducida [inset] por cada lado (para dejar lugar a un marco o un aro).
    fun coverInset(inset: Float, oval: Boolean, rr: Float) {
        val m = android.graphics.Matrix().apply { setRectToRect(RectF(0f, 0f, w, h), RectF(inset, inset, w - inset, h - inset), android.graphics.Matrix.ScaleToFit.FILL) }
        cover.shader.setLocalMatrix(m)
        val rect = RectF(inset, inset, w - inset, h - inset)
        if (oval) canvas.drawOval(rect, cover) else canvas.drawRoundRect(rect, rr, rr, cover)
    }
    when (shape) {
        CoverShape.CREAM_FRAME -> {
            canvas.drawPath(path, fill.apply { color = 0xFFF6E7C8.toInt() })
            coverInset(3f * k, oval = false, rr = maxOf(r - 3f * k, 0f))
        }
        CoverShape.COPPER_FRAME -> {
            canvas.drawPath(path, fill.apply { color = 0xFFE09452.toInt() })
            canvas.drawRoundRect(RectF(1.5f * k, 1.5f * k, w - 1.5f * k, h - 1.5f * k), r, r, fill.apply { color = 0xFF122019.toInt() })
            coverInset(3.5f * k, oval = false, rr = maxOf(r - 3.5f * k, 0f))
        }
        CoverShape.VINYL -> {
            canvas.drawOval(RectF(0f, 0f, w, h), fill.apply { color = 0xFF1B1416.toInt() })
            coverInset(4f * k, oval = true, rr = 0f)
        }
        else -> canvas.drawPath(path, cover)
    }
    return out
}
