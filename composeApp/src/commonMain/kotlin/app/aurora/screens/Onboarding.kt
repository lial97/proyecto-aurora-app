package app.aurora.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.aurora.AppState
import app.aurora.components.AuroraIcon
import app.aurora.components.Icon
import app.aurora.components.IconButton
import app.aurora.components.pressable
import app.aurora.components.surface
import app.aurora.components.typingField
import app.aurora.data.LibraryFolder
import app.aurora.data.SettingsRepository
import app.aurora.domain.MediaType
import app.aurora.theme.Themes
import app.aurora.theme.Ui
import app.aurora.theme.label
import app.aurora.theme.title

/**
 * Estado de la bienvenida, fuera de la pantalla: al cambiar de tema la pantalla se dibuja dos veces
 * (transición de 600 ms) y ambas copias deben mostrar lo mismo.
 */
class OnboardingModel(private val state: AppState) {
    var step by mutableIntStateOf(state.settings.onboardingStep)
        private set
    var name by mutableStateOf(state.settings.userName.value)
        private set

    fun go(to: Int) { step = to.coerceIn(0, LAST); state.settings.onboardingStep = step }
    fun next() = if (step == LAST) finish() else go(step + 1)
    fun back() = go(step - 1)
    fun updateName(v: String) {
        if (v.length > SettingsRepository.MAX_NAME) return
        name = v
        state.settings.setUserName(v)
    }
    fun finish() = state.finishOnboarding()

    companion object { const val LAST = 3 }
}

