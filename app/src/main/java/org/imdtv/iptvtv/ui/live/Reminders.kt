package org.imdtv.iptvtv.ui.live

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import org.imdtv.iptvtv.R
import org.imdtv.iptvtv.data.catalogue.displayName
import org.imdtv.iptvtv.data.db.ReminderEntity

/** The reminder popup shows this long before the programme starts (#18). */
const val REMIND_BEFORE = 2 * 60_000L
/** A reminder nobody saw (the app wasn't in front) is dropped this long after the start; the row has no stop time. */
const val REMIND_LATE = 15 * 60_000L

fun ReminderEntity.dueAt() = programmeStart - REMIND_BEFORE
fun ReminderEntity.gone(now: Long) = now >= programmeStart + REMIND_LATE

/** The reminder to show now: the earliest one that is due and not gone. */
fun firstDue(rows: List<ReminderEntity>, now: Long) = rows.filter { it.dueAt() <= now && !it.gone(now) }.minByOrNull { it.programmeStart }

/** When the next reminder falls due, or null when none is waiting. */
fun nextDue(rows: List<ReminderEntity>, now: Long) = rows.filter { it.dueAt() > now }.minOfOrNull { it.dueAt() }

/** The reminder popup over every screen: Watch plays the channel full screen, Dismiss (or Back) drops the reminder. */
@androidx.compose.runtime.Composable
fun ReminderPrompt(vm: LiveViewModel, onWatch: (org.imdtv.iptvtv.data.db.ChannelEntity) -> Unit) {
    val r = vm.dueReminder.collectAsState().value ?: return
    // Looked up before the popup shows, so focus starts on Watch rather than on Dismiss.
    val looked by androidx.compose.runtime.produceState<Pair<ReminderEntity?, org.imdtv.iptvtv.data.db.ChannelEntity?>>(null to null, r) { value = r to vm.reminderChannel(r) }
    // Unanswered (the app was in the background): it closes once the programme counts as gone.
    androidx.compose.runtime.LaunchedEffect(r) { kotlinx.coroutines.delay((r.programmeStart + REMIND_LATE - System.currentTimeMillis()).coerceAtLeast(0)); vm.answerReminder(r) }
    if (looked.first != r) return
    val c = looked.second
    org.imdtv.iptvtv.ui.MenuFrame(stringResource(R.string.live_reminder_title), onDismiss = { vm.answerReminder(r) }) { first ->
        Text(stringResource(R.string.live_reminder_body, r.title, org.imdtv.iptvtv.ui.clockTime(r.programmeStart), c?.displayName(vm.strip.value).orEmpty()),
            fontSize = 15.sp, color = Color.White, modifier = Modifier.padding(bottom = 10.dp))
        if (c != null) org.imdtv.iptvtv.ui.MenuItem(stringResource(R.string.set_b_watch), Modifier.focusRequester(first)) { vm.answerReminder(r); onWatch(c) }
        org.imdtv.iptvtv.ui.MenuItem(stringResource(R.string.live_reminder_dismiss), if (c == null) Modifier.focusRequester(first) else Modifier) { vm.answerReminder(r) }
    }
}
