package org.imdtv.iptvtv.ui.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StepAmongTest {
    @Test fun stepsOverNonPeers() {
        // b and d are peers of c; x is hidden/other placement and is stepped over.
        val all = listOf("a", "b", "x", "c", "d")
        val peer = { s: String -> s in setOf("b", "c", "d") }
        assertEquals(listOf("a", "c", "b", "x", "d"), stepAmong(all, "c", -1, peer))
        assertEquals(listOf("a", "b", "x", "d", "c"), stepAmong(all, "c", 1, peer))
        assertEquals(listOf("a", "x", "c", "b", "d"), stepAmong(all, "b", 1, peer))
    }

    @Test fun staysAtTheEnds() {
        val peer = { _: String -> true }
        assertNull(stepAmong(listOf("a", "b"), "a", -1, peer))
        assertNull(stepAmong(listOf("a", "b"), "b", 1, peer))
        assertNull(stepAmong(listOf("a"), "a", 1, peer))
        assertEquals(listOf("b", "a"), stepAmong(listOf("a", "b"), "b", -1, peer))
    }
}
