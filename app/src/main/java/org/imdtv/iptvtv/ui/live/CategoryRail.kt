package org.imdtv.iptvtv.ui.live

import androidx.compose.ui.res.stringResource
import org.imdtv.iptvtv.R

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
import androidx.compose.ui.focus.onFocusChanged
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
import org.imdtv.iptvtv.ui.AppLogo
import org.imdtv.iptvtv.ui.onLongOk
import org.imdtv.iptvtv.data.repo.RailEntry
import org.imdtv.iptvtv.data.repo.RailItem
import org.imdtv.iptvtv.ui.FocusBox

/** Rail move mode (spec §4.3): Up/Down moves, OK drops, Back puts it back. [entry] is a provider or your category. */
internal class RailMove(val entry: RailEntry, val from: Int)

@Composable
internal fun Rail(
    items: List<RailItem>, selectedKey: String?, focus: FocusRequester, move: RailMove?,
    /** Kept in view (its menu is open); [focusKey] is focused once, then [onFocusKeyDone]. */
    keepKey: String?, focusKey: String?, onFocusKeyDone: () -> Unit,
    onPick: (RailEntry) -> Unit, onLongPress: (RailEntry) -> Unit, onMove: (Int) -> Unit, onMoveEnd: (cancel: Boolean) -> Unit,
    onClose: () -> Unit,
    others: List<org.imdtv.iptvtv.data.repo.OtherPlaylist> = emptyList(), expanded: Set<Long> = emptySet(),
    onToggle: (Long) -> Unit = {}, onPickOther: (org.imdtv.iptvtv.data.db.CategoryEntity) -> Unit = {},
) {
    val start = items.indexOfFirst { it.entry.key == selectedKey }.coerceAtLeast(0)
    val state = rememberLazyListState(start)
    val movingKey = move?.entry?.key
    val moveFocus = remember { FocusRequester() }
    val movingIndex = items.indexOfFirst { it.entry.key == (movingKey ?: keepKey) }
    val keyFocus = remember { FocusRequester() }
    LaunchedEffect(movingKey) { if (movingKey != null) { withFrameNanos { }; runCatching { moveFocus.requestFocus() } } }
    LaunchedEffect(focusKey) { if (focusKey != null) { withFrameNanos { }; runCatching { keyFocus.requestFocus() }; onFocusKeyDone() } }
    // Hold Up/Down jumps by section: this playlist's categories, then each other playlist (see railJump).
    val keys = remember(items, others, expanded) { items.map { it.entry.key } + otherPlaylistKeys(others, expanded) }
    val starts = remember(keys) { keys.indices.filter { it == 0 || keys[it].startsWith("p") && it >= items.size } }
    val focusedKey = remember { arrayOfNulls<String>(1) }
    val holdFrom = remember { IntArray(1) }
    var jumpKey by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val jumpFocus = remember { FocusRequester() }
    LaunchedEffect(jumpKey) {
        val k = jumpKey ?: return@LaunchedEffect
        state.scrollToItem((keys.indexOf(k) - 3).coerceAtLeast(0)); withFrameNanos { }
        runCatching { jumpFocus.requestFocus() }; jumpKey = null
    }
    fun track(key: String) = Modifier.onFocusChanged { if (it.hasFocus) focusedKey[0] = key }
        .then(if (key == jumpKey) Modifier.focusRequester(jumpFocus) else Modifier)
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
                if (e.key == Key.DirectionUp || e.key == Key.DirectionDown) {
                    val r = e.nativeKeyEvent.repeatCount
                    // Remember where the press started: its first step already moved focus a row before the hold shows.
                    if (down && r == 0) { holdFrom[0] = keys.indexOf(focusedKey[0]); return@onPreviewKeyEvent false }
                    if (down && r == 1 && holdFrom[0] >= 0) jumpKey = keys[railJump(starts, keys.size, holdFrom[0], e.key == Key.DirectionUp)]
                    if (down) return@onPreviewKeyEvent true // the rest of the hold does nothing
                }
                // Left only on a fresh press: the repeats of the hold that opened the rail would close it again at once.
                if (down && (e.key == Key.Back || e.key == Key.DirectionLeft && e.nativeKeyEvent.repeatCount == 0)) { onClose(); true } else false
            },
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.live_categories_header), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = LocalTheme.current.colors.muted, letterSpacing = 2.sp,
                modifier = Modifier.weight(1f))
            AppLogo(Modifier.size(60.dp))
        }
        if (move == null) Text(stringResource(R.string.live_rail_jump_hint), fontSize = 11.sp, color = LocalTheme.current.colors.muted,
            modifier = Modifier.padding(bottom = 8.dp))
        if (move != null) Text(stringResource(R.string.live_rail_move_hint), fontSize = 11.sp, color = LocalTheme.current.colors.muted,
            modifier = Modifier.padding(bottom = 8.dp))
        LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            itemsIndexed(items, key = { _, c -> c.entry.key }) { i, rc ->
                val isMoving = rc.entry.key == movingKey
                FocusBox(
                    onClick = { onPick(rc.entry) },
                    modifier = (if (i == start) Modifier.focusRequester(focus) else Modifier)
                        .then(if (isMoving) Modifier.focusRequester(moveFocus) else Modifier)
                        .then(if (rc.entry.key == focusKey) Modifier.focusRequester(keyFocus) else Modifier)
                        .then(track(rc.entry.key))
                        // Provider and your categories have a menu; Favourites and Recently watched don't.
                        .then(if (rc.entry is RailEntry.Provider || rc.entry is RailEntry.Custom) Modifier.onLongOk { onLongPress(rc.entry) } else Modifier)
                        .onPreviewKeyEvent { e -> if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionRight) { onPick(rc.entry); true } else false },
                ) { f ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(5.dp))
                            .background(if (isMoving) Color(0xFFE5A00D) else if (f) LocalTheme.current.colors.focus else Color.Transparent)
                            .padding(horizontal = 11.dp, vertical = 8.dp),
                    ) {
                        val fg = if (f || isMoving) LocalTheme.current.colors.onFocus else Color.White
                        Text((if (isMoving) "↕ " else "") + rc.entry.titleText(), fontSize = 14.sp, color = fg, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(rc.count.toString(), fontSize = 11.sp, color = if (f || isMoving) fg else LocalTheme.current.colors.muted)
                    }
                }
            }
            if (move == null) otherPlaylistItems(others, expanded, onToggle, onPickOther, ::track) { title, trailing, header, nested, onClick ->
                SectionRowBox(title, trailing, header, nested, onClick, LocalTheme.current.colors.focus, LocalTheme.current.colors.onFocus)
            }
        }
    }
}

