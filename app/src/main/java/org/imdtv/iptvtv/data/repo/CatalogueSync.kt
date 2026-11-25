package org.imdtv.iptvtv.data.repo

import androidx.room.withTransaction
import org.imdtv.iptvtv.data.db.ChannelFromProvider
import org.imdtv.iptvtv.data.db.MovieFromProvider
import org.imdtv.iptvtv.data.db.SeriesFromProvider
import org.imdtv.iptvtv.data.catalogue.detectCountry
import org.imdtv.iptvtv.data.catalogue.matchesLanguage
import org.imdtv.iptvtv.data.db.AppDatabase
import org.imdtv.iptvtv.data.db.CategoryEntity
import org.imdtv.iptvtv.data.db.ChannelEntity
import org.imdtv.iptvtv.data.db.MovieEntity
import org.imdtv.iptvtv.data.db.SeriesEntity
import org.imdtv.iptvtv.data.model.Category
import org.imdtv.iptvtv.data.model.Kind
import org.imdtv.iptvtv.data.source.LibraryPage
import org.imdtv.iptvtv.data.source.PlaylistSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

data class SyncResult(
    val categories: Int,
    val channels: Int,
    val movies: Int,
    val series: Int,
    val newlyHidden: Int,
    /** Malformed source entries skipped (spec §7). */
    val skipped: Int = 0,
) {
    fun summary() = "$categories categories, $channels channels, $movies movies, $series series, $newlyHidden hidden, $skipped skipped."
}

/**
 * Writes a source's catalogue into Room (spec §4.2/§4.3): upsert by (providerId, remoteId), keep user
 * fields, delete what disappeared (with its favourites/progress), hide new categories matching a hide rule.
 * Everything is fetched before any write, so a network failure leaves the cached catalogue untouched.
 */
