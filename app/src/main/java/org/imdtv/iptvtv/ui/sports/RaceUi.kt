package org.imdtv.iptvtv.ui.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import org.imdtv.iptvtv.R
import org.imdtv.iptvtv.data.sports.DriverTeam
import org.imdtv.iptvtv.data.sports.Game
import org.imdtv.iptvtv.data.sports.GameState
import org.imdtv.iptvtv.data.sports.RaceSession
import org.imdtv.iptvtv.data.sports.driverTeam
import org.imdtv.iptvtv.data.sports.favouriteName
import org.imdtv.iptvtv.data.sports.sameTeam
import org.imdtv.iptvtv.ui.clockTime
import org.imdtv.iptvtv.ui.dayDate
import org.imdtv.iptvtv.ui.theme.LocalTheme

/** F1 (SportsHub): the session's name, e.g. "Practice 2". */
@Composable
fun sessionName(s: RaceSession): String = when (s) {
    RaceSession.FP1 -> stringResource(R.string.sports_f1_practice, 1)
    RaceSession.FP2 -> stringResource(R.string.sports_f1_practice, 2)
    RaceSession.FP3 -> stringResource(R.string.sports_f1_practice, 3)
    RaceSession.SPRINT_QUALIFYING -> stringResource(R.string.sports_f1_sprint_qualifying)
    RaceSession.SPRINT -> stringResource(R.string.sports_f1_sprint)
    RaceSession.QUALIFYING -> stringResource(R.string.sports_f1_qualifying)
    RaceSession.RACE -> stringResource(R.string.sports_f1_race)
}

/** The session's name; golf's round ("Round 2 - In Progress") instead. */
@Composable
internal fun sessionName(r: org.imdtv.iptvtv.data.sports.Race): String = r.note ?: sessionName(r.session)

/** "1. J. Thitikul -24": golf adds the to-par score. */
private fun withScore(name: String, d: org.imdtv.iptvtv.data.sports.Driver) = d.score?.let { "$name $it" } ?: name

@Composable
internal fun raceTitle(g: Game) = g.race!!.gp + " · " + sessionName(g.race)

/** The followed F1 teams' names ("racing:106893:Mercedes" → "Mercedes"). */
internal fun followedF1Teams(favs: Collection<String>) = favs.filter { it.startsWith("racing:") }.map(::favouriteName)

/**
 * The followed teams' drivers in this session: "K. Antonelli P1 · G. Russell P4" once it has run, else just their names
 * (from [teams]: ESPN lists no drivers before a session).
 */
internal fun followedDrivers(g: Game, favs: Collection<String>, teams: List<DriverTeam>): List<String> {
    val mine = followedF1Teams(favs)
    // Teams are matched by surname: only F1 has them (a golfer could share a driver's name).
    if (mine.isEmpty() || g.league.sport != org.imdtv.iptvtv.data.sports.Sport.F1) return emptyList()
    val order = g.race?.order.orEmpty()
    if (g.state == GameState.PRE || order.isEmpty())
        return teams.filter { t -> mine.any { sameTeam(it, t.team) } }.map { it.name }
    return order.mapIndexedNotNull { i, d -> driverTeam(d, teams)?.takeIf { t -> mine.any { sameTeam(it, t.team) } }?.let { "${d.short} P${i + 1}" } }
}

/** A session card's two lines: the Grand Prix, then the session (and the leader once it has started). */
@Composable
internal fun RaceCardLines(g: Game, fg: Color) {
    val r = g.race!!
    Text(r.gp, fontSize = 12.sp, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Text(sessionName(r) + (r.order.firstOrNull()?.takeIf { g.state != GameState.PRE }?.let { " · " + withScore(it.short, it) } ?: ""),
        fontSize = 12.sp, color = fg, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** The preview pane without a channel: the Grand Prix, the session and its top three (or the start time). */
@Composable
internal fun RaceScoreCard(g: Game, modifier: Modifier) {
    val colors = LocalTheme.current.colors
    val r = g.race!!
    Column(modifier.panel().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Badge(g.leagueLogo, 16.dp)
            Text(g.leagueName, fontSize = 11.sp, color = colors.muted, maxLines = 1, modifier = Modifier.padding(start = 6.dp))
        }
        Text(r.gp, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, modifier = Modifier.padding(top = 8.dp))
        Text(sessionName(r), fontSize = 13.sp, color = Color.White, maxLines = 1)
        if (g.state == GameState.PRE) Text(clockTime(g.start), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(top = 6.dp))
        else r.order.take(3).forEachIndexed { i, d -> Text(withScore("${i + 1}. ${d.name}", d), fontSize = 12.sp, color = Color.White, maxLines = 1) }
        if (g.state != GameState.PRE) Text(g.detail, fontSize = 11.sp, color = if (g.state == GameState.IN) colors.accent else colors.muted, modifier = Modifier.padding(top = 6.dp))
    }
}

/** The match page for a session: the Grand Prix on the left, the classification (each driver's team, followed ones in the accent). */
@Composable
internal fun RacePanel(g: Game, teams: List<DriverTeam>, favs: Collection<String>, staleSince: Long?, modifier: Modifier) {
    val colors = LocalTheme.current.colors
    val r = g.race!!
    val mine = followedF1Teams(favs)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.width(240.dp).fillMaxHeight().panel().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Badge(g.leagueLogo, 44.dp)
            Text(r.gp, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(top = 6.dp))
            Text(sessionName(r), fontSize = 14.sp, color = Color.White)
            Text(if (g.state == GameState.PRE) dayDate(g.start) + " " + clockTime(g.start) else g.detail, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = if (g.state == GameState.IN) colors.accent else colors.muted)
            staleSince?.let { Text(stringResource(R.string.sports_updated, clockTime(it)), fontSize = 10.sp, color = colors.muted) }
        }
        // Focusable so Up/Down scroll it, like the events panel.
        LazyColumn(Modifier.weight(1f).fillMaxHeight().panel().padding(12.dp).focusable(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (r.order.isEmpty() || g.state == GameState.PRE) item {
                Text(stringResource(R.string.sports_f1_no_results), fontSize = 12.sp, color = colors.muted)
            } else itemsIndexed(r.order, key = { _, d -> d.id }) { i, d ->
                val team = if (g.league.sport == org.imdtv.iptvtv.data.sports.Sport.F1) driverTeam(d, teams) else null
                val fav = team != null && mine.any { sameTeam(it, team.team) }
                val fg = if (fav) colors.accent else Color.White
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0x0DFFFFFF)).padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", fontSize = 12.sp, color = fg, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
                    Badge(d.flag, 16.dp)
                    Text(d.name, fontSize = 12.sp, color = fg, fontWeight = if (fav) FontWeight.Bold else FontWeight.Normal, maxLines = 1,
                        modifier = Modifier.padding(start = 8.dp).weight(1f))
                    Text(team?.team ?: d.score.orEmpty(), fontSize = 11.sp, color = if (team == null) Color.White else colors.muted, maxLines = 1)
                    Spacer(Modifier.width(4.dp))
                }
            }
        }
    }
}
