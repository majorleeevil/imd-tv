package org.imdtv.iptvtv.ui.settings

import org.imdtv.iptvtv.data.repo.RailEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import org.imdtv.iptvtv.R
import androidx.annotation.StringRes

import org.imdtv.iptvtv.data.settings.AppSettings
import org.imdtv.iptvtv.data.settings.DEFAULT_LOGO_ORDER
import org.imdtv.iptvtv.ui.theme.LocalTheme

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.border
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import org.imdtv.iptvtv.data.model.Kind
import org.imdtv.iptvtv.data.settings.LogoKind
import org.imdtv.iptvtv.data.catalogue.parseStripList
import org.imdtv.iptvtv.ui.FocusBox
import org.imdtv.iptvtv.ui.onLongOk
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.focusable
import org.imdtv.iptvtv.data.db.GroupPlacement
import org.imdtv.iptvtv.data.db.CustomGroupEntity
import org.imdtv.iptvtv.data.db.CategoryEntity
import org.imdtv.iptvtv.data.catalogue.displayName
import org.imdtv.iptvtv.ui.TextPrompt
import org.imdtv.iptvtv.ui.ContextMenu

/** Pages of Channels & categories; a page with a parent opens from (and Back returns to) that page.
 *  [title]/[explanation] are English-only compatibility copies for SettingsScreen.kt: use [titleRes]/[explanationRes]. */
enum class ChannelPage(@StringRes val titleRes: Int, @StringRes val explanationRes: Int, val title: String, val explanation: String, val parent: ChannelPage? = null) {
    CHANNELS(R.string.set_a_page_channels, R.string.set_a_page_channels_expl, "Channels", "Logos, name clean-up and header channels"),
    CHANNEL_LOGOS(R.string.set_a_page_channel_logos, R.string.set_a_page_channel_logos_expl, "Channel logos", "Where logos come from, in order; your logo pack", parent = CHANNELS),
    CATEGORIES(R.string.set_a_page_categories, R.string.set_a_page_categories_expl, "Categories", "Show, hide and reorder categories; languages; your own categories"),
    LANGUAGES(R.string.set_a_page_languages, R.string.set_a_page_languages_expl, "Languages & countries", "Show or hide every category of a language or country", parent = CATEGORIES),
    YOUR_CATEGORIES(R.string.set_a_page_your_categories, R.string.set_a_page_your_categories_expl, "Your categories", "Create, rename and delete your own categories", parent = CATEGORIES),
    CATEGORY_NAME_CLEANUP(R.string.set_a_page_category_cleanup, R.string.set_a_page_category_cleanup_expl, "Category name clean-up", "Remove or rename prefixes and suffixes in category names", parent = CATEGORIES),
    PROVIDER_CATEGORIES(R.string.set_a_page_provider_categories, R.string.set_a_page_provider_categories_expl, "Manage provider categories", "Show, hide and reorder the playlist's categories", parent = CATEGORIES),
    LIVE_CATEGORIES(R.string.set_a_page_live_categories, R.string.set_a_page_live_categories_expl, "Live", "Live TV categories", parent = PROVIDER_CATEGORIES),
    MOVIE_CATEGORIES(R.string.set_a_page_movie_categories, R.string.set_a_page_movie_categories_expl, "Movies", "Movie categories", parent = PROVIDER_CATEGORIES),
    SERIES_CATEGORIES(R.string.set_a_page_series_categories, R.string.set_a_page_series_categories_expl, "Series", "Series categories", parent = PROVIDER_CATEGORIES),
    CHANNEL_NAME_CLEANUP(R.string.set_a_page_channel_cleanup, R.string.set_a_page_channel_cleanup_expl, "Channel name clean-up", "Remove or rename prefixes and suffixes such as “UK|”", parent = CHANNELS),
    MANAGE_CHANNELS(R.string.set_a_page_manage_channels, R.string.set_a_page_manage_channels_expl, "Manage channels", "Show, hide and reorder channels, by category", parent = CHANNELS),
    MERGE_OPTIONS(R.string.set_a_page_merge_options, R.string.set_a_page_merge_options_expl, "Merge options", "Merge across, merge by and playlists", parent = CHANNELS),
    MERGE_PLAYLISTS(R.string.set_a_page_merge_playlists, R.string.set_a_page_merge_playlists_expl, "Playlists", "Which playlists merge their channels", parent = MERGE_OPTIONS),
    // Not channel pages, but the same open/Back/refocus plumbing: Playback › Subtitles, Appearance › Date and time.
    SUBTITLES(R.string.set_a_page_subtitles, R.string.set_a_page_subtitles_expl, "Subtitles", "Movies & series and live TV"),
    APPEARANCE_APP(R.string.set_a_page_app, R.string.set_a_page_app_expl, "App", "Theme, colours, date and time"),
    APPEARANCE_LIVE(R.string.set_b_live_tv, R.string.set_a_page_live_tv_expl, "Live TV", "Guide rows, header and titles"),
    APPEARANCE_VOD(R.string.set_b_movies_series, R.string.set_a_page_vod_expl, "Movies & Series", "Rows and titles"),
    DATE_TIME(R.string.set_a_page_date_time, R.string.set_a_page_date_time_expl, "Date and time", "The clock and date at the top of the screen, and in the player", parent = APPEARANCE_APP),
}

@Composable
private fun LogoKind.short() = stringResource(when (this) { LogoKind.PLAYLIST -> R.string.set_a_logo_short_playlist; LogoKind.OPEN_SOURCE -> R.string.set_a_logo_short_open_source; LogoKind.PACK -> R.string.set_a_logo_short_pack; LogoKind.EPG -> R.string.set_a_logo_short_epg })

/** The Channels & categories group: Channels › and Categories ›. */
@Composable
fun ChannelGroupRows(vm: SettingsViewModel, first: Modifier, openers: Map<ChannelPage, FocusRequester>, onOpen: (ChannelPage) -> Unit) {
    SettingRow(stringResource(ChannelPage.CHANNELS.titleRes), stringResource(ChannelPage.CHANNELS.explanationRes), "›", first.focusRequester(openers.getValue(ChannelPage.CHANNELS))) { onOpen(ChannelPage.CHANNELS) }
    SettingRow(stringResource(ChannelPage.CATEGORIES.titleRes), stringResource(ChannelPage.CATEGORIES.explanationRes), "›", Modifier.focusRequester(openers.getValue(ChannelPage.CATEGORIES))) { onOpen(ChannelPage.CATEGORIES) }
}

