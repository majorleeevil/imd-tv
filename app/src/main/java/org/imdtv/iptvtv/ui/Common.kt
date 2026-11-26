package org.imdtv.iptvtv.ui

import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import kotlinx.coroutines.withTimeoutOrNull
import org.imdtv.iptvtv.ui.theme.glass
import org.imdtv.iptvtv.ui.theme.LocalGlassBackdrop
import org.imdtv.iptvtv.ui.theme.LocalTheme

import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Icon
import kotlinx.coroutines.delay
import org.imdtv.iptvtv.R
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun keepOrFirst(current: org.imdtv.iptvtv.data.db.CategoryEntity?, list: List<org.imdtv.iptvtv.data.db.CategoryEntity>, preferId: Long? = null) =
    list.firstOrNull { it.id == current?.id } ?: list.firstOrNull { it.id == preferId } ?: list.firstOrNull()

enum class Tab(val label: String, @androidx.annotation.DrawableRes val icon: Int, @androidx.annotation.StringRes val labelRes: Int) {
    SEARCH("Search", R.drawable.ic_tab_search, R.string.live_tab_search), LIVE("Live", R.drawable.ic_tab_live, R.string.live_tab_live), MOVIES("Movies", R.drawable.ic_tab_movies, R.string.live_tab_movies),
    SERIES("Series", R.drawable.ic_tab_series, R.string.live_tab_series), RECORDINGS("Recordings", R.drawable.ic_tab_recordings, R.string.live_tab_recordings),
    SPORTS("SportsHub", R.drawable.ic_tab_sports, R.string.live_tab_sports), SETTINGS("Settings", R.drawable.ic_tab_settings, R.string.live_tab_settings),
}

/** The main menu's entries: Recordings only while recording is switched on, SportsHub unless hidden in its settings. */
fun visibleTabs(recordingOn: Boolean, sportsOn: Boolean = false): List<Tab> =
    Tab.entries.filter { (it != Tab.RECORDINGS || recordingOn) && (it != Tab.SPORTS || sportsOn) }

/** A D-pad focusable, OK-clickable box that tells its content whether it has focus. */
@Composable
fun FocusBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    // Touch (#29): a tap doesn't move focus, so take it first, as OK would find it (a no-op on a TV, where OK comes from focus).
    // In touch mode Compose 1.7's clickable can't take focus at all, so a tap that left focus elsewhere still runs onFocused:
    // focus-driven lists (Settings sections) follow the tap.
    val self = remember { FocusRequester() }
    Box(
        modifier
            .focusRequester(self)
            .onFocusChanged { if (it.isFocused != focused) { focused = it.isFocused; if (focused) onFocused() } }
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = { runCatching { self.requestFocus() }; if (!focused) onFocused(); onClick() }),
    ) { content(focused) }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun TopNav(selected: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier, selectedFocus: FocusRequester? = null, showClock: Boolean = true,
           showDate: Boolean = false, pingMs: Int? = null, tabs: List<Tab> = visibleTabs(false)) {
    // Entering the menu from below lands on the current tab, not the nearest one.
    Row(
        modifier.focusProperties { enter = { selectedFocus ?: FocusRequester.Default } }.focusGroup()
            // Liquid Glass: the nav sits in a glass pill (no-op in other themes).
            .glass(RoundedCornerShape(22.dp)).padding(if (LocalGlassBackdrop.current != null) 5.dp else 0.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { t ->
            FocusBox(onClick = { onSelect(t) }, modifier = if (t == selected && selectedFocus != null) Modifier.focusRequester(selectedFocus) else Modifier) { f ->
                val fg = when { f -> Color.Black; t == selected -> Color.White; else -> LocalTheme.current.colors.muted }
                Row(
                    Modifier.clip(RoundedCornerShape(15.dp))
                        .background(when { f -> Color.White; t == selected -> Color(0x1FFFFFFF); else -> Color.Transparent })
                        .padding(horizontal = 13.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(t.icon), contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
                    Text("  " + stringResource(t.labelRes), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = fg)
                }
            }
        }
        if (showClock || showDate) NavClock(showClock, showDate)
        pingMs?.let { Text(pingLabel(it), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = LocalTheme.current.colors.muted, modifier = Modifier.padding(start = 14.dp)) }
    }
}

/** Its own composable, so the minute tick recomposes only this Text, not the nav. */
@Composable
private fun NavClock(showClock: Boolean, showDate: Boolean) {
    val now by produceState(System.currentTimeMillis()) {
        while (true) { delay(60_000 - System.currentTimeMillis() % 60_000); value = System.currentTimeMillis() }
    }
    Text(listOfNotNull(clockTime(now).takeIf { showClock }, dayDate(now).takeIf { showDate }).joinToString(" "), fontSize = 12.sp, fontWeight = FontWeight.Medium,
        color = Color.White, modifier = Modifier.padding(start = 14.dp))
}

// shortcut: global, not state; screens pick it up on their next recomposition (clock ticks each minute)
@Volatile var use24Hour = true

private val clock = DateTimeFormatter.ofPattern("h.mma", Locale.UK)
private val clock24 = DateTimeFormatter.ofPattern("HH:mm", Locale.UK)
fun clockTime(t: Long): String =
    (if (use24Hour) clock24 else clock).format(Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault())).lowercase()

private val day = DateTimeFormatter.ofPattern("EEE d/M", Locale.UK)

/** Day and month names in the app's language (English keeps the UK forms). MainActivity sets it on every (re)start. */
var namesLocale: Locale = Locale.UK
/** "Fri 13/11". */
fun dayDate(t: Long): String = day.withLocale(namesLocale).format(Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()))

