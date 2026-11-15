package org.imdtv.iptvtv.ui.live

import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import org.imdtv.iptvtv.data.catalogue.displayName
import org.imdtv.iptvtv.data.db.ChannelEntity
import org.imdtv.iptvtv.data.repo.GuideProgramme
import org.imdtv.iptvtv.ui.FocusBox
import org.imdtv.iptvtv.ui.clockTime
import org.imdtv.iptvtv.ui.onLongOk
import org.imdtv.iptvtv.ui.theme.LocalTheme

/** How a theme draws the timeline grid. A field is added by the first theme that needs it. */
data class GridStyle(
    val rowHeight: Dp, val channelCol: Dp, val dpPerMin: Dp, val windowMin: Int, val gap: Dp, val font: FontWeight,
    /** Background and text of the focused row's current programme (flat cells add a light border). */
    val focusedProgramme: Color, val focusedProgrammeText: Color = Color.White,
    /** Opaque separate boxes (Plus/Retro) instead of flat translucent cells that touch (Glass). */
    val boxed: Boolean = false,
    /** Channel column reads "101 BBC One" instead of "BBC One   101". */
    val numberFirst: Boolean = false,
    /** ◀ ▶ marks where a programme runs past the window's start or end. */
    val overflowArrows: Boolean = false,
    /** "Today" and the times; null = white and muted (for a grid on a dark background). */
    val headerColor: Color? = null,
    /** A thin light line under each row (Xcube: rows touch, gap 0). */
    val rowDividers: Boolean = false,
    /** Flat cells with slightly rounded corners and a small gap instead of touching (Glass, Liquid). */
    val roundedCells: Boolean = false,
)

/** Celestial Glass: mockup 01, 420px per half hour (1dp = 2px on a 1080p TV). */
// 5.9dp/min: 195 + 5 + 120 × 5.9 = 908dp fits between the 24dp margins (the TV's overscan cut off a grid that ran to the edge).
val GLASS_GRID = GridStyle(32.dp, 195.dp, 5.9.dp, 120, 4.dp, FontWeight.Medium, focusedProgramme = Color(0x8C7891AA), roundedCells = true) // frosted glass

/** Liquid Glass: the Glass grid inside one glass panel; the focused programme is a brighter tint (spec §6A.6). */
// dpPerMin 5.75: 195 + 120 × 5.75 = 885dp fits the 888dp inside the inset glass panel.
val LIQUID_GRID = GLASS_GRID.copy(focusedProgramme = Color(0x59FFFFFF), dpPerMin = 5.75.dp)

/** Xcube 180 (spec §6A.7): flat rows with thin light dividers, pale-green focus, ~6 rows × 1½ hours. */
val XCUBE_GRID = GridStyle(30.dp, 200.dp, 6.dp, 90, 0.dp, FontWeight.Medium, focusedProgramme = Color(0xFFE8F5D8),
    focusedProgrammeText = Color(0xFF1A3A0E), rowDividers = true)

/** Celestial Plus (spec §6A.4): boxed cells, number first, yellow focus, overflow arrows, 1½ hours. */
val PLUS_GRID = GridStyle(30.dp, 215.dp, 7.75.dp, 90, 3.dp, FontWeight.SemiBold, focusedProgramme = Color(0xFFF2C94C),
    focusedProgrammeText = Color(0xFF0A1A5C), boxed = true, numberFirst = true, overflowArrows = true)

/** Celestial Retro (spec §6A.5): 10 rows × 1½ hours, bordered royal-blue boxes, bold, yellow focus, navy header on sky. */
val RETRO_GRID = GridStyle(27.dp, 180.dp, 7.5.dp, 90, 3.dp, FontWeight.Bold, focusedProgramme = Color(0xFFFFCC33),
    focusedProgrammeText = Color(0xFF15257A), boxed = true, numberFirst = true, overflowArrows = true, headerColor = Color(0xFF15257A))

private val PILL = RoundedCornerShape(5.dp)
private val BOX = RoundedCornerShape(2.dp)
private val CELL = RoundedCornerShape(4.dp)
private val SEPARATOR = Color(0x40FFFFFF)
private val FOCUS_BORDER = Color(0xCCFFFFFF)

/** Header (Today + times), the channel rows and the now line. Left at the window start calls [onLeftAtStart];
 *  with [onUpAtTop], Up on the first row calls it instead of moving focus. */
