package org.imdtv.iptvtv.data.parental

import org.imdtv.iptvtv.data.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParentalTest {
    @Test fun ratingsToAges() {
        assertEquals(12, ageOf("12A")); assertEquals(8, ageOf("PG")); assertEquals(14, ageOf("TV-14"))
        assertEquals(16, ageOf("FSK 16")); assertEquals(18, ageOf("+18")); assertEquals(0, ageOf("U"))
        assertNull(ageOf(null)); assertNull(ageOf("")); assertNull(ageOf("Unrated"))
    }

    @Test fun pinHashMatchesOnlyItself() {
        val h = Pin.hash("1234")
        assertFalse(h.contains("1234"))
        assertTrue(Pin.matches("1234", h)); assertFalse(Pin.matches("1235", h)); assertFalse(Pin.matches("1234", null))
        assertTrue(Pin.matches(" Rabbit ", Pin.hash("rabbit")))
    }

    @Test fun lockedFromTheLimitUpOrByCategory() {
        val s = AppSettings(parentalOn = true, parentalPin = Pin.hash("1"), ageLimit = AgeLimit.A12)
        assertTrue(s.parentalLocked(false, 12)); assertFalse(s.parentalLocked(false, 8))
        assertFalse(s.parentalLocked(false, null)) // unrated stays unlocked
        assertTrue(s.parentalLocked(true, null))
        assertFalse(s.copy(parentalOn = false).parentalLocked(true, 18))
    }
}
