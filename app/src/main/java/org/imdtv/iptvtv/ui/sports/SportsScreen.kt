package org.imdtv.iptvtv.ui.sports

import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.layout.layout
import androidx.compose.ui.zIndex
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import org.imdtv.iptvtv.R
import org.imdtv.iptvtv.data.db.ChannelEntity
import org.imdtv.iptvtv.data.settings.AppSettings
import org.imdtv.iptvtv.data.sports.Game
import org.imdtv.iptvtv.data.sports.GameState
import org.imdtv.iptvtv.data.sports.LEAGUES
import org.imdtv.iptvtv.data.sports.League
import org.imdtv.iptvtv.data.sports.Side
import org.imdtv.iptvtv.data.sports.Sport
import org.imdtv.iptvtv.data.sports.Table
import org.imdtv.iptvtv.data.sports.favouriteKey
import org.imdtv.iptvtv.data.sports.favouriteName
import org.imdtv.iptvtv.data.sports.favouriteTeamKey
import org.imdtv.iptvtv.data.sports.favouritesFirst
import org.imdtv.iptvtv.data.sports.smallLogo
import org.imdtv.iptvtv.data.sports.teamKey
import org.imdtv.iptvtv.ui.FocusBox
import org.imdtv.iptvtv.ui.onLongOk
import org.imdtv.iptvtv.ui.Spinner
import org.imdtv.iptvtv.ui.clockTime
import org.imdtv.iptvtv.ui.dayDate
import org.imdtv.iptvtv.ui.live.LiveViewModel
import org.imdtv.iptvtv.ui.live.Preview
import org.imdtv.iptvtv.ui.live.PreviewEdge
import org.imdtv.iptvtv.ui.live.backgroundBitmap
import org.imdtv.iptvtv.ui.settings.SwitchRow
import org.imdtv.iptvtv.ui.theme.LocalGlassBackdrop
import org.imdtv.iptvtv.ui.theme.LocalTheme
import org.imdtv.iptvtv.ui.theme.glass

@Composable
fun sportName(s: Sport) = stringResource(when (s) {
    Sport.FOOTBALL -> R.string.sports_sport_football; Sport.AMERICAN_FOOTBALL -> R.string.sports_sport_american_football
    Sport.BASKETBALL -> R.string.sports_sport_basketball; Sport.HOCKEY -> R.string.sports_sport_hockey
    Sport.RUGBY -> R.string.sports_sport_rugby; Sport.CRICKET -> R.string.sports_sport_cricket
    Sport.F1 -> R.string.sports_sport_f1
})

@Composable
private fun regionName(r: org.imdtv.iptvtv.data.sports.Region) = when (r) {
    org.imdtv.iptvtv.data.sports.Region.NONE -> ""
    org.imdtv.iptvtv.data.sports.Region.ENGLAND -> stringResource(R.string.sports_region_england)
    org.imdtv.iptvtv.data.sports.Region.SCOTLAND -> stringResource(R.string.sports_region_scotland)
    org.imdtv.iptvtv.data.sports.Region.EUROPE -> stringResource(R.string.sports_region_europe)
    org.imdtv.iptvtv.data.sports.Region.INTERNATIONAL -> stringResource(R.string.sports_region_international)
    // Real countries: the system's own name for them, in the app's language.
    org.imdtv.iptvtv.data.sports.Region.SPAIN -> country("ES")
    org.imdtv.iptvtv.data.sports.Region.GERMANY -> country("DE")
    org.imdtv.iptvtv.data.sports.Region.ITALY -> country("IT")
    org.imdtv.iptvtv.data.sports.Region.FRANCE -> country("FR")
    org.imdtv.iptvtv.data.sports.Region.IRELAND -> country("IE")
    org.imdtv.iptvtv.data.sports.Region.SAUDI_ARABIA -> country("SA")
    org.imdtv.iptvtv.data.sports.Region.ASIA -> stringResource(R.string.sports_region_asia)
}

@Composable
private fun country(code: String): String =
    java.util.Locale("", code).getDisplayCountry(androidx.compose.ui.platform.LocalConfiguration.current.locales[0])

/** Celestial Glass's background (cached bitmap, like the guide) or Liquid's backdrop. */
@Composable
internal fun Modifier.sportsBackground(onScreen: (Size) -> Unit = {}): Modifier {
    val colors = LocalTheme.current.colors
    val backdrop = LocalGlassBackdrop.current
    return fillMaxSize().onSizeChanged { onScreen(Size(it.width.toFloat(), it.height.toFloat())) }.drawWithCache {
        val img = backdrop?.sharp ?: backgroundBitmap(colors, this, size, Offset.Zero, size)
        onDrawBehind { drawImage(img) }
    }
}

/** A rounded panel: a faint tint in Celestial Glass, glass in Liquid Glass. */
internal val PANEL_TINT = Color(0x12FFFFFF)

@Composable
internal fun Modifier.panel(): Modifier = clip(RoundedCornerShape(20.dp)).background(PANEL_TINT)
    .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(20.dp)).glass(RoundedCornerShape(20.dp), blur = false)

@Composable
internal fun Pill(text: String, modifier: Modifier = Modifier, selected: Boolean = false, small: Boolean = false, onClick: () -> Unit) =
    FocusBox(onClick, modifier) { f ->
        Text(text, fontSize = if (small) 11.sp else 12.sp, fontWeight = FontWeight.Medium, maxLines = 1,
            color = when { f -> Color.Black; selected -> Color.White; else -> LocalTheme.current.colors.muted },
            modifier = Modifier.clip(RoundedCornerShape(15.dp))
                .background(when { f -> Color.White; selected -> Color(0x33FFFFFF); else -> Color(0x14FFFFFF) })
                .padding(horizontal = if (small) 10.dp else 14.dp, vertical = if (small) 4.dp else 6.dp))
    }

