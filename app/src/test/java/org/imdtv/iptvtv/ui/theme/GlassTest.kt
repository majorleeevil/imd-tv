package org.imdtv.iptvtv.ui.theme

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassTest {
    @Test fun boxBlurSpreadsAndKeepsEdges() {
        val img = ImageBitmap(9, 1)
        val bmp = img.asAndroidBitmap()
        for (x in 0 until 9) bmp.setPixel(x, 0, android.graphics.Color.BLACK)
        bmp.setPixel(4, 0, android.graphics.Color.WHITE)
        boxBlur(img, radius = 1, passes = 1)
        val mid = bmp.getPixel(4, 0) and 0xFF; val side = bmp.getPixel(3, 0) and 0xFF; val far = bmp.getPixel(0, 0) and 0xFF
        assertEquals(85, mid); assertEquals(85, side); assertEquals(0, far) // 255/3 spread over three pixels
        assertTrue(bmp.getPixel(4, 0) ushr 24 == 255) // opaque stays opaque
    }
}
