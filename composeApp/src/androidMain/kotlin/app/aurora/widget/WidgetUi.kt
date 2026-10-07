package app.aurora.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.height
import androidx.glance.layout.width
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.aurora.AuroraRuntime
import app.aurora.shared.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/** Fondo oscuro del tema Aurora (el degradado parte del color de la portada y termina en este). */
private const val AURORA_BACKGROUND = 0xFF1B1640.toInt()

const val EXTRA_OPEN = "app.aurora.ABRIR"
const val OPEN_PLAYER = "reproductor"

/** Si el proceso de la app no está vivo, nada suena (p. ej. después de reiniciar el teléfono). */
internal val WidgetSnapshot.playingNow: Boolean get() = playing && (trackId == WidgetSnapshot.PREVIEW_ID || AuroraRuntime.peek() != null)

/** Nada cargado (app cerrada o recién abierta): se ofrece "Continuar" con la última canción. */
internal val WidgetSnapshot.restingNow: Boolean get() = !empty && trackId != WidgetSnapshot.PREVIEW_ID && (resting || AuroraRuntime.peek() == null)

/** Abre la app en el reproductor. */
internal fun openPlayer(context: Context): Action = actionStartActivity(
    (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra(EXTRA_OPEN, OPEN_PLAYER),
)

internal fun widgetAction(what: String, index: Int = -1): Action =
    actionRunCallback<WidgetAction>(actionParametersOf(WidgetAction.KEY to what, WidgetAction.INDEX to index))

internal fun clock(sec: Int): String = "%d:%02d".format(sec / 60, sec % 60)

/** Marco del widget: fondo del tema (o degradado de Aurora), radio de las esquinas y borde si el tema lo tiene. */
@Composable
internal fun WidgetFrame(s: WidgetSnapshot, st: WidgetStyle, content: @Composable () -> Unit) {
    val outer = GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(st.corner)
    val gradient = if (st.background == null) cached("fondo:${s.deep}") { auroraBackground(s.deep) } else null
    val fill: GlanceModifier.() -> GlanceModifier = {
        val bg = st.background
        if (bg != null) background(bg) else background(ImageProvider(gradient!!), ContentScale.FillBounds)
    }
    val border = st.border
    if (border != null) {
        Box(outer.background(border).padding(st.borderWidth)) {
            Box(GlanceModifier.fillMaxSize().cornerRadius(st.corner - st.borderWidth).fill()) { content() }
        }
    } else Box(outer.fill()) { content() }
}

@Composable
internal fun IconButton(icon: Int, description: String, tint: ColorProvider, action: Action, side: Dp = 36.dp, iconSide: Dp = 22.dp) {
    Box(GlanceModifier.size(side).cornerRadius(side / 2).clickable(action), contentAlignment = Alignment.Center) {
        Image(ImageProvider(icon), description, GlanceModifier.size(iconSide), colorFilter = ColorFilter.tint(tint))
    }
}

/** "Continuar": vuelve a sonar la última canción (y su cola, si se guardó) sin abrir la app. */
@Composable
internal fun ContinueButton(st: WidgetStyle, height: Dp = 40.dp, modifier: GlanceModifier = GlanceModifier) {
    Row(
        modifier.height(height).cornerRadius(minOf(st.corner, height / 2)).background(st.play)
            .padding(start = 12.dp, end = 16.dp).clickable(widgetAction(WidgetAction.TOGGLE)),
        verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(ImageProvider(R.drawable.widget_ic_play), null, GlanceModifier.size(20.dp), colorFilter = ColorFilter.tint(st.playInk))
        Spacer(GlanceModifier.width(6.dp))
        // Fuente del sistema (no la del tema): en serif o cursiva la palabra no entraba en el botón.
        Text("Continuar", maxLines = 1, style = TextStyle(color = st.playInk, fontSize = 14.sp, fontWeight = FontWeight.Bold))
    }
}

/** Botón principal con la forma del tema (círculo, cuadrado, esquinas cortadas o paralelogramo). */
@Composable
internal fun PlayButton(st: WidgetStyle, playing: Boolean, side: Dp = 44.dp, resting: Boolean = false) {
    Box(
        GlanceModifier.size(side * st.playShape.widthRatio, side)
            .background(ImageProvider(st.playShape.drawable), ContentScale.FillBounds, ColorFilter.tint(st.play))
            .clickable(widgetAction(WidgetAction.TOGGLE)),
        contentAlignment = Alignment.Center,
    ) {
        Image(ImageProvider(if (playing) R.drawable.widget_ic_pause else R.drawable.widget_ic_play), if (playing) "Pausar" else if (resting) "Continuar" else "Reproducir",
            GlanceModifier.size(side / 2), colorFilter = ColorFilter.tint(st.playInk))
    }
}

/** Logo pequeño de Aurora arriba a la derecha. */
@Composable
internal fun Brand(st: WidgetStyle) {
    Box(GlanceModifier.fillMaxSize().padding(top = 10.dp, end = 12.dp), contentAlignment = Alignment.TopEnd) {
        Box(GlanceModifier.size(16.dp).cornerRadius(5.dp).background(st.accent), contentAlignment = Alignment.Center) {
            Image(ImageProvider(R.drawable.widget_ic_music), null, GlanceModifier.size(10.dp), colorFilter = ColorFilter.tint(st.playInk))
        }
    }
}

/** Nunca sonó nada: invita a abrir la app. */
@Composable
internal fun EmptyState(context: Context, st: WidgetStyle, compact: Boolean = false) {
    Column(GlanceModifier.fillMaxSize().padding(horizontal = 16.dp).clickable(openPlayer(context)), verticalAlignment = Alignment.CenterVertically) {
        Text(st.title("Elige música en Aurora"), maxLines = if (compact) 1 else 2, style = st.titleStyle(if (compact) 14.sp else 16.sp))
        Text("Toca para abrir la app", maxLines = 1, style = st.mutedStyle(12.5.sp))
    }
}

/**
 * Imágenes ya preparadas (portadas recortadas al tamaño real, fondos). Cada actualización arma los widgets
 * de nuevo y antes volvía a leer y recortar la portada cada vez: con esto solo se hace al cambiar algo.
 */
private val bitmaps = object : android.util.LruCache<String, Bitmap>(12 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
}

internal fun cached(key: String, make: () -> Bitmap): Bitmap = bitmaps.get(key) ?: make().also { bitmaps.put(key, it) }

/** Lado en píxeles de una imagen de [side] dp (no tiene sentido enviar más píxeles de los que se ven). */
private fun px(context: Context, side: Dp): Int = (side.value * context.resources.displayMetrics.density).toInt().coerceIn(32, 512)

private fun scaled(src: Bitmap, px: Int): Bitmap =
    if (src.width <= px) src else Bitmap.createScaledBitmap(src, px, px * src.height / src.width, true)

/** Portada de la canción actual (o la de Aurora) con la forma del tema; [side] es su lado en el widget. */
@Composable
internal fun rememberCover(context: Context, s: WidgetSnapshot, st: WidgetStyle, side: Dp, shaped: Boolean = true): Bitmap {
    val own = !s.empty && s.trackId != WidgetSnapshot.PREVIEW_ID
    return cached("portada:${if (own) s.coverVersion else 0}:${st.key}:${side.value}:$shaped") {
        val src = scaled(WidgetStore.coverFile(context).takeIf { own }?.let(::decode) ?: defaultCover(), px(context, side))
        if (shaped) shapeCover(src, st.coverShape, st.coverRadius, side) else src
    }
}

/** Portada pequeña de una canción de "A continuación" (radio a la mitad, como en la maqueta). */
@Composable
internal fun rememberNextCover(context: Context, id: String, st: WidgetStyle, side: Dp): Bitmap =
    cached("siguiente:$id:${st.key}:${side.value}") {
        val src = scaled(decode(WidgetStore.nextCoverFile(context, id)) ?: defaultCover(128), px(context, side))
        shapeCover(src, st.coverShape, st.coverRadius / 2, side)
    }

private fun decode(file: File): Bitmap? = file.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }

