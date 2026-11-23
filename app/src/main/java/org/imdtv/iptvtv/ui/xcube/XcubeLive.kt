package org.imdtv.iptvtv.ui.xcube

import androidx.compose.foundation.background
import org.imdtv.iptvtv.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import org.imdtv.iptvtv.data.catalogue.displayName
import org.imdtv.iptvtv.data.db.ChannelEntity
import org.imdtv.iptvtv.data.repo.GuideProgramme
import org.imdtv.iptvtv.data.settings.LogoKind
import org.imdtv.iptvtv.ui.ChannelLogo
import org.imdtv.iptvtv.ui.FocusBox
import org.imdtv.iptvtv.ui.clockTime
import org.imdtv.iptvtv.ui.live.CategoryRail
import org.imdtv.iptvtv.ui.live.GuideGrid
import org.imdtv.iptvtv.ui.live.GuideMenus
import org.imdtv.iptvtv.ui.live.LiveViewModel
import org.imdtv.iptvtv.ui.live.Preview
import org.imdtv.iptvtv.ui.live.PreviewEdge
import org.imdtv.iptvtv.ui.live.XCUBE_GRID
import org.imdtv.iptvtv.ui.live.backOpensRail
import org.imdtv.iptvtv.ui.live.heroBadges
import org.imdtv.iptvtv.ui.live.rememberGuideState

private val CARD_TEXT = Color(0xFF1A3A0E)

/** Xcube 180 Live TV blade (spec §6A.7): channel card, preview with description, guide below; rail from Left. */
@Composable
fun XcubeLive(vm: LiveViewModel, card: XcubeCard, onFullScreen: (ChannelEntity) -> Unit) {
    val state = rememberGuideState(vm)
    androidx.compose.runtime.LaunchedEffect(vm.openRail) { if (vm.openRail) { vm.openRail = false; state.railOpen = true } }
    // Switched to: the guide's own refocus would take focus from the card a frame later (Left then opened the rail).
    remember { if (card.arrivedBySwitch) state.heldFocus = card.focus }
    val channels by vm.channels.collectAsState()
    val programmes by vm.programmes.collectAsState()
    val now by vm.now.collectAsState()
    val focused by vm.focused.collectAsState()
    val strip by vm.strip.collectAsState()
    val logoSource by vm.logoSource.collectAsState()
    val previewingId by vm.previewingId.collectAsState()
    val formats by vm.player.formats.collectAsState()
    var screen by remember { mutableStateOf(Size.Zero) }
    val ch = channels.find { it.id == focused?.id } ?: focused
    val progs = ch?.let { programmes[it.id] }.orEmpty()
    Box(Modifier.fillMaxSize().onSizeChanged { screen = Size(it.width.toFloat(), it.height.toFloat()) }) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(205.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                FocusBox(onClick = { ch?.let { state.onOk(it, onFullScreen) } }, modifier = Modifier.bladeCard(card)) { f ->
                    ChannelCard(ch, ch?.displayName(strip).orEmpty(), progs, now, heroBadges(focused?.id, previewingId, formats), logoSource,
                        Modifier.width(360.dp).border(2.dp, if (f) Color.White else Color.Transparent, RoundedCornerShape(10.dp)))
                }
                Column(Modifier.weight(1f)) {
                    Preview(vm, visible = state.previewing, screen = screen, edge = PreviewEdge.NONE,
                        modifier = Modifier.size(300.dp, 150.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x40000000)))
                    val cur = progs.firstOrNull { it.start <= now && now < it.stop }
                    Text(cur?.desc.orEmpty(), fontSize = 12.sp, lineHeight = 16.sp, color = Color.White, maxLines = 3,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                }
            }
            GuideGrid(vm, state, XCUBE_GRID, onFullScreen, onLeftAtStart = { state.railOpen = true },
                onUpAtTop = { runCatching { card.focus.requestFocus() } },
                modifier = Modifier.backOpensRail(vm, state).padding(top = 6.dp).fillMaxSize().onFocusChanged { if (it.hasFocus) state.heldFocus = null })
        }
        if (state.railOpen) CategoryRail(vm, state, card.focus)
        GuideMenus(vm, state)
    }
}

/** The gamer-card stand-in: gold header with the channel name, logo tile, Now/progress/badges, Next. */
@Composable
fun ChannelCard(ch: ChannelEntity?, name: String, progs: List<GuideProgramme>, now: Long, badges: List<String>,
                logoSource: List<LogoKind>, modifier: Modifier) {
    val cur = progs.firstOrNull { it.start <= now && now < it.stop }
    val next = progs.firstOrNull { it.start >= (cur?.stop ?: now) }
    Column(modifier.clip(RoundedCornerShape(10.dp)).background(Brush.verticalGradient(listOf(Color(0xFFF4F8F0), Color(0xFFD5E8C8))))) {
        Text(name, fontSize = 16.sp * org.imdtv.iptvtv.ui.LocalTitleScale.current, fontWeight = FontWeight.SemiBold, color = CARD_TEXT, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Color(0xFFE9D58A), Color(0xFFF8F3D0))))
                .padding(horizontal = 12.dp, vertical = 5.dp))
        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(70.dp, 52.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF3A4A30)), contentAlignment = Alignment.Center) {
                if (ch != null) ChannelLogo(ch, logoSource, Modifier.fillMaxSize().padding(4.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(cur?.title ?: stringResource(R.string.vod_no_guide_info), fontSize = 14.sp * org.imdtv.iptvtv.ui.LocalTitleScale.current, fontWeight = FontWeight.SemiBold, color = CARD_TEXT,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (cur != null) {
                    Text("${clockTime(cur.start)} – ${clockTime(cur.stop)}", fontSize = 12.sp, color = CARD_TEXT.copy(alpha = .8f))
                    val f = ((now - cur.start).toFloat() / (cur.stop - cur.start)).coerceIn(0f, 1f)
                    Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x332E8B1E))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(f).background(Color(0xFF2E8B1E)))
                    }
                }
                Row(Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    badges.forEach { b ->
                        Text(b, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = CARD_TEXT,
                            modifier = Modifier.border(1.dp, CARD_TEXT.copy(alpha = .6f), RoundedCornerShape(3.dp)).padding(horizontal = 4.dp, vertical = 1.dp))
                    }
                }
            }
        }
        next?.let {
            Text(stringResource(R.string.vod_next_programme, clockTime(it.start), it.title), fontSize = 12.sp, color = CARD_TEXT.copy(alpha = .8f), maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp))
        }
    }
}
