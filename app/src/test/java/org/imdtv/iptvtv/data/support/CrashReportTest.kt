package org.imdtv.iptvtv.data.support

import android.app.Activity
import android.widget.Button
import android.widget.LinearLayout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.io.File

/** #69: a crash while starting is caught, shown on the next start, and Open anyway clears it. */
@RunWith(RobolectricTestRunner::class)
class CrashReportTest {
    @Test fun aStartupCrashIsShownOnceThenTheAppOpens() {
        val a = Robolectric.buildActivity(Activity::class.java).setup().get()
        assertFalse(CrashReport.showIfPending(a) {})
        CrashReport.install(a)
        runCatching { Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(Thread.currentThread(), IllegalStateException("boom")) }
        val saved = File(a.filesDir, "startup-crash.txt")
        assertTrue(saved.readText().contains("boom"))
        var opened = false
        assertTrue(CrashReport.showIfPending(a) { opened = true })
        val buttons = (a.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0) as LinearLayout).getChildAt(1) as LinearLayout
        (buttons.getChildAt(1) as Button).performClick()
        assertTrue(opened)
        assertFalse(saved.exists())
    }
}
