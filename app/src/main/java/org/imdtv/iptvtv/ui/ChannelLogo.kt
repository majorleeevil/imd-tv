package org.imdtv.iptvtv.ui

import androidx.compose.ui.res.stringResource
import org.imdtv.iptvtv.R

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.tv.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import org.imdtv.iptvtv.data.db.ChannelEntity
import org.imdtv.iptvtv.data.logos.shownLogos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.imdtv.iptvtv.data.settings.LogoKind

/** Appearance > Channel numbers, provided once at the root. */
val LocalShowNumbers = androidx.compose.runtime.staticCompositionLocalOf { true }

/** Appearance › Title size: multiply a title's sp by this (font scale itself stays pinned to 1.0). */
val LocalTitleScale = androidx.compose.runtime.staticCompositionLocalOf { 1f }
/** Appearance › Movies & Series › Title size: the same for movie and series titles. */
val LocalVodTitleScale = androidx.compose.runtime.staticCompositionLocalOf { 1f }

/** Playback › Catch-up; off hides every catch-up control (clock icons, past programmes, Restart, held Left). */
var catchUpOn by androidx.compose.runtime.mutableStateOf(true)
val org.imdtv.iptvtv.data.db.ChannelEntity.hasCatchUp get() = catchUpOn && archiveDays != null

/** The channel's logo per the Channel logos setting; the number tile while loading, on error and when there is none. */
@Composable
fun ChannelLogo(ch: ChannelEntity, source: List<LogoKind>, modifier: Modifier = Modifier, fontSize: TextUnit = 16.sp) {
    val tile = @Composable {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text((ch.customNumber ?: ch.number)?.toString()?.takeIf { LocalShowNumbers.current } ?: stringResource(R.string.live_logo_tv), fontSize = fontSize, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
    val urls = shownLogos(ch, source)
    var tried by remember(urls) { mutableIntStateOf(0) } // failed loads so far: e.g. tv-logos unreachable → playlist logo
    val url = urls.getOrNull(tried)
    if (url == null) Box(modifier) { tile() }
    else SubcomposeAsyncImage(url, null, modifier, contentScale = ContentScale.Fit, loading = { tile() }, error = { tile() },
        onError = { if (tried < urls.size) tried++ })
}
