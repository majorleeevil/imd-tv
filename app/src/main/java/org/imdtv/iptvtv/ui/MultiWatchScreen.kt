package org.imdtv.iptvtv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import org.imdtv.iptvtv.R
import org.imdtv.iptvtv.data.catalogue.displayName
import org.imdtv.iptvtv.data.db.ChannelEntity
import org.imdtv.iptvtv.ui.live.LiveViewModel

/**
 * #65 Multi-Watch: up to four live channels, each in its own player. Only the focused tile has sound; OK opens a menu
 * (Watch full screen, Remove); Back leaves. Tiles play only while the app is on screen, like the main player.
 */
@Composable
fun MultiWatchScreen(channels: List<ChannelEntity>, live: LiveViewModel, onFull: (ChannelEntity) -> Unit, onRemove: (ChannelEntity) -> Unit) {
    val strip by live.strip.collectAsState()
    val lifecycle by androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val onScreen = lifecycle.isAtLeast(Lifecycle.State.STARTED)
    var focusedId by remember { mutableStateOf<Long?>(null) }
    var menuFor by remember { mutableStateOf<ChannelEntity?>(null) }
    val first = remember { FocusRequester() }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // 1: whole screen; 2: side by side; 3–4: two rows of two.
        val rows = if (channels.size <= 2) listOf(channels) else channels.chunked(2)
        Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEachIndexed { r, row ->
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEachIndexed { i, ch ->
                        key(ch.id) {
                            Tile(ch, ch.displayName(strip), live, onScreen, sound = focusedId == ch.id,
                                Modifier.weight(1f).fillMaxHeight().then(if (r == 0 && i == 0) Modifier.focusRequester(first) else Modifier),
                                onFocused = { focusedId = ch.id }) { menuFor = ch }
                        }
                    }
                    if (row.size == 1 && rows.size > 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        if (channels.size < org.imdtv.iptvtv.ui.MULTI_MAX) Text(stringResource(R.string.multi_hint), fontSize = 11.sp, color = Color.White,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x99000000))
                .padding(horizontal = 12.dp, vertical = 5.dp))
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    menuFor?.let { ch ->
        ContextMenu(ch.displayName(strip), listOf(
            stringResource(R.string.sports_watch_full) to { menuFor = null; onFull(ch) },
            stringResource(R.string.multi_remove) to { menuFor = null; onRemove(ch) },
        )) { menuFor = null }
    }
}

@Composable
private fun Tile(ch: ChannelEntity, name: String, live: LiveViewModel, onScreen: Boolean, sound: Boolean, modifier: Modifier, onFocused: () -> Unit, onClick: () -> Unit) {
    val pm = remember { live.tilePlayer() }
    DisposableEffect(pm) { onDispose { pm.release() } }
    var problem by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(onScreen) {
        if (!onScreen) return@LaunchedEffect pm.stop()
        problem = null
        if (!live.playInTile(pm, ch)) problem = context.getString(R.string.multi_locked)
    }
    LaunchedEffect(pm) { pm.errors.collect { problem = org.imdtv.iptvtv.player.playErrorText(context, it) } }
    // The player is only rebuilt for a decoder change, which tiles never get: the volume stays set.
    LaunchedEffect(sound) { pm.player.volume = if (sound) 1f else 0f }
    val buffering by pm.buffering.collectAsState()
    FocusBox(onClick, modifier, onFocused) { f ->
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(Color(0xFF111111))) {
            AndroidView(factory = { ctx -> PlayerView(ctx).also {
                    it.useController = false
                    it.isFocusable = false
                    it.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    pm.attach(it)
                } }, onRelease = { pm.detach(it) }, modifier = Modifier.fillMaxSize())
            if (buffering && problem == null) Spinner(Modifier.align(Alignment.Center), 28.dp, Color.White)
            problem?.let { Text(it, fontSize = 12.sp, color = Color.White, modifier = Modifier.align(Alignment.Center).padding(12.dp)) }
            Text((if (sound) "🔊 " else "") + name, fontSize = 12.sp, color = Color.White, maxLines = 1,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x99000000))
                    .padding(horizontal = 8.dp, vertical = 3.dp))
            if (f) Box(Modifier.fillMaxSize().border(3.dp, Color.White, RoundedCornerShape(10.dp)))
        }
    }
}
