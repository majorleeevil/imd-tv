package org.imdtv.iptvtv.recording

import androidx.compose.foundation.background
import android.content.Context
import org.imdtv.iptvtv.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import org.imdtv.iptvtv.IptvApp
import org.imdtv.iptvtv.ui.ContextMenu
import org.imdtv.iptvtv.ui.FocusBox
import org.imdtv.iptvtv.ui.clockTime
import org.imdtv.iptvtv.ui.dayDate
import org.imdtv.iptvtv.ui.duration
import org.imdtv.iptvtv.ui.onLongOk
import org.imdtv.iptvtv.ui.theme.LocalTheme
import org.imdtv.iptvtv.ui.theme.themeBackground

/** "Sat 15/11 6.00pm · 1h 05m" (length once known). */
fun recordingWhen(r: RecordingEntity): String {
    val start = r.actualStart ?: r.scheduledStart
    val length = r.actualEnd?.let { it - start } ?: (r.scheduledEnd - start).takeIf { r.status == RecStatus.SCHEDULED }
    return "${dayDate(start)} ${clockTime(start)}" + (length?.let { " · ${duration(it)}" } ?: "")
}

fun recordingStatus(context: Context, r: RecordingEntity): String = when (r.status) {
    RecStatus.SCHEDULED -> context.getString(R.string.vod_rec_status_scheduled)
    RecStatus.RECORDING -> context.getString(R.string.vod_rec_status_recording, clockTime(r.scheduledEnd))
    RecStatus.DONE -> ""
    RecStatus.STOPPED -> r.statusReason?.let { recordingReason(context, it) } ?: context.getString(R.string.vod_rec_status_stopped)
    RecStatus.FAILED -> r.statusReason?.let { recordingReason(context, it) } ?: context.getString(R.string.vod_rec_status_failed)
    RecStatus.SKIPPED -> context.getString(R.string.vod_rec_reason_skipped)
    RecStatus.MISSED -> context.getString(R.string.vod_rec_reason_missed)
}

// The reason is stored on the row in English (the tests and the service write it); it's shown translated.
private val FIXED_REASONS = mapOf(
    "Stopped — app was closed" to R.string.vod_rec_reason_app_closed,
    "Missed — the box was off" to R.string.vod_rec_reason_missed,
    "Skipped — you were watching" to R.string.vod_rec_reason_skipped,
    "Channel no longer in the playlist" to R.string.vod_rec_reason_channel_gone,
    "No free connection" to R.string.vod_rec_reason_no_connection,
    "Failed — the recording couldn't start" to R.string.vod_rec_reason_cant_start,
    "Failed — no USB drive found" to R.string.vod_rec_reason_no_usb,
    "Stopped — stream lost" to R.string.vod_rec_reason_stream_lost,
    "Failed — provider refused a second stream" to R.string.vod_rec_reason_second_stream,
    "Stopped — storage full" to R.string.vod_rec_reason_storage_full,
)
private val DRM_REASON = Regex("This channel uses DRM \\((.*)\\) and can't be recorded")
private val UNSUPPORTED_REASON = Regex("This channel can't be recorded \\((.*)\\)")
private val NO_START_REASON = Regex("Failed — the channel didn't start( \\(HTTP (\\d+)\\))?")
private val SAVE_REASON = Regex("Failed — couldn't save to (internal storage|the USB drive|the network share): (.*)")

/** A stored reason in the current language; one with no translation (an error message from the system) is shown as it is. */
fun recordingReason(context: Context, reason: String): String {
    FIXED_REASONS[reason]?.let { return context.getString(it) }
    DRM_REASON.matchEntire(reason)?.let { return context.getString(R.string.vod_rec_reason_drm, it.groupValues[1]) }
    UNSUPPORTED_REASON.matchEntire(reason)?.let { return context.getString(R.string.vod_rec_reason_unsupported, it.groupValues[1]) }
    NO_START_REASON.matchEntire(reason)?.let { m ->
        return if (m.groupValues[2].isEmpty()) context.getString(R.string.vod_rec_reason_no_start)
        else context.getString(R.string.vod_rec_reason_no_start_http, m.groupValues[2])
    }
    SAVE_REASON.matchEntire(reason)?.let { m ->
        val store = when (m.groupValues[1]) { "internal storage" -> R.string.vod_rec_store_internal; "the USB drive" -> R.string.vod_rec_store_usb; else -> R.string.vod_rec_store_smb }
        return context.getString(R.string.vod_rec_reason_cant_save, context.getString(store), m.groupValues[2])
    }
    return reason
}

/** The row last focused, kept across the screen being rebuilt (full-screen playback and back). */
private var lastRow: Long? = null

private fun RecordingEntity.playable() = location != null && (status == RecStatus.DONE || status == RecStatus.STOPPED)

