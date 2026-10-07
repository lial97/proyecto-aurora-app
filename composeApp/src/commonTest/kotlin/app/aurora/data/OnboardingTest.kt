package app.aurora.data

import app.aurora.platform.MemoryStore
import app.aurora.screens.greetingFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnboardingTest {
    @Test fun nameIsTrimmedAndLimitedTo20() {
        val s = SettingsRepository(MemoryStore(), emptyList())
        s.setUserName("   Lila    María  ")
        assertEquals("Lila María", s.userName.value)
        s.setUserName("Maximiliano Alejandro de la Torre")
        assertEquals(20, s.userName.value.length)
    }

    @Test fun newUserSeesWelcomeAndResumesStep() {
        val store = MemoryStore()
        val s = SettingsRepository(store, emptyList())
        assertFalse(s.onboardingDone)
        s.onboardingStep = 2
        assertEquals(2, SettingsRepository(store, emptyList()).onboardingStep)
        s.finishOnboarding()
        assertTrue(SettingsRepository(store, emptyList()).onboardingDone)
    }

    @Test fun resumesAnyOfTheSixStepsAndCanBeRepeated() {
        val store = MemoryStore()
        val s = SettingsRepository(store, emptyList())
        s.onboardingStep = 4
        assertEquals(4, SettingsRepository(store, emptyList()).onboardingStep)
        s.addFolder("/m")
        s.finishOnboarding()
        assertTrue(SettingsRepository(store, emptyList()).onboardingDone)
        // "Repetir configuración inicial" (depuración): vuelve a la bienvenida aunque ya haya carpetas.
        s.restartOnboarding()
        val again = SettingsRepository(store, emptyList())
        assertFalse(again.onboardingDone)
        assertEquals(0, again.onboardingStep)
    }

    @Test fun closingMidSetupAfterChoosingAFolderResumesSetup() {
        val store = MemoryStore()
        val s = SettingsRepository(store, emptyList())
        assertFalse(s.onboardingDone)
        s.markOnboardingStarted()
        s.addFolder("/home/u/Música")
        s.onboardingStep = 3
        val again = SettingsRepository(store, emptyList())
        assertFalse(again.onboardingDone, "con carpeta elegida a mitad, la configuración sigue pendiente")
        assertEquals(3, again.onboardingStep)
    }

    @Test fun previousUsersSkipWelcomeAndKeepVideosInSameFolders() {
        val store = MemoryStore().apply { put(SettingsRepository.KEY_FOLDERS, "/home/u/Música") }
        val s = SettingsRepository(store, emptyList())
        assertTrue(s.onboardingDone)
        assertEquals(listOf("/home/u/Música"), s.videoFolders.value)
    }

    @Test fun videoFoldersAreSeparate() {
        val store = MemoryStore()
        val s = SettingsRepository(store, emptyList())
        s.addFolder("/m"); s.addVideoFolder("/v")
        val again = SettingsRepository(store, emptyList())
        assertEquals(listOf("/m"), again.folders.value)
        assertEquals(listOf("/v"), again.videoFolders.value)
    }

    @Test fun greetingByHourWithName() {
        assertEquals("Buenos días, Lila", greetingFor(5, "Lila"))
        assertEquals("Buenas tardes", greetingFor(12))
        assertEquals("Buenas noches, Lila", greetingFor(19, " Lila "))
        assertEquals("Buenas noches", greetingFor(4, "  "))
    }
}

class OnboardingMobileTest {
    @Test fun oldAndroidFoldersDoNotSkipWelcome() {
        val store = MemoryStore().apply { put(SettingsRepository.KEY_FOLDERS, "/storage/emulated/0/Music") }
        assertFalse(SettingsRepository(store, emptyList(), legacySkipsWelcome = false).onboardingDone)
    }
}