/** Channels: Channel logos ›, Channel name clean-up ›, Hide header channels. */
@Composable
private fun ChannelsPageRows(vm: SettingsViewModel, first: FocusRequester, openers: Map<ChannelPage, FocusRequester>, onOpen: (ChannelPage) -> Unit) {
    val strip by vm.nameStrip.collectAsState()
    val s by vm.settings.collectAsState()
    val matching by vm.logoMatching.collectAsState()
    val logos = ChannelPage.CHANNEL_LOGOS
    // Scrolls: in short themes (Xcube) the rows below Channel logos were out of reach.
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val manage = ChannelPage.MANAGE_CHANNELS
        SettingRow(stringResource(manage.titleRes), stringResource(manage.explanationRes), "›", Modifier.focusRequester(first).focusRequester(openers.getValue(manage))) { onOpen(manage) }
        // On: Merge options › appears (Merge across, Merge by, Playlists), so all of them hide at once when it's off.
        // Spinner while a merge run works in the background (about 10 s for a 46k-channel playlist on a Chromecast).
        val merging by vm.merging.collectAsState()
        SettingRow(stringResource(R.string.set_a_merge_dupes), stringResource(R.string.set_a_merge_dupes_expl), null, busy = merging, toggle = s.mergeChannels) {
            vm.setMergeChannels(!s.mergeChannels)
        }
        if (s.mergeChannels) {
            val opts = ChannelPage.MERGE_OPTIONS
            SettingRow(stringResource(opts.titleRes), stringResource(opts.explanationRes), "›", Modifier.focusRequester(openers.getValue(opts))) { onOpen(opts) }
        }
        SettingRow(stringResource(logos.titleRes), stringResource(logos.explanationRes), s.logoOrder.map { it.short() }.joinToString(" → ") + "  ›",
            Modifier.focusRequester(openers.getValue(logos)), matching) { onOpen(logos) }
        val names = ChannelPage.CHANNEL_NAME_CLEANUP
        SettingRow(stringResource(names.titleRes), stringResource(names.explanationRes), strip.ifBlank { stringResource(R.string.set_a_none) }, Modifier.focusRequester(openers.getValue(names))) { onOpen(names) }
        SwitchRow(stringResource(R.string.set_a_remove_symbols), stringResource(R.string.set_a_remove_symbols_expl), s.removeNameSymbols) { on ->
            vm.update { it.copy(removeNameSymbols = on) }
        }
        SwitchRow(stringResource(R.string.set_a_hide_header), stringResource(R.string.set_a_hide_header_expl), s.hideHeaderChannels) { on ->
            vm.update { it.copy(hideHeaderChannels = on) }
        }
        SwitchRow(stringResource(R.string.set_a_channel_numbers), stringResource(R.string.set_a_channel_numbers_expl), s.showChannelNumbers) { n -> vm.update { it.copy(showChannelNumbers = n) } }
        SwitchRow(stringResource(R.string.set_a_guide_logos), stringResource(R.string.set_a_guide_logos_expl), s.guideLogos) { on -> vm.update { it.copy(guideLogos = on) } }
    }
}

/** A sub-page in the right pane. Back closes it (the categories page first cancels a move). */
@Composable
fun ChannelPageView(
    page: ChannelPage, vm: SettingsViewModel, modifier: Modifier, first: FocusRequester, openers: Map<ChannelPage, FocusRequester>,
    restoring: Boolean, onOpen: (ChannelPage) -> Unit, onClose: () -> Unit,
) {
    // Coming back from a sub-page, the screen puts focus on the row that opened it instead.
    LaunchedEffect(page) { if (!restoring) { withFrameNanos { }; runCatching { first.requestFocus() } } }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(page.titleRes), fontSize = 20.sp, color = Color.White, modifier = Modifier.padding(top = 6.dp, bottom = 18.dp))
        when (page) {
            ChannelPage.CHANNEL_LOGOS -> { BackHandler(onBack = onClose); ChannelLogosPage(vm, first) }
            ChannelPage.CATEGORIES -> {
                BackHandler(onBack = onClose)
                PageRows(listOf(ChannelPage.LANGUAGES, ChannelPage.YOUR_CATEGORIES, ChannelPage.PROVIDER_CATEGORIES, ChannelPage.CATEGORY_NAME_CLEANUP), vm, first, openers, onOpen)
                val s by vm.settings.collectAsState()
                SwitchRow(stringResource(R.string.set_a_recently_watched), stringResource(R.string.set_a_recently_watched_expl), s.showRecent) { on -> vm.update { it.copy(showRecent = on) } }
            }
            ChannelPage.PROVIDER_CATEGORIES -> { BackHandler(onBack = onClose); PageRows(listOf(ChannelPage.LIVE_CATEGORIES, ChannelPage.MOVIE_CATEGORIES, ChannelPage.SERIES_CATEGORIES), vm, first, openers, onOpen) }
            ChannelPage.LIVE_CATEGORIES -> CategoryListPage(vm, Kind.LIVE, first, onClose)
            ChannelPage.MOVIE_CATEGORIES -> CategoryListPage(vm, Kind.MOVIE, first, onClose)
            ChannelPage.SERIES_CATEGORIES -> CategoryListPage(vm, Kind.SERIES, first, onClose)
            ChannelPage.LANGUAGES -> { BackHandler(onBack = onClose); LanguagesPage(vm, first) }
            ChannelPage.CHANNEL_NAME_CLEANUP -> { BackHandler(onBack = onClose); NameCleanupPage(vm, first, category = false) }
            ChannelPage.CATEGORY_NAME_CLEANUP -> { BackHandler(onBack = onClose); NameCleanupPage(vm, first, category = true) }
            ChannelPage.CHANNELS -> { BackHandler(onBack = onClose); ChannelsPageRows(vm, first, openers, onOpen) }
            ChannelPage.YOUR_CATEGORIES -> { BackHandler(onBack = onClose); YourCategoriesPage(vm, first) }
            ChannelPage.MANAGE_CHANNELS -> ManageChannelsPage(vm, first, onClose)
            ChannelPage.MERGE_OPTIONS -> { BackHandler(onBack = onClose); MergeOptionsPage(vm, first, openers, onOpen) }
            ChannelPage.MERGE_PLAYLISTS -> { BackHandler(onBack = onClose); MergePlaylistsPage(vm, first) }
            ChannelPage.SUBTITLES -> { BackHandler(onBack = onClose); SubtitleRows(vm, first) }
            ChannelPage.DATE_TIME -> { BackHandler(onBack = onClose); DateTimeRows(vm, first) }
            ChannelPage.APPEARANCE_APP -> { BackHandler(onBack = onClose); AppearanceAppRows(vm, first, openers, onOpen) }
            ChannelPage.APPEARANCE_LIVE -> { BackHandler(onBack = onClose); AppearanceLiveRows(vm, first) }
            ChannelPage.APPEARANCE_VOD -> { BackHandler(onBack = onClose); AppearanceVodRows(vm, first) }
        }
    }
}

