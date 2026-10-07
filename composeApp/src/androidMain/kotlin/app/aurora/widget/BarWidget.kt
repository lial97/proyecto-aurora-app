package app.aurora.widget

import android.content.Context
import androidx.compose.runtime.Composable
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
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import app.aurora.shared.R

/** Widget "Aurora · Barra" (4×1): portada, título, artista, anterior, reproducir/pausa y siguiente. */
class BarWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Se lee el estado al dibujar, sin escucharlo (los cambios los dibuja WidgetUpdater; ver PlayerWidget).
        provideContent { Content(WidgetStore.current(context)) }
    }

    /** Vista previa del selector de widgets (Android 15+): el widget real con una canción de ejemplo. */
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent { Content(WidgetSnapshot.preview()) }
    }

    @Composable
    private fun Content(snap: WidgetSnapshot) {
        val ctx = LocalContext.current
        val st = widgetStyle(snap)
        WidgetFrame(snap, st) {
            if (snap.empty) EmptyState(ctx, st, compact = true) else {
                val artSide = (LocalSize.current.height - 16.dp).coerceIn(36.dp, 56.dp)
                val cover = rememberCover(ctx, snap, st, artSide)
                val open = openPlayer(ctx)
                Row(GlanceModifier.fillMaxSize().padding(start = 10.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(ImageProvider(cover), "Portada de ${snap.title}", GlanceModifier.size(artSide).clickable(open), contentScale = ContentScale.Crop)
                    Spacer(GlanceModifier.width(10.dp))
                    Column(GlanceModifier.defaultWeight().clickable(open)) {
                        Text(st.title(snap.title), maxLines = 1, style = st.titleStyle(14.5.sp))
                        Text(snap.artist.ifBlank { "Artista desconocido" }, maxLines = 1, style = st.mutedStyle(12.5.sp))
                    }
                    if (snap.restingNow) ContinueButton(st, height = 38.dp) else {
                        IconButton(R.drawable.widget_ic_prev, "Canción anterior", st.ink, widgetAction(WidgetAction.PREVIOUS))
                        PlayButton(st, snap.playingNow, side = 42.dp)
                        IconButton(R.drawable.widget_ic_next, "Siguiente canción", st.ink, widgetAction(WidgetAction.NEXT))
                    }
                }
            }
        }
    }
}
