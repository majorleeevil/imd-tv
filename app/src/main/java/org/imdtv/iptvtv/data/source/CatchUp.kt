package org.imdtv.iptvtv.data.source

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.imdtv.iptvtv.data.security.PASS_TOKEN
import org.imdtv.iptvtv.data.security.USER_TOKEN
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Xtream catch-up links for the programme [startMs]–[stopMs] of the channel whose saved live link is [liveUrl]
 * (`{host}/live/{user}/{pass}/{id}.{ext}`, login as placeholders): the `/timeshift/` path form first, then the
 * `streaming/timeshift.php` form some servers want. Times are in the server's [zone]. Empty when [liveUrl] isn't Xtream.
 */
fun catchUpUrls(liveUrl: String, startMs: Long, stopMs: Long, zone: TimeZone): List<String> {
    val url = liveUrl.toHttpUrlOrNull() ?: return emptyList()
    val seg = url.pathSegments
    val at = seg.lastIndexOf("live").takeIf { it >= 0 && it + 3 == seg.lastIndex } ?: return emptyList()
    val id = seg.last().substringBeforeLast('.')
    val base = url.newBuilder().apply { repeat(seg.size - at) { removePathSegment(at) } }.query(null).build()
    val minutes = ((stopMs - startMs + 59_999) / 60_000).coerceAtLeast(1).toString()
    val start = SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US).apply { timeZone = zone }.format(startMs)
    return listOf(
        base.newBuilder().addPathSegment("timeshift").addPathSegment(USER_TOKEN).addPathSegment(PASS_TOKEN)
            .addPathSegment(minutes).addPathSegment(start).addPathSegment("$id.ts").build().toString(),
        base.newBuilder().addPathSegment("streaming").addPathSegment("timeshift.php").addQueryParameter("username", USER_TOKEN)
            .addQueryParameter("password", PASS_TOKEN).addQueryParameter("stream", id).addQueryParameter("start", start)
            .addQueryParameter("duration", minutes).build().toString(),
    )
}