/** Logo priorities; the logo pack section shows only when a priority is "My logo pack" (user, 2026-11-12). */
@Composable
private fun ChannelLogosPage(vm: SettingsViewModel, first: FocusRequester) {
    val s by vm.settings.collectAsState()
    val matching by vm.logoMatching.collectAsState()
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PriorityRows(R.string.set_a_priority_where_logos, s.logoOrder, LogoKind.entries, 4, { stringResource(it.labelRes) }, Modifier.focusRequester(first),
            busy = { it == LogoKind.OPEN_SOURCE && matching }, defaults = DEFAULT_LOGO_ORDER) { vm.setLogoOrder(it) }
        if (matching) Text(stringResource(R.string.set_a_matching_logos), fontSize = 13.sp, color = LocalTheme.current.colors.muted)
        if (LogoKind.PACK in s.logoOrder) {
            Text(stringResource(R.string.set_a_my_logo_pack), fontSize = 15.sp, color = Color.White, modifier = Modifier.padding(top = 14.dp, bottom = 2.dp))
            LogoPackSection(vm)
        }
    }
}

@Composable
internal fun PageRows(pages: List<ChannelPage>, vm: SettingsViewModel, first: FocusRequester, openers: Map<ChannelPage, FocusRequester>, onOpen: (ChannelPage) -> Unit) {
    val groups by vm.groups.collectAsState()
    pages.forEachIndexed { i, p ->
        val m = Modifier.focusRequester(openers.getValue(p))
        SettingRow(stringResource(p.titleRes), stringResource(p.explanationRes), if (p == ChannelPage.YOUR_CATEGORIES) "${groups.size} ›" else "›",
            if (i == 0) m.focusRequester(first) else m) { onOpen(p) }
    }
}

/** One kind's provider categories: Show all / Hide all, then each category with move (OK) and Shown/Hidden. */
@Composable
private fun CategoryListPage(vm: SettingsViewModel, kind: Kind, first: FocusRequester, onClose: () -> Unit) {
    LaunchedEffect(kind) { vm.categoryKind.value = kind }
    val rows = vm.categoryRows.collectAsState().value.filter { it.category.kind == kind } // not the previous kind's list
    /** Toggles pressed but not yet written back by the database: category id → hidden. */
    val pending = remember { androidx.compose.runtime.mutableStateMapOf<Long, Boolean>() }
    var moving by remember { mutableStateOf<Long?>(null) }
    var movedFrom by remember { mutableStateOf(0) }
    BackHandler(enabled = moving == null, onBack = onClose)
    val list = rememberLazyListState()
    val movingIndex = rows.indexOfFirst { it.category.id == moving }
    // Keep the row being moved on screen (+2 for the Show all / Hide all rows above it).
    LaunchedEffect(movingIndex) {
        if (movingIndex < 0) return@LaunchedEffect
        val visible = list.layoutInfo.visibleItemsInfo.map { it.index }
        if (movingIndex + 2 !in visible.drop(1).dropLast(1)) list.scrollToItem((movingIndex + 2 - 3).coerceAtLeast(0))
    }
    val countHint = when (kind) { Kind.LIVE -> R.plurals.set_a_cat_hint_channels; Kind.MOVIE -> R.plurals.set_a_cat_hint_movies; Kind.SERIES -> R.plurals.set_a_cat_hint_series }
    val scope = rememberCoroutineScope()
    // Leaving the list (to the tabs or the group list) resets it, so coming back starts at the top.
    LazyColumn(state = list, verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.onFocusChanged { if (!it.hasFocus && moving == null) scope.launch { list.scrollToItem(0) } }) {
        item { SettingRow(stringResource(R.string.set_a_show_all), stringResource(when (kind) { Kind.LIVE -> R.string.set_a_show_all_live; Kind.MOVIE -> R.string.set_a_show_all_movies; Kind.SERIES -> R.string.set_a_show_all_series }), null, Modifier.focusRequester(first)) { pending.clear(); vm.setAllHidden(false) } }
        item { SettingRow(stringResource(R.string.set_a_hide_all), stringResource(when (kind) { Kind.LIVE -> R.string.set_a_hide_all_live; Kind.MOVIE -> R.string.set_a_hide_all_movies; Kind.SERIES -> R.string.set_a_hide_all_series }), null) { pending.clear(); vm.setAllHidden(true) } }
        items(rows, key = { it.category.id }) { r ->
            val cat = r.category
            val isMoving = moving == cat.id
            // Shown at once: the write can queue behind a playlist sync or guide import for a while (user bug, 2026-11-12).
            val hidden = pending[cat.id] ?: cat.hidden
            LaunchedEffect(cat.hidden) { if (pending[cat.id] == cat.hidden) pending.remove(cat.id) }
            // OK toggles shown/hidden, a long OK picks the category up to move (user, 2026-11-13).
            // canFocus reads `moving` itself: the captured isMoving is stale when the lambda re-runs, which dropped focus.
            FocusBox(
                onClick = { if (isMoving) moving = null else { pending[cat.id] = !hidden; vm.setHidden(cat, !hidden) } },
                // Hold OK picks it up at once, as Manage channels (no Move up / Move down box).
                modifier = Modifier.focusProperties { canFocus = moving == null || moving == cat.id }
                    .onLongOk { if (moving == null) { moving = cat.id; movedFrom = rows.indexOfFirst { it.category.id == cat.id } } }
                    .onPreviewKeyEvent { e ->
                        if (!isMoving) return@onPreviewKeyEvent false
                        val down = e.type == KeyEventType.KeyDown
                        when (e.key) {
                            Key.DirectionUp -> { if (down) vm.move(cat, -1); true }
                            Key.DirectionDown -> { if (down) vm.move(cat, 1); true }
                            Key.DirectionLeft, Key.DirectionRight -> true
                            Key.Back -> { // cancel: put it back where it started
                                if (!down) { vm.move(cat, movedFrom - rows.indexOfFirst { it.category.id == cat.id }); moving = null }
                                true
                            }
                            else -> false
                        }
                    },
            ) { f ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(if (isMoving) Color(0xFFE5A00D) else if (f) Color.White else Color(0x14FFFFFF))
                    .padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        val fg = if (f || isMoving) Color.Black else Color.White
                        Text((if (isMoving) "↕ " else "") + cat.name.ifEmpty { stringResource(R.string.set_a_uncategorised) }, fontSize = 16.sp, color = fg, maxLines = 1)
                        Text(if (isMoving) stringResource(R.string.set_a_move_hint) else pluralStringResource(countHint, r.count, r.count),
                            fontSize = 12.sp, color = if (f || isMoving) Color(0xFF555555) else LocalTheme.current.colors.muted)
                    }
                    if (!isMoving) Toggle(!hidden, f) // hidden while moving: the gold track would vanish into the gold row
                }
            }
        }
    }
}

