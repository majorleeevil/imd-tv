package org.imdtv.iptvtv.ui.xmb

import org.imdtv.iptvtv.ui.Tab

/** The XMB's icon row, left to right (spec §6A.8). */
enum class XmbColumn(val tab: Tab) { SETTINGS(Tab.SETTINGS), SEARCH(Tab.SEARCH), LIVE(Tab.LIVE), MOVIES(Tab.MOVIES), SERIES(Tab.SERIES), RECORDINGS(Tab.RECORDINGS) }

/** The icon row: Recordings only while recording is switched on. */
fun xmbColumns(recordingOn: Boolean): List<XmbColumn> = XmbColumn.entries.filter { it != XmbColumn.RECORDINGS || recordingOn }

/** Focus in the XMB: a column, an item in it, and optionally an open folder with its own item. */
data class XmbState(val column: XmbColumn = XmbColumn.LIVE, val item: Int = 0, val folder: Int? = null, val folderItem: Int = 0) {
    fun left(columns: List<XmbColumn> = xmbColumns(false)): XmbState = step(-1, columns)
    fun right(columns: List<XmbColumn> = xmbColumns(false)): XmbState = step(1, columns)
    private fun step(d: Int, columns: List<XmbColumn>): XmbState {
        if (folder != null) return this
        val c = columns[(columns.indexOf(column) + d).coerceIn(0, columns.size - 1)]
        return if (c == column) this else copy(column = c, item = 0)
    }

    fun up(count: Int): XmbState = move(-1, count)
    fun down(count: Int): XmbState = move(1, count)
    fun move(d: Int, count: Int): XmbState = when {
        count == 0 -> this
        folder != null -> copy(folderItem = (folderItem + d).coerceIn(0, count - 1))
        else -> copy(item = (item + d).coerceIn(0, count - 1))
    }

    fun open(): XmbState = copy(folder = item, folderItem = 0)

    /** Closes the folder; null when there is nothing to close (the app's root Back handles it). */
    fun back(): XmbState? = if (folder != null) copy(folder = null) else null

    /** Keeps focus on real entries after a list shrinks; a folder whose category is gone closes. */
    fun clamp(itemCount: Int, folderCount: Int): XmbState {
        val i = item.coerceIn(0, (itemCount - 1).coerceAtLeast(0))
        val f = folder?.takeIf { it < itemCount }
        return copy(item = i, folder = f, folderItem = if (f == null) 0 else folderItem.coerceIn(0, (folderCount - 1).coerceAtLeast(0)))
    }
}
