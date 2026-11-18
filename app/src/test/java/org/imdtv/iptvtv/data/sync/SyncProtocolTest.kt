package org.imdtv.iptvtv.data.sync

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncProtocolTest {
    @Test fun sealOpensWithTheRightKeyOnly() {
        val k = newSyncKey()
        val s = seal(k, "hello".toByteArray())
        assertArrayEquals("hello".toByteArray(), open(k, s))
        assertThrows(Exception::class.java) { open(newSyncKey(), s) }
        s[s.size - 1] = (s.last() + 1).toByte()
        assertThrows(Exception::class.java) { open(k, s) }
    }

    @Test fun keyHexRoundTrips() { val k = newSyncKey(); assertArrayEquals(k, k.hex().unhex()) }

    @Test fun requestsMatchExactly() {
        val r = parseSyncRequest("GET /imd-tv/sync/v1/snapshot HTTP/1.1\r\nX-Follower: abc\r\nX-Hash: 12\r\n\r\n")!!
        assertEquals(SyncPath.SNAPSHOT, r.path)
        assertEquals("abc", r.headers["x-follower"])
        assertNull(parseSyncRequest("GET /imd-tv/sync/v1/snapshot?x=1 HTTP/1.1\r\n\r\n"))
        assertNull(parseSyncRequest("POST /imd-tv/sync/v1/snapshot HTTP/1.1\r\n\r\n"))
        assertNull(parseSyncRequest("GET /imd-tv/sync/v1/snapshot\r\n\r\n"))
        assertNull(parseSyncRequest("garbage"))
        assertEquals(SyncPath.PAIR, parseSyncRequest("POST /imd-tv/sync/v1/pair HTTP/1.0\r\nX-Code: 1\r\n\r\n")!!.path)
    }

    @Test fun pairingAllowsFiveTriesAndOneSuccess() {
        val p = Pairing("Lounge", code = "123456")
        repeat(4) { assertFalse(p.tryCode("000000")) }
        assertTrue(p.tryCode("123456"))
        assertFalse(p.open)
        assertFalse(p.tryCode("123456"))
        val q = Pairing("Lounge", code = "123456")
        repeat(5) { q.tryCode("1") }
        assertFalse(q.tryCode("123456"))
    }

    @Test fun pairingExpires() {
        assertFalse(Pairing("x", code = "1", startedAt = System.currentTimeMillis() - PAIR_MS - 1).tryCode("1"))
    }

    @Test fun pairCodeIsSixDigits() = repeat(50) { assertTrue(newPairCode().matches(Regex("\\d{6}"))) }

    @Test fun followerNameIsCleaned() {
        assertEquals("A box", cleanName(null))
        assertEquals("LoungeTV", cleanName(" Lounge\r\nTV "))
        assertEquals(40, cleanName("x".repeat(100)).length)
    }
}
