package org.imdtv.iptvtv.data.source

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.imdtv.iptvtv.data.model.Category
import org.imdtv.iptvtv.data.model.Channel
import org.imdtv.iptvtv.data.model.Episode
import org.imdtv.iptvtv.data.model.Kind
import org.imdtv.iptvtv.data.model.Movie
import org.imdtv.iptvtv.data.model.MovieDetail
import org.imdtv.iptvtv.data.model.Series
import org.imdtv.iptvtv.data.model.SeriesDetail
import java.io.IOException
import java.net.URLDecoder
import java.net.URLEncoder

/** A Stalker portal problem; [message] is shown to the user as is. */
class StalkerException(message: String) : Exception(message)

/** The box identity a Stalker portal knows the user by. Stored as provider username = MAC, password = "serial\ndeviceId". */
data class StalkerLogin(val mac: String, val serial: String? = null, val deviceId: String? = null) {
    fun packed(): String? = listOf(serial.orEmpty().trim(), deviceId.orEmpty().trim()).takeIf { l -> l.any { it.isNotEmpty() } }?.joinToString("\n")

    companion object {
        fun from(username: String?, password: String?) = password.orEmpty().split('\n', limit = 2).let {
            StalkerLogin(username.orEmpty().trim().uppercase(), it[0].trim().ifEmpty { null }, it.getOrNull(1)?.trim()?.ifEmpty { null })
        }
    }
}

private val MAC = Regex("^([0-9A-F]{2}:){5}[0-9A-F]{2}$")
fun isValidMac(mac: String) = MAC.matches(mac.trim().uppercase())

/** The portal root: what is left after the usual client paths (`/c/`, `/portal.php`, `/stalker_portal/…`). */
fun portalBase(url: String): String {
    var u = url.trim().trimEnd('/')
    for (suffix in listOf("/c", "/portal.php", "/server/load.php", "/stalker_portal")) u = u.removeSuffix(suffix).trimEnd('/')
    return u
}

/** Saved links hold no token or MAC: `stalker://<providerId>/<itv|vod>?cmd=…[&series=n]`, resolved per play with create_link. */
data class StalkerLink(val providerId: Long, val module: String, val cmd: String, val series: Int? = null) {
    override fun toString() = "stalker://$providerId/$module?cmd=" + URLEncoder.encode(cmd, "UTF-8") + (series?.let { "&series=$it" } ?: "")

    companion object {
        fun parse(s: String): StalkerLink? {
            if (!s.startsWith("stalker://")) return null
            val rest = s.removePrefix("stalker://")
            val id = rest.substringBefore('/').toLongOrNull() ?: return null
            val module = rest.substringAfter('/').substringBefore('?')
            val q = rest.substringAfter('?', "").split('&').associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('=', ""), "UTF-8") }
            return StalkerLink(id, module, q["cmd"] ?: return null, q["series"]?.toIntOrNull())
        }
    }
}

fun isStalkerLink(s: String) = s.startsWith("stalker://")

private val STREAM_URL = Regex("""(?:^|\s)((?:https?|rtmp|rtsp|udp)://\S+)""")
/** `ffmpeg http://…` / `auto http://…` → the URL; null if there is none. */
internal fun cmdUrl(cmd: String?): String? = cmd?.let { STREAM_URL.find(it.trim())?.groupValues?.get(1) }

private const val MAG_AGENT = "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3"

