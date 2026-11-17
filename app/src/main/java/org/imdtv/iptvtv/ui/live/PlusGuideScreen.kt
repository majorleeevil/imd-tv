package org.imdtv.iptvtv.ui.live

import org.imdtv.iptvtv.ui.ChannelLogo
import androidx.compose.ui.draw.clip
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.imdtv.iptvtv.data.catalogue.displayName
import org.imdtv.iptvtv.data.repo.RailEntry
import org.imdtv.iptvtv.ui.AppLogo
import org.imdtv.iptvtv.ui.clockTime
import org.imdtv.iptvtv.ui.dayDate
import org.imdtv.iptvtv.ui.pingLabel
import org.imdtv.iptvtv.ui.theme.LocalTheme


/** Celestial Plus (spec §6A.4): title band, description + small preview, category tabs over a boxed grid. */
@Composable
fun PlusGuideScreen(vm: LiveViewModel, nav: @Composable () -> Unit, onFullScreen: (org.imdtv.iptvtv.data.db.ChannelEntity) -> Unit, pingMs: Int? = null) {
    val state = rememberGuideState(vm)
    val rail by vm.rail.collectAsState()
    val entry by vm.entry.collectAsState()
    val channels by vm.channels.collectAsState()
    val programmes by vm.programmes.collectAsState()
    val now by vm.now.collectAsState()
    val focused by vm.focused.collectAsState()
    val strip by vm.strip.collectAsState()
    val previewingId by vm.previewingId.collectAsState()
    val formats by vm.player.formats.collectAsState()
    val logoSource by vm.logoSource.collectAsState()
    val colors = LocalTheme.current.colors
    val focusManager = LocalFocusManager.current
    val tabsFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var navFocused by remember { mutableStateOf(false) }
    val toTabs = { state.heldFocus = tabsFocus; runCatching { tabsFocus.requestFocus() }; Unit }
    // Back in the guide goes to the category tabs first; Back there falls through to the app (focus the nav).
    val backToFull = vm.settings.collectAsState().value.backToFullScreen && previewingId != null // then Back is the app's: full screen
    BackHandler(enabled = state.heldFocus == null && !navFocused && state.menu == null && !backToFull) { toTabs() }

    var screen by remember { mutableStateOf(Size.Zero) }
    Box(Modifier.fillMaxSize().onSizeChanged { screen = Size(it.width.toFloat(), it.height.toFloat()) }.drawWithCache {
        val img = backgroundBitmap(colors, this, size, Offset.Zero, size)
        onDrawBehind { drawImage(img) }
    }) {
        // Title band: "TV guide" (the nav row while it has focus), logo centre, clock and date right.
        Box(Modifier.padding(start = 40.dp, top = 14.dp)) {
            if (!navFocused) Text("TV guide", fontSize = 17.sp, color = Color.White, modifier = Modifier.padding(top = 4.dp))
            // Always composed (Back at the top level focuses it), only visible while focused.
            Box(Modifier.graphicsLayer { alpha = if (navFocused) 1f else 0f }
                .onFocusChanged { navFocused = it.hasFocus; if (it.hasFocus) state.heldFocus = tabsFocus }
                .onPreviewKeyEvent { e -> if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionDown) { toTabs(); true } else false }) { nav() }
        }
        if (!navFocused) AppLogo(Modifier.align(Alignment.TopCenter).padding(top = 6.dp).size(48.dp))
        val dateTime = vm.settings.collectAsState().value.showDateTime
        val status = listOfNotNull("${clockTime(now)} ${dayDate(now)}".takeIf { dateTime }, pingMs?.let(::pingLabel)).joinToString("   ")
        if (!navFocused && status.isNotEmpty()) Text(status, fontSize = 12.sp,
            color = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(top = 16.dp, end = 40.dp))

        // Description left, badges right-aligned on the title line.
        val ch = channels.find { it.id == focused?.id } ?: focused
        if (ch != null) {
            val cur = programmes[ch.id].orEmpty().firstOrNull { it.start <= now && now < it.stop }
            Row(Modifier.padding(start = 40.dp, top = 52.dp).width(570.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(70.dp, 50.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x33FFFFFF)), contentAlignment = Alignment.Center) {
                ChannelLogo(ch, logoSource, Modifier.fillMaxSize().padding(4.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (cur != null) "${clockTime(cur.start)}  ${cur.title}" else ch.displayName(strip), fontSize = 15.sp,
                        color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    heroBadges(focused?.id, previewingId, formats).forEach { b ->
                        Text(b, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                            modifier = Modifier.padding(start = 5.dp).border(1.dp, Color(0xB3FFFFFF), RoundedCornerShape(3.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp))
                    }
                }
                Text(cur?.desc ?: if (cur == null) "No guide information" else "", fontSize = 13.sp, lineHeight = 18.sp,
                    color = Color.White.copy(alpha = .92f), maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
            }
        }
        Preview(vm, visible = state.previewing, screen = screen, edge = PreviewEdge.NONE,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 46.dp, end = 40.dp).size(300.dp, 169.dp).background(Color.Black))

        if (rail?.isEmpty() == true) {
            Text("All live categories are hidden. Show them again in Settings › Channels & categories.", fontSize = 16.sp,
                color = Color.White, modifier = Modifier.padding(start = 40.dp, top = 300.dp))
        }
        // The tabs and grid panel run to both sides and the bottom (user, 2026-11-12).
        Column(Modifier.padding(top = 232.dp).fillMaxSize()) {
            val providerKeys = rail.orEmpty().filter { it.entry is RailEntry.Provider }.map { it.entry.key }
            CategoryTabs(
                rail.orEmpty(), entry?.key, tabsFocus,
                onPick = { vm.select(it) },
                onDown = { scope.launch { state.enterFromTabs(channels.map { it.id }) } },
                onUp = { focusManager.moveFocus(FocusDirection.Up) },
                onLongPress = { e -> if (e is RailEntry.Provider) state.menu = GuideMenu.Category(e.category) },
                move = state.railMove,
                onMove = { d -> state.railMove?.let { vm.moveCategory(it.cat, d) } },
                onMoveEnd = { cancel -> endRailMove(vm, state, providerKeys, cancel) },
                modifier = Modifier.fillMaxWidth().height(26.dp),
            )
            // Panel full-bleed, content inside the TV-safe margin (overscan hides the outer edge).
            Column(Modifier.fillMaxSize().background(colors.panel).padding(start = 24.dp, end = 16.dp, top = 4.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                GuideGrid(vm, state, PLUS_GRID, onFullScreen, onLeftAtStart = toTabs, onUpAtTop = toTabs, modifier = Modifier.weight(1f))
                Text("Press SELECT to view", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
        GuideMenus(vm, state)
    }
}
