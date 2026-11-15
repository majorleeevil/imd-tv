package org.imdtv.iptvtv.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.imdtv.iptvtv.data.db.ProviderEntity
import org.imdtv.iptvtv.data.db.ProviderType

/** Add a playlist, or edit [editId]'s URL, login and user agent (its EPG sources are managed in Settings). */
@Composable
fun ProviderFormScreen(vm: AppViewModel, editId: Long? = null) {
    // Wait for the playlist being edited so the fields start filled in.
    val editing by produceState<ProviderEntity?>(null, editId) { value = editId?.let { vm.provider(it) } }
    if (editId != null && editing == null) return
    key(editing?.id) { ProviderForm(vm, editing) }
}

@Composable
private fun ProviderForm(vm: AppViewModel, editing: ProviderEntity?) {
    val state by vm.form.collectAsState()
    var type by rememberSaveable { mutableStateOf(editing?.type ?: ProviderType.XTREAM) }
    var url by rememberSaveable { mutableStateOf(editing?.url.orEmpty()) }
    var user by rememberSaveable { mutableStateOf(editing?.username.orEmpty()) }
    var pass by rememberSaveable { mutableStateOf(editing?.password.orEmpty().substringBefore('\n')) }
    // Stalker keeps "serial\ndeviceId" in the password column (StalkerLogin); the form shows them as two fields.
    var deviceId by rememberSaveable { mutableStateOf(editing?.takeIf { it.type == ProviderType.STALKER }?.password.orEmpty().substringAfter('\n', "")) }
    var epg by rememberSaveable { mutableStateOf("") }
    var agent by rememberSaveable { mutableStateOf(editing?.userAgent.orEmpty()) }

    var name by rememberSaveable { mutableStateOf(editing?.name.orEmpty()) }
    val ready = state.result != null
    val next = remember { FocusRequester() }
    val save = remember { FocusRequester() }
    LaunchedEffect(ready) { if (ready) runCatching { next.requestFocus() } } // Save has gone: focus Watch/Done, not the top
    // One 1080p screen (960×540 dp): labels sit beside the fields, progress and Watch on the right.
    // Scrolls only when the Name box is open on the tallest (Stalker) form.
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 48.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (editing == null) "Add playlist" else "Edit ${editing.name}", style = MaterialTheme.typography.headlineSmall, color = Color.White)
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Column(Modifier.width(560.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (editing == null) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProviderType.entries.forEach { t ->
                        Button(onClick = { type = t }) { Text((if (t == type) "● " else "○ ") + t.name) }
                    }
                }
                FormField(when (type) { ProviderType.XTREAM -> "Server URL"; ProviderType.M3U -> "Playlist URL"; ProviderType.STALKER -> "Portal URL" },
                    url, KeyboardType.Uri) { url = it }
                if (type == ProviderType.STALKER) {
                    FormField("MAC address\ne.g. 00:1A:79:12:34:56", user, KeyboardType.Password) { user = it }
                    FormField("Serial (optional)", pass, KeyboardType.Password) { pass = it }
                    FormField("Device ID (optional)", deviceId, KeyboardType.Password) { deviceId = it }
                } else if (type == ProviderType.XTREAM) {
                    // The password keyboard has the number row many providers' usernames need.
                    FormField("Username", user, KeyboardType.Password) { user = it }
                    FormField("Password", pass, KeyboardType.Password, secret = true) { pass = it }
                } else if (editing == null) {
                    FormField("EPG URLs (optional, one per line)", epg, KeyboardType.Uri) { epg = it }
                }
                FormField("User agent (optional)", agent) { agent = it }
                org.imdtv.iptvtv.ui.settings.EditRow("Name (optional)", "Blank: named after the URL, e.g. example.com",
                    // Down goes to Save: the nearest-below search picked "Refresh guide now".
                    modifier = Modifier.focusProperties { if (!ready) down = save },
                    value = name.ifBlank { "Automatic" }, initial = name, saveLabel = "Confirm") { name = it } // "Save" here read as the form's Save (user, 2026-11-15)
                if (!ready) {
                    // The buttons stay while busy: if Save left the screen, focus fell to the top and the error showed off screen.
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Button(onClick = {
                            if (state.busy) return@Button
                            val secret = if (type == ProviderType.STALKER) org.imdtv.iptvtv.data.source.StalkerLogin(user, pass, deviceId).packed().orEmpty() else pass
                            vm.save(type, url, user, secret, epg, agent, editing, name)
                        }, Modifier.focusRequester(save)) { Text("Save") }
                        if (vm.canLeaveSetup && editing == null) Button(onClick = vm::refreshGuide) { Text("Refresh guide now") }
                        // Beside Save, not above it: a line above pushed Save off the bottom of the Stalker form.
                        if (!state.busy) state.error?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 13.sp, maxLines = 3, modifier = Modifier.weight(1f)) }
                    }
                }
            }
            Column(Modifier.width(240.dp).padding(top = if (editing == null) 56.dp else 0.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Greyed out (not pressable) until the playlist has loaded.
                Button(onClick = { if (editing == null) vm.done() else vm.back() }, Modifier.focusRequester(next), enabled = ready) {
                    Text(if (editing == null) "Watch" else "Done")
                }
                if (state.busy) {
                    Spinner(size = 32.dp)
                    Text(state.progress ?: "Loading playlist…", color = Color.White, fontSize = 14.sp)
                }
                state.result?.let { Text(it, color = Color.White, fontSize = 14.sp) }
            }
        }
    }
}

/** A [Field] with its label on the left, so the form fits one screen. */
@Composable
private fun FormField(label: String, value: String, keyboard: KeyboardType = KeyboardType.Text, secret: Boolean = false, onChange: (String) -> Unit) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(label, color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.width(170.dp))
        Column(Modifier.weight(1f)) { Field("", value, keyboard, secret, openOnOk = true, onChange = onChange) }
    }
}

@Composable
fun Field(
    label: String, value: String, keyboard: KeyboardType = KeyboardType.Text, secret: Boolean = false,
    modifier: Modifier = Modifier, openOnOk: Boolean = false, onChange: (String) -> Unit,
) {
    // openOnOk: read-only until OK, so D-pad can pass through a form without a keyboard on every field.
    var editing by remember { mutableStateOf(!openOnOk) }
    val keyboardCtl = LocalSoftwareKeyboardController.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val focus = LocalFocusManager.current
    Column {
        if (label.isNotEmpty()) Text(label, color = Color.LightGray)
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = !label.startsWith("EPG"), readOnly = !editing,
            interactionSource = interaction,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            textStyle = androidx.tv.material3.LocalTextStyle.current.merge(TextStyle(color = Color.White, fontSize = 20.sp)),
            cursorBrush = SolidColor(Color.White),
            // A text field keeps Up/Down as cursor moves, which traps a D-pad: use them to leave the field.
            modifier = modifier.onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionDown -> { focus.moveFocus(FocusDirection.Down); true }
                    Key.DirectionUp -> { focus.moveFocus(FocusDirection.Up); true }
                    Key.DirectionCenter, Key.Enter -> if (editing) false else { editing = true; keyboardCtl?.show(); true }
                    else -> false
                }
            }.onFocusChanged { if (openOnOk && !it.isFocused) editing = false }.fillMaxWidth()
                .border(2.dp, if (focused) Color.White else Color.Gray, RoundedCornerShape(8.dp))
                .padding(12.dp),
        )
    }
}