private fun defaultCover(px: Int = 0): Bitmap {
    val b = AuroraRuntime.defaultArtwork.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    return if (px > 0) Bitmap.createScaledBitmap(b, px, px, true) else b
}

/** Fondo del tema Aurora: degradado a 140° desde el color oscuro de la portada. */
internal fun auroraBackground(deep: Int, w: Int = 320, h: Int = 160): Bitmap {
    val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val bg = AURORA_BACKGROUND
    Canvas(b).drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
        shader = LinearGradient(0f, 0f, w.toFloat(), h * 1.2f, intArrayOf(deep, bg, bg), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
    })
    return b
}

/** Degradado oscuro de abajo para leer el texto sobre la portada (widget 2×2). */
internal fun bottomShade(): Bitmap {
    val b = Bitmap.createBitmap(4, 100, Bitmap.Config.ARGB_8888)
    Canvas(b).drawRect(0f, 0f, 4f, 100f, Paint().apply {
        shader = LinearGradient(0f, 0f, 0f, 100f, intArrayOf(0x00000000, 0x00000000, 0xBF000000.toInt()), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
    })
    return b
}

/** Receptor común: con la app abierta, un widget recién puesto muestra enseguida lo que suena. */
abstract class AuroraWidgetReceiver : GlanceAppWidgetReceiver() {
    override fun onUpdate(context: Context, appWidgetManager: android.appwidget.AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        if (AuroraRuntime.peek() != null) MainScope().launch { WidgetUpdater.push() }
    }

    /** Al agrandar o achicar (4×2 ↔ 4×3) se vuelve a dibujar enseguida con el tamaño nuevo. */
    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: android.appwidget.AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        if (AuroraRuntime.peek() != null) MainScope().launch { WidgetUpdater.push() }
    }
}

