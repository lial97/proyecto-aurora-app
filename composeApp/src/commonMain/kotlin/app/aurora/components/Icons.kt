package app.aurora.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import app.aurora.theme.AuroraDimens

/** Íconos propios de trazo 2 px sobre una cuadrícula de 24 (spec §6). */
enum class AuroraIcon {
    Play, Pause, Next, Previous, Shuffle, Repeat, Heart, HeartFilled, Back, Down, More,
    Home, Search, Library, Video, Plus, Download, Lyrics, Info, Queue, Share, Edit, Sort,
    Settings, Folder, Refresh, Trash, Panel, Volume, Forward, Music, Palette, Check, Close, Expand, Shrink, ListAdd, ArrowUp, ArrowDown, Equalizer, Accessibility, Sparkle, Grip, Timer, Lock,
}

@Composable
fun Icon(icon: AuroraIcon, tint: Color, modifier: Modifier = Modifier, size: Dp = AuroraDimens.Icon) {
    Canvas(modifier.size(size)) {
        scale(this.size.minDimension / 24f, pivot = Offset.Zero) { drawIcon(icon, tint) }
    }
}

private fun path(block: Path.() -> Unit) = Path().apply(block)

private fun DrawScope.drawIcon(icon: AuroraIcon, c: Color) {
    val st = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun line(vararg pts: Float) = drawPath(path {
        moveTo(pts[0], pts[1]); var i = 2
        while (i < pts.size) { lineTo(pts[i], pts[i + 1]); i += 2 }
    }, c, style = st)

    when (icon) {
        AuroraIcon.Play -> drawPath(path { moveTo(7f, 4.5f); lineTo(19.5f, 12f); lineTo(7f, 19.5f); close() }, c, style = Fill)
        AuroraIcon.Pause -> {
            drawRoundRect(c, Offset(6f, 4.5f), Size(4f, 15f), androidx.compose.ui.geometry.CornerRadius(1.2f))
            drawRoundRect(c, Offset(14f, 4.5f), Size(4f, 15f), androidx.compose.ui.geometry.CornerRadius(1.2f))
        }
        AuroraIcon.Next -> {
            drawPath(path { moveTo(5f, 5f); lineTo(15f, 12f); lineTo(5f, 19f); close() }, c, style = Fill)
            line(18.5f, 5f, 18.5f, 19f)
        }
        AuroraIcon.Previous -> {
            drawPath(path { moveTo(19f, 5f); lineTo(9f, 12f); lineTo(19f, 19f); close() }, c, style = Fill)
            line(5.5f, 5f, 5.5f, 19f)
        }
        AuroraIcon.Shuffle -> {
            line(3f, 7f, 7f, 7f, 15f, 17f, 21f, 17f); line(18f, 14f, 21f, 17f, 18f, 20f)
            line(3f, 17f, 7f, 17f, 9.5f, 13.8f); line(13.5f, 9.6f, 15f, 7f, 21f, 7f); line(18f, 4f, 21f, 7f, 18f, 10f)
        }
        AuroraIcon.Repeat -> {
            line(4f, 11f, 4f, 9f, 7f, 6f, 20f, 6f); line(17f, 3f, 20f, 6f, 17f, 9f)
            line(20f, 13f, 20f, 15f, 17f, 18f, 4f, 18f); line(7f, 21f, 4f, 18f, 7f, 15f)
        }
        AuroraIcon.Heart, AuroraIcon.HeartFilled -> {
            val p = path {
                moveTo(12f, 20f)
                cubicTo(5f, 15.5f, 2.5f, 12f, 2.5f, 8.6f)
                cubicTo(2.5f, 5.8f, 4.7f, 3.8f, 7.3f, 3.8f)
                cubicTo(9.2f, 3.8f, 10.9f, 4.9f, 12f, 6.6f)
                cubicTo(13.1f, 4.9f, 14.8f, 3.8f, 16.7f, 3.8f)
                cubicTo(19.3f, 3.8f, 21.5f, 5.8f, 21.5f, 8.6f)
                cubicTo(21.5f, 12f, 19f, 15.5f, 12f, 20f)
                close()
            }
            drawPath(p, c, style = if (icon == AuroraIcon.HeartFilled) Fill else st)
        }
        AuroraIcon.Back -> line(15f, 5f, 8f, 12f, 15f, 19f)
        AuroraIcon.Down -> line(5f, 9f, 12f, 16f, 19f, 9f)
        AuroraIcon.More -> listOf(5f, 12f, 19f).forEach { drawCircle(c, 1.8f, Offset(it, 12f)) }
        AuroraIcon.Home -> line(3.5f, 11f, 12f, 4f, 20.5f, 11f, 20.5f, 20f, 14.5f, 20f, 14.5f, 14.5f, 9.5f, 14.5f, 9.5f, 20f, 3.5f, 20f, 3.5f, 11f)
        AuroraIcon.Search -> { drawCircle(c, 6.5f, Offset(10.5f, 10.5f), style = st); line(15.5f, 15.5f, 20.5f, 20.5f) }
        AuroraIcon.Library -> { line(5f, 4f, 5f, 20f); line(10f, 4f, 10f, 20f); line(14.5f, 4.8f, 19.5f, 19.2f) }
        AuroraIcon.Video -> {
            drawRoundRect(c, Offset(2.5f, 5.5f), Size(14f, 13f), androidx.compose.ui.geometry.CornerRadius(3f), style = st)
            line(16.5f, 10f, 21.5f, 7f, 21.5f, 17f, 16.5f, 14f)
        }
        AuroraIcon.Plus -> { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) }
        AuroraIcon.Download -> { drawCircle(c, 9f, Offset(12f, 12f), style = st); line(12f, 7.5f, 12f, 16f); line(8.5f, 12.5f, 12f, 16f, 15.5f, 12.5f) }
        AuroraIcon.Lyrics -> { line(4f, 6f, 20f, 6f); line(4f, 11f, 16f, 11f); line(4f, 16f, 11f, 16f); drawCircle(c, 2.2f, Offset(17f, 17.5f), style = st); line(19.2f, 17.5f, 19.2f, 11.5f) }
        AuroraIcon.Info -> { drawCircle(c, 9f, Offset(12f, 12f), style = st); line(12f, 11f, 12f, 16.5f); drawCircle(c, 1.2f, Offset(12f, 7.6f)) }
        AuroraIcon.Queue -> { line(4f, 6f, 20f, 6f); line(4f, 11f, 20f, 11f); line(4f, 16f, 12f, 16f); drawPath(path { moveTo(15f, 14f); lineTo(20f, 17f); lineTo(15f, 20f); close() }, c, style = Fill) }
        AuroraIcon.Share -> { line(12f, 3.5f, 12f, 15f); line(7.5f, 8f, 12f, 3.5f, 16.5f, 8f); line(5f, 12f, 5f, 20f, 19f, 20f, 19f, 12f) }
        AuroraIcon.Edit -> { line(4f, 20f, 4.6f, 16f, 15.5f, 5f, 19f, 8.5f, 8f, 19.4f, 4f, 20f); line(13f, 7.5f, 16.5f, 11f) }
        AuroraIcon.Settings -> {
            drawCircle(c, 3f, Offset(12f, 12f), style = st)
            for (k in 0 until 8) {
                val a = k * kotlin.math.PI.toFloat() / 4f
                line(12f + 6.2f * kotlin.math.cos(a), 12f + 6.2f * kotlin.math.sin(a), 12f + 8.8f * kotlin.math.cos(a), 12f + 8.8f * kotlin.math.sin(a))
            }
            drawCircle(c, 6.2f, Offset(12f, 12f), style = st)
        }
        AuroraIcon.Folder -> line(3f, 7f, 3f, 19f, 21f, 19f, 21f, 8.5f, 11.5f, 8.5f, 9.5f, 5.5f, 3f, 5.5f, 3f, 7f)
        AuroraIcon.Refresh -> {
            drawArc(c, -60f, 300f, false, Offset(4.5f, 4.5f), Size(15f, 15f), style = st)
            line(15.5f, 3.5f, 19.4f, 5.6f, 17.3f, 9.4f)
        }
        AuroraIcon.Trash -> { line(4f, 7f, 20f, 7f); line(9f, 7f, 9f, 4f, 15f, 4f, 15f, 7f); line(6f, 7f, 7f, 20f, 17f, 20f, 18f, 7f) }
        AuroraIcon.Panel -> {
            drawRoundRect(c, Offset(3f, 4f), Size(18f, 16f), androidx.compose.ui.geometry.CornerRadius(2.5f), style = st)
            line(15f, 4f, 15f, 20f)
        }
        AuroraIcon.Volume -> { line(11f, 5f, 6f, 9f, 3f, 9f, 3f, 15f, 6f, 15f, 11f, 19f, 11f, 5f); drawArc(c, -45f, 90f, false, Offset(10f, 7f), Size(10f, 10f), style = st) }
        AuroraIcon.Forward -> line(9f, 5f, 16f, 12f, 9f, 19f)
        AuroraIcon.Music -> { line(9f, 18f, 9f, 5f, 20f, 3f, 20f, 16f); drawCircle(c, 3f, Offset(6f, 18f), style = st); drawCircle(c, 3f, Offset(17f, 16f), style = st) }
        AuroraIcon.Palette -> {
            drawCircle(c, 9f, Offset(12f, 12f), style = st)
            listOf(Offset(8f, 9f), Offset(12f, 7f), Offset(16f, 9.5f), Offset(8.5f, 14f)).forEach { drawCircle(c, 1.4f, it) }
        }
        AuroraIcon.Check -> line(5f, 12.5f, 10f, 17.5f, 19.5f, 7f)
        AuroraIcon.Close -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
        AuroraIcon.Expand -> { line(4f, 9f, 4f, 4f, 9f, 4f); line(20f, 9f, 20f, 4f, 15f, 4f); line(4f, 15f, 4f, 20f, 9f, 20f); line(20f, 15f, 20f, 20f, 15f, 20f) }
        AuroraIcon.Shrink -> { line(9f, 4f, 9f, 9f, 4f, 9f); line(15f, 4f, 15f, 9f, 20f, 9f); line(9f, 20f, 9f, 15f, 4f, 15f); line(15f, 20f, 15f, 15f, 20f, 15f) }
        AuroraIcon.ListAdd -> { line(4f, 6f, 15f, 6f); line(4f, 11f, 15f, 11f); line(4f, 16f, 10f, 16f); line(17.5f, 13f, 17.5f, 20f); line(14f, 16.5f, 21f, 16.5f) }
        AuroraIcon.ArrowUp -> { line(12f, 19f, 12f, 5f); line(6f, 11f, 12f, 5f, 18f, 11f) }
        AuroraIcon.ArrowDown -> { line(12f, 5f, 12f, 19f); line(6f, 13f, 12f, 19f, 18f, 13f) }
        AuroraIcon.Equalizer -> { line(5f, 4f, 5f, 20f); line(12f, 4f, 12f, 20f); line(19f, 4f, 19f, 20f); listOf(Offset(5f, 14f), Offset(12f, 8f), Offset(19f, 15f)).forEach { drawCircle(c, 2.4f, it) } }
        AuroraIcon.Accessibility -> { drawCircle(c, 1.8f, Offset(12f, 4.5f)); line(5f, 8.5f, 12f, 10f, 19f, 8.5f); line(12f, 10f, 12f, 15f); line(8.5f, 21f, 12f, 15f, 15.5f, 21f) }
        AuroraIcon.Sparkle -> { line(12f, 3f, 12f, 21f); line(3f, 12f, 21f, 12f); line(6f, 6f, 9f, 9f); line(15f, 15f, 18f, 18f); line(18f, 6f, 15f, 9f); line(9f, 15f, 6f, 18f) }
        AuroraIcon.Grip -> listOf(9f, 15f).forEach { x -> listOf(6f, 12f, 18f).forEach { y -> drawCircle(c, 1.5f, Offset(x, y)) } }
        AuroraIcon.Lock -> {
            drawRoundRect(c, Offset(5f, 10.5f), Size(14f, 10f), androidx.compose.ui.geometry.CornerRadius(2.5f), style = st)
            line(8.5f, 10.5f, 8.5f, 7.5f); line(15.5f, 10.5f, 15.5f, 7.5f)
            drawArc(c, 180f, 180f, false, Offset(8.5f, 4f), Size(7f, 7f), style = st)
            drawCircle(c, 1.3f, Offset(12f, 15.5f))
        }
        AuroraIcon.Timer -> { drawCircle(c, 8f, Offset(12f, 13f), style = st); line(12f, 13f, 12f, 8.5f); line(9.5f, 2.5f, 14.5f, 2.5f) }
        AuroraIcon.Sort -> { line(7f, 4f, 7f, 20f); line(3.5f, 16.5f, 7f, 20f, 10.5f, 16.5f); line(17f, 20f, 17f, 4f); line(13.5f, 7.5f, 17f, 4f, 20.5f, 7.5f) }
    }
}