@Composable
fun GuideGrid(
    vm: LiveViewModel, state: GuideState, style: GridStyle, onFullScreen: (ChannelEntity) -> Unit, onLeftAtStart: () -> Unit,
    modifier: Modifier = Modifier, onUpAtTop: (() -> Unit)? = null,
) {
    val channels by vm.channels.collectAsState()
    val programmes by vm.programmes.collectAsState()
    val now by vm.now.collectAsState()
    val windowStart by vm.windowStart.collectAsState()
    val focused by vm.focused.collectAsState()
    val strip by vm.strip.collectAsState()
    val marks by vm.marks.collectAsState()
    val recordingOnChannel by vm.recordingOnChannel.collectAsState()
    Column(modifier) {
        Row(Modifier.height(25.dp).drawBehind { drawRect(Color(0x33FFFFFF), Offset(0f, size.height - 1.dp.toPx()), Size(size.width, 1.dp.toPx())) },
            verticalAlignment = Alignment.Bottom) {
            Text(if (windowStart - now > 12 * 3600_000) "Later" else "Today", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = style.headerColor ?: Color.White, modifier = Modifier.width(style.channelCol).padding(bottom = 5.dp))
            for (i in 0 until style.windowMin / 30) {
                Text(clockTime(windowStart + i * LiveViewModel.HALF_HOUR), fontSize = 11.sp, color = style.headerColor ?: LocalTheme.current.colors.muted,
                    fontWeight = if (style.headerColor != null) FontWeight.Bold else null,
                    modifier = Modifier.width(style.dpPerMin * 30).padding(bottom = 5.dp))
            }
        }
        Box(Modifier.fillMaxSize().clipToBounds()) {
            LazyColumn(state = state.list, verticalArrangement = Arrangement.spacedBy(style.gap), modifier = Modifier.fillMaxSize()) {
                itemsIndexed(channels, key = { _, ch -> ch.id }) { i, ch ->
                    GuideRow(
                        ch, ch.displayName(strip), programmes[ch.id].orEmpty(), now, windowStart, style, isFocused = focused?.id == ch.id && !state.railOpen && state.heldFocus == null,
                        marked = { p -> (ch.id to p.start) in marks || ch.id in recordingOnChannel && p.start <= now && now < p.stop },
                        modifier = (if (i == state.focusIndex) Modifier.focusRequester(state.rowFocus) else Modifier)
                            .onPreviewKeyEvent { e ->
                                if (state.railOpen) return@onPreviewKeyEvent true // rail is taking focus; don't move the guide behind it
                                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (e.key) {
                                    Key.DirectionLeft -> { if (!vm.shiftTime(-1)) { state.focusIndex = i; onLeftAtStart() }; true }
                                    Key.DirectionRight -> { vm.shiftTime(1); true }
                                    Key.DirectionUp -> if (i == 0 && onUpAtTop != null) { onUpAtTop(); true } else false
                                    else -> false
                                }
                            }
                            .onLongOk { state.focusIndex = i; state.menu = GuideMenu.Channel(ch) },
                        onFocused = { vm.focused.value = ch; state.focusIndex = i },
                        onClick = { state.onOk(ch, onFullScreen) },
                    )
                }
            }
            val nowMin = (now - windowStart) / 60_000
            if (nowMin in 0..style.windowMin) {
                Box(Modifier.offset(x = style.channelCol + style.dpPerMin * nowMin.toInt()).width(1.dp).fillMaxHeight().background(Color(0xB3FFFFFF)))
            }
        }
    }
}

