package com.drklo.pomodoro.ui.reports.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.drklo.pomodoro.R
import com.drklo.pomodoro.ui.reports.Aggregation

/**
 * Shared control for the period-based charts: Day/Week/Month chips and ‹ period › navigation.
 * Page 0 is the latest window; higher pages go back in time (forward is disabled at page 0).
 */
@Composable
fun AggregationBar(
    aggregation: Aggregation,
    onAggregationChange: (Aggregation) -> Unit,
    page: Int,
    onPageChange: (Int) -> Unit,
    periodLabel: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AggChip(stringResource(R.string.agg_day), aggregation == Aggregation.DAY) {
            onAggregationChange(Aggregation.DAY)
        }
        AggChip(stringResource(R.string.agg_week), aggregation == Aggregation.WEEK) {
            onAggregationChange(Aggregation.WEEK)
        }
        AggChip(stringResource(R.string.agg_month), aggregation == Aggregation.MONTH) {
            onAggregationChange(Aggregation.MONTH)
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onPageChange(page + 1) }) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = stringResource(R.string.cd_prev))
        }
        Text(
            text = periodLabel,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = { onPageChange(page - 1) }, enabled = page > 0) {
            Icon(Icons.Filled.ChevronRight, contentDescription = stringResource(R.string.cd_next))
        }
    }
}

@Composable
private fun AggChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = Modifier.padding(end = 0.dp)
    )
}
