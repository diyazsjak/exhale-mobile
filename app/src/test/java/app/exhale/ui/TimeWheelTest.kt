package app.exhale.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import app.exhale.ui.components.TimeWheel
import app.exhale.ui.theme.ExhaleTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The wheel turns like a spinner: the value below (or a drag up) is later, the one above earlier. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TimeWheelTest {
    @get:Rule val compose = createComposeRule()

    private var minute by mutableIntStateOf(13 * 60 + 5)

    private fun wheel(latest: Int = 24 * 60) = compose.setContent {
        ExhaleTheme(dark = true) { TimeWheel(minute, { minute = it }, canSet = { it <= latest }, h24 = true) }
    }

    @Test fun tappingBelow_isLater_andAbove_isEarlier() {
        wheel()
        compose.onNodeWithContentDescription("One minute later").performClick()
        compose.onNodeWithContentDescription("One hour later").performClick()
        assertEquals(14 * 60 + 6, minute)
        compose.onNodeWithContentDescription("One minute earlier").performClick()
        compose.onNodeWithContentDescription("One minute earlier").performClick()
        assertEquals(14 * 60 + 4, minute)
    }

    @Test fun draggingUp_turnsToLater_andDown_toEarlier() {
        wheel()
        // The minute wheel is the second; drag it up two slots (44dp each, ExSize.touch), then down one.
        val minutes = compose.onAllNodesWithContentDescription("One minute later")[0]
        minutes.performTouchInput { swipe(Offset(centerX, centerY), Offset(centerX, centerY - 88.dp.toPx()), 400) }
        compose.waitForIdle()
        assertEquals(13 * 60 + 7, minute)
        minutes.performTouchInput { swipe(Offset(centerX, centerY), Offset(centerX, centerY + 44.dp.toPx()), 300) }
        compose.waitForIdle()
        assertEquals(13 * 60 + 6, minute)
    }

    @Test fun aFastFlick_turnsAsFarAsTheFingerWent() {
        wheel()
        // Two slots in 40ms: many moves arrive before the wheel's offset catches up.
        compose.onAllNodesWithContentDescription("One minute later")[0]
            .performTouchInput { swipe(Offset(centerX, centerY), Offset(centerX, centerY - 88.dp.toPx()), 40) }
        compose.waitForIdle()
        assertEquals(13 * 60 + 7, minute)
    }

    @Test fun holdingAHalf_stepsOnce_soAPauseBeforeADragAddsNothing() {
        wheel()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("One minute later").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithContentDescription("One minute later").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(100)
        assertEquals(13 * 60 + 6, minute)
    }

    @Test fun aTimeThatCantBeChosen_isNeitherTappedNorDraggedTo() {
        minute = 13 * 60 + 5
        wheel(latest = 13 * 60 + 6)
        compose.onNodeWithContentDescription("One minute later").performClick()
        compose.onNodeWithContentDescription("One minute later").performClick()
        assertEquals(13 * 60 + 6, minute)
        compose.onAllNodesWithContentDescription("One minute later")[0]
            .performTouchInput { swipe(Offset(centerX, centerY), Offset(centerX, centerY - 132.dp.toPx()), 400) }
        compose.waitForIdle()
        assertEquals(13 * 60 + 6, minute)
    }
}
