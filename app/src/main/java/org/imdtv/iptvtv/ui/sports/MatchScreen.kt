package org.imdtv.iptvtv.ui.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import org.imdtv.iptvtv.R
import org.imdtv.iptvtv.data.db.ChannelEntity
import org.imdtv.iptvtv.data.sports.EventKind
import org.imdtv.iptvtv.data.sports.Game
import org.imdtv.iptvtv.data.sports.KeyEvent
import org.imdtv.iptvtv.data.sports.GameState
import org.imdtv.iptvtv.data.sports.Player
import org.imdtv.iptvtv.data.sports.Sport
import org.imdtv.iptvtv.data.sports.StatLine
import org.imdtv.iptvtv.data.sports.TeamSheet
import org.imdtv.iptvtv.data.sports.lineup
import org.imdtv.iptvtv.ui.FocusBox
import org.imdtv.iptvtv.ui.Spinner
import org.imdtv.iptvtv.ui.clockTime
import org.imdtv.iptvtv.ui.dayDate
import org.imdtv.iptvtv.ui.live.LiveViewModel
import org.imdtv.iptvtv.ui.theme.LocalTheme

/** Two stacked team cards (mock-match.png): name, badge and recent form · lineup on a pitch · competition, where to watch and subs. */
@Composable
fun MatchScreen(vm: SportsViewModel, live: LiveViewModel, game: Game, onFull: (ChannelEntity) -> Unit) {
    val colors = LocalTheme.current.colors
    LaunchedEffect(game.id) { vm.pollMatch(game) }
    val fetched by vm.match.collectAsState()
    val failed by vm.matchFailed.collectAsState()
    val strip by live.strip.collectAsState()
    val watch by produceState(emptyList<OnTv>(), game.id) { value = vm.whereToWatch(game, strip) }
    val firstWatch = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
    val m = fetched?.value
    val g = m?.game ?: game
    Box(Modifier.sportsBackground()) {
        when {
            m == null && !failed -> Spinner(Modifier.align(Alignment.Center), 24.dp, colors.muted)
            m == null -> Text(stringResource(R.string.sports_unavailable), fontSize = 14.sp, color = colors.muted, modifier = Modifier.align(Alignment.Center))
            else -> Column(Modifier.padding(horizontal = 24.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Score line, then one card per team (home first).
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${g.home.name}  ${if (g.state == GameState.PRE) "v" else "${g.home.score.orEmpty()} – ${g.away.score.orEmpty()}"}  ${g.away.name}",
                        fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(if (g.state == GameState.PRE) dayDate(g.start) + " " + clockTime(g.start) else g.detail, fontSize = 13.sp,
                        color = if (g.state == GameState.IN) colors.accent else colors.muted)
                    fetched?.takeIf { it.stale }?.let { Text("  " + stringResource(R.string.sports_updated, clockTime(it.updatedAt)), fontSize = 11.sp, color = colors.muted) }
                }
                // Where to watch: every channel showing it, best first (an IPTV playlist often has several).
                if (watch.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.sports_where_to_watch), fontSize = 11.sp, color = colors.muted, modifier = Modifier.padding(end = 10.dp))
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(watch.size) { i -> WatchCard(watch[i], if (i == 0) Modifier.focusRequester(firstWatch) else Modifier) { onFull(watch[i].ch) } }
                    }
                    // Nothing else on the page takes focus: start (and come back from full screen) on the first channel.
                    LaunchedEffect(Unit) { runCatching { firstWatch.requestFocus() } }
                }
                val teams = m.teams.ifEmpty { listOf(TeamSheet(g.home, null, emptyList(), emptyList(), emptyList()), TeamSheet(g.away, null, emptyList(), emptyList(), emptyList())) }
                val kits = org.imdtv.iptvtv.data.sports.kitColours(g.home, g.away)
                fun kitOf(teamId: String?) = colour(if (teamId == g.away.id) kits.second else kits.first)
                val football = g.league.sport == Sport.FOOTBALL
                // Football: team cards narrowed, with the minute-by-minute events panel beside both (user's mockup, 2026-11-24).
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        teams.take(2).forEachIndexed { i, t ->
                            TeamCard(t, g, m.stats, home = i == 0, modifier = Modifier.weight(1f), kit = kitOf(t.side.id), side = if (football) 160.dp else 190.dp)
                        }
                    }
                    if (football) EventsPanel(m.events, ::kitOf, Modifier.width(170.dp).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun TeamCard(t: TeamSheet, g: Game, stats: List<StatLine>, home: Boolean, modifier: Modifier, kit: Color?, side: androidx.compose.ui.unit.Dp) {
    val colors = LocalTheme.current.colors
    Row(modifier.fillMaxWidth().panel().padding(12.dp)) {
        // Left: team, badge, recent form.
        Column(Modifier.width(190.dp).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Badge(t.side.logo, 36.dp)
                Text(t.side.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp))
            }
            Text(stringResource(R.string.sports_recent_form), fontSize = 11.sp, color = colors.muted, modifier = Modifier.padding(top = 8.dp, bottom = 3.dp))
            t.form.take(5).forEach { f ->
                Row(Modifier.padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(f.result, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.width(18.dp).clip(RoundedCornerShape(4.dp)).background(when (f.result) { "W" -> Color(0xFF2E7D32); "L" -> Color(0xFFC62828); else -> Color(0xFF757575) }))
                    Text("${f.score}  ${f.atVs} ${f.opponent}", fontSize = 11.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
        // Middle: the lineup on a pitch (football), else the stats.
        Box(Modifier.padding(horizontal = 12.dp).weight(1f).fillMaxHeight()) {
            when {
                g.league.sport == Sport.FOOTBALL && t.starters.isNotEmpty() -> Pitch(lineup(t.formation, t.starters), t.formation, kit)
                stats.isNotEmpty() -> Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) { StatBars(stats, home) }
                    if (g.league.sport == Sport.FOOTBALL) Text(stringResource(R.string.sports_lineup_soon), fontSize = 11.sp, color = colors.muted)
                }
                else -> Text(stringResource(if (g.league.sport == Sport.FOOTBALL) R.string.sports_lineup_soon else R.string.sports_no_stats), fontSize = 12.sp,
                    color = colors.muted, modifier = Modifier.align(Alignment.Center))
            }
        }
        // Right: competition, subs.
        Column(Modifier.width(side).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Badge(g.leagueLogo, 28.dp)
                Text(g.leagueName, fontSize = 11.sp, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp))
            }
            if (t.subs.isNotEmpty()) {
                Text(stringResource(R.string.sports_subs), fontSize = 11.sp, color = colors.muted, modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
                t.subs.take(10).forEach { p ->
                    Text((if (p.subbedIn) "↑ " else "") + "${p.jersey}  ${p.name}", fontSize = 10.sp, maxLines = 1,
                        color = if (p.subbedIn) colors.accent else Color.White)
                }
            }
        }
    }
}

