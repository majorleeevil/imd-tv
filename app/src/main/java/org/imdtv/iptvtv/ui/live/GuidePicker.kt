package org.imdtv.iptvtv.ui.live

import androidx.compose.ui.res.stringResource
import org.imdtv.iptvtv.R

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import org.imdtv.iptvtv.data.catalogue.displayName
import org.imdtv.iptvtv.data.db.ChannelEntity
import org.imdtv.iptvtv.ui.MenuFrame
import org.imdtv.iptvtv.ui.MenuItem
import org.imdtv.iptvtv.ui.settings.EditRow

/** Use guide from… (spec §5.4): same-name channels first (playlist, then Your EPG sources), a search row, Use automatic guide when the link is manual, Remove guide (#54) when it has one. */
@Composable
fun GuidePicker(vm: LiveViewModel, ch: ChannelEntity, strip: List<String>, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var donors by remember { mutableStateOf<List<GuideChoice>?>(null) }
    var link by remember { mutableStateOf<org.imdtv.iptvtv.data.db.EpgLinkEntity?>(null) }
    LaunchedEffect(ch.id) { link = vm.guideLink(ch) }
    val manual = link?.manual == true
    LaunchedEffect(ch.id, query) { donors = vm.donors(ch, query) }
    androidx.compose.runtime.DisposableEffect(ch.id) { onDispose { vm.clearDonors() } }
    MenuFrame(stringResource(R.string.live_picker_title, ch.displayName(strip)), onDismiss) { first ->
        if (manual) MenuItem(stringResource(R.string.live_picker_automatic), Modifier.focusRequester(first)) { vm.useAutomaticGuide(ch); onDismiss() }
        if (link?.isNoGuide == false) MenuItem(stringResource(R.string.live_picker_remove_guide), Modifier) { vm.removeGuide(ch); onDismiss() }
        // A row that opens the box on OK, so scrolling past it never opens the keyboard (user, 2026-11-10).
        EditRow(stringResource(R.string.live_picker_search), if (query.isBlank()) stringResource(R.string.live_picker_search_hint) else "“$query”", null, query,
            if (manual) Modifier else Modifier.focusRequester(first)) { query = it.trim() }
        when (val d = donors) {
            null -> Note(stringResource(R.string.live_looking))
            emptyList<GuideChoice>() -> Note(if (query.isBlank()) stringResource(R.string.live_picker_none_same) else stringResource(R.string.live_picker_none_match))
            else -> d.forEach { choice ->
                when (choice) {
                    is GuideChoice.Channel -> MenuItem(choice.channel.displayName(strip) + (choice.category?.let { " · $it" }.orEmpty()), Modifier) {
                        vm.useGuideFrom(ch, choice.channel); onDismiss()
                    }
                    is GuideChoice.Source -> MenuItem("${choice.channel.name} · ${choice.channel.source}", Modifier) {
                        vm.useGuideFrom(ch, choice.channel); onDismiss()
                    }
                }
            }
        }
    }
}

@Composable
private fun Note(text: String) = Text(text, fontSize = 14.sp, color = Color.White, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