class PlayerWidgetReceiver : AuroraWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = PlayerWidget() }
class BarWidgetReceiver : AuroraWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = BarWidget() }
class CoverWidgetReceiver : AuroraWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = CoverWidget() }

/**
 * Botones de los widgets. Actúan sobre el estado de la app sin abrirla; si la app estaba cerrada,
 * se crea el estado (y con él el servicio de reproducción) y se espera a que vuelva la cola.
 */
class WidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: androidx.glance.GlanceId, parameters: ActionParameters) {
        val what = parameters[KEY] ?: return
        WidgetUpdater.reshow(context, glanceId)
        withContext(Dispatchers.Main) {
            val app = AuroraRuntime.get(context.applicationContext)
            if (app.player.state.value.current == null) {
                withTimeoutOrNull(7_000) { app.player.state.first { it.current != null } }
                // La cola no vuelve (p. ej. "Retomar" apagado): la última canción que sonó.
                if (app.player.state.value.current == null) app.lastTrack()?.let { app.player.restore(listOf(it), 0, 0) }
            }
            val p = app.player.state.value
            when (what) {
                TOGGLE -> app.player.togglePlay()
                NEXT -> app.player.next()
                PREVIOUS -> app.player.previous()
                LIKE -> app.toggleLike(p.current)
                SHUFFLE -> app.player.toggleShuffle()
                // Una canción de "A continuación": se salta a ella dentro de la misma cola.
                JUMP -> parameters[INDEX]?.takeIf { it in p.queue.indices }?.let { app.player.play(p.queue, it) }
            }
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        }
    }

    companion object {
        val KEY = ActionParameters.Key<String>("accion")
        val INDEX = ActionParameters.Key<Int>("posicion")
        const val TOGGLE = "reproducir"
        const val NEXT = "siguiente"
        const val PREVIOUS = "anterior"
        const val LIKE = "me_gusta"
        const val SHUFFLE = "aleatorio"
        const val JUMP = "saltar"
    }
}
