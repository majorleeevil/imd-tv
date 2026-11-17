package org.imdtv.iptvtv.ui.live

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import org.imdtv.iptvtv.ui.theme.LocalTheme

/** Digits typed with the remote's number keys: up to [max], then the number is complete. Timing lives with the caller. */
class DigitBuffer(private val max: Int = 4) {
    var text = ""; private set
    val active get() = text.isNotEmpty()
    /** Adds a digit; returns the number once [max] digits are in (and empties the buffer). */
    fun append(d: Int): Int? { text += d; return if (text.length >= max) commit() else null }
    /** OK or the timeout: the number typed so far, or null if none. */
    fun commit(): Int? = text.toIntOrNull().also { text = "" }
    fun cancel() { text = "" }
}

/** 0–9 on the main row or the number pad; null for any other key. */
fun digitOf(key: Key): Int? = when (key) {
    Key.Zero, Key.NumPad0 -> 0; Key.One, Key.NumPad1 -> 1; Key.Two, Key.NumPad2 -> 2; Key.Three, Key.NumPad3 -> 3; Key.Four, Key.NumPad4 -> 4
    Key.Five, Key.NumPad5 -> 5; Key.Six, Key.NumPad6 -> 6; Key.Seven, Key.NumPad7 -> 7; Key.Eight, Key.NumPad8 -> 8; Key.Nine, Key.NumPad9 -> 9
    else -> null
}

/** Top-right box over every screen: the digits so far ("75_") or "No channel 754". Back, while it shows, cancels the entry. */
@Composable
fun NumberEntryBox(vm: LiveViewModel, modifier: Modifier = Modifier) {
    val text by vm.typed.collectAsState()
    if (text.isEmpty()) return
    BackHandler { vm.cancelNumber() } // composed only while shown, so it outranks every screen's own Back
    val colors = LocalTheme.current.colors
    Box(modifier.fillMaxSize().padding(top = 28.dp, end = 40.dp)) {
        Text(text, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = colors.onFocus,
            modifier = Modifier.align(Alignment.TopEnd).background(colors.focus, RoundedCornerShape(10.dp)).padding(horizontal = 22.dp, vertical = 10.dp))
    }
}
