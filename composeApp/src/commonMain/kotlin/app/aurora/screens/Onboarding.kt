package app.aurora.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import app.aurora.theme.ThemeId
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aurora.AppState
import app.aurora.components.Artwork
import app.aurora.components.AuroraIcon
import app.aurora.components.Icon
import app.aurora.components.IconButton
import app.aurora.components.pressable
import app.aurora.components.surface
import app.aurora.components.typingField
import app.aurora.data.LibraryFolder
import app.aurora.data.ScanStage
import androidx.compose.ui.semantics.progressBarRangeInfo
import app.aurora.data.SettingsRepository
import app.aurora.data.sample.SampleData
import app.aurora.domain.MediaType
import app.aurora.theme.Themes
import app.aurora.theme.Ui
import app.aurora.theme.label
import app.aurora.theme.title

/**
 * Estado de la configuración inicial, fuera de la pantalla: al cambiar de tema la pantalla se dibuja dos veces
 * (transición de 600 ms) y ambas copias deben mostrar lo mismo.
 *
 * Pasos: 0 Bienvenida · 1 Nombre · 2 Música · 3 Videos · 4 Estilo · 5 Todo listo.
 * Lo elegido se guarda al momento; el paso también, para retomar si se cierra la app.
 */
class OnboardingModel(private val state: AppState) {
    var step by mutableIntStateOf(state.settings.onboardingStep)
        private set
    var name by mutableStateOf(state.settings.userName.value)
        private set

    fun go(to: Int) {
        step = to.coerceIn(0, LAST)
        if (step < LAST) state.settings.onboardingStep = step
    }

    fun next() = when (step) {
        THEME -> { state.finishOnboarding(); go(LAST) }
        LAST -> state.closeOnboarding()
        else -> go(step + 1)
    }

    /** Desde "Todo listo" ya no se vuelve atrás. */
    val canGoBack: Boolean get() = step in 1 until LAST
    fun back() { if (canGoBack) go(step - 1) }

    fun updateName(v: String) {
        if (v.length > SettingsRepository.MAX_NAME) return
        name = v
        state.settings.setUserName(v)
    }

    companion object {
        const val WELCOME = 0
        const val NAME = 1
        const val MUSIC = 2
        const val VIDEOS = 3
        const val THEME = 4
        const val LAST = 5
        /** Pasos numerados ("Paso 2 de 4"): del 1 al 4. */
        const val NUMBERED = 4
    }
}

/** Botones de abajo de cada paso: el principal (y si está activo) y el secundario opcional. */
private data class StepActions(val primary: String, val enabled: Boolean = true, val secondary: Pair<String, () -> Unit>? = null)

@Composable
private fun actionsFor(state: AppState, model: OnboardingModel): StepActions {
    val folders by state.settings.folders.collectAsState()
    val videoFolders by state.settings.videoFolders.collectAsState()
    val counts by state.library.counts.collectAsState()
    val name = SettingsRepository.cleanName(model.name)
    fun counted(list: List<String>, type: MediaType) = list.all { LibraryFolder(it, type) in counts }
    return when (model.step) {
        OnboardingModel.WELCOME -> StepActions("Empezar")
        OnboardingModel.NAME -> StepActions(if (name.isEmpty()) "Continuar sin nombre" else "Continuar")
        OnboardingModel.MUSIC -> StepActions(
            "Continuar", enabled = folders.isNotEmpty() && counted(folders, MediaType.AUDIO),
            secondary = if (folders.isEmpty()) "Lo haré después" to { model.go(OnboardingModel.VIDEOS) } else null,
        )
        OnboardingModel.VIDEOS ->
            if (videoFolders.isEmpty()) StepActions("Saltar este paso")
            else StepActions("Continuar", enabled = counted(videoFolders, MediaType.VIDEO))
        OnboardingModel.THEME -> StepActions(if (name.isEmpty()) "Empezar" else "Empezar, $name")
        else -> StepActions(if (name.isEmpty()) "Ir a Aurora" else "Vamos, $name")
    }
}

