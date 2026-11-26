package org.imdtv.iptvtv.ui.live

import org.imdtv.iptvtv.ui.hasCatchUp
import androidx.compose.ui.res.stringResource
import org.imdtv.iptvtv.R

import org.imdtv.iptvtv.ui.theme.glass
import org.imdtv.iptvtv.data.settings.ThemeId
import org.imdtv.iptvtv.ui.theme.LocalTheme

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import org.imdtv.iptvtv.data.settings.LogoKind
import org.imdtv.iptvtv.ui.ChannelLogo
import org.imdtv.iptvtv.data.catalogue.displayName
import org.imdtv.iptvtv.data.db.ChannelEntity
import org.imdtv.iptvtv.data.repo.GuideProgramme
import org.imdtv.iptvtv.player.badges
import org.imdtv.iptvtv.ui.FocusBox
import org.imdtv.iptvtv.ui.clockTime

/**
 * Info overlay over live full screen (spec §4.2): now/next, then a row of TV guide, Settings and Restart cards and the
 * channels watched before. Focus starts on TV guide, so OK, OK still opens the guide. Celestial Plus (#2) has a smaller
 * bar and small icon buttons (Settings, TV guide, Restart) set apart by a line. No play/pause: a paused live stream outlives the player's buffer (~50 s), then the provider drops it.
 */
@Composable
fun LiveOverlay(vm: LiveViewModel, onGuide: () -> Unit, onPick: (ChannelEntity) -> Unit, onMenuOpen: (Boolean) -> Unit = {}) {
    val entry by vm.entry.collectAsState()
    val now by vm.now.collectAsState()
    val focused by vm.focused.collectAsState()
    val channels by vm.channels.collectAsState()
    val programmes by vm.programmes.collectAsState()
    val recents by vm.recents.collectAsState()
    val recentProgs by vm.recentProgrammes.collectAsState()
    val strip by vm.strip.collectAsState()
    val logoSource by vm.logoSource.collectAsState()
    val formats by vm.player.formats.collectAsState()
    val ch = channels.find { it.id == focused?.id } ?: focused ?: return
    val progs = programmes[ch.id] ?: recentProgs[ch.id].orEmpty()
    val cur = progs.firstOrNull { it.start <= now && now < it.stop }
    val next = progs.firstOrNull { it.start >= (cur?.stop ?: now) }
    val progress = cur?.let { ((now - it.start).toFloat() / (it.stop - it.start)).coerceIn(0f, 1f) }

    val plus = LocalTheme.current.id == ThemeId.CELESTIAL_PLUS
    val small = true
    val guideFocus = remember { FocusRequester() }
    val menus = remember(vm) { GuideState(vm, 0, androidx.compose.foundation.lazy.LazyListState(), false, previews = false) }
    // The player menu opens above the Settings button, not mid-screen (#28): small buttons put it first, cards second.
    val anchorX = if (small) 48.dp else 48.dp + 190.dp + 10.dp
    val anchor = with(androidx.compose.ui.platform.LocalDensity.current) { androidx.compose.ui.unit.IntOffset(anchorX.roundToPx(), -(ROW_BOTTOM + ROW_HEIGHT + 8.dp).roundToPx()) }
    androidx.compose.runtime.CompositionLocalProvider(org.imdtv.iptvtv.ui.LocalMenuAnchor provides anchor) { GuideMenus(vm, menus) }
    // Keys in a menu (a dialog) don't reach the full-screen timer: hold the overlay open while one shows.
    val menuOpen = menus.menu != null
    LaunchedEffect(menuOpen) { onMenuOpen(menuOpen) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { onMenuOpen(false) } }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { guideFocus.requestFocus() } }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x99000000), .18f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(.45f to Color.Transparent, .75f to Color(0xD9000000), 1f to Color(0xF2000000))))
        Text(entry?.title.orEmpty(), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 48.dp, top = 28.dp))
        Text(clockTime(now), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 48.dp, top = 26.dp))

        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
            // Liquid Glass: the info sits on smoked glass (spec §6A.6); other themes keep the plain scrim.
            val liquid = LocalTheme.current.id == ThemeId.LIQUID_GLASS
            Row(if (liquid) Modifier.padding(horizontal = 36.dp).glass(RoundedCornerShape(14.dp), smoked = true).padding(12.dp)
                else Modifier.padding(horizontal = 48.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(84.dp, 60.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x33FFFFFF)), contentAlignment = Alignment.Center) {
                    ChannelLogo(ch, logoSource, Modifier.fillMaxSize().padding(6.dp), fontSize = 18.sp)
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(cur?.title ?: stringResource(R.string.live_no_guide_info), fontSize = 24.sp * org.imdtv.iptvtv.ui.LocalTitleScale.current, fontWeight = FontWeight.SemiBold, color = Color.White,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (cur != null && progress != null) {
                            Text("${clockTime(cur.start)} – ${clockTime(cur.stop)}", fontSize = 13.sp, color = Color.White)
                            Box(Modifier.size(if (plus) 200.dp else 120.dp, if (plus) 4.dp else 3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x33FFFFFF))) {
                                Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(if (plus) LocalTheme.current.colors.focus else Color.White))
                            }
                            Text(stringResource(R.string.live_overlay_min_left, ((cur.stop - now + 59_999) / 60_000).toInt()), fontSize = 13.sp, color = LocalTheme.current.colors.muted)
                        }
                    }
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val number = (ch.customNumber ?: ch.number)?.takeIf { org.imdtv.iptvtv.ui.LocalShowNumbers.current }?.let { "$it  " }.orEmpty()
                        Text(number + ch.displayName(strip), fontSize = 14.sp * org.imdtv.iptvtv.ui.LocalTitleScale.current, fontWeight = FontWeight.Medium, color = Color.White, maxLines = 1)
                        if (ch.hasCatchUp) CatchUpClock(Color.White)
                        badges(formats).forEach { b ->
                            Text(b, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                                modifier = Modifier.border(1.dp, Color(0xB3FFFFFF), RoundedCornerShape(3.dp)).padding(horizontal = 5.dp, vertical = 1.dp))
                        }
                    }
                    cur?.desc?.let {
                        Text(it, fontSize = 13.sp, lineHeight = 18.sp, color = Color(0xFFDDE3F0), maxLines = if (plus) 2 else 3, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 6.dp).fillMaxWidth(.75f))
                    }
                    next?.let {
                        Text(stringResource(R.string.live_overlay_next, clockTime(it.start), it.title), fontSize = 13.sp, color = LocalTheme.current.colors.muted, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
            if (!plus) Box(Modifier.padding(top = 14.dp).fillMaxWidth().height(2.dp).background(Color(0x33FFFFFF))) {
                if (progress != null) Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(Color.White))
            }
            LazyRow(
                Modifier.padding(top = if (plus) 16.dp else 14.dp, bottom = ROW_BOTTOM).height(ROW_HEIGHT),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 48.dp),
            ) {
                val settingsMenu = { menus.menu = GuideMenu.Player(ch) }
                // Only a small cog (user, 26 Nov): subtitles, aspect, record, catch-up, favourites. The guide is Back; Restart is in Catch up.
                item(key = "settings") { OverlayCog(stringResource(R.string.live_overlay_settings), Modifier.focusRequester(guideFocus), settingsMenu) }
                // Small buttons aren't channels: a line sets them apart from the history (#31).
                if (small && recents.isNotEmpty()) item(key = "line") { Box(Modifier.padding(horizontal = 4.dp, vertical = 12.dp).width(1.dp).fillMaxHeight().background(Color(0x4DFFFFFF))) }
                items(recents, key = { it.id }) { r ->
                    val rp = recentProgs[r.id].orEmpty().firstOrNull { it.start <= now && now < it.stop }
                    OverlayCard(onClick = { onPick(r) }) { f -> RecentCard(r, r.displayName(strip), rp, f, logoSource) }
                }
            }
        }
    }
}

