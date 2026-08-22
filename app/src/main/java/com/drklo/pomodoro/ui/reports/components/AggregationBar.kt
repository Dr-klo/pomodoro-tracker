package com.drklo.pomodoro.ui.reports.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Row
import com.drklo.pomodoro.R
import com.drklo.pomodoro.ui.common.SegmentedChoice
import com.drklo.pomodoro.ui.reports.Aggregation

/**
 * Shared control for the period-based charts: Day/Week/Month segmented buttons and ‹ period ›
 * navigation. Page 0 is the latest window; higher pages go back (forward is disabled at page 0).
 */
@Composable
fun AggregationBar(
    aggregation: Aggregation,
    onAggregationChange: (Aggregation) -> Unit,
    paging: Paging,
    periodLabel: String,
    modifier: Modifier = Modifier
) {
    SegmentedChoice(
        options = listOf(
            Aggregation.DAY to stringResource(R.string.agg_day),
            Aggregation.WEEK to stringResource(R.string.agg_week),
            Aggregation.MONTH to stringResource(R.string.agg_month)
        ),
        selected = aggregation,
        onSelect = onAggregationChange,
        modifier = modifier
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Bounded by the oldest pomodoro on record: without it, "‹" walks off into an endless run
        // of empty periods with nothing to say the history has ended.
        IconButton(onClick = { paging.onChange(paging.page + 1) }, enabled = paging.canGoBack) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = stringResource(R.string.cd_prev))
        }
        Text(
            text = periodLabel,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = { paging.onChange(paging.page - 1) }, enabled = paging.page > 0) {
            Icon(Icons.Filled.ChevronRight, contentDescription = stringResource(R.string.cd_next))
        }
    }
}

/**
 * ‹ › navigation for a report chart: which page is shown, whether there is any history left behind
 * it, and how to move. The three always travel together, so they travel as one.
 */
data class Paging(val page: Int, val canGoBack: Boolean, val onChange: (Int) -> Unit)
