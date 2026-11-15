package org.imdtv.iptvtv.ui.retro

/** Celestial Retro shows nine entries per page; bar 0 is MORE… (spec §6A.5). */
const val RETRO_PAGE = 9

data class RetroPage<T>(val items: List<T>, val page: Int, val hasMore: Boolean, val hasBack: Boolean)

/** Page [page] of [all], clamped to the last real page so a shrinking list never shows an empty page. */
fun <T> retroPage(all: List<T>, page: Int): RetroPage<T> {
    val last = maxOf(0, (all.size - 1) / RETRO_PAGE)
    val p = page.coerceIn(0, last)
    return RetroPage(all.drop(p * RETRO_PAGE).take(RETRO_PAGE), p, hasMore = p < last, hasBack = p > 0)
}
