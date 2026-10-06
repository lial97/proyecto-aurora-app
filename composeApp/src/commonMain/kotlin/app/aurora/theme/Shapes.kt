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
