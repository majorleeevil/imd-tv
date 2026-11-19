package org.imdtv.iptvtv.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import org.imdtv.iptvtv.data.repo.RailEntry
import org.imdtv.iptvtv.data.repo.RailItem
import org.imdtv.iptvtv.ui.FocusBox
import org.imdtv.iptvtv.ui.onLongOk
import org.imdtv.iptvtv.ui.theme.LocalTheme

/** A tab sized to its label, clamped so long names ellipsise and at least ~6 tabs fit (spec §6A.4). */
fun tabWidth(textWidth: Dp): Dp = (textWidth + 28.dp).coerceIn(110.dp, 220.dp)

private val TAB = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
private val LABEL = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

/**
 * Celestial Plus category tabs in rail order. Focusing a tab switches the category at once; OK or Down goes to the
 * grid, Up to the nav. Move mode (from a provider tab's long-press): Left/Right move, OK drops, Back cancels.
 */
@Composable
internal fun CategoryTabs(
    items: List<RailItem>, selectedKey: String?, focus: FocusRequester, onPick: (RailEntry) -> Unit, onDown: () -> Unit,
    onUp: () -> Unit, onLongPress: (RailEntry) -> Unit, move: RailMove?, onMove: (Int) -> Unit, onMoveEnd: (cancel: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalTheme.current.colors
    val selected = items.indexOfFirst { it.entry.key == selectedKey }.coerceAtLeast(0)
    val state = rememberLazyListState((selected - 2).coerceAtLeast(0))
    val movingKey = move?.entry?.key
    val movingIndex = items.indexOfFirst { it.entry.key == movingKey }
    val moveFocus = remember { FocusRequester() }
    LaunchedEffect(movingKey) { if (movingKey != null) { withFrameNanos { }; runCatching { moveFocus.requestFocus() } } }
    // Keep the selected (or moving) tab in view, with a neighbour either side.
    val keep = if (movingIndex >= 0) movingIndex else selected
    LaunchedEffect(keep) {
        val shown = state.layoutInfo.visibleItemsInfo.map { it.index }
        if (keep !in shown.drop(1).dropLast(1)) state.scrollToItem((keep - 1).coerceAtLeast(0))
    }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    LazyRow(
        state = state,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp), // TV-safe margin
        modifier = modifier.onPreviewKeyEvent { e ->
            val down = e.type == KeyEventType.KeyDown
            if (move != null) {
                when (e.key) {
                    Key.DirectionLeft -> if (down) onMove(-1)
                    Key.DirectionRight -> if (down) onMove(1)
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> if (!down) onMoveEnd(false)
                    Key.Back -> if (!down) onMoveEnd(true)
                    else -> Unit
                }
                return@onPreviewKeyEvent true
            }
            when {
                down && e.key == Key.DirectionDown -> { onDown(); true }
                down && e.key == Key.DirectionUp -> { onUp(); true }
                else -> false
            }
        },
    ) {
        itemsIndexed(items, key = { _, it -> it.entry.key }) { i, item ->
            val isSelected = item.entry.key == selectedKey
            val isMoving = item.entry.key == movingKey
            val title = item.entry.titleText()
            val width = remember(title) { with(density) { measurer.measure(title, LABEL).size.width.toDp() } }
            FocusBox(
                onClick = onDown,
                onFocused = { if (!isSelected && move == null) onPick(item.entry) },
                modifier = (if (isSelected) Modifier.focusRequester(focus) else Modifier)
                    .then(if (isMoving) Modifier.focusRequester(moveFocus) else Modifier)
                    .then(if (item.entry is RailEntry.Provider || item.entry is RailEntry.Custom) Modifier.onLongOk { onLongPress(item.entry) } else Modifier)
                    .width(tabWidth(width)).fillMaxHeight(),
            ) { f ->
                val nextSelected = items.getOrNull(i + 1)?.entry?.key == selectedKey
                Box(
                    Modifier.fillMaxHeight().width(tabWidth(width))
                        .background(when { isMoving -> colors.accent; f -> colors.focus; isSelected -> colors.panel; else -> colors.cellDim }, TAB)
                        // "|" between two unselected tabs, as the reference.
                        .drawBehind {
                            if (!isSelected && !nextSelected && !f) drawRect(Color(0x66FFFFFF), Offset(size.width - 1.dp.toPx(), size.height * .25f), Size(1.dp.toPx(), size.height * .5f))
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text((if (isMoving) "↔ " else "") + title, style = LABEL, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (f || isMoving) colors.onFocus else if (isSelected) Color.White else Color.White.copy(alpha = .85f),
                        modifier = Modifier.padding(horizontal = 14.dp))
                }
            }
        }
    }
}
