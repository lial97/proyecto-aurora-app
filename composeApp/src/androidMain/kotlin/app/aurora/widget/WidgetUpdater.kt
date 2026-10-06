package app.aurora.widget

import android.content.Context
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.compose
import android.appwidget.AppWidgetManager
import android.os.Build
import android.util.SizeF
import android.widget.RemoteViews
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import app.aurora.AppState
import app.aurora.AuroraRuntime
import app.aurora.color.Hsl
import app.aurora.color.toColor
import app.aurora.components.accentOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Mantiene los widgets al día sin gastar batería: solo se dibujan al cambiar de canción, al reproducir o
 * pausar y al cambiar Me gusta, Aleatorio, la cola, el tema o el estilo de los widgets. Mientras suena y la pantalla está encendida,
 * la barra de progreso cada 5 s (con la pantalla apagada, nada). El tiempo transcurrido avanza solo (Chronometer).
 *
 * El widget se arma aquí mismo (`GlanceAppWidget.compose`) y se entrega a Android al instante: la sesión
 * normal de Glance va por WorkManager, que con la app en segundo plano llegaba a tardar 20 s.
 */
object WidgetUpdater {
    private const val PROGRESS_EVERY_MS = 5_000L

    /** Lo que, si cambia, obliga a dibujar de nuevo (la posición no: va por tiempo). */
    private data class Key(val trackId: String?, val durationKnown: Boolean, val playing: Boolean, val shuffle: Boolean, val liked: Boolean, val next: List<String>, val theme: String, val wallpaper: Boolean)

    private var app: AppState? = null
    private var context: Context? = null
    private var coverFor: String? = null

    /** Los tres widgets: Reproductor (4×2 y 4×3), Barra (4×1) y Portada (2×2). */
    private val WIDGETS = listOf(PlayerWidget(), BarWidget(), CoverWidget())

    /** Cambiar cuando cambie el aspecto de los widgets: así el selector recibe las vistas previas nuevas. */
    private const val PREVIEWS_VERSION = 1

    /**
     * Vistas previas del selector de widgets en Android 15+: el widget real con una canción de ejemplo.
     * Android limita cuántas veces se pueden enviar, así que se envían una vez por versión.
     */
    private suspend fun publishPreviews(ctx: Context) {
        if (Build.VERSION.SDK_INT < 35) return
        val prefs = ctx.getSharedPreferences("aurora_widgets", Context.MODE_PRIVATE)
        if (prefs.getInt("previas", 0) == PREVIEWS_VERSION) return
        val manager = GlanceAppWidgetManager(ctx)
        val results = listOf(PlayerWidgetReceiver::class, BarWidgetReceiver::class, CoverWidgetReceiver::class).map { receiver ->
            runCatching { manager.setWidgetPreviews(receiver) }.getOrDefault(GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_RATE_LIMITED)
        }
        if (results.all { it == GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_SUCCESS }) prefs.edit().putInt("previas", PREVIEWS_VERSION).apply()
    }

    /** Portadas pequeñas (128 px) de las 2 siguientes; se borran las que ya no hacen falta. */
    private suspend fun saveNextCovers(ctx: Context, app: AppState, next: List<WidgetNext>) {
        val keep = next.map { WidgetStore.nextCoverFile(ctx, it.id).name }.toSet()
        ctx.filesDir.listFiles { f -> f.name.startsWith("widget_siguiente_") && f.name !in keep }?.forEach { it.delete() }
        for (n in next) {
            val file = WidgetStore.nextCoverFile(ctx, n.id)
            if (file.exists()) continue
            val track = app.player.state.value.queue.getOrNull(n.index)?.takeIf { it.id == n.id } ?: continue
            val img = runCatching { app.covers.get(track) }.getOrNull() ?: continue
            withContext(Dispatchers.Default) { file.writeBytes(AuroraRuntime.jpeg(AuroraRuntime.square(img.asAndroidBitmap(), 128))) }
        }
    }

    fun start(context: Context, app: AppState, scope: CoroutineScope) {
        this.context = context.applicationContext
        this.app = app
        coverFor = WidgetStore.current(context).trackId
        scope.launch { publishPreviews(this@WidgetUpdater.context!!) }
        scope.launch {
            app.player.state
                .combine(snapshotFlow { app.liked }) { p, liked -> p to liked }
                .combine(app.settings.theme) { (p, liked), theme -> Triple(p, liked, theme) }
                .combine(app.prefsRepo.prefs.map { it.widgetsWallpaper }.distinctUntilChanged()) { (p, liked, theme), wallpaper ->
                    Key(p.current?.id, p.durationSec > 0, p.isPlaying, p.shuffle, p.current?.id?.let { it in liked } == true,
                        listOfNotNull(p.queue.getOrNull(p.index + 1)?.id, p.queue.getOrNull(p.index + 2)?.id), theme.id.name, wallpaper)
                }
                .distinctUntilChanged()
                .collectLatest { k ->
                    push()
                    val power = context.getSystemService(android.os.PowerManager::class.java)
                    while (k.playing) { delay(PROGRESS_EVERY_MS); if (power?.isInteractive != false) push() }
                }
        }
    }

