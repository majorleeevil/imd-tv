package org.imdtv.iptvtv

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Reddit/#69: the app crashed at start on phones and non-TV boxes. Robolectric's default UI mode is not TV. */
@RunWith(RobolectricTestRunner::class)
class StartPhoneTest {
    // Robolectric doesn't run App Startup's provider, which initialises WorkManager on a device.
    @org.junit.Before fun work() { runCatching { androidx.work.WorkManager.initialize(androidx.test.core.app.ApplicationProvider.getApplicationContext(), androidx.work.Configuration.Builder().build()) } }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h914dp-port-xxhdpi")
    fun startsOnAPhone() { Robolectric.buildActivity(MainActivity::class.java).setup() }

    @Test @Config(sdk = [34], qualifiers = "w960dp-h540dp-land-mdpi")
    fun startsOnANonTvBox() { Robolectric.buildActivity(MainActivity::class.java).setup() }

    @Test @Config(sdk = [25], qualifiers = "w960dp-h540dp-land-mdpi")
    fun startsOnAndroid7Box() { Robolectric.buildActivity(MainActivity::class.java).setup() }

    @Test @Config(sdk = [28], qualifiers = "w960dp-h540dp-land-xhdpi")
    fun startsOnAndroid9Box() { Robolectric.buildActivity(MainActivity::class.java).setup() }

    @Test @Config(sdk = [30], qualifiers = "w960dp-h540dp-land-television-xhdpi")
    fun startsOnATv() { Robolectric.buildActivity(MainActivity::class.java).setup() }
}