/** Primera vez que se abre la app: nombre, carpetas de música, carpetas de videos y tema. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun OnboardingScreen(state: AppState, model: OnboardingModel) {
    val c = Ui.colors
    val step = model.step
    // Atrás del sistema: vuelve al paso anterior.
    androidx.compose.ui.backhandler.BackHandler(enabled = step > 0) { model.back() }
    val folders by state.settings.folders.collectAsState()
    val videoFolders by state.settings.videoFolders.collectAsState()
    val userName by state.settings.userName.collectAsState()

    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.fillMaxSize().widthIn(max = 560.dp).padding(horizontal = 22.dp)) {
            // Indicador de progreso: 4 puntos.
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                repeat(OnboardingModel.LAST + 1) { i ->
                    val w by animateDpAsState(if (i == step) 26.dp else 8.dp, tween(if (Ui.reduceMotion) 0 else 300))
                    Box(Modifier.padding(horizontal = 4.dp).height(8.dp).width(w).clip(RoundedCornerShape(50))
                        .background(if (i <= step) c.accent else c.ink.copy(alpha = .2f)))
                }
            }
            BasicText("Paso ${step + 1} de ${OnboardingModel.LAST + 1}", style = Ui.type.caption, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp))

            val dur = if (Ui.reduceMotion) 0 else 320
            AnimatedContent(
                step, Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    val d = dur
                    (fadeIn(tween(d)) + slideInHorizontally(tween(d)) { dir * it / 6 }).togetherWith(fadeOut(tween(d / 2)))
                },
            ) { s ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 28.dp, bottom = 16.dp)) {
                    when (s) {
                        0 -> NameStep(model)
                        1 -> FolderStep(state, MediaType.AUDIO, folders)
                        2 -> FolderStep(state, MediaType.VIDEO, videoFolders)
                        else -> ThemeStep(state)
                    }
                }
            }

            // Atrás y Siguiente.
            val skip = (step == 0 && model.name.isBlank()) || (step == 1 && folders.isEmpty()) || (step == 2 && videoFolders.isEmpty())
            val nextLabel = when {
                step == OnboardingModel.LAST -> if (userName.isNotBlank()) "Empezar, $userName" else "Empezar"
                skip -> "Saltar"
                else -> "Siguiente"
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (step > 0) StepButton("Atrás", primary = false, Modifier.weight(1f)) { model.back() }
                else Spacer(Modifier.weight(1f))
                StepButton(nextLabel, primary = !skip || step == OnboardingModel.LAST, Modifier.weight(if (step == OnboardingModel.LAST) 1.6f else 1f)) { model.next() }
            }
        }
    }
}

@Composable
private fun StepTitle(title: String, lead: String) {
    BasicText(Ui.theme.title(title), style = Ui.type.h1.copy(fontSize = Ui.type.h1.fontSize * .78f))
    BasicText(lead, style = Ui.type.rowSubtitle, modifier = Modifier.padding(top = 8.dp, bottom = 22.dp))
}

@Composable
private fun NameStep(model: OnboardingModel) {
    val c = Ui.colors
    StepTitle("¿Cómo te llamamos?", "Tu nombre o apodo aparece en el saludo de Inicio. Puedes saltar este paso.")
    Row(Modifier.fillMaxWidth().surface(Ui.shapes.chip).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            model.name, model::updateName, singleLine = true,
            textStyle = Ui.type.body.copy(fontSize = Ui.type.body.fontSize * 1.15f), cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { model.next() }),
            modifier = Modifier.weight(1f).typingField(),
            decorationBox = { inner ->
                if (model.name.isEmpty()) BasicText("Por ejemplo, Lila", style = Ui.type.body.copy(color = c.mute, fontSize = Ui.type.body.fontSize * 1.15f))
                inner()
            },
        )
        BasicText("${model.name.length}/${SettingsRepository.MAX_NAME}", style = Ui.type.caption, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun FolderStep(state: AppState, type: MediaType, folders: List<String>) {
    val c = Ui.colors
    val video = type == MediaType.VIDEO
    val counts by state.library.counts.collectAsState()
    if (video) StepTitle("¿Y tus videos musicales?", "Solo los videos de estas carpetas aparecerán en la app. Puedes saltar este paso.")
    else StepTitle("¿Dónde está tu música?", "Aurora busca solo en las carpetas que elijas, con todas sus subcarpetas. Puedes añadir varias.")
    if (state.platform.media.canPickFolder) {
        Row(
            Modifier.fillMaxWidth().pressable("Elegir carpeta", .97f) { state.pickAndAddFolder(type, quiet = true) }
                .clip(Ui.shapes.chip).background(c.playBg).padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(AuroraIcon.Plus, c.playInk, size = 18.dp)
            BasicText(Ui.theme.label("Elegir carpeta"), style = Ui.type.label.copy(color = c.playInk, fontWeight = FontWeight.Bold), modifier = Modifier.padding(start = 8.dp))
        }
    }
    Spacer(Modifier.height(14.dp))
    if (folders.isEmpty()) {
        BasicText(
            if (video) "Sin carpetas de videos: los videos de tu galería no se mostrarán." else "Aún no hay carpetas. Mientras tanto verás canciones de ejemplo.",
            style = Ui.type.body.copy(color = c.mute), modifier = Modifier.padding(4.dp),
        )
    }
    folders.forEach { path ->
        val label = state.platform.media.describeFolder(path)
        val n = counts[LibraryFolder(path, type)]
        Row(Modifier.padding(bottom = 10.dp).fillMaxWidth().surface().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(c.accent.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                Icon(if (video) AuroraIcon.Video else AuroraIcon.Folder, c.accent, size = 19.dp)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                BasicText(label.name, style = Ui.type.rowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicText(label.path, style = Ui.type.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicText(foundLabel(n, video), style = Ui.type.caption.copy(color = if (n == null) c.mute else c.accent, fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(top = 2.dp))
            }
            IconButton(AuroraIcon.Close, "Quitar ${label.name}", { state.removeFolder(path, type) }, tint = c.mute, size = 34.dp, iconSize = 16.dp)
        }
    }
}

@Composable
private fun ThemeStep(state: AppState) {
    val theme by state.settings.theme.collectAsState()
    StepTitle("Elige tu estilo", "Colores, letra y forma de las portadas. Toca uno para verlo.")
    Themes.all.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { t -> ThemePreview(t, t.id == theme.id, Modifier.weight(1f)) { state.settings.setTheme(t) } }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    BasicText("Puedes cambiarlo cuando quieras en Ajustes > Apariencia", style = Ui.type.caption, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun StepButton(label: String, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Ui.colors
    Box(
        modifier.pressable(label, .97f, onClick = onClick).clip(Ui.shapes.chip)
            .background(if (primary) c.playBg else c.surface).padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(Ui.theme.label(label), style = Ui.type.label.copy(color = if (primary) c.playInk else c.ink, fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
