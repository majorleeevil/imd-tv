package org.imdtv.iptvtv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import org.imdtv.iptvtv.R
import org.imdtv.iptvtv.data.parental.ParentalGate
import org.imdtv.iptvtv.data.parental.Pin
import org.imdtv.iptvtv.data.settings.AppSettings

/** The parental controls PIN, over every screen while [ParentalGate.request] is open. The backup word works as well (a forgotten PIN). */
@Composable
fun ParentalPrompt(gate: ParentalGate, s: AppSettings) {
    val req = gate.request.collectAsState().value ?: return
    var text by remember(req) { mutableStateOf("") }
    var wrong by remember(req) { mutableStateOf(false) }
    var word by remember(req) { mutableStateOf(false) }
    MenuFrame(stringResource(if (word) R.string.live_parental_enter_word else R.string.live_parental_enter_pin), { req.complete(false) }) { first ->
        // Keyed on the mode: the keyboard type is read when the field opens.
        androidx.compose.runtime.key(word) {
            Field(stringResource(if (word) R.string.live_parental_word else R.string.live_parental_pin), text,
                if (word) KeyboardType.Password else KeyboardType.NumberPassword, secret = true, modifier = Modifier.focusRequester(first)) { text = it; wrong = false }
        }
        if (wrong) Text(stringResource(if (word) R.string.live_parental_wrong_word else R.string.live_parental_wrong_pin), fontSize = 13.sp, color = androidx.compose.ui.graphics.Color(0xFFFF8A80))
        MenuItem(stringResource(R.string.live_parental_unlock), Modifier) {
            if (Pin.matches(text, if (word) s.parentalWord else s.parentalPin)) req.complete(true) else { wrong = true; text = "" }
        }
        MenuItem(stringResource(if (word) R.string.live_parental_use_pin else R.string.live_parental_forgot), Modifier) { word = !word; text = ""; wrong = false }
        MenuItem(stringResource(R.string.live_cancel), Modifier) { req.complete(false) }
    }
}
