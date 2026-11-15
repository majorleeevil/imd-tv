package org.imdtv.iptvtv.data.update

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.imdtv.iptvtv.ui.MenuFrame
import org.imdtv.iptvtv.ui.MenuItem

/** The update prompt, over every screen. The download runs in [background], so closing the prompt doesn't stop it. */
@Composable
fun UpdatePrompt(updater: Updater, background: CoroutineScope) {
    val open by updater.prompt.collectAsState()
    val update by updater.available.collectAsState()
    val progress by updater.progress.collectAsState()
    val error by updater.error.collectAsState()
    val scope = rememberCoroutineScope()
    val u = update ?: return
    if (!open) return
    MenuFrame("IMD TV ${u.version} is available", onDismiss = { scope.launch { updater.later() } }) { first ->
        val body = error ?: progress ?: u.notes.ifBlank { "A new version of IMD TV is ready to install." }
        Text(body, fontSize = 15.sp, color = Color.White, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp))
        if (progress != null) MenuItem("Hide", Modifier.focusRequester(first)) { updater.prompt.value = false }
        else {
            MenuItem(if (error != null) "Try again" else "Update now", Modifier.focusRequester(first)) { background.launch { updater.install(u) } }
            MenuItem("Later", Modifier) { scope.launch { updater.later() } }
        }
    }
}

/** "What's new": this version's release notes after an update. Up/Down scroll the notes; at the end, Down moves to OK. */
@Composable
fun WhatsNewPrompt(updater: Updater) {
    val notes by updater.whatsNew.collectAsState()
    val scope = rememberCoroutineScope()
    val text = notes ?: return
    val close = { scope.launch { updater.closeWhatsNew() }; Unit }
    MenuFrame("What's new in IMD TV ${org.imdtv.iptvtv.BuildConfig.VERSION_NAME}", onDismiss = close) { first ->
        val scroll = rememberScrollState()
        var focused by remember { mutableStateOf(false) }
        Text(text, fontSize = 15.sp, color = Color.White, modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)
            .clip(RoundedCornerShape(8.dp)).background(if (focused) Color(0x1FFFFFFF) else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }.focusRequester(first)
            .onPreviewKeyEvent { e ->
                val step = when (e.key) { Key.DirectionDown -> 150f; Key.DirectionUp -> -150f; else -> return@onPreviewKeyEvent false }
                if (if (step > 0) !scroll.canScrollForward else !scroll.canScrollBackward) return@onPreviewKeyEvent false
                if (e.type == KeyEventType.KeyDown) scope.launch { scroll.animateScrollBy(step) }
                true
            }.focusable().verticalScroll(scroll).padding(8.dp))
        MenuItem("OK", Modifier.padding(top = 8.dp)) { close() }
    }
}
