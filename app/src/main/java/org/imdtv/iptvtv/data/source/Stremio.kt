package org.imdtv.iptvtv.data.source

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

/** [code] is the API's error code (3 = wrong password); [text] its message. */
class StremioException(val code: Int, val text: String) : java.io.IOException("Stremio: $code $text"), UiMessage {
    override val resId: Int? get() = if (code == 3) R.string.set_b_stremio_wrong_login else R.string.set_b_stremio_failed
    override val args: List<Any> get() = if (code == 3) emptyList() else listOf(text)
}

/** Saved instead of a stream link: the stream is looked up when it plays (add-ons answer per title). "stremio:movie/tt123". */
const val STREMIO_PLAY = "stremio:"

private fun JsonElement?.str(key: String) = (this as? JsonObject)?.get(key)?.let { it as? JsonPrimitive }?.contentOrNull
private fun JsonElement?.arr(key: String) = ((this as? JsonObject)?.get(key) as? JsonArray).orEmpty()

private suspend fun OkHttpClient.json(json: Json, r: Request): JsonElement = withContext(Dispatchers.IO) {
    newCall(r).execute().use { resp ->
        if (!resp.isSuccessful) throw StremioException(resp.code, "HTTP ${resp.code} ${r.url.encodedPath}")
        json.parseToJsonElement(resp.body!!.string())
    }
}

/** Sign-in: a link code (link.stremio.com, approved where the user is signed in) or email + password. */
class StremioAuth(private val http: OkHttpClient, private val json: Json, private val api: String = "https://api.strem.io", private val link: String = "https://link.stremio.com") {
    /** The result object, or throws the API's error. */
    private suspend fun call(r: Request): JsonElement {
        val e = http.json(json, r)
        (e as? JsonObject)?.get("error")?.takeIf { it is JsonObject }?.let { throw StremioException(it.jsonObject["code"]?.jsonPrimitive?.intOrNull ?: 0, it.str("message").orEmpty()) }
        return (e as? JsonObject)?.get("result") ?: e
    }
    private fun post(path: String, vararg kv: Pair<String, Any>) = Request.Builder().url("$api/api/$path")
        .post(JsonObject(kv.associate { (k, v) -> k to when (v) { is JsonElement -> v; is Boolean -> JsonPrimitive(v); else -> JsonPrimitive(v.toString()) } }).toString().toRequestBody("application/json".toMediaType())).build()

    /** A code to enter (or a QR to scan) at [StremioLink.url]. */
    suspend fun createLink(): StremioLink = call(Request.Builder().url("$link/api/v2/create?type=Create").build()).let { StremioLink(it.str("code").orEmpty(), it.str("link").orEmpty()) }

    // shortcut: the read reply before approval wasn't seen (it errors "Invalid or expired token"), so any error means "not yet"; the caller makes a new code after a while.
    /** The account's key once [code] was approved; null while waiting. */
    suspend fun readLink(code: String): String? =
        runCatching { call(Request.Builder().url("$link/api/v2/read?type=Read&code=$code").build()).str("authKey") }.getOrNull()

    suspend fun withPassword(email: String, password: String): String =
        call(post("login", "type" to "Login", "email" to email, "password" to password, "facebook" to false)).str("authKey")
            ?: throw StremioException(0, "no authKey")

    /** The add-ons installed on the account, in its order (each one's address is all it needs, settings included). */
    suspend fun addons(authKey: String): List<StremioAddon> =
        call(post("addonCollectionGet", "type" to "AddonCollectionGet", "authKey" to authKey, "update" to true)).arr("addons").mapNotNull { a ->
            val url = a.str("transportUrl") ?: return@mapNotNull null
            StremioAddon.of(url, (a as JsonObject)["manifest"] ?: return@mapNotNull null)
        }

