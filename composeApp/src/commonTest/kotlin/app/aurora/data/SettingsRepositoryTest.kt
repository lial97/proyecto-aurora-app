package app.aurora.data

import app.aurora.platform.MemoryStore
import app.aurora.theme.ThemeId
import app.aurora.theme.Themes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsRepositoryTest {
    @Test
    fun themeSurvivesRestart() {
        val store = MemoryStore()
        SettingsRepository(store, emptyList()).setTheme(Themes.carbono)
        assertEquals(ThemeId.CARBONO, SettingsRepository(store, emptyList()).theme.value.id)
    }

    @Test
    fun unknownThemeFallsBackToAurora() {
        val store = MemoryStore().apply { put(SettingsRepository.KEY_THEME, "NO_EXISTE") }
        assertEquals(ThemeId.AURORA, SettingsRepository(store, emptyList()).theme.value.id)
    }

    @Test
    fun usesDefaultFoldersUntilUserChanges() {
        val store = MemoryStore()
        val s = SettingsRepository(store, listOf("/home/u/Música"))
        assertEquals(listOf("/home/u/Música"), s.folders.value)
        assertTrue(s.addFolder("/media/usb/Canciones/"))
        assertFalse(s.addFolder("/media/usb/Canciones"), "duplicada")
        s.removeFolder("/home/u/Música")
        val again = SettingsRepository(store, listOf("/home/u/Música"))
        assertEquals(listOf("/media/usb/Canciones"), again.folders.value)
    }

    @Test
    fun emptyListIsRememberedInsteadOfDefaults() {
        val store = MemoryStore()
        val s = SettingsRepository(store, listOf("/a"))
        s.removeFolder("/a")
        assertEquals(emptyList(), SettingsRepository(store, listOf("/a")).folders.value)
    }
}
