package org.imdtv.iptvtv.ui.live

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class GuideRowsTest {
    @Test fun rowsShareTheAreaWithTheirGaps() {
        assertEquals(72, guideRowPx(394, 5, 8)) // whole pixels; the 2 left over stay below the list
        assertEquals(35, guideRowPx(394, 10, 4))
    }

    @Test fun tallRowsKeepTheirFontAndOneLine() {
        val s = GLASS_GRID.forGuide(multiLine = false)
        assertEquals(1f, s.textScale, 0f); assertEquals(1, s.lines)
        assertEquals(1f, RETRO_GRID.forGuide(false).textScale, 0f)
    }

    @Test fun shortRowsShrinkText() {
        val s = GLASS_GRID.copy(rowHeight = 14.4.dp).forGuide(false)
        assertEquals(.65f, s.textScale, 0f); assertEquals(1, s.lines)
    }

    @Test fun multiLineNeedsRoomAndShrinksToFit() {
        val five = GLASS_GRID.copy(rowHeight = 29.2.dp).forGuide(true)
        assertEquals(2, five.lines); assertEquals((29.2f - 2) / 33.6f, five.textScale, .001f)
        assertEquals(2, XCUBE_GRID.forGuide(true).lines)
        assertEquals(1, GLASS_GRID.copy(rowHeight = 18.5.dp).forGuide(true).lines)
    }

    @Test fun epgTextSizeGrowsOnlyAsFarAsTheRowHolds() {
        assertEquals(1.2f, GLASS_GRID.copy(rowHeight = 40.dp).forGuide(false, 1.2f).textScale, 0f)
        assertEquals((22f - 4) / 17.5f, GLASS_GRID.copy(rowHeight = 22.dp).forGuide(false, 1.2f).textScale, .001f)
        assertEquals(.88f, GLASS_GRID.forGuide(false, .88f).textScale, 0f)
    }
}
