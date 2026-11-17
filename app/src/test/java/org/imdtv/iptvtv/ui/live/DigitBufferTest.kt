package org.imdtv.iptvtv.ui.live

import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class DigitBufferTest {
    @Test fun appendsDigitsUntilFourThenCommits() {
        val b = DigitBuffer()
        assertNull(b.append(7)); assertNull(b.append(5)); assertEquals("75", b.text)
        assertNull(b.append(0))
        assertEquals(7501, b.append(1))
        assertFalse(b.active)
    }

    @Test fun timeoutAndOkCommitWhatWasTyped() {
        val b = DigitBuffer()
        b.append(7); b.append(5)
        assertEquals(75, b.commit())
        assertNull(b.commit())
    }

    @Test fun backCancels() {
        val b = DigitBuffer()
        b.append(7)
        b.cancel()
        assertFalse(b.active); assertNull(b.commit())
    }

    @Test fun digitKeys() {
        assertEquals(0, digitOf(Key.Zero)); assertEquals(7, digitOf(Key.NumPad7)); assertNull(digitOf(Key.DirectionUp))
    }
}
