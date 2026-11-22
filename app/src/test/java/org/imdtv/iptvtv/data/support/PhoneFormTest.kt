package org.imdtv.iptvtv.data.support

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneFormTest {
    @Test fun onlyTheTokenPathAnswers() {
        assertEquals("GET", phoneRequest("GET /abc/ HTTP/1.1", "abc"))
        assertEquals("POST", phoneRequest("POST /abc/ HTTP/1.1\r", "abc"))
        assertNull(phoneRequest("GET /abd/ HTTP/1.1", "abc"))
        assertNull(phoneRequest("GET /abc/?x=1 HTTP/1.1", "abc"))
        assertNull(phoneRequest("PUT /abc/ HTTP/1.1", "abc"))
    }

    @Test fun formBodyDecodes() {
        assertEquals(mapOf("xurl" to "http://a.b:80", "user" to "me & you", "pass" to ""),
            parseForm("xurl=http%3A%2F%2Fa.b%3A80&user=me+%26+you&pass="))
    }
}
