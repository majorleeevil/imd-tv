package org.imdtv.iptvtv.data.settings

import org.imdtv.iptvtv.data.settings.DpadAction.CHANNEL
import org.imdtv.iptvtv.data.settings.DpadAction.GUIDE
import org.imdtv.iptvtv.data.settings.DpadAction.PREVIOUS
import org.junit.Assert.assertEquals
import org.junit.Test

class DpadKeysTest {
    @Test fun nothingSetKeepsTheOldArrowsBitForBit() {
        for (lr in listOf(false, true)) for (k in Dpad.entries) {
            val sideways = k == Dpad.LEFT || k == Dpad.RIGHT
            assertEquals(if (sideways == lr) CHANNEL else PREVIOUS, dpadAction(k, lr, emptyMap()))
            assertEquals(dpadAction(k, lr, emptyMap()), dpadAction(k, lr, mapOf(k to DpadAction.DEFAULT)))
        }
        assertEquals(-1, Dpad.UP.delta); assertEquals(1, Dpad.DOWN.delta); assertEquals(-1, Dpad.LEFT.delta); assertEquals(1, Dpad.RIGHT.delta)
    }

    @Test fun aSetArrowOverridesOnlyItself() {
        val m = mapOf(Dpad.LEFT to GUIDE)
        assertEquals(GUIDE, dpadAction(Dpad.LEFT, false, m))
        assertEquals(PREVIOUS, dpadAction(Dpad.RIGHT, false, m))
        assertEquals(CHANNEL, dpadAction(Dpad.UP, false, m))
    }

    @Test fun encodesAndSkipsJunk() {
        val m = mapOf(Dpad.LEFT to GUIDE, Dpad.UP to DpadAction.DEFAULT, Dpad.DOWN to DpadAction.NOTHING)
        assertEquals("LEFT=GUIDE,DOWN=NOTHING", m.encodeDpadKeys())
        assertEquals(mapOf(Dpad.LEFT to GUIDE, Dpad.DOWN to DpadAction.NOTHING), parseDpadKeys(m.encodeDpadKeys()))
        assertEquals(emptyMap<Dpad, DpadAction>(), parseDpadKeys(""))
        assertEquals(mapOf(Dpad.UP to GUIDE), parseDpadKeys("UP=GUIDE,LEFT=WARP,SIDE=GUIDE,RIGHT"))
    }
}
