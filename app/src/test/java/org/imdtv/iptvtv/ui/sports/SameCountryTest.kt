package org.imdtv.iptvtv.ui.sports

import org.imdtv.iptvtv.data.catalogue.detectCountry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SameCountryTest {
    /** Where to watch lists every copy of a channel (26 Nov): TNT's VIP/UK copies yes, Germany's Sky Sport Mix for the UK one no. */
    @Test fun copiesOfAChannelStayInOneCountry() {
        fun same(a: String, b: String) = sameCountry(detectCountry(a), detectCountry(b))
        assertTrue(same("VIP: TNT SPORTS 2 ᴿᴬᵂ ⁵⁰ FPS", "UK: TNT SPORTS 2 ᴴᴰ ◉"))
        assertTrue(same("UK: TNT SPORTS 2 HEVC 4K", "UK: TNT SPORTS 2 ᴿᴬᵂ"))
        assertFalse(same("DE: SKY SPORT MIX ᴿᴬᵂ", "UK: SKY SPORT MIX ᴿᴬᵂ"))
    }
}