@Composable
internal fun Badge(url: String?, size: androidx.compose.ui.unit.Dp) {
    // Fetched at the size drawn (rounded up to 40 px steps, so sizes share cache entries): 96 px stretched to 200 dp was blurry.
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { size.roundToPx() }.let { (it + 39) / 40 * 40 }
    if (url == null) Spacer(Modifier.size(size))
    else AsyncImage(smallLogo(url, px), null, contentScale = ContentScale.Fit, modifier = Modifier.size(size))
}

/** Scroll only as far as needed to show the focused item (the TV default moves it to a pivot ~30% down: the box jumped). */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private val ScrollIfNeeded = object : androidx.compose.foundation.gestures.BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = when {
        offset >= 0f && offset + size <= containerSize -> 0f
        offset < 0f || size > containerSize -> offset
        else -> offset + size - containerSize
    }
}

/** "19:45" before kick-off, ESPN's "63'" / "FT" after. */
@Composable
internal fun status(g: Game, day: java.time.LocalDate? = null): String {
    if (g.state != GameState.PRE) return g.detail
    // ESPN's day is the US one: a game on another local day says which ("Wed 00:00").
    val local = java.time.Instant.ofEpochMilli(g.start).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    return if (day != null && local != day) dayDate(g.start).substringBefore(' ') + " " + clockTime(g.start) else clockTime(g.start)
}

/** A league's row: live games first, then those still to kick off (soonest first), full time last (user, 26 Nov). */
internal fun liveFirst(games: List<Game>) = games.sortedWith(compareBy({ when (it.state) { GameState.IN -> 0; GameState.PRE -> 1; GameState.POST -> 2 } }, { it.start }))

@Composable
internal fun GameCard(g: Game, favTeams: Set<String>, modifier: Modifier = Modifier, day: java.time.LocalDate? = null, width: androidx.compose.ui.unit.Dp = 206.dp, onFocused: () -> Unit = {}, onClick: () -> Unit) =
    FocusBox(onClick, modifier, onFocused) { f ->
        val colors = LocalTheme.current.colors
        val fg = if (f) colors.onFocus else Color.White
        Row(Modifier.size(width, 56.dp).clip(RoundedCornerShape(10.dp)).background(if (f) Color.White else colors.cell).padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (g.race != null) RaceCardLines(g, fg)
                else for (side in listOf(g.home, g.away)) Row(verticalAlignment = Alignment.CenterVertically) {
                    Badge(side.logo, 16.dp)
                    val fav = teamKey(g.league.sport, side.id) in favTeams
                    Text(side.short, fontSize = 12.sp, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontWeight = if (fav) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.padding(start = 6.dp).weight(1f))
                    if (g.state != GameState.PRE) Text(side.score.orEmpty(), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = fg, maxLines = 1)
                }
            }
            Text(status(g, day), fontSize = 10.sp, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.End,
                color = when { g.state == GameState.IN -> if (f) Color(0xFFC62828) else colors.accent; f -> colors.onFocus; else -> colors.muted },
                fontWeight = if (g.state == GameState.IN) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.padding(start = 6.dp).width(44.dp))
        }
    }

@Composable
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun SportsScreen(vm: SportsViewModel, live: LiveViewModel, nav: @Composable () -> Unit, onFull: (ChannelEntity) -> Unit, onMatch: (Game) -> Unit) {
    val s by vm.settings.collectAsState()
    val page by vm.page.collectAsState()
    val sport = vm.sport(s)
    var screen by remember { mutableStateOf(Size.Zero) }
    var pillsAfterBack by remember { mutableStateOf(false) }
    BackHandler(enabled = page != SportsPage.GAMES) { vm.page.value = SportsPage.GAMES; pillsAfterBack = true }
    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides ScrollIfNeeded) {
    Box(Modifier.sportsBackground { screen = it }) {
        Box(Modifier.padding(start = 24.dp, top = 22.dp)) { nav() }
        // Down from the nav lands on the open page's pill, not on whichever pill is under the SportsHub tab.
        val pageFocus = remember { FocusRequester() }
        @Composable fun PagePill(res: Int, p: SportsPage) =
            Pill(stringResource(res), if (page == p) Modifier.focusRequester(pageFocus) else Modifier, selected = page == p) { vm.page.value = p }
        // Above the pages: the sport drop-down opens over them.
        Row(Modifier.zIndex(1f).padding(start = 24.dp, top = 62.dp).focusProperties { enter = { pageFocus } }.focusGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // The sport first: every page below is for it (the open page stays, now for the sport picked).
            val sports = Sport.entries.filter { it.key in s.sports }
            DropDown(stringResource(R.string.sports_change_sport, sportName(sport)), sports.map { sportName(it) }, sports.indexOf(sport)) { i ->
                vm.update { it.copy(sportsSport = sports[i].key) }
            }
            PagePill(R.string.sports_games, SportsPage.GAMES)
            PagePill(R.string.sports_fav_teams, SportsPage.TEAMS)
            PagePill(R.string.sports_followed_leagues, SportsPage.LEAGUES)
            PagePill(R.string.sports_standings, SportsPage.STANDINGS)
            PagePill(R.string.sports_settings, SportsPage.SETTINGS)
        }
        // Opened from the nav (whose tab left composition with the old screen, taking focus with it): start on the page pills.
        LaunchedEffect(Unit) { if (!vm.returning) { androidx.compose.runtime.withFrameNanos { }; runCatching { pageFocus.requestFocus() } } }
        // Back from a page: focus its row of pills (the page's own items have gone).
        LaunchedEffect(page) { if (pillsAfterBack) { pillsAfterBack = false; androidx.compose.runtime.withFrameNanos { }; runCatching { pageFocus.requestFocus() } } }
        when (page) {
            SportsPage.GAMES -> GamesPage(vm, live, s, sport, screen, onFull, onMatch)
            SportsPage.TEAMS -> TeamsPage(vm, s, sport)
            SportsPage.LEAGUES -> LeaguesPage(vm, s, sport)
            SportsPage.STANDINGS -> StandingsPage(vm, s, sport)
            SportsPage.SETTINGS -> Column(Modifier.padding(start = 24.dp, top = 104.dp, bottom = 12.dp).width(620.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)) { SportsSettingsRows(s, vm::update) }
        }
    }
}
}

