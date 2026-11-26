package org.imdtv.iptvtv.ui.xcube

import org.imdtv.iptvtv.ui.onTouchTap
import androidx.compose.foundation.layout.wrapContentSize
import org.imdtv.iptvtv.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.size
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import org.imdtv.iptvtv.ui.FocusBox
import org.imdtv.iptvtv.ui.Tab
import org.imdtv.iptvtv.ui.theme.LocalTheme
import org.imdtv.iptvtv.ui.theme.ThemeColors

private val BLADE = 34.dp      // width of each stacked side blade
private val EDGE = 22.dp       // screen margin outside the stacks
private val LABELS = mapOf(Tab.SEARCH to R.string.vod_xcube_blade_search, Tab.LIVE to R.string.vod_xcube_blade_live, Tab.MOVIES to R.string.vod_xcube_blade_movies,
    Tab.SERIES to R.string.vod_xcube_blade_series, Tab.RECORDINGS to R.string.vod_xcube_blade_recordings, Tab.SETTINGS to R.string.vod_xcube_blade_settings)
private val TITLES = mapOf(Tab.SEARCH to R.string.vod_nav_search, Tab.LIVE to R.string.vod_nav_live, Tab.MOVIES to R.string.vod_nav_movies,
    Tab.SERIES to R.string.vod_nav_series, Tab.RECORDINGS to R.string.vod_nav_recordings, Tab.SETTINGS to R.string.vod_nav_settings)

/** The blade card's focus: Left/Right switch blade, and it is the "menu" the app's root Back focuses. */
class XcubeCard(val focus: FocusRequester, val onBlade: (Int) -> Unit, val onFocused: (Boolean) -> Unit,
                /** This blade was just switched to: the card has focus, the blade mustn't move it to its content. */
                val arrivedBySwitch: Boolean = false)

/** Makes this the blade card: focusable via [XcubeCard.focus], Left/Right step to the next blade, except while [typing]
 *  (the Search blade's field: Left/Right then move the text cursor). */
fun Modifier.bladeCard(c: XcubeCard, typing: () -> Boolean = { false }): Modifier = focusRequester(c.focus)
    .onFocusChanged { c.onFocused(it.hasFocus) }
    .onPreviewKeyEvent { e ->
        if (e.type != KeyEventType.KeyDown || typing()) return@onPreviewKeyEvent false
        // A held or slightly long press repeats: one blade per press, not a run through several.
        val step = e.nativeKeyEvent.repeatCount == 0
        when (e.key) {
            Key.DirectionLeft -> { if (step) c.onBlade(-1); true }
            Key.DirectionRight -> { if (step) c.onBlade(1); true }
            else -> false
        }
    }

/**
 * Xcube 180 frame (spec §6A.7): the selected blade is a green centre panel titled top-left; the others stack as
 * silver edges with lowercase labels. Background rasterised once per (size, blade); content slides in on change.
 */
@Composable
fun XcubeFrame(selected: Tab, onBlade: (Tab) -> Unit, cardFocus: FocusRequester, onCardFocused: (Boolean) -> Unit,
               tabs: List<Tab> = org.imdtv.iptvtv.ui.visibleTabs(false), content: @Composable (XcubeCard) -> Unit) {
    val colors = LocalTheme.current.colors
    val focusManager = LocalFocusManager.current
    val (left, right) = bladeSides(selected, tabs)
    var cardFocused by remember { mutableStateOf(false) }
    var focusCardAfterSwitch by remember { mutableStateOf(false) }
    var lastBlade by remember { mutableStateOf(selected) }
    val card = remember(selected, tabs) {
        XcubeCard(cardFocus, onBlade = { d ->
            val to = nextBlade(selected, d, tabs)
            // Settings focuses its first group itself; also asking for its card made the landing random.
            if (to != selected) { focusCardAfterSwitch = to != Tab.SETTINGS; onBlade(to) }
        }, onFocused = { cardFocused = it; onCardFocused(it) }, arrivedBySwitch = focusCardAfterSwitch)
    }
    // Slide (±60dp) and fade the new blade's content in: graphicsLayer only (spec §6A.2).
    val anim = remember { Animatable(1f) }
    val dir = if (tabs.indexOf(selected) >= tabs.indexOf(lastBlade)) 1 else -1
    LaunchedEffect(selected) {
        if (selected != lastBlade) { anim.snapTo(0f); anim.animateTo(1f, tween(200)) }
        lastBlade = selected
        if (focusCardAfterSwitch) { focusCardAfterSwitch = false; withFrameNanos { }; runCatching { cardFocus.requestFocus() } }
    }
    // Settings at its group list: Back goes to the blade card (Settings' own Back handling is registered later and wins).
    BackHandler(enabled = selected == Tab.SETTINGS && !cardFocused) { runCatching { cardFocus.requestFocus() } }

    Box(Modifier.fillMaxSize().drawWithCache {
        val img = xcubeBackground(colors, this, size, left.size, right.size)
        onDrawBehind { drawImage(img) }
    }) {
        // Each label is centred on the visible strip of its blade.
        left.forEachIndexed { i, t -> BladeLabel(stringResource(LABELS.getValue(t)), Modifier.offset(x = EDGE + BLADE * i + BLADE / 2 - 40.dp, y = 70.dp).onTouchTap { onBlade(t) }) }
        right.forEachIndexed { i, t ->
            BladeLabel(stringResource(LABELS.getValue(t)), Modifier.align(Alignment.TopEnd).offset(x = -(EDGE + BLADE * (right.size - i - 1) + BLADE / 2 - 40.dp), y = 70.dp).onTouchTap { onBlade(t) })
        }
        Box(Modifier.fillMaxSize().padding(start = EDGE + BLADE * left.size + 14.dp, end = EDGE + BLADE * right.size + 14.dp, top = 10.dp, bottom = 10.dp)) {
            Text(stringResource(TITLES.getValue(selected)), fontSize = 20.sp, color = Color.White, modifier = Modifier.padding(start = 24.dp, top = 14.dp))
            // Left/Right that nothing in the blade used and that can't move focus further switch blade.
            Box(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 48.dp, bottom = 34.dp)
                .onKeyEvent { e ->
                    val d = when (e.key) { Key.DirectionLeft -> -1; Key.DirectionRight -> 1; else -> 0 }
                    if (d == 0 || e.type != KeyEventType.KeyDown) return@onKeyEvent false
                    if (e.nativeKeyEvent.repeatCount > 0) return@onKeyEvent true // holding a key stops at the edge, not run through blades
                    if (!focusManager.moveFocus(if (d < 0) FocusDirection.Left else FocusDirection.Right)) card.onBlade(d)
                    true
                }
                .graphicsLayer { alpha = anim.value; translationX = (1 - anim.value) * 60.dp.toPx() * dir }) {
                content(card)
                if (selected == Tab.SETTINGS) SettingsCard(card) // Settings has no card of its own; drawn over it
            }
            Text(stringResource(R.string.vod_xcube_back), fontSize = 13.sp, color = Color.White, modifier = Modifier.align(Alignment.BottomStart).padding(start = 24.dp, bottom = 10.dp))
            Text(stringResource(R.string.vod_xcube_select_ok), fontSize = 13.sp, color = Color.White, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 10.dp))
        }
    }
}

