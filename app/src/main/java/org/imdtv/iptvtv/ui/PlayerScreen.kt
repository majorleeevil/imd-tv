package org.imdtv.iptvtv.ui

import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.pointerInput
import org.imdtv.iptvtv.data.settings.delta
import org.imdtv.iptvtv.ui.hasCatchUp
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.viewinterop.AndroidView
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import org.imdtv.iptvtv.data.settings.VideoAspect
import kotlinx.coroutines.delay
import org.imdtv.iptvtv.R
import org.imdtv.iptvtv.data.catalogue.displayName
import org.imdtv.iptvtv.player.PlayerManager
import androidx.tv.material3.Text
import org.imdtv.iptvtv.ui.live.LiveOverlay
import org.imdtv.iptvtv.ui.live.LiveViewModel

private val OK = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

/** Full-screen playback. Live ([liveVm] set) adds the info overlay (OK opens, Back closes, 5s auto-hide) and Up/Down (or Left/Right) zapping. */
@Composable
fun PlayerScreen(player: PlayerManager, title: String, liveVm: LiveViewModel? = null, onGuide: () -> Unit = {}, showClock: Boolean = false,
                 aspect: VideoAspect = VideoAspect.AUTO, onCategories: () -> Unit = onGuide, onSettings: () -> Unit = {}) {
    val live = liveVm != null
    val context = androidx.compose.ui.platform.LocalContext.current
    val zapLeftRight = liveVm?.settings?.collectAsState()?.value?.zapLeftRight == true
    val dpadKeys = liveVm?.settings?.collectAsState()?.value?.dpadKeys.orEmpty()
    val dpad = remember(dpadKeys) { org.imdtv.iptvtv.data.settings.parseDpadKeys(dpadKeys) }
    val focused = liveVm?.focused?.collectAsState()?.value
    val name = focused?.displayName(liveVm.strip.collectAsState().value) ?: title
    // Kept with the channel it is for: a stream that fails inside play() reports before a zap's effects run.
    var failure by remember { mutableStateOf<Pair<Long?, String>?>(null) }
    val shownName by androidx.compose.runtime.rememberUpdatedState(if (live) name else title) // the zapped-to channel, not the first
    // The view model's value, not the composed one: a zap sets it, then plays, before the next recomposition.
    LaunchedEffect(Unit) { player.errors.collect { failure = liveVm?.focused?.value?.id to context.getString(R.string.live_err_cant_play, shownName, org.imdtv.iptvtv.player.playErrorText(context, it)) } }
    val error = failure?.takeIf { it.first == focused?.id }?.second

    var overlay by remember { mutableStateOf(false) }
    var lastKey by remember { mutableLongStateOf(0L) }
    var menuOpen by remember { mutableStateOf(false) }
    val keys = remember { FocusRequester() }
    val leftHeld = remember { BooleanArray(1) }
    BackHandler(enabled = overlay) { overlay = false }
    LaunchedEffect(overlay, lastKey, menuOpen) {
        if (overlay) { if (!menuOpen) { delay(5_000); overlay = false } }
        else if (live) { withFrameNanos { }; runCatching { keys.requestFocus() } }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .onPreviewKeyEvent { lastKey = System.nanoTime(); liveVm?.numberKey(it, true) == true },
    ) {
        // 16:9 / 4:3: the view is boxed to that shape and the picture stretched to fill it; Fill zooms and crops.
        val box = when (aspect) { VideoAspect.WIDE -> 16f / 9; VideoAspect.STANDARD -> 4f / 3; else -> null }
        AndroidView(
            factory = { ctx -> PlayerView(ctx).also {
                it.useController = !live
                if (!live) { insetControls(it); it.setShowSubtitleButton(true); keepFocusWhenControlsHide(it) } // shown beside the cog only when the file has subtitles
                player.attach(it)
                if (live) it.isFocusable = false else it.requestFocus()
            } },
            update = { it.resizeMode = when (aspect) {
                VideoAspect.AUTO -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                VideoAspect.FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                else -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            } },
            onRelease = { player.detach(it) },
            modifier = if (box == null) Modifier.fillMaxSize() else Modifier.align(Alignment.Center).fillMaxHeight().aspectRatio(box, matchHeightConstraintsFirst = true),
        )
        if (liveVm != null) {
            // Key target while the overlay is closed. OK opens on key-up, so the same press can't also click the TV guide card.
            // An arrow's action (#21): zaps repeat while held; the others act once, on the first press.
            fun arrow(k: org.imdtv.iptvtv.data.settings.Dpad, first: Boolean) {
                when (org.imdtv.iptvtv.data.settings.dpadAction(k, zapLeftRight, dpad)) {
                    org.imdtv.iptvtv.data.settings.DpadAction.CHANNEL -> liveVm.zapBy(k.delta)
                    org.imdtv.iptvtv.data.settings.DpadAction.PREVIOUS -> liveVm.toPreviousChannel(k.delta)
                    org.imdtv.iptvtv.data.settings.DpadAction.GUIDE -> if (first) onGuide()
                    org.imdtv.iptvtv.data.settings.DpadAction.CATEGORIES -> if (first) onCategories()
                    org.imdtv.iptvtv.data.settings.DpadAction.INFO -> if (first) overlay = true
                    org.imdtv.iptvtv.data.settings.DpadAction.SETTINGS -> if (first) onSettings()
                    else -> Unit
                }
            }
            Box(Modifier.fillMaxSize().focusRequester(keys).focusable().pointerInput(focused, overlay) {
                // Touch (#29): tap = OK (the overlay; again closes it), swipe = that arrow's action, long press on catch-up = hold Left.
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var last = down.position
                    fun moved() = (last - down.position).getDistance() > viewConfiguration.touchSlop
                    val lifted = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        do { val e = awaitPointerEvent(); e.changes.firstOrNull()?.let { last = it.position } } while (e.changes.any { it.pressed })
                        true
                    }
                    val long = lifted == null && !moved()
                    if (lifted == null) do { val e = awaitPointerEvent(); e.changes.firstOrNull()?.let { last = it.position } } while (e.changes.any { it.pressed })
                    val d = last - down.position
                    val swipe = 48.dp.toPx()
                    when {
                        long -> if (focused?.hasCatchUp == true) liveVm.jumpBack(focused)
                        overlay -> overlay = false
                        kotlin.math.abs(d.y) > swipe && kotlin.math.abs(d.y) > kotlin.math.abs(d.x) ->
                            arrow(if (d.y < 0) org.imdtv.iptvtv.data.settings.Dpad.UP else org.imdtv.iptvtv.data.settings.Dpad.DOWN, first = true)
                        kotlin.math.abs(d.x) > swipe -> arrow(if (d.x < 0) org.imdtv.iptvtv.data.settings.Dpad.LEFT else org.imdtv.iptvtv.data.settings.Dpad.RIGHT, first = true)
                        !moved() -> liveVm.lockedChannel.value?.let { liveVm.preview(it) } ?: run { overlay = true }
                    }
                }
            }.onKeyEvent { e ->
                if (overlay) return@onKeyEvent false
                val k = when (e.key) {
                    Key.DirectionUp -> org.imdtv.iptvtv.data.settings.Dpad.UP; Key.DirectionDown -> org.imdtv.iptvtv.data.settings.Dpad.DOWN
                    Key.DirectionLeft -> org.imdtv.iptvtv.data.settings.Dpad.LEFT; Key.DirectionRight -> org.imdtv.iptvtv.data.settings.Dpad.RIGHT
                    else -> null
                }
                when {
                    // A parental-locked channel: OK asks for the PIN again instead of opening the overlay.
                    e.key in OK -> { if (e.type == KeyEventType.KeyUp) liveVm.lockedChannel.value?.let { liveVm.preview(it) } ?: run { overlay = true }; true }
                    // Catch-up channel: Left acts on key-up, and held it jumps 30 s back into catch-up of the programme on now.
                    e.key == Key.DirectionLeft && focused?.hasCatchUp == true -> {
                        if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount > 0) { if (!leftHeld[0]) { leftHeld[0] = true; liveVm.jumpBack(focused) } }
                        else if (e.type == KeyEventType.KeyUp) {
                            if (!leftHeld[0]) arrow(org.imdtv.iptvtv.data.settings.Dpad.LEFT, first = true)
                            leftHeld[0] = false
                        }
                        true
                    }
                    // By default one pair zaps and the other steps through the channels watched before (Playback › View Previous Channel With).
                    k != null -> { if (e.type == KeyEventType.KeyDown) arrow(k, e.nativeKeyEvent.repeatCount == 0); true }
                    // The remote's Channel Up/Down zap whatever the arrow setting (Up = next channel).
                    e.key == Key.ChannelUp || e.key == Key.ChannelDown -> { if (e.type == KeyEventType.KeyDown) liveVm.zapBy(if (e.key == Key.ChannelUp) 1 else -1); true }
                    else -> false
                }
            })
            if (overlay) LiveOverlay(liveVm, onGuide = onGuide, onPick = { liveVm.switchTo(it); overlay = false }, onMenuOpen = { menuOpen = it })
        }
        if (player.buffering.collectAsState().value && error == null) Spinner(Modifier.align(Alignment.Center), size = 40.dp)
        if (showClock) PlayerClock(Modifier.align(Alignment.TopEnd).padding(top = 27.dp, end = 48.dp))
        error?.let {
            Text(it, color = Color.White, modifier = Modifier.align(Alignment.Center).background(Color(0xCC000000)).padding(24.dp))
        }
        liveVm?.lockedChannel?.collectAsState()?.value?.let { ch ->
            val app = liveVm.settings.collectAsState().value.unlockFor == org.imdtv.iptvtv.data.parental.UnlockFor.APP
            Text(androidx.compose.ui.res.stringResource(org.imdtv.iptvtv.R.string.live_parental_locked, ch.displayName(liveVm.strip.collectAsState().value)) +
                (if (app) "\n" + androidx.compose.ui.res.stringResource(org.imdtv.iptvtv.R.string.live_parental_locked_app) else "") +
                "\n" + androidx.compose.ui.res.stringResource(org.imdtv.iptvtv.R.string.live_parental_locked_ok),
                color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).background(Color(0xCC000000), androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).padding(24.dp))
        }
    }
}

