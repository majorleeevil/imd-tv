package org.imdtv.iptvtv.data.source

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.imdtv.iptvtv.R
import org.imdtv.iptvtv.data.model.Category
import org.imdtv.iptvtv.data.model.Channel
import org.imdtv.iptvtv.data.model.Episode
import org.imdtv.iptvtv.data.model.Kind
import org.imdtv.iptvtv.data.model.Movie
import org.imdtv.iptvtv.data.model.MovieDetail
import org.imdtv.iptvtv.data.model.Series
import org.imdtv.iptvtv.data.model.SeriesDetail
import org.imdtv.iptvtv.data.security.PASS_TOKEN
import java.util.concurrent.TimeUnit

// --- plex.tv and server JSON (only the fields the app reads) ---

@Serializable data class PlexPin(val id: Long, val code: String, val authToken: String? = null)
@Serializable data class PlexConnection(val uri: String, val local: Boolean = false, val relay: Boolean = false, val protocol: String = "")
@Serializable data class PlexResource(
    val name: String, val clientIdentifier: String, val provides: String = "", val accessToken: String? = null,
    val owned: Boolean = false, val connections: List<PlexConnection> = emptyList(),
)
@Serializable data class PlexResponse(@SerialName("MediaContainer") val container: PlexContainer = PlexContainer())
@Serializable data class PlexContainer(
    val totalSize: Int? = null,
    @SerialName("Metadata") val items: List<PlexItem> = emptyList(),
    @SerialName("Directory") val dirs: List<PlexDir> = emptyList(),
)
@Serializable data class PlexDir(val key: String, val type: String = "", val title: String = "")
@Serializable data class PlexTag(val tag: String = "")
@Serializable data class PlexPart(val key: String)
@Serializable data class PlexMedia(val height: Int? = null, @SerialName("Part") val parts: List<PlexPart> = emptyList())
@Serializable data class PlexItem(
    val ratingKey: String, val title: String = "", val year: Int? = null, val rating: Double? = null, val audienceRating: Double? = null,
    val addedAt: Long? = null, val thumb: String? = null, val art: String? = null, val summary: String? = null, val duration: Long? = null,
    val parentIndex: Int? = null, val index: Int? = null,
    @SerialName("Media") val media: List<PlexMedia> = emptyList(),
    @SerialName("Genre") val genres: List<PlexTag> = emptyList(),
    @SerialName("Director") val directors: List<PlexTag> = emptyList(),
    @SerialName("Role") val roles: List<PlexTag> = emptyList(),
)

/** A server the account can use, with the token for it (a shared server has its own) and its addresses, best first. */
data class PlexServer(val name: String, val clientId: String, val token: String, val uris: List<String>)

/** Library titles left unticked when adding a server (the user can tick them). */
fun plexSkipByDefault(title: String) = Regex("adult|xxx|porn", RegexOption.IGNORE_CASE).containsMatchIn(title)

/** Addresses in the order to try: direct before relay, local before remote, https before http. */
fun orderedUris(c: List<PlexConnection>): List<String> =
    c.sortedWith(compareBy({ it.relay }, { !it.local }, { it.protocol != "https" })).map { it.uri.trimEnd('/') }

/** plex.tv sign-in by link code (plex.tv/link): no password ever goes through the app. */
class PlexAuth(private val http: OkHttpClient, private val json: Json, private val clientId: String, private val product: String) {
    private fun req(url: String, token: String? = null) = Request.Builder().url(url)
        .header("Accept", "application/json").header("X-Plex-Client-Identifier", clientId).header("X-Plex-Product", product)
        .apply { token?.let { header("X-Plex-Token", it) } }

    private suspend fun <T> call(r: Request, s: DeserializationStrategy<T>): T = withContext(Dispatchers.IO) {
        http.newCall(r).execute().use { resp ->
            if (!resp.isSuccessful) throw PlexException(resp.code)
            json.decodeFromString(s, resp.body!!.string())
        }
    }

    suspend fun createPin(): PlexPin = call(req("https://plex.tv/api/v2/pins?strong=false").post(ByteArray(0).toRequestBody()).build(), PlexPin.serializer())

    /** The account token once the code was entered at plex.tv/link; null while waiting. Throws [PlexException] 404 when the code expired. */
    suspend fun pollPin(id: Long): String? = call(req("https://plex.tv/api/v2/pins/$id").build(), PlexPin.serializer()).authToken

    suspend fun servers(accountToken: String): List<PlexServer> =
        call(req("https://plex.tv/api/v2/resources?includeHttps=1&includeRelay=1", accountToken).build(), ListSerializer(PlexResource.serializer()))
            .filter { "server" in it.provides.split(',') }
            .map { PlexServer(it.name, it.clientIdentifier, it.accessToken ?: accountToken, orderedUris(it.connections)) }

    /** The first address that answers (a shared server's local address is someone else's network); null if none does. */
    suspend fun reachable(s: PlexServer): String? = withContext(Dispatchers.IO) {
        val quick = http.newBuilder().connectTimeout(4, TimeUnit.SECONDS).readTimeout(4, TimeUnit.SECONDS).build()
        s.uris.firstOrNull { uri -> runCatching { quick.newCall(req("$uri/identity", s.token).build()).execute().use { it.isSuccessful } }.getOrDefault(false) }
    }