/** Primera vez que se abre la app: bienvenida, nombre, carpetas de música y de videos, estilo y resumen. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun OnboardingScreen(state: AppState, model: OnboardingModel) {
    val step = model.step
    val actions = actionsFor(state, model)
    // Atrás del sistema: vuelve al paso anterior. En la bienvenida no se intercepta (Android sale de la app).
    androidx.compose.ui.backhandler.BackHandler(enabled = model.canGoBack) { model.back() }
    val advance = { if (actions.enabled) model.next() }

    // Esc y Alt+← vuelven siempre (antes que el elemento enfocado). Enter avanza solo si el elemento enfocado no
    // lo usó: sobre una tarjeta de tema la elige, sobre "Atrás" vuelve.
    val keys = Modifier
        .onPreviewKeyEvent { e ->
            e.type == KeyEventType.KeyDown && (e.key == Key.Escape || (e.isAltPressed && e.key == Key.DirectionLeft)) &&
                model.canGoBack.also { if (it) model.back() }
        }
        .onKeyEvent { e ->
            e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter) && true.also { advance() }
        }
    if (Ui.isDesktop) DesktopOnboarding(state, model, actions, advance, keys)
    else MobileOnboarding(state, model, actions, advance, keys)
}

/** Móvil: una columna centrada, progreso arriba y el botón principal fijo abajo, al alcance del pulgar. */
@Composable
private fun MobileOnboarding(state: AppState, model: OnboardingModel, actions: StepActions, advance: () -> Unit, keys: Modifier) {
    val step = model.step
    Box(
        Modifier.fillMaxSize().onboardingBackground().statusBarsPadding().navigationBarsPadding().imePadding().then(keys),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(Modifier.fillMaxSize().widthIn(max = 560.dp).padding(horizontal = 22.dp)) {
            if (step in OnboardingModel.NAME..OnboardingModel.THEME) TopBar(step, model::back)
            else Spacer(Modifier.height(18.dp))
            StepBody(state, model, advance, Modifier.weight(1f).fillMaxWidth(), PaddingValues(top = 20.dp, bottom = 16.dp))
            // Botón principal fijo abajo (52 dp, ancho completo) y, si hay, el secundario debajo.
            Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                PrimaryButton(actions.primary, actions.enabled, advance, Modifier.fillMaxWidth().height(52.dp))
                actions.secondary?.let { (text, onClick) -> SecondaryLink(text, onClick, Modifier.padding(top = 6.dp).fillMaxWidth()) }
            }
        }
    }
}

/**
 * Escritorio (ventana de 1000×700): a la izquierda, 330 dp con el logo, los 4 pasos y las portadas en abanico;
 * a la derecha el paso, con 56 dp de margen, y abajo "Atrás" y el botón principal con flecha.
 */
