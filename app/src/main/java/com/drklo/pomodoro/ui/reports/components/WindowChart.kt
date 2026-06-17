package com.drklo.pomodoro.ui.reports.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.drklo.pomodoro.data.model.PomodoroLog
import com.drklo.pomodoro.data.model.Project
import com.drklo.pomodoro.ui.reports.Aggregation
import com.drklo.pomodoro.ui.reports.ProjectValue
import com.drklo.pomodoro.ui.reports.sumByProject
import com.drklo.pomodoro.ui.reports.windowFor
import java.time.LocalDate
import java.util.Locale

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
    subtitle: @Composable (List<ProjectValue>) -> String,
    content: @Composable (List<ProjectValue>) -> Unit
) {
    val locale = Locale.getDefault()
    var aggregation by remember { mutableStateOf(Aggregation.DAY) }
    var page by remember { mutableIntStateOf(0) }

    val window = remember(aggregation, page, today) { windowFor(aggregation, today, page, locale) }
    val values = remember(logs, window, projects) { sumByProject(logs, window, projects, valueOf) }

    ChartCard(title = title, subtitle = subtitle(values)) {
        AggregationBar(
            aggregation = aggregation,
            onAggregationChange = { aggregation = it; page = 0 },
            page = page,
            onPageChange = { page = it.coerceAtLeast(0) },
            periodLabel = window.label
        )
        content(values)
    }
}