/** Goals, cards, subs and the half markers, newest at the top; each line has its team's kit colour down the side. */
@Composable
private fun EventsPanel(events: List<KeyEvent>, kit: (String?) -> Color?, modifier: Modifier) {
    val colors = LocalTheme.current.colors
    Column(modifier.panel().padding(10.dp)) {
        Text(stringResource(R.string.sports_events), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(bottom = 6.dp))
        // shortcut: what the panel shows before kick-off is the user's call (2026-11-24); one muted line until then.
        if (events.isEmpty()) Text(stringResource(R.string.sports_events_soon), fontSize = 11.sp, color = colors.muted)
        // Not scrollable (the page has no focus here): a long game shows its newest events and clips the oldest.
        Column(Modifier.clip(RoundedCornerShape(4.dp)), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            events.forEach { e -> EventLine(e, kit(e.teamId)) }
        }
    }
}

@Composable
private fun EventLine(e: KeyEvent, kit: Color?) {
    val colors = LocalTheme.current.colors
    val marker = when (e.kind) {
        EventKind.KICKOFF -> R.string.sports_ev_kickoff; EventKind.HALFTIME -> R.string.sports_ev_halftime
        EventKind.SECOND_HALF -> R.string.sports_ev_second_half; EventKind.FULLTIME -> R.string.sports_ev_fulltime
        else -> null
    }
    Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(if (marker == null) kit ?: colors.accent else Color.Transparent))
        Text(e.minute, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.muted, maxLines = 1, modifier = Modifier.padding(start = 5.dp).width(42.dp))
        if (marker != null) { Text(stringResource(marker), fontSize = 10.sp, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis); return@Row }
        when (e.kind) {
            EventKind.GOAL -> Text("⚽", fontSize = 10.sp)
            EventKind.YELLOW, EventKind.RED -> Box(Modifier.size(7.dp, 10.dp).clip(RoundedCornerShape(1.dp)).background(if (e.kind == EventKind.RED) Color(0xFFE53935) else Color(0xFFFDD835)))
            else -> Text("⇄", fontSize = 10.sp, color = colors.accent)
        }
        Column(Modifier.padding(start = 5.dp)) {
            Text(e.who.firstOrNull().orEmpty(), fontSize = 10.sp, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            // A substitution's second name is the player going off.
            if (e.kind == EventKind.SUB) e.who.getOrNull(1)?.let { Text("↓ $it", fontSize = 9.sp, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

fun colour(hex: String?): Color? = hex?.toLongOrNull(16)?.let { Color(0xFF000000 or it) }

/** Where to watch: the channel's logo and name, live now or its start time; OK plays it full screen. */
@Composable
private fun WatchCard(tv: OnTv, modifier: Modifier, onWatch: () -> Unit) = FocusBox(onWatch, modifier) { f ->
    val colors = LocalTheme.current.colors
    val now = tv.liveAt(System.currentTimeMillis())
    // A button named after the channel: a red dot when it's on now, else its start time.
    Row(Modifier.clip(RoundedCornerShape(15.dp)).background(if (f) Color.White else colors.cell).padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (now) Box(Modifier.padding(end = 6.dp).size(7.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(0xFFFF3B3B)))
        Text(tv.ch.name + if (now) "" else "  " + clockTime(tv.p.start), fontSize = 13.sp, fontWeight = FontWeight.Medium,
            color = if (f) colors.onFocus else Color.White, maxLines = 1)
    }
}

/** Sky style: the team attacks to the right, keeper on the left; each player a number tag + name. Lines are [lineup]'s. */
@Composable
private fun Pitch(lines: List<List<Player>>, formation: String?, color: Color?) {
    val accent = color ?: LocalTheme.current.colors.accent
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).drawWithCache {
        val stripe = size.width / 10
        val line = Color(0x8CFFFFFF)
        val w = 1.dp.toPx()
        onDrawBehind {
            for (i in 0 until 10) drawRect(if (i % 2 == 0) Color(0xFF1B5E20) else Color(0xFF226B27), Offset(i * stripe, 0f), Size(stripe + 1, size.height))
            val m = 4.dp.toPx()
            drawRoundRect(line, Offset(m, m), Size(size.width - 2 * m, size.height - 2 * m), CornerRadius(2f), style = Stroke(w))
            drawLine(line, Offset(size.width / 2, m), Offset(size.width / 2, size.height - m), w)
            drawCircle(line, size.height * .16f, Offset(size.width / 2, size.height / 2), style = Stroke(w))
            val boxW = size.width * .13f; val boxH = size.height * .56f
            drawRect(line, Offset(m, (size.height - boxH) / 2), Size(boxW, boxH), style = Stroke(w))
            drawRect(line, Offset(size.width - m - boxW, (size.height - boxH) / 2), Size(boxW, boxH), style = Stroke(w))
        }
    }) {
        formation?.let { Text(it, fontSize = 10.sp, color = Color(0xCCFFFFFF), modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)) }
        // Each tag is centred on its spot: column = line, row = place in the line (the team's left at the top).
        Layout({ lines.flatten().forEach { PlayerTag(it, accent) } }, Modifier.fillMaxSize()) { ms, c ->
            val ps = ms.map { it.measure(c.copy(minWidth = 0, minHeight = 0)) }
            layout(c.maxWidth, c.maxHeight) {
                var k = 0
                lines.forEachIndexed { li, l ->
                    val x = c.maxWidth * (li + .5f) / lines.size
                    l.forEachIndexed { pi, _ ->
                        val p = ps[k++]
                        val y = c.maxHeight * (pi + .5f) / l.size
                        p.place((x - p.width / 2).toInt().coerceIn(0, (c.maxWidth - p.width).coerceAtLeast(0)), (y - p.height / 2).toInt().coerceIn(0, (c.maxHeight - p.height).coerceAtLeast(0)))
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerTag(p: Player, accent: Color) = Row(Modifier.clip(RoundedCornerShape(3.dp)).background(Color(0xF2FFFFFF)), verticalAlignment = Alignment.CenterVertically) {
    // The team's colour behind the number; a light colour (white kits) gets dark digits.
    Text(p.jersey, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (accent.luminance() > .6f) Color(0xFF14213D) else Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier.background(accent).width(18.dp).padding(vertical = 1.dp))
    Text((if (p.subbedOut) "↓ " else "") + p.name.substringAfter(". "), fontSize = 9.sp, fontWeight = FontWeight.Medium, color = Color(0xFF14213D), maxLines = 1,
        modifier = Modifier.padding(horizontal = 4.dp))
}

/** A few box-score lines as two-sided bars, this team's side highlighted. */
@Composable
private fun StatBars(stats: List<StatLine>, home: Boolean) {
    val colors = LocalTheme.current.colors
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        stats.take(7).forEach { s ->
            val a = (s.home.toFloatOrNull() ?: 0f).coerceAtLeast(0f); val b = (s.away.toFloatOrNull() ?: 0f).coerceAtLeast(0f) // goal difference can be negative
            val share = if (a + b > 0) a / (a + b) else .5f
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.home, fontSize = 11.sp, color = if (home) Color.White else colors.muted, modifier = Modifier.width(44.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.label, fontSize = 10.sp, color = colors.muted, maxLines = 1, modifier = Modifier.align(Alignment.CenterHorizontally))
                    Row(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))) {
                        if (share > 0f) Box(Modifier.weight(share).fillMaxHeight().background(if (home) colors.accent else Color(0x55FFFFFF)))
                        if (share < 1f) Box(Modifier.weight(1f - share).fillMaxHeight().background(if (home) Color(0x55FFFFFF) else colors.accent))
                    }
                }
                Text(s.away, fontSize = 11.sp, color = if (home) colors.muted else Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.width(44.dp))
            }
        }
    }
}
