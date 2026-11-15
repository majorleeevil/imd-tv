package org.imdtv.iptvtv.ui.retro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetroPagesTest {
    @Test fun pagesOfNine() {
        val all = (0 until 20).toList()
        val p0 = retroPage(all, 0)
        assertEquals((0..8).toList(), p0.items); assertTrue(p0.hasMore); assertFalse(p0.hasBack)
        val p2 = retroPage(all, 2)
        assertEquals(listOf(18, 19), p2.items); assertFalse(p2.hasMore); assertTrue(p2.hasBack)
    }

    @Test fun exactMultipleHasNoEmptyPage() {
        val p = retroPage((0 until 18).toList(), 5)
        assertEquals(1, p.page); assertFalse(p.hasMore); assertEquals((9..17).toList(), p.items)
    }

    @Test fun lastPageOfOne() = assertEquals(listOf(9), retroPage((0 until 10).toList(), 1).items)

    @Test fun emptyRail() {
        val p = retroPage(emptyList<Int>(), 0)
        assertTrue(p.items.isEmpty()); assertFalse(p.hasMore); assertFalse(p.hasBack)
    }

    @Test fun pageClampsWhenRailShrinks() = assertEquals(1, retroPage((0 until 12).toList(), 2).page)
}
