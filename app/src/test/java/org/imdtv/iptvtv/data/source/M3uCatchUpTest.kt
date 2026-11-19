package org.imdtv.iptvtv.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class M3uCatchUpTest {
    // 2024-03-05 06:07:08 UTC .. +1h; now 100 s after start
    private val start = 1709618828000L
    private val stop = start + 3_600_000
    private val now = start + 100_000
    private fun url(live: String, packed: String?) = m3uCatchUpUrl(live, packed, start, stop, now)

    @Test fun defaultIsTheFullTemplate() =
        assertEquals("http://c/x?s=1709618828&e=1709622428", url("http://h/1", "default|http://c/x?s={utc}&e={utcend}"))

    @Test fun appendUsesQuestionMarkOrAmpersand() {
        assertEquals("http://h/1?utc=1709618828", url("http://h/1", "append|?utc={start}"))
        assertEquals("http://h/1?t=x&utc=1709618828", url("http://h/1?t=x", "append|?utc={start}"))
        assertEquals("http://h/1/arch", url("http://h/1", "append|/arch"))
    }

    @Test fun shiftAndUnknownAndNullTypes() {
        assertEquals("http://h/1?utc=1709618828&lutc=1709618928", url("http://h/1", "shift|"))
        assertEquals("http://h/1?t=x&utc=1709618828&lutc=1709618928", url("http://h/1?t=x", null))
        assertEquals("http://h/1?utc=1709618828&lutc=1709618928", url("http://h/1", "weird|"))
    }

    @Test fun flussonicRewritesTheLastSegment() {
        assertEquals("http://h/ch/index-1709618828-3600.m3u8?token=a", url("http://h/ch/index.m3u8?token=a", "flussonic|"))
        assertEquals("http://h/ch/mono-1709618828-3600.m3u8", url("http://h/ch/mono.m3u8", "fs|"))
        assertEquals("http://h/ch/index-1709618828-3600.m3u8", url("http://h/ch/index.m3u8", "flussonic-hls|"))
        assertEquals("http://h/ch/timeshift_abs-1709618828.ts", url("http://h/ch/mpegts", "flussonic|"))
        assertEquals("http://h/ch/timeshift_abs-1709618828.ts?t=1", url("http://h/ch/live.ts?t=1", "flussonic|"))
        assertEquals("http://h/ch/timeshift_abs-1709618828.ts", url("http://h/ch/index.m3u8", "flussonic-ts|"))
    }

    @Test fun xcIsLeftToTheXtreamBuilder() = assertNull(url("http://h/1", "xc|"))

    @Test fun timePlaceholders() {
        val t = "{utc} {start} \${start} {utcend} {end} \${end} {lutc} {now} {timestamp} \${now} \${timestamp} {duration} \${duration} {offset}"
        assertEquals("1709618828 1709618828 1709618828 1709622428 1709622428 1709622428 1709618928 1709618928 1709618928 1709618928 1709618928 3600 3600 100",
            url("http://h/1", "default|$t"))
    }

    @Test fun dateParts() {
        assertEquals("20240305060708", url("http://h/1", "default|{Y}{m}{d}{H}{M}{S}"))
    }

    @Test fun formattedPlaceholders() {
        assertEquals("2024-03-05:06-07 2024-03-05:07-07 2024/03/05 07", url("http://h/1", "default|{utc:Y-m-d:H-M} {end:Y-m-d:H-M} {start:Y/m/d} {utcend:H}"))
    }
}
