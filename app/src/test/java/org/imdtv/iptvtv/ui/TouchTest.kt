package org.imdtv.iptvtv.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Touch stand-ins for remote keys (Reddit report: playlist fields other than the first never opened on a phone). */
@RunWith(RobolectricTestRunner::class)
class TouchTest {
    @get:Rule val rule = createComposeRule()

    @Test fun aTapOpensAReadOnlyUntilOkField() {
        var text by mutableStateOf("")
        rule.setContent { Field("Username", text, openOnOk = true, modifier = Modifier.testTag("f")) { text = it } }
        // Untapped it's read-only: typing is refused.
        assertTrue(runCatching { rule.onNodeWithTag("f").performTextInput("x") }.isFailure)
        rule.onNodeWithTag("f").performTouchInput { click() }
        rule.waitForIdle()
        rule.onNodeWithTag("f").performTextInput("abc")
        assertEquals("abc", text)
    }

    @Test fun aSwipeStepsAndDoesNotClick() {
        val steps = mutableListOf<Pair<Int, Int>>()
        var clicks = 0
        rule.setContent {
            Box(Modifier.size(400.dp).testTag("b").onSwipe { dx, dy -> steps += dx to dy }.clickable { clicks++ })
        }
        rule.onNodeWithTag("b").performTouchInput { swipeLeft() }
        assertTrue(steps.isNotEmpty() && steps.all { it == -1 to 0 })
        assertEquals(0, clicks)
        steps.clear()
        rule.onNodeWithTag("b").performTouchInput { swipeUp() }
        assertTrue(steps.isNotEmpty() && steps.all { it == 0 to -1 })
    }

    @Test fun aSwipeOnADisallowedAxisIsLeftAlone() {
        var steps = 0
        rule.setContent { Box(Modifier.size(400.dp).testTag("b").onSwipe(vertical = false) { _, _ -> steps++ }) }
        rule.onNodeWithTag("b").performTouchInput { swipeUp() }
        assertEquals(0, steps)
    }

    @Test fun aTouchTapFiresOnATapOnly() {
        var taps = 0
        rule.setContent { Box(Modifier.size(400.dp).testTag("b").onTouchTap { taps++ }) }
        rule.onNodeWithTag("b").performTouchInput { click() }
        assertEquals(1, taps)
        rule.onNodeWithTag("b").performTouchInput { swipeLeft() }
        assertEquals(1, taps)
    }

    @Test fun aTapPressesAFormButton() { // #59: tv-material3's Button answered OK only
        var clicks = 0
        rule.setContent { FormButton(onClick = { clicks++ }, Modifier.testTag("b")) { androidx.tv.material3.Text("Save") } }
        rule.onNodeWithTag("b").performTouchInput { click() }
        assertEquals(1, clicks)
    }

    @Test fun aTapOnAGreyedOutFormButtonDoesNothing() {
        var clicks = 0
        rule.setContent { FormButton(onClick = { clicks++ }, Modifier.testTag("b"), enabled = false) { androidx.tv.material3.Text("Watch") } }
        rule.onNodeWithTag("b").performTouchInput { click() }
        assertEquals(0, clicks)
    }

    @Test fun aLongPressIsHoldOkAndSwallowsTheClick() {
        var long = 0
        var clicks = 0
        rule.setContent {
            var unused by remember { mutableStateOf(0) }
            Box(Modifier.size(200.dp).testTag("b").onLongOk { long++; unused++ }.clickable { clicks++ })
        }
        rule.onNodeWithTag("b").performTouchInput { longClick() }
        assertEquals(1 to 0, long to clicks)
        rule.onNodeWithTag("b").performTouchInput { click() }
        assertEquals(1 to 1, long to clicks)
    }
}
