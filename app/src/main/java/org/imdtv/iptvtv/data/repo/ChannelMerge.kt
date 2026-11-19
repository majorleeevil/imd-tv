package org.imdtv.iptvtv.data.repo

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import org.imdtv.iptvtv.data.catalogue.displayName
import org.imdtv.iptvtv.data.catalogue.isHeaderChannel
import org.imdtv.iptvtv.data.catalogue.parseStripList
import org.imdtv.iptvtv.data.settings.MergeBy
import org.imdtv.iptvtv.data.db.AppDatabase
import org.imdtv.iptvtv.data.db.ChannelDao
import org.imdtv.iptvtv.data.db.ChannelEntity
import org.imdtv.iptvtv.data.security.Login
import org.imdtv.iptvtv.data.security.withLogin
import java.util.concurrent.TimeUnit

/** The app-wide merge settings a run needs: Merge channels on, and Merge across categories. */
data class MergeOptions(val on: Boolean, val across: Boolean, val byResolution: Boolean = true)

// Channel One = Channel 1. shortcut: English number words only; add other languages' words if their playlists need it.
private val NUMBER_WORDS = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
    "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen", "twenty")
    .withIndex().associate { (i, w) -> w to i.toString() }

// Superscript tags (BBC One ᴿᴬᵂ, Sky Atlantic ᴴᴰ, ⁴ᴷ) get a space before them and become plain letters (RAW, HD, 4K).
private val SUPER = Regex("""[\u00B2\u00B3\u00B9\u1D2C-\u1D6A\u1D9B-\u1DBF\u2070-\u209F\u2C7D]+""")
private fun plain(name: String) = java.text.Normalizer.normalize(SUPER.replace(name) { " " + it.value }, java.text.Normalizer.Form.NFKC)

// Quality tags, longest first; whole words only (BBC One HD, BT Sport 1 4K, ITV1 HEVC, BBC One ᴿᴬᵂ once plain).
private val TAG = Regex(
    """(?<![\p{L}\p{N}])(2160p|4k|uhd|1080[pi]?|fhd|720p?|hd|576[pi]?|480p?|sd|hevc|[hx]\.?26[45]|hdr|raw|[56]0\s?fps)(?![\p{L}\p{N}])""",
    RegexOption.IGNORE_CASE,
)

/**
 * What duplicates share: the name after the playlist's Channel name clean-up [strip] (prefixes and suffixes), without
 * quality tags, number words as digits, letters and digits only, lower case. Empty = never merged.
 */
fun mergeKey(name: String, strip: List<String> = emptyList()): String =
    TAG.replace(plain(displayName(name, null, strip)), " ").lowercase().split(Regex("""[^\p{L}\p{N}]+"""))
        .filter { it.isNotEmpty() }.joinToString(" ") { NUMBER_WORDS[it] ?: it }

/** The shown name of a merged channel: [name] without its quality tags (or [name] itself if nothing would be left). */
fun baseName(name: String): String = TAG.replace(plain(name), "")
    .replace(Regex("""\(\s*\)|\[\s*\]"""), "").replace(Regex("""\s+"""), " ")
    .trim().trim('-', '|', ':', '_', '/', ' ').ifBlank { name }

/** 4 = 4K / HDR, 3 = 1080 / HEVC / RAW, 2 = HD / 720, 1 = no tag, 0 = SD; the best tag in the name counts. */
fun qualityRank(name: String): Int = TAG.findAll(plain(name)).maxOfOrNull {
    when (it.value.lowercase().replace(".", "")) {
        "2160p", "4k", "uhd", "hdr" -> 4
        "1080", "1080p", "1080i", "fhd", "hevc", "h265", "x265", "raw" -> 3
        "720", "720p", "hd" -> 2
        "576", "576p", "576i", "480", "480p", "sd" -> 0
        else -> 1
    }
} ?: 1

/** Guide order (as the channel queries sort): placed first, then by number, then by name. */
private val GUIDE_ORDER = compareBy<ChannelEntity>({ it.sortOrder == null }, { it.sortOrder }, { (it.customNumber ?: it.number) == null },
    { it.customNumber ?: it.number }, { it.customName ?: it.name })

object ChannelMerge {
    /**
     * "Merge duplicate channels" for one playlist: channels of one category (or any, with Merge across) whose names match bar quality tags become one,
     * the first in guide order keeping its place. Hidden channels, header rows and merges made by hand are left alone.
     * Off, it only undoes its own merges. Writes only what changed.
     */
    // Off the main thread: a big playlist's regex grouping took over 5 s on a Chromecast (ANR when the switch was flipped).
    // One run at a time: Off then quickly On let the On run read rows before Off's clear landed, leaving nothing merged.
    private val runLock = kotlinx.coroutines.sync.Mutex()
    private val runs = kotlinx.coroutines.flow.MutableStateFlow(0)
    /** True while a merge run is waiting or working (the spinner on Settings › Channels › Merge channels). */
    val running: kotlinx.coroutines.flow.Flow<Boolean> = runs.map { it > 0 }.distinctUntilChanged()

    suspend fun applyAuto(db: AppDatabase, providerId: Long, opts: MergeOptions) {
        runs.update { it + 1 }
        try { runLock.withLock { withContext(Dispatchers.Default) { applyAutoNow(db, providerId, opts) } } } finally { runs.update { it - 1 } }
    }

