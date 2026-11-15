package org.imdtv.iptvtv.ui.live

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryTabsTest {
    @Test fun tabWidthClamps() {
        assertEquals(110.dp, tabWidth(20.dp))
        assertEquals(148.dp, tabWidth(120.dp))
        assertEquals(220.dp, tabWidth(400.dp))
    }
}
