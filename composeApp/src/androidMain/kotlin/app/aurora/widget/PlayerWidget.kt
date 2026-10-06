package app.aurora.widget

import android.content.Context
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.Text
import app.aurora.shared.R

/**
 * Widget "Aurora · Reproductor". En 4×2: portada grande, título, artista, progreso con tiempos y
 * Me gusta, anterior, reproducir/pausa y siguiente. Al agrandarlo a 4×3 suma Aleatorio y
 * "A continuación" con las 2 siguientes (se tocan para reproducirlas).
 */
class PlayerWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val flow = WidgetStore.flow(context)
        provideContent {
            val snap by flow.collectAsState()
            Content(snap ?: WidgetSnapshot())
        }
    }

    /** Vista previa del selector de widgets (Android 15+): el widget real con una canción de ejemplo. */
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent { Content(WidgetSnapshot.preview()) }
    }

    @Composable
    private fun Content(s: WidgetSnapshot) {
        val context = LocalContext.current
        val size = LocalSize.current
        val st = widgetStyle(s)
        WidgetFrame(s, st) {
            when {
                s.empty -> EmptyState(context, st)
                size.height >= LARGE_MIN_HEIGHT -> Large(context, s, st)
                else -> Medium(context, s, st, artSide = (size.height - 24.dp).coerceIn(56.dp, 140.dp))
            }
            Brand(st)
        }
    }

    /** 4×2: portada a la izquierda y todo lo demás a la derecha. */
    @Composable
    private fun Medium(context: Context, s: WidgetSnapshot, st: WidgetStyle, artSide: Dp) {
        val playing = s.playingNow
        val cover = rememberCover(context, s, st, artSide)
        val open = openPlayer(context)
        Row(GlanceModifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(ImageProvider(cover), "Portada de ${s.title}", GlanceModifier.size(artSide).clickable(open), contentScale = ContentScale.Crop)
            Spacer(GlanceModifier.width(12.dp))
            Column(GlanceModifier.defaultWeight().fillMaxHeight().padding(vertical = 2.dp)) {
                Column(GlanceModifier.fillMaxWidth().padding(end = 20.dp).clickable(open)) {
                    Text(st.title(s.title), maxLines = 1, style = st.titleStyle(16.sp))
                    Text(s.artist.ifBlank { "Artista desconocido" }, maxLines = 1, style = st.mutedStyle(12.5.sp))
                }
                Spacer(GlanceModifier.defaultWeight())
                Progress(s, st, playing)
                Spacer(GlanceModifier.defaultWeight())
                if (s.restingNow) Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    LikeButton(s, st)
                    Spacer(GlanceModifier.defaultWeight())
                    ContinueButton(st)
                } else Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    LikeButton(s, st)
                    Spacer(GlanceModifier.defaultWeight())
                    IconButton(R.drawable.widget_ic_prev, "Canción anterior", st.ink, widgetAction(WidgetAction.PREVIOUS))
                    Spacer(GlanceModifier.defaultWeight())
                    PlayButton(st, playing)
                    Spacer(GlanceModifier.defaultWeight())
                    IconButton(R.drawable.widget_ic_next, "Siguiente canción", st.ink, widgetAction(WidgetAction.NEXT))
                }
            }
        }
    }

    /** 4×3: portada y datos arriba, controles completos en medio y "A continuación" abajo. */
    @Composable
    private fun Large(context: Context, s: WidgetSnapshot, st: WidgetStyle) {
        val playing = s.playingNow
        val cover = rememberCover(context, s, st, 84.dp)
        val open = openPlayer(context)
        Column(GlanceModifier.fillMaxSize().padding(12.dp)) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Image(ImageProvider(cover), "Portada de ${s.title}", GlanceModifier.size(84.dp).clickable(open), contentScale = ContentScale.Crop)
                Spacer(GlanceModifier.width(12.dp))
                Column(GlanceModifier.defaultWeight()) {
                    Column(GlanceModifier.fillMaxWidth().padding(end = 20.dp).clickable(open)) {
                        Text(st.title(s.title), maxLines = 1, style = st.titleStyle(16.sp))
                        Text(s.artist.ifBlank { "Artista desconocido" }, maxLines = 1, style = st.mutedStyle(12.5.sp))
                    }
                    Spacer(GlanceModifier.height(10.dp))
                    Progress(s, st, playing)
                }
            }
            Spacer(GlanceModifier.defaultWeight())
            if (s.restingNow) Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(R.drawable.widget_ic_shuffle, if (s.shuffle) "Desactivar aleatorio" else "Aleatorio",
                    if (s.shuffle) st.accent else st.ink, widgetAction(WidgetAction.SHUFFLE))
                Spacer(GlanceModifier.defaultWeight())
                ContinueButton(st)
                Spacer(GlanceModifier.defaultWeight())
                LikeButton(s, st)
            } else Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(R.drawable.widget_ic_shuffle, if (s.shuffle) "Desactivar aleatorio" else "Aleatorio",
                    if (s.shuffle) st.accent else st.ink, widgetAction(WidgetAction.SHUFFLE))
                Spacer(GlanceModifier.defaultWeight())
                IconButton(R.drawable.widget_ic_prev, "Canción anterior", st.ink, widgetAction(WidgetAction.PREVIOUS))
                Spacer(GlanceModifier.defaultWeight())
                PlayButton(st, playing)
                Spacer(GlanceModifier.defaultWeight())
                IconButton(R.drawable.widget_ic_next, "Siguiente canción", st.ink, widgetAction(WidgetAction.NEXT))
                Spacer(GlanceModifier.defaultWeight())
                LikeButton(s, st)
            }
            Spacer(GlanceModifier.defaultWeight())
            // Línea separadora y "A continuación".
            Box(GlanceModifier.fillMaxWidth().height(1.dp).background(st.divider)) {}
            Spacer(GlanceModifier.height(8.dp))
            Text("A CONTINUACIÓN", style = st.mutedStyle(10.5.sp))
            if (s.next.isEmpty()) {
                Spacer(GlanceModifier.height(6.dp))
                Text("No hay más canciones en la cola", maxLines = 1, style = st.mutedStyle(12.5.sp))
            }
            s.next.forEach { n ->
                Spacer(GlanceModifier.height(6.dp))
                val art = rememberNextCover(context, n.id, st, 28.dp)
                Row(GlanceModifier.fillMaxWidth().clickable(widgetAction(WidgetAction.JUMP, n.index))
                    .semantics { contentDescription = "Reproducir ${n.title}" + if (n.artist.isNotBlank()) ", de ${n.artist}" else "" }, verticalAlignment = Alignment.CenterVertically) {
                    Image(ImageProvider(art), "Portada de ${n.title}", GlanceModifier.size(28.dp), contentScale = ContentScale.Crop)
                    Spacer(GlanceModifier.width(8.dp))
                    Text(n.title, maxLines = 1, style = st.bodyStyle(12.5.sp))
                    if (n.artist.isNotBlank()) Text("  ·  ${n.artist}", maxLines = 1, style = st.mutedStyle(12.5.sp))
                }
            }
        }
    }

    @Composable
    private fun Progress(s: WidgetSnapshot, st: WidgetStyle, playing: Boolean) {
        val pos = s.positionAt(System.currentTimeMillis(), playing)
        LinearProgressIndicator(
            progress = if (s.durationSec > 0) pos.toFloat() / s.durationSec else 0f,
            modifier = GlanceModifier.fillMaxWidth().height(4.dp).cornerRadius(2.dp),
            color = st.accent, backgroundColor = st.track,
        )
        Row(GlanceModifier.fillMaxWidth().padding(top = 4.dp)) {
            ElapsedTime(pos, playing, st)
            Spacer(GlanceModifier.defaultWeight())
            // Mismo formato que el Chronometer (01:18).
            Text("%02d:%02d".format(s.durationSec / 60, s.durationSec % 60), style = st.mutedStyle(10.5.sp))
        }
    }

    /** Tiempo transcurrido: un Chronometer de Android que, si suena, avanza solo cada segundo. */
    @Composable
    private fun ElapsedTime(positionSec: Int, playing: Boolean, st: WidgetStyle) {
        val context = LocalContext.current
        val views = RemoteViews(context.packageName, R.layout.widget_tiempo).apply {
            setChronometer(R.id.widget_tiempo, SystemClock.elapsedRealtime() - positionSec * 1000L, null, playing)
            setTextColor(R.id.widget_tiempo, st.muted.getColor(context).toArgb())
        }
        AndroidRemoteViews(views)
    }

    @Composable
    private fun LikeButton(s: WidgetSnapshot, st: WidgetStyle) =
        IconButton(if (s.liked) R.drawable.widget_ic_heart_filled else R.drawable.widget_ic_heart,
            if (s.liked) "Quitar de Me gusta" else "Me gusta", if (s.liked) st.accent else st.ink, widgetAction(WidgetAction.LIKE))

    companion object {
        /** Desde este alto se usa la vista 4×3 (en Samsung, 4×2 mide unos 206 dp y 4×3 unos 310 dp). */
        val LARGE_MIN_HEIGHT = 260.dp
    }
}
