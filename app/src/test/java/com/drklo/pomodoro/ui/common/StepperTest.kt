package com.drklo.pomodoro.ui.common

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.drklo.pomodoro.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every duration in the app is set through this one control, so its clamping is what stops a project
 * from being configured with a zero-minute pomodoro or a two-hour break — and its three ways in
 * (drag, nudge, type) are what stopped 90 minutes from costing 65 taps.
 */
@RunWith(AndroidJUnit4::class)
class StepperTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val minus get() = context.getString(R.string.cd_decrease)
    private val plus get() = context.getString(R.string.cd_increase)

    @Test
    fun theButtonsMoveTheValueByOneStep() {
        var value = 25
        compose.setContent {
            Stepper(
                label = "Focus",
                value = value,
                onValueChange = { value = it },
                min = 1,
                max = 180,
                step = 5
            )
        }

        compose.onNodeWithContentDescription(plus).performClick()
        assertEquals(30, value)

        compose.onNodeWithContentDescription(minus).performClick()
        // Recomposition has not run — the caller owns the value — so this steps down from 25, not
        // from 30. What is under test is the arithmetic the control reports, not the state holder.
        assertEquals(20, value)
    }

    @Test
    fun itWillNotStepBelowItsMinimum() {
        compose.setContent {
            Stepper(label = "Focus", value = 1, onValueChange = {}, min = 1, max = 180)
        }

        compose.onNodeWithContentDescription(minus).assertIsNotEnabled()
    }

    @Test
    fun itWillNotStepAboveItsMaximum() {
        compose.setContent {
            Stepper(label = "Focus", value = 180, onValueChange = {}, min = 1, max = 180)
        }

        compose.onNodeWithContentDescription(plus).assertIsNotEnabled()
    }

    @Test
    fun theValueIsShownThroughTheCallersFormatter() {
        compose.setContent {
            Stepper(
                label = "Idle alert",
                value = 0,
                onValueChange = {},
                valueText = { if (it == 0) "Off" else "$it min" }
            )
        }

        // 0 means "off" for the idle alert, and the screen says so rather than showing a bare zero.
        compose.onNodeWithText("Off").assertExists()
    }

    @Test
    fun draggingLandsOnTheSlidersGridRatherThanBetweenTwoMinutes() {
        var value = 25
        compose.setContent {
            Stepper(
                label = "Focus",
                value = value,
                onValueChange = { value = it },
                min = 1,
                max = 180,
                sliderStep = 5
            )
        }

        // Straight to the semantics action, which is also the route a screen reader takes. 91 is not
        // on the grid: counting from the minimum in fives lands on 91 exactly, and that is the point
        // — the grid is anchored to `min`, not to zero, so the range's own end stays reachable.
        compose.onNodeWithContentDescription("Focus")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(93f) }

        assertEquals(91, value)
    }

    @Test
    fun theSliderReadsOutTheSameWordsAsTheLabelAboveIt() {
        compose.setContent {
            Stepper(
                label = "Focus",
                value = 25,
                onValueChange = {},
                min = 1,
                max = 180,
                valueText = { "$it min" }
            )
        }

        // A bare slider announces a fraction. What a screen reader should hear is the value the
        // sighted user is looking at.
        compose.onNodeWithContentDescription("Focus").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "25 min")
        )
    }

    @Test
    fun aValueCanBeTypedInInsteadOfTravelledTo() {
        var value = 25
        compose.setContent {
            Stepper(
                label = "Focus",
                value = value,
                onValueChange = { value = it },
                min = 1,
                max = 180
            )
        }

        compose.onNodeWithText("25").performClick()
        // The row still shows "25" behind the dialog, so the field is addressed by the one thing
        // only it can do.
        compose.onNode(hasSetTextAction()).performTextReplacement("47")
        compose.onNodeWithText(context.getString(R.string.action_ok)).performClick()

        assertEquals(47, value)
    }

    @Test
    fun aTypedValueOutsideTheRangeIsRefusedRatherThanQuietlyClamped() {
        var value = 25
        compose.setContent {
            Stepper(
                label = "Focus",
                value = value,
                onValueChange = { value = it },
                min = 1,
                max = 180
            )
        }

        compose.onNodeWithText("25").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("300")

        // Saving 180 would hide that the number was not taken literally, so the way out is closed
        // until the input makes sense.
        compose.onNodeWithText(context.getString(R.string.action_ok)).assertIsNotEnabled()
        assertEquals(25, value)
    }

    @Test
    fun aSurroundingListStillScrollsPastIt() {
        // Named as a risk when this control gained a slider: a horizontal drag handler sitting in a
        // vertically scrolling settings screen is exactly the shape of bug that makes a list feel
        // stuck. Settings is a scrolling list of these, so it is worth holding still.
        lateinit var scroll: androidx.compose.foundation.ScrollState
        compose.setContent {
            scroll = rememberScrollState()
            Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                repeat(12) { i ->
                    Stepper(
                        label = "Field $i",
                        value = 25,
                        onValueChange = {},
                        min = 1,
                        max = 180,
                        sliderStep = 5,
                        modifier = Modifier.height(96.dp)
                    )
                }
            }
        }

        compose.onRoot().performTouchInput { swipeUp() }
        compose.waitForIdle()

        assertTrue("the list did not move: offset ${scroll.value}", scroll.value > 0)
    }
}
