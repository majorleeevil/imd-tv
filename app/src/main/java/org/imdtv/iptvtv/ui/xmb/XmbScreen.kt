package org.imdtv.iptvtv.ui.xmb

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import org.imdtv.iptvtv.ui.search.SearchViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.imdtv.iptvtv.R
import org.imdtv.iptvtv.data.catalogue.displayName
import org.imdtv.iptvtv.data.db.ChannelEntity
import org.imdtv.iptvtv.data.model.Kind
import org.imdtv.iptvtv.ui.ChannelLogo
import org.imdtv.iptvtv.ui.clockTime
import org.imdtv.iptvtv.ui.use24Hour
import java.util.Locale
import org.imdtv.iptvtv.ui.live.GuideMenu
import org.imdtv.iptvtv.ui.live.GuideMenus
import org.imdtv.iptvtv.ui.live.LiveViewModel
import org.imdtv.iptvtv.ui.live.Preview
import org.imdtv.iptvtv.ui.live.PreviewEdge
import org.imdtv.iptvtv.ui.live.heroBadges
import org.imdtv.iptvtv.ui.live.rememberGuideState
import org.imdtv.iptvtv.ui.onLongOk
import org.imdtv.iptvtv.ui.settings.SettingsGroup
import org.imdtv.iptvtv.ui.vod.VodItem
import org.imdtv.iptvtv.ui.vod.VodViewModel

private val CLOCK = DateTimeFormatter.ofPattern("d/M H:mm")
private val CLOCK12 = DateTimeFormatter.ofPattern("d/M h.mma", Locale.UK)
private val ICONS = mapOf(XmbColumn.SETTINGS to R.drawable.ic_tab_settings, XmbColumn.SEARCH to R.drawable.ic_tab_search, XmbColumn.LIVE to R.drawable.ic_tab_live,
    XmbColumn.MOVIES to R.drawable.ic_tab_movies, XmbColumn.SERIES to R.drawable.ic_tab_series, XmbColumn.RECORDINGS to R.drawable.ic_tab_recordings)
private val LABELS = mapOf(XmbColumn.SETTINGS to "Settings", XmbColumn.SEARCH to "Search", XmbColumn.LIVE to "Live TV", XmbColumn.MOVIES to "Movies",
    XmbColumn.SERIES to "Series", XmbColumn.RECORDINGS to "Recordings")
private val ROW_Y = 150.dp      // icon row, about a third down
private val COL_X = 120.dp      // first column
private val COL_W = 150.dp
private val ITEM_H = 46.dp
private val FOCUS_Y = 250.dp    // the focused item sits just under the icon row
private val GLOW = Shadow(Color(0x9999D9FF), Offset.Zero, 24f)

/**
 * Gamestation (spec §6A.8): an icon row (Left/Right) with each column's items listed vertically (Up/Down); OK on a
 * category opens it as a folder. One focusable box takes the keys and the layout is drawn from [LiveViewModel.xmb].
 */
