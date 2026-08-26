package com.drklo.pomodoro.ui.common

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every duration in the app is set through this one control, so its clamping is what stops a
 * project from being configured with a zero-minute pomodoro or a two-hour break.
 */
@RunWith(AndroidJUnit4::class)
class StepperTest {

    @get:Rule
    val compose = createComposeRule()

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

        compose.onNodeWithContentDescription("+").performClick()
        assertEquals(30, value)

        compose.onNodeWithContentDescription("-").performClick()
        // Recomposition has not run — the caller owns the value — so this steps down from 25, not
        // from 30. What is under test is the arithmetic the control reports, not the state holder.
        assertEquals(20, value)
    }

    @Test
    fun itWillNotStepBelowItsMinimum() {
        compose.setContent {
            Stepper(label = "Focus", value = 1, onValueChange = {}, min = 1, max = 180)
        }

        compose.onNodeWithContentDescription("-").assertIsNotEnabled()
    }

    @Test
    fun itWillNotStepAboveItsMaximum() {
        compose.setContent {
            Stepper(label = "Focus", value = 180, onValueChange = {}, min = 1, max = 180)
        }

        compose.onNodeWithContentDescription("+").assertIsNotEnabled()
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
}
