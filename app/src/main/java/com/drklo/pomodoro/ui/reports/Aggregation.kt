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

/** One project's slice of a bar. [value] drives the drawn height; the rest feeds the tooltip. */
data class BarSegment(
    val projectId: Long,
    val name: String,
    val colorArgb: Int,
    val value: Float,
    val focusSec: Int,
    val pomodoros: Int
)

data class BarColumn(
    val label: String,
    val rangeLabel: String,
    val segments: List<BarSegment>,
    val total: Float,
    val focusSec: Int,
    val pomodoros: Int
)

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

private val monthYear: DateTimeFormatter = DateTimeFormatter.ofPattern("LLLL yyyy")

/** A single aggregation window (one day/week/month) for snapshot charts; page 0 = current. */
fun windowFor(agg: Aggregation, today: LocalDate, page: Int, locale: Locale): DateBucket =
    when (agg) {
        Aggregation.DAY -> {
            val d = today.minusDays(page.toLong())
            DateBucket(d.format(dayMonth), d, d)
        }
        Aggregation.WEEK -> {
            val weekStart = today
                .minusDays((today.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())
                .minusWeeks(page.toLong())
            val end = weekStart.plusDays(6)
            DateBucket("${weekStart.format(dayMonth)} – ${end.format(dayMonth)}", weekStart, end)
        }
        Aggregation.MONTH -> {
            val ms = today.withDayOfMonth(1).minusMonths(page.toLong())
            DateBucket(ms.format(monthYear.withLocale(locale)), ms, ms.plusMonths(1).minusDays(1))
        }
    }

/** Per-project aggregated value within a window, sorted descending and filtered to > 0. */
data class ProjectValue(
    val projectId: Long,
    val name: String,
    val colorArgb: Int,
    val value: Float,
    val focusSec: Int,
    val pomodoros: Int
)

fun sumByProject(
    logs: List<PomodoroLog>,
    window: DateBucket,
    projects: List<Project>,
    valueOf: (PomodoroLog) -> Float
): List<ProjectValue> {
    val sums = HashMap<Long, Metrics>()
    for (log in logs) {
        val date = runCatching { LocalDate.parse(log.dayKey) }.getOrNull() ?: continue
        if (window.contains(date)) sums.getOrPut(log.projectId) { Metrics() }.add(log, valueOf(log))
    }
    return projects
        .mapNotNull { p ->
            val m = sums[p.id] ?: return@mapNotNull null
            if (m.value > 0f) {
                ProjectValue(p.id, p.name, p.pomodoroColor, m.value, m.focusSec, m.pomodoros)
            } else {
                null
            }
        }
        .sortedByDescending { it.value }
}

/** Focus seconds and pomodoro count for a single logical day, keyed by [PomodoroLog.dayKey]. */
data class DayMetrics(val focusSec: Int, val pomodoros: Int)

fun metricsByDay(logs: List<PomodoroLog>): Map<String, DayMetrics> =
    logs.groupBy { it.dayKey }
        .mapValues { (_, l) -> DayMetrics(l.sumOf { it.durationSeconds }, l.size) }

/**
 * Aggregates [logs] into stacked bars over [buckets], one segment per project (in [projects] order),
 * using [valueOf] per log (e.g. focus seconds or a constant 1 for counts). Alongside the drawn
 * value every segment keeps its focus seconds and pomodoro count, so a tapped bar can be explained.
 */
fun buildStackedBars(
    logs: List<PomodoroLog>,
    buckets: List<DateBucket>,
    projects: List<Project>,
    valueOf: (PomodoroLog) -> Float
): List<BarColumn> {
    // bucketIndex -> projectId -> accumulated metrics
    val sums = Array(buckets.size) { HashMap<Long, Metrics>() }
    for (log in logs) {
        val date = runCatching { LocalDate.parse(log.dayKey) }.getOrNull() ?: continue
        val idx = buckets.indexOfFirst { it.contains(date) }
        if (idx < 0) continue
        sums[idx].getOrPut(log.projectId) { Metrics() }.add(log, valueOf(log))
    }
    return buckets.mapIndexed { i, bucket ->
        val perProject = sums[i]
        val segments = projects.mapNotNull { p ->
            val m = perProject[p.id] ?: return@mapNotNull null
            if (m.value <= 0f) null
            else BarSegment(p.id, p.name, p.pomodoroColor, m.value, m.focusSec, m.pomodoros)
        }
        BarColumn(
            label = bucket.label,
            rangeLabel = rangeLabelOf(bucket),
            segments = segments,
            total = perProject.values.sumOf { it.value.toDouble() }.toFloat(),
            focusSec = perProject.values.sumOf { it.focusSec },
            pomodoros = perProject.values.sumOf { it.pomodoros }
        )
    }
}

/** A self-describing label for a bucket, e.g. "Mon, 12.05" or "12.05 – 18.05". */
private fun rangeLabelOf(bucket: DateBucket): String =
    if (bucket.start == bucket.endInclusive) "${bucket.label}, ${bucket.start.format(dayMonth)}"
    else "${bucket.start.format(dayMonth)} – ${bucket.endInclusive.format(dayMonth)}"

/** Mutable accumulator for the three numbers every chart element carries. */
private class Metrics {
    var value = 0f
    var focusSec = 0
    var pomodoros = 0

    fun add(log: PomodoroLog, drawnValue: Float) {
        value += drawnValue
        focusSec += log.durationSeconds
        pomodoros += 1
    }
}
