package org.imdtv.iptvtv.data.support

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

/**
 * A crash in the first 30 s after the app starts (#69: "crashes as soon as I press the icon") means the user can't reach
 * Send logs. The handler saves the error, and the next start shows it on a plain-View screen (no Compose, no database)
 * with Copy and Open anyway, so it can be pasted into an issue.
 */
object CrashReport {
    private const val STARTUP_MS = 30_000L
    private fun file(c: Context) = File(c.filesDir, "startup-crash.txt")

    fun install(c: Context) {
        val f = file(c)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            if (SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime() < STARTUP_MS) runCatching {
                f.writeText("IMD TV ${org.imdtv.iptvtv.BuildConfig.VERSION_NAME} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · " +
                    "${Build.MANUFACTURER} ${Build.MODEL}\nThread: ${t.name}\n\n${e.stackTraceToString()}")
            }
            previous?.uncaughtException(t, e)
        }
    }

    /** Shows the saved startup crash instead of the app and returns true; [onContinue] runs after Open anyway. */
    fun showIfPending(a: Activity, onContinue: () -> Unit): Boolean {
        val f = file(a)
        val text = runCatching { f.takeIf { it.isFile }?.readText() }.getOrNull() ?: return false
        val pad = (16 * a.resources.displayMetrics.density).toInt()
        val copy = Button(a).apply {
            setText(org.imdtv.iptvtv.R.string.crash_copy)
            setOnClickListener {
                (a.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("IMD TV crash", text))
            }
        }
        val open = Button(a).apply {
            setText(org.imdtv.iptvtv.R.string.crash_open_anyway)
            setOnClickListener { f.delete(); onContinue() }
        }
        a.setContentView(LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(TextView(a).apply { setText(org.imdtv.iptvtv.R.string.crash_title); textSize = 20f })
            addView(LinearLayout(a).apply { gravity = Gravity.START; addView(copy); addView(open) })
            addView(ScrollView(a).apply {
                addView(TextView(a).apply { this.text = text; textSize = 12f; setTextIsSelectable(true) })
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        })
        copy.requestFocus() // remote: OK copies at once
        return true
    }
}
