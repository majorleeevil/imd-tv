package org.imdtv.iptvtv.data.source

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val PLAIN = Regex("""\$?\{(utc|start|utcend|end|lutc|now|timestamp|duration|offset|Y|m|d|H|M|S)\}""")
private val FORMATTED = Regex("""\{(utc|start|end|utcend):([^}]*)\}""")

/**
 * Catch-up link for an M3U channel. [packed] is "type|template" (see ChannelEntity.catchupSource); null/unknown type = shift.
 * Returns null for type xc, whose link the Xtream builder makes.
 */
fun m3uCatchUpUrl(liveUrl: String, packed: String?, startMs: Long, stopMs: Long, nowMs: Long): String? {
    val type = packed?.substringBefore('|')?.lowercase()?.ifEmpty { null }
    val tpl = packed?.substringAfter('|', "").orEmpty()
    fun fill(s: String) = fillPlaceholders(s, startMs, stopMs, nowMs)
    fun join(base: String, add: String) = if (add.startsWith("?") || add.startsWith("&")) base + (if ('?' in base) "&" else "?") + add.substring(1) else base + add
    val utc = startMs / 1000
    return when (type) {
        "xc" -> null
        "default" -> fill(tpl)
        "append" -> join(liveUrl, fill(tpl))
        "flussonic", "flussonic-hls", "fs" -> {
            val path = liveUrl.substringBefore('?')
            val seg = path.substringAfterLast('/')
            if (seg.endsWith(".m3u8", true)) {
                // index.m3u8 → index-{utc}-{duration}.m3u8; the same for mono.m3u8, video.m3u8, …
                swapSegment(liveUrl, "${seg.substringBeforeLast('.')}-$utc-${(stopMs - startMs) / 1000}.m3u8")
            } else swapSegment(liveUrl, "timeshift_abs-$utc.ts")
        }
        "flussonic-ts" -> swapSegment(liveUrl, "timeshift_abs-$utc.ts")
        else -> join(liveUrl, fill("?utc={utc}&lutc={lutc}")) // shift
    }
}

/** Replaces the last path segment, keeping the query string (tokens). */
private fun swapSegment(url: String, seg: String): String {
    val path = url.substringBefore('?')
    val query = url.substring(path.length)
    return path.substringBeforeLast('/') + "/" + seg + query
}

private fun fillPlaceholders(t: String, startMs: Long, stopMs: Long, nowMs: Long): String {
    val start = startMs / 1000; val stop = stopMs / 1000; val now = nowMs / 1000
    fun parts(ms: Long, ch: Char) = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern(when (ch) {
        'Y' -> "yyyy"; 'm' -> "MM"; 'd' -> "dd"; 'H' -> "HH"; 'M' -> "mm"; else -> "ss"
    }))
    val formatted = FORMATTED.replace(t) { m ->
        val ms = if (m.groupValues[1] == "end" || m.groupValues[1] == "utcend") stopMs else startMs
        m.groupValues[2].map { if (it in "YmdHMS") parts(ms, it) else it.toString() }.joinToString("")
    }
    return PLAIN.replace(formatted) { m ->
        when (m.groupValues[1]) {
            "utc", "start" -> start.toString()
            "utcend", "end" -> stop.toString()
            "lutc", "now", "timestamp" -> now.toString()
            "duration" -> (stop - start).toString()
            "offset" -> (now - start).toString()
            else -> parts(startMs, m.groupValues[1][0])
        }
    }
}