/** Manage channels: live categories; OK opens one's channels to show/hide, move, or tick many (Reddit R1, 2026-11-15; Batch 2). */
@Composable
private fun ManageChannelsPage(vm: SettingsViewModel, first: FocusRequester, onClose: () -> Unit) {
    LaunchedEffect(Unit) { vm.categoryKind.value = Kind.LIVE }
    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    val cats = vm.categoryRows.collectAsState().value.filter { it.category.kind == Kind.LIVE }
    var open by remember { mutableStateOf<CategoryEntity?>(null) }
    var cameFrom by remember { mutableStateOf<Long?>(null) }
    val back = remember { FocusRequester() }
    // Every row is replaced on open/close: park focus on a spot that stays (else it falls to the section list and Settings jumps to its root).
    val parkSpot = remember { FocusRequester() }
    var parking by remember { mutableStateOf(false) }
    fun go(to: CategoryEntity?) { parking = true; runCatching { parkSpot.requestFocus() }; if (to == null) cameFrom = open?.id; open = to }
    BackHandler { if (open == null) onClose() else go(null) }
    LaunchedEffect(open) {
        withFrameNanos { }
        runCatching { if (open == null && cameFrom != null) back.requestFocus() else first.requestFocus() }
        parking = false
    }
    Box(Modifier.focusRequester(parkSpot).focusProperties { canFocus = parking }.focusable())
    val cat = open
    if (cat == null) {
        val list = rememberLazyListState(cats.indexOfFirst { it.category.id == cameFrom }.coerceAtLeast(0))
        if (cats.isEmpty()) Text(stringResource(R.string.set_a_no_categories), fontSize = 15.sp, color = LocalTheme.current.colors.muted)
        LazyColumn(state = list, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // `first` (where Right from the section list lands) goes on the first row on screen: a row scrolled out of a
            // lazy list isn't composed, and focusing it crashed (back from a category, which scrolls to it, then Right).
            itemsIndexed(cats, key = { _, r -> r.category.id }) { i, r ->
                val m = if (r.category.id == cameFrom) Modifier.focusRequester(back) else Modifier
                SettingRow(r.category.name.ifEmpty { stringResource(R.string.set_a_uncategorised) }, stringResource(R.string.set_a_n_shown, r.count), "›",
                    if (i == list.firstVisibleItemIndex) m.focusRequester(first) else m) { go(r.category) }
            }
        }
        return
    }
    val entry = remember(cat.id) { RailEntry.Provider(cat) }
    val stored by remember(cat.id) { vm.channelsIn(cat.id) }.collectAsState(emptyList())
    // Sort by (issue #9): listed as the guide shows them; the first move in a list that isn't Manual asks first.
    val by by remember(cat.id) { vm.sortOf(entry) }.collectAsState(null)
    val channels = by?.let { vm.sorted(stored, it) } ?: stored
    var sortOpen by remember(cat.id) { mutableStateOf(false) }
    var toManual by remember(cat.id) { mutableStateOf<org.imdtv.iptvtv.data.db.ChannelEntity?>(null) }
    val strip = org.imdtv.iptvtv.data.catalogue.channelStrip(vm.nameStrip.collectAsState().value, vm.settings.collectAsState().value.removeNameSymbols)
    // Default: OK shows/hides one channel, hold OK moves it. "Select channels" switches to ticking many, then Save changes.
    var selecting by remember(cat.id) { mutableStateOf(false) }
    val selected = remember(cat.id) { androidx.compose.runtime.mutableStateListOf<Long>() }
    /** Toggles pressed but not yet written back by the database: channel id → hidden (as the categories page). */
    val pending = remember(cat.id) { androidx.compose.runtime.mutableStateMapOf<Long, Boolean>() }
    // Back leaves select mode (dropping the ticks) before it leaves the category.
    val selectRow = remember { FocusRequester() }
    var inAllRows by remember { mutableStateOf(false) }
    // Select All / Unselect All vanish with select mode: move focus off them first (a removed focused row sends Settings to its root).
    BackHandler(enabled = selecting) { if (inAllRows) runCatching { selectRow.requestFocus() }; selected.clear(); selecting = false }
    var noneTicked by remember { mutableStateOf(false) }
    var savedMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(savedMessage) { if (savedMessage != null) { kotlinx.coroutines.delay(3_000); savedMessage = null } }
    fun hiddenNow(ch: org.imdtv.iptvtv.data.db.ChannelEntity) = pending[ch.id] ?: ch.hidden
    fun toggle(ch: org.imdtv.iptvtv.data.db.ChannelEntity) { val h = !hiddenNow(ch); pending[ch.id] = h; vm.setChannelsHidden(listOf(ch.id), h) }
    // Channels with others merged in: a second OK within DOUBLE_TAP_MS views them instead of showing/hiding.
    val counts by remember(cat.id) { vm.mergeCounts(cat.id) }.collectAsState(emptyMap())
    var viewing by remember(cat.id) { mutableStateOf<org.imdtv.iptvtv.data.db.ChannelEntity?>(null) }
    val scope = rememberCoroutineScope()
    val tap = remember { arrayOfNulls<kotlinx.coroutines.Job>(1) }
    val tapId = remember { LongArray(1) }
    viewing?.let { h -> org.imdtv.iptvtv.ui.live.MergedChannelsMenu(h, strip, { vm.mergedMembers(h) }, { c, on -> vm.setMerged(c, h, on) }) { viewing = null } }
    var choose by remember { mutableStateOf(false) }
    /** Hide or show every ticked channel. */
    fun apply(hide: Boolean) {
        val ids = channels.filter { it.id in selected && hiddenNow(it) != hide }.map { it.id }
        ids.forEach { pending[it] = hide }
        if (ids.isNotEmpty()) vm.setChannelsHidden(ids, hide)
        savedMessage = if (hide) channelsSavedMessage(resources, ids.size, 0) else channelsSavedMessage(resources, 0, ids.size)
        selected.clear(); selecting = false; choose = false
    }
    // Hold OK picks a channel up (as Manage provider categories' move): Up/Down carry it, OK drops it, Back puts it back.
    var moving by remember(cat.id) { mutableStateOf<Long?>(null) }
    var movedFrom by remember(cat.id) { mutableStateOf(0) }
    val list = rememberLazyListState()
    val movingIndex = channels.indexOfFirst { it.id == moving }
    // Keep the row being moved on screen (+3 for the title, Sort by and Select rows above it).
    LaunchedEffect(movingIndex) {
        if (movingIndex < 0) return@LaunchedEffect
        if (movingIndex + 3 !in list.layoutInfo.visibleItemsInfo.map { it.index }.drop(1).dropLast(1)) list.scrollToItem((movingIndex + 3 - 3).coerceAtLeast(0))
    }
    if (sortOpen) org.imdtv.iptvtv.ui.live.SortMenu(by, { vm.setSort(entry, it) }, { vm.sortOnce(entry) }) { sortOpen = false }
    toManual?.let { ch -> org.imdtv.iptvtv.ui.live.ToManualConfirm({
        vm.setSort(entry, org.imdtv.iptvtv.data.db.SortBy.MANUAL)
        moving = ch.id; movedFrom = channels.indexOfFirst { it.id == ch.id }; toManual = null
    }) { toManual = null } }
    // Closing the dialog gives focus back to the row that opened it.
    if (noneTicked) ContextMenu(stringResource(R.string.set_a_no_channels_selected), listOf(stringResource(R.string.set_a_ok) to { noneTicked = false })) { noneTicked = false }
    val n = selected.size
    if (choose) ContextMenu(pluralStringResource(R.plurals.set_a_channels_ticked, n, n), listOf(
        stringResource(R.string.set_a_hide_channels) to { apply(true) }, stringResource(R.string.set_a_show_channels) to { apply(false) })) { choose = false }
    val muted = LocalTheme.current.colors.muted
    LazyColumn(state = list, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Text(cat.name.ifEmpty { stringResource(R.string.set_a_uncategorised) }, fontSize = 15.sp, color = muted, modifier = Modifier.padding(bottom = 6.dp)) }
        item { SortByRow(by, (if (list.firstVisibleItemIndex <= 1) Modifier.focusRequester(first) else Modifier).focusProperties { canFocus = moving == null }) { sortOpen = true } }
        // One row whose label changes: swapping two rows would drop focus (and Settings would jump to its root).
        // Select All / Unselect All live in this item too, not items of their own: the index maths for `first` and the moving row stays.
        item { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SettingRow(if (selecting) stringResource(R.string.set_a_hide_or_show) else stringResource(R.string.set_a_select_channels),
                if (!selecting) stringResource(R.string.set_a_select_channels_expl)
                else if (n == 0) stringResource(R.string.set_a_tick_channels_hint) else pluralStringResource(R.plurals.set_a_hide_or_show_ticked, n, n),
                null, (if (list.firstVisibleItemIndex == 2) Modifier.focusRequester(first) else Modifier).focusRequester(selectRow).focusProperties { canFocus = moving == null }) {
                if (!selecting) selecting = true else if (n == 0) noneTicked = true else choose = true
            }
            if (selecting) Column(Modifier.onFocusChanged { inAllRows = it.hasFocus }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SettingRow(stringResource(R.string.set_a_select_all), stringResource(R.string.set_a_select_all_expl), null) {
                    selected.clear(); selected.addAll(channels.map { it.id }) // hidden ones too (user, 2026-11-22)
                }
                SettingRow(stringResource(R.string.set_a_unselect_all), stringResource(R.string.set_a_unselect_all_expl), null) { selected.clear() }
            }
        } }
        itemsIndexed(channels, key = { _, ch -> ch.id }) { i, ch ->
            val ticked = ch.id in selected
            val hidden = hiddenNow(ch)
            val isMoving = moving == ch.id
            LaunchedEffect(ch.hidden) { if (pending[ch.id] == ch.hidden) pending.remove(ch.id) }
            FocusBox(
                onClick = {
                    when {
                        isMoving -> moving = null
                        selecting -> if (ticked) selected.remove(ch.id) else selected.add(ch.id)
                        (counts[ch.id] ?: 0) == 0 -> toggle(ch)
                        tap[0]?.isActive == true && tapId[0] == ch.id -> { tap[0]?.cancel(); viewing = ch }
                        else -> { tapId[0] = ch.id; tap[0] = scope.launch { kotlinx.coroutines.delay(DOUBLE_TAP_MS); toggle(ch) } }
                    }
                },
                // canFocus reads `moving` itself: a captured value is stale when the lambda re-runs.
                // Scrolled past the Select row: `first` moves to the first channel on screen (items 0–2 are the title, Sort by and Select).
                modifier = (if (list.firstVisibleItemIndex > 2 && i == list.firstVisibleItemIndex - 3) Modifier.focusRequester(first) else Modifier)
                    .focusProperties { canFocus = moving == null || moving == ch.id }
                    .onLongOk { if (!selecting && moving == null) { if (by != org.imdtv.iptvtv.data.db.SortBy.MANUAL) toManual = ch else { moving = ch.id; movedFrom = channels.indexOfFirst { it.id == ch.id } } } }
                    .onPreviewKeyEvent { e ->
                        if (!isMoving) return@onPreviewKeyEvent false
                        val down = e.type == KeyEventType.KeyDown
                        when (e.key) {
                            Key.DirectionUp -> { if (down) vm.moveChannel(ch, -1); true }
                            Key.DirectionDown -> { if (down) vm.moveChannel(ch, 1); true }
                            Key.DirectionLeft, Key.DirectionRight -> true
                            Key.Back -> { // cancel: put it back where it started
                                if (!down) { vm.moveChannel(ch, movedFrom - channels.indexOfFirst { it.id == ch.id }); moving = null }
                                true
                            }
                            else -> false
                        }
                    },
            ) { f ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(if (isMoving) Color(0xFFE5A00D) else if (f) Color.White else Color(0x14FFFFFF))
                    .padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    val dark = f || isMoving
                    Column(Modifier.weight(1f)) {
                        Text((if (isMoving) "↕ " else "") + ch.displayName(strip), fontSize = 16.sp, color = if (dark) Color.Black else Color.White, maxLines = 1)
                        Text(when { isMoving -> stringResource(R.string.set_a_move_hint); selecting -> if (hidden) stringResource(R.string.set_a_hidden) else stringResource(R.string.set_a_shown)
                            (counts[ch.id] ?: 0) > 0 -> counts.getValue(ch.id).let { n -> stringResource(R.string.set_a_merged_row_hint, pluralStringResource(R.plurals.set_a_n_merged, n, n)) }
                            else -> stringResource(R.string.set_a_channel_row_hint) }, fontSize = 12.sp, color = if (dark) Color(0xFF555555) else muted)
                    }
                    // Select mode: the switch becomes a tick box. Moving: neither (the gold track would vanish into the gold row).
                    if (selecting) Box(Modifier.size(22.dp).clip(RoundedCornerShape(4.dp))
                        .background(if (ticked) LocalTheme.current.colors.accent else Color.Transparent)
                        .border(2.dp, if (ticked) LocalTheme.current.colors.accent else if (f) Color(0xFF555555) else muted, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center) { if (ticked) Text("✓", fontSize = 14.sp, color = Color.White) }
                    else if (!isMoving) Toggle(!hidden, f)
                }
            }
        }
    }
    savedMessage?.let { msg ->
        // Not focusable: the bar never takes focus from the list.
        androidx.compose.ui.window.Popup(alignment = Alignment.BottomCenter) { org.imdtv.iptvtv.ui.InfoBar(msg, Modifier.padding(bottom = 40.dp)) } // at the foot of the pane, about where the exit hint sits
    }
}

