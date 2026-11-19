package org.imdtv.iptvtv.ui.vod

import org.imdtv.iptvtv.ui.theme.LocalTheme
import org.imdtv.iptvtv.ui.theme.themeBackground

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import org.imdtv.iptvtv.data.db.CategoryEntity
import org.imdtv.iptvtv.data.model.Episode
import org.imdtv.iptvtv.data.model.Kind
import org.imdtv.iptvtv.player.nextEpisode
import org.imdtv.iptvtv.player.resumeAt
import org.imdtv.iptvtv.ui.ContextMenu
import org.imdtv.iptvtv.ui.onLongOk
import org.imdtv.iptvtv.ui.FocusBox
import org.imdtv.iptvtv.ui.duration
import org.imdtv.iptvtv.ui.live.RailCategory

private val leftKey: (androidx.compose.ui.input.key.KeyEvent) -> Boolean = { it.type == KeyEventType.KeyDown && it.key == Key.DirectionLeft }

/** Movies/Series home (mockup 02 screen 1): backdrop + hero for the focused item, rows per category, ← opens the rail. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun VodHomeScreen(vm: VodViewModel, nav: @Composable () -> Unit, onOpen: (VodItem) -> Unit) {
    val kind by vm.kind.collectAsState()
    val cats by vm.categories.collectAsState()
    val favs by vm.favourites.collectAsState()
    val gridCat by vm.gridCategory.collectAsState()
    val grid by vm.grid.collectAsState()
    val focused by vm.focused.collectAsState()
    val details by vm.details.collectAsState()
    val query by vm.query.collectAsState()
    val matches by vm.matches.collectAsState()
    var railOpen by remember { mutableStateOf(false) }
    val firstItem = remember { FocusRequester() }
    val railFocus = remember { FocusRequester() }
    var refocus by remember { mutableStateOf(0) }
    // Rows load asynchronously, so the first poster grabs focus itself once it exists (per tab / grid).
    val wantFirst = remember(kind, gridCat?.id) { mutableStateOf(true) }

    LaunchedEffect(focused?.id) { val f = focused ?: return@LaunchedEffect; delay(500); vm.loadDetail(f) }
    LaunchedEffect(refocus, railOpen, gridCat?.id, kind) {
        withFrameNanos { }
        runCatching { if (railOpen) railFocus.requestFocus() else firstItem.requestFocus() }
    }
    val claim: @Composable () -> Unit = {
        LaunchedEffect(Unit) { if (wantFirst.value) { withFrameNanos { }; runCatching { firstItem.requestFocus(); wantFirst.value = false } } }
    }

    Box(Modifier.fillMaxSize().themeBackground(LocalTheme.current.colors.vodBg)) {
        Backdrop(focused?.let { details[it.id]?.backdrop ?: it.poster })
        Box(Modifier.padding(start = 50.dp, top = 22.dp)) { nav() }
        // On the search bar's line: the nav's clock and ping take the top right.
        Row(Modifier.align(Alignment.TopEnd).padding(top = 66.dp, end = 50.dp), verticalAlignment = Alignment.CenterVertically) {
            if (vm.updating.collectAsState().value) {
                org.imdtv.iptvtv.ui.Spinner(size = 14.dp)
                Text("Updating ${if (kind == Kind.MOVIE) "movies" else "series"}…", fontSize = 11.sp, color = Color.White,
                    modifier = Modifier.padding(start = 6.dp, end = 16.dp))
            }
            Text("Category", fontSize = 11.sp, color = LocalTheme.current.colors.muted)
            Text("◀ ${gridCat?.name ?: if (kind == Kind.MOVIE) "All Movies" else "All Series"}", fontSize = 11.sp, color = Color.White,
                fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 4.dp).clip(RoundedCornerShape(10.dp))
                    .background(Color(0x1AFFFFFF)).padding(horizontal = 8.dp, vertical = 3.dp))
        }
        // In-category search sits just above the title (search spec); it searches what the rows/grid show.
        LaunchedEffect(kind) { vm.warmSearch() }
        org.imdtv.iptvtv.ui.search.SearchBar(query, { vm.query.value = it },
            "Search ${gridCat?.name ?: if (kind == Kind.MOVIE) "All Movies" else "All Series"}…",
            Modifier.padding(start = 50.dp, top = 62.dp).width(410.dp))
        focused?.let { Hero(it, details[it.id], Modifier.padding(start = 50.dp, top = 100.dp).width(410.dp), titleLines = 1) }

        val openRail = { railOpen = true }
        val rowToTop = with(androidx.compose.ui.platform.LocalDensity.current) { remember { rowToTop(6.dp.toPx()) } }
        Box(Modifier.padding(start = 50.dp, top = 260.dp).fillMaxSize()) {
            val found = matches
            if (found != null) {
                if (found.isEmpty()) Text("No matches", fontSize = 15.sp, color = LocalTheme.current.colors.muted)
                else androidx.compose.runtime.CompositionLocalProvider(androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides rowToTop) { LazyVerticalGrid(GridCells.Fixed(7), verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 8.dp, top = 6.dp, end = 42.dp, bottom = 20.dp),
                    modifier = Modifier.fillMaxSize().offset(x = (-8).dp)) {
                    // Back from detail refocuses the opened match (the screen's first-item focus lands here).
                    val home = found.indexOfFirst { it.id == focused?.id }.coerceAtLeast(0)
                    itemsIndexed(found, key = { _, it -> it.id }) { i, item ->
                        Poster(item, Modifier.then(if (i == home) Modifier.focusRequester(firstItem) else Modifier).onPreviewKeyEvent { if (i % 7 == 0 && leftKey(it)) { openRail(); true } else false }, onFocused = { vm.focused.value = item }, onClick = { onOpen(item) })
                    }
                } }
            } else if (gridCat != null) {
                androidx.compose.runtime.CompositionLocalProvider(androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides rowToTop) { LazyVerticalGrid(GridCells.Fixed(7), verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 8.dp, top = 6.dp, end = 42.dp, bottom = 20.dp),
                    modifier = Modifier.fillMaxSize().offset(x = (-8).dp)) {
                    itemsIndexed(grid, key = { _, it -> it.id }) { i, item ->
                        if (i == 0) claim()
                        Poster(item, Modifier.then(if (i == 0) Modifier.focusRequester(firstItem) else Modifier)
                            .onPreviewKeyEvent { if (i % 7 == 0 && leftKey(it)) { openRail(); true } else false },
                            onFocused = { vm.focused.value = item }, onClick = { onOpen(item) })
                    }
                } }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    if (favs.isNotEmpty()) item(key = "fav") {
                        PosterRow("★ Favourites", favs, firstItem, vm, onOpen, openRail, claim)
                    }
                    itemsIndexed(cats, key = { _, c -> c.category.id }) { i, rc ->
                        val cont = rc.category.id == VodViewModel.CONTINUE_ID
                        val items by if (cont) vm.continueWatching.collectAsState()
                            else produceState<List<VodItem>?>(null, rc.category.id) { value = vm.inCategory(rc.category.id) }
                        val rows by vm.progress.collectAsState()
                        PosterRow(rc.category.name.ifEmpty { "Uncategorised" }, items, firstItem.takeIf { i == 0 && favs.isEmpty() }, vm, onOpen, openRail, claim,
                            progress = { if (cont) progressFraction(it, rows) else null })
                    }
                }
            }
        }
        if (railOpen) VodRail(cats, gridCat?.id, railFocus,
            onPick = { vm.gridCategory.value = it; railOpen = false },
            onClose = { railOpen = false; refocus++ })
    }
}

private const val PLACEHOLDERS = 6

/** Poster grids put the focused row at the top, whole; Android TV's default pivot left half a row above it. [padPx]: room for the focus scale. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun rowToTop(padPx: Float) = object : androidx.compose.foundation.gestures.BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float) = offset - padPx
}

@Composable
private fun PosterRow(title: String, items: List<VodItem>?, focus: FocusRequester?, vm: VodViewModel, onOpen: (VodItem) -> Unit, openRail: () -> Unit, claim: @Composable () -> Unit,
                      progress: (VodItem) -> Float? = { null }) {
    Column(Modifier.padding(bottom = 18.dp)) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.padding(bottom = 6.dp))
        // Padding leaves room for the focused poster's 1.06× scale and outline, which a LazyRow would clip.
        LazyRow(horizontalArrangement = Arrangement.spacedBy(11.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 8.dp, end = 50.dp, top = 6.dp, bottom = 6.dp),
            modifier = Modifier.padding(start = 0.dp).offset(x = (-8).dp)) {
            // While loading (null) the row shows focusable placeholders: an empty row has nothing to focus, so a fast
            // Down made the focus search lay out every remaining row in one key event (ANR). Keyed by position so the
            // placeholder that has focus becomes the real poster in the same slot and keeps it.
            itemsIndexed(items ?: List(PLACEHOLDERS) { null }, key = { i, _ -> i }) { i, item ->
                if (i == 0 && focus != null) claim()
                Poster(item, Modifier.then(if (i == 0 && focus != null) Modifier.focusRequester(focus) else Modifier)
                    .onPreviewKeyEvent { if (i == 0 && leftKey(it)) { openRail(); true } else false },
                    progress = item?.let(progress), onFocused = { item?.let { vm.focused.value = it } }, onClick = { item?.let(onOpen) })
            }
        }
    }
}

/** [item] null = a placeholder card while its row loads. */
@Composable
/** [progress] 0–1 draws a watched-so-far bar along the bottom. */
fun Poster(item: VodItem?, modifier: Modifier = Modifier, caption: Boolean = false, progress: Float? = null, onFocused: () -> Unit, onClick: () -> Unit) {
    FocusBox(onClick = onClick, onFocused = onFocused, modifier = modifier) { f -> Column(Modifier.width(116.dp)) {
        // A placeholder that already has focus becomes this item without a focus change: report it now.
        if (f && item != null) LaunchedEffect(item.id) { onFocused() }
        Box(
            Modifier.graphicsLayer { val s = if (f) 1.06f else 1f; scaleX = s; scaleY = s }
                .border(2.dp, if (f) Color.White else Color.Transparent, RoundedCornerShape(8.dp)).padding(3.dp)
                .size(110.dp, 165.dp).clip(RoundedCornerShape(6.dp)).background(LocalTheme.current.colors.vodCard),
            contentAlignment = Alignment.BottomStart,
        ) {
            if (item != null) {
                Text(item.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 3, modifier = Modifier.padding(7.dp))
                if (item.poster != null) AsyncImage(item.poster, item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (progress != null) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).background(Color(0x66FFFFFF))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(LocalTheme.current.colors.accent))
                }
            }
        }
        if (caption && item != null) Text(item.name, fontSize = 12.sp, lineHeight = 15.sp, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 3.dp, top = 6.dp))
    } }
}

