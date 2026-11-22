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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JellyfinSourceTest {
    private val server = MockWebServer()
    private val pages = mutableListOf<String>()
    private val auth = """MediaBrowser Client="IMD TV", Device="TV", DeviceId="dev", Version="1""""

    // Shapes as Jellyfin's demo server (12.4) returns them (2026-11-22), trimmed.
    private val views = """{"Items":[{"Id":"mv","Name":"Movies","CollectionType":"movies"},{"Id":"xx","Name":"Adult","CollectionType":"movies"},
        {"Id":"mu","Name":"Music","CollectionType":"music"},{"Id":"tv","Name":"Shows","CollectionType":"tvshows"}],"TotalRecordCount":4}"""
    private fun moviePage(start: Int) = """{"TotalRecordCount":3,"StartIndex":$start,"Items":[""" +
        (start until minOf(start + 2, 3)).joinToString(",") { i ->
            """{"Name":"Movie $i","Id":"m$i","DateCreated":"2019-11-04T03:08:41.0000000Z","CommunityRating":5.7,"ProductionYear":1976,
               "Type":"Movie","ImageTags":${if (i == 2) "{}" else """{"Primary":"a1"}"""}}"""
        } + "]}"
    private val shows = """{"TotalRecordCount":1,"Items":[{"Name":"Pioneer One","Id":"s1","ProductionYear":2010,"ImageTags":{"Primary":"b2"}}]}"""
    private val show = """{"Name":"Pioneer One","Id":"s1","Overview":"Plot","Genres":["Drama","Sci-Fi"],"BackdropImageTags":["bd"],"ImageTags":{}}"""
    private val episodes = """{"TotalRecordCount":2,"Items":[
        {"Id":"e1","Name":"Earthfall","ParentIndexNumber":1,"IndexNumber":1,"RunTimeTicks":18963580000,"Overview":"Space","ImageTags":{"Primary":"x"},"LocationType":"FileSystem"},
        {"Id":"e2","Name":"Missing","ParentIndexNumber":1,"IndexNumber":2,"LocationType":"Virtual"}]}"""
    private val movie = """{"Name":"Movie 0","Id":"m0","Overview":"Bubble","Genres":["Drama"],"RunTimeTicks":55842000000,"Height":480,
        "People":[{"Name":"Randal Kleiser","Type":"Director"},{"Name":"John Travolta","Type":"Actor"}],"BackdropImageTags":[],"ImageTags":{}}"""

    private fun base() = server.url("/jf/").toString()
    private fun source(skip: Set<String> = setOf("xx")) = JellyfinSource(base(), "tok", "uid", auth, skip, OkHttpClient(), xtreamJson())

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val u = request.requestUrl!!
                if (request.getHeader("Authorization") != "$auth, Token=\"tok\"") return MockResponse().setResponseCode(401)
                return MockResponse().setBody(when (u.encodedPath) {
                    "/jf/Users/uid/Views" -> views
                    "/jf/Users/uid/Items" -> when (u.queryParameter("ParentId")) {
                        "mv" -> { assertEquals("Movie", u.queryParameter("IncludeItemTypes")); pages += u.queryParameter("StartIndex")!!; moviePage(pages.last().toInt()) }
                        "tv" -> shows
                        else -> return MockResponse().setResponseCode(404)
                    }
                    "/jf/Users/uid/Items/s1" -> show
                    "/jf/Users/uid/Items/m0" -> movie
                    "/jf/Shows/s1/Episodes" -> episodes
                    else -> return MockResponse().setResponseCode(404)
                })
            }
        }
    }

    @After fun tearDown() = server.shutdown()

    @Test fun moviesAndShowLibrariesBecomeCategoriesAndSkippedOnesAreNeverFetched() = runTest {
        val s = source()
        assertEquals(listOf("mv" to "Movies"), s.vodCategories().map { it.remoteId to it.name })
        assertEquals(listOf("tv"), s.seriesCategories().map { it.remoteId })
        assertEquals(listOf(Kind.SERIES), s.seriesCategories().map { it.kind })
        assertTrue(s.liveChannels().isEmpty())
    }

    @Test fun moviesArePagedAndKeepTheTokenOutOfSavedLinks() = runTest {
        val movies = source().movies()
        assertEquals(listOf("m0", "m1", "m2"), movies.map { it.remoteId })
        assertEquals(listOf("0", "2"), pages) // the server sent 2 per page
        val m = movies.first()
        assertEquals("mv", m.categoryRemoteId); assertEquals(1572836921000L, m.added); assertEquals(5.7, m.rating!!, 0.0); assertEquals(1976, m.year)
        assertFalse("tok" in m.streamUrl!!)
        assertEquals("${base()}Videos/m0/stream?static=true&api_key=tok", withLogin(m.streamUrl!!, Login("uid", "tok")))
        assertEquals("${base()}Items/m0/Images/Primary?maxWidth=300", m.poster)
        assertNull(movies[2].poster) // no Primary image
    }

    @Test fun seriesDetailListsPlayableEpisodes() = runTest {
        val d = source().seriesDetail("s1")
        assertEquals("Plot", d.plot); assertEquals("Drama, Sci-Fi", d.genre); assertEquals("${base()}Items/s1/Images/Backdrop?maxWidth=1280", d.backdrop)
        assertEquals(1, d.episodes.size) // the missing (Virtual) one is left out
        val e = d.episodes[0]
        assertEquals(1 to 1, e.season to e.number); assertEquals(1896, e.durationSecs); assertEquals("Space", e.plot)
        assertTrue(withLogin(e.streamUrl, Login("uid", "tok")).endsWith("/Videos/e1/stream?static=true&api_key=tok"))
    }

    @Test fun movieDetailHasPeopleAndRuntime() = runTest {
        val d = source().movieDetail("m0")
        assertEquals("Randal Kleiser", d.director); assertEquals("John Travolta", d.cast)
        assertEquals(5584, d.durationSecs); assertEquals(480, d.height); assertNull(d.backdrop)
    }

    @Test fun typedAddressesAreTidied() {
        assertEquals("http://192.168.1.10:8096", jellyfinBase("192.168.1.10:8096"))
        assertEquals("https://demo.jellyfin.org/stable", jellyfinBase(" https://demo.jellyfin.org/stable/web/#/home.html "))
        assertEquals("http://nas:8096/jf", jellyfinBase("http://nas:8096/jf/"))
    }
}
