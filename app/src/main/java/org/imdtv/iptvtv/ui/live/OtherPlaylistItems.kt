package org.imdtv.iptvtv.ui.live

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import org.imdtv.iptvtv.R
import org.imdtv.iptvtv.data.db.CategoryEntity
import org.imdtv.iptvtv.data.repo.OtherPlaylist

/** How a rail draws one row of the playlist sections; [header] rows carry the chevron, [nested] rows are indented. */
typealias SectionRow = @Composable (title: String, trailing: String, header: Boolean, nested: Boolean, onClick: () -> Unit) -> Unit

/**
 * After the active playlist's categories: each other playlist as a header row (name + chevron, OK/Right opens or
 * closes it) with its categories nested under it. Picking one of those switches to that playlist ([onPick]).
 */
fun LazyListScope.otherPlaylistItems(
    others: List<OtherPlaylist>, expanded: Set<Long>, onToggle: (Long) -> Unit, onPick: (CategoryEntity) -> Unit,
    /** Wraps each row (by its item key), e.g. to track or request focus on it. */
    wrap: (String) -> Modifier = { Modifier }, row: SectionRow,
) = others.forEach { o ->
    val open = o.provider.id in expanded
    item(key = "p${o.provider.id}") { Box(wrap("p${o.provider.id}")) { row(o.provider.railTitle(), if (open) "⌃" else "⌄", true, false) { onToggle(o.provider.id) } } }
    if (open) items(o.categories, key = { "o${it.first.id}" }) { (cat, n) ->
        Box(wrap("o${cat.id}")) { row(cat.name.ifEmpty { stringResource(R.string.live_uncategorised) }, n.toString(), false, true) { onPick(cat) } }
    }
}

/** A playlist's section header: media servers say which kind, "Plex (Home)", "Jellyfin (Home)". */
fun org.imdtv.iptvtv.data.db.ProviderEntity.railTitle() = when (type) {
    org.imdtv.iptvtv.data.db.ProviderType.PLEX -> "Plex ($name)"
    org.imdtv.iptvtv.data.db.ProviderType.JELLYFIN -> "Jellyfin ($name)"
    org.imdtv.iptvtv.data.db.ProviderType.STREMIO -> "Stremio ($name)"
    else -> name
}

/** The item keys [otherPlaylistItems] lays out, in order. */
fun otherPlaylistKeys(others: List<OtherPlaylist>, expanded: Set<Long>): List<String> = others.flatMap { o ->
    listOf("p${o.provider.id}") + if (o.provider.id in expanded) o.categories.map { "o${it.first.id}" } else emptyList()
}

/**
 * Hold Up / Down in a rail of sections starting at [starts] (ascending, first is 0): from [from], Up goes to the top of
 * its section, or the previous section's top when already there; Down to the next section's top, or the last row.
 */
fun railJump(starts: List<Int>, size: Int, from: Int, up: Boolean): Int =
    if (up) starts.lastOrNull { it < from } ?: 0
    else starts.firstOrNull { it > from } ?: (size - 1).coerceAtLeast(0)

/** [set] with [id] added, or removed if it was there. */
fun Set<Long>.toggled(id: Long) = if (id in this) this - id else this + id

/**
 * A rail's keys over [keys] (its rows, in order, as laid out in [state]): Channel/Page Up/Down move a screen; hold
 * Up/Down jumps by section (sections start at [starts], see railJump). [onKey] true = handled; give each row [track].
 */
class RailKeys internal constructor(private val state: androidx.compose.foundation.lazy.LazyListState) {
    internal var keys: List<String> = emptyList()
    internal var starts: List<Int> = emptyList()
    private var focused: String? = null
    private var holdFrom = -1
    internal var jumpKey by androidx.compose.runtime.mutableStateOf<String?>(null)
    internal val jumpFocus = androidx.compose.ui.focus.FocusRequester()

    fun track(key: String): Modifier = Modifier.onFocusChanged { if (it.hasFocus) focused = key }
        .then(if (key == jumpKey) Modifier.focusRequester(jumpFocus) else Modifier)

    fun onKey(e: androidx.compose.ui.input.key.KeyEvent): Boolean {
        val down = e.type == KeyEventType.KeyDown
        pageKey(e.key)?.let { d -> // a screen of entries at a time (issue #10)
            val at = keys.indexOf(focused)
            if (down && at >= 0) jumpKey = keys[(at + d * (state.layoutInfo.visibleItemsInfo.size - 1).coerceAtLeast(1)).coerceIn(0, keys.lastIndex)]
            return true
        }
        if (e.key != Key.DirectionUp && e.key != Key.DirectionDown || !down) return false
        val r = e.nativeKeyEvent.repeatCount
        // Remember where the press started: its first step already moved focus a row before the hold shows.
        if (r == 0) { holdFrom = keys.indexOf(focused); return false }
        if (r == 1 && holdFrom >= 0) jumpKey = keys[railJump(starts, keys.size, holdFrom, e.key == Key.DirectionUp)]
        return true // the rest of the hold does nothing
    }
}

/** [keys] and [starts] as in [RailKeys]; the jump scrolls the target into view, then focuses it. */
@Composable
fun rememberRailKeys(state: androidx.compose.foundation.lazy.LazyListState, keys: List<String>, starts: List<Int>): RailKeys {
    val k = androidx.compose.runtime.remember(state) { RailKeys(state) }
    k.keys = keys; k.starts = starts
    androidx.compose.runtime.LaunchedEffect(k.jumpKey) {
        val target = k.jumpKey ?: return@LaunchedEffect
        state.scrollToItem((k.keys.indexOf(target) - 3).coerceAtLeast(0)); androidx.compose.runtime.withFrameNanos { }
        runCatching { k.jumpFocus.requestFocus() }; k.jumpKey = null
    }
    return k
}
