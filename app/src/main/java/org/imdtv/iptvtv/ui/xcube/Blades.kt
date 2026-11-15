package org.imdtv.iptvtv.ui.xcube

import org.imdtv.iptvtv.ui.Tab
import org.imdtv.iptvtv.ui.visibleTabs

/** Blades before [selected] (left stack, nearest last) and after it (right stack), spec §6A.7. */
fun bladeSides(selected: Tab, tabs: List<Tab> = visibleTabs(false)): Pair<List<Tab>, List<Tab>> =
    tabs.indexOf(selected).let { i -> tabs.take(i) to tabs.drop(i + 1) }

/** The blade [dir] steps from [selected]; stops at the ends (no wrap). */
fun nextBlade(selected: Tab, dir: Int, tabs: List<Tab> = visibleTabs(false)): Tab = tabs[(tabs.indexOf(selected) + dir).coerceIn(0, tabs.size - 1)]