/** "12 ms", or "offline" when the last check failed (-1). */
@Composable
fun pingLabel(ms: Int): String = if (ms < 0) stringResource(R.string.live_ping_offline) else stringResource(R.string.live_ping_ms, ms)

fun duration(ms: Long): String {
    val m = ms / 60_000
    return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
}

private val OK_KEYS = setOf(android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER, android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)

/** OK held: the first auto-repeat (or a long-press flagged event) of an OK key going down. */
fun isLongOk(e: android.view.KeyEvent): Boolean =
    e.keyCode in OK_KEYS && e.action == android.view.KeyEvent.ACTION_DOWN && (e.repeatCount == 1 || e.isLongPress)

/** Long OK runs [action] and swallows the rest of that press (repeats and key-up), so the normal click doesn't fire too. */
fun Modifier.onLongOk(action: () -> Unit): Modifier = composed {
    var swallowing by remember { mutableStateOf(false) }
    val latest by androidx.compose.runtime.rememberUpdatedState(action)
    // Touch (#29): a long press is hold OK. Initial pass, so its lift is consumed before the click inside sees it.
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            // Lifted, or moved (a scroll), before the timeout: not a long press.
            val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                var e: androidx.compose.ui.input.pointer.PointerEvent
                do { e = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial) }
                while (e.changes.any { it.pressed && (it.position - down.position).getDistance() < viewConfiguration.touchSlop })
                e
            }
            if (up == null) {
                latest()
                do {
                    val e = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                    e.changes.forEach { it.consume() }
                } while (e.changes.any { it.pressed })
            }
        }
    }.onPreviewKeyEvent { k ->
        val e = k.nativeKeyEvent
        when {
            isLongOk(e) -> { swallowing = true; action(); true }
            swallowing && e.keyCode in OK_KEYS -> { if (e.action == android.view.KeyEvent.ACTION_UP) swallowing = false; true }
            else -> false
        }
    }
}

/** A touch tap (a still finger lifted), for what a remote reaches by a key with no click of its own. Not consumed. */
fun Modifier.onTouchTap(action: (Offset) -> Unit): Modifier = composed {
    val act by androidx.compose.runtime.rememberUpdatedState(action)
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            while (true) {
                val c = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                if (c.isConsumed || (c.position - down.position).getDistance() > viewConfiguration.touchSlop) return@awaitEachGesture
                if (!c.pressed) { act(c.position); return@awaitEachGesture }
            }
        }
    }
}

/**
 * Touch stand-in for the D-pad: every 48dp of drag along an allowed axis calls [onStep] with the finger's direction
 * (dx or dy is -1/1). Once locked to an axis it consumes the touch, so it never also clicks or scrolls; a drag along
 * a disallowed axis is left alone (a list under it still scrolls).
 */
fun Modifier.onSwipe(horizontal: Boolean = true, vertical: Boolean = true, onStep: (dx: Int, dy: Int) -> Unit): Modifier = composed {
    val step by androidx.compose.runtime.rememberUpdatedState(onStep)
    pointerInput(horizontal, vertical) {
        val stepPx = 48.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var total = Offset.Zero
            var axis = 0 // 0 undecided, 1 horizontal, 2 vertical
            while (true) {
                val c = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                if (!c.pressed) return@awaitEachGesture
                total += c.positionChange()
                if (axis == 0 && total.getDistance() > viewConfiguration.touchSlop) {
                    axis = if (kotlin.math.abs(total.x) > kotlin.math.abs(total.y)) 1 else 2
                    if (if (axis == 1) !horizontal else !vertical) return@awaitEachGesture
                }
                if (axis == 0) continue
                c.consume()
                val along = if (axis == 1) total.x else total.y
                if (kotlin.math.abs(along) >= stepPx) {
                    val d = if (along > 0) 1 else -1
                    if (axis == 1) step(d, 0) else step(0, d)
                    total = if (axis == 1) total.copy(x = along - d * stepPx) else total.copy(y = along - d * stepPx)
                }
            }
        }
    }
}

/** Touch never sends OK: a tap on a read-only-until-OK text field (one using [interaction]) opens it as OK does. */
@Composable
fun OpenOnTap(interaction: androidx.compose.foundation.interaction.InteractionSource, open: () -> Unit) {
    val o by androidx.compose.runtime.rememberUpdatedState(open)
    androidx.compose.runtime.LaunchedEffect(interaction) {
        interaction.interactions.collect { if (it is androidx.compose.foundation.interaction.PressInteraction.Release) o() }
    }
}

/** The app's logo (res/drawable-xhdpi/logo.png, rendered from art/mark.svg). */
@Composable
fun AppLogo(modifier: Modifier = Modifier) =
    androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(org.imdtv.iptvtv.R.drawable.logo), contentDescription = null, modifier = modifier)

/** The small bottom information bar ("Press ↩ to exit application", "5 channels hidden"); the caller places and times it. */
@Composable
fun InfoBar(text: AnnotatedString, modifier: Modifier = Modifier) = Text(text, fontSize = 14.sp, color = Color.White,
    modifier = modifier.background(Color(0xCC1A1C24), RoundedCornerShape(20.dp)).padding(horizontal = 20.dp, vertical = 9.dp))

@Composable
fun InfoBar(text: String, modifier: Modifier = Modifier) = InfoBar(AnnotatedString(text), modifier)
