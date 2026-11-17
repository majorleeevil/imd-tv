package org.imdtv.iptvtv.data.support

import org.junit.Assert.*
import org.junit.Test

class LogShareTest {
    private fun s(x: String, lit: List<String> = emptyList()) = scrubLog(x, lit)

    @Test fun userinfo() = assertEquals("GET http://***:***@h.tv/x", s("GET http://bob:pw@h.tv/x"))
    @Test fun xtreamPaths() {
        for (k in listOf("live", "movie", "series", "timeshift")) assertEquals("http://h/$k/***/***/1.ts", s("http://h/$k/bob/pw/1.ts"))
    }
    @Test fun queryParams() {
        assertEquals("http://h/player_api.php?username=***&password=***&action=x", s("http://h/player_api.php?username=bob&password=pw&action=x"))
        assertEquals("http://h/xmltv.php?username=***&password=***", s("http://h/xmltv.php?username=bob&password=pw"))
        assertEquals("http://h/p?a=1&token=***&api_key=***&mac=***", s("http://h/p?a=1&token=abc&api_key=k&mac=00%3A1A"))
    }
    @Test fun mac() = assertEquals("mac ***/***", s("mac 00:1A:79:AB:CD:EF/00-1a-79-ab-cd-ef"))
    @Test fun headers() = assertEquals("Authorization: ***\nCookie: ***\nX: ok", s("Authorization: Bearer abc\nCookie: a=b; c=d\nX: ok"))
    @Test fun literals() {
        assertEquals("u=*** p=*** x%20y=ok", s("u=secretuser p=p@ss word x%20y=ok".replace("p@ss word", "p@ssword"), listOf("secretuser", "p@ssword", "ab")))
        assertEquals("***", s("p%40ssword", listOf("p@ssword")))
        assertEquals("keep ab", s("keep ab", listOf("ab")))
    }
    @Test fun requestMatching() {
        val t = "abc123"
        assertTrue(logRequestMatches("GET /abc123/imd-tv-log.txt HTTP/1.1", t))
        assertFalse(logRequestMatches("GET /abc123/imd-tv-log.txt?x=1 HTTP/1.1", t))
        assertFalse(logRequestMatches("GET /abc124/imd-tv-log.txt HTTP/1.1", t))
        assertFalse(logRequestMatches("GET / HTTP/1.1", t))
        assertFalse(logRequestMatches("POST /abc123/imd-tv-log.txt HTTP/1.1", t))
        assertFalse(logRequestMatches("", t))
    }
}
