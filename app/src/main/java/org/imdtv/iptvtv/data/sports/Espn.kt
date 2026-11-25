package org.imdtv.iptvtv.data.sports

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.text.Normalizer

/** SportsHub (#26): ESPN's public site API (undocumented, no key). Every parser drops what it can't read instead of throwing. */
enum class Sport(val key: String) { FOOTBALL("soccer"), AMERICAN_FOOTBALL("football"), BASKETBALL("basketball"), HOCKEY("hockey"), RUGBY("rugby"), RUGBY_LEAGUE("rugby-league"), CRICKET("cricket") }

/**
 * [slug] is ESPN's league path, e.g. "eng.1"; [id] = "soccer/eng.1", the form settings keep.
 * [region] groups Followed competitions ("" = no heading); [cup] puts it in the cup column; [table] = ESPN has standings for it.
 */
data class League(val sport: Sport, val slug: String, val name: String, val region: Region = Region.NONE, val cup: Boolean = false, val table: Boolean = !cup) {
    val id get() = "${sport.key}/$slug"
}

enum class Region { NONE, ENGLAND, SCOTLAND, SPAIN, GERMANY, ITALY, FRANCE, EUROPE, INTERNATIONAL, AUSTRALIA }

private fun soccer(slug: String, name: String, r: Region, cup: Boolean = false, table: Boolean = !cup) = League(Sport.FOOTBALL, slug, name, r, cup, table)

val LEAGUES = listOf(
    soccer("eng.1", "Premier League", Region.ENGLAND), soccer("eng.2", "Championship", Region.ENGLAND),
    soccer("eng.fa", "FA Cup", Region.ENGLAND, cup = true), soccer("eng.league_cup", "League Cup", Region.ENGLAND, cup = true),
    soccer("eng.trophy", "EFL Trophy", Region.ENGLAND, cup = true), soccer("eng.charity", "Community Shield", Region.ENGLAND, cup = true),
    soccer("sco.1", "Scottish Premiership", Region.SCOTLAND),
    soccer("sco.tennents", "Scottish Cup", Region.SCOTLAND, cup = true), soccer("sco.cis", "Scottish League Cup", Region.SCOTLAND, cup = true),
    soccer("esp.1", "LaLiga", Region.SPAIN), soccer("esp.copa_del_rey", "Copa del Rey", Region.SPAIN, cup = true), soccer("esp.super_cup", "Supercopa", Region.SPAIN, cup = true),
    soccer("ger.1", "Bundesliga", Region.GERMANY), soccer("ger.dfb_pokal", "DFB-Pokal", Region.GERMANY, cup = true), soccer("ger.super_cup", "Supercup", Region.GERMANY, cup = true),
    soccer("ita.1", "Serie A", Region.ITALY), soccer("ita.coppa_italia", "Coppa Italia", Region.ITALY, cup = true), soccer("ita.super_cup", "Supercoppa", Region.ITALY, cup = true),
    soccer("fra.1", "Ligue 1", Region.FRANCE), soccer("fra.coupe_de_france", "Coupe de France", Region.FRANCE, cup = true),
    soccer("fra.super_cup", "Trophée des Champions", Region.FRANCE, cup = true),
    soccer("uefa.champions", "Champions League", Region.EUROPE, cup = true, table = true), soccer("uefa.europa", "Europa League", Region.EUROPE, cup = true, table = true),
    soccer("uefa.europa.conf", "Conference League", Region.EUROPE, cup = true, table = true),
    League(Sport.AMERICAN_FOOTBALL, "nfl", "NFL"), League(Sport.BASKETBALL, "nba", "NBA"), League(Sport.HOCKEY, "nhl", "NHL"),
    League(Sport.RUGBY, "267979", "Premiership Rugby", Region.ENGLAND), League(Sport.RUGBY, "270559", "Top 14", Region.FRANCE),
    League(Sport.RUGBY, "270557", "United Rugby Championship", Region.EUROPE),
    League(Sport.RUGBY, "271937", "Champions Cup", Region.EUROPE, cup = true), League(Sport.RUGBY, "272073", "Challenge Cup", Region.EUROPE, cup = true),
    League(Sport.RUGBY, "180659", "Six Nations", Region.INTERNATIONAL, cup = true, table = true), League(Sport.RUGBY, "244293", "The Rugby Championship", Region.INTERNATIONAL, cup = true, table = true),
    League(Sport.RUGBY, "164205", "Rugby World Cup", Region.INTERNATIONAL, cup = true, table = true),
    League(Sport.RUGBY_LEAGUE, "3", "NRL", Region.AUSTRALIA),
    // Cricket has no league list: one header feed of the current series.
    League(Sport.CRICKET, "header", "Cricket"),
)