class CatalogueSync(
    private val db: AppDatabase,
    private val now: () -> Long = System::currentTimeMillis,
    /** "Match TV language": the language new categories must have to start shown, or null when off. */
    private val keepLanguage: suspend () -> String? = { null },
    /** Settings › Channels › Merge channels and Merge across, read at the end of each live sync. */
    private val mergeOptions: suspend () -> MergeOptions = { MergeOptions(on = false, across = false) },
) {
    companion object {
        /** remoteId of the synthetic per-kind category for items with no (known) category. */
        const val UNCATEGORISED = ""
    }


    /**
     * Kinds are fetched and written one after another to bound peak memory on low-RAM TV boxes.
     * Each kind is fully fetched before its transaction, so a failure keeps that kind's cached data.
     */
    /**
     * A kind [loads] says no to isn't fetched; it syncs as empty, which removes what was there. [loads] is asked again
     * at the end, so a kind switched off while this sync runs (before, during or after its turn) is removed too.
     */
    /** [since] (epoch ms): media servers fetch only what changed since then, per library, when nothing was removed there. */
    suspend fun sync(providerId: Long, source: PlaylistSource, loads: suspend (Kind) -> Boolean = { true }, onProgress: (String) -> Unit = {},
                     onKind: (Kind) -> Unit = {}, since: Long? = null): SyncResult {
        suspend fun kind(k: Kind, fetch: suspend () -> KindResult): KindResult {
            onKind(k)
            if (!loads(k)) { clear(providerId, k); return KindResult(0, 0, 0) }
            return fetch()
        }
        // English like the rest of the sync steps; counts tick per page / write chunk so a long sync visibly moves.
        fun n(i: Int) = "%,d".format(i)
        fun step(what: String, done: Int, total: Int?, savedSoFar: String) =
            onProgress("$what… ${n(done)}" + (total?.let { " of ${n(it)}" } ?: "") + (if (savedSoFar.isEmpty()) "" else " · $savedSoFar saved"))
        onProgress("Downloading channels…")
        val live = kind(Kind.LIVE) { syncLive(providerId, source.liveCategories(), source.liveChannels()) { d, t -> step("Saving channels", d, t, "") } }
        val liveSaved = if (live.items > 0) "${n(live.items)} channels" else ""
        onProgress("Downloading movies…" + if (liveSaved.isEmpty()) "" else " · $liveSaved saved")
        val vod = kind(Kind.MOVIE) {
            val cats = source.vodCategories()
            val lib = byLibrary(cats, { db.movieDao().byCategory(providerId) }, since, { it.remoteId }, { d, t -> step("Downloading movies", d, t, liveSaved) }) { c, s, f -> source.moviesIn(c, s, f) }
            syncMovies(providerId, cats, lib?.items ?: source.movies(), lib?.keep.orEmpty()) { d, t -> step("Saving movies", d, t, liveSaved) }
        }
        val vodSaved = listOf(liveSaved, if (vod.items > 0) "${n(vod.items)} movies" else "").filter { it.isNotEmpty() }.joinToString(", ")
        onProgress("Downloading series…" + if (vodSaved.isEmpty()) "" else " · $vodSaved saved")
        val ser = kind(Kind.SERIES) {
            val cats = source.seriesCategories()
            val lib = byLibrary(cats, { db.seriesDao().byCategory(providerId) }, since, { it.remoteId }, { d, t -> step("Downloading series", d, t, vodSaved) }) { c, s, f -> source.seriesIn(c, s, f) }
            syncSeries(providerId, cats, lib?.items ?: source.series(), lib?.keep.orEmpty()) { d, t -> step("Saving series", d, t, vodSaved) }
        }
        Kind.entries.forEach { if (!loads(it)) clear(providerId, it) } // switched off mid-sync; cheap when already empty
        return SyncResult(
            categories = live.categories + vod.categories + ser.categories,
            channels = live.items, movies = vod.items, series = ser.items,
            newlyHidden = live.newlyHidden + vod.newlyHidden + ser.newlyHidden,
            skipped = source.skipped(),
        )
    }

    /** Removes one kind of [providerId]'s catalogue (with its favourites/progress) without fetching anything. */
    suspend fun clear(providerId: Long, kind: Kind) {
        when (kind) {
            Kind.LIVE -> syncLive(providerId, emptyList(), emptyList())
            Kind.MOVIE -> syncMovies(providerId, emptyList(), emptyList())
            Kind.SERIES -> syncSeries(providerId, emptyList(), emptyList())
        }
    }

    /** Items with no category, or one the provider didn't list, go to a synthetic "Uncategorised" category. */
    private fun withUncategorised(cats: List<Category>, kind: Kind, itemCats: List<String?>): Pair<List<Category>, (String?) -> String> {
        val known = cats.mapTo(HashSet()) { it.remoteId }
        val resolve = { r: String? -> if (r != null && r in known) r else UNCATEGORISED }
        val needed = itemCats.any { it == null || it !in known }
        return (if (needed) cats + Category(UNCATEGORISED, "Uncategorised", kind) else cats) to resolve
    }

    private data class KindResult(val categories: Int, val items: Int, val newlyHidden: Int)
    private class Library<T>(val items: List<T>, val keep: Set<String>)

    /**
     * A media server's titles library by library (null when [cats] is empty or the source can't). With [since], a library
     * whose new titles plus the ones stored add up to its total had nothing removed: only its changes come back and its stored
     * titles stay ([Library.keep]); otherwise it's fetched whole. [progress] gets (titles so far, total when every library told).
     */
    private suspend fun <T> byLibrary(cats: List<Category>, stored: suspend () -> List<org.imdtv.iptvtv.data.db.CategoryItem>, since: Long?,
        id: (T) -> String, progress: (Int, Int?) -> Unit, fetch: suspend (String, Long?, (Int, Int?) -> Unit) -> LibraryPage<T>?): Library<T>? = coroutineScope {
        if (cats.isEmpty()) return@coroutineScope null
        val have = if (since == null) emptyMap() else stored().groupBy({ it.cat }, { it.remoteId }).mapValues { it.value.toHashSet() }
        val counts = java.util.concurrent.ConcurrentHashMap<String, Pair<Int, Int?>>()
        fun report(c: String): (Int, Int?) -> Unit = { done, total ->
            counts[c] = done to total
            val v = counts.values.toList()
            progress(v.sumOf { it.first }, if (v.size == cats.size && v.all { it.second != null }) v.sumOf { it.second!! } else null)
        }
        val libs = cats.map { c -> async {
            val p = fetch(c.remoteId, since, report(c.remoteId)) ?: return@async null
            val mine = have[c.remoteId].orEmpty()
            when {
                !p.changesOnly -> Library(p.items, emptySet())
                mine.size + p.items.count { id(it) !in mine } == p.total -> Library(p.items, setOf(c.remoteId))
                else -> Library(fetch(c.remoteId, null, report(c.remoteId))!!.items, emptySet()) // something was removed: the whole library
            }
        } }.awaitAll()
        if (libs.any { it == null }) null else Library(libs.flatMap { it!!.items }, libs.flatMapTo(HashSet()) { it!!.keep })
    }
    private data class CatResult(val idByRemote: Map<String, Long>, val newlyHidden: Int)

    private suspend fun syncCategories(providerId: Long, kind: Kind, incoming: List<Category>): CatResult {
        val dao = db.categoryDao()
        val hideRules = db.ruleDao().hideRules(providerId, kind).mapNotNull {
            runCatching { Regex(it.regex, RegexOption.IGNORE_CASE) }.getOrNull()
        }
        val existing = dao.byRemoteIds(providerId, kind).associateBy { it.remoteId }
        val language = keepLanguage()
        var nextOrder = (existing.values.maxOfOrNull { it.sortOrder } ?: -1) + 1
        var newlyHidden = 0
        val unique = incoming.distinctBy { it.remoteId }
        val rows = unique.map { c ->
            existing[c.remoteId]?.let { e ->
                val country = detectCountry(c.name)
                // Newly detected as another language (a country added to the list, a renamed category): hide it like a new one.
                // A category whose detection didn't change keeps the user's own Shown/Hidden choice.
                val nowForeign = language != null && country != e.country && !e.hidden && matchesLanguage(e.country, language) && !matchesLanguage(country, language)
                if (nowForeign) newlyHidden++
                e.copy(name = c.name, country = country, hidden = e.hidden || nowForeign)
            } ?: run {
                val country = detectCountry(c.name)
                val hidden = hideRules.any { it.containsMatchIn(c.name) } || (language != null && !matchesLanguage(country, language))
                if (hidden) newlyHidden++
                CategoryEntity(providerId = providerId, kind = kind, remoteId = c.remoteId, name = c.name,
                    sortOrder = nextOrder++, hidden = hidden, country = country)
            }
        }
        dao.upsert(rows)
        dao.deleteMissing(providerId, kind, unique.map { it.remoteId })
        return CatResult(dao.byRemoteIds(providerId, kind).associate { it.remoteId to it.id }, newlyHidden)
    }

    // No transaction around a whole kind (46k channels, 185k movies): new rows go in chunks, rows the provider changed
    // get a provider-columns-only update, unchanged rows aren't written at all, so user writes never wait long.
    private suspend fun syncLive(providerId: Long, cats: List<Category>, channels: List<org.imdtv.iptvtv.data.model.Channel>, saved: (Int, Int) -> Unit = { _, _ -> }): KindResult {
        val unique = channels.distinctBy { it.remoteId }
        val (allCats, resolve) = withUncategorised(cats, Kind.LIVE, unique.map { it.categoryRemoteId })
        val c = db.withTransaction { syncCategories(providerId, Kind.LIVE, allCats) }
        val dao = db.channelDao()
        val existing = dao.byProvider(providerId).associateBy { it.remoteId }
        val fresh = ArrayList<ChannelEntity>(); val changed = ArrayList<ChannelFromProvider>()
        unique.forEach { ch ->
            val catId = c.idByRemote[resolve(ch.categoryRemoteId)]
            val old = existing[ch.remoteId]
            if (old == null) fresh += ChannelEntity(providerId = providerId, categoryId = catId, remoteId = ch.remoteId, name = ch.name,
                logo = ch.logo, number = ch.number, tvgId = ch.tvgId, streamUrl = ch.streamUrl, drmType = ch.drm?.type, drmKey = ch.drm?.key, archiveDays = ch.archiveDays, catchupSource = ch.catchupSource)
            else ChannelFromProvider(old.id, ch.name, ch.logo, ch.number, ch.tvgId, ch.streamUrl, catId, ch.drm?.type, ch.drm?.key, ch.archiveDays, ch.catchupSource).let { n ->
                if (n != ChannelFromProvider(old.id, old.name, old.logo, old.number, old.tvgId, old.streamUrl, old.categoryId, old.drmType, old.drmKey, old.archiveDays, old.catchupSource)) changed += n
            }
        }
        val writes = fresh.size + changed.size
        fresh.chunked(WRITE_CHUNK).forEachIndexed { i, it -> dao.upsert(it); saved(minOf((i + 1) * WRITE_CHUNK, fresh.size), writes) }
        changed.chunked(WRITE_CHUNK).forEachIndexed { i, it -> dao.updateFromProvider(it); saved(fresh.size + minOf((i + 1) * WRITE_CHUNK, changed.size), writes) }
        val keep = unique.mapTo(HashSet()) { it.remoteId }
        existing.values.filter { it.remoteId !in keep }.map { it.id }.chunked(SQL_DELETE_CHUNK).forEach { ids ->
            db.withTransaction { dao.unmergeFrom(ids); dao.deleteIds(ids); db.favouriteDao().deleteForItems(providerId, Kind.LIVE, ids) }
        }
        ChannelMerge.applyAuto(db, providerId, mergeOptions()) // new duplicates join, gone ones leave
        return KindResult(c.idByRemote.size, unique.size, c.newlyHidden)
    }

    /** [keepCats]: categories whose stored titles not in [movies] stay (only their changes were fetched). */
    private suspend fun syncMovies(providerId: Long, cats: List<Category>, movies: List<org.imdtv.iptvtv.data.model.Movie>, keepCats: Set<String> = emptySet(),
                                   saved: (Int, Int) -> Unit = { _, _ -> }): KindResult {
        val unique = movies.distinctBy { it.remoteId }
        val (allCats, resolve) = withUncategorised(cats, Kind.MOVIE, unique.map { it.categoryRemoteId })
        val c = db.withTransaction { syncCategories(providerId, Kind.MOVIE, allCats) }
        val dao = db.movieDao()
        val existing = dao.byProvider(providerId).associateBy { it.remoteId }
        val t = now()
        val fresh = ArrayList<MovieEntity>(); val changed = ArrayList<MovieFromProvider>()
        unique.forEach { m ->
            val catId = c.idByRemote[resolve(m.categoryRemoteId)]
            val old = existing[m.remoteId]
            if (old == null) fresh += MovieEntity(providerId = providerId, categoryId = catId, remoteId = m.remoteId, name = m.name,
                poster = m.poster, year = m.year, rating = m.rating, streamUrl = m.streamUrl, addedAt = m.added ?: t)
            // The provider's date replaces when the app first saw it (all items of a fresh install share that).
            else MovieFromProvider(old.id, m.name, m.poster, m.year, m.rating, m.streamUrl, catId, m.added ?: old.addedAt).let { n ->
                if (n != MovieFromProvider(old.id, old.name, old.poster, old.year, old.rating, old.streamUrl, old.categoryId, old.addedAt)) changed += n
            }
        }
        val writes = fresh.size + changed.size
        fresh.chunked(WRITE_CHUNK).forEachIndexed { i, it -> dao.upsert(it); saved(minOf((i + 1) * WRITE_CHUNK, fresh.size), writes) }
        changed.chunked(WRITE_CHUNK).forEachIndexed { i, it -> dao.updateFromProvider(it); saved(fresh.size + minOf((i + 1) * WRITE_CHUNK, changed.size), writes) }
        val keep = unique.mapTo(HashSet()) { it.remoteId }
        val keepIds = keepCats.mapNotNullTo(HashSet()) { c.idByRemote[it] }
        val kept = existing.values.count { it.remoteId !in keep && it.categoryId in keepIds }
        existing.values.filter { it.remoteId !in keep && it.categoryId !in keepIds }.map { it.id }.chunked(SQL_DELETE_CHUNK).forEach { ids ->
            db.withTransaction {
                dao.deleteIds(ids)
                db.favouriteDao().deleteForItems(providerId, Kind.MOVIE, ids)
                db.progressDao().deleteForItems(providerId, Kind.MOVIE, ids)
            }
        }
        return KindResult(c.idByRemote.size, unique.size + kept, c.newlyHidden)
    }

    private suspend fun syncSeries(providerId: Long, cats: List<Category>, series: List<org.imdtv.iptvtv.data.model.Series>, keepCats: Set<String> = emptySet(),
                                   saved: (Int, Int) -> Unit = { _, _ -> }): KindResult {
        val unique = series.distinctBy { it.remoteId }
        val (allCats, resolve) = withUncategorised(cats, Kind.SERIES, unique.map { it.categoryRemoteId })
        val c = db.withTransaction { syncCategories(providerId, Kind.SERIES, allCats) }
        val dao = db.seriesDao()
        val existing = dao.byProvider(providerId).associateBy { it.remoteId }
        val t = now()
        val fresh = ArrayList<SeriesEntity>(); val changed = ArrayList<SeriesFromProvider>()
        unique.forEach { s ->
            val catId = c.idByRemote[resolve(s.categoryRemoteId)]
            val old = existing[s.remoteId]
            if (old == null) fresh += SeriesEntity(providerId = providerId, categoryId = catId, remoteId = s.remoteId, name = s.name,
                poster = s.poster, year = s.year, rating = s.rating, addedAt = s.added ?: t)
            else SeriesFromProvider(old.id, s.name, s.poster, s.year, s.rating, catId, s.added ?: old.addedAt).let { n ->
                if (n != SeriesFromProvider(old.id, old.name, old.poster, old.year, old.rating, old.categoryId, old.addedAt)) changed += n
            }
        }
        val writes = fresh.size + changed.size
        fresh.chunked(WRITE_CHUNK).forEachIndexed { i, it -> dao.upsert(it); saved(minOf((i + 1) * WRITE_CHUNK, fresh.size), writes) }
        changed.chunked(WRITE_CHUNK).forEachIndexed { i, it -> dao.updateFromProvider(it); saved(fresh.size + minOf((i + 1) * WRITE_CHUNK, changed.size), writes) }
        val keep = unique.mapTo(HashSet()) { it.remoteId }
        val keepIds = keepCats.mapNotNullTo(HashSet()) { c.idByRemote[it] }
        val kept = existing.values.count { it.remoteId !in keep && it.categoryId in keepIds }
        existing.values.filter { it.remoteId !in keep && it.categoryId !in keepIds }.map { it.id }.chunked(SQL_DELETE_CHUNK).forEach { ids ->
            db.withTransaction {
                dao.deleteIds(ids)
                db.favouriteDao().deleteForItems(providerId, Kind.SERIES, ids)
                db.progressDao().deleteForItems(providerId, Kind.SERIES, ids) // series progress rows are keyed by the series id
            }
        }
        return KindResult(c.idByRemote.size, unique.size + kept, c.newlyHidden)
    }
}

private const val WRITE_CHUNK = 2_000
private const val SQL_DELETE_CHUNK = 900 // SQLite's bound-variable limit
