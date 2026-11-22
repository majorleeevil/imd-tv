package org.imdtv.iptvtv.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import org.imdtv.iptvtv.R
import org.imdtv.iptvtv.data.parental.AgeLimit
import org.imdtv.iptvtv.data.parental.Pin
import org.imdtv.iptvtv.data.parental.UnlockFor
import org.imdtv.iptvtv.data.settings.AppSettings
import org.imdtv.iptvtv.ui.TextPrompt

/** App behaviour › Parental controls (opened only after the PIN once it's on). Turning on: PIN, confirm it, then a backup word. */
@Composable
fun ParentalRows(vm: SettingsViewModel, first: FocusRequester) {
    val s by vm.settings.collectAsState()
    /** 0 = new PIN, 1 = confirm it, 2 = backup word; null = no prompt. */
    var step by remember { mutableStateOf<Int?>(null) }
    var pin by remember { mutableStateOf("") }
    val badPin = stringResource(R.string.set_b_parental_bad_pin)
    val mismatch = stringResource(R.string.set_b_parental_mismatch)
    SwitchRow(stringResource(R.string.set_b_parental), stringResource(R.string.set_b_parental_switch_expl), s.parentalOn, Modifier.focusRequester(first)) { on ->
        if (on) step = 0 else vm.update { it.copy(parentalOn = false, parentalPin = null, parentalWord = null) }
    }
    if (s.parentalOn) {
        ChoiceRow(stringResource(R.string.set_b_age_limit), stringResource(R.string.set_b_age_limit_expl), AgeLimit.entries, s.ageLimit, { stringResource(it.labelRes) },
            default = AppSettings().ageLimit) { v -> vm.update { it.copy(ageLimit = v) } }
        ChoiceRow(stringResource(R.string.set_b_unlock_for), stringResource(R.string.set_b_unlock_for_expl), UnlockFor.entries, s.unlockFor, { stringResource(it.labelRes) },
            default = AppSettings().unlockFor) { v -> vm.update { it.copy(unlockFor = v) } }
        SettingRow(stringResource(R.string.set_b_change_pin), stringResource(R.string.set_b_change_pin_expl), null) { step = 0 }
    }
    when (step) {
        0 -> TextPrompt(stringResource(R.string.set_b_parental_new_pin), "", stringResource(R.string.live_parental_pin), onSave = { p ->
            if (p.length == 4 && p.all(Char::isDigit)) { pin = p; step = 1 } else { vm.notice.value = badPin; step = null }
        }, keyboard = KeyboardType.NumberPassword, secret = true) { step = null }
        1 -> TextPrompt(stringResource(R.string.set_b_parental_confirm_pin), "", stringResource(R.string.live_parental_pin), onSave = { p ->
            if (p == pin) step = 2 else { vm.notice.value = mismatch; step = null }
        }, keyboard = KeyboardType.NumberPassword, secret = true) { step = null }
        2 -> TextPrompt(stringResource(R.string.set_b_parental_word), "", stringResource(R.string.set_b_parental_word_hint), onSave = { w ->
            if (w.isNotBlank()) vm.update { it.copy(parentalOn = true, parentalPin = Pin.hash(pin), parentalWord = Pin.hash(w)) }
            step = null; pin = ""
        }) { step = null; pin = "" }
    }
}
