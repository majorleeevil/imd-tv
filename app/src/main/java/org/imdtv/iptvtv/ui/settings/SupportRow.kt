package org.imdtv.iptvtv.ui.settings

import androidx.compose.ui.res.stringResource
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.imdtv.iptvtv.data.support.LogCollector
import org.imdtv.iptvtv.data.support.LogServer
import org.imdtv.iptvtv.data.support.lanAddress
import org.imdtv.iptvtv.data.support.scrubLog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import org.imdtv.iptvtv.IptvApp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import org.imdtv.iptvtv.BuildConfig
import org.imdtv.iptvtv.R
import org.imdtv.iptvtv.ui.MenuFrame
import org.imdtv.iptvtv.ui.MenuItem

const val SUPPORT_URL = "https://ko-fi.com/majorleeevil"
const val SUPPORT_LABEL = "ko-fi.com/majorleeevil"
const val WEBSITE_LABEL = "imd-tv.com"
const val WEBSITE_URL = "https://$WEBSITE_LABEL/"
const val SITE_LABEL = "majorleeevil.github.io/imd-tv"
const val SITE_URL = "https://$SITE_LABEL/"
const val CONTACT_EMAIL = "support@imd-tv.com"

/** Settings › About: version, website, source, licence, contact, and the quiet Support IMD TV entry. */
@Composable
fun AboutRows(first: Modifier) {
    VersionRow(first)
    QrRow(stringResource(R.string.set_b_website), stringResource(R.string.set_b_website_expl), WEBSITE_LABEL, R.drawable.website_qr, WEBSITE_LABEL)
    QrRow(stringResource(R.string.set_b_source), stringResource(R.string.set_b_source_expl), SITE_LABEL, R.drawable.site_qr, SITE_LABEL)
    SettingRow(stringResource(R.string.set_b_licence), stringResource(R.string.set_b_licence_expl), "GPL-3.0") {}
    SettingRow(stringResource(R.string.set_b_contact), stringResource(R.string.set_b_contact_expl), CONTACT_EMAIL) {}
    SendLogsRow()
    QrRow(stringResource(R.string.set_b_support), stringResource(R.string.set_b_support_expl), null, R.drawable.support_qr, SUPPORT_LABEL)
}

/** Version: OK opens the update prompt when there is one, else checks GitHub now. */
@Composable
private fun VersionRow(first: Modifier) {
    val updater = (LocalContext.current.applicationContext as IptvApp).container.updater
    val update by updater.available.collectAsState()
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val u = update
    val msgCheckFailed = stringResource(R.string.set_b_check_failed)
    val msgLatest = stringResource(R.string.set_b_latest)
    SettingRow(stringResource(R.string.set_b_version), result ?: if (u != null) stringResource(R.string.set_b_version_available, u.version) else stringResource(R.string.set_b_version_check),
        BuildConfig.VERSION_NAME, first, busy = checking) {
        if (u != null) updater.prompt.value = true
        else if (!checking) scope.launch {
            checking = true
            val ok = updater.check(manual = true)
            checking = false
            result = if (!ok) msgCheckFailed else if (updater.available.value == null) msgLatest else null
        }
    }
}

/** A row whose OK shows a QR code for [label] (a TV has no browser) with the address written out. Never prompts. */
@Composable
private fun QrRow(title: String, explanation: String, value: String?, qr: Int, label: String) {
    var open by remember { mutableStateOf(false) }
    SettingRow(title, explanation, value) { open = true }
    if (open) MenuFrame(title, onDismiss = { open = false }) { first ->
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            // The QR is drawn at 1 px per module: scale without smoothing or phones can't read it.
            Image(ImageBitmap.imageResource(qr), contentDescription = stringResource(R.string.set_b_qr_for, label), filterQuality = FilterQuality.None,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White).padding(12.dp).size(200.dp))
        }
        Text(stringResource(R.string.set_b_scan_visit, label), fontSize = 15.sp, color = Color.White, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
        MenuItem(stringResource(R.string.set_b_close), Modifier.focusRequester(first)) { open = false }
    }
}

private const val LOG_PAGE_MS = 10 * 60 * 1000L

