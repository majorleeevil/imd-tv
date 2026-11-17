package org.imdtv.iptvtv.ui

import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ZapKeysTest {
    @Test fun upDownByDefault() {
        assertEquals(-1, zapDelta(Key.DirectionUp, false)); assertEquals(1, zapDelta(Key.DirectionDown, false))
        assertNull(zapDelta(Key.DirectionLeft, false)); assertNull(zapDelta(Key.DirectionRight, false))
    }

    @Test fun leftRightWhenChosen() {
        assertEquals(-1, zapDelta(Key.DirectionLeft, true)); assertEquals(1, zapDelta(Key.DirectionRight, true))
        assertNull(zapDelta(Key.DirectionUp, true)); assertNull(zapDelta(Key.DirectionDown, true))
    }
}
