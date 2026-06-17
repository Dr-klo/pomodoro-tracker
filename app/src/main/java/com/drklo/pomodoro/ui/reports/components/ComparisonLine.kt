package com.drklo.pomodoro.ui.reports.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.drklo.pomodoro.R
import kotlin.math.abs

/**
 * Builds a "+X more/less than <when>" sentence comparing the current value to the previous period.
 * Returns "" when both periods are empty.
 */
@Composable
fun comparisonLine(
    current: Float,
    previous: Float,
    whenLabel: String,
    formatValue: (Float) -> String
): String {
    val delta = current - previous
    return when {
        current == 0f && previous == 0f -> ""
        abs(delta) < 0.5f -> stringResource(R.string.compare_same, whenLabel)
        delta > 0f -> stringResource(R.string.compare_more, formatValue(delta), whenLabel)
        else -> stringResource(R.string.compare_less, formatValue(-delta), whenLabel)
    }
}
