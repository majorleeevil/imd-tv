package org.imdtv.iptvtv.data.repo

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.imdtv.iptvtv.data.catalogue.cleaned
import org.imdtv.iptvtv.data.catalogue.parseStripList
import org.imdtv.iptvtv.data.db.AppDatabase
import org.imdtv.iptvtv.data.db.CategoryEntity
import org.imdtv.iptvtv.data.db.ProviderEntity
import org.imdtv.iptvtv.data.model.Kind

/** A playlist other than the active one with its visible, non-empty categories of one kind: a section of the rails. */
data class OtherPlaylist(val provider: ProviderEntity, val categories: List<Pair<CategoryEntity, Int>>)

/**
 * The other playlists for the category rails, in name order. A playlist with [kind] switched off (0.5.1 per-playlist
 * switches) or with nothing to show is left out; hidden categories stay hidden.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun otherPlaylists(db: AppDatabase, activeId: Flow<Long?>, kind: Flow<Kind>): Flow<List<OtherPlaylist>> =
    combine(
        activeId, kind,
        // Only what the sections show: the provider row also changes on every sync (lastSync).
        db.providerDao().observeAll().distinctUntilChanged { a, b -> a.map(::sectionKey) == b.map(::sectionKey) },
    ) { active, k, providers -> k to providers.filter { it.id != active && it.loads(k) } }
        .flatMapLatest { (k, list) ->
            if (list.isEmpty()) flowOf(emptyList())
            else combine(list.map { p -> section(db, p, k) }) { s -> s.filter { it.categories.isNotEmpty() } }
        }

private fun sectionKey(p: ProviderEntity) = listOf(p.id, p.name, p.contentOff, p.categoryStrip)

private fun section(db: AppDatabase, p: ProviderEntity, k: Kind): Flow<OtherPlaylist> {
    val counts = when (k) {
        Kind.LIVE -> db.channelDao().observeCounts(p.id)
        Kind.MOVIE -> db.movieDao().observeCounts(p.id)
        Kind.SERIES -> db.seriesDao().observeCounts(p.id)
    }
    val strip = parseStripList(p.categoryStrip)
    return combine(db.categoryDao().observeVisible(p.id, k), counts.map { n -> n.associate { it.categoryId to it.n } }) { cats, n ->
        OtherPlaylist(p, cats.map { it.cleaned(strip) to (n[it.id] ?: 0) }.filter { it.second > 0 })
    }
}