/** SportsHub settings: here and in Settings › SportsHub. */
@Composable
fun SportsSettingsRows(s: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit, first: Modifier = Modifier) {
    SwitchRow(stringResource(R.string.sports_show_tab), stringResource(R.string.sports_show_tab_expl), s.sportsTab, first) { on -> update { it.copy(sportsTab = on) } }
    SwitchRow(stringResource(R.string.sports_preview_fade), stringResource(R.string.sports_preview_fade_expl), s.sportsPreviewFade) { on -> update { it.copy(sportsPreviewFade = on) } }
    SwitchRow(stringResource(R.string.sports_team_results), stringResource(R.string.sports_team_results_expl), s.sportsTeamResults) { on -> update { it.copy(sportsTeamResults = on) } }
    SwitchRow(stringResource(R.string.sports_focus_preview), stringResource(R.string.sports_focus_preview_expl), s.sportsFocusPreview) { on -> update { it.copy(sportsFocusPreview = on) } }
    Text(stringResource(R.string.sports_sports_title), fontSize = 14.sp, color = LocalTheme.current.colors.muted, modifier = Modifier.padding(top = 10.dp, start = 4.dp))
    for (sp in Sport.entries) {
        val on = sp.key in s.sports
        // The last sport can't be switched off: Change sport needs one.
        SwitchRow(sportName(sp), stringResource(R.string.sports_sport_shown_expl), on, enabled = !on || s.sports.size > 1) { v ->
            update { it.copy(sports = if (v) it.sports + sp.key else it.sports - sp.key) }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.GamesPage(vm: SportsViewModel, live: LiveViewModel, s: AppSettings, sport: Sport, screen: Size, onFull: (ChannelEntity) -> Unit, onMatch: (Game) -> Unit) {
    val offset by vm.dayOffset.collectAsState()
    val fetched by vm.games.collectAsState()
    val failed by vm.failed.collectAsState()
    val pane by vm.pane.collectAsState()
    val lineups by vm.lineupsOut.collectAsState()
    val strip by live.strip.collectAsState()
    val colors = LocalTheme.current.colors
    LaunchedEffect(sport, offset) { vm.poll(sport, offset, strip) } // stops when the page closes: no calls off screen
    // The channel search index (47k names) builds while ESPN answers, not after it on the first lookup.
    LaunchedEffect(strip) { vm.warm(strip) }
    LaunchedEffect(sport) { if (sport == Sport.F1) vm.loadDriverTeams() }
    val driverTeams by vm.driverTeams.collectAsState()
    // Day strip
    Row(Modifier.padding(start = 24.dp, top = 98.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        for (d in 0..4) {
            val label = when (d) {
                0 -> stringResource(R.string.sports_today); 1 -> stringResource(R.string.sports_tomorrow)
                else -> dayDate(System.currentTimeMillis() + d * 86_400_000L)
            }
            Pill(label, selected = d == offset) { vm.dayOffset.value = d }
        }
        fetched?.takeIf { it.stale }?.let {
            Text(stringResource(R.string.sports_updated, clockTime(it.updatedAt)), fontSize = 11.sp, color = colors.muted, modifier = Modifier.padding(start = 8.dp))
        }
    }
    val all = fetched?.value.orEmpty()
    // Top box: favourite teams' games only. Every other game is below, favourite leagues first (★) (user, 26 Nov).
    val heroes = remember(all, s.sportsTeams, s.sportsTeamResults) { vm.heroes(all, s) }
    val rest = remember(all, heroes) { all.filter { it !in heroes } }
    val favTeams = remember(s.sportsTeams) { s.sportsTeams.map(::favouriteTeamKey).toSet() }
    val back = remember { FocusRequester() }
    val paneFocus = remember { FocusRequester() }
    var toPane by remember { mutableStateOf<String?>(null) }
    // The card OK was pressed on (no followed games): Down from Watch full screen returns to it.
    val fromCard = remember { FocusRequester() }
    var cameFrom by remember { mutableStateOf<String?>(null) }
    val favScroll = rememberScrollState()
    var heroFocused by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // A card held in focus for a moment previews its game if a channel shows it now (no Preview buttons).
    var dwell by remember { mutableStateOf<Game?>(null) }
    // Off (Settings › SportsHub › Preview on focus): only the hold-OK menu's Preview changes the preview.
    LaunchedEffect(dwell) { dwell?.takeIf { s.sportsFocusPreview }?.let { kotlinx.coroutines.delay(250); vm.focusPreview(it, strip) } } // long enough to scroll past a card

    // Hold OK on a card: Preview, Stats, and each team in or out of the favourites.
    var menuGame by remember { mutableStateOf<Game?>(null) }
    menuGame?.let { g ->
        val close = { menuGame = null }
        org.imdtv.iptvtv.ui.ContextMenu(if (g.race != null) raceTitle(g) else "${g.home.short} v ${g.away.short}", buildList {
            add(stringResource(R.string.sports_preview) to { close(); vm.preview(g, strip) })
            add(stringResource(R.string.sports_stats) to { close(); onMatch(g) })
            // F1: both "sides" are the Grand Prix; teams are followed on Favourite teams.
            if (g.race == null) for (side in listOf(g.home, g.away)) {
                val key = teamKey(sport, side.id)
                if (key in favTeams) add(stringResource(R.string.sports_fav_remove, side.name) to { close(); vm.update { st -> st.copy(sportsTeams = st.sportsTeams.filterNot { favouriteTeamKey(it) == key }.toSet()) } })
                else add(stringResource(R.string.sports_fav_add, side.name) to { close(); vm.update { st -> st.copy(sportsTeams = st.sportsTeams + favouriteKey(sport, side.id, side.name)) } })
            }
        }, onDismiss = close)
    }
    @Composable fun Card(g: Game, width: androidx.compose.ui.unit.Dp = 206.dp, onFocused: () -> Unit = { dwell = g }) =
        GameCard(g, favTeams, (if (g.id == vm.lastFocusedGame) Modifier.focusRequester(back) else Modifier)
            .then(if (g.id == cameFrom) Modifier.focusRequester(fromCard) else Modifier).onLongOk { menuGame = g }, vm.day(), width,
            onFocused = { vm.lastFocusedGame = g.id; onFocused() }) {
            // No followed games: OK puts the game in the top card and moves to Watch full screen (Stats beside it; Down comes back).
            vm.preview(g, strip); toPane = g.id; cameFrom = g.id
        }

    // Favourites box: the whole width, the preview inside it on the right.
    val boxH = 212.dp
    val previewH = boxH - 20.dp
    val previewW = previewH * 16 / 9
    Box(Modifier.padding(start = 24.dp, top = 136.dp, end = 24.dp).height(boxH).fillMaxWidth().panel()) {
        Box(Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = previewW + 20.dp).fillMaxSize()) {
            when {
                fetched == null && !failed -> Spinner(Modifier.align(Alignment.Center), 20.dp, colors.muted)
                failed -> Text(stringResource(R.string.sports_unavailable), fontSize = 13.sp, color = colors.muted, modifier = Modifier.align(Alignment.Center))
                // Nothing followed on this day: the game being browsed below (the preview's), else one on now, as the big card.
                heroes.isEmpty() -> (pane?.game ?: all.firstOrNull { it.state == GameState.IN } ?: all.firstOrNull())?.let { g ->
                    Hero(g, favTeams, lineups[g.id] == true, pane?.takeIf { it.game.id == g.id }?.tv, Modifier.height(previewH).fillMaxWidth(),
                        emptyList(), Modifier, onFocused = { heroFocused = it }) { onMatch(g) }
                } ?: Text(stringResource(R.string.sports_no_favourites), fontSize = 12.sp, color = colors.muted, modifier = Modifier.align(Alignment.Center).padding(horizontal = 20.dp))
                // A favourite team playing fills the box; the followed leagues' other games are further down (scroll).
                // A plain scrolling column, not a lazy one: everything stays composed, so focus moving back up to a hero
                // can't land on an item that's being disposed (it jumped on to the Sport pill).
                else -> Column(Modifier.fillMaxSize().verticalScroll(favScroll), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    heroes.forEach { g ->
                        androidx.compose.runtime.key("h" + g.id) {
                            Hero(g, favTeams, lineups[g.id] == true, pane?.takeIf { it.game.id == g.id }?.tv, Modifier.height(previewH).fillMaxWidth(),
                                if (g.race != null) followedDrivers(g, s.sportsTeams, driverTeams) else emptyList(),
                                if (g.id == vm.lastFocusedGame) Modifier.focusRequester(back) else Modifier,
                                onFocused = { on ->
                                    heroFocused = on
                                    if (on) { vm.lastFocusedGame = g.id; dwell = null; if (s.sportsFocusPreview) vm.focusHero(g, strip) }
                                }) { onMatch(g) }
                        }
                    }
                }
            }
            // More games below: only while the favourite team's card has focus.
            if (heroFocused && heroes.size > 1) Text("▾", fontSize = 16.sp, color = Color.White, modifier = Modifier.align(Alignment.BottomCenter).offset(y = 9.dp)
                .clip(RoundedCornerShape(8.dp)).background(Color(0x33FFFFFF)).padding(horizontal = 14.dp))
        }
        // Preview pane: the channel showing the game, else its score card. OK = full screen.
        val tv = pane?.tv?.takeIf { it.liveAt(System.currentTimeMillis()) }
        LaunchedEffect(tv?.ch?.id) { if (tv != null) live.preview(tv.ch, ask = false) else live.player.stop() }
        val fade = s.sportsPreviewFade && LocalGlassBackdrop.current == null // Liquid: framed, like its guide
        LaunchedEffect(toPane, pane?.game?.id) { if (toPane != null && pane?.game?.id == toPane) { toPane = null; runCatching { paneFocus.requestFocus() } } }
        FocusBox(onClick = { tv?.let { onFull(it.ch) } ?: pane?.game?.let(onMatch) },
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp).size(previewW, previewH).focusRequester(paneFocus)
                .focusProperties { canFocus = pane != null }
                // Unhandled (card scrolled away): the usual Down.
                .onPreviewKeyEvent { e -> e.type == KeyEventType.KeyDown && e.key == Key.DirectionDown && cameFrom != null && runCatching { fromCard.requestFocus() }.isSuccess }) { f ->
            Box(Modifier.fillMaxSize()) {
                if (tv != null) {
                    Preview(live, visible = true, screen = screen, edge = if (fade) PreviewEdge.NONE else PreviewEdge.RIM, modifier = Modifier.fillMaxSize())
                    if (fade) PanelFade(screen)
                } else pane?.game?.let { ScoreCard(it, Modifier.fillMaxSize()) }
                val line = when {
                    tv != null -> stringResource(R.string.sports_watch_full) // the channel's name meant little here (user, 2026-11-25)
                    pane?.looked == true && pane?.tv != null -> stringResource(R.string.sports_on_channel_at, pane!!.tv!!.ch.name, clockTime(pane!!.tv!!.p.start))
                    pane?.looked == true -> stringResource(R.string.sports_not_on_tv)
                    else -> null
                }
                if (line != null) Text(line, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (f) Color.Black else Color.White,
                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).clip(RoundedCornerShape(10.dp)).background(if (f) Color.White else Color(0x59000000))
                        .padding(horizontal = 10.dp, vertical = 5.dp))
                else if (f) Box(Modifier.fillMaxSize().border(2.dp, Color.White, RoundedCornerShape(16.dp)))
            }
        }
    }

    // All matches: league sections; only this part scrolls, so the preview stays put.
    Box(Modifier.padding(start = 24.dp, top = 356.dp, end = 24.dp, bottom = 12.dp).fillMaxSize().panel().padding(horizontal = 12.dp, vertical = 6.dp)) {
        if (fetched != null && rest.isEmpty()) Text(stringResource(if (all.isEmpty()) R.string.sports_no_games else R.string.sports_no_other_games), fontSize = 12.sp,
            color = colors.muted, modifier = Modifier.align(Alignment.Center))
        val sections = remember(rest, s.sportsLeagues) { rest.groupBy { it.leagueName }.toList().map { (name, games) -> name to liveFirst(games) }
            .sortedBy { it.second.first().league.id !in s.sportsLeagues } }
        // Each league is exactly 76 dp (16 heading + 4 + 56 cards) and two plus their gap fill the panel, so no row is cut off at the bottom.
        // Exactly four cards across: with scroll-only-if-needed, moving along shifts by whole cards, none half off the edge.
        androidx.compose.foundation.layout.BoxWithConstraints {
        val cardW = (maxWidth - 6.dp * 3) / 4
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(sections, key = { it.first }) { (name, games) ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(16.dp).padding(start = 2.dp)) {
                        Badge(games.first().leagueLogo, 14.dp)
                        Text((if (games.first().league.id in s.sportsLeagues) "★ " else "") + name, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Box {
                        val row = androidx.compose.foundation.lazy.rememberLazyListState()
                        LazyRow(state = row, horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(games, key = { it.id }) { Card(it, cardW) } }
                        // More games off to the right.
                        if (row.canScrollForward) Text("▸", fontSize = 16.sp, color = Color.White, modifier = Modifier.align(Alignment.CenterEnd).offset(x = 9.dp)
                            .clip(RoundedCornerShape(8.dp)).background(Color(0x59000000)).padding(horizontal = 4.dp, vertical = 10.dp))
                    }
                }
            }
        }
        }
    }
    // Back from a match page or full screen: the card it was opened from (only then: opening the tab leaves focus on the pills).
    LaunchedEffect(fetched != null) { if (fetched != null && vm.returning) { vm.returning = false; kotlinx.coroutines.delay(50); runCatching { back.requestFocus() } } }
}

