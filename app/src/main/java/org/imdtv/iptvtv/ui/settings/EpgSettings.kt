package org.imdtv.iptvtv.ui.settings

import org.imdtv.iptvtv.data.settings.AppSettings
import org.imdtv.iptvtv.data.settings.DEFAULT_GUIDE_ORDER
import org.imdtv.iptvtv.ui.theme.LocalTheme

import android.net.Uri
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import org.imdtv.iptvtv.data.epg.EpgImporter
import org.imdtv.iptvtv.data.db.ORIGIN_EPGSHARE
import org.imdtv.iptvtv.data.settings.GuideKind
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import org.imdtv.iptvtv.data.db.EpgSourceEntity
import org.imdtv.iptvtv.data.epg.OFFSET_STEPS
import org.imdtv.iptvtv.data.epg.formatOffset
import org.imdtv.iptvtv.data.settings.EPG_INTERVALS

/** A row whose value Left/Right step through [values] (OK steps forward and wraps). */
@Composable
fun <T> StepperRow(title: String, explanation: String, values: List<T>, current: T, label: (T) -> String, modifier: Modifier = Modifier,
    onChange: (T) -> Unit) {
    val i = values.indexOf(current).coerceAtLeast(0)
    SettingRow(title, explanation, "‹ ${label(values[i])} ›", modifier.onPreviewKeyEvent { e ->
        val step = when (e.key) { Key.DirectionLeft -> -1; Key.DirectionRight -> 1; else -> return@onPreviewKeyEvent false }
        if (e.type == KeyEventType.KeyDown) values.getOrNull(i + step)?.let(onChange)
        true
    }) { } // OK does nothing: a stray press mustn't change a setting (user, 2026-11-12)
}

/** A source's time offset, −12:00 to +12:00 in 15-minute steps; its last status is the explanation. */
@Composable
fun OffsetRow(vm: SettingsViewModel, s: EpgSourceEntity, modifier: Modifier = Modifier) =
    StepperRow("Time offset · ${sourceLabel(s)}", s.lastStatus ?: "Not updated yet", OFFSET_STEPS, s.offsetMinutes, ::formatOffset, modifier) {
        vm.setOffset(s.id, it)
    }

fun sourceLabel(s: EpgSourceEntity) = s.name ?: android.net.Uri.parse(s.url).host ?: s.url

/** Errors first, so a failing source isn't hidden behind a good one. */
fun guideStatus(sources: List<EpgSourceEntity>): String? =
    sources.mapNotNull { it.lastStatus }.let { all -> all.firstOrNull { !it.startsWith("OK") } ?: all.firstOrNull() }

/** The TV guide (EPG) group (spec §5.3). */
@Composable
fun EpgGroupRows(vm: SettingsViewModel, first: Modifier, sourcesOpener: Modifier, onOpenSources: () -> Unit) {
    val s by vm.settings.collectAsState()
    val sources by vm.activeSources.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }
    val updating by vm.guideUpdating.collectAsState()
    SettingRow("Update now", if (updating) "Updating guide… This can take a few minutes" else guideStatus(sources) ?: "Download the TV guide now",
        null, first, busy = updating) { vm.refreshGuide() }
    SettingRow("EPG sources", "Add, rename, time-shift or remove guide sources", "${sources.size} ›", sourcesOpener) { onOpenSources() }
    PriorityRows("guides", s.guideOrder, GuideKind.entries, 3, { it.label }, defaults = DEFAULT_GUIDE_ORDER) { vm.setGuideOrder(it) }
    SwitchRow("Borrow guide from similar channels", "A channel with no guide uses one with the same name, e.g. Sport HEVC uses Sport",
        s.borrowSiblingGuide) { on -> vm.setBorrowSiblingGuide(on) }
    SettingRow(
        "Clear guide data",
        if (confirmClear) "Press OK again — the guide stays empty until the next update" else "Remove downloaded programmes and automatic channel links",
        null,
    ) { if (confirmClear) { vm.clearGuideData(); confirmClear = false } else confirmClear = true }
    ChoiceRow("Update every", "How often the guide downloads in the background", EPG_INTERVALS, s.epgUpdateIntervalHours, { "$it hours" }, default = AppSettings().epgUpdateIntervalHours) { h ->
        vm.update { it.copy(epgUpdateIntervalHours = h) }
    }
    SwitchRow("Update on app start", "Download the guide each time the app opens", s.epgUpdateOnStart) { on ->
        vm.update { it.copy(epgUpdateOnStart = on) }
    }
    SwitchRow("Update when the playlist changes", "Download the guide after a playlist update", s.epgUpdateOnPlaylistChange) { on ->
        vm.update { it.copy(epgUpdateOnPlaylistChange = on) }
    }
    StepperRow("Days to keep", "Days of upcoming programmes stored at each update", (1..14).toList(), s.epgDaysToKeep, { "$it" }) { n ->
        vm.update { it.copy(epgDaysToKeep = n) }
    }
    StepperRow("Past days to keep", "Days of past programmes stored at each update", (0..7).toList(), s.epgPastDays, { "$it" }) { n ->
        vm.update { it.copy(epgPastDays = n) }
    }
}

