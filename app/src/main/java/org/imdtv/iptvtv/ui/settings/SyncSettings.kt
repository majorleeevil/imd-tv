package org.imdtv.iptvtv.ui.settings

import org.imdtv.iptvtv.R
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.imdtv.iptvtv.data.sync.FollowerBox
import org.imdtv.iptvtv.data.sync.FoundBox
import org.imdtv.iptvtv.data.sync.PAIR_MS
import org.imdtv.iptvtv.data.sync.SyncManager
import org.imdtv.iptvtv.data.sync.SyncRole
import org.imdtv.iptvtv.data.update.ScrollText
import org.imdtv.iptvtv.ui.MenuFrame
import org.imdtv.iptvtv.ui.MenuItem
import org.imdtv.iptvtv.ui.clockTime
import org.imdtv.iptvtv.ui.dayDate
import org.imdtv.iptvtv.ui.theme.LocalTheme

private fun whenText(t: Long) = "${dayDate(t)} ${clockTime(t)}"

@Composable
fun syncRoleLabel(r: SyncRole) = stringResource(when (r) { SyncRole.OFF -> R.string.set_b_role_off; SyncRole.MASTER -> R.string.set_b_role_master; SyncRole.FOLLOWER -> R.string.set_b_role_follower })

/** Master: the pairing code on screen, over every screen, until it is used, cancelled or expires. */
@Composable
fun PairingPrompt(sync: SyncManager) {
    val p by sync.pairing.collectAsState()
    val pairing = p ?: return
    LaunchedEffect(pairing) { delay(PAIR_MS); if (sync.pairing.value === pairing) sync.pairing.value = null }
    MenuFrame(stringResource(R.string.set_b_allow_follower, pairing.followerName), onDismiss = { sync.pairing.value = null }) { f ->
        Text(stringResource(R.string.set_b_pair_code_prompt, pairing.followerName), fontSize = 14.sp,
            color = Color.White, modifier = Modifier.padding(bottom = 10.dp))
        Text(pairing.code.toList().joinToString(" "), fontSize = 40.sp, color = Color.White, modifier = Modifier.padding(bottom = 14.dp))
        MenuItem(stringResource(R.string.set_b_cancel), Modifier.focusRequester(f)) { sync.pairing.value = null }
    }
}