/** Fades the video's edges into the favourites box: the box's own look (background + its tint) drawn over them, soft towards the middle. */
@Composable
private fun PanelFade(screen: Size) {
    val colors = LocalTheme.current.colors
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }.drawWithCache {
        val w = size.width.toInt().coerceAtLeast(1); val h = size.height.toInt().coerceAtLeast(1)
        val bg = backgroundBitmap(colors, this, size, origin, screen)
        fun edge(brush: androidx.compose.ui.graphics.Brush): androidx.compose.ui.graphics.ImageBitmap {
            val img = androidx.compose.ui.graphics.ImageBitmap(w, h)
            androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(this, layoutDirection, androidx.compose.ui.graphics.Canvas(img), size) {
                drawImage(bg); drawRect(PANEL_TINT)
                drawRect(brush, blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
            }
            return img
        }
        val k = Color.Black; val t = Color.Transparent
        val edges = listOf(
            edge(androidx.compose.ui.graphics.Brush.horizontalGradient(0f to k, .35f to t)), edge(androidx.compose.ui.graphics.Brush.horizontalGradient(.9f to t, 1f to k)),
            edge(androidx.compose.ui.graphics.Brush.verticalGradient(0f to k, .15f to t)), edge(androidx.compose.ui.graphics.Brush.verticalGradient(.7f to t, 1f to k)),
        )
        onDrawBehind { edges.forEach { drawImage(it) } }
    })
}