    /** Movie and show libraries as (key, title, kind). */
    suspend fun libraries(base: String, token: String): List<Triple<String, String, Kind>> =
        call(req("$base/library/sections", token).build(), PlexResponse.serializer()).container.dirs.mapNotNull { d ->
            when (d.type) { "movie" -> Kind.MOVIE; "show" -> Kind.SERIES; else -> null }?.let { Triple(d.key, d.title, it) }
        }
}

class PlexException(val code: Int) : java.io.IOException("Plex request failed: HTTP $code"), UiMessage {
    override val resId: Int? get() = R.string.set_b_plex_failed
    override val args: List<Any> get() = listOf(code)
}

/**
 * A Plex server's Movies and TV Shows libraries as a playlist's VOD (no live). [token] is the server's own token; saved
 * links carry PASS_TOKEN instead and the player / image loader put it back (withLogin), so the database never holds it.
 */
class PlexSource(
    baseUrl: String, private val token: String, private val clientId: String, private val skip: Set<String>,
    private val http: OkHttpClient, private val json: Json,
) : PlaylistSource {
    private val base = baseUrl.trim().trimEnd('/')

    private fun url(path: String, vararg q: Pair<String, String>): HttpUrl =
        "$base$path".toHttpUrl().newBuilder().apply { q.forEach { (k, v) -> addQueryParameter(k, v) } }.build()

    private suspend fun get(u: HttpUrl): PlexContainer = withContext(Dispatchers.IO) {
        val r = Request.Builder().url(u).header("Accept", "application/json").header("X-Plex-Token", token)
            .header("X-Plex-Client-Identifier", clientId).build()
        http.newCall(r).execute().use { resp ->
            if (!resp.isSuccessful) throw PlexException(resp.code)
            @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
            resp.body!!.byteStream().use { json.decodeFromStream(PlexResponse.serializer(), it) }.container
        }
    }

    private var sectionCache: List<PlexDir>? = null
    private suspend fun sections(type: String) = (sectionCache ?: get(url("/library/sections")).dirs.also { sectionCache = it })
        .filter { it.type == type && it.key !in skip }

    /** Every item of library [key], a page at a time (the user's server: 95k movies, 11 MB per 5000). */
    // shortcut: a full fetch every sync (user's server: 108k movies 34 s, 34k shows ~200 s, server-side slow); fetch only addedAt/updatedAt>last sync if it bites.
    private suspend fun all(key: String): List<PlexItem> {
        val out = ArrayList<PlexItem>()
        var start = 0
        while (true) {
            val c = get(url("/library/sections/$key/all", "X-Plex-Container-Start" to "$start", "X-Plex-Container-Size" to "$PAGE",
                "excludeFields" to "summary", "includeGuids" to "0", "excludeElements" to "Genre,Country,Director,Writer,Role,Collection,Label,Image,UltraBlurColors"))
            out += c.items
            start += c.items.size // not PAGE: a server may send fewer per page
            if (c.items.isEmpty() || start >= (c.totalSize ?: 0)) return out
        }
    }

    fun stream(partKey: String) = url(partKey, "X-Plex-Token" to PASS_TOKEN).toString()
    private fun image(path: String?, w: Int, h: Int) = path?.let {
        url("/photo/:/transcode", "width" to "$w", "height" to "$h", "minSize" to "1", "upscale" to "1", "url" to it, "X-Plex-Token" to PASS_TOKEN).toString()
    }
    private fun PlexItem.part() = media.firstOrNull()?.parts?.firstOrNull()?.key
    private fun PlexItem.movie(cat: String?) = Movie(ratingKey, cat, title, image(thumb, 300, 450), year, audienceRating ?: rating, part()?.let(::stream), addedAt?.times(1000))
    private fun PlexItem.series(cat: String?) = Series(ratingKey, cat, title, image(thumb, 300, 450), year, audienceRating ?: rating, addedAt?.times(1000))

    override suspend fun liveCategories(): List<Category> = emptyList()
    override suspend fun liveChannels(): List<Channel> = emptyList()
    override fun epgUrls(): List<String> = emptyList()
    override suspend fun vodCategories() = sections("movie").map { Category(it.key, it.title, Kind.MOVIE) }
    override suspend fun seriesCategories() = sections("show").map { Category(it.key, it.title, Kind.SERIES) }
    override suspend fun movies() = sections("movie").flatMap { s -> all(s.key).map { it.movie(s.key) } }
    override suspend fun series() = sections("show").flatMap { s -> all(s.key).map { it.series(s.key) } }

    override suspend fun movieDetail(id: String): MovieDetail {
        val m = get(url("/library/metadata/$id")).items.first()
        return MovieDetail(m.movie(null), image(m.art, 1280, 720), m.summary, m.genres.joinToString { it.tag }.ifEmpty { null },
            m.directors.joinToString { it.tag }.ifEmpty { null }, m.roles.take(6).joinToString { it.tag }.ifEmpty { null },
            m.duration?.let { (it / 1000).toInt() }, m.media.firstOrNull()?.height)
    }

    override suspend fun seriesDetail(id: String): SeriesDetail {
        val s = get(url("/library/metadata/$id")).items.first()
        val eps = get(url("/library/metadata/$id/allLeaves")).items.mapNotNull { e ->
            e.part()?.let { Episode(e.ratingKey, e.parentIndex ?: 0, e.index ?: 0, e.title, e.summary, image(e.thumb, 400, 225), e.duration?.let { (it / 1000).toInt() }, stream(it)) }
        }
        return SeriesDetail(s.series(null), image(s.art, 1280, 720), s.summary, s.genres.joinToString { it.tag }.ifEmpty { null }, eps)
    }

    companion object { const val PAGE = 2000 }
}
