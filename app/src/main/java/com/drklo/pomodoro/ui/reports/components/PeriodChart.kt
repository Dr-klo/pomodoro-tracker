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
import com.drklo.pomodoro.ui.reports.earliestLogDate
import com.drklo.pomodoro.ui.reports.elapsedDaysIn
import com.drklo.pomodoro.ui.reports.periodLabel
import com.drklo.pomodoro.ui.reports.sumInRange
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

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
    var selected by remember { mutableStateOf<Int?>(null) }

    val buckets = remember(aggregation, page, today) { bucketsFor(aggregation, today, page, locale) }
    val bars = remember(logs, buckets, projects) {
        buildStackedBars(logs, buckets, projects, locale, valueOf)
    }

    val prevBuckets = remember(aggregation, page, today) { bucketsFor(aggregation, today, page + 1, locale) }

    // Compare like with like: only as many days of the previous period as have elapsed in this one.
    // Measured against a whole previous period, an unfinished one reads as a collapse every time.
    val elapsed = elapsedDaysIn(buckets.first().start, buckets.last().endInclusive, today)
    val periodStart = buckets.first().start
    val prevStart = prevBuckets.first().start
    val currentTotal = sumInRange(logs, periodStart, periodStart.plusDays(elapsed - 1), valueOf)
    val prevTotal = sumInRange(logs, prevStart, prevStart.plusDays(elapsed - 1), valueOf)

    // The average is over the buckets that actually finished; counting today's half-done one as a
    // full one drags the average down every morning.
    val finished = bars.filterIndexed { i, _ -> buckets[i].endInclusive.isBefore(today) }
    val averageBasis = finished.ifEmpty { bars }
    val average =
        if (averageBasis.isNotEmpty()) averageBasis.sumOf { it.total.toDouble() } / averageBasis.size else 0.0
    val maxTotal = bars.maxOfOrNull { it.total } ?: 0f
    val barsTotal = bars.sumOf { it.total.toDouble() }.toFloat()
    val earliest = remember(logs) { earliestLogDate(logs) }

    val avgMax = stringResource(
        R.string.chart_avg_max,
        summaryFormatter(average.toFloat()),
        summaryFormatter(maxTotal)
    )
    val compare = comparisonLine(currentTotal, prevTotal, stringResource(R.string.prev_period), summaryFormatter)

    ChartCard(
        title = title,
        subtitle = if (compare.isEmpty()) avgMax else "$avgMax\n$compare"
    ) {
        AggregationBar(
            aggregation = aggregation,
            onAggregationChange = { aggregation = it; page = 0; selected = null },
            paging = Paging(
                page = page,
                canGoBack = earliest != null && periodStart.isAfter(earliest),
                onChange = {
                    page = it.coerceAtLeast(0)
                    selected = null
                }
            ),
            periodLabel = periodLabel(buckets)
        )
        StackedBarChart(
            columns = bars,
            selectedIndex = selected?.takeIf { it in bars.indices },
            onSelect = { selected = it }
        )
        val column = selected?.let { bars.getOrNull(it) }
        ChartTooltip(
            title = column?.rangeLabel,
            focusSec = column?.focusSec ?: 0,
            pomodoros = column?.pomodoros ?: 0,
            hint = stringResource(R.string.chart_tap_hint_bar),
            note = column?.let { col ->
                val share = if (barsTotal > 0f) (col.total / barsTotal * 100f).roundToInt() else 0
                stringResource(R.string.chart_share_of_total, share)
            },
            rows = column?.segments.orEmpty().map {
                TooltipRow(it.name, it.colorArgb, it.focusSec, it.pomodoros)
            }
        )
    }
}