/** When lineups come out: 75 min before kick-off in UEFA competitions, 60 elsewhere. */
internal fun lineupsAt(g: Game) = g.start - (if (g.league.slug.startsWith("uefa.")) 75 else 60) * 60_000L

/** The countdown text, ticking once a second on its own (so the rest of the box doesn't recompose). */
@Composable
private fun Countdown(to: Long, @androidx.annotation.StringRes res: Int, style: androidx.compose.ui.text.TextStyle, color: Color) {
    val now by produceState(System.currentTimeMillis(), to) { while (true) { value = System.currentTimeMillis(); kotlinx.coroutines.delay(1_000 - value % 1_000) } }
    val left = ((to - now) / 1000).coerceAtLeast(0)
    val text = if (left >= 86_400) "${left / 86_400}d ${left % 86_400 / 3600}h" else "%d:%02d:%02d".format(left / 3600, left % 3600 / 60, left % 60)
    Text(stringResource(res, text), style = style, color = color)
}

/** A favourite team's game: both badges large behind it (faded), the teams, a countdown to kick-off and to the lineups, Stats. */
@Composable
private fun Hero(g: Game, favTeams: Set<String>, lineupsOut: Boolean, tv: OnTv?, modifier: Modifier, drivers: List<String>, statsModifier: Modifier, onFocused: (Boolean) -> Unit, onStats: () -> Unit) {
    val colors = LocalTheme.current.colors
    val favHome = teamKey(g.league.sport, g.home.id) in favTeams
    val (fav, other) = if (favHome || teamKey(g.league.sport, g.away.id) !in favTeams) g.home to g.away else g.away to g.home
    // The whole card takes focus, so moving onto it scrolls all of it into view; OK = Stats.
    FocusBox(onStats, modifier.then(statsModifier).onFocusChanged { onFocused(it.isFocused) }) { f ->
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))) {
        // Badges behind: the favourite's big on the left, the opponent's on the right, both faded into the box.
        // Home badge on the left, away on the right, as the names read; the favourite's is the big, stronger one.
        @Composable fun Behind(side: org.imdtv.iptvtv.data.sports.Side, align: Alignment) {
            val big = side == fav
            Box(Modifier.align(align).padding(horizontal = 8.dp).size(if (big) 190.dp else 140.dp).graphicsLayer { alpha = if (big) .28f else .18f }) { Badge(side.logo, if (big) 190.dp else 140.dp) }
        }
        Behind(g.home, Alignment.CenterStart); Behind(g.away, Alignment.CenterEnd)
        Column(Modifier.align(Alignment.CenterStart).padding(start = 28.dp, end = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Badge(g.leagueLogo, 16.dp)
                Text(g.leagueName, fontSize = 11.sp, color = colors.muted, maxLines = 1, modifier = Modifier.padding(start = 6.dp))
            }
            if (g.race != null) {
                // F1: the Grand Prix and session, the followed teams' drivers (with places once it has run).
                Text(raceTitle(g), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (drivers.isNotEmpty()) Text(drivers.joinToString(" · "), fontSize = 13.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else Row(verticalAlignment = Alignment.CenterVertically) {
                // Home team first (as listed everywhere); the favourite larger and bold.
                // Neither followed (a game browsed with no favourites): both plain.
                @Composable fun Name(side: org.imdtv.iptvtv.data.sports.Side) = (side == fav && teamKey(g.league.sport, side.id) in favTeams).let { big ->
                    Text(side.name, fontSize = if (big) 22.sp else 18.sp,
                        fontWeight = if (big) FontWeight.Bold else FontWeight.Normal, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Name(g.home)
                Text(if (g.state == GameState.PRE) "  v  " else "  ${g.home.score.orEmpty()} – ${g.away.score.orEmpty()}  ", fontSize = 22.sp,
                    fontWeight = if (g.state == GameState.PRE) FontWeight.Normal else FontWeight.Bold, color = Color.White)
                Name(g.away)
            }
            val big = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium)
            val small = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
            when (g.state) {
                GameState.PRE -> if (g.race != null) Countdown(g.start, R.string.sports_starts_in, big, Color.White) else {
                    Countdown(g.start, R.string.sports_kickoff_in, big, Color.White)
                    when {
                        lineupsOut -> Text(stringResource(R.string.sports_lineups_out), style = small, color = colors.accent)
                        System.currentTimeMillis() < lineupsAt(g) -> Countdown(lineupsAt(g), R.string.sports_lineups_in, small, colors.muted)
                        else -> Text(stringResource(R.string.sports_lineups_due), style = small, color = colors.muted)
                    }
                }
                else -> Text(g.detail, style = big, color = if (g.state == GameState.IN) colors.accent else colors.muted)
            }
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.sports_stats), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if (f) Color.Black else colors.muted,
                    modifier = Modifier.clip(RoundedCornerShape(15.dp)).background(if (f) Color.White else Color(0x14FFFFFF)).padding(horizontal = 14.dp, vertical = 6.dp))
                tv?.let { Text("  " + stringResource(R.string.sports_on_channel, it.ch.name) + if (it.liveAt(System.currentTimeMillis())) "" else " · " + clockTime(it.p.start),
                    fontSize = 11.sp, color = colors.muted, maxLines = 1) }
            }
        }
    }
    }
}