@Composable
private fun LanguagesPage(vm: SettingsViewModel, first: FocusRequester) {
    val langs by vm.languages.collectAsState()
    val s by vm.settings.collectAsState()
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SwitchRow(stringResource(R.string.set_a_match_tv_language), stringResource(R.string.set_a_match_tv_language_expl, vm.tvLanguage),
            s.matchTvLanguage, Modifier.focusRequester(first).padding(bottom = 12.dp)) { vm.setMatchTvLanguage(it) }
        if (langs.isEmpty()) Text(stringResource(R.string.set_a_no_categories), fontSize = 15.sp, color = LocalTheme.current.colors.muted)
        langs.forEach { l ->
            val shown = l.countries.count { it.shown }
            val value = when (shown) { l.countries.size -> stringResource(R.string.set_a_shown); 0 -> stringResource(R.string.set_a_hidden); else -> stringResource(R.string.set_a_partly) }
            val explanation = if (l.countries.size == 1) l.countries[0].label else pluralStringResource(R.plurals.set_a_n_countries, l.countries.size, l.countries.size)
            SettingRow(l.language, explanation, value) {
                vm.setCountriesShown(l.countries.map { it.country }, shown != l.countries.size)
            }
            if (l.countries.size > 1) l.countries.forEach { c ->
                SwitchRow(c.label, stringResource(R.string.set_a_every_category_as, c.label), c.shown, Modifier.padding(start = 32.dp)) {
                    vm.setCountriesShown(listOf(c.country), it)
                }
            }
        }
    }
}