/** What a recording's menu offers: [all] = every action (Gamestation's OK), else the hold-OK menu. */
@Composable
fun RecordingMenu(r: RecordingEntity, all: Boolean, onPlay: (RecordingEntity) -> Unit, onDismiss: () -> Unit) {
    val c = (LocalContext.current.applicationContext as IptvApp).container
    val scope = rememberCoroutineScope()
    var confirm by remember(r.id) { mutableStateOf(false) }
    if (confirm) {
        ContextMenu(stringResource(R.string.vod_rec_delete_q, r.title), listOf(
            stringResource(R.string.vod_rec_delete) to { scope.launch { c.recordings.delete(r.id, c::deleteRecordingFile) }; onDismiss() },
            stringResource(R.string.vod_cancel) to onDismiss,
        ), onDismiss)
        return
    }
    val items = buildList {
        if (all && r.playable()) add(stringResource(R.string.vod_rec_play) to { onDismiss(); onPlay(r) })
        when (r.status) {
            RecStatus.SCHEDULED -> add(stringResource(R.string.vod_rec_cancel_recording) to { scope.launch { c.recordings.cancel(r.id) }; onDismiss() })
            RecStatus.RECORDING -> add(stringResource(R.string.vod_rec_stop_recording) to { c.recordings.stop(r.id); onDismiss() })
            else -> add(stringResource(R.string.vod_rec_delete) to { confirm = true })
        }
    }
    ContextMenu(r.title, items, onDismiss)
}

/**
 * The Recordings tab: scheduled first, then finished newest first. OK plays (or offers Cancel / Stop), hold OK → Delete.
 * [nav] (Celestial Glass, Plus, Liquid Glass) draws the theme's background and menu above the list; without it the list
 * sits inside the theme's own frame (Retro, Xcube). [firstModifier] goes on the first row (Xcube's blade card).
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun RecordingsScreen(nav: (@Composable () -> Unit)?, onPlay: (RecordingEntity) -> Unit, firstModifier: Modifier = Modifier, textColor: Color = Color.White) {
    val c = (LocalContext.current.applicationContext as IptvApp).container
    val rows by remember { c.recordings.observe() }.collectAsState(null)
    val colors = LocalTheme.current.colors
    var menu by remember { mutableStateOf<Pair<RecordingEntity, Boolean>?>(null) }
    // Back from playing a recording lands on its row again (the screen is rebuilt meanwhile).
    val rowFocus = remember { mutableMapOf<Long, androidx.compose.ui.focus.FocusRequester>() }
    androidx.compose.runtime.LaunchedEffect(rows != null) {
        val id = lastRow ?: return@LaunchedEffect
        androidx.compose.runtime.withFrameNanos { }
        rowFocus[id]?.let { runCatching { it.requestFocus() } }
    }
    Box(if (nav != null) Modifier.fillMaxSize().themeBackground(colors.vodBg) else Modifier.fillMaxSize()) {
        Column(Modifier.padding(if (nav != null) androidx.compose.foundation.layout.PaddingValues(start = 50.dp, end = 50.dp, top = 22.dp) else androidx.compose.foundation.layout.PaddingValues(0.dp))) {
            nav?.invoke()
            val list = rows
            if (list != null && list.isEmpty()) {
                FocusBox(onClick = {}, modifier = firstModifier.padding(top = 32.dp)) { f ->
                    Text(stringResource(R.string.vod_rec_none_guide), fontSize = 16.sp,
                        color = if (f) colors.onFocus else textColor,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (f) colors.focus else Color.Transparent).padding(14.dp))
                }
            }
            // Entering the list (Down from the menu) lands on a row: the last one used, else the first.
            val enterRow = (lastRow?.takeIf { id -> list.orEmpty().any { it.id == id } } ?: list?.firstOrNull()?.id)?.let { rowFocus[it] }
            LazyColumn(Modifier.padding(top = if (nav != null) 24.dp else 8.dp)
                .focusProperties { enter = { enterRow ?: androidx.compose.ui.focus.FocusRequester.Default } }.focusGroup(),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(list.orEmpty(), key = { _, r -> r.id }) { i, r ->
                    FocusBox(
                        onClick = { if (r.playable()) onPlay(r) else menu = r to false },
                        modifier = (if (i == 0) firstModifier else Modifier)
                            .focusRequester(rowFocus.getOrPut(r.id) { androidx.compose.ui.focus.FocusRequester() })
                            .onLongOk { menu = r to false },
                        onFocused = { lastRow = r.id },
                    ) { f -> RecordingRow(r, f, textColor) }
                }
            }
        }
    }
    menu?.let { (r, all) -> RecordingMenu(r, all, onPlay) { menu = null } }
}

@Composable
private fun RecordingRow(r: RecordingEntity, f: Boolean, textColor: Color) {
    val colors = LocalTheme.current.colors
    val fg = if (f) colors.onFocus else textColor
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (f) colors.focus else colors.cell).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(64.dp, 40.dp), contentAlignment = Alignment.Center) {
            if (r.channelLogo != null) coil.compose.AsyncImage(r.channelLogo, null, Modifier.fillMaxSize())
            else Text(r.channelName.take(3), fontSize = 13.sp, color = fg, maxLines = 1)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(r.title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${r.channelName} · ${recordingWhen(r)}", fontSize = 12.sp, color = fg.copy(alpha = .75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val status = recordingStatus(LocalContext.current, r)
        if (status.isNotEmpty()) {
            Spacer(Modifier.width(12.dp))
            Text(status, fontSize = 13.sp, color = if (r.status == RecStatus.RECORDING) Color(0xFFE53935) else fg.copy(alpha = .85f), maxLines = 1)
        }
    }
}
