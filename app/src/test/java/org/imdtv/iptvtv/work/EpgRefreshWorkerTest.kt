package org.imdtv.iptvtv.work

import androidx.work.ExistingWorkPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import androidx.work.WorkInfo
import org.junit.Test

class EpgRefreshWorkerTest {
    @Test
    fun retriesOnlyWhenEverySourceFailed() {
        assertTrue(EpgRefreshWorker.shouldRetry(attempted = 2, failed = 2))
        assertFalse(EpgRefreshWorker.shouldRetry(attempted = 3, failed = 1)) // one dead feed mustn't re-import the good ones
        assertFalse(EpgRefreshWorker.shouldRetry(attempted = 0, failed = 0))
    }

    @Test
    fun queuesAtMostOneRunBehindTheRunningOne() {
        val (R, B, E, D) = listOf(WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED, WorkInfo.State.ENQUEUED, WorkInfo.State.SUCCEEDED)
        val p = EpgRefreshWorker::policy
        assertEquals(ExistingWorkPolicy.APPEND_OR_REPLACE, p(emptyList(), false, false))
        assertEquals(ExistingWorkPolicy.APPEND_OR_REPLACE, p(listOf(R, D), false, false)) // the running one may have read old settings
        assertNull(p(listOf(R, B), false, false))                                         // the waiting one picks up the change
        assertEquals(ExistingWorkPolicy.KEEP, p(listOf(R), true, false))
        assertNull(p(listOf(R), true, true))
        assertEquals(ExistingWorkPolicy.REPLACE, p(listOf(R, B, B, B), false, false))     // an old backlog becomes one run
        assertEquals(ExistingWorkPolicy.REPLACE, p(listOf(E, B), true, true))
    }
}