fun leagueById(id: String) = LEAGUES.find { it.id == id }

enum class GameState { PRE, IN, POST }

/** [color] / [altColor]: ESPN's home and away colours, "660e36" (lineup tags, see [kitColours]). */
data class Side(val id: String, val name: String, val short: String, val abbr: String, val logo: String?, val score: String?,
                val color: String? = null, val altColor: String? = null)

data class Game(
    val id: String, val league: League, val leagueName: String, val leagueLogo: String?, val start: Long, val state: GameState,
    /** "FT", "63'", "Q3 4:12"; for PRE the screen shows the local kick-off time instead. */
    val detail: String, val home: Side, val away: Side, val broadcasts: List<String>,
) { fun has(teamKey: String) = teamKey == teamKey(league.sport, home.id) || teamKey == teamKey(league.sport, away.id) }

/** Favourite teams are kept as "soccer:362:Aston Villa" (ESPN team ids are per sport). */
fun teamKey(sport: Sport, id: String) = "${sport.key}:$id"
fun favouriteKey(sport: Sport, id: String, name: String) = "${teamKey(sport, id)}:$name"
fun favouriteName(fav: String) = fav.split(':', limit = 3).getOrNull(2).orEmpty()
fun favouriteTeamKey(fav: String) = fav.split(':', limit = 3).take(2).joinToString(":")

data class Player(val jersey: String, val name: String, val position: String, val starter: Boolean, val place: Int, val subbedIn: Boolean, val subbedOut: Boolean)
data class FormGame(val result: String, val score: String, val opponent: String, val atVs: String, val league: String, val date: Long)
data class TeamSheet(val side: Side, val formation: String?, val starters: List<Player>, val subs: List<Player>, val form: List<FormGame>)
data class StatLine(val label: String, val home: String, val away: String)
data class Match(val game: Game?, val teams: List<TeamSheet>, val stats: List<StatLine>, val venue: String?, val events: List<KeyEvent> = emptyList())

enum class EventKind { KICKOFF, HALFTIME, SECOND_HALF, FULLTIME, GOAL, YELLOW, RED, SUB }
/** One line of the match page's events panel; [who] = scorer / booked player, or (in, out) for a substitution. */
data class KeyEvent(val kind: EventKind, val minute: String, val teamId: String?, val who: List<String>)

/** ESPN `keyEvents` (football), newest first; delays and other noise left out. */
fun parseKeyEvents(root: JsonElement): List<KeyEvent> = root.a("keyEvents").mapNotNull { e ->
    val type = e.o("type").s("type").orEmpty()
    val kind = when {
        e.s("scoringPlay") == "true" || type.startsWith("goal") || type == "own-goal" -> EventKind.GOAL
        type == "kickoff" -> EventKind.KICKOFF
        type == "halftime" -> EventKind.HALFTIME
        type == "start-2nd-half" -> EventKind.SECOND_HALF
        type == "end-regular-time" || type == "full-time" -> EventKind.FULLTIME
        type == "yellow-card" -> EventKind.YELLOW
        type == "red-card" || type == "second-yellow-card" -> EventKind.RED
        type == "substitution" -> EventKind.SUB
        else -> return@mapNotNull null
    }
    KeyEvent(kind, e.o("clock").s("displayValue").orEmpty(), e.o("team").s("id"),
        e.a("participants").mapNotNull { it.o("athlete").s("displayName") })
}.reversed()

data class TableRow(val rank: Int, val team: Side, val played: String, val won: String, val drawn: String, val lost: String, val gd: String, val points: String)
data class Table(val name: String, val rows: List<TableRow>)

/** A cached or fresh answer; [stale] = the network failed and this is the last good copy from [updatedAt]. */
data class Fetched<T>(val value: T, val updatedAt: Long, val stale: Boolean)

// --- JSON helpers: anything of the wrong shape reads as null/empty. ---
private fun JsonElement?.o(key: String) = (this as? JsonObject)?.get(key)
private fun JsonElement?.s(key: String) = (o(key) as? JsonPrimitive)?.contentOrNull
private fun JsonElement?.a(key: String) = (o(key) as? JsonArray).orEmpty()
private fun JsonElement?.first(key: String) = a(key).firstOrNull()