/** Its own composable, so the minute tick recomposes only this Text. */
@Composable
private fun PlayerClock(modifier: Modifier) {
    val now by androidx.compose.runtime.produceState(System.currentTimeMillis()) {
        while (true) { delay(60_000 - System.currentTimeMillis() % 60_000); value = System.currentTimeMillis() }
    }
    Text(clockTime(now), fontSize = 14.sp, color = Color.White,
        modifier = modifier.background(Color(0x99000000), RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 4.dp))
}

/** Moves the controller's bottom bar (cog, time) and time bar into the TV-safe area; the dark backdrop stays full-screen. */
private fun insetControls(view: PlayerView) {
    val d = view.resources.displayMetrics.density
    val side = (48 * d).toInt(); val bottom = (27 * d).toInt()
    listOf(androidx.media3.ui.R.id.exo_bottom_bar, androidx.media3.ui.R.id.exo_progress).forEach { id ->
        view.findViewById<android.view.View>(id)?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            leftMargin += side; rightMargin += side; bottomMargin += bottom
        }
    }
}

/** When the controls hide with focus on one of their buttons (subtitles, cog), Android drops focus and the D-pad goes
 *  nowhere; hand it back to the player view so the next key shows the controls again. */
fun keepFocusWhenControlsHide(view: PlayerView) {
    view.isFocusable = true
    view.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { v ->
        if (v != android.view.View.VISIBLE && !view.isFocused) view.requestFocus()
    })
}


