package org.imdtv.iptvtv.ui.live

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    item(key = "p${o.provider.id}") { Box(wrap("p${o.provider.id}")) { row(o.provider.name, if (open) "⌃" else "⌄", true, false) { onToggle(o.provider.id) } } }
    if (open) items(o.categories, key = { "o${it.first.id}" }) { (cat, n) ->
        Box(wrap("o${cat.id}")) { row(cat.name.ifEmpty { stringResource(R.string.live_uncategorised) }, n.toString(), false, true) { onPick(cat) } }
    }
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
