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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
    var pass by rememberSaveable { mutableStateOf(editing?.password.orEmpty()) }
    var epg by rememberSaveable { mutableStateOf("") }
    var agent by rememberSaveable { mutableStateOf(editing?.userAgent.orEmpty()) }

    Column(
        Modifier.padding(48.dp).width(640.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(if (editing == null) "Add playlist" else "Edit ${editing.name}", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        if (editing == null) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ProviderType.entries.forEach { t ->
                Button(onClick = { type = t }) { Text((if (t == type) "● " else "○ ") + t.name) }
            }
        }
        Field(if (type == ProviderType.XTREAM) "Server URL" else "Playlist URL", url, KeyboardType.Uri, openOnOk = true) { url = it }
        if (type == ProviderType.XTREAM) {
            // The password keyboard has the number row many providers' usernames need.
            Field("Username", user, KeyboardType.Password, openOnOk = true) { user = it }
            Field("Password", pass, KeyboardType.Password, secret = true, openOnOk = true) { pass = it }
        } else if (editing == null) {
            Field("EPG URLs (optional, one per line)", epg, KeyboardType.Uri, openOnOk = true) { epg = it }
        }
        Field("User agent (optional)", agent, openOnOk = true) { agent = it }
        when {
            state.busy -> Text(state.progress ?: "Loading playlist…", color = Color.White)
            state.result != null -> {
                Text(state.result!!, color = Color.White)
                if (editing == null) Button(onClick = vm::done) { Text("Watch") }
                else Button(onClick = { vm.back() }) { Text("Done") }
            }
            else -> {
                state.error?.let { Text(it, color = Color(0xFFFF8A80)) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { vm.save(type, url, user, pass, epg, agent, editing) }) { Text("Save") }
                    if (vm.canLeaveSetup && editing == null) Button(onClick = vm::refreshGuide) { Text("Refresh guide now") }
                }
            }
        }
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