private fun time(iso: String?): Long? = iso?.let { runCatching { Instant.parse(it.replace(Regex("T(\\d\\d:\\d\\d)Z$"), "T$1:00Z")).toEpochMilli() }.getOrNull() }

private fun hex(s: String?) = s?.takeIf { it.length == 6 && it.toLongOrNull(16) != null }

private fun rgb(h: String) = h.toLong(16).let { Triple(it shr 16 and 255, it shr 8 and 255, it and 255) }
/** Near enough the same colour that the kits would clash (white vs off-white). */
fun clash(a: String?, b: String?): Boolean {
    if (a == null || b == null) return false
    val (r1, g1, b1) = rgb(a); val (r2, g2, b2) = rgb(b)
    return (r1 - r2) * (r1 - r2) + (g1 - g2) * (g1 - g2) + (b1 - b2) * (b1 - b2) < 60 * 60
}

/** Kit colours for the lineups: home in its home colour, away in its away colour, or its home colour if that would clash. */
fun kitColours(home: Side, away: Side): Pair<String?, String?> =
    home.color to (away.altColor?.takeIf { !clash(it, home.color) } ?: away.color)

private fun state(s: String?) = when (s) { "in" -> GameState.IN; "post" -> GameState.POST; else -> GameState.PRE }

private fun side(c: JsonElement): Side? {
    val t = c.o("team") ?: c // cricket's header puts the team fields on the competitor
    val id = t.s("id") ?: c.s("id") ?: return null
    val name = t.s("displayName") ?: t.s("name") ?: return null
    return Side(id, name, t.s("shortDisplayName") ?: name, t.s("abbreviation") ?: name.take(3).uppercase(),
        t.s("logo") ?: t.first("logos").s("href") ?: c.s("logo"), c.s("score") ?: (c.o("score") as? JsonObject).s("displayValue"),
        hex(t.s("color")), hex(t.s("alternateColor")))
}

/** One scoreboard response → its games; a game that doesn't parse is skipped. */
fun parseScoreboard(root: JsonElement, league: League): List<Game> {
    val l = root.first("leagues")
    val leagueName = l.s("name") ?: league.name
    val leagueLogo = l.first("logos").s("href")
    return root.a("events").mapNotNull { e -> runCatching { game(e, e.first("competitions"), league, leagueName, leagueLogo) }.getOrNull() }
}

private fun game(e: JsonElement, comp: JsonElement?, league: League, leagueName: String, leagueLogo: String?): Game? {
    val cs = comp.a("competitors").ifEmpty { e.a("competitors") }
    val sides = cs.mapNotNull { c -> side(c)?.let { (c.s("homeAway") ?: "") to it } }
    if (sides.size != 2) return null
    val home = sides.find { it.first == "home" }?.second ?: sides[0].second
    val away = sides.find { it.second !== home }?.second ?: return null
    val st = e.o("status") ?: comp.o("status")
    val type = st.o("type")
    val stateStr = type.s("state") ?: (st as? JsonPrimitive)?.contentOrNull // cricket header: "status": "post"
    return Game(e.s("id") ?: return null, league, leagueName, leagueLogo, time(e.s("date") ?: comp.s("date")) ?: return null, state(stateStr),
        type.s("shortDetail") ?: e.s("summary") ?: "", home, away,
        comp.a("broadcasts").flatMap { b -> b.a("names").mapNotNull { (it as? JsonPrimitive)?.contentOrNull } })
}

/** Cricket's header feed: every current series at once. */
fun parseCricket(root: JsonElement, league: League): List<Game> = root.first("sports").a("leagues").flatMap { l ->
    val name = l.s("name") ?: return@flatMap emptyList()
    l.a("events").mapNotNull { e -> runCatching { game(e, e, league, name, null) }.getOrNull() }
}

