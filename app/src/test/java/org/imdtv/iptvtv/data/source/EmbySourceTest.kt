package org.imdtv.iptvtv.data.source

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** Emby (#25): Jellyfin's client with Emby's headers and /emby paths. Untested against a real Emby server. */
class EmbySourceTest {
    private val server = MockWebServer()
    private val auth = """MediaBrowser Client="IMD TV", Device="TV", DeviceId="dev", Version="1""""
    @After fun tearDown() = server.shutdown()

    @Test fun addressGetsOneEmbyPrefix() {
        assertEquals("http://nas:8096/emby", embyBase("nas:8096"))
        assertEquals("http://nas:8096/emby", embyBase("http://nas:8096/emby/"))
        assertEquals("https://e.example/emby", embyBase("https://e.example/web/index.html#!/home"))
    }

    @Test fun callsCarryEmbyHeadersNotAuthorization() = runTest {
        server.enqueue(MockResponse().setBody("""{"Items":[{"Id":"mv","Name":"Films","CollectionType":"movies"}]}"""))
        val s = JellyfinSource(server.url("/emby").toString(), "tok", "uid", auth, emptySet(), OkHttpClient(), xtreamJson(), emby = true)
        assertEquals(listOf("mv"), s.vodCategories().map { it.remoteId })
        val r = server.takeRequest()
        assertEquals("/emby/Users/uid/Views", r.requestUrl!!.encodedPath)
        assertEquals(auth, r.getHeader("X-Emby-Authorization"))
        assertEquals("tok", r.getHeader("X-Emby-Token"))
        assertNull(r.getHeader("Authorization"))
    }

    @Test fun noQuickConnectAndAFailureNamesEmby() = runTest {
        val a = JellyfinAuth(OkHttpClient(), xtreamJson(), auth, emby = true)
        assertFalse(a.quickConnectEnabled(server.url("/emby").toString()))
        assertEquals(0, server.requestCount)
        assertEquals(org.imdtv.iptvtv.R.string.set_b_emby_failed, JellyfinException(500, emby = true).resId)
    }
}
