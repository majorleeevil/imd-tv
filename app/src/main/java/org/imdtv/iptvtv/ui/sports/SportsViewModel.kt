package org.imdtv.iptvtv.ui.sports

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.imdtv.iptvtv.IptvApp
import org.imdtv.iptvtv.data.db.ChannelEntity
import org.imdtv.iptvtv.data.repo.GuideProgramme
import org.imdtv.iptvtv.data.settings.AppSettings
import org.imdtv.iptvtv.data.sports.EspnClient
import org.imdtv.iptvtv.data.sports.Fetched
import org.imdtv.iptvtv.data.sports.Game
import org.imdtv.iptvtv.data.sports.GameState
import org.imdtv.iptvtv.data.sports.LEAGUES
import org.imdtv.iptvtv.data.sports.League
import org.imdtv.iptvtv.data.sports.Match
import org.imdtv.iptvtv.data.sports.Side
import org.imdtv.iptvtv.data.sports.Sport
import org.imdtv.iptvtv.data.sports.Table
import org.imdtv.iptvtv.data.sports.favouritesFirst
import org.imdtv.iptvtv.data.sports.teamNames
import org.imdtv.iptvtv.data.sports.gameMatch
import org.imdtv.iptvtv.data.sports.titleNamesGame
import java.time.LocalDate

enum class SportsPage { GAMES, TEAMS, LEAGUES, STANDINGS, SETTINGS, SPORT }

/** Where a game is on: the channel and the programme that names both teams. */
data class OnTv(val ch: ChannelEntity, val p: GuideProgramme) { fun liveAt(t: Long) = p.start <= t && t < p.stop }

/** The preview pane: a game, and the channel playing it (null = the score card). */
data class Pane(val game: Game, val tv: OnTv?, val looked: Boolean)

class SportsViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as IptvApp).container
    private val espn by lazy { EspnClient(c.http, c.json, java.io.File(app.cacheDir, "espn")) }
    val settings: StateFlow<AppSettings> = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    // Kept here, so Back from a match page returns to the same page, day and card.
    var page = MutableStateFlow(SportsPage.GAMES)
    val dayOffset = MutableStateFlow(0)
    var lastFocusedGame: String? = null
    /** Set when a match page opens: on return the screen puts focus back on [lastFocusedGame]. */
    var returning = false

    /** The day's games for the sport; null before the first answer. [failed]: no answer and no cache. */
    val games = MutableStateFlow<Fetched<List<Game>>?>(null)
    val failed = MutableStateFlow(false)
    val pane = MutableStateFlow<Pane?>(null)
    /** True once the user picked a preview: auto-play then leaves the pane alone until the day or sport changes. */
    private var paneManual = false
    private var shownKey: Pair<Sport, Int>? = null

    fun sport(s: AppSettings = settings.value): Sport =
        Sport.entries.find { it.key == s.sportsSport && it.key in s.sports } ?: Sport.entries.firstOrNull { it.key in s.sports } ?: Sport.FOOTBALL

    fun leagues(s: Sport) = LEAGUES.filter { it.sport == s }

    fun day(): LocalDate = LocalDate.now().plusDays(dayOffset.value.toLong())

    fun update(change: (AppSettings) -> AppSettings) { viewModelScope.launch { c.settings.update(change) } }

    /** Polls while the caller (the screen's LaunchedEffect) is alive: every 60 s with a live game, else at the next kick-off or 15 min. */
    suspend fun poll(sport: Sport, offset: Int, strip: List<String>) {
        // Same sport and day as last time (back from a match page or full screen): keep what's shown, refresh in place.
        if (shownKey != sport to offset) { games.value = null; failed.value = false; paneManual = false; pane.value = null }
        shownKey = sport to offset
        while (true) {
            val got = espn.day(leagues(sport), LocalDate.now().plusDays(offset.toLong()))
            if (got != null) games.value = got
            failed.value = got == null && games.value == null
            autoPane(strip)
            val list = games.value?.value.orEmpty()
            val heroes = heroes(list)
            checkLineups(heroes)
            val now = System.currentTimeMillis()
            val nextKickOff = list.filter { it.state == GameState.PRE && it.start > now }.minOfOrNull { it.start }
            delay(when {
                got == null -> 60_000L // offline: try again soon
                list.any { it.state == GameState.IN } -> 60_000L
                heroes.any { it.state == GameState.PRE && now >= it.start - 90 * 60_000 && lineupsOut.value[it.id] != true } -> minOf(5 * 60_000L, nextKickOff?.let { (it - now + 30_000).coerceAtLeast(60_000L) } ?: 5 * 60_000L)
                nextKickOff != null -> (nextKickOff - now + 30_000).coerceIn(60_000L, 15 * 60_000L)
                else -> 15 * 60_000L
            })
        }
    }

    /** Favourite teams' games: each gets the big card at the top of the favourites box. */
    fun heroes(list: List<Game>, s: AppSettings = settings.value): List<Game> {
        val keys = s.sportsTeams.map { org.imdtv.iptvtv.data.sports.favouriteTeamKey(it) }
        return favouritesFirst(list, s.sportsTeams, emptySet(), s.sportsTeamResults).first.filter { g -> keys.any(g::has) && (s.sportsTeamResults || g.state != GameState.POST) }
    }

    /** Favourite team's game first; the first favourite whose programme is on now plays, else the first favourite's score card. */
    private suspend fun autoPane(strip: List<String>) {
        val s = settings.value
        val favs = favouritesFirst(games.value?.value.orEmpty(), s.sportsTeams, s.sportsLeagues, s.sportsTeamResults).first
        val shown = pane.value
        if (paneManual && shown != null) {
            // Keep the picked game, with its fresh score.
            pane.value = shown.copy(game = games.value?.value?.find { it.id == shown.game.id } ?: shown.game)
            return
        }
        val now = System.currentTimeMillis()
        for (g in favs.filter { it.state != GameState.POST }) {
            val tv = onTv(g, strip)
            if (tv != null && tv.liveAt(now)) { pane.value = Pane(g, tv, true); return }
        }
        pane.value = favs.firstOrNull()?.let { Pane(it, null, false) }
    }

    /** Preview button: that game in the pane (playing if a channel shows it now). */
    fun preview(g: Game, strip: List<String>) {
        paneManual = true
        pane.value = Pane(g, null, false)
        viewModelScope.launch { pane.value = Pane(g, onTv(g, strip), true) }
    }

    private val tvCache = HashMap<String, Pair<Long, List<OnTv>>>()
    private val categoryNames = HashMap<Long, String?>()

    /**
     * EPG lookup: a programme on at kick-off (or now) naming both teams in its title or description, on a channel in a shown
     * category (the search index leaves hidden ones out). Title matches beat description ones; then a channel in the TV's
     * language (from its name or category, e.g. "FR| Canal+ Sport"); then the earliest start.
     * Cached 5 min per game: auto-play asks for every favourite on each live poll.
     */
    suspend fun onTv(g: Game, strip: List<String>): OnTv? = whereToWatch(g, strip).firstOrNull()

    /** Every channel showing [g], best first (the match page's Where to watch row). */
    suspend fun whereToWatch(g: Game, strip: List<String>): List<OnTv> {
        val now = System.currentTimeMillis()
        tvCache[g.id]?.takeIf { now - it.first < 5 * 60_000 }?.let { return it.second }
        val pid = c.providerRepository.activeProviderId.first() ?: return emptyList()
        val at = maxOf(now, g.start)
        // Per channel: the best programme and its match strength (0 = the game as the only fixture named).
        val hits = LinkedHashMap<Long, Pair<org.imdtv.iptvtv.data.search.GuideHit, Int>>()
        for (q in teamNames(g.home)) {
            // shortcut: diagnostics for the 2026-11-24 TV test (all of today's mentions, any time, and any error); remove once matching is signed off.
            runCatching { c.search.guide(pid, strip, q, now - 12 * 3_600_000L, cap = 300, inDesc = true) }
                .onFailure { android.util.Log.w("SportsHub", "\"$q\" search failed", it) }
                .onSuccess { all -> android.util.Log.i("SportsHub", "\"$q\" today: ${all.size}; " + all.take(10).joinToString(" / ") { "${it.ch.name} ${org.imdtv.iptvtv.ui.clockTime(it.p.start)}-${org.imdtv.iptvtv.ui.clockTime(it.p.stop)}: ${it.p.title} — ${it.p.desc.orEmpty().take(60)}" }) }
            // From kick-off (not now), so earlier mentions of a common word ("Villa") can't use up the cap before the game.
            runCatching { c.search.guide(pid, strip, q, at, cap = 300, inDesc = true) }.getOrDefault(emptyList())
                .filter { it.p.start <= at + 10 * 60_000 && it.p.stop > at }
                // For Send logs: what the guide has for this team at kick-off, so a miss can be explained from the user's own guide.
                .also { on -> android.util.Log.i("SportsHub", "${g.home.name} v ${g.away.name} \"$q\": ${on.size} on at kick-off; " + on.take(8).joinToString(" / ") { "${it.ch.name}: ${it.p.title} — ${it.p.desc.orEmpty().take(80)}" }) }
                .forEach { h ->
                    val m = gameMatch(h.p.title + " " + h.p.desc.orEmpty(), g.home, g.away) ?: return@forEach
                    if (hits[h.ch.id].let { it == null || m < it.second }) hits[h.ch.id] = h to m
                }
        }
        // Event channels with no guide, named for the game ("EPL 03: Wolves v Leeds"): on from kick-off for 3 hours.
        for (q in teamNames(g.home)) {
            runCatching { c.search.universal(pid, strip, q, cap = 100, vod = { emptyList() }).channels.items }.getOrDefault(emptyList())
                .filter { titleNamesGame(it.name, g.home, g.away) && !hits.containsKey(it.id) }
                .forEach { ch -> hits[ch.id] = org.imdtv.iptvtv.data.search.GuideHit(ch, GuideProgramme(ch.name, null, null, g.start, g.start + 3 * 3_600_000L)) to (gameMatch(ch.name, g.home, g.away) ?: 1) }
        }
        val lang = org.imdtv.iptvtv.data.catalogue.tvLanguage()
        val home = java.util.Locale.getDefault().country // "GB": a UK channel beats an Australian one, though both are English
        /** 0 = the TV's country, 1 = its language, 2 = elsewhere. */
        suspend fun place(ch: ChannelEntity): Int {
            val cat = ch.categoryId?.let { id -> categoryNames.getOrPut(id) { c.db.categoryDao().get(id)?.name } }
            val country = org.imdtv.iptvtv.data.catalogue.detectCountry(ch.name) ?: cat?.let { org.imdtv.iptvtv.data.catalogue.detectCountry(it) }
            return when { country != null && country == home -> 0; org.imdtv.iptvtv.data.catalogue.languageOf(country) == lang -> 1; else -> 2 }
        }
        // The game as the only fixture named; then on now (not a show starting in 10 min); then the TV's country, language; then start.
        val found = hits.values.map { (h, m) -> Triple(h, m, place(h.ch)) }
            .sortedWith(compareBy({ it.second }, { it.first.p.start > now }, { it.third }, { it.first.p.start }))
            .also { r -> android.util.Log.i("SportsHub", "${g.home.name} v ${g.away.name}: " + r.joinToString { "${it.first.ch.name} (match ${it.second}, place ${it.third})" }.ifEmpty { "no channel" }) }
            .map { OnTv(it.first.ch, it.first.p) }
        tvCache[g.id] = now to found
        return found
    }

    /** The favourite team's card focused: its game in the preview (playing if on now, else its score card). */
    fun focusHero(g: Game, strip: List<String>) {
        paneManual = false
        if (pane.value?.game?.id == g.id) return
        pane.value = Pane(g, null, false)
        viewModelScope.launch { onTv(g, strip).let { if (pane.value?.game?.id == g.id) pane.value = Pane(g, it, true) } }
    }

    /** A card held in focus: its game takes the preview if a channel shows it now (else the preview stays as it is). */
    suspend fun focusPreview(g: Game, strip: List<String>) {
        if (pane.value?.game?.id == g.id) return
        val tv = onTv(g, strip) ?: return
        if (tv.liveAt(System.currentTimeMillis())) { paneManual = true; pane.value = Pane(g, tv, true) }
    }

    /** Favourite team's game: whether ESPN has its lineups yet (checked from 90 min before kick-off). */
    val lineupsOut = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    private suspend fun checkLineups(heroes: List<Game>) {
        val now = System.currentTimeMillis()
        for (g in heroes.filter { it.state == GameState.PRE && now >= it.start - 90 * 60_000 && lineupsOut.value[it.id] != true }) {
            val out = runCatching { espn.match(g).value.teams.any { it.starters.isNotEmpty() } }.getOrNull() ?: continue
            lineupsOut.value = lineupsOut.value + (g.id to out)
        }
    }

    // --- Match page ---
    val match = MutableStateFlow<Fetched<Match>?>(null)
    val matchFailed = MutableStateFlow(false)

    suspend fun pollMatch(g: Game) {
        match.value = null; matchFailed.value = false
        while (true) {
            val m = runCatching { espn.match(g) }.getOrNull()
            if (m != null) match.value = m
            matchFailed.value = m == null && match.value == null
            val shown = m?.value?.game ?: g
            delay(when {
                shown.state == GameState.IN -> 60_000L
                shown.state == GameState.PRE -> (shown.start - System.currentTimeMillis() + 30_000).coerceIn(60_000L, 15 * 60_000L) // wake at kick-off
                else -> 15 * 60_000L
            })
        }
    }

    // --- Standings and team picker: fetched when their page opens. ---
    suspend fun standings(l: League): Fetched<List<Table>>? = runCatching { espn.standings(l) }.getOrNull()
    suspend fun teams(l: League): List<Side>? = runCatching { espn.teams(l) }.getOrNull()
}
