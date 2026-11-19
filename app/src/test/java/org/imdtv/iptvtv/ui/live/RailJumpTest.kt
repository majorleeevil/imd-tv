package org.imdtv.iptvtv.ui.live

import org.junit.Assert.assertEquals
import org.junit.Test

class RailJumpTest {
    // Sections start at 0 (this playlist), 5 and 9 (other playlists); 12 rows.
    private val starts = listOf(0, 5, 9)
    @Test fun upGoesToSectionTopThenPreviousSection() {
        assertEquals(5, railJump(starts, 12, 7, up = true))
        assertEquals(0, railJump(starts, 12, 5, up = true))
        assertEquals(0, railJump(starts, 12, 0, up = true))
    }
    @Test fun downGoesToNextSectionThenBottom() {
        assertEquals(5, railJump(starts, 12, 2, up = false))
        assertEquals(9, railJump(starts, 12, 5, up = false))
        assertEquals(11, railJump(starts, 12, 10, up = false))
    }
}
