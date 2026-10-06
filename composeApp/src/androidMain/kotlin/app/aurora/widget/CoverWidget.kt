package app.aurora.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.unit.ColorProvider

/**
 * Widget "Aurora · Portada" (2×2): la portada ocupa todo, con un degradado oscuro abajo,
 * título, artista y el botón de reproducir/pausa abajo a la derecha.
 */
class CoverWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val flow = WidgetStore.flow(context)
        provideContent {
            val s by flow.collectAsState()
            Content(s ?: WidgetSnapshot())
        }
    }

    /** Vista previa del selector de widgets (Android 15+): el widget real con una canción de ejemplo. */
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent { Content(WidgetSnapshot.preview()) }
    }

    @Composable
    private fun Content(snap: WidgetSnapshot) {
        val ctx = LocalContext.current
        val st = widgetStyle(snap)
        val shade = cached("sombra") { bottomShade() }
        val open = openPlayer(ctx)
        WidgetFrame(snap, st) {
            if (snap.empty) EmptyState(ctx, st) else {
                // La portada ocupa todo: sin forma propia, la recorta el borde del widget.
                val cover = rememberCover(ctx, snap, st, 150.dp, shaped = false)
                Box(GlanceModifier.fillMaxSize().cornerRadius(st.corner - st.borderWidth)) {
                    Image(ImageProvider(cover), "Portada de ${snap.title}", GlanceModifier.fillMaxSize().clickable(open), contentScale = ContentScale.Crop)
                    Image(ImageProvider(shade), null, GlanceModifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                    Row(GlanceModifier.fillMaxSize().padding(start = 12.dp, end = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
                        Column(GlanceModifier.defaultWeight().padding(bottom = 2.dp).clickable(open)) {
                            Text(st.title(snap.title), maxLines = 1, style = st.titleStyle(14.sp, ColorProvider(Color.White)))
                            Text(snap.artist.ifBlank { "Artista desconocido" }, maxLines = 1, style = st.mutedStyle(11.5.sp, ColorProvider(Color(0xBFFFFFFF))))
                        }
                        Spacer(GlanceModifier.width(8.dp))
                        PlayButton(st, snap.playingNow, side = 42.dp, resting = snap.restingNow)
                    }
                }
            }
        }
    }
}
