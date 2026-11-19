package org.imdtv.iptvtv.ui.settings

import androidx.compose.ui.res.stringResource
import org.imdtv.iptvtv.R
import androidx.annotation.StringRes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import org.imdtv.iptvtv.data.settings.priorityChoices
import org.imdtv.iptvtv.data.settings.withPriority
import org.imdtv.iptvtv.ui.MenuFrame
import org.imdtv.iptvtv.ui.MenuItem

/** "No default given" for [ChoiceRow]/[StepperRow] (null can be a real default, e.g. the TV's subtitle language). */
object NoDefault

/** " (Default)" after the default option's label, in the drop-down list only (user, 2026-11-17). */
@Composable
fun defaultTag(o: Any?, default: Any?) = if (default !== NoDefault && o == default) stringResource(R.string.set_a_default_tag) else ""

/**
 * A multiple-choice setting: OK opens a drop-down list, so nothing changes (or starts downloading) by a stray press.
 * Picking the current value again changes nothing.
 */
@Composable
fun <T> ChoiceRow(
    title: String, explanation: String, options: List<T>, current: T, label: @Composable (T) -> String,
    modifier: Modifier = Modifier, busy: Boolean = false, default: Any? = NoDefault, onPick: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    SettingRow(title, explanation, label(current) + "  ▾", modifier, busy) { open = true }
    if (open) MenuFrame(title, onDismiss = { open = false }) { first ->
        options.forEach { o ->
            MenuItem((if (o == current) "✓  " else "     ") + label(o) + defaultTag(o, default), if (o == current) Modifier.focusRequester(first) else Modifier) {
                open = false
                if (o != current) onPick(o)
            }
        }
    }
}

/** First / Second / Third priority drop-downs over [all]; a level shows once the level above it is set. */
@Composable
fun <K> PriorityRows(
    @StringRes firstExplanation: Int, order: List<K>, all: List<K>, levels: Int, label: @Composable (K) -> String,
    firstModifier: Modifier = Modifier, busy: (K?) -> Boolean = { false }, defaults: List<K> = emptyList(), onChange: (List<K>) -> Unit,
) {
    val names = listOf(R.string.set_a_priority_first, R.string.set_a_priority_second, R.string.set_a_priority_third, R.string.set_a_priority_fourth)
    for (level in 0 until minOf(levels, order.size + 1, all.size)) {
        val current = order.getOrNull(level)
        ChoiceRow(
            stringResource(names[level]), if (level == 0) stringResource(firstExplanation) else stringResource(R.string.set_a_priority_used_when),
            priorityChoices(order, level, all), current, { if (it == null) stringResource(R.string.set_a_none) else label(it) },
            if (level == 0) firstModifier else Modifier, busy(current), default = defaults.getOrNull(level),
        ) { onChange(withPriority(order, level, it)) }
    }
}
