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

    /** Read-ahead buffers live on the Java heap on Android, beside Media3's ~125 MiB video buffer (OOM crashes, 26 Nov). */
    @Test fun readAheadLeavesRoomForMedia3AndTheApp() {
        val mib = 1L shl 20
        assertEquals(16 * mib, readAheadFor(availRam = 800 * mib, maxHeap = 256 * mib))  // was 200 MiB: crashed ~40 s in
        assertEquals(128 * mib, readAheadFor(availRam = 800 * mib, maxHeap = 384 * mib)) // largeHeap on the Chromecast: heap levelled at ~335 of 384 MB playing 4K
        assertEquals(100 * mib, readAheadFor(availRam = 400 * mib, maxHeap = 512 * mib)) // free RAM still limits
        assertEquals(16 * mib, readAheadFor(availRam = 800 * mib, maxHeap = 192 * mib))  // floor
    }
}