@Composable
private fun NameCleanupPage(vm: SettingsViewModel, first: FocusRequester, category: Boolean) {
    val strip by (if (category) vm.categoryStrip else vm.nameStrip).collectAsState()
    val preview by (if (category) vm.categoryStripPreview else vm.stripPreview).collectAsState()
    val draft = if (category) vm.categoryStripDraft else vm.stripDraft
    val save: (String) -> Unit = if (category) vm::setCategoryStrip else { s -> vm.setNameStrip(s) }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // One saved list: plain entries remove, "find=replace" entries rename (parseStripList).
        val (renames, removes) = parseStripList(strip).partition { '=' in it }
        fun join(a: List<String>, b: String?) = (a + parseStripList(b.orEmpty())).joinToString(", ")
        val removeText = removes.joinToString(", "); val renameText = renames.joinToString(", ")
        EditRow(stringResource(R.string.set_a_strip_remove), stringResource(R.string.set_a_strip_remove_expl), removeText.ifBlank { stringResource(R.string.set_a_none) }, removeText,
            Modifier.focusRequester(first), onDraft = { d -> draft.value = d?.let { join(renames, it) } }) { save(join(renames, it)) }
        EditRow(stringResource(R.string.set_a_strip_rename), stringResource(R.string.set_a_strip_rename_expl), renameText.ifBlank { stringResource(R.string.set_a_none) }, renameText,
            onDraft = { d -> draft.value = d?.let { join(removes, it) } }) { save(join(removes, it)) }
        Text(stringResource(R.string.set_a_preview), fontSize = 15.sp, color = Color.White, modifier = Modifier.padding(top = 12.dp))
        if (preview.isEmpty()) Text(if (category) stringResource(R.string.set_a_no_category_names_change) else stringResource(R.string.set_a_no_channel_names_change), fontSize = 14.sp, color = LocalTheme.current.colors.muted)
        preview.forEach { (before, after) -> Text("$before  →  $after", fontSize = 14.sp, color = Color.White, maxLines = 1) }
    }
}

/** What the Options button of one of your categories has open. */
private sealed interface GroupMenu {
    val g: CustomGroupEntity
    data class Options(override val g: CustomGroupEntity) : GroupMenu
    data class Rename(override val g: CustomGroupEntity) : GroupMenu
    data class Delete(override val g: CustomGroupEntity) : GroupMenu
    data class Priority(override val g: CustomGroupEntity) : GroupMenu
}

@Composable
private fun GroupPlacement.label() = stringResource(when (this) {
    GroupPlacement.TOP -> R.string.set_a_place_top; GroupPlacement.NORMAL -> R.string.set_a_place_above; GroupPlacement.BOTTOM -> R.string.set_a_place_below
})

