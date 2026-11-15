package org.imdtv.iptvtv.ui.xmb

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.random.Random

/** Gamestation backdrop (spec §6A.8): deep-blue gradient, three translucent wave ribbons, sparkle dots. Drawn once per size. */
fun waveBitmap(density: Density, size: Size): ImageBitmap {
    val w = size.width; val h = size.height
    val img = ImageBitmap(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1))
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(img), size) {
        drawRect(Brush.linearGradient(listOf(Color(0xFF0E5C8C), Color(0xFF0B2A5A)), start = Offset(w, 0f), end = Offset(0f, h)))
        // Each ribbon: a band between two cubic curves across the screen.
        listOf(Triple(.42f, .10f, 0x5599D9FF), Triple(.48f, .14f, 0x3399D9FF), Triple(.55f, .07f, 0x22FFFFFF)).forEachIndexed { i, (y, t, c) ->
            val path = Path().apply {
                moveTo(0f, h * (y - .12f + i * .03f))
                cubicTo(w * .3f, h * (y - .25f), w * .6f, h * (y + .15f), w, h * (y - .05f))
                lineTo(w, h * (y - .05f + t))
                cubicTo(w * .6f, h * (y + .15f + t), w * .3f, h * (y - .25f + t * 1.6f), 0f, h * (y - .12f + i * .03f + t * 1.4f))
                close()
            }
            drawPath(path, Color(c.toLong() and 0xFFFFFFFFL))
        }
        val r = Random(7) // fixed seed: the same sparkles every time
        repeat(40) {
            drawCircle(Color.White.copy(alpha = .25f + r.nextFloat() * .5f), radius = 1f + r.nextFloat() * 2f, center = Offset(r.nextFloat() * w, h * (.2f + r.nextFloat() * .6f)))
        }
    }
    return img
}