@Composable
fun XmbScreen(
    live: LiveViewModel, vod: VodViewModel, onFullScreen: (ChannelEntity) -> Unit, onOpen: (VodItem) -> Unit,
    onSettings: (SettingsGroup) -> Unit, focus: FocusRequester, onFocused: (Boolean) -> Unit,
    search: SearchViewModel, onSearchChannel: (ChannelEntity) -> Unit,
    onPlayRecording: (org.imdtv.iptvtv.recording.RecordingEntity) -> Unit = {},
) {
    val recordingOn by live.recordingOn.collectAsState()
    val columns = org.imdtv.iptvtv.ui.xmb.xmbColumns(recordingOn)
    // Six columns need a little less room each to fit the screen.
    val colW = if (columns.size > 5) 135.dp else COL_W
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as org.imdtv.iptvtv.IptvApp
    val recordings by remember { app.container.recordings.observe() }.collectAsState(emptyList())
    var recordingMenu by remember { mutableStateOf<org.imdtv.iptvtv.recording.RecordingEntity?>(null) }
    LaunchedEffect(recordingOn) { if (!recordingOn && live.xmb.column == XmbColumn.RECORDINGS) live.xmb = XmbState(column = XmbColumn.LIVE) }
    val query by search.query.collectAsState()
    // Movies/Series folders: item 0 is the in-category search bar, then the matches (or the whole category).
    val vodQuery by vod.query.collectAsState()
    val vodMatches by vod.matches.collectAsState()
    val results by search.results.collectAsState()
    // Search column: item 0 is the query (OK types into it with the system keyboard), items 1–3 the result folders.
    var editing by remember { mutableStateOf(false) }
    val field = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    fun section(i: Int): List<Any> = results?.let { listOf(it.channels.items, it.movies.items, it.series.items) }?.getOrNull(i).orEmpty()
    val guide = rememberGuideState(live)
    val rail by live.rail.collectAsState()
    val channels by live.channels.collectAsState()
    val programmes by live.programmes.collectAsState()
    val now by live.now.collectAsState()
    val strip by live.strip.collectAsState()
    val logoSource by live.logoSource.collectAsState()
    val previewingId by live.previewingId.collectAsState()
    val formats by live.player.formats.collectAsState()
    val cats by vod.categories.collectAsState()
    val grid by vod.grid.collectAsState()
    val details by vod.details.collectAsState()
    val x = live.xmb
    val inFolder = x.folder != null

    val items: List<String> = when (x.column) {
        XmbColumn.SETTINGS -> SettingsGroup.entries.map { it.title }
        XmbColumn.LIVE -> rail.orEmpty().map { it.entry.title }
        XmbColumn.RECORDINGS -> recordings.map { r -> listOf(r.title, org.imdtv.iptvtv.recording.recordingStatus(r)).filter { it.isNotEmpty() }.joinToString(" · ") }
        XmbColumn.SEARCH -> listOf(if (query.isEmpty()) "Search…" else "Search: $query") + listOf("Channels", "Movies", "Series").mapIndexed { i, t ->
            val s = results?.let { listOf(it.channels, it.movies, it.series)[i] }
            if (s == null) t else "$t (${if (s.total > s.items.size) "${s.items.size} of ${s.total}" else s.total})"
        }
        else -> cats.map { it.category.name.ifEmpty { "Uncategorised" } }
    }
    val folderCount = when {
        !inFolder -> 0
        x.column == XmbColumn.LIVE -> channels.size
        x.column == XmbColumn.SEARCH -> section((x.folder ?: 1) - 1).size
        else -> (vodMatches ?: grid).size + 1
    }

    // Movies/Series columns show that kind's categories (setKind is a no-op when it already matches).
    LaunchedEffect(x.column) {
        when (x.column) {
            XmbColumn.MOVIES -> vod.setKind(Kind.MOVIE)
            XmbColumn.SERIES -> vod.setKind(Kind.SERIES)
            XmbColumn.SEARCH -> search.warm()
            else -> Unit
        }
        // Nothing plays unseen once the Live folder is left.
        if (x.column != XmbColumn.LIVE) live.player.stop()
    }
    // Lists that shrank (hidden channels, a sync) never leave focus on nothing; a gone folder closes.
    LaunchedEffect(items.size, folderCount) { if (items.isNotEmpty()) live.xmb = live.xmb.clamp(items.size, folderCount) }
    // Back from full screen: the folder reopens on the channel being watched.
    LaunchedEffect(channels) {
        if (live.xmb.column == XmbColumn.LIVE && live.xmb.folder != null) {
            channels.indexOfFirst { it.id == live.focused.value?.id }.takeIf { it >= 0 }?.let { live.xmb = live.xmb.copy(folderItem = it) }
        }
    }
    val focusedChannel = if (inFolder && x.column == XmbColumn.LIVE) channels.getOrNull(x.folderItem) else null
    val focusedVod = if (inFolder && x.column != XmbColumn.LIVE && x.column != XmbColumn.SETTINGS && x.column != XmbColumn.SEARCH) (vodMatches ?: grid).getOrNull(x.folderItem - 1) else null
    val onVodBar = inFolder && (x.column == XmbColumn.MOVIES || x.column == XmbColumn.SERIES) && x.folderItem == 0
    val searchHit = if (inFolder && x.column == XmbColumn.SEARCH) section((x.folder ?: 1) - 1).getOrNull(x.folderItem) else null
    LaunchedEffect(focusedChannel?.id) { focusedChannel?.let { live.focused.value = it } }
    LaunchedEffect(focusedVod?.id) { focusedVod?.let { vod.focused.value = it; vod.loadDetail(it) } }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    guide.heldFocus = focus // after a long-press menu, focus returns here

    BackHandler(enabled = inFolder) {
        if (x.column == XmbColumn.LIVE) { live.player.stop(); guide.previewing = false }
        live.xmb = x.back() ?: x
    }

    val click = {
        when {
            !inFolder && x.column == XmbColumn.SETTINGS -> SettingsGroup.entries.getOrNull(x.item)?.let(onSettings)
            !inFolder && x.column == XmbColumn.RECORDINGS -> recordingMenu = recordings.getOrNull(x.item)
            !inFolder && x.column == XmbColumn.SEARCH && x.item == 0 -> { editing = true }
            onVodBar -> { editing = true }
            !inFolder && x.column == XmbColumn.SEARCH -> if (section(x.item - 1).isNotEmpty()) live.xmb = x.open()
            searchHit is ChannelEntity -> onSearchChannel(searchHit)
            searchHit is VodItem -> onOpen(searchHit)
            !inFolder && x.column == XmbColumn.LIVE -> rail.orEmpty().getOrNull(x.item)?.let { live.select(it.entry); live.xmb = x.open() }
            !inFolder -> cats.getOrNull(x.item)?.let { vod.gridCategory.value = it.category; live.xmb = x.open() }
            focusedChannel != null -> guide.onOk(focusedChannel, onFullScreen)
            focusedVod != null -> onOpen(focusedVod)
        }
        Unit
    }

    var screen by remember { mutableStateOf(Size.Zero) }
    Box(
        Modifier.fillMaxSize().onSizeChanged { screen = Size(it.width.toFloat(), it.height.toFloat()) }
            .drawWithCache { val img = waveBitmap(this, size); onDrawBehind { drawImage(img) } }
            .focusRequester(focus).onFocusChanged { onFocused(it.hasFocus) }
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown || editing) return@onPreviewKeyEvent false
                val count = if (inFolder) folderCount else items.size
                when (e.key) {
                    Key.DirectionLeft -> { live.xmb = x.left(columns); true }
                    Key.DirectionRight -> { live.xmb = x.right(columns); true }
                    Key.DirectionUp -> { live.xmb = x.up(count); true }
                    Key.DirectionDown -> { live.xmb = x.down(count); true }
                    else -> false
                }
            }
            .onLongOk { (focusedChannel ?: searchHit as? ChannelEntity)?.let { guide.menu = GuideMenu.Channel(it) } }
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = click)
            .focusable(),
    ) {
        Text((if (use24Hour) CLOCK else CLOCK12).format(Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())), fontSize = 13.sp, color = Color.White,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 22.dp, end = 30.dp)
                .border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(3.dp)).padding(horizontal = 18.dp, vertical = 4.dp).width(160.dp))

        // Icon row: hidden while a folder is open (its category moves left instead).
        if (!inFolder) columns.forEachIndexed { i, c ->
            val sel = c == x.column
            Column(Modifier.offset(x = COL_X + colW * i, y = ROW_Y - 30.dp).width(110.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(painterResource(ICONS.getValue(c)), null, tint = Color.White.copy(alpha = if (sel) 1f else .55f), modifier = Modifier.size(if (sel) 46.dp else 36.dp))
                if (sel) Text(LABELS.getValue(c), fontSize = 13.sp, color = Color.White, modifier = Modifier.padding(top = 4.dp))
            }
        }

        val colX = COL_X + colW * columns.indexOf(x.column).coerceAtLeast(0) + 20.dp
        if (!inFolder) {
            // Items of the selected column; the focused one just under the icon row, earlier ones above it.
            items.forEachIndexed { i, title ->
                val d = i - x.item
                if (d < -2 || d > 6) return@forEachIndexed
                if (editing && x.column == XmbColumn.SEARCH && i == 0) return@forEachIndexed // the field is drawn there instead
                val y = if (d < 0) ROW_Y - 60.dp + ITEM_H * (d + 1) - 30.dp else FOCUS_Y + ITEM_H * d + if (d > 0) 14.dp else 0.dp
                XmbItem(title, d == 0, Modifier.offset(x = colX, y = y).width(500.dp),
                    folderIcon = x.column != XmbColumn.SETTINGS && x.column != XmbColumn.RECORDINGS && !(x.column == XmbColumn.SEARCH && i == 0))
            }
            if (items.isEmpty() && x.column == XmbColumn.RECORDINGS) {
                Text("No recordings yet. Hold OK on a channel in Live TV to record it.", fontSize = 15.sp, color = Color.White, modifier = Modifier.offset(x = colX - 200.dp, y = FOCUS_Y))
            }
            if (items.isEmpty() && x.column == XmbColumn.LIVE && rail != null) {
                Text("All live categories are hidden. Show them again in Settings › Channels & categories.", fontSize = 15.sp,
                    color = Color.White, modifier = Modifier.offset(x = colX, y = FOCUS_Y))
            }
        } else {
            // Folder: the category on the left, its contents listed to the right.
            XmbItem(items.getOrNull(x.folder ?: 0).orEmpty(), true, Modifier.offset(x = 40.dp, y = FOCUS_Y).width(220.dp), folderIcon = true)
            val listX = 290.dp
            if (x.column == XmbColumn.LIVE) {
                channels.forEachIndexed { i, ch ->
                    val d = i - x.folderItem
                    if (d < -3 || d > 6) return@forEachIndexed
                    val y = FOCUS_Y + ITEM_H * d + if (d > 0) 26.dp else 0.dp
                    Row(Modifier.offset(x = listX, y = y).width(250.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp, 30.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x22FFFFFF)), contentAlignment = Alignment.Center) {
                            ChannelLogo(ch, logoSource, Modifier.fillMaxSize().padding(2.dp))
                        }
                        Column(Modifier.padding(start = 10.dp).weight(1f)) {
                            XmbText(ch.displayName(strip), d == 0)
                            if (d == 0) {
                                val cur = programmes[ch.id].orEmpty().firstOrNull { it.start <= now && now < it.stop }
                                val meta = listOfNotNull(cur?.let { "Now: ${it.title} ${clockTime(it.start)}–${clockTime(it.stop)}" })
                                    .plus(heroBadges(ch.id, previewingId, formats)).joinToString(" · ")
                                if (meta.isNotEmpty()) Text(meta, fontSize = 12.sp, color = Color.White.copy(alpha = .8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                if (channels.isEmpty()) Text("No channels", fontSize = 15.sp, color = Color.White.copy(alpha = .55f), modifier = Modifier.offset(x = listX, y = FOCUS_Y))
                Column(Modifier.align(Alignment.TopEnd).padding(top = 70.dp, end = 30.dp).width(380.dp)) {
                    Preview(live, visible = guide.previewing, screen = screen, edge = PreviewEdge.NONE,
                        modifier = Modifier.size(380.dp, 214.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x40000000)))
                    val cur = focusedChannel?.let { c -> programmes[c.id].orEmpty().firstOrNull { it.start <= now && now < it.stop } }
                    Text(cur?.desc.orEmpty(), fontSize = 13.sp, lineHeight = 18.sp, color = Color.White, maxLines = 5,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                }
            } else if (x.column == XmbColumn.SEARCH) {
                section((x.folder ?: 1) - 1).forEachIndexed { i, hit ->
                    val d = i - x.folderItem
                    if (d < -3 || d > 6) return@forEachIndexed
                    val title = if (hit is ChannelEntity) listOfNotNull((hit.customNumber ?: hit.number)?.toString()?.takeIf { org.imdtv.iptvtv.ui.LocalShowNumbers.current }, hit.displayName(strip)).joinToString("  ")
                        else (hit as VodItem).name
                    XmbItem(title, d == 0, Modifier.offset(x = listX, y = FOCUS_Y + ITEM_H * d + if (d > 0) 14.dp else 0.dp).width(560.dp), folderIcon = false)
                }
            } else {
                val shown = vodMatches ?: grid
                val barD = -x.folderItem
                if (!editing && barD >= -3) XmbItem(if (vodQuery.isEmpty()) "Search ${items.getOrNull(x.folder ?: 0).orEmpty()}…" else "Search: $vodQuery",
                    barD == 0, Modifier.offset(x = listX, y = FOCUS_Y + ITEM_H * barD).width(330.dp), folderIcon = false)
                shown.forEachIndexed { i, v ->
                    val d = i + 1 - x.folderItem
                    if (d < -3 || d > 6) return@forEachIndexed
                    XmbItem(v.name, d == 0, Modifier.offset(x = listX, y = FOCUS_Y + ITEM_H * d + if (d > 0) 14.dp else 0.dp).width(330.dp), folderIcon = false)
                }
                if (shown.isEmpty()) Text(if (vodMatches != null) "No matches" else "Nothing here", fontSize = 15.sp, color = Color.White.copy(alpha = .55f), modifier = Modifier.offset(x = listX, y = FOCUS_Y + ITEM_H + 14.dp))
                focusedVod?.let { v ->
                    Column(Modifier.align(Alignment.TopEnd).padding(top = 70.dp, end = 30.dp).width(300.dp)) {
                        Box(Modifier.size(150.dp, 225.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x26FFFFFF))) {
                            v.poster?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                        }
                        Text(details[v.id]?.plot.orEmpty(), fontSize = 13.sp, lineHeight = 18.sp, color = Color.White, maxLines = 6,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
        if (editing) {
            LaunchedEffect(Unit) { withFrameNanos { }; runCatching { field.requestFocus() }; keyboard?.show() }
            val hadFocus = remember { mutableStateOf(false) } // the first report is "not focused", before requestFocus
            fun done() { keyboard?.hide(); editing = false; runCatching { focus.requestFocus() } }
            val vodBar = inFolder && x.column != XmbColumn.SEARCH
            BasicTextField(if (vodBar) vodQuery else query, { if (vodBar) vod.query.value = it else search.query.value = it }, singleLine = true,
                textStyle = TextStyle(fontSize = 19.sp, color = Color.White, shadow = GLOW), cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { done() }),
                modifier = Modifier.offset(x = if (vodBar) 290.dp else colX, y = FOCUS_Y).width(if (vodBar) 330.dp else 500.dp).background(Color(0x66000000), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp).focusRequester(field)
                    .onFocusChanged { if (hadFocus.value && !it.isFocused) editing = false; hadFocus.value = it.isFocused }
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (e.key) {
                            Key.DirectionUp -> { done(); true }
                            Key.DirectionDown -> { done(); live.xmb = live.xmb.down(if (vodBar) folderCount else items.size); true }
                            else -> false
                        }
                    })
        }
        GuideMenus(live, guide)
        recordingMenu?.let { r -> org.imdtv.iptvtv.recording.RecordingMenu(r, all = true, onPlay = onPlayRecording) { recordingMenu = null; runCatching { focus.requestFocus() } } }
    }
}

@Composable
private fun XmbItem(title: String, focused: Boolean, modifier: Modifier, folderIcon: Boolean) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (folderIcon) Icon(painterResource(R.drawable.ic_cat_folder), null, tint = Color.White.copy(alpha = if (focused) 1f else .55f),
            modifier = Modifier.padding(end = 10.dp).size(if (focused) 26.dp else 20.dp))
        Box(Modifier.weight(1f, fill = false)) { XmbText(title, focused) } // ellipsise inside the row's width
    }
}

/** Focused: larger, bright white with a soft blue glow; others ~55%. */
@Composable
private fun XmbText(text: String, focused: Boolean) {
    Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis,
        style = if (focused) TextStyle(fontSize = 19.sp, color = Color.White, shadow = GLOW) else TextStyle(fontSize = 16.sp, color = Color.White.copy(alpha = .55f)))
}