/** Ends move mode; Back (cancel) puts the category back where it started. */
internal fun endRailMove(vm: LiveViewModel, state: GuideState, rail: List<RailItem>, cancel: Boolean) {
    state.railMove?.let { m -> if (cancel) vm.moveRail(m.entry, m.from - rail.sameKindKeys(m.entry).indexOf(m.entry.key)) }
    state.railMove = null
}

/** Keys of the rail entries of [e]'s kind (provider categories, or your categories), in rail order. */
internal fun List<RailItem>.sameKindKeys(e: RailEntry) = filter { it.entry::class == e::class }.map { it.entry.key }

/** Long-press on a rail entry: the menu for a provider category or one of your categories. */
internal fun railMenu(e: RailEntry, horizontal: Boolean = false): GuideMenu? = when (e) {
    is RailEntry.Provider -> GuideMenu.Category(e.category, horizontal)
    is RailEntry.Custom -> GuideMenu.Group(e.group, horizontal)
    else -> null
}

/** The category rail wired to the guide: pick, long-press Move up/down/Hide, move mode, close (spec §4.3). */
@Composable
fun CategoryRail(vm: LiveViewModel, state: GuideState, modifier: Modifier = Modifier) {
    val rail by vm.rail.collectAsState()
    val entry by vm.entry.collectAsState()
    val others by vm.otherPlaylists.collectAsState()
    val expanded by vm.expandedPlaylists.collectAsState()
    Box(modifier) {
        Rail(rail.orEmpty(), entry?.key, state.railFocus, state.railMove,
            keepKey = when (val m = state.menu) { is GuideMenu.Category -> "c${m.cat.id}"; is GuideMenu.Group -> "g${m.g.id}"; else -> null },
            focusKey = state.railFocusKey,
            onFocusKeyDone = { state.railFocusKey = null },
            onPick = { vm.select(it); state.railOpen = false },
            onLongPress = { e -> railMenu(e)?.let { state.menu = it } },
            onMove = { d -> state.railMove?.let { vm.moveRail(it.entry, d) } },
            onMoveEnd = { cancel -> endRailMove(vm, state, rail.orEmpty(), cancel) },
            onClose = { state.railOpen = false; state.refocus() },
            others = others, expanded = expanded, onToggle = { id -> vm.expandedPlaylists.value = vm.expandedPlaylists.value.toggled(id) },
            onPickOther = { cat -> vm.usePlaylistAt(cat.providerId, cat.id); state.railOpen = false })
    }
}

/** A playlist header or nested category row in a rail's playlist sections; Right acts like OK, as on the other rows. */
@Composable
internal fun SectionRowBox(title: String, trailing: String, header: Boolean, nested: Boolean, onClick: () -> Unit, focusBg: Color, focusFg: Color) {
    FocusBox(onClick = onClick, modifier = Modifier.padding(top = if (header) 10.dp else 0.dp)
        .onPreviewKeyEvent { e -> if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionRight) { onClick(); true } else false }) { f ->
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(5.dp)).background(if (f) focusBg else Color.Transparent)
                .padding(start = if (nested) 25.dp else 11.dp, end = 11.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val fg = if (f) focusFg else Color.White
            Text(title, fontSize = 14.sp, fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal, color = fg, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(trailing, fontSize = if (header) 14.sp else 11.sp, color = if (f) fg else LocalTheme.current.colors.muted)
        }
    }
}

/** The entry's title in the current language: app-defined titles come from resources, category and group names as they are. */
@Composable
fun RailEntry.titleText(): String = if (titleRes != 0) stringResource(titleRes) else title
