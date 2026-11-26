package org.imdtv.iptvtv.data.source

import org.imdtv.iptvtv.data.model.Category
import org.imdtv.iptvtv.data.model.Channel
import org.imdtv.iptvtv.data.model.Movie
import org.imdtv.iptvtv.data.model.MovieDetail
import org.imdtv.iptvtv.data.model.Series
import org.imdtv.iptvtv.data.model.SeriesDetail

/** A provider's catalogue (spec §4.1). Stream URLs are carried on the models. */
interface PlaylistSource {
    suspend fun liveCategories(): List<Category>
    suspend fun liveChannels(): List<Channel>
    suspend fun vodCategories(): List<Category>
    suspend fun movies(): List<Movie>
    suspend fun movieDetail(id: String): MovieDetail
    suspend fun seriesCategories(): List<Category>
    suspend fun series(): List<Series>
    suspend fun seriesDetail(id: String): SeriesDetail
    fun epgUrls(): List<String>
    /** Malformed entries dropped while reading the source (call after the lists). */
    suspend fun skipped(): Int = 0

    /**
     * Media servers: one library's (category's) titles, all of them or, with [since] (epoch ms), only those added or changed
     * since then. [fetched] gets (titles so far, the library's total or null). Null = not supported: sync uses movies().
     */
    suspend fun moviesIn(cat: String, since: Long?, fetched: (Int, Int?) -> Unit): LibraryPage<Movie>? = null
    suspend fun seriesIn(cat: String, since: Long?, fetched: (Int, Int?) -> Unit): LibraryPage<Series>? = null

    /** Media servers: what the account is part-way through on the server itself, for Continue watching. */
    suspend fun resumePoints(): List<ResumePoint> = emptyList()

    /** Media servers: tell the server where this title was left (Continue watching both ways). Others: nothing. */
    suspend fun reportProgress(p: ProgressReport) {}

    /** Media servers: subtitle files kept beside movie/episode [id] (not inside the video), for the player to side-load. */
    suspend fun subtitles(id: String): List<SideSubtitle> = emptyList()
}

/** An external subtitle file: [url] may hold PASS_TOKEN (the player puts the login back); [language] ISO code or null. */
data class SideSubtitle(val url: String, val mime: String, val language: String?, val label: String?)

/** Where a movie ([episode] "") or episode of series [remoteId] was left: [watched], or [positionMs] (0 and not watched = forget it). */
data class ProgressReport(val kind: org.imdtv.iptvtv.data.model.Kind, val remoteId: String, val episode: String, val positionMs: Long, val durationMs: Long,
                          val watched: Boolean, val title: String, val poster: String?)

/** A part-watched movie ([episode] "") or episode of series [remoteId], as the server last saw it ([at] epoch ms). */
data class ResumePoint(val kind: org.imdtv.iptvtv.data.model.Kind, val remoteId: String, val episode: String, val positionMs: Long, val durationMs: Long, val at: Long)

/** A library's titles; [changesOnly] = only those changed since the asked time, out of [total] the library holds now. */
class LibraryPage<T>(val items: List<T>, val total: Int, val changesOnly: Boolean)