/** The pane without a channel: both badges, the score (or kick-off) and the league. */
@Composable
internal fun ScoreCard(g: Game, modifier: Modifier) {
    if (g.race != null) return RaceScoreCard(g, modifier)
    val colors = LocalTheme.current.colors
    Column(modifier.panel().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Badge(g.leagueLogo, 16.dp)
            Text(g.leagueName, fontSize = 11.sp, color = colors.muted, maxLines = 1, modifier = Modifier.padding(start = 6.dp))
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            for ((i, side) in listOf(g.home, g.away).withIndex()) {
                Column(Modifier.width(110.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Badge(side.logo, 46.dp)
                    Text(side.short, fontSize = 12.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                }
                if (i == 0) Text(if (g.state == GameState.PRE) clockTime(g.start) else "${g.home.score.orEmpty()} – ${g.away.score.orEmpty()}",
                    fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.width(80.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        if (g.state != GameState.PRE) Text(g.detail, fontSize = 11.sp, color = if (g.state == GameState.IN) colors.accent else colors.muted, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun TeamsPage(vm: SportsViewModel, s: AppSettings, sport: Sport) {
    val colors = LocalTheme.current.colors
    // A cup's teams are its leagues' teams (and a long list of lower-league ones).
    val leagues = vm.leagues(sport).filter { !it.cup }.ifEmpty { vm.leagues(sport) }
    var league by remember(sport) { mutableStateOf(leagues.firstOrNull()) }
    val teams by produceState<List<Side>?>(null, league) { value = null; value = league?.let { vm.teams(it) } ?: emptyList() }
    val mine = s.sportsTeams.filter { it.startsWith(sport.key + ":") }
    // F1: each team's drivers under its name.
    LaunchedEffect(sport) { if (sport == Sport.F1) vm.loadDriverTeams() }
    val driverTeams by vm.driverTeams.collectAsState()
    Row(Modifier.padding(start = 24.dp, top = 104.dp, end = 24.dp, bottom = 12.dp)) {
        LazyColumn(Modifier.width(210.dp).fillMaxSize().panel().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(leagues, key = { it.id }) { l -> Pill(l.name, Modifier.fillMaxWidth(), selected = l == league) { league = l } }
        }
        Column(Modifier.padding(start = 12.dp).fillMaxSize().panel().padding(12.dp)) {
            Text(stringResource(R.string.sports_your_teams), fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium)
            if (mine.isEmpty()) Text(stringResource(R.string.sports_no_teams), fontSize = 12.sp, color = colors.muted, modifier = Modifier.padding(vertical = 6.dp))
            else LazyRow(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(mine, key = { it }) { fav -> Pill("★ " + favouriteName(fav), selected = true) { vm.update { it.copy(sportsTeams = it.sportsTeams - fav) } } }
            }
            when (val list = teams) {
                null -> Spinner(Modifier.padding(20.dp), 20.dp, colors.muted)
                else -> if (list.isEmpty()) Text(stringResource(R.string.sports_unavailable), fontSize = 12.sp, color = colors.muted)
                else LazyVerticalGrid(GridCells.Fixed(3), verticalArrangement = Arrangement.spacedBy(4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(list.sortedBy { it.name }, key = { it.id }) { t ->
                        val key = favouriteKey(sport, t.id, t.name)
                        val on = s.sportsTeams.any { favouriteTeamKey(it) == teamKey(sport, t.id) }
                        FocusBox({ vm.update { st -> st.copy(sportsTeams = if (on) st.sportsTeams.filterNot { favouriteTeamKey(it) == teamKey(sport, t.id) }.toSet() else st.sportsTeams + key) } }) { f ->
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (f) Color.White else colors.cell).padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Badge(t.logo, 20.dp)
                                Column(Modifier.padding(start = 6.dp).weight(1f)) {
                                    Text(t.name, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (f) colors.onFocus else Color.White)
                                    if (sport == Sport.F1) driverTeams.filter { org.imdtv.iptvtv.data.sports.sameTeam(it.team, t.name) }.takeIf { it.isNotEmpty() }?.let { ds ->
                                        Text(ds.joinToString(" · ") { d -> listOfNotNull(d.number?.let { "#$it" }, d.name).joinToString(" ") }, fontSize = 10.sp, maxLines = 1,
                                            overflow = TextOverflow.Ellipsis, color = if (f) Color(0xFF555555) else colors.muted)
                                    }
                                }
                                Text(if (on) "★" else "☆", fontSize = 14.sp, color = if (on) colors.accent else if (f) colors.onFocus else colors.muted)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Favourite competitions of the chosen sport, per region, two to a row. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun LeaguesPage(vm: SportsViewModel, s: AppSettings, sport: Sport) {
    val colors = LocalTheme.current.colors
    val comps = LEAGUES.filter { it.sport == sport }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // A plain scrolling column, not a lazy one: every row stays composed, so Up always finds the row above
    // (the lazy list's off-screen rows let Up jump out to the SportsHub pills).
    Column(Modifier.padding(start = 24.dp, top = 104.dp, end = 24.dp, bottom = 12.dp).fillMaxSize().panel().padding(12.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for ((region, list) in comps.groupBy { it.region }) androidx.compose.runtime.key(region) {
            // Focus on a region's first row brings its heading into view too.
            val block = remember { BringIntoViewRequester() }
            Column(Modifier.bringIntoViewRequester(block), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (region != org.imdtv.iptvtv.data.sports.Region.NONE) Text(regionName(region), fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold)
                list.chunked(2).forEachIndexed { row, line ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        line.forEach { l ->
                            val on = l.id in s.sportsLeagues
                            FocusBox({ vm.update { it.copy(sportsLeagues = if (on) it.sportsLeagues - l.id else it.sportsLeagues + l.id) } }, Modifier.weight(1f),
                                onFocused = { if (row == 0) scope.launch { block.bringIntoView() } }) { f ->
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (f) Color.White else colors.cell).padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Text(l.name, fontSize = 13.sp, color = if (f) colors.onFocus else Color.White, maxLines = 1, modifier = Modifier.weight(1f))
                                    org.imdtv.iptvtv.ui.settings.Toggle(on, f)
                                }
                            }
                        }
                        if (line.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * A button that drops [options] down under itself, over what's below, on the same screen; OK picks one, Back closes it.
 * Focus stays in the list while it's open. Put it above later siblings (zIndex) so the list draws over them.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun DropDown(label: String, options: List<String>, selected: Int, modifier: Modifier = Modifier, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val button = remember { FocusRequester() }
    // The list keeps focus in while open; on close it lets go first, so focus can move back to the button (the trap refused
    // it, and the closing list took focus away with it, to the top menu).
    val leaving = remember { BooleanArray(1) }
    Box {
        Pill(label, modifier.focusRequester(button), selected = open) { open = !open }
        if (open) {
            val current = remember { FocusRequester() }
            val close = { leaving[0] = true; runCatching { button.requestFocus() }; open = false }
            BackHandler(onBack = close)
            // Takes no room: drawn below the button, over the page.
            Column(Modifier.layout { m, c -> val p = m.measure(c.copy(minWidth = 0, minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity))
                    layout(0, 0) { p.place(0, 38.dp.roundToPx()) } }
                .width(280.dp).heightIn(max = 360.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xF21A1C24)).padding(6.dp)
                .focusProperties { exit = { if (leaving[0]) FocusRequester.Default else FocusRequester.Cancel } }.focusGroup().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                options.forEachIndexed { i, o ->
                    Pill(o, (if (i == selected.coerceAtLeast(0)) Modifier.focusRequester(current) else Modifier).fillMaxWidth(), selected = i == selected) { close(); onPick(i) }
                }
            }
            LaunchedEffect(Unit) { leaving[0] = false; runCatching { current.requestFocus() } }
        }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun StandingsPage(vm: SportsViewModel, s: AppSettings, sport: Sport) {
    val colors = LocalTheme.current.colors
    val all = vm.leagues(sport).filter { it.sport != Sport.CRICKET && it.table }
    val leagues = all.filter { it.id in s.sportsLeagues }.ifEmpty { all }
    var league by remember(sport) { mutableStateOf(leagues.firstOrNull()) }
    val tables by produceState<org.imdtv.iptvtv.data.sports.Fetched<List<Table>>?>(null, league) { value = null; value = league?.let { vm.standings(it) } }
    val football = sport == Sport.FOOTBALL
    val form by produceState(emptyMap<String, String>(), league, tables) {
        value = emptyMap()
        val ids = tables?.value?.flatMap { t -> t.rows.map { it.team.id } }?.toSet() ?: return@produceState
        if (football) value = league?.let { vm.form(it, ids) }.orEmpty()
    }
    val favTeams = s.sportsTeams.map(::favouriteTeamKey).toSet()
    val picker = remember { FocusRequester() }
    // Down from the pills lands on the competition button, not in the table.
    Column(Modifier.padding(start = 24.dp, top = 104.dp, end = 24.dp, bottom = 12.dp).focusProperties { enter = { picker } }.focusGroup()) {
        if (leagues.isEmpty()) { Text(stringResource(R.string.sports_no_tables), fontSize = 13.sp, color = colors.muted); return@Column }
        // One button for the competition: OK drops the list down over the table, on this screen.
        Box(Modifier.zIndex(1f)) { DropDown((league?.name ?: "") + " ▾", leagues.map { it.name }, leagues.indexOf(league), Modifier.focusRequester(picker)) { league = leagues[it] } }
        // Up from the table goes to the competition button (it went past it to the menu).
        Box(Modifier.padding(top = 10.dp).fillMaxSize().panel().padding(12.dp)
            .focusProperties { exit = { if (it == androidx.compose.ui.focus.FocusDirection.Up) picker else FocusRequester.Default } }.focusGroup()) {
            val t = tables
            // Each sport's own columns (ESPN stat names); football's headings are translated, the others are ESPN's own.
            val cols = org.imdtv.iptvtv.data.sports.tableColumns(sport)
            val footballHeads = listOf(R.string.sports_col_played, R.string.sports_col_won, R.string.sports_col_drawn, R.string.sports_col_lost,
                R.string.sports_col_gf, R.string.sports_col_ga, R.string.sports_col_gd, R.string.sports_col_pts).map { stringResource(it) }
            val heads = if (football) footballHeads else cols.map { c -> t?.value?.firstNotNullOfOrNull { it.abbr[c] } ?: c }
            val bold = cols.indexOfFirst { it == "points" || it == "championshipPts" || it == "winPercent" }
            when {
                t == null -> Spinner(Modifier.align(Alignment.Center), 20.dp, colors.muted)
                t.value.isEmpty() -> Text(stringResource(R.string.sports_no_tables), fontSize = 13.sp, color = colors.muted, modifier = Modifier.align(Alignment.Center))
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (t.stale) item { Text(stringResource(R.string.sports_updated, clockTime(t.updatedAt)), fontSize = 11.sp, color = colors.muted) }
                    for (table in t.value) {
                        item(key = "h" + table.name) {
                            Column {
                                if (t.value.size > 1) Text(table.name, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
                                TableLine("#", null, "", heads, header = true, form = if (football) stringResource(R.string.sports_last5) else null, bold = bold)
                            }
                        }
                        items(table.rows, key = { table.name + it.team.id }) { r ->
                            FocusBox({}) { f ->
                                TableLine(if (r.rank > 0) r.rank.toString() else "", r.team.logo, r.team.name, cols.map { r.stats[it].orEmpty() },
                                    focused = f, fav = teamKey(sport, r.team.id) in favTeams, zone = r.zone?.let(::colour), form = if (football) form[r.team.id].orEmpty() else null, bold = bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One standings line, left-aligned (club, then narrow number columns, then the last five): a bar on the left in the place's
 * colour (European places, relegation). [form] oldest first: W/D/L as green ✓, grey –, red ✕; a header passes its "Last 5".
 */
@Composable
private fun TableLine(rank: String, logo: String?, name: String, cols: List<String>, header: Boolean = false, focused: Boolean = false, fav: Boolean = false,
                      zone: Color? = null, form: String? = null, bold: Int = cols.lastIndex) {
    val colors = LocalTheme.current.colors
    val fg = when { focused -> colors.onFocus; header -> colors.muted; fav -> colors.accent; else -> Color.White }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (focused) Color.White else if (header) Color.Transparent else Color(0x0DFFFFFF))
        .padding(end = 10.dp, top = if (header) 2.dp else 4.dp, bottom = if (header) 2.dp else 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(20.dp).background(zone ?: Color.Transparent))
        Text(rank, fontSize = 12.sp, color = fg, textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.padding(end = 10.dp).width(28.dp))
        if (!header) Badge(logo, 18.dp) else Spacer(Modifier.width(18.dp))
        Text(name, fontSize = 12.sp, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (fav) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(start = 8.dp).width(190.dp))
        cols.forEachIndexed { i, c ->
            Text(c, fontSize = 12.sp, color = fg, fontWeight = if (i == bold) FontWeight.Bold else FontWeight.Normal,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.width(40.dp))
        }
        if (form != null) Row(Modifier.padding(start = 16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (header) Text(form, fontSize = 12.sp, color = fg)
            else form.forEach { r ->
                val (mark, bg) = when (r) { 'W' -> "✓" to Color(0xFF2E7D32); 'L' -> "✕" to Color(0xFFC62828); else -> "–" to Color(0xFF757575) }
                Text(mark, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.size(16.dp).clip(androidx.compose.foundation.shape.CircleShape).background(bg))
            }
        }
    }
}