    /**
     * The account's Continue watching, from its library: as Stremio's own rule, an item with a position that's still in the
     * library or only watched (temp). A series' position is on its last video ("tt…:season:episode").
     */
    suspend fun continueWatching(authKey: String): List<ResumePoint> =
        call(post("datastoreGet", "type" to "DatastoreGet", "authKey" to authKey, "collection" to "libraryItem", "ids" to JsonArray(emptyList()), "all" to true))
            .let { it as? JsonArray ?: JsonArray(emptyList()) }.mapNotNull { i ->
                val o = i as? JsonObject ?: return@mapNotNull null
                val st = o["state"] as? JsonObject ?: return@mapNotNull null
                val off = st["timeOffset"]?.jsonPrimitive?.longOrNull ?: 0
                val removed = o["removed"]?.jsonPrimitive?.booleanOrNull == true
                if (off <= 0 || (removed && o["temp"]?.jsonPrimitive?.booleanOrNull != true)) return@mapNotNull null
                val id = o.str("_id") ?: return@mapNotNull null
                val at = st.str("lastWatched")?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0
                val dur = st["duration"]?.jsonPrimitive?.longOrNull ?: 0
                when (o.str("type")) {
                    "movie" -> ResumePoint(Kind.MOVIE, id, "", off, dur, at)
                    "series" -> st.str("video_id")?.let { ResumePoint(Kind.SERIES, id, it, off, dur, at) }
                    else -> null
                }
            }

    /**
     * Saves where [p] was left in the account's library, as Stremio's apps do: the existing item with its state updated, or
     * a new temp item (watched, not added to the library) with every field Stremio's apps expect.
     */
    suspend fun saveProgress(authKey: String, p: ProgressReport, now: Long = System.currentTimeMillis()) {
        val old = call(post("datastoreGet", "type" to "DatastoreGet", "authKey" to authKey, "collection" to "libraryItem",
            "ids" to JsonArray(listOf(JsonPrimitive(p.remoteId))), "all" to false)).let { (it as? JsonArray)?.firstOrNull() as? JsonObject }
        val iso = java.time.Instant.ofEpochMilli(now).toString()
        val was = old?.get("state") as? JsonObject
        fun n(k: String) = was?.get(k)?.jsonPrimitive?.longOrNull ?: 0
        val state = JsonObject((was ?: JsonObject(mapOf("timeWatched" to JsonPrimitive(0), "overallTimeWatched" to JsonPrimitive(0),
            "watched" to kotlinx.serialization.json.JsonNull, "noNotif" to JsonPrimitive(false)))) + mapOf(
            "lastWatched" to JsonPrimitive(iso),
            "timeOffset" to JsonPrimitive(if (p.watched) 0 else p.positionMs),
            "duration" to JsonPrimitive(p.durationMs.takeIf { it > 0 } ?: n("duration")),
            "video_id" to JsonPrimitive(p.episode.ifEmpty { p.remoteId }),
            "timesWatched" to JsonPrimitive(n("timesWatched") + if (p.watched) 1 else 0),
            "flaggedWatched" to JsonPrimitive(if (p.watched) 1 else if (p.positionMs == 0L) 0 else n("flaggedWatched")),
        ))
        val item = JsonObject((old ?: JsonObject(mapOf(
            "_id" to JsonPrimitive(p.remoteId), "name" to JsonPrimitive(p.title), "type" to JsonPrimitive(if (p.kind == Kind.MOVIE) "movie" else "series"),
            "poster" to (p.poster?.let(::JsonPrimitive) ?: kotlinx.serialization.json.JsonNull), "posterShape" to JsonPrimitive("poster"),
            "removed" to JsonPrimitive(true), "temp" to JsonPrimitive(true), "_ctime" to JsonPrimitive(iso),
            "behaviorHints" to JsonObject(mapOf("defaultVideoId" to kotlinx.serialization.json.JsonNull, "featuredVideoId" to kotlinx.serialization.json.JsonNull,
                "hasScheduledVideos" to JsonPrimitive(false))),
        ))) + mapOf("_mtime" to JsonPrimitive(iso), "state" to state))
        call(post("datastorePut", "type" to "DatastorePut", "authKey" to authKey, "collection" to "libraryItem", "changes" to JsonArray(listOf(item))))
    }

