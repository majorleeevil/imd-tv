package org.imdtv.iptvtv.ui.settings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChannelSaveMessageTest {
    private val res = ApplicationProvider.getApplicationContext<Application>().resources
    @Test fun hiddenPlural() = assertEquals("5 channels hidden", channelsSavedMessage(res, 5, 0))
    @Test fun hiddenSingular() = assertEquals("1 channel hidden", channelsSavedMessage(res, 1, 0))
    @Test fun shownPlural() = assertEquals("3 channels shown", channelsSavedMessage(res, 0, 3))
    @Test fun shownSingular() = assertEquals("1 channel shown", channelsSavedMessage(res, 0, 1))
    @Test fun bothCounts() = assertEquals("5 hidden, 3 shown", channelsSavedMessage(res, 5, 3))
}