@Composable
private fun DesktopOnboarding(state: AppState, model: OnboardingModel, actions: StepActions, advance: () -> Unit, keys: Modifier) {
    val c = Ui.colors
    Row(Modifier.fillMaxSize().onboardingBackground().then(keys)) {
        Column(Modifier.width(330.dp).fillMaxHeight().background(c.surface.copy(alpha = c.surface.alpha * .6f)).padding(horizontal = 28.dp, vertical = 30.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(Ui.shapes.artSmall).background(c.accent), contentAlignment = Alignment.Center) { Icon(AuroraIcon.Music, c.onAccent, size = 19.dp) }
                BasicText(Ui.theme.title("Aurora"), style = Ui.type.h2, modifier = Modifier.padding(start = 10.dp))
            }
            Spacer(Modifier.height(36.dp))
            StepList(model.step)
            Spacer(Modifier.weight(1f))
            CoverFan(scale = .85f)
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            StepBody(state, model, advance, Modifier.weight(1f).fillMaxWidth(), PaddingValues(start = 56.dp, end = 56.dp, top = 52.dp, bottom = 16.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 56.dp, vertical = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                if (model.canGoBack) {
                    Row(
                        Modifier.pressable("Atrás", .96f) { model.back() }.surface(buttonShape).padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(AuroraIcon.Back, c.ink, size = 16.dp)
                        BasicText(Ui.theme.label("Atrás"), style = Ui.type.label.copy(color = c.ink, fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(start = 6.dp))
                    }
                }
                Spacer(Modifier.weight(1f))
                actions.secondary?.let { (text, onClick) -> SecondaryLink(text, onClick, Modifier.padding(end = 16.dp)) }
                PrimaryButton(actions.primary, actions.enabled, advance, Modifier.widthIn(min = 180.dp).height(48.dp), arrow = true)
            }
        }
    }
}

/** Lista de los 4 pasos (escritorio): número, nombre y subtítulo; el actual resaltado y los hechos con ✓. */
@Composable
private fun StepList(step: Int) {
    val c = Ui.colors
    val steps = listOf("Tu nombre" to "Cómo te llamamos", "Tu música" to "Carpetas de canciones", "Tus videos" to "Videos musicales", "Tu estilo" to "Elige un tema")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        steps.forEachIndexed { k, (name, sub) ->
            val n = k + 1
            val done = n < step || step == OnboardingModel.LAST
            val current = n == step
            Row(
                Modifier.fillMaxWidth().clip(Ui.shapes.card).background(if (current) c.accent.copy(alpha = .12f) else Color.Transparent)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .semantics { contentDescription = "Paso $n de 4, $name${if (done) ", hecho" else if (current) ", actual" else ""}" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val circle = Ui.shapes.playButton
                Box(
                    Modifier.size(30.dp).clip(circle)
                        .background(if (current) c.accent else if (done) c.accent.copy(alpha = .16f) else Color.Transparent)
                        .border(if (current || done) 0.dp else 1.5.dp, if (current || done) Color.Transparent else c.surfaceBorder, circle),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) Icon(AuroraIcon.Check, c.accent, size = 16.dp)
                    else BasicText("$n", style = Ui.type.label.copy(color = if (current) c.onAccent else c.mute, fontWeight = FontWeight.Bold))
                }
                Column(Modifier.padding(start = 12.dp)) {
                    BasicText(name, style = Ui.type.rowTitle.copy(color = if (current || done) c.ink else c.mute))
                    BasicText(sub, style = Ui.type.caption.copy(color = c.mute))
                }
            }
        }
    }
}

/** Contenido del paso, que aparece subiendo 8 dp con fundido (sin animación con "Reducir movimiento"). */
@Composable
private fun StepBody(state: AppState, model: OnboardingModel, advance: () -> Unit, modifier: Modifier, padding: PaddingValues) {
    val dur = if (Ui.reduceMotion) 0 else 300
    val rise = with(androidx.compose.ui.platform.LocalDensity.current) { 8.dp.roundToPx() }
    val mobile = !Ui.isDesktop
    AnimatedContent(
        model.step, modifier,
        transitionSpec = { (fadeIn(tween(dur)) + slideInVertically(tween(dur)) { rise }).togetherWith(fadeOut(tween(dur / 2))) },
    ) { s ->
        val centered = mobile && (s == OnboardingModel.WELCOME || s == OnboardingModel.NAME || s == OnboardingModel.LAST)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding),
            horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            when (s) {
                OnboardingModel.WELCOME -> WelcomeStep()
                OnboardingModel.NAME -> NameStep(model, advance)
                OnboardingModel.MUSIC -> FolderStep(state, MediaType.AUDIO)
                OnboardingModel.VIDEOS -> FolderStep(state, MediaType.VIDEO)
                OnboardingModel.THEME -> ThemeStep(state)
                else -> DoneStep(state, model)
            }
        }
    }
}

@Composable
private fun SecondaryLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.height(44.dp).pressable(text, .97f, onClick = onClick).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
        BasicText(Ui.theme.label(text), style = Ui.type.label.copy(color = Ui.colors.mute, fontWeight = FontWeight.SemiBold))
    }
}

/**
 * Fondo de la configuración: el color del tema con dos manchas difuminadas (énfasis arriba a la izquierda y la
 * segunda luz abajo a la derecha). Sin las decoraciones de cada tema, que tapaban el texto, y sin animación.
 */