    /** Movie and series catalogs as (key, "Catalog · Add-on", kind): one needing a search or genre to answer is left out. */
    fun catalogs(addons: List<StremioAddon>): List<Triple<String, String, Kind>> = addons.flatMap { a -> a.catalogs.map { Triple(it.key, "${it.name} · ${a.name}", it.kind) } }
}

data class StremioLink(val code: String, val url: String)

data class StremioCatalog(val key: String, val type: String, val id: String, val name: String, val kind: Kind)

/** One add-on: where it lives, what it lists and whether it answers streams / details for a type and id. */
data class StremioAddon(val base: String, val id: String, val name: String, val catalogs: List<StremioCatalog>,
                        private val resources: List<Triple<String, Set<String>?, List<String>?>>) {
    /** Answers [resource] ("stream", "meta") for [type] and [id] (its idPrefixes, when it lists any). */
    fun serves(resource: String, type: String, id: String) = resources.any { (name, types, prefixes) ->
        name == resource && (types == null || type in types) && (prefixes == null || prefixes.any { id.startsWith(it) })
    }

    companion object {
        fun of(transportUrl: String, m: JsonElement): StremioAddon {
            val types = m.arr("types").mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.toSet()
            val prefixes = m.arr("idPrefixes").mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.ifEmpty { null }
            // A resource is "stream", or {name, types, idPrefixes} narrowing the manifest's own.
            val res = m.arr("resources").mapNotNull { r ->
                if (r is JsonPrimitive) Triple(r.content, types, prefixes)
                else r.str("name")?.let { n -> Triple(n, r.arr("types").mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.toSet().ifEmpty { types }, r.arr("idPrefixes").mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.ifEmpty { prefixes }) }
            }
            val id = m.str("id").orEmpty()
            val cats = m.arr("catalogs").mapNotNull { c ->
                val kind = when (c.str("type")) { "movie" -> Kind.MOVIE; "series" -> Kind.SERIES; else -> return@mapNotNull null }
                val required = c.arr("extra").any { e -> (e as? JsonObject)?.get("isRequired")?.jsonPrimitive?.contentOrNull == "true" } || c.arr("extraRequired").isNotEmpty()
                if (required) return@mapNotNull null
                val cid = c.str("id") ?: return@mapNotNull null
                StremioCatalog("$id/${c.str("type")}/$cid", c.str("type")!!, cid, c.str("name") ?: cid, kind)
            }
            return StremioAddon(transportUrl.removeSuffix("/manifest.json").trimEnd('/'), id, m.str("name") ?: id, cats, res)
        }
    }
}

/**
 * A Stremio account's add-ons as a playlist's VOD (no live): their movie and series catalogs (capped, they are open-ended)
 * as categories. Links are [STREMIO_PLAY] ids; [stream] asks the stream add-ons at play time and takes the first direct
 * link (torrent-only answers can't play: no torrent engine, a known limitation).
 */