/** [order] with the item at [i] moved [d] steps, staying among items with the same placement; null if it can't. */
internal fun <T> moveWithin(order: List<T>, i: Int, d: Int, placement: (T) -> GroupPlacement): List<T>? {
    val j = i + d
    if (i !in order.indices || j !in order.indices || placement(order[j]) != placement(order[i])) return null
    return order.toMutableList().apply { add(j, removeAt(i)) }
}

/** Add, then each of your categories on one line: its name (OK to move, like provider categories) and an Options button (Rename, Delete, Priority). */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun YourCategoriesPage(vm: SettingsViewModel, first: FocusRequester) {
    val saved by vm.groups.collectAsState()
    val favouritesTitle = stringResource(R.string.set_a_favourites)
    var menu by remember { mutableStateOf<GroupMenu?>(null) }
    var moving by remember { mutableStateOf<Long?>(null) }
    // Channel order of one category (null id = Favourites) and its title; replaces the list while open.
    var ordering by remember { mutableStateOf<Pair<Long?, String>?>(null) }
    // Every row is replaced on open/close: park focus on a spot that stays (else it falls to the section list and Settings jumps to its root).
    val parkSpot = remember { FocusRequester() }
    val orderFirst = remember { FocusRequester() }
    var parking by remember { mutableStateOf(false) }
    fun showOrder(o: Pair<Long?, String>?) { parking = true; runCatching { parkSpot.requestFocus() }; ordering = o }
    LaunchedEffect(ordering) {
        if (!parking) return@LaunchedEffect
        // The channel list loads a moment after the page opens: try each frame for ~½ s. An empty category has no row
        // to focus, so focus stays parked until Back.
        repeat(30) {
            withFrameNanos { }
            if (runCatching { (if (ordering != null) orderFirst else first).requestFocus() }.isSuccess) { parking = false; return@LaunchedEffect }
        }
    }
    Box(Modifier.focusRequester(parkSpot).focusProperties { canFocus = parking }.focusable())
    ordering?.let { (id, title) ->
        BackHandler { showOrder(null) }
        val entry = if (id == null) RailEntry.Favourites else saved.find { it.id == id }?.let { RailEntry.Custom(it) }
        if (entry != null) ChannelOrderPage(vm, entry, title, orderFirst)
        return
    }
    // While moving, this page's order leads (fast presses mustn't wait for the database); grouped by side of the list.
    var order by remember { mutableStateOf<List<org.imdtv.iptvtv.data.db.CustomGroupEntity>?>(null) }
    val groups = order ?: saved.sortedBy { it.placement.ordinal }
    fun drop() { moving = null; order?.let(vm::saveGroupOrder) }
    BackHandler(enabled = moving != null) { moving = null; order = null } // cancel: back where it was
    LaunchedEffect(saved) { if (moving == null) order = null }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        EditRow(stringResource(R.string.set_a_add_category), stringResource(R.string.set_a_add_category_expl), null, "", Modifier.focusRequester(first)) {
            if (it.isNotBlank()) vm.createGroup(it)
        }
        Text(stringResource(R.string.set_a_add_channels_hint), fontSize = 12.sp, color = LocalTheme.current.colors.muted,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
        SettingRow(stringResource(R.string.set_a_favourites_star), stringResource(R.string.set_a_favourites_expl), "›") { showOrder(null to favouritesTitle) }
        groups.forEach { g ->
            key(g.id) {
                val isMoving = moving == g.id
                // No BringIntoViewRequester on the moving row: it drops focus to the Settings root (as on the channel order page).
                // shortcut: a long list doesn't follow the moving row; make it a LazyColumn like ChannelOrderPage if users have many.
                // OK opens Options (rename, delete, priority), a long OK picks it up to move (user, 2026-11-13).
                SettingRow((if (isMoving) "↕ " else "") + g.name,
                    if (isMoving) stringResource(R.string.set_a_move_hint) else stringResource(R.string.set_a_group_row_hint, g.placement.label()), null,
                    // canFocus reads `moving` itself: the captured isMoving is stale when the lambda re-runs, which dropped focus.
                    Modifier.focusProperties { canFocus = moving == null || moving == g.id }
                        .onLongOk { if (moving == null) { order = groups; moving = g.id } }
                        .onPreviewKeyEvent { e ->
                            if (!isMoving) return@onPreviewKeyEvent false
                            val d = when (e.key) { Key.DirectionUp -> -1; Key.DirectionDown -> 1; Key.DirectionLeft, Key.DirectionRight -> 0; else -> return@onPreviewKeyEvent false }
                            if (d != 0 && e.type == KeyEventType.KeyDown) moveWithin(groups, groups.indexOf(g), d) { it.placement }?.let { order = it }
                            true
                        }, moving = isMoving) { if (isMoving) drop() else menu = GroupMenu.Options(g) }
            }
        }
    }
    when (val m = menu) {
        null -> Unit
        is GroupMenu.Options -> ContextMenu(stringResource(R.string.set_a_quoted, m.g.name), listOf(
            stringResource(R.string.set_a_rename_ellipsis) to { menu = GroupMenu.Rename(m.g) },
            stringResource(R.string.set_a_delete) to { menu = GroupMenu.Delete(m.g) },
            stringResource(R.string.set_a_priority_arrow) to { menu = GroupMenu.Priority(m.g) },
            stringResource(R.string.set_a_channel_order_arrow) to { menu = null; showOrder(m.g.id to m.g.name) },
        )) { menu = null }
        is GroupMenu.Rename -> TextPrompt(stringResource(R.string.set_a_rename_title, m.g.name), m.g.name, stringResource(R.string.set_a_name_hint), onSave = { if (it.isNotBlank()) vm.renameGroup(m.g, it); menu = null }) { menu = null }
        is GroupMenu.Delete -> ContextMenu(stringResource(R.string.set_a_delete_group_title, m.g.name), listOf(
            // The row is about to vanish: focus Add category first, or focus falls to the group list.
            stringResource(R.string.set_a_delete) to { menu = null; runCatching { first.requestFocus() }; vm.deleteGroup(m.g) },
            stringResource(R.string.set_a_cancel) to { menu = null },
        )) { menu = null }
        is GroupMenu.Priority -> ContextMenu(stringResource(R.string.set_a_priority), GroupPlacement.entries.map { p ->
            ((if (p == m.g.placement) "✓ " else "    ") + p.label()) to { vm.setGroupPlacement(m.g, p); menu = null }
        }) { menu = null }
    }
}

