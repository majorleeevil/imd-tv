package org.imdtv.iptvtv.ui.settings

import org.imdtv.iptvtv.data.settings.ThemeColour
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import org.imdtv.iptvtv.data.settings.AppSettings
import org.imdtv.iptvtv.data.settings.guideRowChoices
import org.imdtv.iptvtv.data.settings.HeaderSize
import org.imdtv.iptvtv.data.settings.PlayerClock
import org.imdtv.iptvtv.data.settings.ThemeId

@Composable
fun AppearanceRows(s: AppSettings, first: Modifier, dateTimeOpener: Modifier, onDateTime: () -> Unit, onReset: () -> Unit,
    onChange: ((AppSettings) -> AppSettings) -> Unit) {
    // Each theme keeps its own colour, mode, rows, header and fade (withTheme).
    ChoiceRow("Theme", "How the whole app looks", ThemeId.entries, s.theme, { it.label }, first, default = AppSettings().theme, onPick = { t -> onChange { it.withTheme(t) } })
    // Only the two glass themes take a background colour.
    if (s.theme == ThemeId.CELESTIAL_GLASS || s.theme == ThemeId.LIQUID_GLASS) {
        ChoiceRow("Theme colour", "Background colour of ${s.theme.label}", ThemeColour.entries, s.themeColour, { it.label }, default = AppSettings().themeColour) { c -> onChange { it.copy(themeColour = c) } }
        ChoiceRow("Theme mode", "Dark uses near-black versions of the theme colour", listOf(false, true), s.themeDark, { if (it) "Dark" else "Light" }, default = AppSettings().themeDark, onPick = { d -> onChange { it.copy(themeDark = d) } })
        val rowChoices = guideRowChoices(s.headerSize, s.theme == ThemeId.LIQUID_GLASS)
        ChoiceRow("Channels in guide", "How many channel rows the guide shows at once; more rows are shorter with smaller text", rowChoices, s.guideRows, { "$it" }, default = rowChoices.first()) { n -> onChange { it.copy(guideRows = n) } }
        ChoiceRow("Header size", "A smaller header makes the guide taller, with more channels", HeaderSize.entries, s.headerSize, { it.label }, default = AppSettings().headerSize) { h -> onChange { it.withHeader(h) } }
        if (s.theme == ThemeId.CELESTIAL_GLASS) {
            SwitchRow("Preview fade", "Blend the preview into the background; off shows it as a framed box", s.previewFade) { on -> onChange { it.copy(previewFade = on) } }
        }
    }
    ChoiceRow("Movies & Series rows", "How many rows of posters the Movies and Series pages show at once; 2 rows uses smaller posters", listOf(false, true), s.vodTwoRows, { if (it) "2 rows" else "1 row" }, default = AppSettings().vodTwoRows, onPick = { v -> onChange { it.copy(vodTwoRows = v) } })
    SwitchRow("Multi-line names","Let channel names and programme titles wrap onto 2 lines in the guide. In Celestial Glass and Liquid Glass this needs taller rows: 5 channels in the guide (or 6 in Celestial Glass)",
        s.multiLineGuide) { on -> onChange { it.copy(multiLineGuide = on) } }
    val dt = org.imdtv.iptvtv.ui.settings.ChannelPage.DATE_TIME
    SettingRow(dt.title, dt.explanation, "›", dateTimeOpener, onClick = onDateTime)
    var confirmReset by remember { mutableStateOf(false) }
    SettingRow("Reset appearance", if (confirmReset) "Press OK again to put every Appearance setting back to its default" else "Theme, colours, guide layout and clock back to their defaults", null) {
        if (confirmReset) { onChange { it.resetAppearance() }; confirmReset = false; onReset() } else confirmReset = true
    }

}

/** Appearance › Date and time. */
@Composable
fun DateTimeRows(vm: SettingsViewModel, first: FocusRequester) {
    val s by vm.settings.collectAsState()
    SwitchRow("Clock", "The time at the top of the screen", s.showDateTime, Modifier.focusRequester(first)) { on -> vm.update { it.copy(showDateTime = on) } }
    SwitchRow("Date", "The date beside the clock", s.showDate) { on -> vm.update { it.copy(showDate = on) } }
    SwitchRow("24-hour clock", "13:05 instead of 1.05pm", s.clock24h) { on -> vm.update { it.copy(clock24h = on) } }
    ChoiceRow("Clock in player", "Show the time in the top-right corner of full screen", PlayerClock.entries, s.playerClock, { it.label }, default = AppSettings().playerClock) { c ->
        vm.update { it.copy(playerClock = c) }
    }
}
