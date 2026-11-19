package org.imdtv.iptvtv.data.source

import org.imdtv.iptvtv.data.security.Login
import org.imdtv.iptvtv.data.security.withLogin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class CatchUpTest {
    private val live = "http://host.example:8080/live/~iptvu~/~iptvp~/162258.ts"
    private val start = 1_763_542_800_000L // 2025-11-19 09:00 UTC
    private val utc = TimeZone.getTimeZone("UTC")

    @Test fun pathFormFirstThenPhp() {
        val urls = catchUpUrls(live, start, start + 45 * 60_000, utc)
        assertEquals("http://host.example:8080/timeshift/~iptvu~/~iptvp~/45/2025-11-19:09-00/162258.ts", urls[0])
        assertEquals("http://host.example:8080/streaming/timeshift.php?username=%7Eiptvu%7E&password=%7Eiptvp%7E&stream=162258&start=2025-11-19%3A09-00&duration=45", urls[1])
    }

    @Test fun serverZoneAndRoundedUpMinutes() {
        val urls = catchUpUrls(live, start, start + 30 * 60_000 + 1, TimeZone.getTimeZone("Europe/Amsterdam"))
        assertEquals("http://host.example:8080/timeshift/~iptvu~/~iptvp~/31/2025-11-19:10-00/162258.ts", urls[0])
    }

    @Test fun loginFillsBothForms() {
        val (a, b) = catchUpUrls(live, start, start + 60_000, utc).map { withLogin(it, Login("me", "pw")) }
        assertEquals("http://host.example:8080/timeshift/me/pw/1/2025-11-19:09-00/162258.ts", a)
        assertTrue(b, b.contains("username=me&password=pw&stream=162258"))
    }

    @Test fun hlsAndSubpathHosts() {
        assertEquals("http://h/x/timeshift/~iptvu~/~iptvp~/1/2025-11-19:09-00/7.ts",
            catchUpUrls("http://h/x/live/~iptvu~/~iptvp~/7.m3u8", start, start + 1, utc)[0])
    }

    @Test fun notXtream() {
        assertTrue(catchUpUrls("http://h/stream/abc.m3u8", start, start + 1, utc).isEmpty())
        assertTrue(catchUpUrls("stalker://123", start, start + 1, utc).isEmpty())
    }
}
