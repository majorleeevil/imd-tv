package org.imdtv.iptvtv.ui

import androidx.compose.ui.res.stringResource
import org.imdtv.iptvtv.R

import org.imdtv.iptvtv.ui.theme.LocalTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text

/** A small D-pad menu in a dialog; Back dismisses it. Picking an item does not dismiss by itself. */
@Composable
fun ContextMenu(title: String, items: List<Pair<String, () -> Unit>>, onDismiss: () -> Unit) = ContextMenu(title, items, 0, onDismiss)

/** As above, with focus starting on item [focusIndex]. */
@Composable
fun ContextMenu(title: String, items: List<Pair<String, () -> Unit>>, focusIndex: Int, onDismiss: () -> Unit) {
    MenuFrame(title, onDismiss) { first ->
        items.forEachIndexed { i, (label, action) ->
            MenuItem(label, if (i == focusIndex) Modifier.focusRequester(first) else Modifier, onClick = action)
        }
    }
}

/** A one-field prompt (rename, new category). Save passes the trimmed text, which may be empty. */
@Composable
fun TextPrompt(title: String, initial: String, hint: String, onSave: (String) -> Unit, keyboard: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    MenuFrame(title, onDismiss) { first ->
        Field(hint, text, keyboard, modifier = Modifier.focusRequester(first)) { text = it }
        MenuItem(stringResource(R.string.live_save), Modifier) { onSave(text.trim()) }
        MenuItem(stringResource(R.string.live_cancel), Modifier, onClick = onDismiss)
    }
}

private val OK_KEYS = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

@Composable
internal fun MenuFrame(title: String, onDismiss: () -> Unit, content: @Composable (FocusRequester) -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { first.requestFocus() } }
    // A long OK opens this menu while the button is still down: its repeats and release must not pick an item.
    // OK counts only from the first fresh press after opening.
    var armed by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.onPreviewKeyEvent { e ->
                if (armed || e.key !in OK_KEYS) return@onPreviewKeyEvent false
                if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) { armed = true; false } else true
            }.width(420.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xF2101A3A)).padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = LocalTheme.current.colors.muted, maxLines = 2,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 10.dp))
            content(first)
        }
    }
}

@Composable
internal fun MenuItem(label: String, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    // Disabled: still focusable so it reads as unavailable, greyed, OK does nothing.
    FocusBox(onClick = { if (enabled) onClick() }, modifier = modifier) { f ->
        Text(label, fontSize = 16.sp, color = if (!enabled) Color(0x80FFFFFF).let { if (f) Color(0x99000000) else it } else if (f) Color.Black else Color.White,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (f) Color.White else Color.Transparent)
                .padding(horizontal = 14.dp, vertical = 10.dp))
    }
}