@Composable
private fun GuideRow(
    ch: ChannelEntity, name: String, progs: List<GuideProgramme>, now: Long, windowStart: Long, style: GridStyle, isFocused: Boolean,
    marked: (GuideProgramme) -> Boolean, modifier: Modifier, onFocused: () -> Unit, onClick: () -> Unit,
) {
    val colors = LocalTheme.current.colors
    Row(Modifier.height(style.rowHeight).then(if (style.rowDividers) Modifier.drawBehind {
        drawRect(Color(0x40FFFFFF), Offset(0f, size.height - 1.dp.toPx()), Size(size.width, 1.dp.toPx()))
    } else Modifier)) {
        FocusBox(onClick = onClick, onFocused = onFocused, modifier = modifier.width(style.channelCol - 5.dp).fillMaxHeight()) { f ->
            Row(
                Modifier.fillMaxSize()
                    .graphicsLayer { val s = if (f && !style.boxed) 1.03f else 1f; scaleX = s; scaleY = s }
                    .then(if (f && !style.boxed) Modifier.shadow(14.dp, RoundedCornerShape(5.dp), ambientColor = Color(0xFFA0BEFF), spotColor = Color(0xFFA0BEFF)) else Modifier)
                    .background(if (f) colors.focus else colors.cell, if (style.boxed) BOX else PILL)
                    .padding(horizontal = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val fg = if (f) colors.onFocus else Color.White
                val number = ch.customNumber ?: ch.number
                if (style.numberFirst) {
                    Text(listOfNotNull(number, name).joinToString("  "), fontSize = 14.sp, fontWeight = style.font, color = fg, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                } else {
                    Text(name, fontSize = 14.sp, fontWeight = style.font, color = fg, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    number?.let { Text("  $it", fontSize = 12.sp, color = fg.copy(alpha = .85f)) }
                }
            }
        }
        Spacer(Modifier.width(5.dp))
        Box(Modifier.fillMaxSize().clipToBounds()) {
            val end = windowStart + style.windowMin * 60_000L
            if (progs.none { it.stop > windowStart && it.start < end }) {
                // Shaped and spaced like a programme cell of this style.
                Box(Modifier.width(style.dpPerMin * style.windowMin - when { style.boxed -> 3.dp; style.roundedCells -> 2.dp; else -> 0.dp })
                    .fillMaxHeight().background(colors.cellDim, when { style.boxed -> BOX; style.roundedCells -> CELL; else -> RectangleShape }),
                    contentAlignment = Alignment.CenterStart) {
                    Text(if (progs.isEmpty()) "No guide information" else "", fontSize = 12.sp, color = colors.muted, modifier = Modifier.padding(horizontal = 10.dp))
                }
            }
            for (p in progs) {
                val span = Timeline.span(p.start, p.stop, windowStart, end) ?: continue
                val isNow = p.start <= now && now < p.stop
                val past = p.stop <= now
                val focusedNow = isFocused && isNow
                val cell = when {
                    // Boxed: separate opaque boxes with a small gap (spec §6A.4).
                    style.boxed -> Modifier.width(style.dpPerMin * span.lengthMin - 3.dp)
                        .background(if (focusedNow) style.focusedProgramme else colors.cell, BOX)
                    // Flat cells: rounded with a small gap (Glass, Liquid) or touching (Xcube).
                    focusedNow -> Modifier.width(style.dpPerMin * span.lengthMin - if (style.roundedCells) 2.dp else 0.dp)
                        .background(style.focusedProgramme, if (style.roundedCells) CELL else RectangleShape)
                        .border(1.dp, FOCUS_BORDER, if (style.roundedCells) CELL else RectangleShape)
                    // Glass/Liquid: the theme's cell colour plus a faint edge, so boxes stay distinct on the lighter right of the background.
                    style.roundedCells -> Modifier.width(style.dpPerMin * span.lengthMin - 2.dp)
                        .background(colors.cellDim.copy(alpha = if (past) .19f else .35f), CELL)
                        .border(1.dp, Color.White.copy(alpha = if (past) .08f else .16f), CELL)
                    else -> Modifier.width(style.dpPerMin * span.lengthMin)
                        .background(colors.cellDim.copy(alpha = if (past) .19f else .35f), RectangleShape)
                }
                val runsBefore = style.overflowArrows && p.start < windowStart
                val runsAfter = style.overflowArrows && p.stop > end
                val rec = marked(p)
                Box(
                    Modifier.offset(x = style.dpPerMin * span.offsetMin).fillMaxHeight().then(cell)
                        // Separator (flat) or ◀ ▶ overflow marks (boxed), drawn in the same pass: no extra nodes.
                        .drawBehind {
                            if (!style.boxed && !style.roundedCells && p.stop <= end && size.width > 1.dp.toPx()) drawRect(SEPARATOR, Offset(size.width - 1.dp.toPx(), 0f), Size(1.dp.toPx(), size.height))
                            val a = 4.dp.toPx(); val mid = size.height / 2
                            val mark = if (focusedNow) style.focusedProgrammeText else Color.White
                            if (runsBefore) drawPath(Path().apply { moveTo(1f, mid); lineTo(1f + a, mid - a); lineTo(1f + a, mid + a); close() }, mark)
                            if (runsAfter) drawPath(Path().apply { val x = size.width - 1f; moveTo(x, mid); lineTo(x - a, mid - a); lineTo(x - a, mid + a); close() }, mark)
                            // Scheduled or recording: a small red dot top-right.
                            if (rec) drawCircle(REC_DOT, 3.dp.toPx(), Offset(size.width - 7.dp.toPx(), 7.dp.toPx()))
                        },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(p.title, fontSize = 12.5.sp, fontWeight = if (focusedNow) FontWeight.SemiBold else FontWeight.Normal,
                        color = (if (focusedNow) style.focusedProgrammeText else Color.White).copy(alpha = if (past && !focusedNow) .55f else 1f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 10.dp))
                }
            }
        }
    }
}

private val REC_DOT = Color(0xFFE53935)
