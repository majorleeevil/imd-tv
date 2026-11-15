package org.imdtv.iptvtv.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.imdtv.iptvtv.data.settings.ThemeId
import org.imdtv.iptvtv.data.settings.ThemeColour
import org.imdtv.iptvtv.data.settings.themeIdOf

class ThemesTest {
    @Test fun oldNamesReadAsCelestialGlass() {
        listOf("SKY_GLASS", "MIDNIGHT", "PLEZY", "CUSTOM", "nonsense", null)
            .forEach { assertEquals(it, ThemeId.CELESTIAL_GLASS, themeIdOf(it)) }
    }

    @Test fun liquidGlassNameNowReads() = assertEquals(ThemeId.LIQUID_GLASS, themeIdOf("LIQUID_GLASS"))

    /** The default colour keeps Celestial Glass exactly as it was signed off. */
    @Test fun defaultColourKeepsGlass() = assertEquals(colorsOf(ThemeId.CELESTIAL_GLASS), colorsOf(ThemeId.CELESTIAL_GLASS, ThemeColour.DEFAULT))

    @Test fun colourChangesGlassAndLiquidBackgrounds() {
        ThemeColour.entries.drop(1).forEach { c ->
            assertTrue(c.name, colorsOf(ThemeId.CELESTIAL_GLASS, c).bg != colorsOf(ThemeId.CELESTIAL_GLASS).bg)
            assertTrue(c.name, colorsOf(ThemeId.LIQUID_GLASS, c).bg != colorsOf(ThemeId.LIQUID_GLASS).bg)
        }
    }

    @Test fun colourIgnoredByOtherThemes() = assertEquals(colorsOf(ThemeId.XCUBE_180), colorsOf(ThemeId.XCUBE_180, ThemeColour.RED))

    @Test fun everyThemeHasColours() {
        ThemeId.entries.forEach { assertTrue(it.name, colorsOf(it).bg.size >= 2) }
    }

    @Test fun interOnlyForTheGlassThemes() = assertEquals(
        setOf(ThemeId.CELESTIAL_GLASS, ThemeId.LIQUID_GLASS), ThemeId.entries.filter { usesInter(it) }.toSet())
}
