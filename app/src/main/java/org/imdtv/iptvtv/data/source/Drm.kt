package org.imdtv.iptvtv.data.source

import org.imdtv.iptvtv.R
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.Base64

/** A channel's DRM as the playlist gave it (Kodi `license_type` / `license_key`); read with [drmSpec] at play time. */
data class Drm(val type: String, val key: String?)

enum class DrmScheme { CLEARKEY, WIDEVINE }

/** What the player needs: a licence server, or ClearKey keys as a JWK set ([keysJson]). */
data class DrmSpec(val scheme: DrmScheme, val licenseUrl: String?, val headers: Map<String, String>, val keysJson: String?)

/** An error that reaches the screen: the English [message] stays for logs and tests, [resId] (+ [args]) is what the UI shows. */
interface UiMessage { val resId: Int?; val args: List<Any> }

/** The text to show for [this]: its string resource when it has one, else its message. */
fun Throwable.uiText(context: android.content.Context): String? {
    val m = this as? UiMessage
    val id = m?.resId ?: return message
    return context.getString(id, *m.args.toTypedArray())
}

/** An [java.io.IOException] with a string resource for the screen (see [UiMessage]). */
class UiIOException(message: String, @androidx.annotation.StringRes override val resId: Int? = null, override val args: List<Any> = emptyList()) : java.io.IOException(message), UiMessage

/** A playlist DRM entry the app can't use; [message] is shown to the user. */
class DrmException(message: String, @androidx.annotation.StringRes override val resId: Int? = null, override val args: List<Any> = emptyList()) : Exception(message), UiMessage

fun drmSpec(d: Drm): DrmSpec {
    val t = d.type.trim().lowercase()
    val scheme = when {
        "clearkey" in t -> DrmScheme.CLEARKEY
        "widevine" in t -> DrmScheme.WIDEVINE
        else -> throw DrmException("Unsupported DRM type “${d.type}”", R.string.set_b_drm_unsupported, listOf(d.type))
    }
    val key = d.key?.trim().orEmpty()
    if (key.startsWith("http://", true) || key.startsWith("https://", true)) {
        // Kodi: url|Header=v&Header2=v|postdata|response — only the URL and headers matter here.
        val parts = key.split('|')
        return DrmSpec(scheme, parts[0], parts.getOrNull(1)?.let(::parseHeaders).orEmpty(), null)
    }
    if (scheme == DrmScheme.WIDEVINE) {
        if (key.isEmpty()) return DrmSpec(scheme, null, emptyMap(), null) // the manifest's own licence URL
        throw DrmException("Widevine needs a licence URL, not keys", R.string.set_b_drm_widevine_url)
    }
    if (key.isEmpty()) throw DrmException("ClearKey channel without keys", R.string.set_b_drm_no_keys)
    return DrmSpec(scheme, null, emptyMap(), clearKeysJson(key))
}

/** `kid:key,kid:key` (hex or base64), a JWK set `{"keys":[…]}` or a `{"kid":"key"}` map → a JWK set for Media3. */
internal fun clearKeysJson(key: String): String {
    val pairs: List<Pair<String, String>> = if (key.startsWith("{")) {
        val o = runCatching { Json.parseToJsonElement(key).jsonObject }.getOrElse { throw DrmException("ClearKey keys aren't valid JSON", R.string.set_b_drm_bad_json) }
        o["keys"]?.let { keys ->
            keys.jsonArray.map { k -> k.jsonObject.let { it.need("kid") to it.need("k") } }
        } ?: o.map { (kid, k) -> kid to k.jsonPrimitive.content }
    } else {
        key.split(',', ' ', ';').filter { it.isNotBlank() }.map {
            val kv = it.split(':')
            if (kv.size != 2) throw DrmException("ClearKey keys must be KID:KEY pairs", R.string.set_b_drm_pairs)
            kv[0] to kv[1]
        }
    }
    if (pairs.isEmpty()) throw DrmException("ClearKey channel without keys", R.string.set_b_drm_no_keys)
    return buildJsonObject {
        put("keys", buildJsonArray {
            pairs.forEach { (kid, k) -> add(buildJsonObject { put("kty", "oct"); put("kid", b64url(kid)); put("k", b64url(k)) }) }
        })
        put("type", "temporary")
    }.toString()
}

private fun JsonObject.need(name: String) = (this[name] as? JsonPrimitive)?.content ?: throw DrmException("ClearKey key set without “$name”", R.string.set_b_drm_missing, listOf(name))

private val HEX = Regex("^[0-9a-fA-F]{32}$")

/** A 16-byte key or key id, hex (dashes allowed) or base64/base64url, as unpadded base64url. */
internal fun b64url(s: String): String {
    val t = s.trim().removeSurrounding("\"")
    val bytes = t.replace("-", "").takeIf { HEX.matches(it) }?.chunked(2)?.map { it.toInt(16).toByte() }?.toByteArray()
        ?: runCatching { Base64.getUrlDecoder().decode(t.replace('+', '-').replace('/', '_').trimEnd('=')) }.getOrNull()
        ?: throw DrmException("ClearKey key “$t” is neither hex nor base64", R.string.set_b_drm_neither, listOf(t))
    if (bytes.size != 16) throw DrmException("ClearKey keys and key ids must be 16 bytes", R.string.set_b_drm_16)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

/** `Header=v&Header2=v`, values percent-encoded or plain. */
internal fun parseHeaders(s: String): Map<String, String> = s.split('&').mapNotNull { kv ->
    val (k, v) = kv.split('=', limit = 2).takeIf { it.size == 2 && it[0].isNotBlank() } ?: return@mapNotNull null
    k.trim() to runCatching { java.net.URLDecoder.decode(v.replace("+", "%2B"), "UTF-8") }.getOrDefault(v)
}.toMap()
