package org.imdtv.iptvtv.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import org.imdtv.iptvtv.data.settings.AppSettings

class StartStackTest {
    @Test fun lastChannelOpensFullScreenOverGuide() =
        assertEquals(listOf(Screen.Live, Screen.Player("", live = true)), startStack(true, AppSettings(lastLiveChannelId = 5), true))

    @Test fun noLastChannelOrSettingOffShowsGuide() {
        assertEquals(listOf(Screen.Live), startStack(true, AppSettings(lastLiveChannelId = null), true))
        assertEquals(listOf(Screen.Live), startStack(true, AppSettings(lastChannelOnStart = false, lastLiveChannelId = 5), true))
        assertEquals(listOf(Screen.Live), startStack(true, AppSettings(lastLiveChannelId = 5), lastChannelExists = false))
    }

    @Test fun noProviderShowsSetup() = assertEquals(listOf(Screen.Setup()), startStack(false, AppSettings(lastLiveChannelId = 5), true))
}
