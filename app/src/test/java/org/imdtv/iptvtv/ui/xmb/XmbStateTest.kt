package org.imdtv.iptvtv.ui.xmb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XmbStateTest {
    @Test fun leftRightMovesColumnsAndClamps() {
        val s = XmbState()
        assertEquals(XmbColumn.SEARCH, s.left().column)
        assertEquals(XmbColumn.SETTINGS, s.left().left().column)
        assertEquals(XmbColumn.SETTINGS, s.left().left().left().column)
        assertEquals(XmbColumn.SERIES, s.right().right().right().column)
        assertEquals(0, XmbState(item = 3).right().item)
    }

    @Test fun upDownClampAndEmptyList() {
        var s = XmbState()
        repeat(5) { s = s.down(3) }
        assertEquals(2, s.item)
        assertEquals(s, s.down(0))
        assertEquals(1, s.up(3).item)
    }

    @Test fun openAndBack() {
        val open = XmbState(item = 2).open()
        assertEquals(2, open.folder); assertEquals(0, open.folderItem)
        assertEquals(1, open.down(5).folderItem)
        assertNull(open.back()!!.folder)
        assertNull(XmbState().back())
    }

    @Test fun leftRightIgnoredInFolder() {
        val open = XmbState(item = 1).open()
        assertEquals(open, open.left()); assertEquals(open, open.right())
    }

    @Test fun clampAfterListShrinks() {
        val a = XmbState(item = 8, folder = 8, folderItem = 40).clamp(itemCount = 5, folderCount = 10)
        assertEquals(4, a.item); assertNull(a.folder); assertEquals(0, a.folderItem)
        assertEquals(9, XmbState(item = 2, folder = 2, folderItem = 40).clamp(5, 10).folderItem)
    }

    @Test fun recordingsColumnOnlyWhenOn() {
        val series = XmbState(column = XmbColumn.SERIES)
        assertEquals(XmbColumn.SERIES, series.right().column)
        assertEquals(XmbColumn.RECORDINGS, series.right(xmbColumns(true)).column)
        assertEquals(XmbColumn.SERIES, XmbState(column = XmbColumn.RECORDINGS).left(xmbColumns(true)).column)
    }
}
