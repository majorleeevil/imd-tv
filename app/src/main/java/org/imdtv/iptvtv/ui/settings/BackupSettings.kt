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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import org.imdtv.iptvtv.data.backup.Backup
import org.imdtv.iptvtv.data.settings.themeIdOf
import org.imdtv.iptvtv.ui.theme.LocalTheme

/**
 * Back up now; Restore → backups found → summary → a login per Xtream playlist (Skip leaves it out) → progress → result.
 * "Back up now" and "Restore" stay put while the list/form below them changes, so focus always has a row to return to.
 */
@Composable
fun BackupRows(vm: SettingsViewModel, first: Modifier) {
    val status by vm.backupStatus.collectAsState()
    val files by vm.backupFiles.collectAsState()
    val pending by vm.pendingRestore.collectAsState()
    val running by vm.restoreRunning.collectAsState()
    var listing by remember { mutableStateOf(false) }
    var browsing by remember { mutableStateOf<BrowseMode?>(null) }
    val settings by vm.settings.collectAsState()
    val folderRow = remember { FocusRequester() }
    // Rows below come and go; focus parks on "Backup folder" (always there) first, or it falls to the group list.
    fun park() = runCatching { folderRow.requestFocus() }
    val restoreRow = remember { FocusRequester() }
    fun backToRestore() = runCatching { restoreRow.requestFocus() }
    var refocusRestore by remember { mutableStateOf(false) }
    LaunchedEffect(refocusRestore) { if (refocusRestore) { withFrameNanos { }; backToRestore(); refocusRestore = false } }

    status?.let { Text(it, fontSize = 15.sp, color = Color.White, modifier = Modifier.padding(bottom = 8.dp)) }
    SettingRow(stringResource(R.string.set_b_backup_now), stringResource(R.string.set_b_backup_now_expl), null, first) { vm.backUpNow() }
    SettingRow(stringResource(R.string.set_b_backup_folder), stringResource(R.string.set_b_backup_folder_expl), settings.backupFolder?.substringAfterLast('/') ?: stringResource(R.string.set_b_downloads),
        Modifier.focusRequester(folderRow)) { listing = false; browsing = BrowseMode.SAVE }
    browsing?.let { mode ->
        FolderBrowser(mode, park = ::park, onClose = { park(); browsing = null; if (mode == BrowseMode.RESTORE) refocusRestore = true },
            onFolder = { vm.setBackupFolder(it) }, onFile = { vm.openBackup(it); listing = false })
        return
    }
    SettingRow(stringResource(R.string.set_b_restore), stringResource(R.string.set_b_restore_expl), if (listing || pending != null) "▾" else "›",
        Modifier.focusRequester(restoreRow), busy = running) {
        if (running) return@SettingRow
        vm.cancelRestore(); listing = !listing; if (listing) vm.refreshBackups()
    }
    val b = pending
    when {
        running -> Unit
        b != null -> RestoreForm(b, vm, park = { backToRestore() }) { backToRestore(); vm.cancelRestore() }
        listing -> {
            files.forEach { f -> SettingRow(f.name, f.volume, null) { backToRestore(); listing = false; vm.openBackup(f) } }
            if (files.isEmpty()) Text(stringResource(R.string.set_b_no_backups), fontSize = 14.sp, color = LocalTheme.current.colors.muted)
            SettingRow(stringResource(R.string.set_b_browse_backup), stringResource(R.string.set_b_browse_backup_expl), "›") { park(); browsing = BrowseMode.RESTORE }
        }
    }
}

/** Settings › Backup & sync › Backup & restore. */
@Composable
fun BackupPage(vm: SettingsViewModel, modifier: Modifier, first: FocusRequester, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { first.requestFocus() } }
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.set_b_backup_restore_title), fontSize = 20.sp, color = Color.White, modifier = Modifier.padding(top = 6.dp, bottom = 18.dp))
        BackupRows(vm, Modifier.focusRequester(first))
    }
}

