package org.imdtv.iptvtv

import org.junit.Assert.assertEquals
import org.junit.Test

class SmokeTest {
    @Test
    fun applicationIdIsCorrect() {
        assertEquals("org.imdtv.iptvtv", BuildConfig.APPLICATION_ID)
    }
}
