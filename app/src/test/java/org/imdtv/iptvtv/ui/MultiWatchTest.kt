package org.imdtv.iptvtv.ui

import org.imdtv.iptvtv.data.db.ChannelEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/** #65 Multi-Watch: four channels at most, each once; leaving for a tile's channel drops a stale live player. */
class MultiWatchTest {
    private fun ch(id: Long) = ChannelEntity(id = id, providerId = 1, categoryId = null, remoteId = "$id", name = "c$id", logo = null, number = null, tvgId = null, streamUrl = "u$id")

    @Test fun addsOnceAndAFifthReplacesTheLast() {
        var l = emptyList<ChannelEntity>()
        for (i in 1L..4) l = withMulti(l, ch(i))
        assertEquals(listOf(1L, 2, 3, 4), l.map { it.id })
        assertEquals(l, withMulti(l, ch(2)))
        assertEquals(listOf(1L, 2, 3, 5), withMulti(l, ch(5)).map { it.id })
    }

    @Test fun leavingFromFullScreenGoesBackToTheGuide() {
        assertEquals(listOf(Screen.Live), leftMulti(listOf(Screen.Live, Screen.Player("A", live = true), Screen.MultiWatch)))
        assertEquals(listOf(Screen.Live), leftMulti(listOf(Screen.Live, Screen.MultiWatch)))
        assertEquals(listOf(Screen.Player("A", live = true)), leftMulti(listOf(Screen.Player("A", live = true), Screen.MultiWatch))) // never empty
    }
}
