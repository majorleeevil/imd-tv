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
}