@Composable
private fun RestoreForm(b: Backup, vm: SettingsViewModel, park: () -> Unit, onCancel: () -> Unit) {
    BackHandler(onBack = onCancel)
    val asks = remember(b) { b.playlists.indices.filter { needsLogin(b.playlists[it]) && !vm.hasSavedLogin(b.playlists[it]) } }
    var step by remember(b) { mutableStateOf(0) }
    val logins = remember(b) { mutableStateMapOf<Int, Pair<String, String>>() }
    val firstRow = remember { FocusRequester() }
    LaunchedEffect(step) { withFrameNanos { }; runCatching { firstRow.requestFocus() } }

    val names = b.playlists.joinToString { it.name }.ifEmpty { stringResource(R.string.set_b_none) }
    Text(stringResource(R.string.set_b_this_backup, b.playlists.size, names, stringResource(themeIdOf(b.settings.theme).labelRes)),
        fontSize = 14.sp, color = LocalTheme.current.colors.muted, modifier = Modifier.padding(top = 8.dp))
    if (step < asks.size) {
        val i = asks[step]
        val p = b.playlists[i]
        var user by remember(step) { mutableStateOf("") }
        var pass by remember(step) { mutableStateOf("") }
        // Stalker: the MAC in the username slot, the optional serial in the password slot (as the playlist form saves them).
        val portal = p.type == org.imdtv.iptvtv.data.db.ProviderType.STALKER.name
        val ready = if (portal) org.imdtv.iptvtv.data.source.isValidMac(user) else user.isNotBlank() && pass.isNotBlank()
        Text(stringResource(R.string.set_b_login_for, p.name), fontSize = 16.sp, color = Color.White, modifier = Modifier.padding(top = 8.dp))
        // The password keyboard has the number row many providers' logins need (as the playlist form).
        EditRow(if (portal) stringResource(R.string.set_b_mac) else stringResource(R.string.set_b_username), if (portal) stringResource(R.string.set_b_mac_example) else stringResource(R.string.set_b_as_given),
            user.ifEmpty { stringResource(R.string.set_b_not_set) }, user, Modifier.focusRequester(firstRow),
            keyboard = androidx.compose.ui.text.input.KeyboardType.Password) { user = it }
        EditRow(if (portal) stringResource(R.string.set_b_serial_optional) else stringResource(R.string.set_b_password), stringResource(R.string.set_b_as_given), if (pass.isEmpty()) stringResource(R.string.set_b_not_set) else "••••••", pass,
            keyboard = androidx.compose.ui.text.input.KeyboardType.Password, secret = true) { pass = it }
        SettingRow(if (step == asks.size - 1) stringResource(R.string.set_b_next_review) else stringResource(R.string.set_b_next_playlist),
            if (!ready) (if (portal) stringResource(R.string.set_b_enter_mac) else stringResource(R.string.set_b_enter_user_pass)) else stringResource(R.string.set_b_use_login, p.name), null) {
            // The last step swaps these rows out: park focus first, or it falls to the group list (and switches group).
            if (ready) { logins[i] = user.trim() to pass; if (step == asks.size - 1) park(); step++ }
        }
        SettingRow(stringResource(R.string.set_b_skip), stringResource(R.string.set_b_leave_out, p.name), null) { logins.remove(i); if (step == asks.size - 1) park(); step++ }
    } else {
        val left = asks.filter { it !in logins }.map { b.playlists[it].name }
        SettingRow(stringResource(R.string.set_b_restore_now), if (left.isEmpty()) stringResource(R.string.set_b_restore_now_expl)
            else stringResource(R.string.set_b_leaving_out, left.joinToString()), null, Modifier.focusRequester(firstRow)) { park(); vm.restore(b, logins.toMap()) }
        SettingRow(stringResource(R.string.set_b_cancel), stringResource(R.string.set_b_change_nothing), null) { onCancel() }
    }
}

