package org.imdtv.iptvtv.ui.settings

import org.imdtv.iptvtv.data.settings.ThemeColour
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.imdtv.iptvtv.data.settings.AppSettings
import org.imdtv.iptvtv.data.settings.ThemeId

@Composable
fun AppearanceRows(s: AppSettings, first: Modifier, onTheme: (ThemeId) -> Unit, onColour: (ThemeColour) -> Unit) {
    ChoiceRow("Theme", "How the whole app looks", ThemeId.entries, s.theme, { it.label }, first, onPick = onTheme)
    // Only the two glass themes take a background colour.
    if (s.theme == ThemeId.CELESTIAL_GLASS || s.theme == ThemeId.LIQUID_GLASS) {
        ChoiceRow("Theme colour", "Background colour of ${s.theme.label}", ThemeColour.entries, s.themeColour, { it.label }, onPick = onColour)
    }
}
