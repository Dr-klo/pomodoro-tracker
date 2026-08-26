package com.drklo.pomodoro.ui.main.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.drklo.pomodoro.data.model.TimerStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The dial is the app's primary control, and for most of this project's life it was invisible to
 * accessibility services: a Canvas under raw pointer input announces no name, no role and no way to
 * activate it (F-R0-01). These tests pin the contract that fixed it, because the failure is silent —
 * to a sighted user the app looks and behaves identically whether or not it still holds.
 */
@RunWith(AndroidJUnit4::class)
class TimerDialTest {

    @get:Rule
    val compose = createComposeRule()

    private fun setDial(
        status: TimerStatus = TimerStatus.IDLE,
        tapLabel: String? = "start",
        onTap: () -> Unit = {}
    ) {
        compose.setContent {
            TimerDial(
                fraction = 1f,
                color = Color.White,
                status = status,
                showHand = false,
                onTap = onTap,
                onSeek = {},
                tapLabel = tapLabel
            )
        }
    }

    @Test
    fun anAccessibilityServiceFindsAnActivatableButton() {
        setDial()

        compose.onNode(hasClickAction())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    @Test
    fun theActionSaysWhatActivatingItWillDo() {
        setDial(status = TimerStatus.RUNNING, tapLabel = "pause")

        // A bare "double tap to activate" would not say whether the timer is about to start or
        // stop, so the label travels with the action.
        compose.onNode(hasClickAction()).assert(
            SemanticsMatcher("click action is labelled \"pause\"") { node ->
                node.config.getOrNull(SemanticsActions.OnClick)?.label == "pause"
            }
        )
    }

    @Test
    fun activatingItReportsATap() {
        var taps = 0
        setDial(onTap = { taps++ })

        compose.onNode(hasClickAction()).performClick()

        // performClick() goes through the semantics action — the same route a screen reader takes,
        // and precisely the path that used to lead nowhere.
        assertEquals(1, taps)
    }

    @Test
    fun theCallerDescribesWhatTheTimerIsDoing() {
        // TimerDial knows the fraction, not the phase or the clock, so the screen supplies the
        // description from outside. This checks that arrangement actually reaches the node.
        compose.setContent {
            TimerDial(
                fraction = 0.5f,
                color = Color.White,
                status = TimerStatus.PAUSED,
                showHand = false,
                onTap = {},
                onSeek = {},
                modifier = Modifier.semantics { contentDescription = "Play or pause" }
            )
        }

        compose.onNodeWithContentDescription("Play or pause").assertExists()
    }
}
