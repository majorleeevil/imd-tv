package org.imdtv.iptvtv.data.source

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
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

// --- Jellyfin JSON (only the fields the app reads) ---

@Serializable data class JfInfo(@SerialName("ServerName") val name: String = "Jellyfin")
@Serializable data class JfQuickConnect(@SerialName("Code") val code: String = "", @SerialName("Secret") val secret: String = "", @SerialName("Authenticated") val authenticated: Boolean = false)
@Serializable data class JfUser(@SerialName("Id") val id: String)
@Serializable data class JfAuth(@SerialName("AccessToken") val token: String, @SerialName("User") val user: JfUser)
@Serializable data class JfPerson(@SerialName("Name") val name: String = "", @SerialName("Type") val type: String = "")
@Serializable data class JfItem(
    @SerialName("Id") val id: String, @SerialName("Name") val name: String = "", @SerialName("CollectionType") val collection: String? = null,
    @SerialName("ProductionYear") val year: Int? = null, @SerialName("CommunityRating") val rating: Double? = null,
    @SerialName("DateCreated") val created: String? = null, @SerialName("ImageTags") val images: Map<String, String> = emptyMap(),
    @SerialName("BackdropImageTags") val backdrops: List<String> = emptyList(), @SerialName("Overview") val overview: String? = null,
    @SerialName("Genres") val genres: List<String> = emptyList(), @SerialName("People") val people: List<JfPerson> = emptyList(),
    @SerialName("RunTimeTicks") val ticks: Long? = null, @SerialName("Height") val height: Int? = null,
    @SerialName("ParentIndexNumber") val season: Int? = null, @SerialName("IndexNumber") val number: Int? = null,
    @SerialName("LocationType") val location: String? = null,
)
@Serializable data class JfItems(@SerialName("Items") val items: List<JfItem> = emptyList(), @SerialName("TotalRecordCount") val total: Int? = null)

/** The server address as typed (scheme optional, maybe copied from the web app's address bar) as the API base. */
fun jellyfinBase(typed: String): String {
    val t = typed.trim().substringBefore('#').replace(Regex("/web(/.*)?$"), "").trimEnd('/')
    return if ("://" in t) t else "http://$t"
}

/** The Authorization header Jellyfin wants on every call; [token] once signed in. */
fun jellyfinAuthHeader(device: String, deviceId: String, version: String) =
    "MediaBrowser Client=\"IMD TV\", Device=\"${device.replace("\"", "")}\", DeviceId=\"$deviceId\", Version=\"$version\""

class JellyfinException(val code: Int, path: String = "") : java.io.IOException("Jellyfin request failed: HTTP $code $path"), UiMessage {
    override val resId: Int? get() = if (code == 401) R.string.set_b_jf_wrong_login else R.string.set_b_jf_failed
    override val args: List<Any> get() = if (code == 401) emptyList() else listOf(code)
}

private suspend fun <T> OkHttpClient.jf(json: Json, r: Request, s: DeserializationStrategy<T>): T = withContext(Dispatchers.IO) {
    newCall(r).execute().use { resp ->
        if (!resp.isSuccessful) throw JellyfinException(resp.code, r.url.encodedPath)
        @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
        resp.body!!.byteStream().use { json.decodeFromStream(s, it) }
    }
}

/** Sign-in: Quick Connect (a code approved in another signed-in Jellyfin app) or username + password. */
class JellyfinAuth(private val http: OkHttpClient, private val json: Json, private val header: String) {
    private fun req(url: String, token: String? = null) = Request.Builder().url(url).header("Accept", "application/json")
        .header("Authorization", if (token == null) header else "$header, Token=\"$token\"")
    private fun body(vararg kv: Pair<String, String>) =
        kotlinx.serialization.json.JsonObject(kv.associate { (k, v) -> k to kotlinx.serialization.json.JsonPrimitive(v) }).toString()
            .toRequestBody("application/json".toMediaType())

    /**
     * The address to use (after redirects: "nas:8096" may land on https, and a redirected sign-in POST turns into a GET)
     * and the server's name; throws when nothing at [base] answers like Jellyfin.
     */
    suspend fun resolve(base: String): Pair<String, String> = withContext(Dispatchers.IO) {
        http.newCall(req("$base/System/Info/Public").build()).execute().use { resp ->
            if (!resp.isSuccessful) throw JellyfinException(resp.code, resp.request.url.encodedPath)
            val name = json.decodeFromString(JfInfo.serializer(), resp.body!!.string()).name
            resp.request.url.toString().substringBefore("/System/Info/Public") to name
        }
    }
    suspend fun quickConnectEnabled(base: String): Boolean =
        runCatching { http.jf(json, req("$base/QuickConnect/Enabled").build(), Boolean.serializer()) }.getOrDefault(false)
    suspend fun initiate(base: String): JfQuickConnect = http.jf(json, req("$base/QuickConnect/Initiate").post(ByteArray(0).toRequestBody()).build(), JfQuickConnect.serializer())
    /** True once the code was approved; throws [JellyfinException] 404 when it expired. */
    suspend fun approved(base: String, secret: String): Boolean =
        http.jf(json, req("$base/QuickConnect/Connect?secret=$secret").build(), JfQuickConnect.serializer()).authenticated
    suspend fun withQuickConnect(base: String, secret: String): JfAuth =
        http.jf(json, req("$base/Users/AuthenticateWithQuickConnect").post(body("Secret" to secret)).build(), JfAuth.serializer())
    suspend fun withPassword(base: String, user: String, password: String): JfAuth =
        http.jf(json, req("$base/Users/AuthenticateByName").post(body("Username" to user, "Pw" to password)).build(), JfAuth.serializer())

