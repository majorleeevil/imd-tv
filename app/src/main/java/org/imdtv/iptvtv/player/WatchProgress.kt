package org.imdtv.iptvtv.player

import androidx.media3.common.C
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.imdtv.iptvtv.data.db.ProgressDao
import org.imdtv.iptvtv.data.db.ProgressEntity
import org.imdtv.iptvtv.data.model.Episode
import org.imdtv.iptvtv.data.model.Kind

/** Under this, a start isn't worth remembering (and doesn't overwrite what was saved before). */
const val MIN_PROGRESS_MS = 30_000L

/** Played to the end: 95 % in, or under 2 minutes (the credits) left. */
fun isWatched(positionMs: Long, durationMs: Long) =
    durationMs > 0 && (positionMs >= durationMs * 95 / 100 || durationMs - positionMs < 120_000)

/** Where a movie with [row] starts: where it was left, or the start if never played or watched. */
fun resumeAt(row: ProgressEntity?): Long = row?.takeIf { !it.completed }?.positionMs ?: 0

/** The episode a series' main button plays: [resume] true = carry on from [startMs]. */
data class NextEpisode(val episode: Episode, val startMs: Long, val resume: Boolean)

/**
 * The last episode in progress, else the one after the last watched (back to the first after the finale), else the first.
 * [rows] are this series' progress rows; rows of episodes no longer listed are ignored.
 */
fun nextEpisode(episodes: List<Episode>, rows: List<ProgressEntity>): NextEpisode? {
    val eps = episodes.sortedWith(compareBy({ it.season }, { it.number }))
    val first = eps.firstOrNull() ?: return null
    val latest = rows.filter { r -> eps.any { it.remoteId == r.episode } }.maxByOrNull { it.updatedAt }
        ?: return NextEpisode(first, 0, false)
    val i = eps.indexOfFirst { it.remoteId == latest.episode }
    return if (!latest.completed) NextEpisode(eps[i], latest.positionMs, true)
    else NextEpisode(eps.getOrNull(i + 1) ?: first, 0, false)
}

/**
 * Remembers where the movie or episode playing now is: whenever the player stops (Back, Home, a tab change) and every
 * 30 s, so a crash or power cut loses little. Writes run on [scope] (the app's), so they finish after the screen is gone.
 */
class WatchProgress(private val player: PlayerManager, private val dao: ProgressDao, private val scope: CoroutineScope,
                    /** Also tells a media server (after the row is saved). */ private val report: suspend (ProgressEntity) -> Unit = {}) {
    private data class Watching(val providerId: Long, val kind: Kind, val itemId: Long, val episode: String, val url: String)
    private var watching: Watching? = null
    private var ticker: Job? = null

    init { player.beforeStop = ::save }

    /** Call right after starting [url] (main thread). [episode]: the episode's remoteId, "" for a movie. */
    fun start(providerId: Long, kind: Kind, itemId: Long, episode: String, url: String) {
        watching = Watching(providerId, kind, itemId, episode, url)
        ticker?.cancel()
        ticker = scope.launch(Dispatchers.Main) { while (true) { delay(30_000); save() } }
    }

    /** Main thread (reads the player). */
    fun save() {
        val w = watching ?: return
        val now = player.playingUrl.value ?: return // stopped or in the background: nothing playing to read
        if (now != w.url) { watching = null; ticker?.cancel(); return } // something else plays now
        val p = player.player
        val pos = p.currentPosition
        val dur = p.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0
        val done = p.playbackState == Player.STATE_ENDED || isWatched(pos, dur)
        if (!done && pos < MIN_PROGRESS_MS) return
        val row = ProgressEntity(w.providerId, w.kind, w.itemId, if (done) 0 else pos, dur, System.currentTimeMillis(), done, w.episode)
        scope.launch { dao.upsert(row); report(row) }
    }
}
