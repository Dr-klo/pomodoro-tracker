package com.drklo.pomodoro.ui.reports

import com.drklo.pomodoro.data.model.PomodoroLog
import com.drklo.pomodoro.data.model.Project
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Time granularity for the period-based charts; [bucketCount] = how many buckets are shown. */
enum class Aggregation(val bucketCount: Int) {
    DAY(7),
    WEEK(8),
    MONTH(6)
}

/** One X-axis bucket (a day, an ISO week, or a calendar month). */
data class DateBucket(val label: String, val start: LocalDate, val endInclusive: LocalDate) {
    fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(endInclusive)
}

data class BarSegment(val colorArgb: Int, val value: Float)
data class BarColumn(val label: String, val segments: List<BarSegment>, val total: Float)

private val dayMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM")

/**
 * Builds the visible buckets for an aggregation and a page (0 = latest window, higher = older).
 * Windows are non-overlapping so paging moves by a whole screen.
 */
fun bucketsFor(agg: Aggregation, today: LocalDate, page: Int, locale: Locale): List<DateBucket> =
    when (agg) {
        Aggregation.DAY -> {
            val end = today.minusDays(7L * page)
            (6 downTo 0).map { offset ->
                val d = end.minusDays(offset.toLong())
                DateBucket(d.dayOfWeek.getDisplayName(TextStyle.SHORT, locale), d, d)
            }
        }
        Aggregation.WEEK -> {
            val weekStart = today.minusDays((today.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())
            (7 downTo 0).map { offset ->
                val ws = weekStart.minusWeeks(offset.toLong() + 8L * page)
                DateBucket(ws.format(dayMonth), ws, ws.plusDays(6))
            }
        }
        Aggregation.MONTH -> {
            val monthStart = today.withDayOfMonth(1)
            (5 downTo 0).map { offset ->
                val ms = monthStart.minusMonths(offset.toLong() + 6L * page)
                DateBucket(
                    ms.month.getDisplayName(TextStyle.SHORT, locale),
                    ms,
                    ms.plusMonths(1).minusDays(1)
                )
            }
        }
    }

fun periodLabel(buckets: List<DateBucket>): String {
    if (buckets.isEmpty()) return ""
    return "${buckets.first().start.format(dayMonth)} – ${buckets.last().endInclusive.format(dayMonth)}"
}

/**
 * Aggregates [logs] into stacked bars over [buckets], one segment per project (in [projects] order),
 * using [valueOf] per log (e.g. focus seconds or a constant 1 for counts).
 */
fun buildStackedBars(
    logs: List<PomodoroLog>,
    buckets: List<DateBucket>,
    projects: List<Project>,
    valueOf: (PomodoroLog) -> Float
): List<BarColumn> {
    // bucketIndex -> projectId -> accumulated value
    val sums = Array(buckets.size) { HashMap<Long, Float>() }
    for (log in logs) {
        val date = runCatching { LocalDate.parse(log.dayKey) }.getOrNull() ?: continue
        val idx = buckets.indexOfFirst { it.contains(date) }
        if (idx < 0) continue
        sums[idx][log.projectId] = (sums[idx][log.projectId] ?: 0f) + valueOf(log)
    }
    return buckets.mapIndexed { i, bucket ->
        val perProject = sums[i]
        val segments = projects
            .filter { (perProject[it.id] ?: 0f) > 0f }
            .map { BarSegment(it.pomodoroColor, perProject[it.id] ?: 0f) }
        BarColumn(bucket.label, segments, perProject.values.sum())
    }
}
