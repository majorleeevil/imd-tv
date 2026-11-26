package org.imdtv.iptvtv.data.settings

import org.imdtv.iptvtv.data.settings.DpadAction.GUIDE
import org.junit.Assert.assertEquals
import org.junit.Test

class DpadKeysTest {
    /** Each choice names its direction (user, 26 Nov); nothing set behaves exactly as before. */
    @Test fun nothingSetKeepsTheOldArrowsBitForBit() {
        val ud = mapOf(Dpad.UP to DpadAction.CHANNEL_PREV, Dpad.DOWN to DpadAction.CHANNEL_NEXT, Dpad.LEFT to DpadAction.WATCHED_FORWARD, Dpad.RIGHT to DpadAction.WATCHED_BACK)
        val lr = mapOf(Dpad.LEFT to DpadAction.CHANNEL_PREV, Dpad.RIGHT to DpadAction.CHANNEL_NEXT, Dpad.UP to DpadAction.WATCHED_FORWARD, Dpad.DOWN to DpadAction.WATCHED_BACK)
        for (k in Dpad.entries) {
            assertEquals(ud[k], dpadAction(k, false, emptyMap()))
            assertEquals(lr[k], dpadAction(k, true, emptyMap()))
            assertEquals(dpadAction(k, false, emptyMap()), dpadAction(k, false, mapOf(k to DpadAction.DEFAULT)))
        }
        assertEquals(-1, Dpad.UP.delta); assertEquals(1, Dpad.DOWN.delta); assertEquals(-1, Dpad.LEFT.delta); assertEquals(1, Dpad.RIGHT.delta)
    }

    /** A choice saved before 26 Nov ("Change channel" on Down) keeps doing what it did. */
    @Test fun oldSavedChoicesKeepTheirDirection() {
        assertEquals(DpadAction.CHANNEL_NEXT, dpadAction(Dpad.DOWN, false, parseDpadKeys("DOWN=CHANNEL")))
        assertEquals(DpadAction.CHANNEL_PREV, dpadAction(Dpad.LEFT, false, parseDpadKeys("LEFT=CHANNEL")))
        assertEquals(DpadAction.WATCHED_FORWARD, dpadAction(Dpad.UP, false, mapOf(Dpad.UP to DpadAction.PREVIOUS)))
        assertEquals(DpadAction.WATCHED_BACK, dpadAction(Dpad.RIGHT, false, mapOf(Dpad.RIGHT to DpadAction.PREVIOUS)))
    }

    @Test fun aSetArrowOverridesOnlyItself() {
        val m = mapOf(Dpad.LEFT to GUIDE)
        assertEquals(GUIDE, dpadAction(Dpad.LEFT, false, m))
        assertEquals(DpadAction.WATCHED_BACK, dpadAction(Dpad.RIGHT, false, m))
        assertEquals(DpadAction.CHANNEL_PREV, dpadAction(Dpad.UP, false, m))
    }

    @Test fun encodesAndSkipsJunk() {
        val m = mapOf(Dpad.LEFT to GUIDE, Dpad.UP to DpadAction.DEFAULT, Dpad.DOWN to DpadAction.NOTHING)
        assertEquals("LEFT=GUIDE,DOWN=NOTHING", m.encodeDpadKeys())
        assertEquals(mapOf(Dpad.LEFT to GUIDE, Dpad.DOWN to DpadAction.NOTHING), parseDpadKeys(m.encodeDpadKeys()))
        assertEquals(emptyMap<Dpad, DpadAction>(), parseDpadKeys(""))
        assertEquals(mapOf(Dpad.UP to GUIDE), parseDpadKeys("UP=GUIDE,LEFT=WARP,SIDE=GUIDE,RIGHT"))
    }
}