/** One portal session: finds the endpoint, handshakes, and handshakes again once when the token stops working. */
class StalkerClient(
    portalUrl: String,
    private val login: StalkerLogin,
    private val http: OkHttpClient,
    userAgent: String?,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {
    val base = portalBase(portalUrl)
    val agent = userAgent ?: MAG_AGENT
    private val mutex = Mutex()
    private var endpoint: String? = null
    private var token: String? = null

    private class Expired : Exception()

    /** The `js` part of the answer to [params] (type, action, …). */
    suspend fun call(vararg params: Pair<String, String>): JsonElement {
        val (ep, tok) = session(null)
        return try { request(ep, tok, params.toList()) } catch (e: Expired) {
            val (ep2, tok2) = session(tok) // the token ran out or the portal restarted: one fresh login, then give up
            try { request(ep2, tok2, params.toList()) } catch (e: Expired) { throw StalkerException("The portal stopped accepting this box. Check the MAC address.") }
        }
    }

    /** The current session, or a new one if there is none or [stale] is the token that just failed. */
    private suspend fun session(stale: String?): Pair<String, String> = mutex.withLock {
        val ep = endpoint; val tok = token
        if (ep != null && tok != null && tok != stale) return@withLock ep to tok
        handshake().also { (e, t) -> endpoint = e; token = t }
    }

    private suspend fun handshake(): Pair<String, String> {
        if (base.toHttpUrlOrNull() == null) throw StalkerException("Enter the portal address, e.g. http://portal.example.com/c/")
        var last: Exception? = null
        for (path in listOf("/portal.php", "/server/load.php", "/stalker_portal/server/load.php")) {
            val ep = base + path
            val js = try { request(ep, null, listOf("type" to "stb", "action" to "handshake", "token" to "", "prehash" to "0")) }
                catch (e: Expired) { last = e; continue } catch (e: IOException) { last = e; continue }
            val tok = (js as? JsonObject)?.str("token") ?: continue
            profile(ep, tok)
            return ep to tok
        }
        throw StalkerException(if (last is IOException && last.message?.startsWith("HTTP") != true) "Couldn't reach the portal: ${last.message}"
            else "No Stalker portal found at $base")
    }

    private suspend fun profile(ep: String, tok: String) {
        val id = login.deviceId.orEmpty()
        val js = try {
            request(ep, tok, listOf("type" to "stb", "action" to "get_profile", "hd" to "1", "ver" to "ImageDescription: 0.2.18-r23-250; ImageDate: Wed Aug 29 10:49:53 EEST 2018; PORTAL version: 5.6.2; API Version: JS API version: 343; STB API version: 146; Player Engine version: 0x58c",
                "num_banks" to "2", "sn" to login.serial.orEmpty(), "stb_type" to "MAG250", "image_version" to "218", "video_out" to "hdmi",
                "device_id" to id, "device_id2" to id, "signature" to "", "auth_second_step" to "1", "hw_version" to "1.7-BD-00", "not_valid_token" to "0"))
        } catch (e: Expired) { throw StalkerException("The portal didn't accept this MAC address") }
        val o = js as? JsonObject ?: throw StalkerException("The portal didn't accept this MAC address")
        (o.str("block_msg") ?: o.str("msg").takeIf { o.str("id") == null })?.let { throw StalkerException("Portal: $it") }
    }

    private suspend fun request(ep: String, tok: String?, params: List<Pair<String, String>>): JsonElement = withContext(Dispatchers.IO) {
        val url = ep.toHttpUrl().newBuilder().apply {
            params.forEach { (k, v) -> addQueryParameter(k, v) }
            addQueryParameter("JsHttpRequest", "1-xml")
        }.build()
        val req = Request.Builder().url(url)
            .header("User-Agent", agent)
            .header("X-User-Agent", "Model: MAG250; Link: WiFi")
            .header("Referer", "$base/c/")
            .header("Cookie", "mac=${URLEncoder.encode(login.mac, "UTF-8")}; stb_lang=en; timezone=${URLEncoder.encode(java.util.TimeZone.getDefault().id, "UTF-8")}")
            .apply { tok?.let { header("Authorization", "Bearer $it") } }
            .build()
        http.newCall(req).execute().use { resp ->
            if (resp.code == 401 || resp.code == 403) throw Expired()
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val text = resp.body!!.string()
            if ("Authorization failed" in text) throw Expired()
            val root = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: throw Expired() // HTML/text: not (or no longer) logged in
            root["js"] ?: JsonNull
        }
    }

    /** Every row of a paged `get_ordered_list`, four pages at a time. */
    suspend fun paged(vararg params: Pair<String, String>): List<JsonObject> {
        suspend fun page(p: Int) = call(*params, "action" to "get_ordered_list", "p" to "$p") as? JsonObject
        val first = page(1) ?: return emptyList()
        val rows = first.rows().toMutableList()
        val perPage = first.str("max_page_items")?.toIntOrNull()?.takeIf { it > 0 } ?: rows.size.takeIf { it > 0 } ?: return rows
        val total = first.str("total_items")?.toIntOrNull() ?: return rows
        val pages = (total + perPage - 1) / perPage
        // shortcut: the whole VOD list is imported (portals give 10–20 rows a page); load per category on demand if that is too slow.
        (2..pages).chunked(4).forEach { chunk ->
            val got = coroutineScope { chunk.map { async { page(it)?.rows().orEmpty() } }.awaitAll() }
            got.forEach { rows += it }
            if (got.any { it.isEmpty() }) return rows // the portal ran out early
        }
        return rows
    }

    /** create_link: the playable URL for a saved [link]. */
    suspend fun createLink(link: StalkerLink): String {
        val js = try {
            call("type" to link.module, "action" to "create_link", "cmd" to link.cmd, "series" to (link.series?.toString() ?: ""),
                "forced_storage" to "undefined", "disable_ad" to "0", "download" to "0")
        } catch (e: StalkerException) { throw e } catch (e: IOException) { null }
        val fallback = cmdUrl(link.cmd)?.takeIf { "localhost" !in it } // some portals' cmd is already the stream
        return cmdUrl((js as? JsonObject)?.str("cmd")) ?: fallback ?: throw StalkerException("The portal didn't give a stream link for this item")
    }
}

private fun String.toHttpUrlOrNull() = runCatching { toHttpUrl() }.getOrNull()

internal fun JsonObject.str(name: String): String? = (this[name] as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
private fun JsonObject.rows(): List<JsonObject> = (this["data"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
private fun JsonElement.list(): List<JsonObject> = when (this) {
    is JsonArray -> mapNotNull { it as? JsonObject }
    is JsonObject -> rows() // get_all_channels: {"data":[…]}
    else -> emptyList()
}

/** A Stalker/Ministra portal as a playlist (spec 2026-11-15-stalker-portal-design). One instance per sync. */
class StalkerSource(
    private val providerId: Long,
    private val client: StalkerClient,
) : PlaylistSource {

    private fun logo(s: String?): String? = when {
        s == null -> null
        s.startsWith("http") -> s
        s.startsWith("/") -> client.base.toHttpUrl().resolve(s)?.toString()
        else -> null
    }

    private fun categories(js: JsonElement, kind: Kind, prefix: String = "") = js.list().mapNotNull { c ->
        val id = c.str("id")?.takeIf { it != "*" } ?: return@mapNotNull null
        Category(prefix + id, c.str("title") ?: id, kind)
    }

    override suspend fun liveCategories() = categories(client.call("type" to "itv", "action" to "get_genres"), Kind.LIVE)

    override suspend fun liveChannels(): List<Channel> {
        val all = runCatching { client.call("type" to "itv", "action" to "get_all_channels").list() }
            .getOrElse { if (it is StalkerException) throw it else emptyList() }
        val rows = all.ifEmpty { liveCategories().flatMap { g -> client.paged("type" to "itv", "genre" to g.remoteId) } }
        return rows.mapNotNull { r ->
            val id = r.str("id") ?: return@mapNotNull null
            val cmd = r.str("cmd") ?: return@mapNotNull null
            Channel(id, r.str("tv_genre_id"), r.str("name") ?: id, logo(r.str("logo")), r.str("number")?.toIntOrNull(),
                r.str("xmltv_id"), StalkerLink(providerId, "itv", cmd).toString())
        }
    }

    private class Vod(val categories: List<Category>, val movies: List<Movie>, val series: List<Series>, val seriesCategories: List<Category>)
    private val vodMutex = Mutex()
    private var vod: Vod? = null

    /** VOD lists once per instance: movies and the old-style series (is_series=1) come from the same pages. */
    private suspend fun vod(): Vod = vodMutex.withLock {
        vod ?: run {
            val cats = categories(client.call("type" to "vod", "action" to "get_categories"), Kind.MOVIE)
            val movies = ArrayList<Movie>(); val series = ArrayList<Series>(); val seriesCats = LinkedHashSet<Category>()
            cats.forEach { c ->
                client.paged("type" to "vod", "category" to c.remoteId, "sortby" to "added").forEach { r ->
                    val id = r.str("id") ?: return@forEach
                    val name = r.str("name") ?: r.str("o_name") ?: id
                    if (r.str("is_series") == "1") {
                        series += Series("vod:$id", "vod:${c.remoteId}", name, logo(r.str("screenshot_uri")), r.str("year")?.let(::yearOf), r.str("rating_imdb")?.toDoubleOrNull())
                        seriesCats += Category("vod:${c.remoteId}", c.name, Kind.SERIES)
                    } else {
                        val cmd = r.str("cmd") ?: return@forEach
                        movies += Movie(id, c.remoteId, name, logo(r.str("screenshot_uri")), r.str("year")?.let(::yearOf),
                            r.str("rating_imdb")?.toDoubleOrNull(), StalkerLink(providerId, "vod", cmd).toString())
                    }
                }
            }
            Vod(cats, movies, series, seriesCats.toList()).also { vod = it }
        }
    }

    override suspend fun vodCategories() = vod().categories
    override suspend fun movies() = vod().movies
    override suspend fun movieDetail(id: String): MovieDetail =
        throw UnsupportedOperationException("Stalker movies have no detail call") // VodViewModel doesn't ask (spec)

    /** Older portals have no series module: then only the VOD ones. */
    private suspend fun <T> optional(block: suspend () -> List<T>): List<T> =
        try { block() } catch (e: StalkerException) { throw e } catch (e: Exception) { emptyList() }

    override suspend fun seriesCategories() =
        optional { categories(client.call("type" to "series", "action" to "get_categories"), Kind.SERIES) } + vod().seriesCategories

    override suspend fun series(): List<Series> {
        val own = optional {
            categories(client.call("type" to "series", "action" to "get_categories"), Kind.SERIES).flatMap { c ->
                client.paged("type" to "series", "category" to c.remoteId).mapNotNull { r ->
                    val id = r.str("id") ?: return@mapNotNull null
                    Series(id, c.remoteId, r.str("name") ?: id, logo(r.str("screenshot_uri")), r.str("year")?.let(::yearOf), r.str("rating_imdb")?.toDoubleOrNull())
                }
            }
        }
        return own + vod().series
    }

    override suspend fun seriesDetail(id: String): SeriesDetail {
        val module = if (id.startsWith("vod:")) "vod" else "series"
        val movieId = id.removePrefix("vod:")
        val seasons = client.paged("type" to module, "movie_id" to movieId, "season_id" to "0", "episode_id" to "0")
        val episodes = seasons.flatMapIndexed { i, s ->
            val seasonNo = s.str("season_number")?.toIntOrNull() ?: s.str("name")?.let { Regex("""\d+""").find(it)?.value?.toIntOrNull() } ?: (i + 1)
            val numbers = (s["series"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content?.toIntOrNull() }.orEmpty()
            val seasonCmd = s.str("cmd")
            if (numbers.isNotEmpty() && seasonCmd != null) numbers.map { n ->
                // Old style: the season's cmd plus series=<episode>.
                Episode("${s.str("id")}:$n", seasonNo, n, "Episode $n", null, null, null, StalkerLink(providerId, "vod", seasonCmd, n).toString())
            } else client.paged("type" to module, "movie_id" to movieId, "season_id" to (s.str("id") ?: return@flatMapIndexed emptyList()), "episode_id" to "0")
                .mapIndexedNotNull { j, e ->
                    val cmd = e.str("cmd") ?: return@mapIndexedNotNull null
                    val n = e.str("series_number")?.toIntOrNull() ?: (j + 1)
                    Episode(e.str("id") ?: "${s.str("id")}:$n", seasonNo, n, e.str("name") ?: "Episode $n", e.str("description"),
                        logo(e.str("screenshot_uri")), null, StalkerLink(providerId, "vod", cmd).toString())
                }
        }.distinctBy { it.remoteId }.sortedWith(compareBy({ it.season }, { it.number }))
        val first = seasons.firstOrNull()
        return SeriesDetail(Series(id, null, first?.str("name") ?: id, null, null, null), null, first?.str("description"), first?.str("genres_str"), episodes)
    }

    override fun epgUrls() = emptyList<String>() // Stalker has no XMLTV link; users add one in TV guide settings
}

/** Resolves saved `stalker://` links at play or record time, keeping one portal session per playlist. */
class StalkerLinks(
    private val http: OkHttpClient,
    private val provider: suspend (Long) -> org.imdtv.iptvtv.data.db.ProviderEntity?,
) {
    private val clients = java.util.concurrent.ConcurrentHashMap<Long, Pair<String, StalkerClient>>()

    fun client(p: org.imdtv.iptvtv.data.db.ProviderEntity): StalkerClient {
        val key = listOf(p.url, p.username, p.password, p.userAgent).joinToString("\u0000") // an edited playlist gets a new session
        clients[p.id]?.takeIf { it.first == key }?.let { return it.second }
        return StalkerClient(p.url, StalkerLogin.from(p.username, p.password), http, p.userAgent).also { clients[p.id] = key to it }
    }

    /** The real stream URL for [link], with the portal's user agent as a link header; other links come back unchanged. */
    suspend fun resolve(link: String): String {
        val l = StalkerLink.parse(link) ?: return link
        val p = provider(l.providerId) ?: throw StalkerException("This playlist was removed")
        val c = client(p)
        return withStreamHeaders(c.createLink(l), mapOf("User-Agent" to c.agent))
    }
}
