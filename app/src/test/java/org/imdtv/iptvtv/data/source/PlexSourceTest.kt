package org.imdtv.iptvtv.data.source

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.imdtv.iptvtv.data.model.Kind
import org.imdtv.iptvtv.data.security.Login
import org.imdtv.iptvtv.data.security.withLogin
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlexSourceTest {
    private val server = MockWebServer()
    private val pages = mutableListOf<String>()

    // Shapes as the user's server returns them (2026-11-22), trimmed.
    private val sections = """{"MediaContainer":{"Directory":[{"key":"1","type":"movie","title":"Movies"},{"key":"28","type":"movie","title":"Adult"},
        {"key":"3","type":"show","title":"TV Shows"},{"key":"40","type":"artist","title":"Music"}]}}"""
    private fun moviePage(start: Int) = """{"MediaContainer":{"totalSize":3,"Metadata":[""" +
        (start until minOf(start + 2, 3)).joinToString(",") { i ->
            """{"ratingKey":"m$i","title":"Movie $i","year":2020,"audienceRating":7.5,"addedAt":1648596575,"thumb":"/library/metadata/m$i/thumb/1",
               "Media":[{"videoResolution":"1080","Part":[{"key":"/library/parts/$i/file.mkv","container":"mkv"}]}]}"""
        } + "]}}"
    private val shows = """{"MediaContainer":{"totalSize":1,"Metadata":[{"ratingKey":"s1","title":"Show","year":2019,"addedAt":1000}]}}"""
    private val show = """{"MediaContainer":{"Metadata":[{"ratingKey":"s1","title":"Show","summary":"Plot","art":"/art/1","Genre":[{"tag":"Drama"}]}]}}"""
    private val leaves = """{"MediaContainer":{"size":2,"Metadata":[
        {"ratingKey":"e1","title":"Pilot","parentIndex":1,"index":1,"duration":2616740,"Media":[{"Part":[{"key":"/library/parts/91/file.mkv"}]}]},
        {"ratingKey":"e2","title":"No file","parentIndex":1,"index":2}]}}"""

    private fun source(skip: Set<String> = setOf("28")) = PlexSource(server.url("/").toString(), "srvtoken", "cid", skip, OkHttpClient(), xtreamJson())

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val u = request.requestUrl!!
                if (request.getHeader("X-Plex-Token") != "srvtoken") return MockResponse().setResponseCode(401)
                return MockResponse().setBody(when (u.encodedPath) {
                    "/library/sections" -> sections
                    "/library/sections/1/all" -> { pages += u.queryParameter("X-Plex-Container-Start")!!; moviePage(pages.last().toInt()) }
                    "/library/sections/3/all" -> shows
                    "/library/metadata/s1" -> show
                    "/library/metadata/s1/allLeaves" -> leaves
                    else -> return MockResponse().setResponseCode(404)
                })
            }
        }
    }

    @After fun tearDown() = server.shutdown()

    @Test fun librariesBecomeCategoriesAndSkippedOnesAreNeverFetched() = runTest {
        val s = source()
        assertEquals(listOf("1" to "Movies"), s.vodCategories().map { it.remoteId to it.name })
        assertEquals(listOf(Kind.SERIES), s.seriesCategories().map { it.kind })
        assertTrue(s.liveChannels().isEmpty())
    }

    @Test fun moviesArePagedAndKeepTheTokenOutOfSavedLinks() = runTest {
        val src = source()
        val movies = src.movies()
        assertEquals(listOf("m0", "m1", "m2"), movies.map { it.remoteId })
        assertEquals(listOf("0", "2"), pages) // the server sent 2 per page
        val m = movies.first()
        assertEquals("1", m.categoryRemoteId); assertEquals(1648596575000L, m.added); assertEquals(7.5, m.rating!!, 0.0)
        assertFalse("srvtoken" in m.streamUrl!!); assertFalse("srvtoken" in m.poster!!)
        val played = withLogin(m.streamUrl!!, Login("cid", "srvtoken"))
        assertTrue(played.endsWith("/library/parts/0/file.mkv?X-Plex-Token=srvtoken"))
        assertTrue(withLogin(m.poster!!, Login("cid", "srvtoken")).contains("X-Plex-Token=srvtoken"))
    }

    @Test fun seriesDetailListsPlayableEpisodes() = runTest {
        val d = source().seriesDetail("s1")
        assertEquals("Plot", d.plot); assertEquals("Drama", d.genre)
        assertEquals(1, d.episodes.size) // the one without a file is left out
        assertEquals(1 to 1, d.episodes[0].season to d.episodes[0].number); assertEquals(2616, d.episodes[0].durationSecs)
    }

    @Test fun connectionsDirectLocalHttpsFirst() {
        val c = listOf(PlexConnection("https://relay", local = false, relay = true, protocol = "https"),
            PlexConnection("http://remote:80", protocol = "http"), PlexConnection("https://remote:443", protocol = "https"),
            PlexConnection("https://lan.plex.direct", local = true, protocol = "https"))
        assertEquals(listOf("https://lan.plex.direct", "https://remote:443", "http://remote:80", "https://relay"), orderedUris(c))
    }

    @Test fun adultLibrariesStartUnticked() {
        assertTrue(plexSkipByDefault("Adult")); assertTrue(plexSkipByDefault("XXX movies")); assertFalse(plexSkipByDefault("Movies - 4K"))
    }
}
