package org.imdtv.iptvtv.ui.theme

import androidx.compose.ui.graphics.ClipOp
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** Liquid Glass backdrop (spec §6A.6): the sharp picture and a pre-blurred copy, both rasterised once per size. */
class GlassBackdrop(val sharp: ImageBitmap, val blurred: ImageBitmap, val dark: Boolean = false) {
    /** White panel tint: a little stronger over the dark backdrop so the panels still read as glass. */
    val tint = if (dark) Color(0x3DFFFFFF) else Color(0x2EFFFFFF)
}

/** Provided only while Liquid Glass is the theme; null elsewhere, which turns [glass] panels off. */
val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

private fun bitmap(density: Density, size: Size, draw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit): ImageBitmap {
    val img = ImageBitmap(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1))
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(img), size, draw)
    return img
}

fun glassBackdrop(density: Density, size: Size, gradient: List<Color> = listOf(Color(0xFF0FB5BA), Color(0xFF7A3CF0)), dark: Boolean = false): GlassBackdrop {
    val w = size.width; val h = size.height
    // Dark mode: the blobs are darkened with the gradient (by [DARK_PULL]) so the backdrop is near-black throughout.
    fun blob(argb: Long) = themeStop(argb, dark)
    val sharp = bitmap(density, size) {
        drawRect(Brush.linearGradient(gradient, end = Offset(w, h)))
        drawCircle(blob(0xFFFF5FA2), radius = h * .34f, center = Offset(w * .82f, h * .2f))
        drawCircle(blob(0xFFFFB347), radius = h * .22f, center = Offset(w * .12f, h * .82f))
        drawCircle(blob(0xFF3D8BFF), radius = h * .28f, center = Offset(w * .55f, h * .95f))
        drawOval(blob(0xFF7FF0E0), topLeft = Offset(w * .3f, h * .05f), size = Size(w * .22f, h * .18f))
    }
    // Frosted blur, done once: shrink to 1/8, box-blur that small copy three times (≈ gaussian), scale back up.
    val small = bitmap(density, Size(w / 8, h / 8)) {
        drawImage(sharp, dstSize = IntSize((w / 8).toInt().coerceAtLeast(1), (h / 8).toInt().coerceAtLeast(1)), filterQuality = FilterQuality.High)
    }
    boxBlur(small, radius = 3, passes = 3)
    val blurred = bitmap(density, size) {
        drawImage(small, dstSize = IntSize(w.toInt(), h.toInt()), filterQuality = FilterQuality.High)
    }
    return GlassBackdrop(sharp, blurred, dark)
}

/** In-place box blur of [img] (horizontal then vertical, [passes] times). Small bitmaps only: it runs on the CPU. */
internal fun boxBlur(img: ImageBitmap, radius: Int, passes: Int) {
    val bmp = img.asAndroidBitmap()
    val w = bmp.width; val h = bmp.height
    val px = IntArray(w * h).also { bmp.getPixels(it, 0, w, 0, 0, w, h) }
    val tmp = IntArray(w * h)
    fun pass(src: IntArray, dst: IntArray, len: Int, lines: Int, step: Int, lineStep: Int) {
        for (l in 0 until lines) {
            val base = l * lineStep
            for (i in 0 until len) {
                var a = 0; var r = 0; var g = 0; var b = 0
                for (k in -radius..radius) {
                    val c = src[base + (i + k).coerceIn(0, len - 1) * step]
                    a += c ushr 24; r += (c shr 16) and 255; g += (c shr 8) and 255; b += c and 255
                }
                val n = 2 * radius + 1
                dst[base + i * step] = ((a / n) shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
            }
        }
    }
    repeat(passes) { pass(px, tmp, w, h, 1, w); pass(tmp, px, h, w, w, 1) }
    bmp.setPixels(px, 0, w, 0, 0, w, h)
}

/** The theme's page background: the Liquid Glass backdrop when there is one, else [fallback]. */
fun Modifier.themeBackground(fallback: Color): Modifier = composed {
    val backdrop = LocalGlassBackdrop.current
    if (backdrop == null) background(fallback) else drawBehind { drawImage(backdrop.sharp) }
}

private val EDGE = Brush.linearGradient(listOf(Color(0xB3FFFFFF), Color(0x26FFFFFF)))

/**
 * A frosted panel: soft shadow, the blurred backdrop under it, a white tint, a bright 1dp edge and an inner top
 * highlight, all in one cached draw. [smoked] (over video) uses a dark tint and no backdrop. Without a Liquid Glass
 * backdrop a non-smoked panel draws nothing, so shared screens can apply it in every theme.
 */
fun Modifier.glass(shape: Shape = RoundedCornerShape(16.dp), smoked: Boolean = false, blur: Boolean = true): Modifier = composed {
    val backdrop = LocalGlassBackdrop.current
    if (backdrop == null && !smoked) return@composed this
    if (!blur && !smoked) return@composed drawWithCache {
        // Tint + edge only, drawn directly (no backdrop patch): the cheap fallback for a large panel (spec §6A.9).
        val outline = shape.createOutline(size, layoutDirection, this)
        val px = 1.dp.toPx()
        onDrawBehind {
            drawOutline(outline, backdrop?.tint ?: Color(0x2EFFFFFF))
            drawOutline(outline, EDGE, style = Stroke(px))
        }
    }
    var origin by remember { mutableStateOf(Offset.Zero) }
    onGloballyPositioned { origin = it.positionInRoot() }.drawWithCache {
        // The whole panel (shadow, backdrop patch, tint, edge, highlight) is rasterised once per size and position,
        // so each frame is one bitmap blit — the guide's frame time is GPU-bound on the Chromecast.
        val m = 6.dp.toPx()
        val at = origin
        val panel = bitmap(this, Size(size.width + 2 * m, size.height + 2 * m)) {
            translate(m, m) {
                val outline = shape.createOutline(this@drawWithCache.size, layoutDirection, this@drawWithCache)
                val path = Path().apply {
                    when (outline) {
                        is Outline.Rectangle -> addRect(outline.rect)
                        is Outline.Rounded -> addRoundRect(outline.roundRect)
                        is Outline.Generic -> addPath(outline.path)
                    }
                }
                val px = 1.dp.toPx()
                // Soft shadow below the panel only (clipped out of the panel, so no lines show inside its top edge).
                clipPath(path, ClipOp.Difference) {
                    for (i in 1..3) translate(0f, i * 1.5f * px) { drawPath(path, Color.Black.copy(alpha = .06f)) }
                }
                if (backdrop != null && !smoked) clipPath(path) {
                    val b = backdrop.blurred
                    val w = this@drawWithCache.size.width.toInt().coerceIn(1, b.width)
                    val h = this@drawWithCache.size.height.toInt().coerceIn(1, b.height)
                    val src = IntOffset(at.x.toInt().coerceIn(0, b.width - w), at.y.toInt().coerceIn(0, b.height - h))
                    drawImage(b, srcOffset = src, srcSize = IntSize(w, h), dstSize = IntSize(w, h))
                }
                drawPath(path, if (smoked) Color(0x8C0A0F1E) else backdrop?.tint ?: Color(0x2EFFFFFF))
                drawPath(path, EDGE, style = Stroke(px))
            }
        }
        onDrawBehind { drawImage(panel, topLeft = Offset(-m, -m)) }
    }
}
