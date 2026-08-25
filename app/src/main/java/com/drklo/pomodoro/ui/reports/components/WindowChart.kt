package com.drklo.pomodoro.ui.reports.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import com.drklo.pomodoro.R
import com.drklo.pomodoro.data.model.PomodoroLog
import com.drklo.pomodoro.data.model.Project
import com.drklo.pomodoro.ui.reports.Aggregation
import com.drklo.pomodoro.ui.reports.ProjectValue
import com.drklo.pomodoro.ui.reports.earliestLogDate
import com.drklo.pomodoro.ui.reports.elapsedDaysIn
import com.drklo.pomodoro.ui.reports.sumByProject
import com.drklo.pomodoro.ui.reports.sumInRange
import com.drklo.pomodoro.ui.reports.windowFor
import java.time.LocalDate

/**
 * A snapshot chart over a single Day/Week/Month window with ‹ window › navigation. Computes the
 * per-project breakdown for the window and hands it to [content] (donut C2, project bars C1).
 */
@Composable
fun WindowChart(
    title: String,
    logs: List<PomodoroLog>,
    projects: List<Project>,
    today: LocalDate,
    valueOf: (PomodoroLog) -> Float,
    formatValue: (Float) -> String,
    subtitle: @Composable (List<ProjectValue>) -> String,
    content: @Composable (List<ProjectValue>) -> Unit
) {
    val locale = LocalLocale.current.platformLocale
    var aggregation by remember { mutableStateOf(Aggregation.DAY) }
    var page by remember { mutableIntStateOf(0) }

    val window = remember(aggregation, page, today) { windowFor(aggregation, today, page, locale) }
    val values = remember(logs, window, projects) { sumByProject(logs, window, projects, valueOf) }

    val prevWindow = remember(aggregation, page, today) { windowFor(aggregation, today, page + 1, locale) }

    // Only the elapsed part of the previous window counts, or the current (unfinished) one always
    // looks like a slump: a Monday measured against a whole week.
    val elapsed = elapsedDaysIn(window.start, window.endInclusive, today)
    val currentTotal = sumInRange(logs, window.start, window.start.plusDays(elapsed - 1), valueOf)
    val prevTotal = sumInRange(logs, prevWindow.start, prevWindow.start.plusDays(elapsed - 1), valueOf)
    val earliest = remember(logs) { earliestLogDate(logs) }
    val whenLabel = stringResource(
        when (aggregation) {
            Aggregation.DAY -> R.string.prev_day
            Aggregation.WEEK -> R.string.prev_week
            Aggregation.MONTH -> R.string.prev_month
        }
    )
    val compare = comparisonLine(currentTotal, prevTotal, whenLabel, formatValue)
    val baseSubtitle = subtitle(values)

    ChartCard(title = title, subtitle = if (compare.isEmpty()) baseSubtitle else "$baseSubtitle\n$compare") {
        AggregationBar(
            aggregation = aggregation,
            onAggregationChange = {
                aggregation = it
                page = 0
            },
            paging = Paging(
                page = page,
                canGoBack = earliest != null && window.start.isAfter(earliest),
                onChange = { page = it.coerceAtLeast(0) }
            ),
            periodLabel = window.label
        )
        // Charts that render their own tooltip own their own selection; keying on the window is
        // what drops it when the user pages, so a tooltip never outlives the data it described.
        key(aggregation, page) { content(values) }
    }
}
