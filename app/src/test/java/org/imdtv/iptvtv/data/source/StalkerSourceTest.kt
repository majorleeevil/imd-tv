package org.imdtv.iptvtv.data.source

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** A fake Ministra portal at /stalker_portal/server/load.php; /portal.php is missing and /server/load.php is a web page. */
class StalkerSourceTest {
    private val server = MockWebServer()
    private var tokens = 0
    private var expireOnce = false
    private var profile = """{"js":{"id":"77","name":"box","status":0}}"""
    private var allChannels = """{"js":{"data":[
        {"id":"1","name":"BBC One","number":"101","cmd":"ffmpeg http://localhost/ch/1_","logo":"/stalker_portal/misc/logos/1.png","tv_genre_id":"10","xmltv_id":"bbc1.uk"},
        {"id":"2","name":"No cmd"}]}}"""
    private val requests = mutableListOf<RecordedRequest>()

    private fun js(body: String) = MockResponse().setBody(body)

    @Before fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                val u = request.requestUrl!!
                when (u.encodedPath) {
                    "/portal.php" -> return MockResponse().setResponseCode(404)
                    "/server/load.php" -> return js("<html>not here</html>")
                    "/stalker_portal/server/load.php" -> Unit
                    else -> return MockResponse().setResponseCode(404)
                }
                val action = u.queryParameter("action")
                if (action == "handshake") return js("""{"js":{"token":"T${++tokens}"}}""")
                if (request.getHeader("Authorization") != "Bearer T$tokens") return MockResponse().setResponseCode(401)
                if (expireOnce && action != "get_profile") { expireOnce = false; return js("Authorization failed.") }
                val type = u.queryParameter("type")
                return js(when ("$type/$action") {
                    "stb/get_profile" -> profile
                    "itv/get_genres" -> """{"js":[{"id":"*","title":"All"},{"id":"10","title":"UK News"}]}"""
                    "itv/get_all_channels" -> allChannels
                    "itv/get_ordered_list" -> """{"js":{"total_items":1,"max_page_items":14,"data":[{"id":"9","name":"Paged","cmd":"ffrt http://localhost/ch/9_"}]}}"""
                    "vod/get_categories" -> """{"js":[{"id":"*","title":"All"},{"id":"5","title":"Films"}]}"""
                    "vod/get_ordered_list" -> when {
                        u.queryParameter("movie_id") == "300" -> """{"js":{"total_items":2,"max_page_items":14,"data":[
                            {"id":"300:1","name":"Season 1","cmd":"eyJ0eXBlIjoic2VyaWVzIn0=","series":[1,2,3]},
                            {"id":"300:2","name":"Season 2","cmd":"eyJzIjoyfQ==","series":[1]}]}}"""
                        else -> when (u.queryParameter("p")) {
                            "1" -> """{"js":{"total_items":3,"max_page_items":2,"data":[
                                {"id":"100","name":"Film A","cmd":"/media/100.mpg","year":"2019-01-01","rating_imdb":"7.1","screenshot_uri":"http://img/a.jpg"},
                                {"id":"300","name":"Old Show","is_series":"1","cmd":"x"}]}}"""
                            "2" -> """{"js":{"total_items":3,"max_page_items":2,"data":[{"id":"101","name":"Film B","cmd":"/media/101.mpg"}]}}"""
                            else -> """{"js":{"data":[]}}"""
                        }
                    }
                    "vod/create_link" -> if (u.queryParameter("cmd")!!.let { it == "broken" || it.startsWith("http://direct") }) """{"js":{"cmd":""}}"""
                        else """{"js":{"cmd":"ffmpeg http://cdn/vod/${u.queryParameter("cmd")!!.substringAfterLast('/')}?s=${u.queryParameter("series")}&play_token=abc"}}"""
                    "itv/create_link" -> """{"js":{"cmd":"ffmpeg http://cdn/live/1.ts?play_token=xyz"}}"""
                    "series/get_categories" -> """{"js":[{"id":"20","title":"Drama"}]}"""
                    "series/get_ordered_list" -> when {
                        u.queryParameter("season_id") == "s1" -> """{"js":{"total_items":2,"max_page_items":14,"data":[
                            {"id":"e1","name":"Pilot","series_number":"1","cmd":"/media/e1.mkv"},{"id":"e2","name":"Two","series_number":"2","cmd":"/media/e2.mkv"}]}}"""
                        u.queryParameter("movie_id") == "400" -> """{"js":{"total_items":1,"max_page_items":14,"data":[{"id":"s1","name":"Season 3","season_number":"3"}]}}"""
                        else -> """{"js":{"total_items":1,"max_page_items":14,"data":[{"id":"400","name":"New Show","year":"2021"}]}}"""
                    }
                    else -> """{"js":[]}"""
                })
            }
        }
        server.start()
    }

    @After fun tearDown() = server.shutdown()

    private val login = StalkerLogin("00:1A:79:AA:BB:CC", serial = "SN1", deviceId = "DEV")
    private fun client(url: String = server.url("/c/").toString()) = StalkerClient(url, login, OkHttpClient(), null)
    private fun source() = StalkerSource(7, client())

    @Test fun findsEndpointAndSendsBoxIdentity() = runTest {
        assertEquals(listOf("10" to "UK News"), source().liveCategories().map { it.remoteId to it.name })
        val profileReq = requests.first { it.requestUrl!!.queryParameter("action") == "get_profile" }
        assertEquals("SN1", profileReq.requestUrl!!.queryParameter("sn"))
        assertEquals("DEV", profileReq.requestUrl!!.queryParameter("device_id"))
        assertTrue(profileReq.getHeader("Cookie")!!.startsWith("mac=00%3A1A%3A79%3AAA%3ABB%3ACC;"))
        assertTrue(profileReq.getHeader("User-Agent")!!.contains("MAG200"))
    }

    @Test fun channelsKeepCmdInLinkNotAToken() = runTest {
        val ch = source().liveChannels().single()
        assertEquals("BBC One", ch.name); assertEquals(101, ch.number); assertEquals("10", ch.categoryRemoteId); assertEquals("bbc1.uk", ch.tvgId)
        assertEquals(server.url("/stalker_portal/misc/logos/1.png").toString(), ch.logo)
        assertEquals(StalkerLink(7, "itv", "ffmpeg http://localhost/ch/1_"), StalkerLink.parse(ch.streamUrl))
        assertTrue("T1" !in ch.streamUrl && "00:1A" !in ch.streamUrl)
    }

    @Test fun channelsFallBackToPagedGenres() = runTest {
        allChannels = """{"js":{"data":[]}}"""
        assertEquals(listOf("Paged"), source().liveChannels().map { it.name })
    }

    @Test fun vodPagesAndSplitsOldStyleSeries() = runTest {
        val s = source()
        assertEquals(listOf("Film A", "Film B"), s.movies().map { it.name })
        val a = s.movies().first()
        assertEquals(2019, a.year); assertEquals(7.1, a.rating!!, 0.0); assertEquals("5", a.categoryRemoteId)
        assertEquals(setOf("vod:300" to "Old Show", "400" to "New Show"), s.series().map { it.remoteId to it.name }.toSet())
        assertEquals(setOf("20", "vod:5"), s.seriesCategories().map { it.remoteId }.toSet())
    }

    @Test fun oldStyleSeasonsListEpisodeNumbers() = runTest {
        val d = source().seriesDetail("vod:300")
        assertEquals(listOf(1 to 1, 1 to 2, 1 to 3, 2 to 1), d.episodes.map { it.season to it.number })
        assertEquals(StalkerLink(7, "vod", "eyJ0eXBlIjoic2VyaWVzIn0=", 2), StalkerLink.parse(d.episodes[1].streamUrl))
    }

    @Test fun newStyleSeasonsListEpisodeRows() = runTest {
        val d = source().seriesDetail("400")
        assertEquals(listOf("Pilot", "Two"), d.episodes.map { it.title })
        assertEquals(3, d.episodes.first().season)
        assertEquals(StalkerLink(7, "vod", "/media/e1.mkv"), StalkerLink.parse(d.episodes.first().streamUrl))
    }

    @Test fun createLinkStripsPrefixAndFallsBack() = runTest {
        val c = client()
        assertEquals("http://cdn/live/1.ts?play_token=xyz", c.createLink(StalkerLink(7, "itv", "ffmpeg http://localhost/ch/1_")))
        assertEquals("http://cdn/vod/x?s=2&play_token=abc", c.createLink(StalkerLink(7, "vod", "x", 2)))
        assertEquals("http://direct/stream.ts", c.createLink(StalkerLink(7, "vod", "http://direct/stream.ts")))
        try { c.createLink(StalkerLink(7, "vod", "broken")); fail() } catch (e: StalkerException) {
            assertEquals("The portal didn't give a stream link for this item", e.message)
        }
    }

    @Test fun expiredTokenHandshakesOnceAgain() = runTest {
        val c = client()
        c.call("type" to "itv", "action" to "get_genres")
        expireOnce = true
        val js = c.call("type" to "itv", "action" to "get_genres")
        assertTrue(js.toString().contains("UK News"))
        assertEquals(2, tokens)
    }

    @Test fun blockedMacIsExplained() = runTest {
        profile = """{"js":{"id":null,"block_msg":"Your STB is blocked"}}"""
        try { source().liveCategories(); fail() } catch (e: StalkerException) { assertEquals("Portal: Your STB is blocked", e.message) }
    }

    @Test fun noPortalIsExplained() = runTest {
        try { StalkerSource(7, client(server.url("/nothing/").toString())).liveCategories(); fail() } catch (e: StalkerException) {
            assertTrue(e.message!!, e.message!!.startsWith("No Stalker portal found"))
        }
    }

    @Test fun resolverAddsAgentAndRejectsGonePlaylist() = runTest {
        val p = org.imdtv.iptvtv.data.db.ProviderEntity(id = 7, name = "p", type = org.imdtv.iptvtv.data.db.ProviderType.STALKER,
            url = server.url("/stalker_portal/c/").toString(), username = "00:1a:79:aa:bb:cc", password = "SN1\nDEV", userAgent = "MyBox",
            streamFormat = org.imdtv.iptvtv.data.model.StreamFormat.TS, epgOverrideUrls = emptyList(), lastSync = null)
        val links = StalkerLinks(OkHttpClient()) { if (it == 7L) p else null }
        val (url, headers) = splitStreamHeaders(links.resolve(StalkerLink(7, "itv", "ffmpeg http://localhost/ch/1_").toString()))
        assertEquals("http://cdn/live/1.ts?play_token=xyz", url)
        assertEquals("MyBox", headers["User-Agent"])
        assertEquals("http://plain/a.ts", links.resolve("http://plain/a.ts"))
        try { links.resolve(StalkerLink(8, "itv", "x").toString()); fail() } catch (e: StalkerException) { assertEquals("This playlist was removed", e.message) }
    }

    @Test fun helpers() {
        assertEquals("http://p.example.com", portalBase("http://p.example.com/c/"))
        assertEquals("http://p.example.com", portalBase("http://p.example.com/stalker_portal/c/"))
        assertEquals("http://p.example.com:8080", portalBase("http://p.example.com:8080/portal.php"))
        assertEquals("http://p.example.com", portalBase("http://p.example.com/stalker_portal/server/load.php"))
        assertTrue(isValidMac("00:1a:79:aa:bb:cc")); assertTrue(!isValidMac("00:1A:79:AA:BB")); assertTrue(!isValidMac("00-1A-79-AA-BB-CC"))
        assertEquals("http://x/1.ts", cmdUrl("ffmpeg http://x/1.ts")); assertEquals("http://x/1.ts", cmdUrl("http://x/1.ts")); assertNull(cmdUrl("/media/1.mpg"))
        val l = StalkerLink(3, "vod", "a b&c=d/é", 4)
        assertEquals(l, StalkerLink.parse(l.toString()))
        assertEquals(StalkerLogin("00:1A:79:AA:BB:CC", "SN", null), StalkerLogin.from("00:1a:79:aa:bb:cc", "SN"))
        assertEquals("SN\nDEV", StalkerLogin("m", "SN", "DEV").packed()); assertNull(StalkerLogin("m", " ", null).packed())
        assertEquals(StalkerLogin("M", null, "DEV"), StalkerLogin.from("m", StalkerLogin("m", null, "DEV").packed()))
    }

    @Test fun stalkerLoginPackingIsBackwardCompatible() {
        assertEquals(StalkerLogin("M", "SN"), StalkerLogin.from("m", "SN"))
        assertEquals(StalkerLogin("M", "SN", "DEV"), StalkerLogin.from("m", "SN\nDEV"))
        assertEquals(StalkerLogin("M", "SN", "DEV", "DEV2", "MAG322"), StalkerLogin.from("m", "SN\nDEV\nDEV2\nMAG322"))
        assertEquals(StalkerLogin("M", null, null, null, "MAG322"), StalkerLogin.from("m", "\n\n\nMAG322"))
        val l = StalkerLogin("M", "SN", null, "D2", "MAG322")
        assertEquals("SN\n\nD2\nMAG322", l.packed()); assertEquals(l, StalkerLogin.from("M", l.packed()))
        assertEquals("SN\nDEV", StalkerLogin("M", "SN", "DEV", " ", "").packed())
        assertEquals("SN", StalkerLogin("M", "SN", null, null, null).packed())
    }
}
