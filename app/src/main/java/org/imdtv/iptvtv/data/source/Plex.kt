package org.imdtv.iptvtv.data.source

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
@Serializable data class PlexStream(val streamType: Int = 0, val key: String? = null, val codec: String = "", val languageCode: String? = null, val displayTitle: String? = null)
@Serializable data class PlexPart(val key: String, @SerialName("Stream") val streams: List<PlexStream> = emptyList())
@Serializable data class PlexMedia(val height: Int? = null, @SerialName("Part") val parts: List<PlexPart> = emptyList())
@Serializable data class PlexItem(
    val ratingKey: String, val title: String = "", val year: Int? = null, val rating: Double? = null, val audienceRating: Double? = null,
    val addedAt: Long? = null, val thumb: String? = null, val art: String? = null, val summary: String? = null, val duration: Long? = null,
    val parentIndex: Int? = null, val index: Int? = null,
    val type: String = "", val viewOffset: Long? = null, val lastViewedAt: Long? = null, val grandparentRatingKey: String? = null,
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
            if (!resp.isSuccessful) throw PlexException(resp.code, r.url.encodedPath)
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

class PlexException(val code: Int, path: String = "") : java.io.IOException("Plex request failed: HTTP $code $path"), UiMessage {
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
            if (!resp.isSuccessful) throw PlexException(resp.code, r.url.encodedPath)
            @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
            resp.body!!.byteStream().use { json.decodeFromStream(PlexResponse.serializer(), it) }.container
        }
    }

    private var sectionCache: List<PlexDir>? = null
    private suspend fun sections(type: String) = (sectionCache ?: get(url("/library/sections")).dirs.also { sectionCache = it })
        .filter { it.type == type && it.key !in skip }

    // A shared (appbox) server, 22 Nov: movies ~1.3 ms each, shows 4–30 ms each server-side depending on its load, so a 2000-show
    // page passed the 60 s read timeout. Shows go 500 a page; up to 3 requests in flight across all libraries hide the latency.
    private val inFlight = Semaphore(3)

    /**
     * Library [key]'s items (only those updated since [since], epoch ms, when given) and how many the library holds: the first
     * page gives the count, the rest are fetched a few at a time, in order.
     */
    // shortcut: a replaced file is assumed to bump updatedAt; if a stale part key ever shows up, force a full sync now and then.
    private suspend fun all(key: String, page: Int, since: Long?, fetched: (Int, Int?) -> Unit): LibraryPage<PlexItem> = coroutineScope {
        val t0 = System.currentTimeMillis()
        val filter = since?.let { "updatedAt>>" to "${it / 1000}" }
        suspend fun at(start: Int, size: Int = page, q: Pair<String, String>? = filter) = inFlight.withPermit {
            get(url("/library/sections/$key/all", *listOfNotNull(q, "X-Plex-Container-Start" to "$start", "X-Plex-Container-Size" to "$size",
                "excludeFields" to "summary", "includeGuids" to "0", "excludeElements" to "Genre,Country,Director,Writer,Role,Collection,Label,Image,UltraBlurColors").toTypedArray()))
        }
        val first = at(0)
        val total = if (since == null) first.totalSize ?: 0 else at(0, size = 0, q = null).totalSize ?: 0
        val step = first.items.size // not page: a server may send fewer per page
        val done = java.util.concurrent.atomic.AtomicInteger(step).also { fetched(step, first.totalSize) } // progress counts what this fetch brings
        val rest = if (step == 0) emptyList() else (step until (first.totalSize ?: 0) step step).map { async { at(it).items.also { fetched(done.addAndGet(it.size), first.totalSize) } } }.awaitAll()
        val items = first.items + rest.flatten()
        Log.i("Plex", "Library $key: ${items.size}${if (since != null) " changed" else ""} of $total in ${System.currentTimeMillis() - t0} ms")
        LibraryPage(items, total, since != null)
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
    override suspend fun movies() = coroutineScope { sections("movie").map { s -> async { moviesIn(s.key, null) { _, _ -> }.items } }.awaitAll().flatten() }
    override suspend fun series() = coroutineScope { sections("show").map { s -> async { seriesIn(s.key, null) { _, _ -> }.items } }.awaitAll().flatten() }
    override suspend fun moviesIn(cat: String, since: Long?, fetched: (Int, Int?) -> Unit) =
        all(cat, PAGE, since, fetched).let { p -> LibraryPage(p.items.map { it.movie(cat) }, p.total, p.changesOnly) }
    override suspend fun seriesIn(cat: String, since: Long?, fetched: (Int, Int?) -> Unit) =
        all(cat, SHOW_PAGE, since, fetched).let { p -> LibraryPage(p.items.map { it.series(cat) }, p.total, p.changesOnly) }

    /** On Deck: part-watched movies and episodes (next-up episodes with no position are left out). */
    override suspend fun resumePoints() = get(url("/library/onDeck")).items.mapNotNull { m ->
        val off = m.viewOffset ?: return@mapNotNull null
        val at = (m.lastViewedAt ?: 0) * 1000
        when (m.type) {
            "movie" -> ResumePoint(Kind.MOVIE, m.ratingKey, "", off, m.duration ?: 0, at)
            "episode" -> m.grandparentRatingKey?.let { ResumePoint(Kind.SERIES, it, m.ratingKey, off, m.duration ?: 0, at) }
            else -> null
        }
    }

    // The calls Plex clients use: scrobble / unscrobble mark (un)watched, progress saves a position ("stopped" = not playing now).
    override suspend fun reportProgress(p: ProgressReport) {
        val key = p.episode.ifEmpty { p.remoteId }
        val lib = "identifier" to "com.plexapp.plugins.library"
        val u = when {
            p.watched -> url("/:/scrobble", "key" to key, lib)
            p.positionMs > 0 -> url("/:/progress", "key" to key, lib, "time" to "${p.positionMs}", "state" to "stopped")
            else -> url("/:/unscrobble", "key" to key, lib)
        }
        withContext(Dispatchers.IO) {
            http.newCall(Request.Builder().url(u).header("X-Plex-Token", token).header("X-Plex-Client-Identifier", clientId).build()).execute()
                .use { if (!it.isSuccessful) throw PlexException(it.code, u.encodedPath) }
        }
    }

    /** #47: Plex lists a sidecar .srt/.ass/.vtt as a subtitle stream (type 3) with its own key; embedded ones have none. */
    override suspend fun subtitles(id: String) = get(url("/library/metadata/$id")).items.firstOrNull()?.media?.firstOrNull()?.parts?.firstOrNull()
        ?.streams.orEmpty().mapNotNull { st ->
            val key = st.key?.takeIf { st.streamType == 3 } ?: return@mapNotNull null
            val mime = when (st.codec.lowercase()) { "srt" -> "application/x-subrip"; "ass", "ssa" -> "text/x-ssa"; "vtt", "webvtt" -> "text/vtt"; else -> return@mapNotNull null }
            // encoding: Plex re-encodes the file to UTF-8 (the player assumes UTF-8; a cp1255 .srt showed as boxes).
            SideSubtitle(url(key, "encoding" to "utf-8", "X-Plex-Token" to PASS_TOKEN).toString(), mime, st.languageCode, st.displayTitle)
        }

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

    companion object { const val PAGE = 2000; const val SHOW_PAGE = 500 }
}
