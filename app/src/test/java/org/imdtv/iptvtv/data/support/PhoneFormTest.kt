package org.imdtv.iptvtv.data.support

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneFormTest {
    @Test fun onlyTheTokenPathAnswers() {
        assertEquals("GET", phoneRequest("GET /add-a-playlist HTTP/1.1", "add-a-playlist"))
        assertEquals("GET", phoneRequest("GET /Add-A-Playlist/ HTTP/1.1", "add-a-playlist"))
        assertEquals("POST", phoneRequest("POST /add-a-playlist HTTP/1.1\r", "add-a-playlist"))
        assertNull(phoneRequest("GET /edit-a-playlist HTTP/1.1", "add-a-playlist"))
        assertNull(phoneRequest("GET /add-a-playlist?x=1 HTTP/1.1", "add-a-playlist"))
        assertNull(phoneRequest("PUT /add-a-playlist HTTP/1.1", "add-a-playlist"))
    }

    @Test fun formBodyDecodes() {
        assertEquals(mapOf("xurl" to "http://a.b:80", "user" to "me & you", "pass" to ""),
            parseForm("xurl=http%3A%2F%2Fa.b%3A80&user=me+%26+you&pass="))
    }
}
