package org.imdtv.iptvtv.data

import org.imdtv.iptvtv.data.update.isNewer
import org.imdtv.iptvtv.data.update.plainNotes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterTest {
    @Test fun versions() {
        assertTrue(isNewer("v0.2.0", "0.1.0"))
        assertTrue(isNewer("v0.10.0", "0.9.5"))
        assertTrue(isNewer("1.1", "1.0.9"))
        assertFalse(isNewer("v0.1.0", "0.1.0"))
        assertFalse(isNewer("v0.1", "0.1.0"))
        assertFalse(isNewer("v0.0.9", "0.1.0"))
        assertFalse(isNewer("v0.2.0-beta", "0.2.0"))
    }

    @Test fun notes() {
        assertEquals("What's new\nBulk hide channels\nSee the site", plainNotes("## What's new\n**Bulk hide** channels\nSee [the site](https://x)"))
    }
}