    private suspend fun applyAutoNow(db: AppDatabase, providerId: Long, opts: MergeOptions) {
        val prov = db.providerDao().get(providerId) ?: return
        val dao = db.channelDao()
        // Off (most playlists, every sync): one UPDATE instead of loading a 46k-channel playlist.
        if (!opts.on || !prov.mergeOn) { db.withTransaction { if (dao.clearAutoMerges(providerId) > 0) dao.refreshMergeHeads(providerId) }; return }
        val all = dao.byProvider(providerId)
        val want = HashMap<Long, Long>() // member id → head id
        val strip = parseStripList(prov.nameStrip)
        all.asSequence()
            .filter { !it.hidden && (it.mergedInto == null || it.mergedAuto) && !isHeaderChannel(it.name) }
            .groupBy { (if (opts.across) null else it.categoryId) to mergeKey(it.name, strip) }
            .filter { (k, g) -> k.second.isNotEmpty() && g.size > 1 }
            // The kept row: by Resolution the best-tagged (4K over HD), ties and Latency by guide order.
            .values.forEach { g -> val s = g.sortedWith(if (opts.byResolution) compareByDescending<ChannelEntity> { qualityRank(it.name) }.then(GUIDE_ORDER) else GUIDE_ORDER)
                s.drop(1).forEach { want[it.id] = s[0].id } }
        val have = all.filter { it.mergedAuto }.mapNotNull { c -> c.mergedInto?.let { c.id to it } }.toMap()
        if (want == have) return
        db.withTransaction {
            (have.keys - want.keys).chunked(900).forEach { dao.setMergedInto(it, null, false) }
            want.entries.filter { have[it.key] != it.value }.groupBy({ it.value }, { it.key })
                .forEach { (head, ids) -> ids.chunked(900).forEach { dao.setMergedInto(it, head, true) } }
            dao.refreshMergeHeads(providerId)
        }
    }

    /** Long OK › Merge channel: [src] becomes a source of [into] (same playlist only). */
    suspend fun merge(db: AppDatabase, src: ChannelEntity, into: ChannelEntity) {
        if (src.id == into.id || src.providerId != into.providerId) return
        db.withTransaction { db.channelDao().setMergedInto(listOf(src.id), into.id, false); db.channelDao().refreshMergeHeads(src.providerId) }
        cache.remove(into.id)
    }

    /** Long OK › Unmerge: every channel merged into [head] shows on its own again. */
    suspend fun unmerge(db: AppDatabase, head: ChannelEntity) {
        val dao = db.channelDao()
        db.withTransaction { dao.setMergedInto(dao.mergedInto(head.id).map { it.id }, null, false); dao.refreshMergeHeads(head.providerId) }
        cache.remove(head.id)
    }

    /** View merged channels: what plays through [head], [head] itself left out. */
    suspend fun members(dao: ChannelDao, head: ChannelEntity): List<ChannelEntity> = sources(dao, head).drop(1)

    /** View merged channels › Unmerge: [member] shows on its own again; the others stay merged. */
    suspend fun unmergeOne(db: AppDatabase, member: ChannelEntity) {
        val into = member.mergedInto ?: return
        db.withTransaction { db.channelDao().setMergedInto(listOf(member.id), null, false); db.channelDao().refreshMergeHeads(member.providerId) }
        cache.remove(into)
    }

    /** [ch] and every visible channel merged into it (and into those), [ch] first. */
    suspend fun sources(dao: ChannelDao, ch: ChannelEntity): List<ChannelEntity> {
        if (!ch.mergeHead) return listOf(ch)
        val out = mutableListOf(ch); val seen = hashSetOf(ch.id)
        var i = 0
        while (i < out.size) { dao.mergedInto(out[i++].id).forEach { if (!it.hidden && seen.add(it.id)) out += it } }
        return out
    }

    // head id → (picked at, source id): a latency pick holds for a while so zapping back doesn't probe again.
    private val cache = java.util.concurrent.ConcurrentHashMap<Long, Pair<Long, Long>>()
    private const val CACHE_MS = 30 * 60_000L

    /** The source to play for [ch]: itself unless other channels are merged into it. */
    suspend fun pick(db: AppDatabase, http: OkHttpClient, ch: ChannelEntity, by: MergeBy, userAgent: String?, login: Login?): ChannelEntity {
        val all = sources(db.channelDao(), ch)
        if (all.size == 1) return ch
        val best = all.maxBy { qualityRank(it.name) } // first of the best: the head on a tie
        if (by == MergeBy.RESOLUTION) return best
        val now = System.currentTimeMillis()
        cache[ch.id]?.let { (at, id) -> if (now - at < CACHE_MS) all.firstOrNull { it.id == id }?.let { return it } }
        val probe = http.newBuilder().callTimeout(3, TimeUnit.SECONDS).build()
        // shortcut: one probe at a time, so a playlist allowing one connection isn't refused for opening several; a source
        // that fails or times out loses. All failing falls back to resolution.
        val timed = all.map { s -> currentCoroutineContext().ensureActive(); s to latency(probe, withLogin(s.streamUrl, login), userAgent) }
        val fastest = timed.filter { it.second != null }.minByOrNull { it.second!! }?.first ?: best
        cache[ch.id] = now to fastest.id
        return fastest
    }

    /** Time to the first byte of [url], or null if it fails. */
    private suspend fun latency(http: OkHttpClient, url: String, userAgent: String?): Long? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(url).apply { userAgent?.let { header("User-Agent", it) } }.build()
            val t0 = System.nanoTime()
            http.newCall(req).execute().use { r -> if (!r.isSuccessful || (r.body?.byteStream()?.read() ?: -1) < 0) null else (System.nanoTime() - t0) / 1_000_000 }
        }.getOrNull()
    }
}
