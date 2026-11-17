package org.imdtv.iptvtv.work

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.imdtv.iptvtv.IptvApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.imdtv.iptvtv.data.epg.EpgImporter
import org.imdtv.iptvtv.data.epg.epgErrorStatus
import org.imdtv.iptvtv.data.epg.epgOkStatus
import org.imdtv.iptvtv.data.epg.selectSources
import org.imdtv.iptvtv.data.db.ORIGIN_EPGSHARE
import org.imdtv.iptvtv.data.settings.GuideKind
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/** Imports every EPG source of every provider, then rematches channels to guide entries. */
class EpgRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as IptvApp).container
        val settings = c.settings.settings.first()
        val mode = settings.guideOrder
        val active = c.providerRepository.activeProviderId.first()
        var attempted = 0
        var failed = 0
        for (p in c.db.providerDao().all()) {
            // epgshare01 files follow the active playlist's shown countries; other playlists keep theirs as they are.
            if (GuideKind.OPEN_SOURCE in mode && p.id == active) {
                runCatching { c.epgShare.sync(p.id) }.onFailure { e ->
                    if (e is CancellationException) throw e // a stopped run must not write "Job was cancelled" as a status
                    Log.w(EpgImporter.TAG, "epgshare01 list failed", e)
                    c.db.epgDao().sources(p.id).filter { it.origin == ORIGIN_EPGSHARE }.forEach { c.db.epgDao().setStatus(it.id, epgErrorStatus(e)) }
                }
            }
            val all = c.db.epgDao().sources(p.id)
            // A guide picked by hand stays fed even when its kind isn't in the order.
            val picked = c.db.epgDao().links(p.id).filter { it.manual }.mapTo(HashSet()) { it.sourceId }
            val sources = (selectSources(all, mode, c.providerRepository.playlistGuideUrls(p.id)) + all.filter { it.id in picked }).distinctBy { it.id }
            // Open-source mode keeps epgshare01 files for the active playlist only: another one would lose all its links.
            if (sources.isEmpty() && p.id != active) continue
            for (s in sources) {
                attempted++
                // One bad source must not block the others; keep its previous guide (importer is atomic).
                runCatching { c.epgImporter.import(s, settings.epgDaysToKeep, settings.epgPastDays) }
                    .onSuccess { c.db.epgDao().setStatus(s.id, epgOkStatus(System.currentTimeMillis(), it, ZoneId.systemDefault())) }
                    .onFailure {
                        if (it is CancellationException) throw it
                        failed++
                        Log.w(EpgImporter.TAG, "EPG import failed for ${EpgImporter.logUrl(s.url)}", it)
                        c.db.epgDao().setStatus(s.id, epgErrorStatus(it))
                    }
            }
            val m = c.epgMatcher.rematch(p.id, mode)
            Log.i(EpgImporter.TAG, "Provider ${p.id}: ${m.matched} matched (${m.borrowed} borrowed), ${m.unmatched} unmatched")
        }
        return if (shouldRetry(attempted, failed)) Result.retry() else Result.success()
    }

    companion object {
        /** A dead feed among good ones waits for the next period instead of re-importing every source. */
        fun shouldRetry(attempted: Int, failed: Int) = attempted > 0 && failed == attempted
        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** UPDATE keeps the period's timing but applies a changed interval. */
        fun schedule(context: Context, intervalHours: Int) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "epg-refresh", ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<EpgRefreshWorker>(intervalHours.toLong(), TimeUnit.HOURS).setConstraints(network).build(),
            )
        }

        /** True while a refresh runs (or waits for the network after Update now). */
        fun updating(context: Context): kotlinx.coroutines.flow.Flow<Boolean> =
            WorkManager.getInstance(context).getWorkInfosFlow(androidx.work.WorkQuery.fromUniqueWorkNames(listOf("epg-refresh-now", "epg-refresh")))
                // The periodic job is ENQUEUED between runs, so only a one-off counts while it waits.
                .map { infos -> infos.any { it.state == androidx.work.WorkInfo.State.RUNNING ||
                    (it.state == androidx.work.WorkInfo.State.ENQUEUED && it.periodicityInfo == null) } }

        /**
         * Runs a refresh now. Never cancels one already running: [automatic] triggers (app start, daily playlist sync)
         * leave a running or queued one alone; user changes queue one more run after it, so their change is included.
         */
        fun runNow(context: Context, automatic: Boolean = false) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "epg-refresh-now", if (automatic) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<EpgRefreshWorker>().setConstraints(network).build(),
            )
        }
    }
}