    /** Movie and show libraries as (id, title, kind). */
    suspend fun libraries(base: String, a: JfAuth): List<Triple<String, String, Kind>> =
        http.jf(json, req("$base/Users/${a.user.id}/Views", a.token).build(), JfItems.serializer()).items.mapNotNull { v ->
            when (v.collection) { "movies" -> Kind.MOVIE; "tvshows" -> Kind.SERIES; else -> null }?.let { Triple(v.id, v.name, it) }
        }
}

/**
 * A Jellyfin server's Movies and TV Shows libraries as a playlist's VOD (no live). Saved stream links carry
 * `api_key=PASS_TOKEN` and the player puts the token back (withLogin); images need no token.
 */
class JellyfinSource(
    baseUrl: String, private val token: String, private val userId: String, header: String, private val skip: Set<String>,
    private val http: OkHttpClient, private val json: Json,
) : PlaylistSource {
    private val base = baseUrl.trim().trimEnd('/')
    private val auth = "$header, Token=\"$token\""

    private fun url(path: String, vararg q: Pair<String, String>): HttpUrl =
        "$base$path".toHttpUrl().newBuilder().apply { q.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
    private suspend fun <T> get(u: HttpUrl, s: DeserializationStrategy<T>): T =
        http.jf(json, Request.Builder().url(u).header("Accept", "application/json").header("Authorization", auth).build(), s)

    private var viewCache: List<JfItem>? = null
    private suspend fun views(type: String) = (viewCache ?: get(url("/Users/$userId/Views"), JfItems.serializer()).items.also { viewCache = it })
        .filter { it.collection == type && it.id !in skip }

    // shortcut: a full fetch every sync, like Plex; ask only for items created since the last sync (MinDateLastSaved) if it bites.
    private suspend fun all(parent: String, type: String): List<JfItem> {
        val out = ArrayList<JfItem>()
        while (true) {
            val page = get(url("/Users/$userId/Items", "ParentId" to parent, "IncludeItemTypes" to type, "Recursive" to "true",
                "Fields" to "DateCreated", "SortBy" to "SortName", "EnableUserData" to "false", "EnableImageTypes" to "Primary", "ImageTypeLimit" to "1",
                "StartIndex" to "${out.size}", "Limit" to "$PAGE"), JfItems.serializer())
            out += page.items // StartIndex by the items returned: a server may send fewer per page
            if (page.items.isEmpty() || out.size >= (page.total ?: 0)) return out
        }
    }

    fun stream(id: String) = url("/Videos/$id/stream", "static" to "true", "api_key" to PASS_TOKEN).toString()
    private fun image(item: JfItem, w: Int) = if ("Primary" in item.images) url("/Items/${item.id}/Images/Primary", "maxWidth" to "$w").toString() else null
    private fun backdrop(item: JfItem) = if (item.backdrops.isNotEmpty()) url("/Items/${item.id}/Images/Backdrop", "maxWidth" to "1280").toString() else null
    private fun JfItem.added() = created?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
    private fun JfItem.secs() = ticks?.let { (it / 10_000_000).toInt() }
    private fun JfItem.movie(cat: String?) = Movie(id, cat, name, image(this, 300), year, rating, stream(id), added())
    private fun JfItem.series(cat: String?) = Series(id, cat, name, image(this, 300), year, rating, added())
    private fun List<String>.text() = joinToString().ifEmpty { null }

    override suspend fun liveCategories(): List<Category> = emptyList()
    override suspend fun liveChannels(): List<Channel> = emptyList()
    override fun epgUrls(): List<String> = emptyList()
    override suspend fun vodCategories() = views("movies").map { Category(it.id, it.name, Kind.MOVIE) }
    override suspend fun seriesCategories() = views("tvshows").map { Category(it.id, it.name, Kind.SERIES) }
    override suspend fun movies() = views("movies").flatMap { v -> all(v.id, "Movie").map { it.movie(v.id) } }
    override suspend fun series() = views("tvshows").flatMap { v -> all(v.id, "Series").map { it.series(v.id) } }

    override suspend fun movieDetail(id: String): MovieDetail {
        val m = get(url("/Users/$userId/Items/$id"), JfItem.serializer())
        return MovieDetail(m.movie(null), backdrop(m), m.overview, m.genres.text(), m.people.filter { it.type == "Director" }.map { it.name }.text(),
            m.people.filter { it.type == "Actor" }.take(6).map { it.name }.text(), m.secs(), m.height)
    }

    override suspend fun seriesDetail(id: String): SeriesDetail {
        val s = get(url("/Users/$userId/Items/$id"), JfItem.serializer())
        val eps = get(url("/Shows/$id/Episodes", "UserId" to userId, "Fields" to "Overview"), JfItems.serializer()).items
            .filter { it.location != "Virtual" } // listed as missing, no file
            .map { e -> Episode(e.id, e.season ?: 0, e.number ?: 0, e.name, e.overview, image(e, 400), e.secs(), stream(e.id)) }
        return SeriesDetail(s.series(null), backdrop(s), s.overview, s.genres.text(), eps)
    }

    companion object { const val PAGE = 2000 }
}
