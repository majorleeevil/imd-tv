package org.imdtv.iptvtv.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.imdtv.iptvtv.IptvApp
import org.imdtv.iptvtv.data.catalogue.parseStripList
import org.imdtv.iptvtv.data.search.SearchResults
import org.imdtv.iptvtv.data.search.GuideHit
import org.imdtv.iptvtv.data.search.Section
import org.imdtv.iptvtv.data.search.queryWords
import org.imdtv.iptvtv.data.settings.AppSettings
import org.imdtv.iptvtv.data.settings.LogoKind

/**
 * Results after a 300 ms typing pause; a newer query cancels the running one. Blank = null at once, no search.
 * A [version] change (catalogue write: hide, rename, sync) re-runs the same query so shown results never go stale.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
fun <T> searchFlow(query: Flow<String>, version: Flow<Long> = kotlinx.coroutines.flow.flowOf(0L), search: suspend (String) -> T): Flow<T?> =
    query.debounce { if (queryWords(it).isEmpty()) 0L else 300L }
        .distinctUntilChanged()
        .combine(version) { q, v -> q to v }
        .distinctUntilChanged()
        .mapLatest { (q, _) -> if (queryWords(q).isEmpty()) null else search(q) }

data class GuideResults(val today: Section<GuideHit>, val later: Section<GuideHit>) {
    companion object { val EMPTY = GuideResults(Section(emptyList(), 0), Section(emptyList(), 0)) }
}

/** [hits] (soonest first) split at the end of [now]'s day, at most [cap] shown in each. */
fun guideResults(hits: List<GuideHit>, now: Long, cap: Int = 50): GuideResults {
    val midnight = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate().plusDays(1)
        .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    val (today, later) = hits.partition { it.p.start < midnight }
    return GuideResults(Section(today.take(cap), today.size), Section(later.take(cap), later.size))
}

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as IptvApp).container

    val query = MutableStateFlow("")

    /** null = nothing searched. */
    val results: StateFlow<SearchResults?> = searchFlow(query, c.search.version) { q ->
        val p = c.providerRepository.activeProviderId.first() ?: return@searchFlow SearchResults.EMPTY
        val strip = org.imdtv.iptvtv.data.catalogue.channelStrip(c.db.providerDao().get(p)?.nameStrip, c.settings.settings.first().removeNameSymbols)
        c.search.universal(p, strip, q)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Guide (EPG) matches, split at midnight: airing or starting today, and later. Its own flow, so the slower guide never holds up the rest. */
    val guide: StateFlow<GuideResults?> = searchFlow(query, c.search.version) { q ->
        val p = c.providerRepository.activeProviderId.first() ?: return@searchFlow GuideResults.EMPTY
        val strip = org.imdtv.iptvtv.data.catalogue.channelStrip(c.db.providerDao().get(p)?.nameStrip, c.settings.settings.first().removeNameSymbols)
        val now = System.currentTimeMillis()
        guideResults(c.search.guide(p, strip, q, now), now)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val logoSource: StateFlow<List<LogoKind>> = c.settings.settings.map { it.logoOrder }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings().logoOrder)

    // Observed, so tiles follow an edit of Channel name clean-up while this (activity-long) VM lives.
    val strip: StateFlow<List<String>> = c.providerRepository.activeProviderId
        .flatMapLatest { p -> p?.let { c.db.providerDao().observe(it) } ?: kotlinx.coroutines.flow.flowOf(null) }
        .combine(c.settings.settings.map { it.removeNameSymbols }.distinctUntilChanged()) { p, sym -> org.imdtv.iptvtv.data.catalogue.channelStrip(p?.nameStrip, sym) }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Builds the name indexes while the user starts typing (the first build takes seconds on a Chromecast). */
    fun warm() {
        viewModelScope.launch {
            val p = c.providerRepository.activeProviderId.first() ?: return@launch
            c.search.warm(p, org.imdtv.iptvtv.data.catalogue.channelStrip(c.db.providerDao().get(p)?.nameStrip, c.settings.settings.first().removeNameSymbols))
        }
    }

    /** The result opened last (section 0–4, index), focused again on return from full screen or detail; null = a fresh visit. */
    var returnTo: Pair<Int, Int>? = null
}