@Composable
private fun Modifier.onboardingBackground(): Modifier {
    val c = Ui.colors
    val lights = Ui.prefs.backgroundLights
    return background(c.background).drawBehind {
        if (!lights) return@drawBehind
        val a = if (c.isDark) .30f else .22f
        fun blob(color: Color, center: Offset, r: Float) = drawCircle(
            Brush.radialGradient(0f to color.copy(alpha = a), .6f to color.copy(alpha = a * .35f), 1f to Color.Transparent, center = center, radius = r),
            r, center,
        )
        val r = maxOf(size.width, size.height) * .55f
        blob(c.accent, Offset(size.width * .05f, size.height * .08f), r)
        blob(c.light2, Offset(size.width * .95f, size.height * .92f), r)
    }
}

/** Atrás, barra de 4 tramos y "2/4" (solo en los pasos 1 a 4). */
@Composable
private fun TopBar(step: Int, onBack: () -> Unit) {
    val c = Ui.colors
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(AuroraIcon.Back, "Atrás", onBack, tint = c.ink, size = 40.dp)
        Row(
            Modifier.weight(1f).padding(horizontal = 10.dp).semantics { contentDescription = "Paso $step de ${OnboardingModel.NUMBERED}" },
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            repeat(OnboardingModel.NUMBERED) { i ->
                val on by animateFloatAsState(if (i < step) 1f else 0f, tween(if (Ui.reduceMotion) 0 else 300))
                Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(50)).background(c.ink.copy(alpha = .14f))) {
                    Box(Modifier.fillMaxWidth(on).height(4.dp).background(c.accent))
                }
            }
        }
        BasicText("$step/${OnboardingModel.NUMBERED}", style = Ui.type.caption.copy(color = c.mute, fontWeight = FontWeight.SemiBold), modifier = Modifier.width(30.dp))
    }
}

/** Etiqueta pequeña sobre el título ("BIENVENIDA", "PASO 1 DE 4", "TODO LISTO"). */
@Composable
private fun Kicker(text: String, centered: Boolean) {
    Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        // Carbono: cuadrito del color de énfasis delante, como en la maqueta.
        if (Ui.theme.id == ThemeId.CARBONO) Box(Modifier.padding(end = 8.dp).size(8.dp).background(Ui.colors.accent))
        BasicText(
            text, style = Ui.type.caption.copy(color = Ui.colors.accent, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp, textAlign = if (centered) TextAlign.Center else TextAlign.Start),
        )
    }
}

/** Forma del botón principal: la de los chips del tema; en Carbono, esquinas cortadas. */
private val buttonShape: androidx.compose.ui.graphics.Shape
    @Composable get() = if (Ui.theme.id == ThemeId.CARBONO) CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp) else Ui.shapes.chip

@Composable
private fun StepTitle(kicker: String, title: String, lead: String, centered: Boolean = false) {
    val align = if (centered) TextAlign.Center else TextAlign.Start
    // Al llegar a un paso el foco va al título: los lectores de pantalla lo anuncian y el teclado ya funciona
    // (Enter avanza) sin tener que hacer clic antes.
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Kicker(kicker, centered)
    BasicText(
        Ui.theme.title(title), style = Ui.type.h1.copy(fontSize = Ui.type.h1.fontSize * .78f, textAlign = align),
        modifier = Modifier.semantics { heading() }.focusRequester(focus).focusable(),
    )
    BasicText(lead, style = Ui.type.rowSubtitle.copy(textAlign = align), modifier = Modifier.padding(top = 8.dp, bottom = 22.dp))
}

