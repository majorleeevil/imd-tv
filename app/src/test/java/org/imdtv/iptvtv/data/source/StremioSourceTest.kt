package org.imdtv.iptvtv.data.source

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.imdtv.iptvtv.data.model.Kind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StremioSourceTest {
    private val server = MockWebServer()
    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient()

    // Shapes as api.strem.io and Cinemeta returned them (2026-11-22), trimmed. Cinemeta pages by 50.
    private fun manifest(id: String, extra: String) = """{"id":"$id","name":"${id.uppercase()}","types":["movie","series"],"idPrefixes":["tt"],$extra}"""
    private val cinemeta = manifest("cinemeta", """"resources":["catalog","meta"],"catalogs":[
        {"type":"movie","id":"top","name":"Popular","extra":[{"name":"genre"},{"name":"skip"}]},
        {"type":"movie","id":"year","name":"New","extra":[{"name":"genre","isRequired":true}]},
        {"type":"series","id":"top","name":"Popular","extra":[]},
        {"type":"series","id":"last-videos","extraRequired":["lastVideosIds"]}]""")
    private val torrents = manifest("torrents", """"resources":[{"name":"stream","types":["movie"],"idPrefixes":["tt"]}],"catalogs":[]""")
    private val direct = manifest("direct", """"resources":["stream"],"catalogs":[]""")
    private fun addons() = """{"result":{"addons":[
        {"transportUrl":"${server.url("/cm/manifest.json")}","manifest":$cinemeta},
        {"transportUrl":"${server.url("/tor/manifest.json")}","manifest":$torrents},
        {"transportUrl":"${server.url("/dir/manifest.json")}","manifest":$direct}]}}"""
    private fun metas(from: Int, n: Int) = """{"metas":[""" + (from until from + n).joinToString(",") {
        """{"id":"tt$it","type":"movie","name":"Film $it","releaseInfo":"2024","imdbRating":"7.5","poster":"https://p/$it"}""" } + "]}"

    private val paths = mutableListOf<String>()
    private fun auth() = StremioAuth(http, json, server.url("/").toString().trimEnd('/'), server.url("/link").toString())

    private fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val p = request.path!!
                paths += p
                val body = when {
                    p == "/api/login" -> if ("\"password\":\"good\"" in request.body.readUtf8()) """{"result":{"authKey":"KEY"}}"""
                        else """{"error":{"code":3,"message":"Wrong passphrase","wrongPass":true}}"""
                    p == "/api/addonCollectionGet" -> addons()
                    p == "/cm/catalog/movie/top.json" -> metas(0, 50)
                    p == "/cm/catalog/movie/top/skip=50.json" -> metas(50, 3) // short: the last page
                    p == "/cm/catalog/series/top.json" -> """{"metas":[{"id":"tt9","type":"series","name":"Show","releaseInfo":"2011–2019"}]}"""
                    p == "/cm/meta/series/tt9.json" -> """{"meta":{"id":"tt9","name":"Show","runtime":"57 min","genres":["Drama"],"background":"https://b","videos":[
                        {"id":"tt9:1:2","name":"Two","season":1,"episode":2},{"id":"tt9:0:1","name":"Preview","season":0,"episode":1},{"id":"tt9:1:1","name":"One","season":1,"episode":1}]}}"""
                    p == "/tor/stream/movie/tt1.json" -> """{"streams":[{"name":"1080p","infoHash":"abc","fileIdx":0}]}"""
                    p == "/dir/stream/movie/tt1.json" -> """{"streams":[{"ytId":"x"},{"name":"HD","url":"https://cdn/film.mp4"}]}"""
                    p == "/dir/stream/series/tt9:1:1.json" -> """{"streams":[{"infoHash":"def"}]}"""
                    else -> return MockResponse().setResponseCode(404)
                }
                return MockResponse().setBody(body)
            }
        }
    }

    @After fun tearDown() = server.shutdown()

    @Test fun signInAndCatalogs() = runTest {
        start()
        val a = auth()
        assertEquals("KEY", a.withPassword("me@x", "good"))
        val wrong = runCatching { a.withPassword("me@x", "bad") }.exceptionOrNull() as StremioException
        assertEquals(3, wrong.code)
        // A catalog needing a genre or ids can't be listed; the others can.
        assertEquals(listOf("cinemeta/movie/top" to Kind.MOVIE, "cinemeta/series/top" to Kind.SERIES), a.catalogs(a.addons("KEY")).map { it.first to it.third })
    }

    @Test fun syncDetailAndStreams() = runTest {
        start()
        val src = StremioSource("KEY", emptySet(), auth(), http, json)
        val movies = src.movies()
        assertEquals(53, movies.size)
        assertEquals(Triple("Film 0", 2024, 7.5), movies[0].let { Triple(it.name, it.year, it.rating) })
        assertEquals("${STREMIO_PLAY}movie/tt0", movies[0].streamUrl)
        assertEquals(listOf("Show"), src.series().map { it.name })
        // Specials (season 0) left out, the rest in order.
        assertEquals(listOf("One", "Two"), src.seriesDetail("tt9").episodes.map { it.title })
        // The torrent add-on comes first but only the direct link plays; a torrent-only title has nothing.
        assertEquals("https://cdn/film.mp4", src.stream("${STREMIO_PLAY}movie/tt1"))
        assertNull(src.stream("${STREMIO_PLAY}series/tt9:1:1"))
        // Unticked catalogs aren't fetched.
        paths.clear()
        assertTrue(StremioSource("KEY", setOf("cinemeta/movie/top"), auth(), http, json).movies().isEmpty())
        assertTrue(paths.none { "catalog" in it })
    }
}
