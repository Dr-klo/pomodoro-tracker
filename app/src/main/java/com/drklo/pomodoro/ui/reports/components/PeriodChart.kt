package com.drklo.pomodoro.ui.reports.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.drklo.pomodoro.R
import com.drklo.pomodoro.data.model.PomodoroLog
import com.drklo.pomodoro.data.model.Project
import com.drklo.pomodoro.ui.reports.Aggregation
import com.drklo.pomodoro.ui.reports.bucketsFor
import com.drklo.pomodoro.ui.reports.buildStackedBars
import com.drklo.pomodoro.ui.reports.periodLabel
import java.time.LocalDate
import java.util.Locale

/**
 * A stacked bar chart over time with its own Day/Week/Month aggregation and ‹ period › navigation.
 * Reused for focus-time (B3) and pomodoro-count (C3) by swapping [valueOf].
 */
@Composable
fun PeriodChart(
    title: String,
    logs: List<PomodoroLog>,
    projects: List<Project>,
    today: LocalDate,
    valueOf: (PomodoroLog) -> Float,
    summaryFormatter: (Float) -> String
) {
    val locale = Locale.getDefault()
    var aggregation by remember { mutableStateOf(Aggregation.DAY) }
    var page by remember { mutableIntStateOf(0) }

    val buckets = remember(aggregation, page, today) { bucketsFor(aggregation, today, page, locale) }
    val bars = remember(logs, buckets, projects) { buildStackedBars(logs, buckets, projects, valueOf) }

    val average = if (bars.isNotEmpty()) bars.sumOf { it.total.toDouble() } / bars.size else 0.0
    val maxTotal = bars.maxOfOrNull { it.total } ?: 0f

    ChartCard(
        title = title,
        subtitle = stringResource(
            R.string.chart_avg_max,
            summaryFormatter(average.toFloat()),
            summaryFormatter(maxTotal)
        )
    ) {
        AggregationBar(
            aggregation = aggregation,
            onAggregationChange = { aggregation = it; page = 0 },
            page = page,
            onPageChange = { page = it.coerceAtLeast(0) },
            periodLabel = periodLabel(buckets)
        )
        StackedBarChart(columns = bars)
    }
}
