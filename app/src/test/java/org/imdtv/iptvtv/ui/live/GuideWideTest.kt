package org.imdtv.iptvtv.ui.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideWideTest {
    @Test fun wideChannelsDoublesTheColumnAndKeepsTheGridWidth() {
        for (g in listOf(GLASS_GRID, LIQUID_GRID, XCUBE_GRID, PLUS_GRID, RETRO_GRID)) {
            val w = g.wideChannels()
            assertEquals(g.channelCol.value * 2, w.channelCol.value, .01f)
            assertEquals(g.channelCol.value + g.dpPerMin.value * g.windowMin, w.channelCol.value + w.dpPerMin.value * w.windowMin, .01f)
            assertTrue(w.dpPerMin.value > 0)
        }
    }
}