fun parseSummary(root: JsonElement, league: League): Match {
    val header = root.o("header")
    val comp = header.first("competitions")
    val hl = header.o("league")
    val game = runCatching { game(header ?: JsonObject(emptyMap()), comp, league, hl.s("name") ?: league.name, hl.first("logos").s("href")) }.getOrNull()
    val sides = comp.a("competitors").mapNotNull { side(it) }
    val forms = root.a("lastFiveGames").associate { f ->
        f.o("team").s("id") to f.a("events").mapNotNull { e ->
            FormGame(e.s("gameResult") ?: return@mapNotNull null, e.s("score").orEmpty(), e.o("opponent").s("displayName").orEmpty(),
                e.s("atVs").orEmpty(), e.s("leagueAbbreviation").orEmpty(), time(e.s("gameDate")) ?: 0)
        }
    }
    val rosters = root.a("rosters").associateBy { it.o("team").s("id") }
    val teams = sides.map { side ->
        val r = rosters[side.id]
        val players = r.a("roster").mapNotNull { p ->
            Player(p.s("jersey").orEmpty(), p.o("athlete").s("shortName") ?: p.o("athlete").s("displayName") ?: return@mapNotNull null,
                p.o("position").s("abbreviation").orEmpty(), p.s("starter") == "true", p.s("formationPlace")?.toIntOrNull() ?: 0,
                p.s("subbedIn") == "true", p.s("subbedOut") == "true")
        }
        TeamSheet(side, r.s("formation")?.takeIf { it.isNotBlank() }, players.filter { it.starter }, players.filter { !it.starter }, forms[side.id].orEmpty())
    }
    val box = root.o("boxscore").a("teams")
    val byTeam = box.associate { it.o("team").s("id") to it.a("statistics").associate { s -> (s.s("label") ?: "") to (s.s("displayValue") ?: "") } }
    val home = byTeam[sides.getOrNull(0)?.id].orEmpty(); val away = byTeam[sides.getOrNull(1)?.id].orEmpty()
    val stats = home.keys.filter { it.isNotEmpty() && it in away }.map { StatLine(it, home.getValue(it), away.getValue(it)) }
    val venue = root.o("gameInfo").o("venue").s("fullName")
    return Match(game, teams, stats, venue, parseKeyEvents(root))
}

fun parseStandings(root: JsonElement): List<Table> = root.a("children").ifEmpty { listOf(root) }.mapNotNull { child ->
    val entries = child.o("standings").a("entries")
    if (entries.isEmpty()) return@mapNotNull null
    val rows = entries.mapNotNull { e ->
        val team = side(e) ?: return@mapNotNull null
        val st = e.a("stats").associate { (it.s("name") ?: "") to (it.s("displayValue") ?: "") }
        TableRow(st["rank"]?.toIntOrNull() ?: 0, team, st["gamesPlayed"].orEmpty(), st["wins"].orEmpty(), st["ties"].orEmpty(),
            st["losses"].orEmpty(), st["pointDifferential"].orEmpty(), st["points"].orEmpty())
    }.sortedBy { if (it.rank == 0) Int.MAX_VALUE else it.rank }
    Table(child.s("name") ?: root.s("name").orEmpty(), rows)
}

fun parseTeams(root: JsonElement): List<Side> = root.first("sports").first("leagues").a("teams").mapNotNull { side(it.o("team") ?: return@mapNotNull null) }

// --- Order and matching (pure, unit-tested). ---

/** Favourites panel: games of a favourite team first, then the followed leagues'; live before upcoming before finished, then by kick-off. */
/** [teamResults] false: a favourite team's finished game is not a favourite (only its followed league can keep it up top). */
fun favouritesFirst(games: List<Game>, teams: Set<String>, leagues: Set<String>, teamResults: Boolean = true): Pair<List<Game>, List<Game>> {
    val teamKeys = teams.map(::favouriteTeamKey).toSet()
    val fav = games.filter { g -> (teamKeys.any(g::has) && (teamResults || g.state != GameState.POST)) || g.league.id in leagues }
        .sortedWith(compareBy<Game>({ g -> if (teamKeys.any(g::has)) 0 else 1 }, { when (it.state) { GameState.IN -> 0; GameState.PRE -> 1; GameState.POST -> 2 } }, { it.start }))
    val ids = fav.map { it.id }.toSet()
    return fav to games.filter { it.id !in ids }.sortedBy { it.start }
}

private val depthOf = listOf("G" to 0, "CD" to 1, "CB" to 1, "LB" to 1, "RB" to 1, "SW" to 1, "D" to 1, "LWB" to 2, "RWB" to 2, "DM" to 2,
    "CM" to 3, "LM" to 3, "RM" to 3, "M" to 3, "AM" to 4, "LW" to 4, "RW" to 4, "CF" to 5, "F" to 5, "ST" to 5)

