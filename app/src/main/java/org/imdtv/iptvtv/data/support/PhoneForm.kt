package org.imdtv.iptvtv.data.support

import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom

/** One phone form field: [key] is sent back, [secret] fields are never filled in (blank = keep what the TV has). */
data class PhoneField(val key: String, val label: String, val value: String = "", val secret: Boolean = false, val types: Set<String> = emptySet())

/**
 * Add / edit playlist from a phone: serves a form at /<random token>/ on the local network and hands the first
 * submitted form to [onSubmit] (then stops). [types] non-empty = a type picker; each field shows only for its [PhoneField.types].
 */
class PhoneFormServer(
    addr: InetAddress, private val title: String, private val types: List<String>, private val type: String,
    private val fields: List<PhoneField>, private val sendLabel: String, private val doneText: String,
    private val onSubmit: (Map<String, String>) -> Unit,
) {
    val token = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    private val socket = ServerSocket(0, 4, addr)
    val url = "http://${addr.hostAddress}:${socket.localPort}/$token/"

    init { Thread({ loop() }, "phone-form").apply { isDaemon = true }.start() }

    fun stop() = runCatching { socket.close() }

    private fun loop() {
        while (!socket.isClosed) {
            try { socket.accept().use { handle(it) } } catch (e: IOException) { /* closed, or a client went away */ }
        }
    }

    private fun handle(c: Socket) {
        c.soTimeout = 10_000
        val inp = c.getInputStream()
        val head = StringBuilder()
        while (head.length < 8192 && !head.endsWith("\r\n\r\n")) { val b = inp.read(); if (b < 0) break; head.append(b.toChar()) }
        val out = c.getOutputStream()
        fun reply(status: String, html: String) {
            val body = html.toByteArray()
            out.write(("HTTP/1.1 $status\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${body.size}\r\n" +
                "Cache-Control: no-store\r\nConnection: close\r\n\r\n").toByteArray()); out.write(body); out.flush()
        }
        when (phoneRequest(head.lineSequence().first(), token)) {
            "GET" -> reply("200 OK", page())
            "POST" -> {
                val len = Regex("(?im)^content-length:\\s*(\\d+)").find(head)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 0..MAX_BODY }
                    ?: return reply("413 Payload Too Large", "")
                val body = ByteArray(len).also { var n = 0; while (n < len) { val r = inp.read(it, n, len - n); if (r < 0) break; n += r } }
                onSubmit(parseForm(String(body, Charsets.UTF_8)))
                reply("200 OK", shell("<p>${esc(doneText)}</p>"))
                stop() // one form per QR code
            }
            else -> reply("404 Not Found", "")
        }
    }

    private fun page(): String {
        val picker = if (types.isEmpty()) "<input type=hidden name=type value=\"${esc(type)}\">"
            else "<select name=type id=t>" + types.joinToString("") { "<option${if (it == type) " selected" else ""}>${esc(it)}</option>" } + "</select>"
        // Secrets are never filled in; passwords are masked, a (blank) address isn't: typed blind, a long URL goes wrong.
        val rows = fields.joinToString("") { f ->
            "<label data-t=\"${f.types.joinToString(" ")}\">${esc(f.label)}<input name=\"${esc(f.key)}\" value=\"${esc(if (f.secret) "" else f.value)}\"" +
                "${if (f.secret && !f.key.endsWith("url")) " type=password" else ""} autocapitalize=off autocorrect=off spellcheck=false></label>"
        }
        // Shows only the fields of the picked type.
        val js = "<script>var t=document.getElementById('t');function s(){var v=t?t.value:'${esc(type)}';" +
            "document.querySelectorAll('label').forEach(function(l){var d=l.dataset.t;l.style.display=!d||d.split(' ').indexOf(v)>=0?'':'none'})}" +
            "if(t)t.onchange=s;s()</script>"
        return shell("<form method=post>$picker$rows<button>${esc(sendLabel)}</button></form>$js")
    }

    private fun shell(content: String) = "<!doctype html><meta charset=utf-8><meta name=viewport content=\"width=device-width,initial-scale=1\">" +
        "<title>${esc(title)}</title><style>body{font:16px system-ui,sans-serif;margin:0;padding:16px;background:#0b1530;color:#fff}" +
        "h1{font-size:20px}label{display:block;margin:12px 0;font-size:14px;color:#c8d0e0}input,select{display:block;width:100%;box-sizing:border-box;" +
        "margin-top:4px;padding:12px;font-size:16px;border-radius:8px;border:1px solid #3a4a70;background:#14204a;color:#fff}" +
        "button{width:100%;margin-top:16px;padding:14px;font-size:17px;border:0;border-radius:8px;background:#e5a00d;color:#000;font-weight:600}</style>" +
        "<h1>${esc(title)}</h1>$content"

    private companion object { const val MAX_BODY = 16 * 1024 }
}

/** "GET" or "POST" for exactly `/<token>/` (no query string), else null. */
fun phoneRequest(requestLine: String, token: String): String? {
    val p = requestLine.trimEnd().split(' ')
    if (p.size != 3 || !p[2].startsWith("HTTP/1.") || p[0] !in setOf("GET", "POST")) return null
    return p[0].takeIf { MessageDigest.isEqual(p[1].toByteArray(), "/$token/".toByteArray()) }
}

/** An application/x-www-form-urlencoded body; later duplicates win. */
fun parseForm(body: String): Map<String, String> = body.split('&').filter { '=' in it }.associate {
    val (k, v) = it.split('=', limit = 2)
    URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
}

private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
