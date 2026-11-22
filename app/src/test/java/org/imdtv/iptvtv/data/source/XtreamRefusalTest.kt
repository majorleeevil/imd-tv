package org.imdtv.iptvtv.data.source

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.imdtv.iptvtv.data.model.StreamFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
class XtreamRefusalTest {
    @Test fun refusalIsLoggedWithoutTheLogin() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(512).setBody("Access denied for paul99 / s3cret\n from your IP").setHeader("Server", "nginx"))
        val src = XtreamSource(server.url("/").toString().trimEnd('/'), "paul99", "s3cret", StreamFormat.TS, OkHttpClient(), xtreamJson(), userAgent = "TiviMate/5")
        val e = runCatching { src.liveCategories() }.exceptionOrNull()
        assertTrue(e?.message.orEmpty(), e?.message.orEmpty().startsWith("Xtream request failed: HTTP 512"))
        val line = ShadowLog.getLogsForTag("Xtream").single().msg
        assertTrue(line, "HTTP 512" in line && "server 'nginx'" in line && "agent 'TiviMate/5'" in line && "Access denied for <user> / <pass> from your IP" in line)
        assertFalse(line, "paul99" in line || "s3cret" in line)
        assertEquals(1, server.requestCount)
        server.shutdown()
    }
}