/** [text] as a QR code at 1 px per module: draw it with FilterQuality.None. */
fun qrImage(text: String): ImageBitmap {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0))
    return Bitmap.createBitmap(IntArray(m.width * m.height) { i -> if (m.get(i % m.width, i / m.width)) 0xFF000000.toInt() else -1 }, m.width, m.height, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/**
 * Add / edit playlist from a phone: a QR code (with the hint and address under it) for [fields]' form on the local network,
 * served while this is on screen. Each send goes to [onSubmit] and starts a fresh link, so the phone can send again.
 */
@Composable
fun PhoneFormQr(path: String, title: String, types: List<String>, type: String, fields: List<org.imdtv.iptvtv.data.support.PhoneField>,
                onSubmit: (Map<String, String>) -> Unit, modifier: Modifier = Modifier, done: String = stringResource(R.string.set_b_phone_done)) {
    var server by remember { mutableStateOf<org.imdtv.iptvtv.data.support.PhoneFormServer?>(null) }
    var qr by remember { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember { mutableStateOf(false) }
    var gen by remember { mutableStateOf(0) }
    val send = stringResource(R.string.set_b_phone_send)
    val now by androidx.compose.runtime.rememberUpdatedState(Triple(fields, type, onSubmit))
    val scope = rememberCoroutineScope()
    LaunchedEffect(gen) {
        server?.stop()
        val s = withContext(Dispatchers.IO) {
            runCatching {
                val addr = lanAddress() ?: return@runCatching null
                // The server's thread calls back: hand the form to the main thread, then serve a new link (with the new values).
                org.imdtv.iptvtv.data.support.PhoneFormServer(addr, path, title, types, now.second, now.first, send, done) { form -> scope.launch { now.third(form); gen++ } }
                    .also { qr = qrImage(it.url) }
            }.getOrNull()
        }
        server = s
        failed = s == null
    }
    DisposableEffect(Unit) { onDispose { server?.stop() } }
    androidx.compose.foundation.layout.Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        val img = qr
        if (img != null) Image(img, contentDescription = stringResource(R.string.set_b_use_phone), filterQuality = FilterQuality.None,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White).padding(10.dp).size(170.dp))
        Text(when {
            img != null -> stringResource(R.string.set_b_phone_scan, server?.address ?: "")
            failed -> stringResource(R.string.set_b_log_no_net)
            else -> stringResource(R.string.set_b_log_preparing)
        }, fontSize = 12.sp, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
    }
}

/** Send logs: OK builds the scrubbed log, serves it on the local network for 10 minutes and shows a QR code for it. Nothing runs otherwise. */
@Composable
private fun SendLogsRow() {
    var open by remember { mutableStateOf(false) }
    SettingRow(stringResource(R.string.set_b_send_logs), stringResource(R.string.set_b_send_logs_expl), null) { open = true }
    if (open) SendLogsPage { open = false }
}

/** The Send logs page; [extraSecrets] are scrubbed too (e.g. a login typed into an add that failed, so never saved). */
@Composable
fun SendLogsPage(extraSecrets: List<String> = emptyList(), onClose: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as IptvApp
    var server by remember { mutableStateOf<LogServer?>(null) }
    var qr by remember { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val s = withContext(Dispatchers.IO) {
            runCatching {
                val addr = lanAddress() ?: return@runCatching null
                val c = app.container
                val providers = c.db.providerDao().all()
                val (prev, cur) = LogCollector.read(ctx)
                val header = "IMD TV ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}\n" +
                    "Theme: ${c.settings.settings.first().theme}\nPlaylists: ${providers.size} ${providers.map { it.type }}\n\n"
                // Stalker passwords hold "serial\ndeviceId": each line is a secret.
                val secrets = (providers.flatMap { listOfNotNull(it.username, it.password) } + extraSecrets).flatMap { it.lines() }
                LogServer(addr, (header + scrubLog(prev + cur, secrets)).toByteArray()).also {
                    server = it // set at once so closing the page mid-build still stops it
                    qr = qrImage(it.url)
                }
            }.getOrNull()
        }
        server = s
        failed = s == null
        delay(LOG_PAGE_MS)
        onClose()
    }
    DisposableEffect(Unit) { onDispose { server?.stop() } }
    MenuFrame(stringResource(R.string.set_b_send_logs), onDismiss = onClose) { first ->
        val img = qr
        if (img != null) Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            Image(img, contentDescription = stringResource(R.string.set_b_qr_log), filterQuality = FilterQuality.None,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White).padding(12.dp).size(200.dp))
        }
        Text(when {
            img != null -> stringResource(R.string.set_b_log_ready, server?.url ?: "", CONTACT_EMAIL)
            failed -> stringResource(R.string.set_b_log_no_net)
            else -> stringResource(R.string.set_b_log_preparing)
        }, fontSize = 15.sp, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
        MenuItem(stringResource(R.string.set_b_close), Modifier.focusRequester(first)) { onClose() }
    }
}