/** Settings › Backup & sync › Sync. */
@Composable
fun SyncPage(vm: SettingsViewModel, modifier: Modifier, first: FocusRequester, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val sync = vm.sync
    val state by sync.state.collectAsState()
    val problem by sync.problem.collectAsState()
    val pulling by sync.pulling.collectAsState()
    val scope = rememberCoroutineScope()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val muted = LocalTheme.current.colors.muted
    var limits by remember { mutableStateOf<SyncRole?>(null) }
    var setup by remember { mutableStateOf<SyncRole?>(null) }
    var removing by remember { mutableStateOf<FollowerBox?>(null) }
    var listing by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf<FoundBox?>(null) }
    val masterRow = remember { FocusRequester() }
    // Rows below come and go; focus parks on a row that is always there first, or it falls to the group list.
    fun parkFirst() = runCatching { first.requestFocus() }
    fun parkMaster() = runCatching { masterRow.requestFocus() }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { first.requestFocus() } }
    val m = state.master

    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.set_b_sync_title), fontSize = 20.sp, color = Color.White, modifier = Modifier.padding(top = 6.dp, bottom = 18.dp))
        ChoiceRow(stringResource(R.string.set_b_role), stringResource(when (state.role) {
                SyncRole.OFF -> R.string.set_b_role_off_expl
                SyncRole.MASTER -> R.string.set_b_role_master_expl
                SyncRole.FOLLOWER -> R.string.set_b_role_follower_expl
            }), SyncRole.entries, state.role, { syncRoleLabel(it) },
            Modifier.focusRequester(first)) { r ->
            if (r == SyncRole.OFF) { listing = false; chosen = null; scope.launch { sync.setRole(r) } } else limits = r
        }
        if (state.role == SyncRole.MASTER) {
            Text(stringResource(R.string.set_b_followers), fontSize = 16.sp, color = Color.White, modifier = Modifier.padding(top = 8.dp))
            state.followers.forEach { f ->
                SettingRow(f.name, f.lastSynced?.let { stringResource(R.string.set_b_last_synced, whenText(it)) } ?: stringResource(R.string.set_b_not_synced), stringResource(R.string.set_b_remove)) { removing = f }
            }
            if (state.followers.isEmpty()) Text(stringResource(R.string.set_b_no_followers), fontSize = 14.sp, color = muted)
        }
        if (state.role == SyncRole.FOLLOWER) {
            SettingRow(stringResource(R.string.set_b_master), problem ?: m?.lastSynced?.let { stringResource(R.string.set_b_last_synced, whenText(it)) } ?: if (m != null) stringResource(R.string.set_b_not_synced) else stringResource(R.string.set_b_not_paired),
                (m?.name ?: stringResource(R.string.set_b_choose_box)) + (if (listing) " ▾" else " ›"), Modifier.focusRequester(masterRow)) {
                if (listing) { chosen = null }
                listing = !listing
            }
            if (listing) {
                val found by remember(listing) { if (listing) sync.browse() else flowOf(emptyList()) }.collectAsState(initial = emptyList())
                found.forEach { b ->
                    SettingRow(b.name, stringResource(R.string.set_b_master_on_network), null) {
                        scope.launch {
                            if (sync.hello(b)) chosen = b else { chosen = null; vm.notice(ctx.getString(R.string.set_b_cant_reach, b.name)) }
                        }
                    }
                }
                if (found.isEmpty()) Text(stringResource(R.string.set_b_looking), fontSize = 14.sp, color = muted)
                chosen?.let { b -> key(b) {
                    EditRow(stringResource(R.string.set_b_code_shown, b.name), stringResource(R.string.set_b_code_expl), stringResource(R.string.set_b_not_set), "", keyboard = KeyboardType.Number, saveLabel = stringResource(R.string.set_b_pair)) { code ->
                        scope.launch {
                            if (sync.pair(b, code.trim())) { parkMaster(); listing = false; chosen = null; vm.notice(ctx.getString(R.string.set_b_paired, b.name)) }
                            else vm.notice(ctx.getString(R.string.set_b_wrong_code))
                        }
                    }
                } }
            }
            if (m != null) {
                SettingRow(stringResource(R.string.set_b_sync_now), stringResource(R.string.set_b_sync_now_expl), null, busy = pulling) { if (!pulling) scope.launch { sync.pull(force = true) } }
                SettingRow(stringResource(R.string.set_b_forget_master), stringResource(R.string.set_b_forget_master_expl, m.name), null) { parkMaster(); scope.launch { sync.forgetMaster() } }
            }
        }
    }

    limits?.let { r ->
        MenuFrame(stringResource(R.string.set_b_before_sync), onDismiss = { limits = null }) { f ->
            ScrollText(stringResource(R.string.set_b_sync_limits), Modifier.focusRequester(f).padding(bottom = 10.dp))
            MenuItem(stringResource(R.string.set_b_turn_on), Modifier) { limits = null; setup = r; parkFirst(); listing = false; chosen = null; scope.launch { sync.setRole(r) } }
            MenuItem(stringResource(R.string.set_b_cancel), Modifier) { limits = null }
        }
    }
    setup?.let { r ->
        MenuFrame(if (r == SyncRole.MASTER) stringResource(R.string.set_b_setup_follower) else stringResource(R.string.set_b_pair_master), onDismiss = { setup = null }) { f ->
            ScrollText(stringResource(if (r == SyncRole.MASTER) R.string.set_b_master_steps else R.string.set_b_follower_steps), Modifier.focusRequester(f).padding(bottom = 10.dp))
            MenuItem(stringResource(R.string.set_b_ok), Modifier) { setup = null }
        }
    }
    removing?.let { f ->
        MenuFrame(stringResource(R.string.set_b_remove_q, f.name), onDismiss = { removing = null }) { fr ->
            MenuItem(stringResource(R.string.set_b_remove), Modifier.focusRequester(fr)) { removing = null; parkFirst(); scope.launch { sync.removeFollower(f.id) } }
            MenuItem(stringResource(R.string.set_b_cancel), Modifier) { removing = null }
        }
    }
}