/**
 * Before kick-off ESPN gives only "D"/"M"/"F", so side and depth come from the formation place, which follows the usual
 * numbering (checked against full positions after a game): 2 right-back, 3 left-back, 5/6 right/left centre-back,
 * 4/8 the deeper midfielders (left/right), 7/11 right/left wide, 9/10 the front.
 */
private val PLACE_SIDE = mapOf(2 to 2, 3 to -2, 4 to -1, 5 to 1, 6 to -1, 7 to 2, 8 to 1, 9 to 0, 10 to 0, 11 to -2)
private fun generic(pos: String) = pos == "D" || pos == "M" || pos == "F"

private fun depth(p: Player): Int {
    val d = depthOf.firstOrNull { p.position.substringBefore('-') == it.first }?.second ?: 3
    // A generic midfielder at 7, 10 or 11 is the attacking line in a 4-2-3-1 (one midfield line either way in 4-3-3 / 4-4-2).
    return if (p.position == "M" && p.place in setOf(7, 10, 11)) 4 else d
}

/** Wide (LB, LM, RW...) ±2, inside (CD-L, AM-R...) ±1, centre 0: left of the team to its right. */
private fun lateral(p: Player): Int {
    val pos = p.position
    return when {
        generic(pos) -> PLACE_SIDE[p.place] ?: 0
        pos.endsWith("-L") -> -1
        pos.endsWith("-R") -> 1
        pos.startsWith("L") && pos.length > 1 -> -2
        pos.startsWith("R") && pos.length > 1 -> 2
        else -> 0
    }
}

/**
 * Starters in formation lines, keeper first, each line ordered from the team's left to its right.
 * Lines come from sorting starters by position depth and cutting them by the formation's line sizes ("4-2-3-1").
 */
fun lineup(formation: String?, starters: List<Player>): List<List<Player>> {
    if (starters.isEmpty()) return emptyList()
    val sorted = starters.sortedWith(compareBy({ depth(it) }, { it.place }))
    val sizes = formation?.split('-')?.mapNotNull { it.trim().toIntOrNull() }.orEmpty()
    val keeper = sorted.take(1)
    val rest = sorted.drop(1)
    val lines = if (sizes.sum() == rest.size) {
        var at = 0
        sizes.map { n -> rest.subList(at, at + n).also { at += n } }
    } else rest.groupBy { depth(it) }.toSortedMap().values.toList() // formation missing or odd: one line per depth
    return listOf(keeper) + lines.map { line -> line.sortedWith(compareBy({ lateral(it) }, { it.place })) }
}

private val ALIASES = mapOf(
    "manchester united" to listOf("man utd", "man united"), "manchester city" to listOf("man city"),
    "tottenham hotspur" to listOf("spurs", "tottenham"), "wolverhampton wanderers" to listOf("wolves"),
    "brighton & hove albion" to listOf("brighton"), "nottingham forest" to listOf("nott'm forest", "forest"),
    "paris saint-germain" to listOf("psg", "paris sg"), "internazionale" to listOf("inter milan", "inter"),
    "afc bournemouth" to listOf("bournemouth"), "west ham united" to listOf("west ham"), "newcastle united" to listOf("newcastle"),
    "sheffield united" to listOf("sheff utd"), "sheffield wednesday" to listOf("sheff wed"), "queens park rangers" to listOf("qpr"),
    "west bromwich albion" to listOf("west brom"), "bayern munich" to listOf("bayern"), "borussia dortmund" to listOf("dortmund"),
)

/** Lower case, no accents, no "&"/"FC"/"AFC" and no punctuation: how team names are compared with programme titles. */
fun normTeam(s: String): String = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
    .replace("&", " and ").replace(Regex("[^a-z0-9' ]"), " ").split(' ').filter { it.isNotEmpty() && it != "fc" && it != "afc" && it != "cf" }.joinToString(" ")

/** The names a team may go by in a programme title, normalised. */
fun teamNames(side: Side): Set<String> = (listOf(side.name, side.short) + ALIASES[side.name.lowercase()].orEmpty()).map(::normTeam).filter { it.length >= 3 }.toSet()

/** A programme title names both teams (whole words). */
fun titleNamesGame(title: String, home: Side, away: Side): Boolean = gameMatch(title, home, away) != null

// After normTeam ("-", "@", ":" are spaces by then): what may stand between two team names in a fixture.
private val VERSUS = setOf("", "v", "vs", "versus", "x", "at", "gegen", "contra", "contre")

