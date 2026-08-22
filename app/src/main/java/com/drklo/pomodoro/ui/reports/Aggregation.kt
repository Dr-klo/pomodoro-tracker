package com.drklo.pomodoro.ui.reports

import com.drklo.pomodoro.data.model.PomodoroLog
import com.drklo.pomodoro.data.model.Project
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
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

/**
 * The earliest logical day with a recorded pomodoro, or null when nothing has been logged. Bounds
 * how far back the report navigation may page: unbounded, "‹" walks forever through empty periods
 * with nothing to say that the history ended.
 */
fun earliestLogDate(logs: List<PomodoroLog>): LocalDate? =
    logs.minOfOrNull { it.dayKey }?.let { key -> runCatching { LocalDate.parse(key) }.getOrNull() }

/**
 * How many days of [start]..[endInclusive] have actually happened by [today]. A period that has not
 * started yet still counts as one day, so callers never divide by zero or build an empty range.
 */
fun elapsedDaysIn(start: LocalDate, endInclusive: LocalDate, today: LocalDate): Long {
    val last = if (today.isBefore(endInclusive)) today else endInclusive
    return (ChronoUnit.DAYS.between(start, last) + 1).coerceAtLeast(1)
}

/**
 * Sums [valueOf] over the logs falling between [start] and [endInclusive].
 *
 * This is what makes "vs the previous period" honest. The current period is almost always
 * unfinished, so measuring it against a whole previous one reports a collapse every Monday morning:
 * one day against seven. Callers cut the previous period to the stretch that has actually elapsed
 * in the current one — first N days against first N days — and the number reads as "ahead of" or
 * "behind my usual" instead.
 */
fun sumInRange(
    logs: List<PomodoroLog>,
    start: LocalDate,
    endInclusive: LocalDate,
    valueOf: (PomodoroLog) -> Float
): Float {
    var total = 0f
    for (log in logs) {
        val date = runCatching { LocalDate.parse(log.dayKey) }.getOrNull() ?: continue
        if (!date.isBefore(start) && !date.isAfter(endInclusive)) total += valueOf(log)
    }
    return total
}

/** The two numbers every report element reports: time spent and pomodoros closed. */
data class Totals(val focusSec: Int = 0, val pomodoros: Int = 0)

/** One logical day's totals, plus the same split per project. */
data class DayMetrics(val total: Totals, val byProject: Map<Long, Totals>)

/**
 * Indexes [logs] by [PomodoroLog.dayKey] in a single pass, so a calendar can read a day's totals,
 * its per-project split and its goal progress without re-scanning the log per project.
 */
fun metricsByDay(logs: List<PomodoroLog>): Map<String, DayMetrics> {
    val days = HashMap<String, Pair<Metrics, HashMap<Long, Metrics>>>()
    for (log in logs) {
        val (total, byProject) = days.getOrPut(log.dayKey) { Metrics() to HashMap() }
        total.add(log)
        byProject.getOrPut(log.projectId) { Metrics() }.add(log)
    }
    return days.mapValues { (_, day) ->
        val (total, byProject) = day
        DayMetrics(total.totals(), byProject.mapValues { (_, m) -> m.totals() })
    }
}

/**
 * Aggregates [logs] into stacked bars over [buckets], one segment per project (in [projects] order),
 * using [valueOf] per log (e.g. focus seconds or a constant 1 for counts). Alongside the drawn
 * value every segment keeps its focus seconds and pomodoro count, so a tapped bar can be explained.
 */
fun buildStackedBars(
    logs: List<PomodoroLog>,
    buckets: List<DateBucket>,
    projects: List<Project>,
    locale: Locale,
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
            rangeLabel = rangeLabelOf(bucket, locale),
            segments = segments,
            total = perProject.values.sumOf { it.value.toDouble() }.toFloat(),
            focusSec = perProject.values.sumOf { it.focusSec },
            pomodoros = perProject.values.sumOf { it.pomodoros }
        )
    }
}

/**
 * A self-describing label for a bucket: "Mon, 12.05" for a day, "May 2026" for a whole calendar
 * month (where a date range would say less than the axis label already does), "12.05 – 18.05"
 * otherwise.
 */
private fun rangeLabelOf(bucket: DateBucket, locale: Locale): String = when {
    bucket.start == bucket.endInclusive ->
        "${bucket.label}, ${bucket.start.format(dayMonth)}"
    bucket.start.dayOfMonth == 1 && bucket.endInclusive == bucket.start.plusMonths(1).minusDays(1) ->
        bucket.start.format(monthYear.withLocale(locale))
    else ->
        "${bucket.start.format(dayMonth)} – ${bucket.endInclusive.format(dayMonth)}"
}

/** Mutable accumulator for the three numbers every chart element carries. */
private class Metrics {
    var value = 0f
    var focusSec = 0
    var pomodoros = 0

    /** [drawnValue] is what the chart plots; the totals are collected the same way regardless. */
    fun add(log: PomodoroLog, drawnValue: Float = 0f) {
        value += drawnValue
        focusSec += log.durationSeconds
        pomodoros += 1
    }

    fun totals() = Totals(focusSec, pomodoros)
}