class StremioSource(private val authKey: String, private val skip: Set<String>, private val auth: StremioAuth,
                    private val http: OkHttpClient, private val json: Json) : PlaylistSource {
    private var addonCache: List<StremioAddon>? = null
    private suspend fun addons() = addonCache ?: auth.addons(authKey).also { addonCache = it }
    private suspend fun catalogs(kind: Kind) = addons().flatMap { a -> a.catalogs.filter { it.kind == kind && it.key !in skip }.map { a to it } }

    private suspend fun get(url: String) = http.json(json, Request.Builder().url(url).header("Accept", "application/json").build())

    /** Every page of a catalog, up to [MAX] titles (Cinemeta's Popular never ends). */
    private suspend fun items(a: StremioAddon, c: StremioCatalog, fetched: (Int, Int?) -> Unit = { _, _ -> }): List<JsonElement> {
        val out = ArrayList<JsonElement>()
        while (out.size < MAX) {
            val page = get("${a.base}/catalog/${c.type}/${c.id}" + (if (out.isEmpty()) "" else "/skip=${out.size}") + ".json").arr("metas")
            if (page.isEmpty()) break
            out += page
            fetched(minOf(out.size, MAX), null)
            if (page.size < 10) break // a short page is the last
        }
        return out.take(MAX)
    }

    private fun year(m: JsonElement?) = (m.str("releaseInfo") ?: m.str("year"))?.take(4)?.toIntOrNull()
    private fun rating(m: JsonElement?) = (m as? JsonObject)?.get("imdbRating")?.let { (it as? JsonPrimitive)?.doubleOrNull ?: it.jsonPrimitive.contentOrNull?.toDoubleOrNull() }
    private fun movie(m: JsonElement, cat: String?) = Movie(m.str("id")!!, cat, m.str("name").orEmpty(), m.str("poster"), year(m), rating(m), "${STREMIO_PLAY}movie/${m.str("id")}")
    private fun series(m: JsonElement, cat: String?) = Series(m.str("id")!!, cat, m.str("name").orEmpty(), m.str("poster"), year(m), rating(m))

    /** The same title in several catalogs is kept once, in the first (a title has one category). */
    private suspend fun <T> list(kind: Kind, make: (JsonElement, String) -> T): List<T> = coroutineScope {
        catalogs(kind).map { (a, c) -> async { c.key to runCatching { items(a, c) }.getOrElse { android.util.Log.w("Stremio", "${a.name} ${c.name} failed: $it"); emptyList() } } }.awaitAll()
            .flatMap { (key, metas) -> metas.filter { it.str("id") != null }.map { it.str("id")!! to make(it, key) } }
            .distinctBy { it.first }.map { it.second }
    }

    override suspend fun liveCategories(): List<Category> = emptyList()
    override suspend fun liveChannels(): List<Channel> = emptyList()
    override fun epgUrls(): List<String> = emptyList()
    override suspend fun vodCategories() = catalogs(Kind.MOVIE).map { (a, c) -> Category(c.key, "${c.name} · ${a.name}", Kind.MOVIE) }
    override suspend fun seriesCategories() = catalogs(Kind.SERIES).map { (a, c) -> Category(c.key, "${c.name} · ${a.name}", Kind.SERIES) }
    override suspend fun movies() = list(Kind.MOVIE) { m, cat -> movie(m, cat) }
    override suspend fun series() = list(Kind.SERIES) { m, cat -> series(m, cat) }
    // Catalogs are rankings (Popular, New…) with no "changed since": always the whole catalog (up to MAX).
    override suspend fun moviesIn(cat: String, since: Long?, fetched: (Int, Int?) -> Unit) = catalog(Kind.MOVIE, cat, fetched) { m, c -> movie(m, c) }
    override suspend fun seriesIn(cat: String, since: Long?, fetched: (Int, Int?) -> Unit) = catalog(Kind.SERIES, cat, fetched) { m, c -> series(m, c) }
    private suspend fun <T> catalog(kind: Kind, cat: String, fetched: (Int, Int?) -> Unit, make: (JsonElement, String) -> T): LibraryPage<T> {
        val (a, c) = catalogs(kind).first { it.second.key == cat }
        val metas = runCatching { items(a, c, fetched) }.getOrElse { android.util.Log.w("Stremio", "${a.name} ${c.name} failed: $it"); emptyList() }
        return metas.filter { it.str("id") != null }.map { make(it, cat) }.let { LibraryPage(it, it.size, changesOnly = false) }
    }

    private suspend fun meta(type: String, id: String): JsonElement? =
        addons().filter { it.serves("meta", type, id) }.firstNotNullOfOrNull { a -> runCatching { (get("${a.base}/meta/$type/$id.json") as? JsonObject)?.get("meta") }.getOrNull() }

    private fun List<JsonElement>.names() = mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.joinToString().ifEmpty { null }

    override suspend fun movieDetail(id: String): MovieDetail {
        val m = meta("movie", id) ?: throw StremioException(404, "no details for $id")
        val mins = m.str("runtime")?.takeWhile { it.isDigit() }?.toIntOrNull()
        return MovieDetail(movie(m, null), m.str("background"), m.str("description"), m.arr("genres").names(), m.arr("director").names(),
            m.arr("cast").take(6).names(), mins?.times(60))
    }

    override suspend fun seriesDetail(id: String): SeriesDetail {
        val m = meta("series", id) ?: throw StremioException(404, "no details for $id")
        val eps = m.arr("videos").mapNotNull { v ->
            val vid = v.str("id") ?: return@mapNotNull null
            val season = (v as JsonObject)["season"]?.jsonPrimitive?.intOrNull ?: 0
            val number = (v["episode"] ?: v["number"])?.jsonPrimitive?.intOrNull ?: 0
            Episode(vid, season, number, v.str("name") ?: v.str("title").orEmpty(), v.str("overview") ?: v.str("description"), v.str("thumbnail"), null, "${STREMIO_PLAY}series/$vid")
        }.filter { it.season > 0 }.sortedWith(compareBy({ it.season }, { it.number })) // season 0: specials and trailers
        return SeriesDetail(series(m, null), m.str("background"), m.str("description"), m.arr("genres").names(), eps)
    }

    /** The first direct link the account's stream add-ons give for [play] (a [STREMIO_PLAY] link), in add-on order; null if none. */
    suspend fun stream(play: String): String? {
        val (type, id) = play.removePrefix(STREMIO_PLAY).split('/', limit = 2).takeIf { it.size == 2 } ?: return null
        for (a in addons().filter { it.serves("stream", type, id) }) {
            val streams = runCatching { get("${a.base}/stream/$type/$id.json").arr("streams") }
                .onFailure { android.util.Log.w("Stremio", "${a.name} streams failed: $it") }.getOrNull() ?: continue
            // A torrent (infoHash), YouTube id or outside link can't play here; a direct http(s) link can.
            streams.firstNotNullOfOrNull { s -> s.str("url")?.takeIf { it.startsWith("http") } }?.let { return it }
        }
        return null
    }

    /** #41: every direct link for [play] across the stream add-ons, as (label, url) in add-on order; the label is the add-on's own name and title lines. */
    suspend fun streams(play: String): List<Pair<String, String>> {
        val (type, id) = play.removePrefix(STREMIO_PLAY).split('/', limit = 2).takeIf { it.size == 2 } ?: return emptyList()
        return addons().filter { it.serves("stream", type, id) }.flatMap { a ->
            val streams = runCatching { get("${a.base}/stream/$type/$id.json").arr("streams") }
                .onFailure { android.util.Log.w("Stremio", "${a.name} streams failed: $it") }.getOrNull().orEmpty()
            streams.mapNotNull { s ->
                val url = s.str("url")?.takeIf { it.startsWith("http") } ?: return@mapNotNull null
                val label = listOfNotNull(s.str("name"), s.str("title") ?: s.str("description")).joinToString(" · ").replace(Regex("""\s*\n\s*"""), " ").trim()
                label.ifEmpty { a.name }.take(140) to url
            }
        }
    }

    // shortcut: only titles in the chosen catalogs can show (the rest have no row here); add them to a category if people ask.
    override suspend fun resumePoints() = auth.continueWatching(authKey)
    override suspend fun reportProgress(p: ProgressReport) = auth.saveProgress(authKey, p)

    companion object { const val MAX = 500 }
}
