package org.imdtv.iptvtv.player

import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.IOException

/**
 * A live MPEG-TS stream that keeps going when the provider ends or stalls the connection. Some providers serve live TV
 * as HLS segments over one connection: about 30 s at once, a segment every ~10 s, then they close it after ~45 s. The
 * player saw that as the end of the stream (frozen until the watchdog reloaded it), and its own retries joined
 * overlapping video (a stutter or a jump back every ~40 s).
 *
 * Here a closed or stalled connection is reopened at once, and the new one's start, which repeats video already passed
 * on, is skipped by finding the last bytes passed on in it. If they aren't found, the new data is passed on as it comes.
 */
@OptIn(UnstableApi::class)
class LiveTsDataSource(private val upstream: DataSource, private val log: (String) -> Unit = { Log.i(TAG, it) }) : DataSource {
    private var spec: DataSpec? = null
    private val tail = ByteArray(TAIL)
    private var tailLen = 0
    private var pending = ByteArray(0)
    private var pendingPos = 0
    private var failures = 0
    @Volatile private var closed = false

    override fun addTransferListener(transferListener: TransferListener) = upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        closed = false; tailLen = 0; pending = ByteArray(0); pendingPos = 0; failures = 0
        spec = dataSpec
        upstream.open(dataSpec)
        return C.LENGTH_UNSET.toLong() // live: a Content-Length the server sends is not the end of the stream
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        while (true) {
            if (pendingPos < pending.size) {
                val n = minOf(length, pending.size - pendingPos)
                System.arraycopy(pending, pendingPos, buffer, offset, n)
                pendingPos += n
                remember(buffer, offset, n)
                return n
            }
            val error = try {
                val n = upstream.read(buffer, offset, length)
                if (n != C.RESULT_END_OF_INPUT) {
                    if (n > 0) { remember(buffer, offset, n); failures = 0 }
                    return n
                }
                null
            } catch (e: IOException) {
                if (closed) throw e
                e
            }
            reconnect(error)
        }
    }

    /** Reopens the stream and queues what follows the bytes already passed on; throws once reconnects keep failing. */
    private fun reconnect(cause: IOException?) {
        val spec = spec ?: throw IOException("not open")
        val needle = tail.copyOf(tailLen)
        while (true) {
            if (closed) throw IOException("closed")
            if (++failures > MAX_FAILURES) throw cause ?: IOException("live stream ended")
            runCatching { upstream.close() }
            if (failures > 1) Thread.sleep(500L * (failures - 1))
            try {
                upstream.open(spec.buildUpon().setPosition(0).setLength(C.LENGTH_UNSET.toLong()).build())
                pending = align(needle); pendingPos = 0
                log("reconnected after ${cause?.javaClass?.simpleName ?: "end of stream"} (attempt $failures)")
                return
            } catch (e: IOException) {
                if (closed) throw e
                log("reconnect failed: $e")
            }
        }
    }

    /**
     * Reads the new connection until [needle] (the last bytes passed on) is found and returns what follows it. Gives up
     * after [WINDOW] bytes or [WINDOW_MS] and returns everything read: better a repeat than a gap.
     */
    private fun align(needle: ByteArray): ByteArray {
        if (needle.size < MIN_NEEDLE) return ByteArray(0)
        val fail = IntArray(needle.size) // KMP failure table
        var k = 0
        for (i in 1 until needle.size) {
            while (k > 0 && needle[i] != needle[k]) k = fail[k - 1]
            if (needle[i] == needle[k]) k++
            fail[i] = k
        }
        var got = ByteArray(1 shl 20)
        var size = 0
        var matched = 0
        val until = System.currentTimeMillis() + WINDOW_MS
        while (size < WINDOW && System.currentTimeMillis() < until) {
            if (got.size - size < CHUNK) got = got.copyOf(got.size * 2)
            val n = upstream.read(got, size, CHUNK)
            if (n == C.RESULT_END_OF_INPUT) break
            for (i in size until size + n) {
                while (matched > 0 && got[i] != needle[matched]) matched = fail[matched - 1]
                if (got[i] == needle[matched]) matched++
                if (matched == needle.size) {
                    log("joined: skipped ${i + 1} bytes already played")
                    return got.copyOfRange(i + 1, size + n)
                }
            }
            size += n
        }
        log("join point not found in $size bytes: passing them on")
        return got.copyOf(size)
    }

    private fun remember(b: ByteArray, off: Int, n: Int) {
        if (n >= TAIL) { System.arraycopy(b, off + n - TAIL, tail, 0, TAIL); tailLen = TAIL; return }
        val keep = minOf(tailLen, TAIL - n)
        System.arraycopy(tail, tailLen - keep, tail, 0, keep)
        System.arraycopy(b, off, tail, keep, n)
        tailLen = keep + n
    }

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        closed = true
        upstream.close()
    }

    companion object {
        const val TAG = "IptvPlayer"
        private const val TAIL = 188 * 16 // 16 TS packets: unique enough to find in the repeated video
        private const val MIN_NEEDLE = 188 * 4
        private const val MAX_FAILURES = 4
        private const val CHUNK = 64 * 1024
        private const val WINDOW = 48 shl 20
        private const val WINDOW_MS = 6_000L
    }
}
