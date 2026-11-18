package org.imdtv.iptvtv.ui.settings

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

private const val SYNC_LIMITS = "• Both boxes must be on, with IMD TV open or used recently.\n" +
    "• A follower gets the master's changes the next time it opens IMD TV (or within 6 hours) while the master can be reached.\n" +
    "• Changes made on a follower are replaced at its next sync.\n" +
    "• Logins are sent only to boxes you pair, on this network."

private const val MASTER_STEPS = "This box is now the master. To add a follower, go to the other box:\n" +
    "1. On the other box, open IMD TV › Settings › Backup & sync › Sync.\n" +
    "2. Set Role to Follower, then choose Turn on and OK.\n" +
    "3. Still on the other box, choose Master, then pick this box from the list.\n" +
    "4. A six-digit code appears on this screen. Type it on the other box and choose Pair.\n" +
    "Repeat for as many boxes as you like; they're listed here under Followers."

private const val FOLLOWER_STEPS = "This box is now a follower. To pair it:\n" +
    "1. On the box to copy, open IMD TV › Settings › Backup & sync › Sync and set Role to Master.\n" +
    "2. Back on this box, choose Master, then pick that box from the list.\n" +
    "3. A six-digit code appears on that box's screen. Type it here and choose Pair.\n" +
    "This box then copies the master each time IMD TV opens and every 6 hours."

private fun whenText(t: Long) = "${dayDate(t)} ${clockTime(t)}"

fun syncRoleLabel(r: SyncRole) = when (r) { SyncRole.OFF -> "Off"; SyncRole.MASTER -> "Master"; SyncRole.FOLLOWER -> "Follower" }

/** Master: the pairing code on screen, over every screen, until it is used, cancelled or expires. */
@Composable
fun PairingPrompt(sync: SyncManager) {
    val p by sync.pairing.collectAsState()
    val pairing = p ?: return
    LaunchedEffect(pairing) { delay(PAIR_MS); if (sync.pairing.value === pairing) sync.pairing.value = null }
    MenuFrame("Allow ${pairing.followerName}?", onDismiss = { sync.pairing.value = null }) { f ->
        Text("Type this code on ${pairing.followerName} to let it copy this box's playlists, logins and settings:", fontSize = 14.sp,
            color = Color.White, modifier = Modifier.padding(bottom = 10.dp))
        Text(pairing.code.toList().joinToString(" "), fontSize = 40.sp, color = Color.White, modifier = Modifier.padding(bottom = 14.dp))
        MenuItem("Cancel", Modifier.focusRequester(f)) { sync.pairing.value = null }
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
        Text("Sync", fontSize = 20.sp, color = Color.White, modifier = Modifier.padding(top = 6.dp, bottom = 18.dp))
        ChoiceRow("Role", when (state.role) {
                SyncRole.OFF -> "One box (the master) keeps the others' playlists and settings the same"
                SyncRole.MASTER -> "Other boxes copy this one"
                SyncRole.FOLLOWER -> "This box copies the master"
            }, SyncRole.entries, state.role, ::syncRoleLabel,
            Modifier.focusRequester(first)) { r ->
            if (r == SyncRole.OFF) { listing = false; chosen = null; scope.launch { sync.setRole(r) } } else limits = r
        }
        if (state.role == SyncRole.MASTER) {
            Text("Followers", fontSize = 16.sp, color = Color.White, modifier = Modifier.padding(top = 8.dp))
            state.followers.forEach { f ->
                SettingRow(f.name, f.lastSynced?.let { "Last synced ${whenText(it)}" } ?: "Not synced yet", "Remove") { removing = f }
            }
            if (state.followers.isEmpty()) Text("No followers yet. On the other box, set Role to Follower, then choose Master and pick this box", fontSize = 14.sp, color = muted)
        }
        if (state.role == SyncRole.FOLLOWER) {
            SettingRow("Master", problem ?: m?.lastSynced?.let { "Last synced ${whenText(it)}" } ?: if (m != null) "Not synced yet" else "Not paired",
                (m?.name ?: "Choose a box") + (if (listing) " ▾" else " ›"), Modifier.focusRequester(masterRow)) {
                if (listing) { chosen = null }
                listing = !listing
            }
            if (listing) {
                val found by remember(listing) { if (listing) sync.browse() else flowOf(emptyList()) }.collectAsState(initial = emptyList())
                found.forEach { b ->
                    SettingRow(b.name, "Master on this network", null) {
                        scope.launch {
                            if (sync.hello(b)) chosen = b else { chosen = null; vm.notice("Couldn't reach ${b.name}") }
                        }
                    }
                }
                if (found.isEmpty()) Text("Looking for masters… (the other box must be set to Master with IMD TV open)", fontSize = 14.sp, color = muted)
                chosen?.let { b -> key(b) {
                    EditRow("Code shown on ${b.name}", "Six digits, on that box's screen", "Not set", "", keyboard = KeyboardType.Number, saveLabel = "Pair") { code ->
                        scope.launch {
                            if (sync.pair(b, code.trim())) { parkMaster(); listing = false; chosen = null; vm.notice("Paired with ${b.name}") }
                            else vm.notice("Wrong or expired code: choose the box again")
                        }
                    }
                } }
            }
            if (m != null) {
                SettingRow("Sync now", "Copy the master's playlists and settings now", null, busy = pulling) { if (!pulling) scope.launch { sync.pull(force = true) } }
                SettingRow("Forget master", "Stop following ${m.name}", null) { parkMaster(); scope.launch { sync.forgetMaster() } }
            }
        }
    }

    limits?.let { r ->
        MenuFrame("Before you turn on sync", onDismiss = { limits = null }) { f ->
            ScrollText(SYNC_LIMITS, Modifier.focusRequester(f).padding(bottom = 10.dp))
            MenuItem("Turn on", Modifier) { limits = null; setup = r; parkFirst(); listing = false; chosen = null; scope.launch { sync.setRole(r) } }
            MenuItem("Cancel", Modifier) { limits = null }
        }
    }
    setup?.let { r ->
        MenuFrame(if (r == SyncRole.MASTER) "Set up a follower" else "Pair with the master", onDismiss = { setup = null }) { f ->
            ScrollText(if (r == SyncRole.MASTER) MASTER_STEPS else FOLLOWER_STEPS, Modifier.focusRequester(f).padding(bottom = 10.dp))
            MenuItem("OK", Modifier) { setup = null }
        }
    }
    removing?.let { f ->
        MenuFrame("Remove ${f.name}?", onDismiss = { removing = null }) { fr ->
            MenuItem("Remove", Modifier.focusRequester(fr)) { removing = null; parkFirst(); scope.launch { sync.removeFollower(f.id) } }
            MenuItem("Cancel", Modifier) { removing = null }
        }
    }
}
