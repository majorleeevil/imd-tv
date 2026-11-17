package org.imdtv.iptvtv.data.support

import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom

private val USERINFO = Regex("(?i)(https?://)[^/\\s@:]+:[^/\\s@]*@")
private val XTREAM_PATH = Regex("(?i)/(live|movie|series|timeshift)/[^/\\s]+/[^/\\s]+/")
private val QUERY = Regex("(?i)([?&][\\w.-]*(?:user|pass|token|key|auth|mac)[\\w.-]*)=[^&\\s\"'#]*")
private val MAC = Regex("(?i)\\b[0-9a-f]{2}([:-])(?:[0-9a-f]{2}\\1){4}[0-9a-f]{2}\\b")
private val HEADER = Regex("(?im)\\b((?:proxy-)?authorization|cookie|set-cookie)\\s*:.*$")

/** Removes secrets from log [text]: the stored logins in [literals] (and their URL-encoded form), then URL/header patterns. */
fun scrubLog(text: String, literals: Collection<String>): String {
    var s = text
    literals.filter { it.length >= 3 }.flatMap { listOf(it, java.net.URLEncoder.encode(it, "UTF-8")) }.distinct()
        .sortedByDescending { it.length }.forEach { s = s.replace(it, "***") }
    return s.replace(USERINFO, "$1***:***@").replace(XTREAM_PATH, "/$1/***/***/").replace(QUERY, "$1=***")
        .replace(MAC, "***").replace(HEADER, "$1: ***")
}

/** The request line is exactly `GET /<token>/imd-tv-log.txt HTTP/1.x` (no query string). */
fun logRequestMatches(requestLine: String, token: String): Boolean {
    val p = requestLine.trimEnd().split(' ')
    return p.size == 3 && p[0] == "GET" && p[2].startsWith("HTTP/1.") &&
        MessageDigest.isEqual(p[1].toByteArray(), "/$token/imd-tv-log.txt".toByteArray())
}

/** The TV's Wi-Fi or Ethernet IPv4 address on the local network, or null. */
fun lanAddress(): InetAddress? = runCatching {
    NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && (it.name.startsWith("wlan") || it.name.startsWith("eth")) }
        .flatMap { it.inetAddresses.toList() }.firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
}.getOrNull()

/** One-shot download server: serves [body] at /<random token>/imd-tv-log.txt on [addr], one request at a time. */
class LogServer(addr: InetAddress, private val body: ByteArray) {
    val token = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    private val socket = ServerSocket(0, 2, addr)
    val url = "http://${addr.hostAddress}:${socket.localPort}/$token/imd-tv-log.txt"

    init { Thread({ loop() }, "log-server").apply { isDaemon = true }.start() }

    fun stop() = runCatching { socket.close() }

    private fun loop() {
        while (!socket.isClosed) {
            try { socket.accept().use { handle(it) } } catch (e: IOException) { /* closed, or a client went away */ }
        }
    }

    private fun handle(c: Socket) {
        c.soTimeout = 5000
        val inp = c.getInputStream()
        // Headers are read and ignored; capped at 8 KB so a client can't make us buffer more.
        val head = StringBuilder()
        while (head.length < 8192 && !head.endsWith("\r\n\r\n")) { val b = inp.read(); if (b < 0) break; head.append(b.toChar()) }
        val ok = logRequestMatches(head.lineSequence().first(), token)
        val out = c.getOutputStream()
        if (ok) {
            out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Disposition: attachment; filename=\"imd-tv-log.txt\"\r\n" +
                "Content-Length: ${body.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n").toByteArray())
            out.write(body)
        } else out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
        out.flush()
    }
}
