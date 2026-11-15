package org.imdtv.iptvtv.ui

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
import androidx.compose.ui.viewinterop.AndroidView
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import org.imdtv.iptvtv.data.catalogue.displayName
import org.imdtv.iptvtv.player.PlayerManager
import androidx.tv.material3.Text
import org.imdtv.iptvtv.ui.live.LiveOverlay
import org.imdtv.iptvtv.ui.live.LiveViewModel

private val OK = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

/** Full-screen playback. Live ([liveVm] set) adds the info overlay (OK opens, Back closes, 5s auto-hide) and Up/Down zapping. */
@Composable
fun PlayerScreen(player: PlayerManager, title: String, liveVm: LiveViewModel? = null, onGuide: () -> Unit = {}) {
    val live = liveVm != null
    val focused = liveVm?.focused?.collectAsState()?.value
    val name = focused?.displayName(liveVm.strip.collectAsState().value) ?: title
    // Kept with the channel it is for: a stream that fails inside play() reports before a zap's effects run.
    var failure by remember { mutableStateOf<Pair<Long?, String>?>(null) }
    val shownName by androidx.compose.runtime.rememberUpdatedState(if (live) name else title) // the zapped-to channel, not the first
    // The view model's value, not the composed one: a zap sets it, then plays, before the next recomposition.
    LaunchedEffect(Unit) { player.errors.collect { failure = liveVm?.focused?.value?.id to "Can't play $shownName: ${org.imdtv.iptvtv.player.playErrorText(it)}" } }
    val error = failure?.takeIf { it.first == focused?.id }?.second

    var overlay by remember { mutableStateOf(false) }
    var lastKey by remember { mutableLongStateOf(0L) }
    val keys = remember { FocusRequester() }
    BackHandler(enabled = overlay) { overlay = false }
    LaunchedEffect(overlay, lastKey) {
        if (overlay) { delay(5_000); overlay = false }
        else if (live) { withFrameNanos { }; runCatching { keys.requestFocus() } }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .onPreviewKeyEvent { lastKey = System.nanoTime(); false },
    ) {
        AndroidView(
            factory = { ctx -> PlayerView(ctx).also {
                it.useController = !live
                if (!live) { insetControls(it); it.setShowSubtitleButton(true); keepFocusWhenControlsHide(it) } // shown beside the cog only when the file has subtitles
                player.attach(it)
                if (live) it.isFocusable = false else it.requestFocus()
            } },
            onRelease = { player.detach(it) },
            modifier = Modifier.fillMaxSize(),
        )
        if (liveVm != null) {
            // Key target while the overlay is closed. OK opens on key-up, so the same press can't also click the TV guide card.
            Box(Modifier.fillMaxSize().focusRequester(keys).focusable().onKeyEvent { e ->
                if (overlay) return@onKeyEvent false
                when {
                    e.key in OK -> { if (e.type == KeyEventType.KeyUp) overlay = true; true }
                    e.key == Key.DirectionUp -> { if (e.type == KeyEventType.KeyDown) liveVm.zapBy(-1); true }
                    e.key == Key.DirectionDown -> { if (e.type == KeyEventType.KeyDown) liveVm.zapBy(1); true }
                    else -> false
                }
            })
            if (overlay) LiveOverlay(liveVm, onGuide = onGuide, onPick = { liveVm.switchTo(it); overlay = false })
        }
        error?.let {
            Text(it, color = Color.White, modifier = Modifier.align(Alignment.Center).background(Color(0xCC000000)).padding(24.dp))
        }
    }
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
