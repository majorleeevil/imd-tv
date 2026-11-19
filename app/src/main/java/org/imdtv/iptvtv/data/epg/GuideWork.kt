package org.imdtv.iptvtv.data.epg

import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

/**
 * Guide imports and matching (minutes of parsing and regexes on a TV box) run here, below normal priority, so a playing
 * stream always gets the CPU first: at full priority on every core they made playback stutter on slower boxes.
 * Two threads, so a rematch after a playlist edit isn't stuck behind a running import.
 */
val GuideWork = Executors.newFixedThreadPool(2) { r ->
    // Java priority 4 is Android's background nice level (10).
    Thread(r, "guide-work").apply { priority = 4; isDaemon = true }
}.asCoroutineDispatcher()
