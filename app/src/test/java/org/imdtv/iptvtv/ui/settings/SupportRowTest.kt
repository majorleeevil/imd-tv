package org.imdtv.iptvtv.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportRowTest {
    @Test fun supportLabelIsTheUrlWithoutScheme() {
        assertEquals(SUPPORT_URL.removePrefix("https://"), SUPPORT_LABEL)
        assertTrue(SUPPORT_URL.startsWith("https://buymeacoffee.com/"))
    }

    @Test fun siteLabelIsTheSiteUrlWithoutSchemeOrSlash() {
        assertEquals("majorleeevil.github.io/imd-tv", SITE_LABEL)
        assertEquals("https://$SITE_LABEL/", SITE_URL)
    }
}
