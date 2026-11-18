package org.imdtv.iptvtv.ui.settings

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
fun defaultTag(o: Any?, default: Any?) = if (default !== NoDefault && o == default) " (Default)" else ""

/**
 * A multiple-choice setting: OK opens a drop-down list, so nothing changes (or starts downloading) by a stray press.
 * Picking the current value again changes nothing.
 */
@Composable
fun <T> ChoiceRow(
    title: String, explanation: String, options: List<T>, current: T, label: (T) -> String,
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
    noun: String, order: List<K>, all: List<K>, levels: Int, label: (K) -> String,
    firstModifier: Modifier = Modifier, busy: (K?) -> Boolean = { false }, defaults: List<K> = emptyList(), onChange: (List<K>) -> Unit,
) {
    val names = listOf("First", "Second", "Third", "Fourth")
    for (level in 0 until minOf(levels, order.size + 1, all.size)) {
        val current = order.getOrNull(level)
        ChoiceRow(
            "${names[level]} priority", if (level == 0) "Where $noun come from first" else "Used when the ones above have none",
            priorityChoices(order, level, all), current, { it?.let(label) ?: "None" },
            if (level == 0) firstModifier else Modifier, busy(current), default = defaults.getOrNull(level),
        ) { onChange(withPriority(order, level, it)) }
    }
}
