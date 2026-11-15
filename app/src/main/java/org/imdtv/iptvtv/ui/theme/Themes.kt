package org.imdtv.iptvtv.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import org.imdtv.iptvtv.data.settings.ThemeId
import org.imdtv.iptvtv.data.settings.ThemeColour
import androidx.compose.ui.graphics.lerp

/** One theme's colours (spec §6A.2). bg = background gradient stops; vod* = Movies/Series screens. */
data class ThemeColors(
    val bg: List<Color>, val panel: Color, val cell: Color, val cellDim: Color, val focus: Color, val onFocus: Color,
    val accent: Color, val text: Color, val muted: Color, val vodBg: Color, val vodCard: Color,
)

fun colorsOf(id: ThemeId, colour: ThemeColour = ThemeColour.DEFAULT): ThemeColors {
    val base = baseColors(id)
    if (colour == ThemeColour.DEFAULT) return base
    return when (id) {
        // Celestial Glass: cells, panel and VOD colours follow the chosen gradient the way the default blue's do.
        ThemeId.CELESTIAL_GLASS -> {
            val (d, m, l) = colour.glass.map { Color(it) }
            base.copy(bg = listOf(d, m, l), cell = lerp(l, Color.White, .08f).copy(alpha = .55f), cellDim = l.copy(alpha = .35f),
                panel = lerp(d, Color.Black, .25f).copy(alpha = .94f), onFocus = d, vodBg = lerp(d, Color.Black, .1f), vodCard = lerp(d, m, .35f))
        }
        ThemeId.LIQUID_GLASS -> base.copy(bg = colour.liquid.map { Color(it) })
        else -> base
    }
}

private fun baseColors(id: ThemeId): ThemeColors = when (id) {
    // Spec §6A.3: the old blue ~10% darker.
    ThemeId.CELESTIAL_GLASS -> ThemeColors(
        bg = listOf(Color(0xFF0A1753), Color(0xFF1A39A6), Color(0xFF3459CA)),
        panel = Color(0xF0071040), cell = Color(0x8C3F63CF), cellDim = Color(0x59365ABD),
        focus = Color(0xFFEEF3FF), onFocus = Color(0xFF0A1753), accent = Color(0xFFE5A00D),
        text = Color.White, muted = Color(0x9EFFFFFF), vodBg = Color(0xFF0A1440), vodCard = Color(0xFF16245E),
    )
    // Spec §6A.4: light top band, dark grid panel (also the selected tab), yellow focus.
    ThemeId.CELESTIAL_PLUS -> ThemeColors(
        bg = listOf(Color(0xFF2C64D8), Color(0xFF3F7BEA)),
        panel = Color(0xFF1A3696), cell = Color(0xFF2550C8), cellDim = Color(0xFF2F5BD4),
        focus = Color(0xFFF2C94C), onFocus = Color(0xFF0A1A5C), accent = Color(0xFFE5A00D),
        text = Color.White, muted = Color(0xB3FFFFFF), vodBg = Color(0xFF173A9E), vodCard = Color(0xFF2550C8),
    )
    // Spec §6A.5: pale sky, navy number cells, royal-blue name cells, yellow focus.
    ThemeId.CELESTIAL_RETRO -> ThemeColors(
        bg = listOf(Color(0xFFB9D3F2), Color(0xFFE6F0FB)),
        panel = Color(0xFF15257A), cell = Color(0xFF1F3FBF), cellDim = Color(0xFF2546C8),
        focus = Color(0xFFFFCC33), onFocus = Color(0xFF15257A), accent = Color(0xFFE5A00D),
        text = Color.White, muted = Color(0xFF8FA6D9), vodBg = Color(0xFF15257A), vodCard = Color(0xFF1F3FBF),
    )
    // Spec §6A.6: tints over a sharp teal-to-violet backdrop; panels are glass (see Glass.kt).
    ThemeId.LIQUID_GLASS -> ThemeColors(
        bg = listOf(Color(0xFF0FB5BA), Color(0xFF7A3CF0)),
        panel = Color(0x2EFFFFFF), cell = Color(0x26FFFFFF), cellDim = Color(0x1AFFFFFF),
        focus = Color(0xE6FFFFFF), onFocus = Color(0xFF1A1440), accent = Color(0xFFFFB347),
        text = Color.White, muted = Color(0xB3FFFFFF), vodBg = Color(0xFF2A2A6A), vodCard = Color(0x26FFFFFF),
    )
    // Spec §6A.7: green blade, silver side blades (drawn in XcubeFrame), pale-green glossy focus.
    ThemeId.XCUBE_180 -> ThemeColors(
        bg = listOf(Color(0xFF2E8B1E), Color(0xFF5DBB3A), Color(0xFF7BD34A)),
        panel = Color(0xCC1F6A14), cell = Color(0x00000000), cellDim = Color(0x26FFFFFF),
        focus = Color(0xFFE8F5D8), onFocus = Color(0xFF1A3A0E), accent = Color(0xFFE9D58A),
        text = Color.White, muted = Color(0xCCFFFFFF), vodBg = Color(0xFF2E8B1E), vodCard = Color(0x33FFFFFF),
    )
    // Spec §6A.8: deep blue, white text that glows when focused; no cell boxes.
    ThemeId.GAMESTATION -> ThemeColors(
        bg = listOf(Color(0xFF0B2A5A), Color(0xFF0E5C8C)),
        panel = Color(0xF00B2A5A), cell = Color(0x26FFFFFF), cellDim = Color(0x14FFFFFF),
        focus = Color.White, onFocus = Color(0xFF0B2A5A), accent = Color(0xFF99D9FF),
        text = Color.White, muted = Color(0x8CFFFFFF), vodBg = Color(0xFF0B2A5A), vodCard = Color(0x26FFFFFF),
    )
}

data class Theme(val id: ThemeId, val colors: ThemeColors)

fun themeOf(id: ThemeId, colour: ThemeColour = ThemeColour.DEFAULT) = Theme(id, colorsOf(id, colour))

val LocalTheme = staticCompositionLocalOf { themeOf(ThemeId.CELESTIAL_GLASS) }

/** Celestial Glass and Liquid Glass use Inter (the website's font); the other themes keep the TV's default font. */
fun usesInter(id: ThemeId) = id == ThemeId.CELESTIAL_GLASS || id == ThemeId.LIQUID_GLASS

/** Inter, one variable font file at the weights the app uses. */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val Inter = androidx.compose.ui.text.font.FontFamily(listOf(400, 500, 600, 700, 800).map { w ->
    androidx.compose.ui.text.font.Font(org.imdtv.iptvtv.R.font.inter, androidx.compose.ui.text.font.FontWeight(w),
        variationSettings = androidx.compose.ui.text.font.FontVariation.Settings(androidx.compose.ui.text.font.FontVariation.weight(w)))
})
