package org.imdtv.iptvtv.ui.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimelineTest {
    private val min = 60_000L

    @Test
    fun clipsToWindowInMinutes() {
        val from = 1000 * min
        val to = from + 120 * min
        assertEquals(Span(0, 20), Timeline.span(from - 10 * min, from + 20 * min, from, to))  // started before
        assertEquals(Span(30, 60), Timeline.span(from + 30 * min, from + 90 * min, from, to))
        assertEquals(Span(100, 20), Timeline.span(from + 100 * min, from + 200 * min, from, to)) // runs past end
        assertNull(Timeline.span(to, to + 10 * min, from, to))
    }

    @Test
    fun cursorStepsThroughProgrammesAndBackToNow() {
        val h = 60 * min
        val base = 1000 * h
        val now = base + 10 * min
        fun p(s: Long, e: Long) = org.imdtv.iptvtv.data.repo.GuideProgramme("t", null, null, s, e)
        val progs = listOf(p(base, base + h), p(base + h, base + 2 * h), p(base + 2 * h, base + 3 * h))
        assertEquals(base + h, Timeline.stepCursor(progs, null, now, 1))          // now → next
        assertEquals(base + 2 * h, Timeline.stepCursor(progs, base + h, now, 1))
        assertEquals(base + h, Timeline.stepCursor(progs, base + 2 * h, now, -1))
        assertNull(Timeline.stepCursor(progs, base + h, now, -1))                 // previous is on now
        assertNull(Timeline.stepCursor(progs, null, now, -1))                     // can't move: same as at
        assertEquals(base + 30 * min, Timeline.stepCursor(emptyList(), null, now, 1)) // no guide: half-hours
        assertEquals(base + h, Timeline.stepCursor(emptyList(), base + 90 * min, now, -1))
    }

    @Test
    fun windowStartsAtPreviousHalfHour() {
        assertEquals(1800_000L * 3, Timeline.windowStart(1800_000L * 3 + 29 * min))
    }

    @Test
    fun returningToTheGuideRefocusesTheWatchedChannel() {
        assertEquals(2, Timeline.rowFor(listOf(10L, 11L, 12L), focusedId = 12L))
        assertEquals(0, Timeline.rowFor(listOf(10L, 11L, 12L), focusedId = 99L)) // other category: start at the top
        assertEquals(0, Timeline.rowFor(listOf(10L), focusedId = null))
    }

    @Test fun okWithoutAutoplayPreviewsThenFullScreen() {
        assertEquals(OkAction.PREVIEW, okAction(false, previewingId = null, channelId = 1))
        assertEquals(OkAction.PREVIEW, okAction(false, previewingId = 2, channelId = 1))
        assertEquals(OkAction.FULL_SCREEN, okAction(false, previewingId = 1, channelId = 1))
    }
    @Test fun okWithAutoplayAlwaysFullScreen() = assertEquals(OkAction.FULL_SCREEN, okAction(true, null, 1))

    @Test fun zapWrapsAndHandlesSingle() {
        assertEquals(2L, zap(listOf(1, 2, 3), 1, +1)); assertEquals(3L, zap(listOf(1, 2, 3), 1, -1))
        assertEquals(1L, zap(listOf(1, 2, 3), 3, +1)); assertEquals(5L, zap(listOf(5), 5, +1))
        assertEquals(1L, zap(listOf(1, 2), 9, +1)); assertNull(zap(emptyList(), 1, +1))
    }

    @Test fun zapInsideRecentsDoesNotReorderThem() {
        assertEquals(false, zapRecordsRecent(org.imdtv.iptvtv.data.repo.RailEntry.Recent))
        assertEquals(true, zapRecordsRecent(org.imdtv.iptvtv.data.repo.RailEntry.Favourites))
        assertEquals(true, zapRecordsRecent(null))
    }

    @Test fun previousChannelStepsBackThroughWatchedOnes() {
        val h = ChannelHistory<String> { it }
        listOf("BBC 1", "ITV 1", "BBC 2").forEach(h::played)
        assertEquals("ITV 1", h.step(1).also { h.played(it!!) }) // switching to it must not reorder the history
        assertEquals("BBC 1", h.step(1)); assertNull(h.step(1))
        assertEquals("ITV 1", h.step(-1))
        h.played("C4") // a fresh channel goes to the newest end
        assertEquals("BBC 2", h.step(1))
    }

    @Test fun pastCursorStepsThroughAiredProgrammesWithinTheArchive() {
        fun p(s: Long, e: Long) = org.imdtv.iptvtv.data.repo.GuideProgramme("t", null, null, s, e)
        val progs = listOf(p(0, 100), p(100, 200), p(200, 300), p(300, 400)) // 300–400 is on now
        val now = 350L
        assertEquals(100L, Timeline.stepPast(progs, 200, now, -1, 50))   // Left: the one before
        assertEquals(200L, Timeline.stepPast(progs, 200, now, -1, 150))  // Left at the archive limit: stays
        assertEquals(200L, Timeline.stepPast(progs, 100, now, 1, 0))     // Right: the next aired one
        assertEquals(null, Timeline.stepPast(progs, 200, now, 1, 0))     // Right onto now: back to now
        assertEquals(200L, Timeline.stepPast(progs, now, now, -1, 0))    // from now: the one before now's
        assertEquals(100L, Timeline.cursorAt(100, now, past = true))
        assertEquals(now, Timeline.cursorAt(100, now, past = false))     // a stale later cursor that is now in the past
    }
}
