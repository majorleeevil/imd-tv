package org.imdtv.iptvtv.data.support

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneFormTest {
    @Test fun onlyTheTokenPathAnswers() {
        assertEquals("GET", phoneRequest("GET /addplaylist HTTP/1.1", "addplaylist"))
        assertEquals("GET", phoneRequest("GET /AddPlaylist/ HTTP/1.1", "addplaylist"))
        assertEquals("POST", phoneRequest("POST /addplaylist HTTP/1.1\r", "addplaylist"))
        assertNull(phoneRequest("GET /editplaylist HTTP/1.1", "addplaylist"))
        assertNull(phoneRequest("GET /addplaylist?x=1 HTTP/1.1", "addplaylist"))
        assertNull(phoneRequest("PUT /addplaylist HTTP/1.1", "addplaylist"))
    }

    @Test fun formBodyDecodes() {
        assertEquals(mapOf("xurl" to "http://a.b:80", "user" to "me & you", "pass" to ""),
            parseForm("xurl=http%3A%2F%2Fa.b%3A80&user=me+%26+you&pass="))
    }
}
