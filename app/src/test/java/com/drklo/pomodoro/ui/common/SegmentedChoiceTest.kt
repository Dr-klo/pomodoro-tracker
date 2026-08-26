package com.drklo.pomodoro.ui.common

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Theme, language and report aggregation are all chosen through this control. */
@RunWith(AndroidJUnit4::class)
class SegmentedChoiceTest {

    @get:Rule
    val compose = createComposeRule()

    private val options = listOf("system" to "System", "light" to "Light", "dark" to "Dark")

    @Test
    fun choosingAnOptionReportsIt() {
        var chosen = "system"
        compose.setContent {
            SegmentedChoice(options = options, selected = chosen, onSelect = { chosen = it })
        }

        compose.onNodeWithText("Dark").performClick()

        assertEquals("dark", chosen)
    }

    @Test
    fun theCurrentOptionIsMarkedSelected() {
        compose.setContent {
            SegmentedChoice(options = options, selected = "light", onSelect = {})
        }

        // Selection has to reach the semantics tree, not just the paint: a screen reader announces
        // the state from here, and so does every UI test after this one.
        compose.onNodeWithText("Light").assertIsSelected()
    }
}
