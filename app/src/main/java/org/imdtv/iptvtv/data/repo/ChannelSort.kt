package org.imdtv.iptvtv.data.repo

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.imdtv.iptvtv.data.catalogue.displayName
import org.imdtv.iptvtv.data.db.AppDatabase
import org.imdtv.iptvtv.data.db.ChannelEntity
import org.imdtv.iptvtv.data.db.SortBy
import org.imdtv.iptvtv.data.model.Kind
import org.imdtv.iptvtv.data.settings.SettingsRepository

/**
 * Sort by (issue #9), shared by the guide and Settings: Playlist and A–Z are worked out when shown, Manual is the
 * entry's saved order (channel.sortOrder, member order, favourite order).
 */
object ChannelSort {
    fun byName(strip: List<String>) = compareBy(java.text.Collator.getInstance()) { ch: ChannelEntity -> ch.displayName(strip) }
    /** The playlist's order: its channel numbers, then (none, e.g. M3U without tvg-chno) the order it was first listed in. */
    val playlistOrder: Comparator<ChannelEntity> = compareBy(nullsLast<Int>()) { ch: ChannelEntity -> ch.number }.thenBy { it.id }

    fun sorted(list: List<ChannelEntity>, by: SortBy, strip: List<String>): List<ChannelEntity> = when (by) {
        SortBy.MANUAL -> list
        SortBy.PLAYLIST -> list.sortedWith(playlistOrder)
        SortBy.AZ -> list.sortedWith(byName(strip))
    }

    /** Favourites have no stored choice until one is made: Manual once any was moved, else Playlist. */
    fun observe(db: AppDatabase, settings: SettingsRepository, providerId: Long, e: RailEntry): Flow<SortBy> = when (e) {
        RailEntry.Favourites -> combine(settings.settings.map { it.favouritesSortBy },
            db.favouriteDao().observe(providerId, Kind.LIVE).map { f -> f.any { it.sortOrder != 0 } }) { s, moved -> s ?: if (moved) SortBy.MANUAL else SortBy.PLAYLIST }
        is RailEntry.Custom -> db.customGroupDao().observeSortBy(e.group.id).map { it ?: SortBy.PLAYLIST }
        is RailEntry.Provider -> db.categoryDao().observeSortBy(e.category.id).map { it ?: SortBy.PLAYLIST }
        RailEntry.Recent -> flowOf(SortBy.MANUAL)
    }.distinctUntilChanged()

    /** Switching to Manual keeps the order now shown, so nothing jumps; Playlist and A–Z only change how it's shown. */
    suspend fun set(db: AppDatabase, settings: SettingsRepository, providerId: Long, e: RailEntry, by: SortBy, strip: List<String>) {
        val was = observe(db, settings, providerId, e).first()
        if (by == SortBy.MANUAL && was != SortBy.MANUAL) save(db, providerId, e, sorted(channels(db, providerId, e), was, strip))
        setMode(db, settings, e, by)
    }

    /** Sort A–Z once: an A–Z order saved as the entry's own (Manual), so channels can still be moved after. */
    suspend fun sortOnce(db: AppDatabase, settings: SettingsRepository, providerId: Long, e: RailEntry, strip: List<String>) {
        save(db, providerId, e, sorted(channels(db, providerId, e), SortBy.AZ, strip))
        setMode(db, settings, e, SortBy.MANUAL)
    }

    private suspend fun setMode(db: AppDatabase, settings: SettingsRepository, e: RailEntry, by: SortBy) = when (e) {
        RailEntry.Favourites -> settings.update { it.copy(favouritesSortBy = by) }
        is RailEntry.Custom -> db.customGroupDao().setSortBy(e.group.id, by)
        is RailEntry.Provider -> db.categoryDao().setSortBy(e.category.id, by)
        RailEntry.Recent -> Unit
    }

    /** A playlist category's channels include hidden ones, so one shown again keeps its place. */
    private suspend fun channels(db: AppDatabase, providerId: Long, e: RailEntry): List<ChannelEntity> = when (e) {
        RailEntry.Favourites -> db.channelDao().observeFavourites(providerId).first()
        is RailEntry.Custom -> db.customGroupDao().observeChannels(e.group.id).first()
        is RailEntry.Provider -> db.channelDao().allByCategory(e.category.id)
        RailEntry.Recent -> emptyList()
    }

    private suspend fun save(db: AppDatabase, providerId: Long, e: RailEntry, order: List<ChannelEntity>) = db.withTransaction {
        order.forEachIndexed { n, x ->
            when (e) {
                RailEntry.Favourites -> db.favouriteDao().setSortOrder(providerId, Kind.LIVE, x.id, n)
                is RailEntry.Custom -> db.customGroupDao().setMemberOrder(e.group.id, x.id, n)
                is RailEntry.Provider -> db.channelDao().setSortOrder(x.id, n)
                RailEntry.Recent -> Unit
            }
        }
    }
}
