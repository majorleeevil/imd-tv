package org.imdtv.iptvtv.ui

import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ClockTimeTest {
    private val zone = TimeZone.getDefault()
    @Before fun setUp() = TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    @After fun tearDown() { TimeZone.setDefault(zone); use24Hour = true }

    // 2026-11-17 13:05 UTC
    private val t = 1_794_920_700_000L

    @Test fun twentyFourHour() { use24Hour = true; assertEquals("13:05", clockTime(t)) }
    @Test fun twelveHour() { use24Hour = false; assertEquals("1.05pm", clockTime(t)) }
}
