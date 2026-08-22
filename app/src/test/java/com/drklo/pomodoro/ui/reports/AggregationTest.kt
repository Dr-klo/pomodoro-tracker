package com.drklo.pomodoro.ui.reports

import com.drklo.pomodoro.data.model.PomodoroLog
import com.drklo.pomodoro.log
import com.drklo.pomodoro.project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

/**
 * Summation and labelling: which logs land in which bucket, and what a tapped element reports.
 * The two metrics (focus seconds, pomodoro count) must survive independently of the plotted
 * value — a count chart plots 1 per log but still has to report the real time spent.
 */
class AggregationTest {

    private val today: LocalDate = LocalDate.of(2026, 5, 13)
    private val locale: Locale = Locale.US
    private val work = project(id = 1, name = "Work")
    private val study = project(id = 2, name = "Study")
    private val projects = listOf(work, study)

    private val focusSeconds: (PomodoroLog) -> Float = { it.durationSeconds.toFloat() }
    private val one: (PomodoroLog) -> Float = { 1f }

    private fun bars(logs: List<PomodoroLog>, valueOf: (PomodoroLog) -> Float = focusSeconds) =
        buildStackedBars(
            logs = logs,
            buckets = bucketsFor(Aggregation.DAY, today, page = 0, locale = locale),
            projects = projects,
            locale = locale,
            valueOf = valueOf
        )

    @Test
    fun `logs are summed per project inside their own day`() {
        val columns = bars(
            listOf(
                log(work.id, "2026-05-13", durationSeconds = 1500),
                log(work.id, "2026-05-13", durationSeconds = 900),
                log(study.id, "2026-05-12", durationSeconds = 600)
            )
        )

        val lastDay = columns.last()
        assertEquals(2400f, lastDay.total, 0f)
        assertEquals(1, lastDay.segments.size)
        assertEquals(work.id, lastDay.segments.single().projectId)
        assertEquals(600, columns[columns.size - 2].focusSec)
    }

    @Test
    fun `metrics are carried even when the plotted value is a count`() {
        val column = bars(
            listOf(
                log(work.id, "2026-05-13", durationSeconds = 1500),
                log(work.id, "2026-05-13", durationSeconds = 900)
            ),
            valueOf = one
        ).last()

        assertEquals(2f, column.total, 0f)
        assertEquals(2400, column.focusSec)
        assertEquals(2, column.pomodoros)
        assertEquals(2400, column.segments.single().focusSec)
    }

    @Test
    fun `logs outside the visible window are ignored`() {
        val columns = bars(listOf(log(work.id, "2026-04-01")))

        assertTrue(columns.all { it.segments.isEmpty() })
        assertTrue(columns.all { it.total == 0f })
    }

    @Test
    fun `an unparseable day key is skipped instead of crashing`() {
        val columns = bars(listOf(log(work.id, "not-a-date"), log(work.id, "2026-05-13")))

        assertEquals(1, columns.last().pomodoros)
    }

    @Test
    fun `segments follow the project order, not the log order`() {
        val columns = bars(
            listOf(
                log(study.id, "2026-05-13", durationSeconds = 3000),
                log(work.id, "2026-05-13", durationSeconds = 300)
            )
        )

        assertEquals(listOf(work.id, study.id), columns.last().segments.map { it.projectId })
    }

    @Test
    fun `a whole calendar month is labelled by its name, not by a date range`() {
        val columns = buildStackedBars(
            logs = emptyList(),
            buckets = bucketsFor(Aggregation.MONTH, today, page = 0, locale = locale),
            projects = projects,
            locale = locale,
            valueOf = focusSeconds
        )

        val label = columns.last().rangeLabel
        assertFalse("month bucket must not fall back to a date range: $label", label.contains("–"))
        assertTrue(label.contains("2026"))
    }

    @Test
    fun `a single day is labelled by weekday and date, a week by its range`() {
        assertTrue(bars(emptyList()).last().rangeLabel.endsWith(", 13.05"))

        val week = buildStackedBars(
            logs = emptyList(),
            buckets = bucketsFor(Aggregation.WEEK, today, page = 0, locale = locale),
            projects = projects,
            locale = locale,
            valueOf = focusSeconds
        )
        assertTrue(week.last().rangeLabel.contains("–"))
    }

    @Test
    fun `window totals are sorted by value and skip empty projects`() {
        val values = sumByProject(
            logs = listOf(
                log(work.id, "2026-05-13", durationSeconds = 600),
                log(study.id, "2026-05-13", durationSeconds = 1500)
            ),
            window = windowFor(Aggregation.DAY, today, page = 0, locale = locale),
            projects = projects,
            valueOf = focusSeconds
        )

        assertEquals(listOf(study.id, work.id), values.map { it.projectId })
        assertEquals(1500f, values.first().value, 0f)
        assertEquals(1, values.first().pomodoros)
    }

