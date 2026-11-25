package org.imdtv.iptvtv

import org.junit.Assert.assertEquals
import org.junit.Test

class FitPhoneTest {
    @Test fun aPhoneShrinksToFitATabletAndTvDoNot() {
        assertEquals(320, phoneDensityDpi(1080, 420)) // Pixel 7 landscape: 1080 px tall at 420 dpi = 411 dp → 540 dp
        assertEquals(null, phoneDensityDpi(1600, 320)) // tablet: 800 dp tall already
        assertEquals(null, phoneDensityDpi(1080, 320)) // 1080p TV: exactly 540 dp
    }
}
