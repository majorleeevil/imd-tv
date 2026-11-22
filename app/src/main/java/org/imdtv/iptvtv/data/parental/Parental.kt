package org.imdtv.iptvtv.data.parental

import androidx.annotation.StringRes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.imdtv.iptvtv.R
import org.imdtv.iptvtv.data.settings.AppSettings
import org.imdtv.iptvtv.data.settings.SettingsRepository
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** App behaviour › Parental controls › Lock content rated: this age and up needs the PIN. */
enum class AgeLimit(val age: Int, @StringRes val labelRes: Int) {
    PG(8, R.string.set_b_age_pg), A12(12, R.string.set_b_age_12a), A15(15, R.string.set_b_age_15), A18(18, R.string.set_b_age_18)
}

/** App behaviour › Parental controls › Ask for the PIN. */
enum class UnlockFor(@StringRes val labelRes: Int) {
    APP(R.string.set_b_unlock_app), EVERY_TIME(R.string.set_b_unlock_every_time), PROGRAMME(R.string.set_b_unlock_programme)
}

/**
 * Age of an XMLTV `<rating><value>` ("12", "12A", "PG", "TV-14", "FSK 16", "+18"); null = unrated or unknown.
 * Unrated content stays unlocked (user, 2026-11-22).
 */
fun ageOf(rating: String?): Int? {
    val r = rating?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: return null
    NAMED[r]?.let { return it }
    return Regex("\\d{1,2}").find(r)?.value?.toInt()?.takeIf { it <= 21 }
}

private val NAMED = mapOf(
    "U" to 0, "UC" to 0, "G" to 0, "TV-Y" to 0, "TV-G" to 0, "PG" to 8, "TV-PG" to 8, "PG-13" to 13, "TV-14" to 14,
    "R" to 17, "TV-MA" to 17, "NC-17" to 18, "R18" to 18, "X" to 18,
)

/** The PIN and backup word are stored only as salted, repeated SHA-256 hashes. */
object Pin {
    fun hash(secret: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return b64.encodeToString(salt) + ":" + b64.encodeToString(derive(norm(secret), salt))
    }
    fun matches(secret: String, stored: String?): Boolean {
        val parts = stored?.split(':')?.takeIf { it.size == 2 } ?: return false
        val salt = runCatching { unb64.decode(parts[0]) }.getOrNull() ?: return false
        return MessageDigest.isEqual(derive(norm(secret), salt), runCatching { unb64.decode(parts[1]) }.getOrNull() ?: return false)
    }
    /** The backup word is typed on a TV keyboard: case and surrounding spaces don't count. */
    private fun norm(s: String) = s.trim().lowercase()
    // Salted SHA-256 repeated, not PBKDF2: the Chromecast's provider threw "Could not generate secret key" (crash on saving the word).
    private fun derive(s: String, salt: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        var h = md.digest(salt + s.toByteArray())
        repeat(20_000) { h = md.digest(h + salt) }
        return h
    }
    private val b64 = Base64.getEncoder()
    private val unb64 = Base64.getDecoder()
}

/** Whether content needs the PIN: in a locked category, or rated at the limit or above. */
fun AppSettings.parentalLocked(categoryLocked: Boolean, age: Int?): Boolean =
    parentalOn && parentalPin != null && (categoryLocked || age != null && age >= ageLimit.age)

/** Asks for the PIN (a dialog at the app's root shows [request]) and remembers unlocks per Ask for the PIN. */
class ParentalGate(private val settings: SettingsRepository) {
    val request = MutableStateFlow<CompletableDeferred<Boolean>?>(null)
    private var appOpen = false
    private val openUntil = HashMap<String, Long>()

    /** The PIN (or backup word) once, unlocking nothing: Settings › Parental controls, turning a category lock off. */
    suspend fun askPin(): Boolean {
        val d = CompletableDeferred<Boolean>()
        request.value = d
        return try { d.await() } finally { request.compareAndSet(d, null) }
    }

    /** Whether locked content [key] may play now; [ask] false (autoplay while scrolling) never shows the dialog. [until]: a Per programme unlock's end. */
    suspend fun allow(locked: Boolean, key: String, until: Long, ask: Boolean): Boolean {
        if (!locked) return true
        val s = settings.settings.first()
        when (s.unlockFor) {
            UnlockFor.APP -> if (appOpen) return true
            UnlockFor.PROGRAMME -> if ((openUntil[key] ?: 0) > System.currentTimeMillis()) return true
            UnlockFor.EVERY_TIME -> Unit
        }
        if (!ask || !askPin()) return false
        when (s.unlockFor) { UnlockFor.APP -> appOpen = true; UnlockFor.PROGRAMME -> openUntil[key] = until; UnlockFor.EVERY_TIME -> Unit }
        return true
    }

    /** Leaving the app ends an Until the app closes unlock (TV boxes keep the process alive for days). */
    fun relock() { appOpen = false; openUntil.clear() }
}