    /** Se llama también al poner un widget nuevo (si la app ya está abierta). */
    suspend fun push() {
        val ctx = context ?: return
        val app = app ?: return
        val glance = GlanceAppWidgetManager(ctx)
        if (WIDGETS.all { glance.getGlanceIds(it.javaClass).isEmpty() }) return
        val p = app.player.state.value
        // Sin nada en la cola (app recién abierta): la última canción, en pausa.
        val track = p.current ?: app.lastTrack()
        val old = WidgetStore.current(ctx)
        var accent = old.accent; var deep = old.deep; var version = old.coverVersion
        if (track?.id != coverFor) {
            val img = track?.let { runCatching { app.covers.get(it) }.getOrNull() }
            withContext(Dispatchers.Default) {
                val file = WidgetStore.coverFile(ctx)
                if (img != null) file.writeBytes(AuroraRuntime.jpeg(AuroraRuntime.square(img.asAndroidBitmap())))
                else file.delete()
                val hue = img?.let { accentOf(it) }?.h
                accent = hue?.let { Hsl(it, 0.85f, 0.65f).toColor().toArgb() } ?: WidgetSnapshot.DEFAULT_ACCENT
                deep = hue?.let { Hsl(it, 0.45f, 0.22f).toColor().toArgb() } ?: WidgetSnapshot.DEFAULT_DEEP
            }
            version = System.currentTimeMillis()
            coverFor = track?.id
        }
        val playing = p.current != null && p.isPlaying
        val next = listOf(p.index + 1, p.index + 2).mapNotNull { i -> p.queue.getOrNull(i)?.let { WidgetNext(i, it.id, it.title, it.artist) } }
        saveNextCovers(ctx, app, next)
        WidgetStore.save(ctx, WidgetSnapshot(
            trackId = track?.id,
            title = track?.title.orEmpty(),
            artist = track?.artist.orEmpty(),
            durationSec = (p.durationSec.takeIf { it > 0 && p.current != null } ?: track?.durationSec ?: 0),
            positionSec = if (p.current != null) p.positionSec else 0,
            savedAtMs = System.currentTimeMillis(),
            playing = playing,
            resting = p.current == null,
            liked = track?.id?.let { it in app.liked } == true,
            shuffle = p.shuffle,
            next = next,
            theme = app.settings.theme.value.id.name,
            wallpaper = app.prefsRepo.prefs.value.widgetsWallpaper,
            accent = accent, deep = deep, coverVersion = version,
        ))
        render(ctx)
    }

    /**
     * Arma cada widget puesto y se lo entrega a Android (vertical y horizontal en Android 12+).
     * Fuera del hilo principal: antes trababa la app (~1,5 s) en cada actualización.
     */
    private suspend fun render(ctx: Context) = withContext(Dispatchers.Default) {
        val glance = GlanceAppWidgetManager(ctx)
        val manager = AppWidgetManager.getInstance(ctx)
        for (widget in WIDGETS) for (id in glance.getGlanceIds(widget.javaClass)) {
            val appWidgetId = glance.getAppWidgetId(id)
            val options = manager.getAppWidgetOptions(appWidgetId)
            val minW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
            val maxW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, minW)
            val minH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
            val maxH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minH)
            val portrait = DpSize(minW.dp, maxH.dp)
            val views = runCatching {
                if (Build.VERSION.SDK_INT >= 31) {
                    val landscape = DpSize(maxW.dp, minH.dp)
                    RemoteViews(mapOf(
                        SizeF(portrait.width.value, portrait.height.value) to widget.compose(ctx, id, options, portrait),
                        SizeF(landscape.width.value, landscape.height.value) to widget.compose(ctx, id, options, landscape),
                    ))
                } else widget.compose(ctx, id, options, portrait)
            }.getOrNull()
            // Si algo falla, la sesión normal de Glance (más lenta pero segura).
            if (views != null) manager.updateAppWidget(appWidgetId, views) else widget.update(ctx, id)
        }
    }
}