@Composable
private fun BladeLabel(text: String, modifier: Modifier) {
    // Rotated a quarter turn, reading top to bottom like the reference.
    Box(modifier.size(80.dp, 160.dp), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 14.sp, color = Color(0xFF5E6368), maxLines = 1, softWrap = false,
            modifier = Modifier.wrapContentSize(unbounded = true).rotate(90f))
    }
}

/** A small focusable chip standing in for the card on the Settings blade (Left/Right switch blade from here). */
@Composable
private fun SettingsCard(card: XcubeCard) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
        FocusBox(onClick = {}, modifier = Modifier.bladeCard(card)) { f ->
            Text(stringResource(R.string.vod_xcube_blades), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (f) Color(0xFF1A3A0E) else Color.White,
                modifier = Modifier.background(if (f) Color(0xFFE8F5D8) else Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp))
        }
    }
}

private fun xcubeBackground(colors: ThemeColors, density: Density, size: Size, left: Int, right: Int): ImageBitmap {
    val img = ImageBitmap(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1))
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(img), size) {
        drawRect(Color(0xFFDADDE0))
        val blade = BLADE.toPx(); val edge = EDGE.toPx(); val r = CornerRadius(26.dp.toPx())
        val silver = Brush.horizontalGradient(listOf(Color(0xFFE8EAEC), Color(0xFFB8BCC0)))
        val rim = Color(0xFF8F9499); val rimW = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx())
        for (i in 0 until left) {
            drawRoundRect(silver, Offset(edge + blade * i, 6.dp.toPx()), Size(blade * 2, size.height - 12.dp.toPx()), r)
            drawRoundRect(rim, Offset(edge + blade * i, 6.dp.toPx()), Size(blade * 2, size.height - 12.dp.toPx()), r, style = rimW)
        }
        for (i in right - 1 downTo 0) { // outermost first, so each inner blade's rim sits on top like the left stack
            val x = size.width - edge - blade * (right - i)
            drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFFB8BCC0), Color(0xFFE8EAEC)), startX = x - blade, endX = x + blade),
                Offset(x - blade, 6.dp.toPx()), Size(blade * 2, size.height - 12.dp.toPx()), r)
            drawRoundRect(rim, Offset(x - blade, 6.dp.toPx()), Size(blade * 2, size.height - 12.dp.toPx()), r, style = rimW)
        }
        val x0 = edge + blade * left; val x1 = size.width - edge - blade * right
        val panel = Size(x1 - x0, size.height - 12.dp.toPx())
        drawRoundRect(Brush.verticalGradient(colors.bg, startY = 0f, endY = size.height), Offset(x0, 6.dp.toPx()), panel, r)
        // Soft light blobs and a silver inner rim.
        listOf(Triple(.3f, .35f, .25f), Triple(.7f, .6f, .3f), Triple(.5f, .9f, .2f)).forEach { (fx, fy, fr) ->
            val c = Offset(x0 + panel.width * fx, size.height * fy)
            drawCircle(Brush.radialGradient(listOf(Color(0x40FFFFFF), Color.Transparent), c, panel.width * fr), panel.width * fr, c)
        }
        // Slight blade border like the reference: a grey outer line and a light inner rim.
        drawRoundRect(Color(0xFF8F9499), Offset(x0, 6.dp.toPx()), panel, r, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
        val i = 2.dp.toPx()
        drawRoundRect(Color(0x99FFFFFF), Offset(x0 + i, 6.dp.toPx() + i), Size(panel.width - 2 * i, panel.height - 2 * i), CornerRadius(r.x - i),
            style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
    }
    return img
}
