package org.imdtv.iptvtv.ui.settings

import org.imdtv.iptvtv.data.settings.ThemeColour
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.imdtv.iptvtv.data.settings.AppSettings
import org.imdtv.iptvtv.data.settings.GUIDE_ROWS
import org.imdtv.iptvtv.data.settings.ThemeId

@Composable
fun AppearanceRows(s: AppSettings, first: Modifier, onTheme: (ThemeId) -> Unit, onColour: (ThemeColour) -> Unit,
    onChange: ((AppSettings) -> AppSettings) -> Unit) {
    ChoiceRow("Theme", "How the whole app looks", ThemeId.entries, s.theme, { it.label }, first, onPick = onTheme)
    // Only the two glass themes take a background colour.
    if (s.theme == ThemeId.CELESTIAL_GLASS || s.theme == ThemeId.LIQUID_GLASS) {
        ChoiceRow("Theme colour", "Background colour of ${s.theme.label}", ThemeColour.entries, s.themeColour, { it.label }, onPick = onColour)
    }
    if (s.theme == ThemeId.CELESTIAL_GLASS || s.theme == ThemeId.LIQUID_GLASS) {
        ChoiceRow("Channels in guide", "How many channel rows the guide shows at once; more rows are shorter with smaller text", GUIDE_ROWS, s.guideRows, { "$it" }) { n -> onChange { it.copy(guideRows = n) } }
    }
    ChoiceRow("Multi-line names", "Let channel names and programme titles wrap onto 2 lines in the guide. In Celestial Glass and Liquid Glass this needs taller rows: 5 channels in the guide (or 6 in Celestial Glass)",
        listOf(false, true), s.multiLineGuide, { if (it) "On" else "Off" }) { on -> onChange { it.copy(multiLineGuide = on) } }
    ChoiceRow("Channel numbers", "Show the number beside channel names", listOf(true, false), s.showChannelNumbers, { if (it) "Shown" else "Hidden" }, onPick = { n -> onChange { it.copy(showChannelNumbers = n) } })
}
