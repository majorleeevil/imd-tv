package org.imdtv.iptvtv.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.imdtv.iptvtv.data.catalogue.cleaned
import org.imdtv.iptvtv.data.catalogue.parseStripList
import org.imdtv.iptvtv.data.db.AppDatabase
import org.imdtv.iptvtv.data.db.CategoryEntity
import org.imdtv.iptvtv.data.db.ChannelEntity
import org.imdtv.iptvtv.data.db.CustomGroupEntity
import org.imdtv.iptvtv.data.db.GroupPlacement
import org.imdtv.iptvtv.data.model.Kind

/** One entry of the live guide's category rail (spec §4.3). */
sealed interface RailEntry {
    val key: String
    val title: String
    /** String resource for a fixed, app-defined title (0 = [title] is data: a category or group name). */
    @get:androidx.annotation.StringRes val titleRes: Int get() = 0

    data object Favourites : RailEntry { override val key = "fav"; override val title = "★ Favourites"; override val titleRes = org.imdtv.iptvtv.R.string.live_rail_favourites }
    data object Recent : RailEntry { override val key = "recent"; override val title = "Recently watched"; override val titleRes = org.imdtv.iptvtv.R.string.live_rail_recent }
    data class Custom(val group: CustomGroupEntity) : RailEntry {
        override val key get() = "g${group.id}"
        override val title get() = group.name
    }
    data class Provider(val category: CategoryEntity) : RailEntry {
        override val key get() = "c${category.id}"
        override val title get() = category.name.ifEmpty { "Uncategorised" }
        override val titleRes get() = if (category.name.isEmpty()) org.imdtv.iptvtv.R.string.live_uncategorised else 0
    }
}

data class RailItem(val entry: RailEntry, val count: Int)

class RailRepository(private val db: AppDatabase) {
    /**
     * Top user categories, Favourites, Recently watched, normal user categories, visible provider categories, bottom
     * user categories (each set in sortOrder, then name); empty entries are left out.
     */
    fun rail(providerId: Long): Flow<List<RailItem>> = combine(
        db.providerDao().observe(providerId).map { parseStripList(it?.categoryStrip.orEmpty()) }.distinctUntilChanged(),
        railRaw(providerId),
    ) { strip, items -> if (strip.isEmpty()) items else items.map { i -> (i.entry as? RailEntry.Provider)?.let { RailItem(RailEntry.Provider(it.category.cleaned(strip)), i.count) } ?: i } }

    private fun railRaw(providerId: Long): Flow<List<RailItem>> = combine(
        combine(db.channelDao().observeFavourites(providerId), db.recentDao().observe(providerId), ::Pair),
        db.customGroupDao().observe(providerId),
        db.customGroupDao().observeCounts(providerId),
        db.categoryDao().observeVisible(providerId, Kind.LIVE),
        db.channelDao().observeCounts(providerId),
    ) { (favs, recents), groups, groupCounts, cats, catCounts ->
        val gn = groupCounts.associate { it.groupId to it.n }
        val cn = catCounts.associate { it.categoryId to it.n }
        buildList {
            fun groupsAt(p: GroupPlacement) = groups.filter { it.placement == p }.forEach { add(RailItem(RailEntry.Custom(it), gn[it.id] ?: 0)) }
            groupsAt(GroupPlacement.TOP)
            add(RailItem(RailEntry.Favourites, favs.size))
            add(RailItem(RailEntry.Recent, recents.size))
            groupsAt(GroupPlacement.NORMAL)
            cats.forEach { add(RailItem(RailEntry.Provider(it), cn[it.id] ?: 0)) }
            groupsAt(GroupPlacement.BOTTOM)
        }.filter { it.count > 0 }
    }

    fun channels(providerId: Long, entry: RailEntry): Flow<List<ChannelEntity>> = when (entry) {
        RailEntry.Favourites -> db.channelDao().observeFavourites(providerId)
        RailEntry.Recent -> db.recentDao().observe(providerId)
        is RailEntry.Custom -> db.customGroupDao().observeChannels(entry.group.id)
        is RailEntry.Provider -> db.channelDao().observeByCategory(entry.category.id)
    }
}