    @Test
    fun `a project with no logs in the window is absent`() {
        val values = sumByProject(
            logs = listOf(log(work.id, "2026-05-13")),
            window = windowFor(Aggregation.DAY, today, page = 0, locale = locale),
            projects = projects,
            valueOf = focusSeconds
        )

        assertEquals(listOf(work.id), values.map { it.projectId })
    }

    @Test
    fun `logs of a project that no longer exists are not reported`() {
        val values = sumByProject(
            logs = listOf(log(projectId = 99, dayKey = "2026-05-13")),
            window = windowFor(Aggregation.DAY, today, page = 0, locale = locale),
            projects = projects,
            valueOf = focusSeconds
        )

        assertTrue(values.isEmpty())
    }

    @Test
    fun `the earliest logged day bounds how far back the reports can page`() {
        val logs = listOf(
            log(work.id, "2026-05-13"),
            log(work.id, "2026-03-02"),
            log(study.id, "2026-04-20")
        )

        assertEquals(LocalDate.of(2026, 3, 2), earliestLogDate(logs))
        assertEquals(null, earliestLogDate(emptyList()))
        assertEquals(null, earliestLogDate(listOf(log(work.id, "not-a-date"))))
    }

    @Test
    fun `only the part of a period that has already happened counts as elapsed`() {
        val weekStart = LocalDate.of(2026, 5, 11)
        val weekEnd = weekStart.plusDays(6)

        // Wednesday: three days of this week are in.
        assertEquals(3, elapsedDaysIn(weekStart, weekEnd, LocalDate.of(2026, 5, 13)))
        // A week that is over counts in full, no matter how long ago it ended.
        assertEquals(7, elapsedDaysIn(weekStart, weekEnd, LocalDate.of(2026, 6, 1)))
        // Its first day, and anything before it, is still one day rather than zero or negative.
        assertEquals(1, elapsedDaysIn(weekStart, weekEnd, weekStart))
        assertEquals(1, elapsedDaysIn(weekStart, weekEnd, weekStart.minusDays(5)))
    }

    @Test
    fun `comparing periods uses the same number of days on both sides`() {
        val logs = listOf(
            // This week: Monday and Tuesday.
            log(work.id, "2026-05-11", durationSeconds = 1500),
            log(work.id, "2026-05-12", durationSeconds = 1500),
            // Last week: two quiet early days, then a lot of work later on.
            log(work.id, "2026-05-04", durationSeconds = 900),
            log(work.id, "2026-05-05", durationSeconds = 900),
            log(work.id, "2026-05-08", durationSeconds = 6000),
            log(work.id, "2026-05-10", durationSeconds = 6000)
        )
        val thisWeek = LocalDate.of(2026, 5, 11)
        val lastWeek = thisWeek.minusWeeks(1)
        val elapsed = elapsedDaysIn(thisWeek, thisWeek.plusDays(6), today = LocalDate.of(2026, 5, 12))

        val current = sumInRange(logs, thisWeek, thisWeek.plusDays(elapsed - 1), focusSeconds)
        val previous = sumInRange(logs, lastWeek, lastWeek.plusDays(elapsed - 1), focusSeconds)

        assertEquals(3000f, current, 0f)
        // Against the whole of last week this reads as a collapse; against its first two days it
        // reads as "ahead of my usual", which is what the number is for.
        assertEquals(1800f, previous, 0f)
    }

    @Test
    fun `a range sum ignores logs outside it and unparseable days`() {
        val logs = listOf(
            log(work.id, "2026-05-10"),
            log(work.id, "2026-05-11"),
            log(work.id, "2026-05-20"),
            log(work.id, "broken")
        )

        val sum = sumInRange(logs, LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 11), one)

        assertEquals(2f, sum, 0f)
    }

    @Test
    fun `a day is indexed with its total and its per-project split`() {
        val metrics = metricsByDay(
            listOf(
                log(work.id, "2026-05-13", durationSeconds = 1500),
                log(study.id, "2026-05-13", durationSeconds = 900),
                log(work.id, "2026-05-12", durationSeconds = 600)
            )
        )

        val day = metrics.getValue("2026-05-13")
        assertEquals(2400, day.total.focusSec)
        assertEquals(2, day.total.pomodoros)
        assertEquals(1500, day.byProject.getValue(work.id).focusSec)
        assertEquals(1, day.byProject.getValue(study.id).pomodoros)
        assertEquals(600, metrics.getValue("2026-05-12").total.focusSec)
    }

    @Test
    fun `a day without logs has no entry at all`() {
        val metrics = metricsByDay(listOf(log(work.id, "2026-05-13")))

        assertEquals(null, metrics["2026-05-12"])
        assertTrue(metricsByDay(emptyList()).isEmpty())
    }
}
