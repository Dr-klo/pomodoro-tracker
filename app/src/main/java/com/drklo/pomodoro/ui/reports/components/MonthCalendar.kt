package com.drklo.pomodoro.ui.reports.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drklo.pomodoro.R
import com.drklo.pomodoro.data.model.PomodoroLog
import com.drklo.pomodoro.data.model.Project
import com.drklo.pomodoro.ui.reports.dayTitle
import com.drklo.pomodoro.ui.reports.metricsByDay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Month calendar (B2) where each date is wrapped in a mini donut ring showing how much of that
 * day's per-project daily goals were closed. Navigable by month.
 */
@Composable
fun MonthCalendar(
    logs: List<PomodoroLog>,
    projects: List<Project>,
    today: LocalDate,
    modifier: Modifier = Modifier
) {
    val locale = Locale.getDefault()
    var page by remember { mutableIntStateOf(0) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    val month = remember(today, page) { YearMonth.from(today).minusMonths(page.toLong()) }

    // dayKey -> that day's totals and per-project split, indexed once per log change.
    val dayMetrics = remember(logs) { metricsByDay(logs) }
    val goalProjects = remember(projects) { projects.filter { it.dailyGoal > 0 } }
    // The whole ring is the day's combined goal, so each project's arc is weighted by its own goal.
    val totalGoal = remember(goalProjects) { goalProjects.sumOf { it.dailyGoal } }

    val monthLabel = remember(month, locale) {
        month.format(DateTimeFormatter.ofPattern("LLLL yyyy").withLocale(locale))
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Month navigation.
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { page += 1; selectedDate = null }) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = stringResource(R.string.cd_prev))
            }
            Text(monthLabel, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            IconButton(
                onClick = { page = (page - 1).coerceAtLeast(0); selectedDate = null },
                enabled = page > 0
            ) {
                Icon(Icons.Filled.ChevronRight, contentDescription = stringResource(R.string.cd_next))
            }
        }

        // Weekday headers (Mon..Sun).
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            for (d in DayOfWeek.entries) {
                Text(
                    text = d.getDisplayName(TextStyle.NARROW, locale),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Build the grid: leading blanks then the month's days, padded to full weeks.
        val first = month.atDay(1)
        val leading = first.dayOfWeek.value - DayOfWeek.MONDAY.value
        val daysInMonth = month.lengthOfMonth()
        val cells = buildList {
            repeat(leading) { add(null) }
            for (day in 1..daysInMonth) add(month.atDay(day))
            while (size % 7 != 0) add(null)
        }

        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f).padding(3.dp)) {
                        if (date != null) {
                            val dayCounts = dayMetrics[date.toString()]?.byProject.orEmpty()
                            val arcs = goalProjects.map { p ->
                                val done = dayCounts[p.id]?.pomodoros ?: 0
                                GoalArc(
                                    colorArgb = p.pomodoroColor,
                                    share = p.dailyGoal.toFloat() / totalGoal,
                                    fill = (done.toFloat() / p.dailyGoal).coerceIn(0f, 1f)
                                )
                            }
                            DayCell(
                                day = date.dayOfMonth,
                                arcs = arcs,
                                isToday = date == today,
                                isSelected = date == selectedDate,
                                onClick = {
                                    selectedDate = if (date == selectedDate) null else date
                                }
                            )
                        }
                    }
                }
            }
        }

        val selectedMetrics = selectedDate?.let { dayMetrics[it.toString()] }
        ChartTooltip(
            title = selectedDate?.format(dayTitle.withLocale(locale)),
            focusSec = selectedMetrics?.total?.focusSec ?: 0,
            pomodoros = selectedMetrics?.total?.pomodoros ?: 0,
            hint = stringResource(R.string.chart_tap_hint_day),
            // Only projects that ran that day, so the rows add up to the headline above them.
            rows = projects.mapNotNull { p ->
                val totals = selectedMetrics?.byProject?.get(p.id) ?: return@mapNotNull null
                TooltipRow(
                    name = p.name,
                    colorArgb = p.pomodoroColor,
                    focusSec = totals.focusSec,
                    pomodoros = totals.pomodoros,
                    note = if (p.dailyGoal > 0) {
                        stringResource(R.string.chart_goal_progress, totals.pomodoros, p.dailyGoal)
                    } else {
                        null
                    }
                )
            }
        )
    }
}

/**
 * One project's arc in a day ring: it owns [share] of the circle (its daily goal's weight in the
 * day's combined goal) and that arc is [fill]ed by how much of the goal was closed. Because the
 * two multiply, the drawn length is proportional to pomodoros actually completed — a goal of 8
 * closed in full draws twice the arc of a goal of 4 closed in full.
 */
private data class GoalArc(val colorArgb: Int, val share: Float, val fill: Float)

/** Degrees of blank kept between neighbouring project arcs so they stay distinguishable. */
private const val GAP = 3f

@Composable
private fun DayCell(
    day: Int,
    arcs: List<GoalArc>,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val textColor =
        if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val highlightColor = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(CircleShape)
            .background(if (isSelected) highlightColor else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.11f
            val inset = stroke / 2f + 1f
            val arcSize = Size(size.width - 2 * inset, size.height - 2 * inset)
            val topLeft = Offset(inset, inset)

            drawArc(
                color = trackColor,
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(width = stroke)
            )
            var wedgeStart = -90f
            arcs.forEach { arc ->
                val wedge = 360f * arc.share
                if (arc.fill > 0f) {
                    drawArc(
                        color = Color(arc.colorArgb),
                        startAngle = wedgeStart + GAP / 2f,
                        sweepAngle = (wedge - GAP).coerceAtLeast(0f) * arc.fill,
                        useCenter = false,
                        topLeft = topLeft, size = arcSize,
                        style = Stroke(width = stroke)
                    )
                }
                wedgeStart += wedge
            }
        }
        Text(
            text = day.toString(),
            fontSize = 11.sp,
            color = textColor,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
        )
    }
}