enum class BrowseMode { SAVE, RESTORE }

/**
 * In-app folder browser (Google TV has no system folder picker): drives first, then folders. SAVE picks a folder for
 * backups; RESTORE picks a .json file. Needs all files access; without it, says so and offers the grant screen.
 */
@Composable
private fun FolderBrowser(mode: BrowseMode, park: () -> Unit, onClose: () -> Unit, onFolder: (String?) -> Unit, onFile: (org.imdtv.iptvtv.data.files.SharedFile) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var access by remember { mutableStateOf(org.imdtv.iptvtv.data.files.hasAllFilesAccess()) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) { access = org.imdtv.iptvtv.data.files.hasAllFilesAccess(); onPauseOrDispose { } }
    var dir by remember { mutableStateOf<java.io.File?>(null) } // null = the list of drives
    val roots = remember { org.imdtv.iptvtv.data.files.storageRoots(context) }
    val top = remember { FocusRequester() }
    LaunchedEffect(dir, access) { withFrameNanos { }; runCatching { top.requestFocus() } }
    // Every row is replaced on a folder change: park focus first (see BackupRows), then the new top row takes it.
    fun go(to: java.io.File?) { park(); dir = to }
    fun up() { val d = dir ?: return onClose(); go(if (roots.any { it.first == d }) null else d.parentFile) }
    BackHandler { up() }

    Text(if (mode == BrowseMode.SAVE) stringResource(R.string.set_b_choose_folder) else stringResource(R.string.set_b_choose_file), fontSize = 15.sp, color = Color.White,
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
    if (!access) {
        val grant = remember { org.imdtv.iptvtv.data.files.allFilesAccessIntent(context) }
        if (grant != null) SettingRow(stringResource(R.string.set_b_allow_files), stringResource(R.string.set_b_allow_files_expl), "›",
            Modifier.focusRequester(top)) { context.startActivity(grant) }
        else Text(stringResource(R.string.set_b_no_all_files), fontSize = 14.sp,
            color = LocalTheme.current.colors.muted)
        SettingRow(stringResource(R.string.set_b_cancel), "", null, if (grant == null) Modifier.focusRequester(top) else Modifier) { onClose() }
        return
    }
    val d = dir
    if (d == null) {
        roots.forEachIndexed { i, (root, label) ->
            SettingRow(label, root.path, "›", if (i == 0) Modifier.focusRequester(top) else Modifier) { go(root) }
        }
        if (mode == BrowseMode.SAVE) SettingRow(stringResource(R.string.set_b_use_downloads), stringResource(R.string.set_b_use_downloads_expl), null) { onFolder(null); onClose() }
        SettingRow(stringResource(R.string.set_b_cancel), "", null) { onClose() }
        return
    }
    val folders = remember(d) { d.listFiles().orEmpty().filter { it.isDirectory && !it.isHidden }.sortedBy { it.name.lowercase() } }
    val files = remember(d, mode) { if (mode == BrowseMode.RESTORE) org.imdtv.iptvtv.data.backup.backupFilesIn(d).sortedByDescending { it.modified } else emptyList() }
    SettingRow(stringResource(R.string.set_b_up), d.path, null, Modifier.focusRequester(top)) { up() }
    if (mode == BrowseMode.SAVE) SettingRow(stringResource(R.string.set_b_use_folder), d.path, null) { onFolder(d.path); onClose() }
    files.forEach { f -> SettingRow(f.name, stringResource(R.string.set_b_restore_from_file), null) { onFile(f); onClose() } }
    folders.forEach { f -> SettingRow("📁 " + f.name, stringResource(R.string.set_b_open_folder), "›") { go(f) } }
    if (folders.isEmpty() && files.isEmpty()) Text(if (mode == BrowseMode.SAVE) stringResource(R.string.set_b_no_folders) else stringResource(R.string.set_b_no_folders_files),
        fontSize = 14.sp, color = LocalTheme.current.colors.muted)
}
