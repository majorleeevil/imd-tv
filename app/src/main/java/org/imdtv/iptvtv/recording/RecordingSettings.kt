package org.imdtv.iptvtv.recording

import org.imdtv.iptvtv.data.settings.AppSettings
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import org.imdtv.iptvtv.data.settings.END_LATE
import org.imdtv.iptvtv.data.settings.START_EARLY
import org.imdtv.iptvtv.ui.MenuFrame
import org.imdtv.iptvtv.ui.MenuItem
import org.imdtv.iptvtv.ui.settings.ChoiceRow
import org.imdtv.iptvtv.ui.settings.EditRow
import org.imdtv.iptvtv.ui.settings.SettingRow
import org.imdtv.iptvtv.ui.settings.SettingsViewModel
import org.imdtv.iptvtv.ui.settings.SwitchRow

/** Settings › Playback › Recording. */
@Composable
fun RecordingPage(vm: SettingsViewModel, modifier: Modifier, first: FocusRequester, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val s by vm.settings.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf(false) }
    var confirmOff by remember { mutableStateOf(false) }
    var test by remember { mutableStateOf<String?>(null) }
    var free by remember { mutableStateOf<Long?>(null) }
    val drives = remember(s.recordTo) { usbDirs(context) }
    val str = { id: Int -> context.getString(id) }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { first.requestFocus() } }
    LaunchedEffect(s.recordingOn, s.recordTo, s.recordUsbPath, s.smbServer, s.smbShare, s.smbFolder, s.smbUser, s.smbPassword) {
        free = null
        if (s.recordingOn && (s.recordTo != RecStorage.SMB || s.smbServer.isNotBlank() && s.smbShare.isNotBlank())) free = vm.recordingFreeBytes(s)
    }

    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.vod_rec_title), fontSize = 20.sp, color = Color.White, modifier = Modifier.padding(top = 6.dp, bottom = 18.dp))
        SwitchRow(stringResource(R.string.vod_rec_title), stringResource(R.string.vod_rec_switch_expl), s.recordingOn, Modifier.focusRequester(first)) { on ->
            if (on) info = true else confirmOff = true
        }
        if (!s.recordingOn) return@Column
        val usbLabel = stringResource(if (drives.isEmpty()) R.string.vod_rec_usb_none else R.string.vod_rec_usb)
        ChoiceRow(stringResource(R.string.vod_rec_save_to), stringResource(R.string.vod_rec_save_to_expl), RecStorage.entries, s.recordTo,
            { when (it) { RecStorage.INTERNAL -> stringResource(R.string.vod_rec_store_internal_title); RecStorage.USB -> usbLabel; RecStorage.SMB -> stringResource(R.string.vod_rec_store_smb_title) } }, default = AppSettings().recordTo) { to ->
            if (to == RecStorage.USB && drives.isEmpty()) vm.notice(str(R.string.vod_rec_plug_usb)) else vm.update { it.copy(recordTo = to) }
        }
        if (s.recordTo == RecStorage.USB && drives.size > 1) {
            val current = drives.firstOrNull { it.path == s.recordUsbPath } ?: drives.first()
            ChoiceRow(stringResource(R.string.vod_rec_usb), stringResource(R.string.vod_rec_usb_expl), drives, current, { driveName(context, it) }) { d -> vm.update { it.copy(recordUsbPath = d.path) } }
        }
        if (s.recordTo == RecStorage.SMB) {
            EditRow(stringResource(R.string.vod_rec_server), stringResource(R.string.vod_rec_server_expl), s.smbServer.ifEmpty { stringResource(R.string.vod_rec_not_set) }, s.smbServer, keyboard = KeyboardType.Uri) { v -> vm.update { it.copy(smbServer = v) } }
            EditRow(stringResource(R.string.vod_rec_share), stringResource(R.string.vod_rec_share_expl), s.smbShare.ifEmpty { stringResource(R.string.vod_rec_not_set) }, s.smbShare) { v -> vm.update { it.copy(smbShare = v) } }
            EditRow(stringResource(R.string.vod_rec_folder), stringResource(R.string.vod_rec_folder_expl), s.smbFolder.ifEmpty { stringResource(R.string.vod_rec_folder_top) }, s.smbFolder) { v -> vm.update { it.copy(smbFolder = v) } }
            EditRow(stringResource(R.string.vod_rec_username), stringResource(R.string.vod_rec_username_expl), s.smbUser.ifEmpty { stringResource(R.string.vod_rec_guest) }, s.smbUser, keyboard = KeyboardType.Password) { v -> vm.update { it.copy(smbUser = v) } }
            EditRow(stringResource(R.string.vod_rec_password), stringResource(R.string.vod_rec_password_expl), if (s.smbPassword.isEmpty()) stringResource(R.string.vod_rec_not_set) else "••••••", s.smbPassword,
                keyboard = KeyboardType.Password, secret = true) { v -> vm.update { it.copy(smbPassword = v) } }
            SettingRow(stringResource(R.string.vod_rec_test), stringResource(R.string.vod_rec_test_expl), test) {
                test = str(R.string.vod_rec_testing)
                scope.launch { test = vm.testShare(s)?.let { context.getString(R.string.vod_rec_test_failed, it) } ?: str(R.string.vod_ok) }
            }
        }
        SettingRow(stringResource(R.string.vod_rec_free_space_title), stringResource(R.string.vod_rec_free_space_expl), free?.let { freeSpaceText(context, it) } ?: "…") {}
        ChoiceRow(stringResource(R.string.vod_rec_start_early), stringResource(R.string.vod_rec_start_early_expl), START_EARLY, s.startEarlyMin, { stringResource(R.string.vod_minutes_short, it) }, default = AppSettings().startEarlyMin) { m ->
            vm.update { it.copy(startEarlyMin = m) }
        }
        ChoiceRow(stringResource(R.string.vod_rec_end_late), stringResource(R.string.vod_rec_end_late_expl), END_LATE, s.endLateMin, { stringResource(R.string.vod_minutes_short, it) }, default = AppSettings().endLateMin) { m -> vm.update { it.copy(endLateMin = m) } }
        SwitchRow(stringResource(R.string.vod_rec_keep_awake), stringResource(R.string.vod_rec_keep_awake_expl), s.keepAwake) { on ->
            vm.update { it.copy(keepAwake = on) }
        }
    }

    if (info) MenuFrame(stringResource(R.string.vod_rec_before_title), onDismiss = { info = false }) { f ->
        Text(stringResource(R.string.vod_rec_info), fontSize = 14.sp, color = Color.White, modifier = Modifier.padding(bottom = 10.dp))
        MenuItem(stringResource(R.string.vod_ok), Modifier.focusRequester(f)) { info = false; vm.update { it.copy(recordingOn = true) } }
        MenuItem(stringResource(R.string.vod_cancel), Modifier) { info = false }
    }
    if (confirmOff) MenuFrame(stringResource(R.string.vod_rec_off_title), onDismiss = { confirmOff = false }) { f ->
        Text(stringResource(R.string.vod_rec_off_body), fontSize = 14.sp,
            color = Color.White, modifier = Modifier.padding(bottom = 10.dp))
        MenuItem(stringResource(R.string.vod_rec_turn_off), Modifier.focusRequester(f)) { confirmOff = false; vm.recordingOff() }
        MenuItem(stringResource(R.string.vod_cancel), Modifier) { confirmOff = false }
    }
}

/** A drive's name as Android shows it ("SanDisk", "USB drive"), from the volume holding [dir]. */
private fun driveName(context: android.content.Context, dir: java.io.File): String =
    context.getSystemService(android.os.storage.StorageManager::class.java).getStorageVolume(dir)?.getDescription(context) ?: dir.path
