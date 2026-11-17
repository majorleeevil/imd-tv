package org.imdtv.iptvtv.ui.live

import org.imdtv.iptvtv.ui.theme.glass
import org.imdtv.iptvtv.ui.theme.LocalGlassBackdrop
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import org.imdtv.iptvtv.ui.theme.LocalTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.first
import org.imdtv.iptvtv.data.db.CategoryEntity
import org.imdtv.iptvtv.ui.AppLogo
import org.imdtv.iptvtv.ui.onLongOk
import org.imdtv.iptvtv.data.repo.RailEntry
import org.imdtv.iptvtv.data.repo.RailItem
import org.imdtv.iptvtv.ui.FocusBox

/** Rail move mode (spec §4.3): Up/Down moves, OK drops, Back puts it back. */
internal class RailMove(val cat: CategoryEntity, val from: Int)

@Composable
internal fun Rail(
    items: List<RailItem>, selectedKey: String?, focus: FocusRequester, move: RailMove?,
    /** Kept in view (its menu is open); [focusKey] is focused once, then [onFocusKeyDone]. */
    keepKey: String?, focusKey: String?, onFocusKeyDone: () -> Unit,
    onPick: (RailEntry) -> Unit, onLongPress: (RailEntry) -> Unit, onMove: (Int) -> Unit, onMoveEnd: (cancel: Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val start = items.indexOfFirst { it.entry.key == selectedKey }.coerceAtLeast(0)
    val state = rememberLazyListState(start)
    val movingKey = move?.let { "c${it.cat.id}" }
    val moveFocus = remember { FocusRequester() }
    val movingIndex = items.indexOfFirst { it.entry.key == (movingKey ?: keepKey) }
    val keyFocus = remember { FocusRequester() }
    LaunchedEffect(movingKey) { if (movingKey != null) { withFrameNanos { }; runCatching { moveFocus.requestFocus() } } }
    LaunchedEffect(focusKey) { if (focusKey != null) { withFrameNanos { }; runCatching { keyFocus.requestFocus() }; onFocusKeyDone() } }
    LaunchedEffect(movingIndex) {
        if (movingIndex >= 0 && movingIndex !in state.layoutInfo.visibleItemsInfo.map { it.index }.drop(1).dropLast(1)) {
            state.scrollToItem((movingIndex - 3).coerceAtLeast(0))
        }
    }
    Column(
        Modifier.fillMaxHeight().width(260.dp)
            .then(if (LocalGlassBackdrop.current != null) Modifier.glass(RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
                else Modifier.shadow(30.dp).background(LocalTheme.current.colors.panel))
            .padding(horizontal = 20.dp, vertical = 35.dp)
            .onPreviewKeyEvent { e ->
                val down = e.type == KeyEventType.KeyDown
                if (move != null) {
                    // An ancestor sees keys first: handle the whole move here so the row's click and the close never fire.
                    when (e.key) {
                        Key.DirectionUp -> if (down) onMove(-1)
                        Key.DirectionDown -> if (down) onMove(1)
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> if (!down) onMoveEnd(false)
                        Key.Back -> if (!down) onMoveEnd(true)
                        else -> Unit
                    }
                    return@onPreviewKeyEvent true
                }
                if (down && (e.key == Key.Back || e.key == Key.DirectionLeft)) { onClose(); true } else false
            },
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("CATEGORIES", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = LocalTheme.current.colors.muted, letterSpacing = 2.sp,
                modifier = Modifier.weight(1f))
            AppLogo(Modifier.size(60.dp))
        }
        if (move != null) Text("Up/Down move · OK drop · Back cancel", fontSize = 11.sp, color = LocalTheme.current.colors.muted,
            modifier = Modifier.padding(bottom = 8.dp))
        LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            itemsIndexed(items, key = { _, c -> c.entry.key }) { i, rc ->
                val isMoving = rc.entry.key == movingKey
                FocusBox(
                    onClick = { onPick(rc.entry) },
                    modifier = (if (i == start) Modifier.focusRequester(focus) else Modifier)
                        .then(if (isMoving) Modifier.focusRequester(moveFocus) else Modifier)
                        .then(if (rc.entry.key == focusKey) Modifier.focusRequester(keyFocus) else Modifier)
                        // Only provider categories have a menu; Favourites and user categories are managed in Settings.
                        .then(if (rc.entry is RailEntry.Provider) Modifier.onLongOk { onLongPress(rc.entry) } else Modifier)
                        .onPreviewKeyEvent { e -> if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionRight) { onPick(rc.entry); true } else false },
                ) { f ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(5.dp))
                            .background(if (isMoving) Color(0xFFE5A00D) else if (f) LocalTheme.current.colors.focus else Color.Transparent)
                            .padding(horizontal = 11.dp, vertical = 8.dp),
                    ) {
                        val fg = if (f || isMoving) LocalTheme.current.colors.onFocus else Color.White
                        Text((if (isMoving) "↕ " else "") + rc.entry.title, fontSize = 14.sp, color = fg, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(rc.count.toString(), fontSize = 11.sp, color = if (f || isMoving) fg else LocalTheme.current.colors.muted)
                    }
                }
            }
        }
    }
}

/** Ends move mode; Back (cancel) puts the category back where it started. */
internal fun endRailMove(vm: LiveViewModel, state: GuideState, providerKeys: List<String>, cancel: Boolean) {
    state.railMove?.let { m -> if (cancel) vm.moveCategory(m.cat, m.from - providerKeys.indexOf("c${m.cat.id}")) }
    state.railMove = null
}

/** The category rail wired to the guide: pick, long-press Move up/down/Hide, move mode, close (spec §4.3). */
@Composable
fun CategoryRail(vm: LiveViewModel, state: GuideState, modifier: Modifier = Modifier) {
    val rail by vm.rail.collectAsState()
    val entry by vm.entry.collectAsState()
    val providerKeys = rail.orEmpty().filter { it.entry is RailEntry.Provider }.map { it.entry.key }
    Box(modifier) {
        Rail(rail.orEmpty(), entry?.key, state.railFocus, state.railMove,
            keepKey = (state.menu as? GuideMenu.Category)?.let { "c${it.cat.id}" }, focusKey = state.railFocusKey,
            onFocusKeyDone = { state.railFocusKey = null },
            onPick = { vm.select(it); state.railOpen = false },
            onLongPress = { e -> if (e is RailEntry.Provider) state.menu = GuideMenu.Category(e.category) },
            onMove = { d -> state.railMove?.let { vm.moveCategory(it.cat, d) } },
            onMoveEnd = { cancel -> endRailMove(vm, state, providerKeys, cancel) },
            onClose = { state.railOpen = false; state.refocus() })
    }
}
