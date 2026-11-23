package org.imdtv.iptvtv.ui.live

import org.imdtv.iptvtv.data.db.ReminderEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemindersTest {
    private val min = 60_000L
    private fun r(start: Long) = ReminderEntity(channelId = 1, programmeStart = start, title = "t")

    @Test fun dueTwoMinutesBeforeTheStart() {
        val a = r(100 * min)
        assertNull(firstDue(listOf(a), 98 * min - 1))
        assertEquals(a, firstDue(listOf(a), 98 * min))
        assertEquals(98 * min, nextDue(listOf(a), 0))
        assertNull(nextDue(listOf(a), 98 * min))
    }

    @Test fun goneFifteenMinutesAfterTheStart() {
        val a = r(100 * min)
        assertFalse(a.gone(115 * min - 1))
        assertTrue(a.gone(115 * min))
        assertNull(firstDue(listOf(a), 115 * min))
    }

    @Test fun earliestDueFirst() {
        val a = r(100 * min); val b = r(101 * min); val later = r(200 * min)
        assertEquals(a, firstDue(listOf(b, later, a), 100 * min))
        assertEquals(198 * min, nextDue(listOf(b, later, a), 100 * min))
    }
}
