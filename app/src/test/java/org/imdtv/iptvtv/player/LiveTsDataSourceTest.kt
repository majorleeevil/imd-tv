package org.imdtv.iptvtv.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
class LiveTsDataSourceTest {
    /** Each open serves the next response; null = the open fails; a response ending in [STALL] throws mid-read. */
    private class Fake(private val responses: List<ByteArray?>) : DataSource {
        var opens = 0
        private var cur = ByteArray(0); private var pos = 0
        override fun addTransferListener(transferListener: TransferListener) {}
        override fun open(dataSpec: DataSpec): Long {
            val r = responses.getOrNull(opens++) ?: throw IOException("refused")
            cur = r; pos = 0
            return r.size.toLong()
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (pos >= cur.size) return C.RESULT_END_OF_INPUT
            val n = minOf(length, cur.size - pos, 7000) // odd chunks, so the join point falls inside a read
            System.arraycopy(cur, pos, buffer, offset, n); pos += n
            return n
        }
        override fun getUri(): Uri? = null
        override fun close() {}
    }

    private fun drain(src: DataSource, max: Int): ByteArray {
        src.open(DataSpec(Uri.parse("http://x/live/1.ts")))
        val out = ByteArrayOutputStream(); val buf = ByteArray(10_000)
        try {
            while (out.size() < max) { val n = src.read(buf, 0, buf.size); if (n == C.RESULT_END_OF_INPUT) break; out.write(buf, 0, n) }
        } catch (_: IOException) {}
        return out.toByteArray()
    }

    private val video = Random(1).nextBytes(400_000)
    private fun part(from: Int, to: Int) = video.copyOfRange(from, to)

    @Test
    fun aClosedConnectionContinuesWithoutRepeatingVideo() {
        // Second connection repeats 100 kB already passed on (the provider's catch-up burst), then carries on.
        val src = LiveTsDataSource(Fake(listOf(part(0, 200_000), part(100_000, 300_000), part(250_000, 400_000))), log = {})
        assertArrayEquals(video, drain(src, video.size))
    }

    @Test
    fun withoutAJoinPointTheNewDataIsPassedOnAsItCame() {
        val other = Random(2).nextBytes(50_000)
        val src = LiveTsDataSource(Fake(listOf(part(0, 100_000), other)), log = {})
        assertArrayEquals(part(0, 100_000) + other, drain(src, 150_000))
    }

    @Test
    fun givesUpWhenReconnectsKeepFailing() {
        val fake = Fake(listOf(part(0, 10_000), null, null, null, null, null))
        val got = drain(LiveTsDataSource(fake, log = {}), 1_000_000)
        assertEquals(10_000, got.size)
        assertTrue(fake.opens <= 5)
    }
}
