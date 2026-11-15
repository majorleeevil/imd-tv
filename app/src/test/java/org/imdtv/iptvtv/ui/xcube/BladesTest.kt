package org.imdtv.iptvtv.ui.xcube

import org.junit.Assert.assertEquals
import org.junit.Test
import org.imdtv.iptvtv.ui.Tab
import org.imdtv.iptvtv.ui.visibleTabs

class BladesTest {
    @Test fun bladeSidesSplitAroundSelected() =
        assertEquals(listOf(Tab.SEARCH, Tab.LIVE) to listOf(Tab.SERIES, Tab.SETTINGS), bladeSides(Tab.MOVIES))

    @Test fun nextBladeClamps() {
        assertEquals(Tab.SEARCH, nextBlade(Tab.LIVE, -1))
        assertEquals(Tab.SEARCH, nextBlade(Tab.SEARCH, -1))
        assertEquals(Tab.SETTINGS, nextBlade(Tab.SETTINGS, 1))
        assertEquals(Tab.MOVIES, nextBlade(Tab.LIVE, 1))
    }

    @Test fun recordingsBladeOnlyWhenOn() {
        assertEquals(Tab.SETTINGS, nextBlade(Tab.SERIES, 1, visibleTabs(false)))
        assertEquals(Tab.RECORDINGS, nextBlade(Tab.SERIES, 1, visibleTabs(true)))
        assertEquals(listOf(Tab.SEARCH, Tab.LIVE, Tab.MOVIES, Tab.SERIES) to listOf(Tab.SETTINGS), bladeSides(Tab.RECORDINGS, visibleTabs(true)))
    }
}
