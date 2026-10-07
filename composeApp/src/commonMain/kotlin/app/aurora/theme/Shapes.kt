package app.aurora.theme

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** Pétalo: arco (semicírculo arriba, esquinas suaves abajo). */
val ArchShape = RoundedCornerShape(CornerSize(50), CornerSize(50), CornerSize(28.dp), CornerSize(28.dp))
val ArchShapeSmall = RoundedCornerShape(CornerSize(50), CornerSize(50), CornerSize(10.dp), CornerSize(10.dp))

/** Carbono: esquinas cortadas arriba a la izquierda y abajo a la derecha. */
val CarbonArtLarge = CutCornerShape(topStart = 20.dp, bottomEnd = 20.dp)
val CarbonArtSmall = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp)
val CarbonPlay = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp)

/** Estadio: paralelogramo inclinado (botón principal y chips). */
class ParallelogramShape(private val skewFraction: Float = 0.16f, private val byHeight: Boolean = false) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val dx = if (byHeight) size.height * skewFraction else size.width * skewFraction
        return Outline.Generic(Path().apply {
            moveTo(dx, 0f); lineTo(size.width, 0f); lineTo(size.width - dx, size.height); lineTo(0f, size.height); close()
        })
    }
}

/**
 * Acuarela: gota irregular. Equivale al `border-radius` de la maqueta: cada esquina es un cuarto de elipse cuyos
 * radios (en fracción del ancho y del alto) van de "42% 58% 55% 45% / 48% 42% 58% 52%" ([phase] = 0) a
 * "56% 44% 45% 55% / 45% 55% 45% 55%" ([phase] = 1). Animar [phase] hace que la portada cambie de forma.
 */
data class OrganicBlobShape(val phase: Float = 0f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        fun mix(a: Float, b: Float) = a + (b - a) * phase
        val w = size.width
        val h = size.height
        // Radios horizontales (arriba izq., arriba der., abajo der., abajo izq.) y verticales, en ese orden.
        val rx = floatArrayOf(mix(.42f, .56f), mix(.58f, .44f), mix(.55f, .45f), mix(.45f, .55f)).map { it * w }
        val ry = floatArrayOf(mix(.48f, .45f), mix(.42f, .55f), mix(.58f, .45f), mix(.52f, .55f)).map { it * h }
        val k = .5523f // curva de Bézier que aproxima un cuarto de elipse
        return Outline.Generic(Path().apply {
            moveTo(rx[0], 0f)
            lineTo(w - rx[1], 0f)
            cubicTo(w - rx[1] + k * rx[1], 0f, w, ry[1] - k * ry[1], w, ry[1])
            lineTo(w, h - ry[2])
            cubicTo(w, h - ry[2] + k * ry[2], w - rx[2] + k * rx[2], h, w - rx[2], h)
            lineTo(rx[3], h)
            cubicTo(rx[3] - k * rx[3], h, 0f, h - ry[3] + k * ry[3], 0f, h - ry[3])
            lineTo(0f, ry[0])
            cubicTo(0f, ry[0] - k * ry[0], rx[0] - k * rx[0], 0f, rx[0], 0f)
            close()
        })
    }
}
