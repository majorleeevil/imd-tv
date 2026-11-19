package org.imdtv.iptvtv.recording

import androidx.compose.foundation.background
import androidx.annotation.StringRes
import org.imdtv.iptvtv.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import org.imdtv.iptvtv.ui.MenuFrame
import org.imdtv.iptvtv.ui.MenuItem

/** Questions and notices about connections and recordings, shown by the activity's prompt host. */
sealed interface Prompt {
    /** Decision 8: a scheduled recording needs the connection the player holds. No answer by [deadline] = record. */
    data class StartsSoon(val id: Long, val title: String, val channel: String, val deadline: Long) : Prompt
    /** Decision 9: watching needs the connection a recording holds. */
    data class StopRecordingToWatch(val title: String, val onStop: () -> Unit) : Prompt
    /** Decision 10: Record now needs the connection the player holds. */
    data class RecordNowStopsWatching(val channel: String, val onRecord: () -> Unit) : Prompt
    /** [leavePlayer]: playback was stopped for a recording; a full-screen live player returns to the guide. */
    data class Notice(val text: String, val leavePlayer: Boolean = false, @StringRes val res: Int = 0, val args: List<Any> = emptyList()) : Prompt
}

/** Shows the repository's current [Prompt]; drawn last over every screen. [onLeavePlayer] returns a live player to the guide. */
@androidx.compose.runtime.Composable
fun PromptHost(repo: RecordingRepository, onLeavePlayer: () -> Unit) {
    val p by repo.prompt.collectAsState()
    fun clear(q: Prompt) { if (repo.prompt.value == q) repo.prompt.value = null }
    when (val q = p) {
        null -> Unit
        is Prompt.StartsSoon -> {
            // No answer = record: the prompt just goes away at the start.
            LaunchedEffect(q) { delay((q.deadline - System.currentTimeMillis()).coerceAtLeast(0)); clear(q) }
            MenuFrame(stringResource(R.string.vod_rec_starts_soon), onDismiss = { clear(q) }) { first ->
                Body(stringResource(R.string.vod_rec_starts_soon_body, q.title, q.channel))
                MenuItem(stringResource(R.string.vod_rec_record), Modifier.focusRequester(first)) { clear(q) }
                MenuItem(stringResource(R.string.vod_rec_keep_watching), Modifier) { repo.keepWatching(q.id) }
            }
        }
        is Prompt.StopRecordingToWatch -> MenuFrame(stringResource(R.string.vod_rec_stop_q), onDismiss = { repo.keepRecording() }) { first ->
            Body(stringResource(R.string.vod_rec_stop_to_watch_body, q.title))
            MenuItem(stringResource(R.string.vod_rec_keep_recording), Modifier.focusRequester(first)) { repo.keepRecording() }
            MenuItem(stringResource(R.string.vod_rec_stop_recording), Modifier) { clear(q); q.onStop() }
        }
        is Prompt.RecordNowStopsWatching -> MenuFrame(stringResource(R.string.vod_rec_record_channel_q, q.channel), onDismiss = { clear(q) }) { first ->
            Body(stringResource(R.string.vod_rec_record_now_body))
            MenuItem(stringResource(R.string.vod_rec_record), Modifier.focusRequester(first)) { clear(q); q.onRecord(); onLeavePlayer() }
            MenuItem(stringResource(R.string.vod_cancel), Modifier) { clear(q) }
        }
        is Prompt.Notice -> {
            LaunchedEffect(q) { if (q.leavePlayer) onLeavePlayer(); delay(4_000); clear(q) }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Text(if (q.res != 0) stringResource(q.res, *q.args.toTypedArray()) else q.text, fontSize = 14.sp, color = Color.White, modifier = Modifier.padding(bottom = 40.dp)
                    .background(Color(0xCC1A1C24), RoundedCornerShape(20.dp)).padding(horizontal = 20.dp, vertical = 9.dp))
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun Body(text: String) = Text(text, fontSize = 15.sp, color = Color.White, modifier = Modifier.padding(bottom = 10.dp))
