package org.imdtv.iptvtv.data.sports

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EspnTest {
    private fun fx(name: String) = Json.parseToJsonElement(javaClass.getResource("/espn/$name")!!.readText())
    private val ucl = leagueById("soccer/uefa.champions")!!
    private val eng2 = leagueById("soccer/eng.2")!!

    @Test fun scoreboardEverySport() {
        val games = parseScoreboard(fx("scoreboard-soccer-ucl-20261124.json"), ucl)
        val villa = games.single { it.id == "401915300" }
        assertEquals("Aston Villa", villa.away.name)
        assertEquals("Galatasaray", villa.home.name)
        assertEquals(java.time.Instant.parse("2026-11-24T17:45:00Z").toEpochMilli(), villa.start)
        assertEquals(GameState.PRE, villa.state)
        assertEquals("UEFA Champions League", villa.leagueName)
        assertEquals(8, parseScoreboard(fx("scoreboard-soccer-eng2-20261124.json"), eng2).size)
        for ((f, l) in listOf("scoreboard-nfl.json" to "football/nfl", "scoreboard-nba.json" to "basketball/nba", "scoreboard-nhl.json" to "hockey/nhl", "scoreboard-rugby-urc.json" to "rugby/270557"))
            assertTrue(f, parseScoreboard(fx(f), leagueById(l)!!).isNotEmpty())
        val cricket = parseCricket(fx("cricket-header.json"), leagueById("cricket/header")!!)
        assertTrue(cricket.isNotEmpty())
        assertTrue(cricket.any { it.state == GameState.POST && it.home.score != null })
    }

    @Test fun badShapesDropGamesNotTheScreen() {
        val bad = Json.parseToJsonElement("""{"events":[{"id":"1"},{"id":"2","date":"x","competitions":[{"competitors":"no"}]}, 3], "leagues": "?"}""")
        assertTrue(parseScoreboard(bad, eng2).isEmpty())
        assertTrue(parseSummary(Json.parseToJsonElement("[]"), eng2).teams.isEmpty())
        assertTrue(parseStandings(Json.parseToJsonElement("{}")).isEmpty())
    }

    @Test fun summaryLineupsFormAndStats() {
        val m = parseSummary(fx("summary-soccer-eng2-post.json"), eng2)
        assertEquals(GameState.POST, m.game!!.state)
        val saints = m.teams.single { it.side.name == "Southampton" }
        assertEquals("4-2-3-1", saints.formation)
        assertEquals(11, saints.starters.size)
        assertEquals(9, saints.subs.size)
        assertEquals(5, saints.form.size)
        assertTrue(m.stats.any { it.label == "Possession" })
        // Keeper, then the back four left to right, two, three, one.
        val lines = lineup(saints.formation, saints.starters).map { l -> l.map { it.name } }
        assertEquals(listOf(listOf("D. Peretz"), listOf("R. Manning", "J. Stephens", "Z. Abbott", "F. Downes"), listOf("J. Ward-Prowse", "C. Bragg"),
            listOf("S. Edozie", "F. Azaz", "T. Fellows"), listOf("C. Larin")), lines)
    }

    @Test fun preMatchGenericPositionsUseFormationPlaces() {
        val m = parseSummary(fx("summary-soccer-ucl-pre-lineups.json"), ucl)
        val villa = m.teams.single { it.side.name == "Aston Villa" }
        val lines = lineup(villa.formation, villa.starters).map { l -> l.map { it.name } }
        // 4-2-3-1: left-back Maatsen (3) ... right-back Harwood-Bellis (2); Gomes (4) and Kamara (8) behind McGinn (11), Manzambi (10), Alysson (7).
        assertEquals(listOf(listOf("Z. Suzuki"), listOf("I. Maatsen", "T. Mings", "V. Lindelöf", "T. Harwood-Bellis"), listOf("J. Gomes", "B. Kamara"),
            listOf("J. McGinn", "J. Manzambi", "Alysson"), listOf("N. Jackson")), lines)
        val gala = m.teams.single { it.side.name == "Galatasaray" }
        assertEquals(listOf(1, 4, 3, 3), lineup(gala.formation, gala.starters).map { it.size })
    }

    @Test fun kitColoursAvoidAClash() {
        val m = parseSummary(fx("summary-soccer-ucl-pre-lineups.json"), ucl)
        val gala = m.teams.single { it.side.name == "Galatasaray" }.side
        val villa = m.teams.single { it.side.name == "Aston Villa" }.side
        assertEquals("aa0031" to "000000", kitColours(gala, villa)) // Villa away in their away colour
        val whiteHome = gala.copy(color = "ffffff")
        val whiteAway = villa.copy(altColor = "fefefe")
        assertEquals("ffffff" to "660e36", kitColours(whiteHome, whiteAway)) // both white: Villa wear their home colour
    }

    @Test fun lineupWithoutFormationStillPlacesEveryone() {
        val ps = listOf("G", "LB", "CD", "RB", "CM", "F").mapIndexed { i, p -> Player("$i", p, p, true, i + 1, false, false) }
        val lines = lineup(null, ps)
        assertEquals(6, lines.flatten().size)
        assertEquals("G", lines[0][0].position)
    }

    @Test fun standingsAndTeams() {
        val t = parseStandings(fx("standings-eng1.json")).single()
        assertEquals(20, t.rows.size)
        assertEquals(1, t.rows[0].rank)
        assertEquals(20, parseTeams(fx("teams-eng1.json")).size)
    }

    @Test fun favouriteTeamBeatsFollowedLeague() {
        val games = parseScoreboard(fx("scoreboard-soccer-eng2-20261124.json"), eng2) + parseScoreboard(fx("scoreboard-soccer-ucl-20261124.json"), ucl)
        val (fav, rest) = favouritesFirst(games, setOf(favouriteKey(Sport.FOOTBALL, "362", "Aston Villa")), setOf("soccer/eng.1", "soccer/eng.2"))
        assertEquals("401915300", fav.first().id) // UCL: not a followed league, but Villa are playing
        assertEquals(9, fav.size)
        assertTrue(rest.none { it.league == eng2 })
        assertFalse(rest.any { it.id == "401915300" })
    }

    @Test fun finishedTeamGameLeavesFavouritesWhenResultsOff() {
        val games = parseScoreboard(fx("scoreboard-soccer-eng2-20261124.json"), eng2) + parseScoreboard(fx("scoreboard-soccer-ucl-20261124.json"), ucl)
        val villa = setOf(favouriteKey(Sport.FOOTBALL, "362", "Aston Villa"))
        val ended = games.single { it.id == "401915300" }.copy(state = GameState.POST)
        val list = games.map { if (it.id == ended.id) ended else it }
        assertTrue(favouritesFirst(list, villa, emptySet(), teamResults = true).first.any { it.id == ended.id })
        assertTrue(favouritesFirst(list, villa, emptySet(), teamResults = false).second.any { it.id == ended.id })
    }

    @Test fun eventChannelNamesNameBothTeams() {
        val wolves = Side("380", "Wolverhampton Wanderers", "Wolves", "WOL", null, null)
        val leeds = Side("357", "Leeds United", "Leeds", "LEE", null, null)
        assertTrue(titleNamesGame("UK| EPL 03: Wolves v Leeds 20:00", wolves, leeds))
        assertFalse(titleNamesGame("UK| Championship PPV 03", wolves, leeds))
    }

    @Test fun programmeTitlesNameBothTeams() {
        val gala = Side("1", "Galatasaray", "Galatasaray", "GAL", null, null)
        val villa = Side("362", "Aston Villa", "Aston Villa", "AVL", null, null)
        assertTrue(titleNamesGame("Live: Galatasaray v Aston Villa", gala, villa))
        // TNT Sports 2 in a real UK guide (2026-11-24): the title names only the competition, the description the teams.
        assertFalse(titleNamesGame("Live UEFA Champions League", gala, villa))
        assertTrue(titleNamesGame("Live UEFA Champions League Galatasaray v Aston Villa (Kick-off 5.45pm). Coverage of the league phase match from Rams Park.", gala, villa))
        assertTrue(titleNamesGame("UEFA Champions League - Galatasaray vs Aston Villa FC", gala, villa))
        assertEquals(0, gameMatch("Live UEFA Champions League Galatasaray v Aston Villa (Kick-off 5.45pm). Coverage of the league phase match from Rams Park.", gala, villa))
        assertEquals(0, gameMatch("UEFA Champions League - Galatasaray vs Aston Villa FC", gala, villa))
        assertEquals(0, gameMatch("UCL: Aston Villa @ Galatasaray", gala, villa))
        // Real UK goals show (2026-11-24): names the game among others, so it ranks below the match itself.
        assertEquals(1, gameMatch("Live: UEFA Champions League Goals Show Xtra Coverage of all the goals from Tuesday's fixtures, including Manchester City v Napoli, Arsenal v Borussia Dortmund, and Galatasaray v Aston Villa.", gala, villa))
        assertEquals(1, gameMatch("Galatasaray host Aston Villa in Istanbul", gala, villa))
        assertFalse(titleNamesGame("Aston Villa: Season Review", gala, villa))
        val utd = Side("360", "Manchester United", "Man United", "MUN", null, null)
        val spurs = Side("367", "Tottenham Hotspur", "Tottenham", "TOT", null, null)
        assertTrue(titleNamesGame("Live PL: Man Utd v Spurs", utd, spurs))
        assertTrue(titleNamesGame("Brighton & Hove Albion v Man Utd", Side("331", "Brighton & Hove Albion", "Brighton", "BHA", null, null), utd))
    }

    @Test fun keyEventsNewestFirst() {
        val ev = parseSummary(fx("summary-soccer-eng2-post.json"), eng2).events
        assertEquals(EventKind.FULLTIME, ev.first().kind)
        assertEquals(EventKind.KICKOFF, ev.last().kind)
        assertEquals(2, ev.count { it.kind == EventKind.GOAL })
        assertTrue(ev.none { it.who.isEmpty() && it.kind == EventKind.SUB })
        val sub = ev.last { it.kind == EventKind.SUB } // the first substitution of the game
        assertEquals(listOf("Kuryu Matsuki", "Cameron Bragg"), sub.who)
        assertEquals("45'", sub.minute)
        val lastGoal = ev.first { it.kind == EventKind.GOAL }
        assertEquals("90'+5'", lastGoal.minute); assertEquals("376", lastGoal.teamId); assertEquals("Divin Mubama", lastGoal.who.first())
    }

    private val f1 = leagueById("racing/f1")!!

    @Test fun f1WeekendIsOneGamePerSession() {
        val sessions = parseRacing(fx("scoreboard-f1-20261121.json"), f1)
        assertEquals(listOf(RaceSession.FP1, RaceSession.FP2, RaceSession.FP3, RaceSession.QUALIFYING, RaceSession.RACE), sessions.map { it.race!!.session })
        val race = sessions.last()
        assertEquals("Las Vegas Grand Prix", race.race!!.gp)
        assertEquals(GameState.POST, race.state)
        assertEquals("Max Verstappen", race.race!!.order.first().name)
        assertTrue(race.has("racing:106893:Mercedes"))
        assertFalse(race.has("soccer:362:Aston Villa"))
    }

    @Test fun gpNameDropsTheSponsor() {
        assertEquals("Qatar Grand Prix", gpName("Qatar Airways Qatar Grand Prix"))
        assertEquals("São Paulo Grand Prix", gpName("MSC Cruises São Paulo Grand Prix"))
        assertEquals("Mexico City Grand Prix", gpName("Mexico City Grand Prix"))
        assertEquals("Monaco Grand Prix", gpName("Monaco Grand Prix"))
        assertEquals("Bahrain Grand Prix in Malaysia", gpName("Gulf Air Bahrain Grand Prix in Malaysia"))
    }

    @Test fun raceMatchKnowsTheSession() {
        val r = Race("Qatar Grand Prix", RaceSession.QUALIFYING, setOf("qatar", "lusail"), emptyList())
        assertEquals(0, raceMatch("Formula 1: Qatar Grand Prix Qualifying", r))
        assertEquals(1, raceMatch("F1: Qatar Grand Prix", r))
        assertEquals(null, raceMatch("F1: Qatar Grand Prix Sprint Qualifying", r))
        assertEquals(null, raceMatch("Formula 1: Qatar Grand Prix - Race", r))
        assertEquals(null, raceMatch("Formula 1: Las Vegas Grand Prix Qualifying", r))
        assertEquals(null, raceMatch("Qatar v Oman", r))
        assertEquals(0, raceMatch("Formula 1: Qatar GP Race", r.copy(session = RaceSession.RACE)))
        assertEquals(0, raceMatch("F1 Sprint Qualifying Lusail", r.copy(session = RaceSession.SPRINT_QUALIFYING)))
    }

    @Test fun f1DriversAndTeams() {
        val teams = parseDriverTeams(fx("jolpica-driverstandings.json"))
        assertTrue(teams.size >= 20)
        val race = parseRacing(fx("scoreboard-f1-20261121.json"), f1).last().race!!
        assertTrue(race.order.count { driverTeam(it, teams) != null } >= 20)
        assertTrue(sameTeam("Haas F1 Team", "Haas")); assertTrue(sameTeam("RB F1 Team", "Racing Bulls")); assertFalse(sameTeam("Red Bull", "Racing Bulls"))
        val tables = parseStandings(fx("standings-f1.json"))
        assertEquals(2, tables.size)
        assertTrue(tables[0].rows.first().stats["championshipPts"].orEmpty().isNotEmpty())
    }

    @Test fun usualEndIsKickOffPlusTheSportsLength() {
        val g = parseScoreboard(fx("scoreboard-soccer-ucl-20261124.json"), ucl).first()
        assertEquals(g.start + 115 * 60_000L, usualEnd(g))
        val race = parseRacing(fx("scoreboard-f1-20261121.json"), f1)
        assertEquals(race.first().start + 60 * 60_000L, usualEnd(race.first())) // practice
        assertEquals(race.last().start + 120 * 60_000L, usualEnd(race.last()))
    }

    /** 26 Nov: ESPN's "CSU Craiova" is "Universitatea Craiova" in the guide and event channel names, so Brighton's game found nothing. */
    @Test fun clubsMatchOnTheirDistinctiveLastWord() {
        val brighton = Side("331", "Brighton & Hove Albion", "Brighton", "BHA", null, null)
        val craiova = Side("2", "CSU Craiova", "CSU Craiova", "CRA", null, null)
        assertEquals(0, gameMatch("Brighton vs. Universitatea Craiova", brighton, craiova))
        assertEquals(0, gameMatch("Live UEFA Conference League Brighton v Universitatea Craiova (Kick-off 5.45pm).", brighton, craiova))
        // Generic last words stay out: "Leeds United" must not match any "... United".
        val leeds = Side("357", "Leeds United", "Leeds United", "LEE", null, null)
        assertEquals(null, gameMatch("Brighton v Newcastle United", brighton, leeds))
        assertEquals(null, gameMatch("Brighton v Aston Villa", brighton, craiova))
    }

    @Test
    fun levelScoreSettledOnPenaltiesIsADraw() {
        assertEquals("D", formResult("L", "1-1"))  // Carabao Cup, lost the shoot-out
        assertEquals("D", formResult("W", "2 - 2"))
        assertEquals("L", formResult("L", "0-1"))
        assertEquals("W", formResult("W", ""))
    }

    @Test fun golfIsALeaderboardAndUfcACardOfFights() {
        val golf = parseGolf(fx("scoreboard-golf-lpga.json"), leagueById("golf/lpga")!!).single()
        val r = golf.race!!
        assertEquals("CME Group Tour Championship", r.gp)
        assertEquals(GameState.POST, golf.state)
        assertEquals("Jeeno Thitikul", r.order.first().name)
        assertEquals("-24", r.order.first().score)
        assertEquals(0, golfMatch("Live Golf: CME Group Tour Championship", r))
        assertEquals(1, golfMatch("LPGA Tour Golf highlights", r))
        assertEquals(null, golfMatch("CME Group news", r))
        val fights = parseFights(fx("scoreboard-ufc.json"), leagueById("mma/ufc")!!)
        assertEquals(14, fights.size)
        assertTrue(fights.all { it.home.name.isNotEmpty() && it.away.name.isNotEmpty() && it.home.logo != null })
        assertEquals("UFC Fight Night: Adesanya vs Magomedov", fights.first().leagueName)
    }
}