private val ROW_HEIGHT = 78.dp
private val ROW_BOTTOM = 24.dp

/** The overlay's settings button: an icon only, [label] for screen readers. */
@Composable
private fun OverlayCog(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OverlayCard(onClick, modifier, width = 56.dp) { f ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            androidx.tv.material3.Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_tab_settings), label,
                tint = if (f) LocalTheme.current.colors.onFocus else Color.White, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun OverlayCard(onClick: () -> Unit, modifier: Modifier = Modifier, width: androidx.compose.ui.unit.Dp = 190.dp, content: @Composable (Boolean) -> Unit) {
    FocusBox(onClick = onClick, modifier = modifier.width(width).fillMaxHeight()) { f ->
        val smoked = !f && LocalTheme.current.id == ThemeId.LIQUID_GLASS
        Box(Modifier.fillMaxSize().then(if (smoked) Modifier.glass(RoundedCornerShape(6.dp), smoked = true)
            else Modifier.clip(RoundedCornerShape(6.dp)).background(if (f) LocalTheme.current.colors.focus else Color(0x33FFFFFF)))) { content(f) }
    }
}

@Composable
private fun RecentCard(ch: ChannelEntity, name: String, cur: GuideProgramme?, focused: Boolean, logoSource: List<LogoKind>) {
    val fg = if (focused) LocalTheme.current.colors.onFocus else Color.White
    Row(Modifier.fillMaxSize().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp, 34.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x33FFFFFF)), contentAlignment = Alignment.Center) {
            ChannelLogo(ch, logoSource, Modifier.fillMaxSize().padding(3.dp), fontSize = 12.sp)
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(((ch.customNumber ?: ch.number)?.takeIf { org.imdtv.iptvtv.ui.LocalShowNumbers.current }?.let { "$it  " }.orEmpty()) + name, fontSize = 12.sp * org.imdtv.iptvtv.ui.LocalTitleScale.current, fontWeight = FontWeight.SemiBold,
                color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(cur?.title ?: stringResource(R.string.live_no_guide_info), fontSize = 11.sp * org.imdtv.iptvtv.ui.LocalTitleScale.current, color = fg.copy(alpha = .75f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
