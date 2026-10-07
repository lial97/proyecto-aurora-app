package app.aurora.data

import app.aurora.platform.KeyValueStore
import app.aurora.theme.AppTheme
import app.aurora.theme.Themes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Ajustes del usuario que sobreviven al reiniciar la app. */
class SettingsRepository(
    private val store: KeyValueStore,
    private val defaultFolders: List<String>,
    /** Quien ya tenía carpetas guardadas no ve la bienvenida (solo en escritorio: en Android antes se leía todo el dispositivo). */
    private val legacySkipsWelcome: Boolean = true,
) {
    private val _theme = MutableStateFlow(Themes.byKey(store.get(KEY_THEME)))
    val theme: StateFlow<AppTheme> = _theme

    private val _folders = MutableStateFlow(readFolders())
    /** Carpetas donde se buscan canciones. */
    val folders: StateFlow<List<String>> = _folders

    private val _videoFolders = MutableStateFlow(readVideoFolders())
    /** Carpetas donde se buscan videos musicales (solo esos videos aparecen en la app). */
    val videoFolders: StateFlow<List<String>> = _videoFolders

    private val _userName = MutableStateFlow(store.get(KEY_NAME).orEmpty())
    /** Nombre o apodo para el saludo ("Buenas noches, Lila"); vacío = sin nombre. */
    val userName: StateFlow<String> = _userName

    /**
     * La bienvenida ya se completó. Quien ya tenía carpetas guardadas (versiones anteriores) no la ve.
     */
    val onboardingDone: Boolean get() = store.get(KEY_ONBOARDING) == "1" || (legacySkipsWelcome && store.get(KEY_ONBOARDING) == null && store.get(KEY_FOLDERS) != null)

    /** Paso de la configuración inicial donde quedó el usuario (0-5), para retomarla si cerró la app. */
    var onboardingStep: Int
        get() = store.get(KEY_STEP)?.toIntOrNull()?.coerceIn(0, 5) ?: 0
        set(v) = store.put(KEY_STEP, v.toString())

    fun finishOnboarding() {
        store.put(KEY_ONBOARDING, "1")
        store.put(KEY_STEP, null)
        // A partir de aquí las listas se guardan aunque queden vacías.
        saveFolders(_folders.value); saveVideoFolders(_videoFolders.value)
    }

    /**
     * Se abrió la configuración inicial: desde ahora las carpetas que se elijan no cuentan como "usuario de una
     * versión anterior" (si no, al cerrar la app a mitad y volver, la configuración se saltaba).
     */
    fun markOnboardingStarted() {
        if (store.get(KEY_ONBOARDING) == null) store.put(KEY_ONBOARDING, "0")
    }

    /** Opción de depuración: la próxima vez (o ahora) se vuelve a mostrar la configuración inicial. */
    fun restartOnboarding() {
        store.put(KEY_ONBOARDING, "0")
        store.put(KEY_STEP, "0")
    }

    fun setUserName(name: String) {
        val clean = cleanName(name)
        _userName.value = clean
        store.put(KEY_NAME, clean.ifEmpty { null })
    }

    val volume: Float get() = store.get(KEY_VOLUME)?.toFloatOrNull() ?: 0.8f

    fun setTheme(theme: AppTheme) {
        _theme.value = theme
        store.put(KEY_THEME, theme.id.name)
    }

    /** @return `false` si la carpeta ya estaba. */
    fun addFolder(path: String): Boolean {
        val clean = path.trim().trimEnd('/', '\\').ifEmpty { return false }
        if (clean in _folders.value) return false
        saveFolders(_folders.value + clean)
        return true
    }

    fun removeFolder(path: String) = saveFolders(_folders.value - path)

    /** @return `false` si la carpeta ya estaba. */
    fun addVideoFolder(path: String): Boolean {
        val clean = path.trim().trimEnd('/', '\\').ifEmpty { return false }
        if (clean in _videoFolders.value) return false
        saveVideoFolders(_videoFolders.value + clean)
        return true
    }

    fun removeVideoFolder(path: String) = saveVideoFolders(_videoFolders.value - path)

    fun saveVolume(v: Float) = store.put(KEY_VOLUME, v.toString())

    private fun readFolders(): List<String> {
        val raw = store.get(KEY_FOLDERS) ?: return defaultFolders
        return raw.split('\n').filter { it.isNotBlank() }
    }

    /** Sin lista propia de videos (versiones anteriores), los videos se buscaban en las mismas carpetas. */
    private fun readVideoFolders(): List<String> {
        val raw = store.get(KEY_VIDEO_FOLDERS) ?: return readFolders()
        return raw.split('\n').filter { it.isNotBlank() }
    }

    private fun saveVideoFolders(list: List<String>) {
        _videoFolders.value = list
        store.put(KEY_VIDEO_FOLDERS, list.joinToString("\n"))
    }

    private fun saveFolders(list: List<String>) {
        _folders.value = list
        // Se guarda aunque quede vacía para no volver a las carpetas por defecto.
        store.put(KEY_FOLDERS, list.joinToString("\n"))
    }

    companion object {
        const val KEY_THEME = "tema"
        const val KEY_FOLDERS = "carpetas"
        const val KEY_VOLUME = "volumen"
        const val KEY_VIDEO_FOLDERS = "carpetas_video"
        const val KEY_NAME = "nombre"
        const val KEY_ONBOARDING = "bienvenida_lista"
        const val KEY_STEP = "bienvenida_paso"
        const val MAX_NAME = 20

        /** Sin espacios de sobra y como mucho [MAX_NAME] caracteres. */
        fun cleanName(name: String) = name.trim().replace(Regex("\\s+"), " ").take(MAX_NAME).trim()
    }
}
