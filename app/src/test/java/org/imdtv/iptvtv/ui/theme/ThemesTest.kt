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

    /** Theme mode › Dark: every colour's background is darker than its light version, in both glass themes. */
    @Test fun darkIsDarkerForEveryColour() {
        fun sum(c: ThemeColors) = c.bg.sumOf { (it.red + it.green + it.blue).toDouble() }
        for (id in listOf(ThemeId.CELESTIAL_GLASS, ThemeId.LIQUID_GLASS)) ThemeColour.entries.forEach { c ->
            assertTrue("$id $c", sum(colorsOf(id, c, dark = true)) < sum(colorsOf(id, c)))
            assertTrue("$id $c", colorsOf(id, c, dark = true).bg.all { it.alpha == 1f })
        }
    }

    /** dark = false is today's output exactly (default argument and explicit false). */
    @Test fun lightIsUnchanged() {
        assertEquals(colorsOf(ThemeId.CELESTIAL_GLASS), colorsOf(ThemeId.CELESTIAL_GLASS, ThemeColour.DEFAULT, dark = false))
        ThemeId.entries.forEach { id -> ThemeColour.entries.forEach { c -> assertEquals("$id $c", colorsOf(id, c), colorsOf(id, c, false)) } }
        assertEquals(androidx.compose.ui.graphics.Color(0xFF0A1753), colorsOf(ThemeId.CELESTIAL_GLASS).bg[0])
    }

    @Test fun darkIgnoredByOtherThemes() = listOf(ThemeId.CELESTIAL_PLUS, ThemeId.CELESTIAL_RETRO, ThemeId.XCUBE_180, ThemeId.GAMESTATION)
        .forEach { assertEquals(it.name, colorsOf(it, ThemeColour.RED), colorsOf(it, ThemeColour.RED, dark = true)) }

    @Test fun everyThemeHasColours() {
        ThemeId.entries.forEach { assertTrue(it.name, colorsOf(it).bg.size >= 2) }
    }

    @Test fun interOnlyForTheGlassThemes() = assertEquals(
        setOf(ThemeId.CELESTIAL_GLASS, ThemeId.LIQUID_GLASS), ThemeId.entries.filter { usesInter(it) }.toSet())
}
