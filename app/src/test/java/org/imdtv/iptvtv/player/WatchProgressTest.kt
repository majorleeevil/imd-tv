package org.imdtv.iptvtv.player

import org.imdtv.iptvtv.data.db.ProgressEntity
import org.imdtv.iptvtv.data.model.Episode
import org.imdtv.iptvtv.data.model.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchProgressTest {
    private fun ep(id: String, season: Int, number: Int) = Episode(id, season, number, "t$id", null, null, null, "http://s/$id")
    private fun row(episode: String, pos: Long = 60_000, at: Long = 1, done: Boolean = false) =
        ProgressEntity(1, Kind.SERIES, 7, pos, 3_600_000, at, done, episode)

    private val eps = listOf(ep("s2e1", 2, 1), ep("s1e2", 1, 2), ep("s1e1", 1, 1))

    @Test fun watchedAtNinetyFivePercent() {
        assertTrue(isWatched(95_000_000, 100_000_000))
        assertFalse(isWatched(94_000_000, 100_000_000))
    }

    @Test fun watchedWithUnderTwoMinutesLeft() {
        assertTrue(isWatched(1_000_000 - 119_000, 1_000_000)) // 88 % in, but a short one
        assertFalse(isWatched(1_000_000 - 121_000, 1_000_000))
    }

    @Test fun notWatchedWithoutDurationOrMidWay() {
        assertFalse(isWatched(0, 0))
        assertFalse(isWatched(5_000_000, 0))
        assertFalse(isWatched(1_800_000, 3_600_000))
    }

    @Test fun resumeAtNullCompletedAndInProgress() {
        assertEquals(0L, resumeAt(null))
        assertEquals(0L, resumeAt(row("", pos = 0, done = true)))
        assertEquals(90_000L, resumeAt(row("", pos = 90_000)))
    }

    @Test fun noEpisodesGivesNull() = assertNull(nextEpisode(emptyList(), listOf(row("x"))))

    @Test fun noRowsGivesFirstEpisodeSorted() {
        assertEquals(NextEpisode(eps[2], 0, false), nextEpisode(eps, emptyList()))
    }

    @Test fun inProgressResumesThatEpisode() {
        assertEquals(NextEpisode(eps[1], 75_000, true), nextEpisode(eps, listOf(row("s1e2", pos = 75_000))))
    }

    @Test fun completedGivesFollowingEpisodeAcrossSeasons() {
        assertEquals(NextEpisode(eps[1], 0, false), nextEpisode(eps, listOf(row("s1e1", done = true))))
        assertEquals(NextEpisode(eps[0], 0, false), nextEpisode(eps, listOf(row("s1e2", done = true))))
    }

    @Test fun completedFinaleWrapsToFirst() {
        assertEquals(NextEpisode(eps[2], 0, false), nextEpisode(eps, listOf(row("s2e1", done = true))))
    }

    @Test fun rowForUnlistedEpisodeIgnored() {
        assertEquals(NextEpisode(eps[2], 0, false), nextEpisode(eps, listOf(row("gone", at = 9))))
        assertEquals(NextEpisode(eps[1], 60_000, true), nextEpisode(eps, listOf(row("gone", at = 9), row("s1e2", at = 1))))
    }

    @Test fun mostRecentRowWins() {
        val rows = listOf(row("s1e1", done = true, at = 1), row("s1e2", pos = 42_000, at = 5), row("s2e1", pos = 1_000, at = 3))
        assertEquals(NextEpisode(eps[1], 42_000, true), nextEpisode(eps, rows))
    }

    @Test fun posterBarUsesTheNewestRowAndHidesWhenWatched() {
        val item = org.imdtv.iptvtv.ui.vod.VodItem(7, Kind.SERIES, "r", "n", null, null, null, null, null, 1)
        assertEquals(.25f, org.imdtv.iptvtv.ui.vod.progressFraction(item, listOf(row("s1e2", pos = 900_000, at = 5), row("s1e1", done = true, at = 1)))!!, 1e-6f)
        assertNull(org.imdtv.iptvtv.ui.vod.progressFraction(item, listOf(row("s1e2", done = true, at = 5))))
        assertNull(org.imdtv.iptvtv.ui.vod.progressFraction(item, emptyList()))
    }
}
