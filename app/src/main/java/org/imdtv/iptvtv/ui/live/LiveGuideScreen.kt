package org.imdtv.iptvtv.ui.live

import org.imdtv.iptvtv.ui.theme.glass
import org.imdtv.iptvtv.ui.theme.LocalGlassBackdrop
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import org.imdtv.iptvtv.ui.theme.LocalTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.drawWithCache
import androidx.tv.material3.Text
import org.imdtv.iptvtv.ui.Spinner
import org.imdtv.iptvtv.data.catalogue.displayName
import org.imdtv.iptvtv.data.db.ChannelEntity

@Composable
fun LiveGuideScreen(vm: LiveViewModel, nav: @Composable () -> Unit, onFullScreen: (ChannelEntity) -> Unit) {
    val state = rememberGuideState(vm)
    val rail by vm.rail.collectAsState()
    val entry by vm.entry.collectAsState()
    val updating by vm.guideUpdating.collectAsState()
    val rows = vm.settings.collectAsState().value.guideRows

    var screen by remember { mutableStateOf(Size.Zero) }
    val colors = LocalTheme.current.colors // read here so a theme change re-rasterises the cached background
    val backdrop = LocalGlassBackdrop.current // Liquid Glass: same layout, glass panels over its backdrop
    val inset = if (backdrop != null) 12.dp else 0.dp
    Box(Modifier.fillMaxSize().onSizeChanged { screen = Size(it.width.toFloat(), it.height.toFloat()) }.drawWithCache {
        // The Chromecast GPU is the bottleneck: rasterise the full-screen gradients once, then just blit them.
        val img = backdrop?.sharp ?: backgroundBitmap(colors, this, size, Offset.Zero, size)
        onDrawBehind { drawImage(img) }
    }) {
        Preview(vm, visible = state.previewing, screen = screen, edge = if (backdrop != null) PreviewEdge.RIM else PreviewEdge.FADE,
            // Liquid Glass: a smaller framed preview clear of the description panel.
            // Top and height match the hero panel, so the nav pill never overlaps it.
            modifier = Modifier.align(Alignment.TopEnd).then(if (backdrop != null) Modifier.padding(top = 80.dp, end = 30.dp).size(412.dp, 232.dp) else Modifier))
        Box(Modifier.padding(start = 24.dp, top = 22.dp)) { nav() }
        GuideHero(vm, Modifier.padding(start = 24.dp, top = 80.dp).width(450.dp)
            // Liquid Glass: a fixed-size panel, so moving focus doesn't re-rasterise it for each description length.
            .then(if (backdrop != null) Modifier.height(232.dp) else Modifier).glass().padding(inset))

        if (rail?.isEmpty() == true) {
            Text("All live categories are hidden. Show them again in Settings › Channels & categories.", fontSize = 16.sp,
                color = Color.White, modifier = Modifier.padding(start = 24.dp, top = 380.dp))
        }
        Row(Modifier.padding(start = 24.dp, top = if (backdrop != null) 302.dp else 286.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "◀ ${entry?.title.orEmpty()}", fontSize = 11.sp, color = Color.White, maxLines = 1,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(colors.cellDim).padding(horizontal = 7.dp, vertical = 2.dp),
            )
            Text("  Press left for categories", fontSize = 11.sp, color = colors.muted)
            if (updating) {
                Spacer(Modifier.width(16.dp)); Spinner(size = 11.dp, color = colors.muted)
                Text("  Updating guide…", fontSize = 11.sp, color = colors.muted)
            }
        }

        // Glass and Liquid: the grid runs off the right edge and stops 12dp above the bottom, so the last row's border shows; GuideGrid splits it into 5–9 whole rows.
        val grid = (if (backdrop != null) LIQUID_GRID else GLASS_GRID).copy(gap = if (rows > 5) 2.dp else 4.dp)
        GuideGrid(vm, state, grid, onFullScreen, onLeftAtStart = { state.railOpen = true }, rows = rows,
            modifier = Modifier.padding(start = 24.dp, top = if (backdrop != null) 320.dp else 306.dp, bottom = 12.dp).fillMaxSize().glass(blur = false).padding(inset)) // no backdrop patch on the grid: the performance gate (ledger)
        if (state.railOpen) CategoryRail(vm, state)
        GuideMenus(vm, state)
    }
}

/** The hero reads focus, guide and stream format itself, so a key press recomposes only it and the rows, not the screen. */
@Composable
private fun GuideHero(vm: LiveViewModel, modifier: Modifier) {
    val channels by vm.channels.collectAsState()
    val programmes by vm.programmes.collectAsState()
    val now by vm.now.collectAsState()
    val focused by vm.focused.collectAsState()
    val strip by vm.strip.collectAsState()
    val logoSource by vm.logoSource.collectAsState()
    val previewingId by vm.previewingId.collectAsState()
    val formats by vm.player.formats.collectAsState()
    val hero = channels.find { it.id == focused?.id } ?: focused // the list has the renamed copy
    Hero(hero, hero?.displayName(strip).orEmpty(), programmes[focused?.id].orEmpty(), now, logoSource, heroBadges(focused?.id, previewingId, formats), modifier)
}