/** Tres portadas en abanico (bienvenida y final). */
@Composable
private fun CoverFan(scale: Float = 1f) {
    val covers = remember { SampleData.tracks.filter { it.mediaType == MediaType.AUDIO }.take(3) }
    Box(Modifier.fillMaxWidth().height(170.dp * scale).padding(top = 8.dp), contentAlignment = Alignment.Center) {
        val shape = Ui.shapes.artLarge
        // Pétalo: marco blanco como una foto; Seda: anillo fino del color de énfasis separado del fondo.
        val frame = when (Ui.theme.id) {
            ThemeId.PETALO -> Modifier.border(4.dp, Color.White, shape)
            ThemeId.SEDA -> Modifier.border(1.dp, Ui.colors.accent, shape).padding(4.dp)
            else -> Modifier
        }
        listOf(-12f to -70.dp, 12f to 70.dp, 0f to 0.dp).forEachIndexed { i, (deg, dx) ->
            val t = covers.getOrNull(if (i == 2) 0 else i + 1) ?: return@forEachIndexed
            Box(Modifier.offset(x = dx * scale, y = if (i == 2) 0.dp else 10.dp * scale).rotate(deg).alpha(if (i == 2) 1f else .92f).then(frame)) {
                Artwork(t, (if (i == 2) 132.dp else 112.dp) * scale, large = true)
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    val mobile = !Ui.isDesktop
    if (mobile) { CoverFan(); Spacer(Modifier.height(28.dp)) } else Spacer(Modifier.height(60.dp))
    StepTitle(
        "BIENVENIDA", "Tu música, a tu manera.",
        "Cuatro pasos rápidos: cómo te llamamos, dónde está tu música, tus videos y el estilo que más te guste.",
        centered = mobile,
    )
}

@Composable
private fun NameStep(model: OnboardingModel, onDone: () -> Unit) {
    val c = Ui.colors
    StepTitle("PASO 1 DE 4", "¿Cómo te llamamos?", "Tu nombre o un apodo. Lo usaremos para saludarte.", centered = !Ui.isDesktop)
    Row(Modifier.fillMaxWidth().surface().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            model.name, model::updateName, singleLine = true,
            textStyle = Ui.type.body.copy(fontSize = Ui.type.body.fontSize * 1.15f), cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { onDone() }),
            modifier = Modifier.weight(1f).typingField().semantics { contentDescription = "Tu nombre" },
            decorationBox = { inner ->
                if (model.name.isEmpty()) BasicText("Escribe tu nombre", style = Ui.type.body.copy(color = c.mute, fontSize = Ui.type.body.fontSize * 1.15f))
                inner()
            },
        )
        BasicText("${model.name.length}/${SettingsRepository.MAX_NAME}", style = Ui.type.caption, modifier = Modifier.padding(start = 8.dp))
    }
    // Vista previa en vivo del saludo, con la fuente del tema.
    Column(Modifier.padding(top = 16.dp).fillMaxWidth().surface().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText("Así te saludará Aurora", style = Ui.type.caption.copy(color = c.mute))
        BasicText(
            Ui.theme.title(greeting(SettingsRepository.cleanName(model.name))),
            style = Ui.type.h2.copy(textAlign = TextAlign.Center), modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun FolderStep(state: AppState, type: MediaType) {
    val c = Ui.colors
    val video = type == MediaType.VIDEO
    val media = state.platform.media
    val folders by (if (video) state.settings.videoFolders else state.settings.folders).collectAsState()
    val counts by state.library.counts.collectAsState()
    if (video) StepTitle("PASO 3 DE 4", "¿Y tus videos musicales?", "Solo los videos de estas carpetas aparecerán en la app.")
    else StepTitle("PASO 2 DE 4", "¿Dónde está tu música?", "Elige las carpetas con tus canciones. Aurora buscará también en sus subcarpetas.")
    folders.forEach { path ->
        val label = media.describeFolder(path)
        val n = counts[LibraryFolder(path, type)]
        Row(Modifier.padding(bottom = 10.dp).fillMaxWidth().surface().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(Ui.shapes.artSmall).background(c.accent.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                Icon(if (video) AuroraIcon.Video else AuroraIcon.Folder, c.accent, size = 19.dp)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                BasicText(label.name, style = Ui.type.rowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicText(label.path, style = Ui.type.caption.copy(fontFamily = FontFamily.Monospace), maxLines = 1, overflow = TextOverflow.Ellipsis)
                // El conteo se anuncia solo al lector de pantalla cuando cambia ("146 canciones").
                BasicText(
                    if (n == null) "Buscando…" else foundLabel(n, video),
                    style = Ui.type.caption.copy(color = if (n == null) c.mute else c.accent, fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(top = 2.dp).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            IconButton(AuroraIcon.Close, "Quitar ${label.name}", { state.removeFolder(path, type) }, tint = c.mute, size = 34.dp, iconSize = 16.dp)
        }
    }
    if (media.canPickFolder) {
        val text = if (folders.isEmpty()) "Elegir carpeta" else "Añadir otra carpeta"
        val shape = Ui.shapes.card
        Row(
            Modifier.fillMaxWidth().pressable(text, .97f) { state.pickAndAddFolder(type, quiet = true) }
                .clip(shape).background(c.accent.copy(alpha = .06f)).dashedBorder(c.accent.copy(alpha = .6f), shape)
                .padding(vertical = 18.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(36.dp).clip(Ui.shapes.playButton).background(c.accent), contentAlignment = Alignment.Center) {
                Icon(AuroraIcon.Plus, c.onAccent, size = 20.dp)
            }
            Column(Modifier.padding(start = 12.dp)) {
                BasicText(Ui.theme.label(text), style = Ui.type.label.copy(color = c.ink, fontWeight = FontWeight.Bold))
                BasicText(
                    if (state.platform.isDesktop) "Se abre el selector de carpetas del sistema" else "Se abre el selector de carpetas del teléfono",
                    style = Ui.type.caption.copy(color = c.mute),
                )
            }
        }
    }
    // Sugerencias: carpetas habituales que aún no se eligieron (en escritorio, con su número de archivos).
    val suggestions by androidx.compose.runtime.produceState(emptyList<app.aurora.platform.FolderSuggestion>(), type, folders) {
        value = runCatching { media.suggestFolders(type, folders) }.getOrDefault(emptyList())
    }
    if (suggestions.isNotEmpty()) {
        BasicText(
            if (video) "Encontramos videos aquí:" else "Encontramos música aquí:",
            style = Ui.type.caption.copy(color = c.mute, fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
        )
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            suggestions.forEach { sug ->
                val desc = sug.count?.let { "Añadir ${sug.name}, ${foundLabel(it, video)}" } ?: "Elegir ${sug.name}"
                Row(
                    Modifier.pressable(desc, .95f) {
                        if (media.suggestionsAddDirectly) state.addFolder(sug.path, type, quiet = true)
                        else state.pickAndAddFolder(type, quiet = true, initial = sug.path)
                    }.surface(Ui.shapes.chip).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AuroraIcon.Plus, c.accent, size = 14.dp)
                    BasicText(sug.name, style = Ui.type.label.copy(color = c.ink, fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(start = 6.dp))
                    sug.count?.let { BasicText("$it", style = Ui.type.label.copy(color = c.mute), modifier = Modifier.padding(start = 6.dp)) }
                }
            }
        }
    }
    val where = if (state.platform.isDesktop) "de tu equipo" else "de tu teléfono"
    Row(Modifier.padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(AuroraIcon.Shield, c.mute, size = 18.dp)
        BasicText(
            if (video) "Tus fotos y videos personales no se tocan." else "Aurora solo lee las carpetas que elijas. Nada sale $where.",
            style = Ui.type.caption.copy(color = c.mute), modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** Borde punteado con la forma dada (botón "Elegir carpeta"). */
private fun Modifier.dashedBorder(color: Color, shape: androidx.compose.ui.graphics.Shape): Modifier = drawBehind {
    val w = 1.5.dp.toPx()
    val outline = shape.createOutline(size, layoutDirection, this)
    drawOutline(outline, color, style = androidx.compose.ui.graphics.drawscope.Stroke(w, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))))
}

@Composable
private fun ThemeStep(state: AppState) {
    val theme by state.settings.theme.collectAsState()
    StepTitle("PASO 4 DE 4", "Elige tu estilo", "Toca uno para probarlo. Puedes cambiarlo cuando quieras en Ajustes.")
    val cols = if (Ui.isDesktop) 4 else 2
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    // Grupo de opciones: Tab entra, las flechas se mueven entre tarjetas y Enter elige.
    Column(
        Modifier.selectableGroup().onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val dir = when (e.key) {
                Key.DirectionLeft -> androidx.compose.ui.focus.FocusDirection.Left
                Key.DirectionRight -> androidx.compose.ui.focus.FocusDirection.Right
                Key.DirectionUp -> androidx.compose.ui.focus.FocusDirection.Up
                Key.DirectionDown -> androidx.compose.ui.focus.FocusDirection.Down
                else -> return@onPreviewKeyEvent false
            }
            focusManager.moveFocus(dir)
        },
    ) {
        Themes.all.chunked(cols).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { t -> ThemePreview(t, t.id == theme.id, Modifier.weight(1f)) { state.settings.setTheme(t) } }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DoneStep(state: AppState, model: OnboardingModel) {
    val c = Ui.colors
    val lib by state.library.state.collectAsState()
    val folders by state.settings.folders.collectAsState()
    val videoFolders by state.settings.videoFolders.collectAsState()
    val theme by state.settings.theme.collectAsState()
    val counts by state.library.counts.collectAsState()
    val name = SettingsRepository.cleanName(model.name)
    val mobile = !Ui.isDesktop
    if (mobile) { CoverFan(); Spacer(Modifier.height(28.dp)) }
    StepTitle("TODO LISTO", if (name.isEmpty()) "¡Todo listo!" else "¡Listo, $name!", "Estamos terminando de preparar tu biblioteca.", centered = mobile)
    // Resumen en 4 tarjetas.
    val cards = listOf("${lib.songCount}" to "canciones", "${lib.videoCount}" to "videos") +
        (folders + videoFolders).distinct().size.let { n -> listOf("$n" to if (n == 1) "carpeta" else "carpetas", theme.displayName to "tema") }
    // Móvil: 2 × 2; escritorio: las 4 en una fila.
    cards.chunked(if (mobile) 2 else 4).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { (value, what) ->
                Column(Modifier.weight(1f).surface().padding(14.dp)) {
                    BasicText(value, style = Ui.type.h2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BasicText(what, style = Ui.type.caption.copy(color = c.mute))
                }
            }
        }
    }
    // Progreso real de preparar la biblioteca: lectura (portadas, letras), orden y tempo.
    val reading = lib.stage == ScanStage.READING || folders.any { LibraryFolder(it, MediaType.AUDIO) !in counts } ||
        videoFolders.any { LibraryFolder(it, MediaType.VIDEO) !in counts }
    val stage = if (reading) ScanStage.READING else lib.stage
    fun part(done: Int, total: Int) = if (total > 0) done.toFloat() / total else 0f
    val target = when (stage) {
        ScanStage.READING -> .05f + .45f * part(lib.stageDone, lib.stageTotal)
        ScanStage.SORTING -> .55f
        ScanStage.TEMPO -> .6f + .4f * part(lib.stageDone, lib.stageTotal)
        null -> 1f
    }
    val shown by animateFloatAsState(target, tween(if (Ui.reduceMotion) 0 else 500))
    Box(
        Modifier.padding(top = 8.dp).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(c.ink.copy(alpha = .12f))
            .semantics { progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo(shown, 0f..1f) },
    ) {
        Box(Modifier.fillMaxWidth(shown).height(6.dp).background(c.accent))
    }
    val message = when (stage) {
        ScanStage.READING -> "Leyendo portadas y letras…"
        ScanStage.SORTING -> "Ordenando por artista y álbum…"
        ScanStage.TEMPO -> "Calculando el tempo de cada canción…"
        null -> "Listo."
    }
    // El lector de pantalla anuncia cada etapa (no cada número).
    BasicText(
        message, style = Ui.type.caption.copy(color = c.mute, textAlign = if (mobile) TextAlign.Center else TextAlign.Start),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
    )
    if (stage != null && stage != ScanStage.SORTING && lib.stageTotal > 0) {
        BasicText(
            "${lib.stageDone} de ${lib.stageTotal}", style = Ui.type.caption.copy(color = c.mute.copy(alpha = .7f), textAlign = if (mobile) TextAlign.Center else TextAlign.Start),
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        )
    }
}

@Composable
private fun PrimaryButton(label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier, arrow: Boolean = false) {
    val c = Ui.colors
    Row(
        modifier
            .then(if (enabled) Modifier.pressable(label, .97f, onClick = onClick) else Modifier.semantics { contentDescription = "$label, desactivado" })
            .alpha(if (enabled) 1f else .45f)
            .clip(buttonShape).background(c.playBg).padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(Ui.theme.label(label), style = Ui.type.label.copy(color = c.playInk, fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (arrow) BasicText("→", style = Ui.type.label.copy(color = c.playInk, fontWeight = FontWeight.Bold), modifier = Modifier.padding(start = 10.dp))
    }
}
