package org.imdtv.iptvtv.ui.settings

import androidx.compose.foundation.Image
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

const val SUPPORT_URL = "https://buymeacoffee.com/majorleeevil"
const val SUPPORT_LABEL = "buymeacoffee.com/majorleeevil"
const val SITE_LABEL = "majorleeevil.github.io/imd-tv"
const val SITE_URL = "https://$SITE_LABEL/"
const val CONTACT_EMAIL = "imd-tv@whittall.org"

/** Settings › About: version, website & source, licence, contact, and the quiet Support IMD TV entry. */
@Composable
fun AboutRows(first: Modifier) {
    VersionRow(first)
    QrRow("Website & source", "News, help and the source code", SITE_LABEL, R.drawable.site_qr, SITE_LABEL)
    SettingRow("Licence", "Free and open source software", "GPL-3.0") {}
    SettingRow("Contact", "Questions and feedback", CONTACT_EMAIL) {}
    QrRow("Support IMD TV", "Free and open source. Buy me a coffee if you'd like", null, R.drawable.support_qr, SUPPORT_LABEL)
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
    SettingRow("Version", result ?: if (u != null) "Version ${u.version} is available. Press OK to update" else "IMD TV for Android TV. Press OK to check for updates",
        BuildConfig.VERSION_NAME, first, busy = checking) {
        if (u != null) updater.prompt.value = true
        else if (!checking) scope.launch {
            checking = true
            val ok = updater.check(manual = true)
            checking = false
            result = if (!ok) "Couldn't check for updates. Check your connection" else if (updater.available.value == null) "You have the latest version" else null
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
            Image(ImageBitmap.imageResource(qr), contentDescription = "QR code for $label", filterQuality = FilterQuality.None,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White).padding(12.dp).size(200.dp))
        }
        Text("Scan with your phone, or visit\n$label", fontSize = 15.sp, color = Color.White, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
        MenuItem("Close", Modifier.focusRequester(first)) { open = false }
    }
}