/** EPG sources: one row per playlist, each opening its sources (add, rename, time offset, remove, open-source on/off). */
@Composable
fun EpgSourcesPage(vm: SettingsViewModel, modifier: Modifier, first: FocusRequester, park: () -> Unit, onClose: () -> Unit) {
    val providers by vm.providers.collectAsState()
    val settings by vm.settings.collectAsState()
    var open by remember { mutableStateOf<Long?>(null) }
    val rows = remember { mutableMapOf<Long, FocusRequester>() }
    var refocus by remember { mutableStateOf<Long?>(null) }
    val single = providers.singleOrNull()?.id // one playlist: straight to its sources
    val shown = providers.find { it.id == (open ?: single) }
    // Swapping the rows removes the focused one, which would drop focus to the Settings root: park it first.
    BackHandler { if (open != null && single == null) { park(); refocus = open; open = null } else onClose() }
    LaunchedEffect(open) { withFrameNanos { }; runCatching { (refocus?.let(rows::get) ?: first).requestFocus() }; refocus = null }
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(if (shown != null && single == null) "EPG sources · ${shown.name}" else "EPG sources", fontSize = 20.sp, color = Color.White,
            modifier = Modifier.padding(top = 6.dp, bottom = 18.dp))
        if (shown == null) providers.forEachIndexed { i, p ->
            key(p.id) {
                val sources by remember(p.id) { vm.sources(p.id) }.collectAsState(emptyList())
                val r = rows.getOrPut(p.id) { FocusRequester() }
                SettingRow(p.name, "Add, rename, time-shift or remove this playlist's guide sources", "${sources.size} ›",
                    (if (i == 0) Modifier.focusRequester(first) else Modifier).focusRequester(r)) { park(); open = p.id }
            }
        } else key(shown.id) {
            val p = shown
            val sources by remember(p.id) { vm.sources(p.id) }.collectAsState(emptyList())
            EditRow("Add source", "Paste or type an XMLTV guide URL", null, "", Modifier.focusRequester(first), KeyboardType.Uri) { url ->
                if (Uri.parse(url).host != null) vm.addSource(p.id, url)
            }
            sources.forEach { s ->
                key(s.id) {
                    var confirmRemove by remember { mutableStateOf(false) }
                    OffsetRow(vm, s, Modifier.padding(top = 12.dp))
                    // Open-source (epgshare01) sources follow the shown countries; the app adds and removes them, the user can turn one off.
                    if (s.origin == ORIGIN_EPGSHARE) {
                        SwitchRow("Use ${sourceLabel(s)}", "Off keeps it in the list but never downloads or uses it", s.url !in settings.guidesOff) { on -> vm.setGuideOn(s.url, on) }
                    } else {
                        EditRow("Rename", "Shown as “${sourceLabel(s)}”; empty uses the address", null, s.name.orEmpty()) { vm.renameSource(s.id, it) }
                        SettingRow("Remove source", if (confirmRemove) "Press OK again to remove it and its guide" else EpgImporter.logUrl(s.url), null) {
                            if (confirmRemove) { runCatching { first.requestFocus() }; vm.removeSource(p.id, s.id) } else confirmRemove = true
                        }
                    }
                }
            }
        }
    }
}