/** The channels of Favourites (id null) or one of your categories: OK picks one up, Up/Down moves it, OK drops it. */
@Composable
private fun ChannelOrderPage(vm: SettingsViewModel, entry: RailEntry, title: String, first: FocusRequester) {
    val groupId = (entry as? RailEntry.Custom)?.group?.id
    val stored by remember(groupId) { vm.orderedChannels(groupId) }.collectAsState(emptyList())
    // Sort by (issue #9): the list as the guide shows it; picking a channel up in a list that isn't Manual asks first.
    val by by remember(entry.key) { vm.sortOf(entry) }.collectAsState(null)
    val saved = by?.let { vm.sorted(stored, it) } ?: stored
    var sortOpen by remember { mutableStateOf(false) }
    var toManual by remember { mutableStateOf<org.imdtv.iptvtv.data.db.ChannelEntity?>(null) }
    var picked by remember { mutableStateOf<Long?>(null) }
    var order by remember { mutableStateOf<List<org.imdtv.iptvtv.data.db.ChannelEntity>?>(null) }
    val channels = order ?: saved
    fun drop() { picked = null; order?.let { vm.saveChannelOrder(groupId, it) } }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    BackHandler(enabled = picked != null) { // cancel: back where it was, with the list scrolled to it
        val at = saved.indexOfFirst { it.id == picked }
        picked = null; order = null
        scope.launch { list.scrollToItem((at + 1 - 3).coerceAtLeast(0)) }
    }
    LaunchedEffect(saved) { if (picked == null) order = null }
    val pickedIndex = channels.indexOfFirst { it.id == picked }
    // Keep the row being moved on screen (+2 for the hint and Sort by rows above it), as provider categories do.
    LaunchedEffect(pickedIndex) {
        if (pickedIndex < 0) return@LaunchedEffect
        val visible = list.layoutInfo.visibleItemsInfo.map { it.index }
        if (pickedIndex + 2 !in visible.drop(1).dropLast(1)) list.scrollToItem((pickedIndex + 2 - 3).coerceAtLeast(0))
    }
    if (sortOpen) org.imdtv.iptvtv.ui.live.SortMenu(by, { vm.setSort(entry, it) }, { vm.sortOnce(entry) }) { sortOpen = false }
    toManual?.let { ch -> org.imdtv.iptvtv.ui.live.ToManualConfirm({ vm.setSort(entry, org.imdtv.iptvtv.data.db.SortBy.MANUAL); order = channels; picked = ch.id; toManual = null }) { toManual = null } }
    LazyColumn(state = list, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Text(stringResource(R.string.set_a_order_hint, title), fontSize = 12.sp, color = LocalTheme.current.colors.muted,
                modifier = Modifier.padding(bottom = 8.dp))
        }
        item { SortByRow(by, Modifier.focusRequester(first).focusProperties { canFocus = picked == null }) { sortOpen = true } }
        items(channels, key = { it.id }) { ch ->
            val isPicked = picked == ch.id
            SettingRow((if (isPicked) "↕ " else "") + ch.displayName(emptyList()), if (isPicked) stringResource(R.string.set_a_move_hint) else "", null,
                Modifier.focusProperties { canFocus = picked == null || picked == ch.id }
                    .onPreviewKeyEvent { e ->
                        if (!isPicked) return@onPreviewKeyEvent false
                        val d = when (e.key) { Key.DirectionUp -> -1; Key.DirectionDown -> 1; Key.DirectionLeft, Key.DirectionRight -> 0; else -> return@onPreviewKeyEvent false }
                        if (d != 0 && e.type == KeyEventType.KeyDown) moveWithin(channels, channels.indexOf(ch), d) { GroupPlacement.NORMAL }?.let { order = it }
                        true
                    }, moving = isPicked) { if (isPicked) drop() else if (by != org.imdtv.iptvtv.data.db.SortBy.MANUAL) toManual = ch else { order = channels; picked = ch.id } }
        }
    }
}

/** Merge options: Merge across, Merge by, Playlists ›. */
@Composable
private fun MergeOptionsPage(vm: SettingsViewModel, first: FocusRequester, openers: Map<ChannelPage, FocusRequester>, onOpen: (ChannelPage) -> Unit) {
    val s by vm.settings.collectAsState()
    ChoiceRow(stringResource(R.string.set_a_merge_scope), stringResource(R.string.set_a_merge_scope_expl), listOf(false, true), s.mergeAcross,
        { stringResource(if (it) R.string.set_a_merge_scope_across else R.string.set_a_merge_scope_same) }, Modifier.focusRequester(first), default = false) { vm.setMergeAcross(it) }
    ChoiceRow(stringResource(R.string.set_a_merge_by), stringResource(R.string.set_a_merge_by_expl), org.imdtv.iptvtv.data.settings.MergeBy.entries, s.mergeBy,
        { stringResource(it.labelRes) }, default = AppSettings().mergeBy) { vm.setMergeBy(it) }
    val lists = ChannelPage.MERGE_PLAYLISTS
    SettingRow(stringResource(lists.titleRes), stringResource(lists.explanationRes), "›", Modifier.focusRequester(openers.getValue(lists))) { onOpen(lists) }
}

/** Merge options › Playlists: a switch per playlist. */
@Composable
private fun MergePlaylistsPage(vm: SettingsViewModel, first: FocusRequester) {
    val providers by vm.providers.collectAsState()
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        providers.forEachIndexed { i, p ->
            SwitchRow(p.name, stringResource(R.string.set_a_merge_playlist_expl), p.mergeOn, if (i == 0) Modifier.focusRequester(first) else Modifier) {
                vm.setPlaylistMerge(p.id, it)
            }
        }
    }
}

private const val DOUBLE_TAP_MS = 400L

/** Sort by › for one category's channels (Manage channels, channel order): opens the guide's Sort by menu. */
@Composable
private fun SortByRow(by: org.imdtv.iptvtv.data.db.SortBy?, modifier: Modifier, onClick: () -> Unit) =
    SettingRow(stringResource(R.string.live_menu_sort_by), stringResource(R.string.set_a_sort_by_expl), by?.let { org.imdtv.iptvtv.ui.live.sortLabel(it) + "  ›" }, modifier, onClick = onClick)