@Composable
fun Backdrop(url: String?) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
        Box(Modifier.size(700.dp, 410.dp)) {
            if (url != null) AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to LocalTheme.current.colors.vodBg, .22f to LocalTheme.current.colors.vodBg.copy(alpha = .85f), .6f to LocalTheme.current.colors.vodBg.copy(alpha = .2f), 1f to Color.Transparent)))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(.45f to Color.Transparent, .75f to LocalTheme.current.colors.vodBg.copy(alpha = .6f), 1f to LocalTheme.current.colors.vodBg)))
        }
    }
}

@Composable
private fun Meta(item: VodItem, d: VodDetail?, extra: String? = null, resolution: String? = null) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 11.dp)) {
        item.year?.let { Text(it.toString(), fontSize = 12.sp, color = Color(0xFFDDDDDD)) }
        extra?.let { Text(it, fontSize = 12.sp, color = Color(0xFFDDDDDD)) }
        resolution?.let { Text(it, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
            modifier = Modifier.border(1.dp, Color(0xB3FFFFFF), RoundedCornerShape(3.dp)).padding(horizontal = 4.dp, vertical = 1.dp)) }
        d?.durationSecs?.takeIf { it > 0 }?.let { Text(duration(it * 1000L), fontSize = 12.sp, color = Color(0xFFDDDDDD)) }
        item.rating?.takeIf { it > 0 }?.let { Text("★ ${"%.1f".format(it)}", fontSize = 12.sp, color = LocalTheme.current.colors.accent) }
        d?.genre?.let { Text(it.replace(", ", " · "), fontSize = 12.sp, color = Color(0xFFDDDDDD), maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun Hero(item: VodItem, d: VodDetail?, modifier: Modifier, extra: String? = null, titleLines: Int = 2, resolution: String? = null) {
    Column(modifier) {
        Text(item.name, fontSize = 40.sp, lineHeight = 42.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = titleLines, overflow = TextOverflow.Ellipsis)
        Meta(item, d, extra, resolution)
        d?.plot?.let {
            Text(it, fontSize = 12.5.sp, lineHeight = 18.sp, color = Color(0xFFD6D6D6), maxLines = 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp))
        }
    }
}

@Composable
private fun VodRail(cats: List<RailCategory>, selectedId: Long?, focus: FocusRequester, onPick: (CategoryEntity?) -> Unit, onClose: () -> Unit) {
    val start = (cats.indexOfFirst { it.category.id == selectedId } + 1).coerceAtLeast(0)
    val state = rememberLazyListState(start)
    Column(
        Modifier.fillMaxHeight().width(260.dp).shadow(30.dp).background(Color(0xF515171D)).padding(horizontal = 20.dp, vertical = 35.dp)
            .onPreviewKeyEvent { e -> if (e.type == KeyEventType.KeyDown && (e.key == Key.Back || e.key == Key.DirectionLeft)) { onClose(); true } else false },
    ) {
        Text("CATEGORIES", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = LocalTheme.current.colors.muted, letterSpacing = 2.sp, modifier = Modifier.padding(bottom = 12.dp))
        LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            val all = listOf<RailCategory?>(null) + cats
            itemsIndexed(all, key = { _, c -> c?.category?.id ?: -1L }) { i, rc ->
                // Right picks, as on the Live rail; unhandled it moved focus behind the rail and left the rail open.
                FocusBox(onClick = { onPick(rc?.category) }, modifier = (if (i == start) Modifier.focusRequester(focus) else Modifier)
                    .onPreviewKeyEvent { e -> if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionRight) { onPick(rc?.category); true } else false }) { f ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(5.dp)).background(if (f) Color.White else Color.Transparent).padding(horizontal = 11.dp, vertical = 8.dp)) {
                        val fg = if (f) Color.Black else Color.White
                        Text(rc?.category?.name?.ifEmpty { "Uncategorised" } ?: "All", fontSize = 14.sp, color = fg, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        rc?.let { Text(it.count.toString(), fontSize = 11.sp, color = if (f) fg else LocalTheme.current.colors.muted) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Btn(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FocusBox(onClick = onClick, modifier = modifier) { f ->
        Text(
            text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (f) Color.Black else Color.White,
            modifier = Modifier.graphicsLayer { val s = if (f) 1.05f else 1f; scaleX = s; scaleY = s }
                .clip(RoundedCornerShape(6.dp)).background(if (f) Color.White else Color(0x1FFFFFFF))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

/** Movie detail (mockup 02 screen 3) and series detail with season tabs and episode cards (screen 2). */
@Composable
fun VodDetailScreen(vm: VodViewModel, item: VodItem, onPlay: (url: String, title: String, episode: String, startMs: Long) -> Unit, onOpen: (VodItem) -> Unit) {
    val details by vm.details.collectAsState()
    val favs by vm.favourites.collectAsState()
    val rows by vm.progress.collectAsState()
    val d = details[item.id]
    val play = remember { FocusRequester() }
    LaunchedEffect(item.id) { vm.loadDetail(item); withFrameNanos { }; runCatching { play.requestFocus() } }
    val fav = favs.any { it.id == item.id }
    vm.learnedHeights.collectAsState().value // recompose when a height is learned
    val resolution = (d?.height ?: vm.learnedHeight(item))?.let { org.imdtv.iptvtv.player.resolutionLabel(it) }

    Box(Modifier.fillMaxSize().themeBackground(LocalTheme.current.colors.vodBg)) {
        Backdrop(d?.backdrop ?: item.poster)
        if (item.kind == Kind.MOVIE) {
            Column(Modifier.padding(start = 50.dp, top = 55.dp).width(430.dp)) {
                Hero(item, d, Modifier, resolution = resolution)
                listOfNotNull(d?.director?.let { "Director: $it" }, d?.cast?.let { "Cast: $it" }).takeIf { it.isNotEmpty() }?.let {
                    Text(it.joinToString("  ·  "), fontSize = 10.5.sp, color = LocalTheme.current.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 9.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 16.dp).wrapContentWidth(Alignment.Start, unbounded = true)) { // four buttons may run past the column
                    val row = rows.find { it.kind == item.kind && it.itemId == item.id && it.episode == "" }
                    val at = resumeAt(row)
                    if (at > 0) {
                        Btn("▶  Resume from ${duration(at)}", Modifier.focusRequester(play)) { item.streamUrl?.let { onPlay(it, item.name, "", at) } }
                        Btn("↺  From start") { item.streamUrl?.let { onPlay(it, item.name, "", 0) } }
                    } else Btn("▶  Play", Modifier.focusRequester(play)) { item.streamUrl?.let { onPlay(it, item.name, "", 0) } }
                    Btn(if (fav) "★  Favourited" else "☆  Favourite") { vm.toggleFavourite(item) }
                    Btn(if (row?.completed == true) "✓  Watched" else "Mark watched") { vm.setWatched(item, row?.completed != true) }
                }
            }
            item.categoryId?.let { catId ->
                val more by produceState(emptyList<VodItem>(), catId) { value = vm.inCategory(catId).filter { it.id != item.id } }
                // Bottom-anchored inside the TV-safe area; padding leaves room for the focused poster's scale and outline.
                Column(Modifier.align(Alignment.BottomStart).padding(start = 50.dp, bottom = 20.dp)) {
                    Text("More like this", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.padding(bottom = 2.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(11.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 8.dp, end = 50.dp, top = 6.dp, bottom = 6.dp),
                        modifier = Modifier.offset(x = (-8).dp)) {
                        items(more, key = { it.id }) { m -> Poster(m, onFocused = {}, onClick = { onOpen(m) }) }
                    }
                }
            }
        } else {
            val eps = d?.episodes.orEmpty()
            val seasons = eps.map { it.season }.distinct().sorted()
            var season by remember(item.id) { mutableStateOf<Int?>(null) }
            val seriesRows = rows.filter { it.kind == item.kind && it.itemId == item.id }
            val next = nextEpisode(eps, seriesRows)
            val sel = season ?: next?.episode?.season ?: seasons.firstOrNull()
            var menuFor by remember(item.id) { mutableStateOf<Episode?>(null) }
            Column(Modifier.padding(start = 50.dp, top = 55.dp).width(430.dp)) {
                Hero(item, d, Modifier, extra = seasons.size.takeIf { it > 0 }?.let { if (it == 1) "1 Season" else "$it Seasons" }, resolution = resolution)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 16.dp).wrapContentWidth(Alignment.Start, unbounded = true)) { // four buttons may run past the column
                    val ne = next?.episode
                    val ntitle = ne?.let { "${item.name} S${it.season}E${it.number}" }
                    if (ne == null) Btn("▶  Play", Modifier.focusRequester(play)) { }
                    else if (next.resume) {
                        Btn("▶  Resume S${ne.season} · E${ne.number}", Modifier.focusRequester(play)) { onPlay(ne.streamUrl, ntitle!!, ne.remoteId, next.startMs) }
                        Btn("↺  From start") { onPlay(ne.streamUrl, ntitle!!, ne.remoteId, 0) }
                    } else Btn("▶  Play S${ne.season} · E${ne.number}", Modifier.focusRequester(play)) { onPlay(ne.streamUrl, ntitle!!, ne.remoteId, 0) }
                    Btn(if (fav) "★  Favourited" else "☆  Favourite") { vm.toggleFavourite(item) }
                    if (ne != null) Btn("Mark E${ne.number} watched") { vm.setWatched(item, true, ne.remoteId) }
                }
            }
            Column(Modifier.padding(start = 50.dp, top = 300.dp)) {
                if (d == null) Text("Loading episodes…", fontSize = 12.sp, color = LocalTheme.current.colors.muted)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 13.dp)) {
                    seasons.forEach { s ->
                        FocusBox(onClick = { season = s }, onFocused = { season = s }) { f ->
                            Text("Season $s", fontSize = 11.5.sp, fontWeight = FontWeight.Medium,
                                color = if (s == sel) Color.Black else LocalTheme.current.colors.muted,
                                modifier = Modifier.border(1.5.dp, if (f) Color.White else Color.Transparent, RoundedCornerShape(17.dp)).padding(2.dp)
                                    .clip(RoundedCornerShape(15.dp)).background(if (s == sel) Color.White else Color(0x14FFFFFF))
                                    .padding(horizontal = 13.dp, vertical = 5.dp))
                        }
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(13.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(end = 50.dp)) {
                    items(eps.filter { it.season == sel }, key = { it.remoteId }) { ep ->
                        val r = seriesRows.find { it.episode == ep.remoteId }
                        FocusBox(onClick = { onPlay(ep.streamUrl, "${item.name} S${ep.season}E${ep.number}", ep.remoteId, resumeAt(r)) },
                            modifier = Modifier.width(216.dp).onLongOk { menuFor = ep }) { f ->
                            Column {
                                Box(
                                    Modifier.graphicsLayer { val s = if (f) 1.06f else 1f; scaleX = s; scaleY = s }
                                        .border(2.dp, if (f) Color.White else Color.Transparent, RoundedCornerShape(8.dp)).padding(3.dp)
                                        .size(210.dp, 118.dp).clip(RoundedCornerShape(6.dp)).background(LocalTheme.current.colors.vodCard),
                                ) {
                                    (ep.thumb ?: d?.backdrop)?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                                    Chip("E${ep.number}", Modifier.align(Alignment.BottomStart))
                                    ep.durationSecs?.takeIf { it > 0 }?.let { Chip(duration(it * 1000L), Modifier.align(Alignment.BottomEnd)) }
                                    if (r != null && !r.completed && r.durationMs > 0) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).background(Color(0x66FFFFFF))) {
                                        Box(Modifier.fillMaxHeight().fillMaxWidth((r.positionMs.toFloat() / r.durationMs).coerceIn(0f, 1f)).background(LocalTheme.current.colors.accent))
                                    }
                                    if (r?.completed == true) Chip("✓", Modifier.align(Alignment.TopEnd))
                                }
                                Text(ep.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp, bottom = 3.dp))
                                ep.plot?.let { Text(it, fontSize = 9.5.sp, lineHeight = 13.sp, color = LocalTheme.current.colors.muted, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                            }
                        }
                    }
                }
            }
            menuFor?.let { ep ->
                val r = seriesRows.find { it.episode == ep.remoteId }
                val items = listOfNotNull<Pair<String, () -> Unit>>(
                    if (r != null && !r.completed && r.positionMs > 0) "Play from start" to { menuFor = null; onPlay(ep.streamUrl, "${item.name} S${ep.season}E${ep.number}", ep.remoteId, 0) } else null,
                    if (r?.completed == true) "Mark unwatched" to { menuFor = null; vm.setWatched(item, false, ep.remoteId) }
                    else "Mark watched" to { menuFor = null; vm.setWatched(item, true, ep.remoteId) },
                )
                ContextMenu("S${ep.season} · E${ep.number}", items) { menuFor = null }
            }
        }
    }
}

@Composable
private fun Chip(text: String, modifier: Modifier) {
    Text(text, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White,
        modifier = modifier.padding(7.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x99000000)).padding(horizontal = 5.dp, vertical = 1.5.dp))
}