/**
 * How well [text] names the game: null = not both teams; 0 = as a fixture ("Arsenal v Dortmund", either order) and the
 * only fixture named; 1 = both teams loosely, or among several fixtures (a goals show listing the night's games).
 */
fun gameMatch(text: String, home: Side, away: Side): Int? {
    val t = " ${normTeam(text)} "
    val h = teamNames(home).filter { t.contains(" $it ") }
    val a = teamNames(away).filter { t.contains(" $it ") }
    if (h.isEmpty() || a.isEmpty()) return null
    fun pair(x: String, y: String) = Regex(" ${Regex.escape(x)} (\\S* )?${Regex.escape(y)} ").findAll(t).any { (it.groupValues[1].trim()) in VERSUS }
    val fixture = h.any { x -> a.any { y -> pair(x, y) || pair(y, x) } }
    val fixtures = Regex(" (v|vs|versus) ").findAll(t).count()
    return if (fixture && fixtures <= 1) 0 else 1
}

// --- Network + cache. ---

private val DAY = DateTimeFormatter.BASIC_ISO_DATE

class EspnClient(private val http: OkHttpClient, private val json: Json, private val cacheDir: File) {
    private val site = "https://site.api.espn.com/apis/site/v2/sports"

    /** The URL's body, cached per URL; on failure the last good copy (stale), else the error. */
    /** [cacheOnly]: the last saved answer (stale), no network; throws when there is none. */
    private suspend fun get(url: String, cacheOnly: Boolean = false): Fetched<JsonElement> = withContext(Dispatchers.IO) {
        val file = File(cacheDir, "%08x".format(url.hashCode()) + ".json")
        try {
            if (cacheOnly) throw java.io.IOException("cache only")
            val body = http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
                r.body!!.string()
            }
            val parsed = json.parseToJsonElement(body)
            cacheDir.mkdirs(); file.writeText(body)
            Fetched(parsed, System.currentTimeMillis(), false)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (!file.exists()) throw e
            Fetched(json.parseToJsonElement(file.readText()), file.lastModified(), true)
        }
    }

    /** One day's games of [leagues], fetched in parallel; a league that fails (and has no cache) is left out. Null = all failed. */
    // shortcut: ESPN's dates= is its own (US Eastern) day, so a US game after midnight UK shows on the day before; ranges 400 on some sports
    /** [cacheOnly]: the last saved answers only, to show at once while the network answers. */
    suspend fun day(leagues: List<League>, day: LocalDate, cacheOnly: Boolean = false): Fetched<List<Game>>? = coroutineScope {
        val parts = leagues.map { l ->
            async {
                runCatching {
                    if (l.sport == Sport.CRICKET) get("https://site.api.espn.com/apis/personalized/v2/scoreboard/header?sport=cricket&region=gb", cacheOnly).let { f ->
                        Fetched(parseCricket(f.value, l).filter { java.time.Instant.ofEpochMilli(it.start).atZone(java.time.ZoneId.systemDefault()).toLocalDate() == day }, f.updatedAt, f.stale)
                    } else get("$site/${l.sport.key}/${l.slug}/scoreboard?dates=${DAY.format(day)}", cacheOnly).let { Fetched(parseScoreboard(it.value, l), it.updatedAt, it.stale) }
                }.getOrNull()
            }
        }.awaitAll().filterNotNull()
        if (parts.isEmpty()) null else Fetched(parts.flatMap { it.value }, parts.minOf { it.updatedAt }, parts.any { it.stale })
    }

    suspend fun match(g: Game): Fetched<Match> = get("$site/${g.league.sport.key}/${g.league.slug}/summary?event=${g.id}").let { Fetched(parseSummary(it.value, g.league), it.updatedAt, it.stale) }

    suspend fun standings(l: League): Fetched<List<Table>> =
        get("https://site.api.espn.com/apis/v2/sports/${l.sport.key}/${l.slug}/standings").let { Fetched(parseStandings(it.value), it.updatedAt, it.stale) }

    suspend fun teams(l: League): List<Side> = parseTeams(get("$site/${l.sport.key}/${l.slug}/teams").value)
}

/** ESPN's image resizer: badges come as 500 px PNGs; the screens draw them at ~40 dp. */
fun smallLogo(url: String?, px: Int = 96): String? = url?.let {
    val path = it.substringAfter("https://a.espncdn.com", "")
    if (path.startsWith("/i/")) "https://a.espncdn.com/combiner/i?img=$path&w=$px&h=$px" else it
}
