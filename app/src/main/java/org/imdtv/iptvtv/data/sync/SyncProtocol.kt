package org.imdtv.iptvtv.data.sync

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** NSD service type the master advertises; the service name is the box's name. */
const val SYNC_SERVICE = "_imdtv-sync._tcp"
const val PAIR_TRIES = 5
const val PAIR_MS = 120_000L

private val random = SecureRandom()

fun newSyncKey(): ByteArray = ByteArray(32).also { random.nextBytes(it) }
fun newFollowerId(): String = ByteArray(12).also { random.nextBytes(it) }.joinToString("") { "%02x".format(it) }
fun newPairCode(): String = "%06d".format(random.nextInt(1_000_000))

/** AES-256-GCM: 12-byte IV then ciphertext+tag. */
fun seal(key: ByteArray, plain: ByteArray): ByteArray {
    val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES")) }
    return c.iv + c.doFinal(plain)
}

/** Throws (AEADBadTagException) for a wrong key or a changed message. */
fun open(key: ByteArray, sealed: ByteArray): ByteArray {
    val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, sealed, 0, 12)) }
    return c.doFinal(sealed, 12, sealed.size - 12)
}

fun sha256(b: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

enum class SyncPath(val method: String, val path: String) {
    /** Follower asks to pair; header X-Name. The master shows a code. */
    HELLO("POST", "/imd-tv/sync/v1/hello"),
    /** Follower sends X-Code (+ X-Name); the answer body is "<followerId> <key hex>". */
    PAIR("POST", "/imd-tv/sync/v1/pair"),
    /** Follower sends X-Follower and X-Hash (its last); 304 when unchanged, else the sealed snapshot. */
    SNAPSHOT("GET", "/imd-tv/sync/v1/snapshot"),
}

/** [headers] keys are lower case. */
data class SyncRequest(val path: SyncPath, val headers: Map<String, String>)

/** Exact request line match (no query string), like logRequestMatches; null for anything else. */
fun parseSyncRequest(head: String): SyncRequest? {
    val lines = head.split("\r\n")
    val p = lines.first().split(' ')
    if (p.size != 3 || !p[2].startsWith("HTTP/1.")) return null
    val path = SyncPath.entries.find { it.method == p[0] && it.path == p[1] } ?: return null
    val headers = lines.drop(1).mapNotNull { l -> l.indexOf(':').takeIf { it > 0 }?.let { l.substring(0, it).trim().lowercase() to l.substring(it + 1).trim() } }.toMap()
    return SyncRequest(path, headers)
}

/** One pairing on the master: the code shown on screen, at most [PAIR_TRIES] guesses, for [PAIR_MS]. */
class Pairing(val followerName: String, val code: String = newPairCode(), private val startedAt: Long = System.currentTimeMillis()) {
    private var tries = 0
    val open get() = tries < PAIR_TRIES && System.currentTimeMillis() - startedAt < PAIR_MS

    @Synchronized fun tryCode(guess: String): Boolean {
        if (!open) return false
        tries++
        return MessageDigest.isEqual(guess.toByteArray(), code.toByteArray()).also { if (it) tries = PAIR_TRIES }
    }
}

fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
fun String.unhex(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
