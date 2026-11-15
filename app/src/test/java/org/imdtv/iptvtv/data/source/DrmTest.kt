package org.imdtv.iptvtv.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class DrmTest {
    private fun parse(text: String) = M3uParser.parse(text.byteInputStream()).entries

    @Test fun kodipropBeforeAndAfterExtinf() {
        val e = parse("""
            #EXTM3U
            #KODIPROP:inputstream.adaptive.license_type=clearkey
            #KODIPROP:inputstream.adaptive.license_key=0123456789abcdef0123456789abcdef:fedcba9876543210fedcba9876543210
            #EXTINF:-1,One
            https://a/one.mpd
            #EXTINF:-1,Two
            #KODIPROP:inputstream.adaptive.license_type=com.widevine.alpha
            #KODIPROP:inputstream.adaptive.license_key=https://lic/wv?x=1|X-Token=abc&User-Agent=Foo
            https://a/two.mpd
            #EXTINF:-1,Plain
            https://a/three.m3u8
        """.trimIndent())
        assertEquals(Drm("clearkey", "0123456789abcdef0123456789abcdef:fedcba9876543210fedcba9876543210"), e[0].drm)
        assertEquals("https://a/one.mpd", e[0].url)
        assertEquals(Drm("com.widevine.alpha", "https://lic/wv?x=1|X-Token=abc&User-Agent=Foo"), e[1].drm)
        assertNull(e[2].drm)
    }

    @Test fun drmLegacyAndStreamHeaders() {
        val e = parse("""
            #EXTINF:-1,One
            #KODIPROP:inputstream.adaptive.drm_legacy=org.w3.clearkey|0123456789abcdef0123456789abcdef:fedcba9876543210fedcba9876543210
            #KODIPROP:inputstream.adaptive.stream_headers=User-Agent=Box%201&Referer=https://r/
            https://a/one.mpd
        """.trimIndent()).single()
        assertEquals("org.w3.clearkey", e.drm!!.type)
        assertEquals("https://a/one.mpd" to mapOf("User-Agent" to "Box 1", "Referer" to "https://r/"), splitStreamHeaders(e.url))
    }

    @Test fun hexPairsBecomeJwk() {
        val s = drmSpec(Drm("clearkey", "0123456789abcdef0123456789abcdef:fedcba9876543210fedcba9876543210"))
        assertEquals(DrmScheme.CLEARKEY, s.scheme)
        assertNull(s.licenseUrl)
        assertEquals("""{"keys":[{"kty":"oct","kid":"ASNFZ4mrze8BI0VniavN7w","k":"_ty6mHZUMhD-3LqYdlQyEA"}],"type":"temporary"}""", s.keysJson)
    }

    @Test fun base64AndJsonKeysGiveSameJwk() {
        val hex = drmSpec(Drm("clearkey", "0123456789abcdef0123456789abcdef:fedcba9876543210fedcba9876543210")).keysJson
        assertEquals(hex, drmSpec(Drm("clearkey", "ASNFZ4mrze8BI0VniavN7w==:/ty6mHZUMhD+3LqYdlQyEA==")).keysJson)
        assertEquals(hex, drmSpec(Drm("clearkey", """{"0123456789abcdef0123456789abcdef":"fedcba9876543210fedcba9876543210"}""")).keysJson)
        assertEquals(hex, drmSpec(Drm("clearkey", """{"keys":[{"kty":"oct","kid":"ASNFZ4mrze8BI0VniavN7w","k":"_ty6mHZUMhD-3LqYdlQyEA"}]}""")).keysJson)
        assertEquals(hex, drmSpec(Drm("clearkey", "01234567-89ab-cdef-0123-456789abcdef:fedcba9876543210fedcba9876543210")).keysJson)
    }

    @Test fun multipleKeys() {
        val s = drmSpec(Drm("clearkey", "0123456789abcdef0123456789abcdef:fedcba9876543210fedcba9876543210,fedcba9876543210fedcba9876543210:0123456789abcdef0123456789abcdef"))
        assertEquals(2, Regex("\"kty\"").findAll(s.keysJson!!).count())
    }

    @Test fun licenceUrlsWithHeaders() {
        val wv = drmSpec(Drm("com.widevine.alpha", "https://lic/wv?x=1|X-Token=a%20b&Origin=https://o|R{SSM}|"))
        assertEquals(DrmScheme.WIDEVINE, wv.scheme)
        assertEquals("https://lic/wv?x=1", wv.licenseUrl)
        assertEquals(mapOf("X-Token" to "a b", "Origin" to "https://o"), wv.headers)
        assertEquals("https://ck/lic", drmSpec(Drm("org.w3.clearkey", "https://ck/lic")).licenseUrl)
        assertNull(drmSpec(Drm("widevine", null)).licenseUrl) // the manifest's own licence URL
    }

    @Test fun badInputIsExplained() {
        assertEquals("Unsupported DRM type “com.microsoft.playready”", assertThrows(DrmException::class.java) { drmSpec(Drm("com.microsoft.playready", "https://x")) }.message)
        assertThrows(DrmException::class.java) { drmSpec(Drm("clearkey", null)) }
        assertThrows(DrmException::class.java) { drmSpec(Drm("clearkey", "nothex:alsonot")) }
        assertThrows(DrmException::class.java) { drmSpec(Drm("clearkey", "0123:4567")) }
        assertThrows(DrmException::class.java) { drmSpec(Drm("clearkey", "{broken")) }
        assertThrows(DrmException::class.java) { drmSpec(Drm("widevine", "0123456789abcdef0123456789abcdef:fedcba9876543210fedcba9876543210")) }
    }
}
