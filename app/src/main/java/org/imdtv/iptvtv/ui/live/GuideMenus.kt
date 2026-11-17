package org.imdtv.iptvtv.ui.live

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.imdtv.iptvtv.data.catalogue.displayName
import org.imdtv.iptvtv.data.db.CategoryEntity
import org.imdtv.iptvtv.data.db.ChannelEntity
import org.imdtv.iptvtv.data.repo.RailEntry
import org.imdtv.iptvtv.ui.ContextMenu
import org.imdtv.iptvtv.ui.TextPrompt

internal sealed interface GuideMenu {
    data class Channel(val ch: ChannelEntity) : GuideMenu
    data class AddTo(val ch: ChannelEntity) : GuideMenu
    data class NewGroup(val ch: ChannelEntity) : GuideMenu
    data class Rename(val ch: ChannelEntity) : GuideMenu
    data class ChangeLogo(val ch: ChannelEntity) : GuideMenu
    data class UseGuideFrom(val ch: ChannelEntity) : GuideMenu
    data class RecordLater(val ch: ChannelEntity) : GuideMenu
    data class CancelRecording(val ch: ChannelEntity, val p: org.imdtv.iptvtv.data.repo.GuideProgramme) : GuideMenu
    /** [horizontal]: Celestial Plus tabs, so the steps read left/right. */
    data class Category(val cat: CategoryEntity, val horizontal: Boolean = false) : GuideMenu
}

/** The long-press menus of a channel row and a rail category, shared by every theme. */
@Composable
fun GuideMenus(vm: LiveViewModel, state: GuideState) {
    val m = state.menu ?: return
    val strip by vm.strip.collectAsState()
    val entry by vm.entry.collectAsState()
    val favourites by vm.favouriteIds.collectAsState()
    val groups by vm.groups.collectAsState()
    val rail by vm.rail.collectAsState()
    val recordingOn by vm.recordingOn.collectAsState()
    val recordingOnChannel by vm.recordingOnChannel.collectAsState()
    val marks by vm.marks.collectAsState()
    when (m) {
        is GuideMenu.Channel -> ContextMenu(m.ch.displayName(strip), buildList {
            val fav = m.ch.id in favourites
            add((if (fav) "Remove from Favourites" else "Add to Favourites") to {
                if (fav && entry == RailEntry.Favourites) state.refocusOnChange = true
                vm.toggleFavourite(m.ch); state.menu = null
            })
            if (recordingOn) {
                val rec = recordingOnChannel[m.ch.id]
                if (rec != null) add("Stop recording" to { vm.stopRecording(rec); state.menu = null })
                else add("Record now" to { vm.recordNow(m.ch); state.menu = null })
                add("Record a later programme…" to { state.menu = GuideMenu.RecordLater(m.ch) })
            }
            add("Add to category…" to { state.menu = GuideMenu.AddTo(m.ch) })
            (entry as? RailEntry.Custom)?.let { e ->
                add("Remove from ${e.group.name}" to { state.refocusOnChange = true; vm.removeFromGroup(m.ch, e.group.id); state.menu = null })
            }
            add("Rename" to { state.menu = GuideMenu.Rename(m.ch) })
            add("Change logo…" to { state.menu = GuideMenu.ChangeLogo(m.ch) })
            add("Use guide from…" to { state.menu = GuideMenu.UseGuideFrom(m.ch) })
            add("Hide" to { state.refocusOnChange = true; vm.hide(m.ch); state.menu = null })
        }) { state.menu = null }
        is GuideMenu.AddTo -> ContextMenu("Add ${m.ch.displayName(strip)} to",
            groups.map { g -> g.name to { vm.addToGroup(m.ch, g.id); state.menu = null } } +
                ("New category…" to { state.menu = GuideMenu.NewGroup(m.ch) })) { state.menu = null }
        is GuideMenu.NewGroup -> TextPrompt("New category", "", "Category name",
            onSave = { if (it.isNotEmpty()) vm.addToNewGroup(m.ch, it); state.menu = null }) { state.menu = null }
        is GuideMenu.Rename -> TextPrompt("Rename channel", m.ch.customName ?: m.ch.displayName(strip), "Name (leave empty for the provider's name)",
            onSave = { vm.rename(m.ch, it); state.menu = null }) { state.menu = null }
        is GuideMenu.ChangeLogo -> LogoPicker(vm, m.ch, m.ch.displayName(strip)) { state.menu = null }
        is GuideMenu.UseGuideFrom -> GuidePicker(vm, m.ch, strip) { state.menu = null }
        is GuideMenu.RecordLater -> {
            val list by androidx.compose.runtime.produceState<List<org.imdtv.iptvtv.data.repo.GuideProgramme>?>(null, m.ch) { value = vm.upcoming(m.ch) }
            val progs = list ?: return
            val today = org.imdtv.iptvtv.ui.dayDate(System.currentTimeMillis())
            ContextMenu("Record on ${m.ch.displayName(strip)}", if (progs.isEmpty()) listOf("No guide for this channel" to { state.menu = null })
                else progs.map { p ->
                    val marked = (m.ch.id to p.start) in marks
                    val day = org.imdtv.iptvtv.ui.dayDate(p.start).takeIf { it != today }?.let { "$it " }.orEmpty()
                    "${if (marked) "● " else ""}$day${org.imdtv.iptvtv.ui.clockTime(p.start)}  ${p.title}" to {
                        if (marked) state.menu = GuideMenu.CancelRecording(m.ch, p) else { vm.schedule(m.ch, p); state.menu = null }
                    }
                }) { state.menu = null }
        }
        is GuideMenu.CancelRecording -> ContextMenu(m.p.title, listOf(
            (if (m.p.start <= System.currentTimeMillis()) "Stop recording" else "Cancel recording") to { vm.cancelRecording(m.ch, m.p); state.menu = null },
            "Keep it" to { state.menu = null },
        )) { state.menu = null }
        is GuideMenu.Category -> {
            // Move up/down are one step per press and keep the menu open; closing it puts focus on the moved row.
            val provider = rail.orEmpty().filter { it.entry is RailEntry.Provider }
            val key = "c${m.cat.id}"
            val at = provider.indexOfFirst { it.entry.key == key }
            ContextMenu("${m.cat.name.ifEmpty { "Uncategorised" }}  ·  ${at + 1} of ${provider.size}", listOf(
                (if (m.horizontal) "Move left" else "Move up") to { vm.moveCategory(m.cat, -1) },
                (if (m.horizontal) "Move right" else "Move down") to { vm.moveCategory(m.cat, 1) },
                "Hide" to { vm.hideCategory(m.cat); state.menu = null; state.railOpen = false; state.refocus() },
                "Move several places…" to { state.railMove = RailMove(m.cat, at); state.menu = null },
            )) { state.menu = null; state.railFocusKey = key }
        }
    }
}
