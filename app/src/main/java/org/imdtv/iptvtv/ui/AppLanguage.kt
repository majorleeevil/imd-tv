package org.imdtv.iptvtv.ui

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * The app's language: the TV's by default, or one picked in Settings. Android 13+ keeps it as the system's per-app
 * language (also settable in the TV's own app settings); older Android keeps it here and applies it to the app's and
 * the activity's contexts.
 */
object AppLanguage {
    /** Offered in Settings, after "System default". Each is shown in its own language. */
    val TAGS = listOf("en", "fr", "es", "pl", "nl", "de", "it", "pt", "tr", "ro", "el", "sv")

    private const val PREFS = "app_language"
    private const val KEY = "tag"

    /** "" = the TV's language. */
    fun get(ctx: Context): String =
        if (Build.VERSION.SDK_INT >= 33) ctx.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
        else ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()

    fun set(activity: Activity, tag: String) {
        if (Build.VERSION.SDK_INT >= 33) {
            // The system stores it and recreates the activity.
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
            return
        }
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tag).commit()
        // Strings made outside the screen (notices, errors) come from the app's context: switch it too.
        val app = activity.applicationContext
        @Suppress("DEPRECATION") app.resources.updateConfiguration(localized(app, tag).resources.configuration, app.resources.displayMetrics)
        activity.recreate()
    }

    /** For attachBaseContext (Application and Activity) before Android 13. */
    fun wrap(base: Context): Context = if (Build.VERSION.SDK_INT >= 33) base else localized(base, get(base))

    private fun localized(base: Context, tag: String): Context {
        val locale = if (tag.isEmpty()) LocaleList.getDefault()[0] ?: Locale.getDefault() else Locale.forLanguageTag(tag)
        return base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocale(locale) })
    }

    /** "Deutsch", "Ελληνικά", … */
    fun nativeName(tag: String): String = Locale.forLanguageTag(tag).let { it.getDisplayLanguage(it) }.replaceFirstChar { it.titlecase(Locale.forLanguageTag(tag)) }
}
