package org.imdtv.iptvtv.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelSaveMessageTest {
    @Test fun hiddenPlural() = assertEquals("5 channels hidden", channelsSavedMessage(5, 0))
    @Test fun hiddenSingular() = assertEquals("1 channel hidden", channelsSavedMessage(1, 0))
    @Test fun shownPlural() = assertEquals("3 channels shown", channelsSavedMessage(0, 3))
    @Test fun shownSingular() = assertEquals("1 channel shown", channelsSavedMessage(0, 1))
    @Test fun bothCounts() = assertEquals("5 hidden, 3 shown", channelsSavedMessage(5, 3))
}
