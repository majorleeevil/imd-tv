package org.imdtv.iptvtv.ui.sports

import org.imdtv.iptvtv.data.sports.Game
import org.imdtv.iptvtv.data.sports.GameState
import org.imdtv.iptvtv.data.sports.League
import org.imdtv.iptvtv.data.sports.Side
import org.imdtv.iptvtv.data.sports.Sport
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveFirstTest {
    private val side = Side("1", "A", "A", "A", null, null)
    private fun g(id: String, state: GameState, start: Long) =
        Game(id, League(Sport.FOOTBALL, "uefa.europa", "Europa League"), "Europa League", null, start, state, "", side, side, emptyList())

    /** User, 26 Nov: live games first, then those still to kick off (soonest first), full time last. */
    @Test fun liveThenUpcomingThenFinished() {
        val row = listOf(g("ft", GameState.POST, 100), g("later", GameState.PRE, 400), g("live2", GameState.IN, 300),
            g("soon", GameState.PRE, 350), g("live1", GameState.IN, 200))
        assertEquals(listOf("live1", "live2", "soon", "later", "ft"), liveFirst(row).map { it.id })
    }
}
