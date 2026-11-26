package org.imdtv.iptvtv.player

import androidx.media3.exoplayer.audio.AudioSink
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackTuningTest {
    /** #52: +ms = audio later (the clock runs ahead, so video shows sooner); "not set" stays not set. */
    @Test fun audioDelayShiftsTheClock() {
        assertEquals(1_250_000L, delayedPositionUs(1_000_000L, 250))
        assertEquals(500_000L, delayedPositionUs(1_000_000L, -500))
        assertEquals(AudioSink.CURRENT_POSITION_NOT_SET, delayedPositionUs(AudioSink.CURRENT_POSITION_NOT_SET, 300))
    }

    /** #53: 0 = Media3's defaults (today's behaviour); a bigger buffer never lowers the after-a-stall wait. */
    @Test fun bufferBeforePlaying() {
        assertEquals(null, bufferMs(0))
        assertEquals(10_000 to 10_000, bufferMs(10))
        assertEquals(androidx.media3.exoplayer.DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS to androidx.media3.exoplayer.DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS, bufferMs(1))
    }
}
