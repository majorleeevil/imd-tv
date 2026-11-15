package org.imdtv.iptvtv.ui.retro

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.imdtv.iptvtv.data.db.ChannelEntity
import org.imdtv.iptvtv.ui.AppLogo
import org.imdtv.iptvtv.ui.clockTime
import org.imdtv.iptvtv.ui.live.GuideGrid
import org.imdtv.iptvtv.ui.live.GuideMenus
import org.imdtv.iptvtv.ui.live.LiveViewModel
import org.imdtv.iptvtv.ui.live.RETRO_GRID
import org.imdtv.iptvtv.ui.live.rememberGuideState
import org.imdtv.iptvtv.ui.theme.LocalTheme

private val DATE = DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)

/**
 * Celestial Retro guide (spec §6A.5): navy band with logo, time/date and the category in caps; full-width grid,
 * no preview or description; OK goes full screen. Back to the menu is handled by the app's root Back.
 */
@Composable
fun RetroGuideScreen(vm: LiveViewModel, onFullScreen: (ChannelEntity) -> Unit) {
    val state = rememberGuideState(vm, previews = false)
    val entry by vm.entry.collectAsState()
    val now by vm.now.collectAsState()
    val colors = LocalTheme.current.colors
    // No preview in Retro: coming back from full screen must not leave the stream playing unseen.
    LaunchedEffect(Unit) { vm.player.stop() }
    Box(Modifier.fillMaxSize().drawWithCache {
        val img = retroSky(colors, this, size)
        onDrawBehind { drawImage(img) }
    }) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(92.dp).background(colors.panel).padding(start = 50.dp, top = 12.dp),
                verticalAlignment = Alignment.Top) {
                AppLogo(Modifier.size(58.dp))
                Column(Modifier.padding(start = 18.dp)) {
                    Text(if (vm.settings.collectAsState().value.showDateTime) "${clockTime(now)} ${DATE.format(Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()))}" else "",
                        fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(entry?.title.orEmpty().uppercase(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                }
            }
            GuideGrid(vm, state, RETRO_GRID, onFullScreen, onLeftAtStart = {},
                modifier = Modifier.weight(1f).padding(start = 60.dp, end = 35.dp, top = 8.dp))
            Row(Modifier.fillMaxWidth().height(40.dp).background(colors.panel).padding(start = 50.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Press ", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("SELECT", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.onFocus,
                    modifier = Modifier.background(colors.focus, RoundedCornerShape(2.dp)).padding(horizontal = 4.dp))
                Text(" to view", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
        GuideMenus(vm, state)
    }
}
