package app.aurora.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.unit.dp

/** Medidas comunes (spec §6). */
object AuroraDimens {
    val ScreenPadding = 18.dp
    val IconButton = 40.dp
    val Icon = 22.dp
    val PlayButtonPlayer = 70.dp
    val PlayButtonList = 58.dp
    val CoverXs = 40.dp
    val CoverRow = 46.dp
    val CoverCard = 132.dp
    val CoverList = 180.dp
    val CoverPlayer = 250.dp
    val MiniPlayerHeight = 60.dp
}

/** Medidas de escritorio (ventana de referencia 1280 x 800). */
object DesktopDimens {
    val Sidebar = 232.dp
    val SidebarCompact = 72.dp
    val RightPanel = 300.dp
    val PlayerBar = 86.dp
    val ContentPadding = 28.dp
    val Hero = 252.dp
    val NowPlayingHero = 340.dp
    /** Desde este ancho se ven las tres columnas. */
    val FullLayout = 1280.dp
    /** Por debajo de este ancho el lateral se reduce a íconos. */
    val CompactSidebar = 1024.dp
}

object AuroraMotion {
    val Ease = CubicBezierEasing(.2f, .8f, .2f, 1f)
    val Bounce = CubicBezierEasing(.34f, 1.4f, .5f, 1f)
    const val COLOR_FADE_MS = 1200
}
