package org.imdtv.iptvtv.ui.live

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import org.imdtv.iptvtv.data.db.CategoryEntity
import org.imdtv.iptvtv.data.repo.OtherPlaylist

/** How a rail draws one row of the playlist sections; [header] rows carry the chevron, [nested] rows are indented. */
typealias SectionRow = @Composable (title: String, trailing: String, header: Boolean, nested: Boolean, onClick: () -> Unit) -> Unit

/**
 * After the active playlist's categories: each other playlist as a header row (name + chevron, OK/Right opens or
 * closes it) with its categories nested under it. Picking one of those switches to that playlist ([onPick]).
 */
fun LazyListScope.otherPlaylistItems(
    others: List<OtherPlaylist>, expanded: Set<Long>, onToggle: (Long) -> Unit, onPick: (CategoryEntity) -> Unit, row: SectionRow,
) = others.forEach { o ->
    val open = o.provider.id in expanded
    item(key = "p${o.provider.id}") { row(o.provider.name, if (open) "⌃" else "⌄", true, false) { onToggle(o.provider.id) } }
    if (open) items(o.categories, key = { "o${it.first.id}" }) { (cat, n) ->
        row(cat.name.ifEmpty { "Uncategorised" }, n.toString(), false, true) { onPick(cat) }
    }
}

/** [set] with [id] added, or removed if it was there. */
fun Set<Long>.toggled(id: Long) = if (id in this) this - id else this + id
