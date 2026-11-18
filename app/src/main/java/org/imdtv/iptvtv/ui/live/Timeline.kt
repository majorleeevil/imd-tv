package org.imdtv.iptvtv.ui.live

/** Offset from the window start and visible length, both in whole minutes. */
data class Span(val offsetMin: Int, val lengthMin: Int)

object Timeline {
    /** Row to focus when the guide (re)appears: the channel last focused, if it's in this list. */
    fun rowFor(channelIds: List<Long>, focusedId: Long?): Int = channelIds.indexOf(focusedId).coerceAtLeast(0)

    private const val MIN = 60_000L
    private const val HALF_HOUR = 30 * MIN

    fun windowStart(now: Long) = now - now % HALF_HOUR

    fun span(start: Long, stop: Long, from: Long, to: Long): Span? {
        val s = maxOf(start, from)
        val e = minOf(stop, to)
        if (e <= s) return null
        return Span(((s - from) / MIN).toInt(), ((e - s) / MIN).toInt())
    }

    /**
     * The guide's highlighted programme after Right ([dir] 1) or Left (-1) on a row: the start of the next/previous
     * programme, null = the one on now. Returns [at] itself when it can't move (Left on now). A row without guide steps
     * by half-hours.
     */
    fun stepCursor(progs: List<org.imdtv.iptvtv.data.repo.GuideProgramme>, at: Long?, now: Long, dir: Int): Long? {
        val t = at?.takeIf { it > now } ?: now
        val cur = progs.firstOrNull { it.start <= t && t < it.stop }
        if (dir > 0) return cur?.stop ?: progs.firstOrNull { it.start > t }?.start ?: (windowStart(t) + HALF_HOUR)
        if (at == null || t == now) return at
        val from = cur?.start ?: t
        val to = progs.lastOrNull { it.stop <= from }?.start ?: (windowStart(from - 1))
        return to.takeIf { it > now }
    }
}

enum class OkAction { PREVIEW, FULL_SCREEN }

/** Spec §4.1: without autoplay, OK previews a channel and a second OK on the previewing one goes full screen. */
fun okAction(autoplayOnScroll: Boolean, previewingId: Long?, channelId: Long): OkAction =
    if (autoplayOnScroll || previewingId == channelId) OkAction.FULL_SCREEN else OkAction.PREVIEW

/** Channel [delta] places from [currentId] in [ids], wrapping at the ends; a channel not in the list zaps to the first. */
fun zap(ids: List<Long>, currentId: Long, delta: Int): Long? {
    if (ids.isEmpty()) return null
    val i = ids.indexOf(currentId)
    if (i < 0) return ids.first()
    return ids[Math.floorMod(i + delta, ids.size)]
}

/** Zapping through Recently watched must not record recents: that would reorder the list being zapped and Down would bounce. */
fun zapRecordsRecent(entry: org.imdtv.iptvtv.data.repo.RailEntry?): Boolean = entry != org.imdtv.iptvtv.data.repo.RailEntry.Recent

/** Channels watched, oldest first (newest 20), and the one being viewed while stepping back through them. */
class ChannelHistory<T>(private val max: Int = 20, private val key: (T) -> Any) {
    private val list = ArrayList<T>()
    private var at = -1

    /** [ch] started playing. The one stepped to (or already newest) changes nothing; anything else moves to the newest end. */
    fun played(ch: T) {
        if (at in list.indices && key(list[at]) == key(ch)) return
        list.removeAll { key(it) == key(ch) }
        list.add(ch)
        if (list.size > max) list.removeAt(0)
        at = list.lastIndex
    }

    /** [dir] 1 = one older, -1 = one newer; null at either end. */
    fun step(dir: Int): T? {
        val to = at - dir
        if (to !in list.indices) return null
        at = to
        return list[to]
    }

    fun clear() { list.clear(); at = -1 }
}
