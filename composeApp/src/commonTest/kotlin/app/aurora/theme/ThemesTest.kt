package app.aurora.theme

import androidx.compose.ui.unit.dp
import app.aurora.FormFactorRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThemesTest {
    @Test
    fun sevenUniqueThemes() {
        assertEquals(7, Themes.all.size)
        assertEquals(ThemeId.entries.toSet(), Themes.all.map { it.id }.toSet())
        assertEquals(7, Themes.all.map { it.displayName }.toSet().size)
    }

    @Test
    fun onlyAuroraIsDynamic() {
        assertEquals(listOf(ThemeId.AURORA), Themes.all.filter { it.dynamicColor }.map { it.id })
    }

    @Test
    fun titleCaseRules() {
        assertEquals("buenas noches", Themes.poster.title("Buenas noches"))
        assertEquals("BUENAS NOCHES", Themes.estadio.title("Buenas noches"))
        assertEquals("Buenas noches", Themes.bruma.title("Buenas noches"))
        assertEquals("INICIO", Themes.carbono.label("Inicio"))
    }

    @Test
    fun desktopTypeIsLargerThanMobile() {
        Themes.all.forEach { t -> assertTrue(t.scale.h1Desktop > t.scale.h1, t.displayName) }
    }

    @Test
    fun pcUsesDesktopLayoutAndPhoneUsesMobile() {
        assertEquals(FormFactor.DESKTOP, FormFactorRules.decide(isDesktopPlatform = true, width = 1280.dp, height = 800.dp))
        assertEquals(FormFactor.MOBILE, FormFactorRules.decide(isDesktopPlatform = false, width = 400.dp, height = 860.dp))
        // Ventana de PC muy estrecha: diseño de teléfono.
        assertEquals(FormFactor.MOBILE, FormFactorRules.decide(isDesktopPlatform = true, width = 500.dp, height = 800.dp))
        // Tableta en horizontal: diseño de escritorio.
        assertEquals(FormFactor.DESKTOP, FormFactorRules.decide(isDesktopPlatform = false, width = 1200.dp, height = 800.dp))
    }
}
