package org.imdtv.iptvtv.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import android.content.Context
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertNotSame
import org.imdtv.iptvtv.data.settings.AudioDecoder
import org.junit.Test
import kotlinx.coroutines.launch
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayerManagerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = PlayerManager(context)

    @After fun release() = manager.player.release()

    @Test
    fun playingSameUrlTwiceDoesNotReprepare() {
        manager.play("http://x/1.ts", null)
        val item = manager.player.currentMediaItem
        manager.play("http://x/1.ts", null)
        assertSame(item, manager.player.currentMediaItem)
    }

    @Test
    fun playingAnotherUrlSwitches() {
        manager.play("http://x/1.ts", null)
        manager.play("http://x/2.ts", "UA")
        assertEquals("http://x/2.ts", manager.player.currentMediaItem?.localConfiguration?.uri.toString())
    }

    @Test
    fun attachMovesTheSamePlayerBetweenViews() {
        val a = PlayerView(context)
        val b = PlayerView(context)
        manager.play("http://x/1.ts", null)
        val item = manager.player.currentMediaItem
        manager.attach(a)
        manager.attach(b)
        assertNull(a.player)
        assertSame(manager.player, b.player)
        assertSame(item, manager.player.currentMediaItem)
    }

    @Test
    fun keepsScreenOnOnlyWhilePlaying() {
        val v = PlayerView(context)
        manager.attach(v)
        manager.onPlayingChanged(true)
        assertTrue(v.keepScreenOn)
        manager.onPlayingChanged(false)
        assertFalse(v.keepScreenOn)
    }

    @Test
    fun backgroundReleasesTheStreamAndForegroundResumesIt() {
        manager.play("http://x/1.ts", "UA")
        manager.toBackground()
        assertEquals("http://x/1.ts", manager.currentUrl) // a recreated guide must find the channel it will resume
        assertEquals(androidx.media3.common.Player.STATE_IDLE, manager.player.playbackState)
        assertNull(manager.player.currentMediaItem)
        manager.toForeground()
        assertEquals("http://x/1.ts", manager.player.currentMediaItem?.localConfiguration?.uri.toString())
    }

    @Test
    fun foregroundWithoutBackgroundDoesNothing() {
        manager.toForeground()
        assertNull(manager.player.currentMediaItem)
    }

    @Test
    fun decoderChangeAppliesWhenIdle() {
        val before = manager.player
        assertEquals(AudioDecoder.SOFTWARE, manager.audioDecoder)
        manager.setAudioDecoder(AudioDecoder.HARDWARE)
        assertEquals(AudioDecoder.HARDWARE, manager.audioDecoder)
        assertNotSame(before, manager.player)
    }

    @Test
    fun decoderChangeWaitsForNextStream() {
        manager.play("http://x/1.ts", null)
        val before = manager.player
        manager.setAudioDecoder(AudioDecoder.HARDWARE)
        assertEquals(AudioDecoder.SOFTWARE, manager.audioDecoder)
        assertSame(before, manager.player)
        manager.play("http://x/2.ts", null)
        assertEquals(AudioDecoder.HARDWARE, manager.audioDecoder)
        assertNotSame(before, manager.player)
        assertEquals("http://x/2.ts", manager.player.currentMediaItem?.localConfiguration?.uri.toString())
        assertTrue(manager.player.playWhenReady) // the rebuilt player must start, not sit paused
    }

    // --- Subtitles (C7) ---
    private val base = androidx.media3.common.TrackSelectionParameters.Builder(context).build()
    private val TEXT = androidx.media3.common.C.TRACK_TYPE_TEXT

    @Test
    fun offIgnoresDefaultFlagButNotForced() {
        val p = subtitleParams(base, on = false, language = "en")
        assertTrue(p.preferredTextLanguages.isEmpty())
        assertEquals(androidx.media3.common.C.SELECTION_FLAG_DEFAULT, p.ignoredTextSelectionFlags)
        assertFalse(TEXT in p.disabledTrackTypes)
    }

    @Test
    fun onUsesOnlyTheLanguage() {
        val p = subtitleParams(base, on = true, language = "de")
        assertEquals(listOf("de"), p.preferredTextLanguages)
        assertEquals(androidx.media3.common.C.SELECTION_FLAG_DEFAULT, p.ignoredTextSelectionFlags)
        assertFalse(p.selectUndeterminedTextLanguage)
    }

    private fun withTextOverride(p: androidx.media3.common.TrackSelectionParameters) = p.buildUpon().addOverride(
        androidx.media3.common.TrackSelectionOverride(androidx.media3.common.TrackGroup(
            androidx.media3.common.Format.Builder().setSampleMimeType(androidx.media3.common.MimeTypes.APPLICATION_SUBRIP).setLanguage("fr").build()), 0),
    ).build()

    @Test
    fun newTitleDropsManualChoice() {
        manager.setSubtitles(true, null)
        manager.player.trackSelectionParameters = withTextOverride(manager.player.trackSelectionParameters)
        manager.play("http://x/2.mkv", null, vod = true)
        assertTrue(manager.player.trackSelectionParameters.overrides.keys.none { it.type == TEXT })
        assertTrue(manager.player.trackSelectionParameters.preferredTextLanguages.isNotEmpty())
    }

    @Test
    fun decoderSwapKeepsSetting() {
        manager.play("http://x/film.mkv", null, vod = true)
        manager.stop() // idle: the decoder change rebuilds the player now
        manager.setSubtitles(true, "es")
        manager.setAudioDecoder(AudioDecoder.HARDWARE)
        assertEquals(listOf("es"), manager.player.trackSelectionParameters.preferredTextLanguages)
    }

    @Test
    fun liveIgnoresTheSubtitlesSetting() {
        manager.setSubtitles(true, "fr")
        manager.play("http://x/live.ts", null) // live: no vod flag
        val p = manager.player.trackSelectionParameters
        assertTrue(p.preferredTextLanguages.isEmpty())
        // A stream's "default" subtitles never switch on by themselves on live TV (forced ones still do).
        assertEquals(androidx.media3.common.C.SELECTION_FLAG_DEFAULT, p.ignoredTextSelectionFlags)
        manager.play("http://x/film.mkv", null, vod = true)
        assertEquals(listOf("fr"), manager.player.trackSelectionParameters.preferredTextLanguages)
    }

    @Test
    fun dashStreamsLoad() { // iptv-org's BBC streams are DASH (.mpd): this used to crash the app (no DASH module)
        manager.play("http://x/live.mpd", null)
        assertEquals("http://x/live.mpd", manager.player.currentMediaItem?.localConfiguration?.uri.toString())
    }

    @Test
    fun unsupportedStreamFormatDoesNotCrash() { // a format with no module (Smooth Streaming) must fail quietly, not crash
        manager.play("http://x/live.ism/Manifest", null)
        assertNull(manager.player.currentMediaItem)
    }

    @Test
    fun headerSuffixIsNotPartOfTheStreamAddress() {
        manager.play("http://x/live.m3u8|User-Agent=VLC", null)
        assertEquals("http://x/live.m3u8", manager.player.currentMediaItem?.localConfiguration?.uri.toString())
    }

    @Test
    fun guardBlocksThenRetryPlaysWithoutAskingAgain() {
        var retry: (() -> Unit)? = null
        var asks = 0
        manager.guard = { r -> asks++; retry = r; true }
        manager.play("http://x/1.ts", null)
        assertNull(manager.player.currentMediaItem)
        assertNull(manager.playingUrl.value)
        retry!!()
        assertEquals(1, asks)
        assertEquals("http://x/1.ts", manager.playingUrl.value)
    }

    @Test
    fun guardAllowingPlaysAtOnce() {
        manager.guard = { false }
        manager.play("http://x/1.ts", null)
        assertEquals("http://x/1.ts", manager.playingUrl.value)
    }

    @Test
    fun portalDownDuringRetriesKeepsRetryingThenReports() {
        val looper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
        var calls = 0
        manager.resolveLink = { calls++; if (calls == 1) "http://x/1.ts" else throw java.io.IOException("portal down") }
        val got = mutableListOf<androidx.media3.common.PlaybackException>()
        val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined).launch { manager.errors.collect { got += it } }
        manager.play("stalker://1/itv?cmd=x", null)
        looper.idle()
        assertEquals(1, calls)
        manager.streamError(androidx.media3.common.PlaybackException("lost", null, androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
        for ((delay, expected) in listOf(1L to 2, 2L to 3)) {
            looper.idleFor(java.time.Duration.ofSeconds(delay))
            assertEquals(expected, calls)
            assertTrue("no error while retries are left", got.isEmpty())
        }
        looper.idleFor(java.time.Duration.ofSeconds(4))
        assertEquals(4, calls)
        assertEquals("portal down", got.single().message)
        job.cancel()
    }

    @Test
    fun stallClockWaitsLongerForAStartThanForAFreeze() {
        val c = StallClock(startMs = 12_000, playingMs = 5_000)
        assertFalse(c.update(0, 0))
        assertFalse(c.update(0, 11_000)) // still connecting
        assertTrue(c.update(0, 12_000)) // never started
        assertFalse(c.update(500, 13_000)) // playing
        assertFalse(c.update(500, 17_000))
        assertTrue(c.update(500, 18_000)) // frozen for 5 s
        assertFalse(c.update(500, 19_000)) // counts again after firing
        assertTrue(c.update(500, 23_000))
        c.reset() // a new stream gets the start limit again
        assertFalse(c.update(500, 30_000))
        assertFalse(c.update(500, 35_000))
        assertTrue(c.update(500, 42_000))
    }
}
