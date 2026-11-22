package org.imdtv.iptvtv.data.source

import org.imdtv.iptvtv.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import okio.BufferedSource
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.imdtv.iptvtv.data.security.PASS_TOKEN
import org.imdtv.iptvtv.data.security.USER_TOKEN
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
import org.imdtv.iptvtv.data.model.StreamFormat
import java.io.IOException

class XtreamAuthException(message: String, @androidx.annotation.StringRes override val resId: Int? = null, override val args: List<Any> = emptyList()) : Exception(message), UiMessage

/** Xtream Codes `player_api.php` source (spec §4.1). Credentials are checked once, on first use. */
class XtreamSource(
    baseUrl: String,
    private val username: String,
    private val password: String,
    private val streamFormat: StreamFormat,
    private val http: OkHttpClient,
    private val json: Json,
    private val userAgent: String?,
) : PlaylistSource {

    private val base = baseUrl.trim().trimEnd('/')
    private val baseHttpUrl by lazy { base.toHttpUrl() }

    /** `$base/<kind>/<user>/<pass>/<file>` with every segment percent-encoded. */
    private fun streamUrl(kind: String, file: String): String =
        // Saved without the login (placeholders); the player fills it in, so the database never holds it.
        baseHttpUrl.newBuilder().addPathSegment(kind).addPathSegment(USER_TOKEN).addPathSegment(PASS_TOKEN)
            .addPathSegment(file).build().toString()
    private val authMutex = Mutex()
    private var authenticated = false

    private suspend fun get(action: String?, vararg params: Pair<String, String>): String =
        withBody(action, *params) { it.readUtf8() }

    /** Runs [read] on the response body stream, so large lists are decoded without a full String copy. */
    private suspend fun <T> withBody(action: String?, vararg params: Pair<String, String>, read: (BufferedSource) -> T): T =
        withContext(Dispatchers.IO) {
            val url = "$base/player_api.php".toHttpUrl().newBuilder()
                .addQueryParameter("username", username)
                .addQueryParameter("password", password)
                .apply {
                    action?.let { addQueryParameter("action", it) }
                    params.forEach { (k, v) -> addQueryParameter(k, v) }
                }.build()
            val req = Request.Builder().url(url).apply { userAgent?.let { header("User-Agent", it) } }.build()
            http.newCall(req).execute().use { resp ->
                if (resp.code == 401 || resp.code == 403) {
                    throw XtreamAuthException("Login rejected by server (HTTP ${resp.code})", R.string.set_b_xt_rejected, listOf(resp.code))
                }
                if (!resp.isSuccessful) { logRefusal(url, req, resp); throw UiIOException("Xtream request failed: HTTP ${resp.code} ${resp.message}", R.string.set_b_xt_request_failed, listOf(resp.code, resp.message)) }
                read(resp.body!!.source())
            }
        }

    /**
     * For Send logs: what the panel answered and how it could be reached. A playlist refused on one network but not another
     * (issue: HTTP 512 on a 5G router) points at IPv6 or the user agent. No login: the URL isn't logged.
     */
    private fun logRefusal(url: okhttp3.HttpUrl, req: Request, resp: okhttp3.Response) {
        val body = runCatching { resp.peekBody(300).string() }.getOrDefault("").replace(Regex("\\s+"), " ")
            .let { if (username.isNotEmpty()) it.replace(username, "<user>") else it }.let { if (password.isNotEmpty()) it.replace(password, "<pass>") else it }
        val server = runCatching { java.net.InetAddress.getAllByName(url.host).joinToString { it.hostAddress.orEmpty() } }.getOrDefault("?")
        val boxV6 = runCatching { java.net.NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
            .any { it is java.net.Inet6Address && !it.isLinkLocalAddress && !it.isLoopbackAddress } }.getOrDefault(false)
        android.util.Log.w("Xtream", "HTTP ${resp.code} '${resp.message}' from ${url.host}:${url.port} [$server], box IPv6 ${if (boxV6) "yes" else "no"}, " +
            "${resp.protocol}, server '${resp.header("Server")}', agent '${req.header("User-Agent") ?: "default"}', body: $body")
    }

    private suspend fun ensureAuth() = authMutex.withLock {
        if (authenticated) return@withLock
        val info = json.decodeFromString(XAuthResponse.serializer(), get(null)).userInfo
        if (info?.auth != 1) {
            throw info?.message?.let { XtreamAuthException(it) }
                ?: info?.status?.let { XtreamAuthException("Account $it", R.string.set_b_xt_account, listOf(it)) }
                ?: XtreamAuthException("Invalid username or password", R.string.set_b_xt_invalid)
        }
        authenticated = true
    }

    /** The login's stream limit (user_info.max_connections); null when unknown, 0 or the call fails. */
    suspend fun maxConnections(): Int? = try {
        json.decodeFromString(XAuthResponse.serializer(), get(null)).userInfo?.maxConnections?.takeIf { it > 0 }
    } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { null }

    /** The server's time zone (server_info.timezone), which catch-up start times are in; null when unknown or the call fails. */
    suspend fun serverTimeZone(): java.util.TimeZone? = try {
        json.decodeFromString(XAuthResponse.serializer(), get(null)).serverInfo?.timezone
            ?.takeIf { it in java.util.TimeZone.getAvailableIDs() }?.let(java.util.TimeZone::getTimeZone)
    } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { null }

    private suspend fun <T> call(action: String, strategy: DeserializationStrategy<T>, vararg params: Pair<String, String>): T {
        ensureAuth()
        return json.decodeFromString(strategy, get(action, *params))
    }

    /** List endpoints sometimes return `{}` or `null` instead of `[]`. */
    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun <T> list(action: String, item: kotlinx.serialization.KSerializer<T>): List<T> {
        ensureAuth()
        return withBody(action) { body ->
            if (firstNonBlankByte(body) == '['.code.toByte()) {
                json.decodeFromStream(ListSerializer(item), body.inputStream())
            } else {
                emptyList()
            }
        }
    }

    /** Peeks past leading whitespace without consuming the body. */
    private fun firstNonBlankByte(body: BufferedSource): Byte? {
        val peek = body.peek()
        while (!peek.exhausted()) {
            val b = peek.readByte()
            if (!b.toInt().toChar().isWhitespace()) return b
        }
        return null
    }

    private suspend fun categories(action: String, kind: Kind) =
        list(action, XCategory.serializer()).mapNotNull { c ->
            c.categoryId?.let { Category(it, c.categoryName ?: it, kind) }
        }

    override suspend fun liveCategories() = categories("get_live_categories", Kind.LIVE)
    override suspend fun vodCategories() = categories("get_vod_categories", Kind.MOVIE)
    override suspend fun seriesCategories() = categories("get_series_categories", Kind.SERIES)

    private val liveExt get() = if (streamFormat == StreamFormat.HLS) "m3u8" else "ts"

    override suspend fun liveChannels() =
        list("get_live_streams", XLiveStream.serializer()).mapNotNull { s ->
            val id = s.streamId ?: return@mapNotNull null
            Channel(
                remoteId = id,
                categoryRemoteId = s.categoryId,
                name = s.name ?: id,
                logo = s.streamIcon,
                number = s.num,
                tvgId = s.epgChannelId,
                streamUrl = streamUrl("live", "$id.$liveExt"),
                archiveDays = s.tvArchiveDuration?.takeIf { s.tvArchive == 1 && it > 0 },
            )
        }

    /** Xtream dates are unix seconds as text; 0 or junk = none. */
    private fun unixMs(s: String?): Long? = s?.trim()?.toLongOrNull()?.takeIf { it > 0 }?.times(1000)

    private fun XVodStream.toMovie(): Movie? {
        val id = streamId ?: return null
        return Movie(
            remoteId = id,
            categoryRemoteId = categoryId,
            name = name ?: id,
            poster = streamIcon,
            year = year,
            rating = rating,
            streamUrl = streamUrl("movie", "$id.${containerExtension ?: "mp4"}"),
            added = unixMs(added),
        )
    }

    override suspend fun movies() = list("get_vod_streams", XVodStream.serializer()).mapNotNull { it.toMovie() }

    override suspend fun movieDetail(id: String): MovieDetail {
        val r = call("get_vod_info", XVodInfo.serializer(), "vod_id" to id)
        val info = r.info.asObjectOrNull()?.let { json.decodeFromJsonElement(XVodInfoInfo.serializer(), it) }
        val movie = (r.movieData ?: XVodStream(streamId = id)).let { it.copy(streamId = it.streamId ?: id) }.toMovie()!!
            .let { m ->
                m.copy(
                    name = if (m.name == id) info?.name ?: m.name else m.name,
                    poster = m.poster ?: info?.movieImage,
                    year = m.year ?: yearOf(info?.releasedate),
                    rating = m.rating ?: info?.rating,
                )
            }
        return MovieDetail(
            movie = movie,
            backdrop = firstUrl(info?.backdropPath),
            plot = info?.plot,
            genre = info?.genre,
            director = info?.director,
            cast = info?.cast ?: info?.actors,
            durationSecs = info?.durationSecs,
            height = videoHeight(info?.video),
        )
    }

    override suspend fun series() =
        list("get_series", XSeries.serializer()).mapNotNull { s ->
            val id = s.seriesId ?: return@mapNotNull null
            Series(id, s.categoryId, s.name ?: id, s.cover, yearOf(s.releaseDate), s.rating, unixMs(s.lastModified))
        }

    override suspend fun seriesDetail(id: String): SeriesDetail {
        val r = call("get_series_info", XSeriesInfo.serializer(), "series_id" to id)
        val info = r.info.asObjectOrNull()?.let { json.decodeFromJsonElement(XSeriesInfoInfo.serializer(), it) }
        // `episodes` is normally {"1":[…],"2":[…]} but may be a flat array or [] when empty.
        val groups: List<Pair<Int?, JsonElement>> = when (val e = r.episodes) {
            is JsonObject -> e.entries.map { (season, eps) -> season.toIntOrNull() to eps }
            is JsonArray -> listOf(null to e)
            else -> emptyList()
        }
        val episodes = groups.flatMap { (seasonKey, eps) ->
            (eps as? JsonArray).orEmpty().mapNotNull { el ->
                val ep = (el as? JsonObject)?.let { json.decodeFromJsonElement(XEpisode.serializer(), it) } ?: return@mapNotNull null
                val epId = ep.id ?: return@mapNotNull null
                val epInfo = ep.info.asObjectOrNull()?.let { json.decodeFromJsonElement(XEpisodeInfo.serializer(), it) }
                Episode(
                    remoteId = epId,
                    season = ep.season ?: seasonKey ?: 1,
                    number = ep.episodeNum ?: 0,
                    title = ep.title ?: "Episode ${ep.episodeNum ?: ""}".trim(),
                    plot = epInfo?.plot,
                    thumb = epInfo?.movieImage,
                    durationSecs = epInfo?.durationSecs,
                    streamUrl = streamUrl("series", "$epId.${ep.containerExtension ?: "mp4"}"),
                )
            }
        }.sortedWith(compareBy({ it.season }, { it.number }))
        val series = Series(
            remoteId = id,
            categoryRemoteId = info?.categoryId,
            name = info?.name ?: id,
            poster = info?.cover,
            year = yearOf(info?.releaseDate),
            rating = info?.rating,
        )
        // Providers sometimes repeat an episode id; duplicate list keys crash the detail screen.
        return SeriesDetail(series, firstUrl(info?.backdropPath), info?.plot, info?.genre, episodes.distinctBy { it.remoteId })
    }

    override fun epgUrls() = listOf(
        baseHttpUrl.newBuilder().addPathSegment("xmltv.php")
            .addQueryParameter("username", USER_TOKEN).addQueryParameter("password", PASS_TOKEN).build().toString()
    )
}
