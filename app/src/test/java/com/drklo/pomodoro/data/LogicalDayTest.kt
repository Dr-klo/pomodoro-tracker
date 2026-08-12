package com.drklo.pomodoro.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The end-of-day boundary decides which day a late-night pomodoro belongs to. The owner runs with
 * a 01:00 boundary, so "finished at 00:30" must still count towards yesterday.
 */
class LogicalDayTest {

    private fun at(hour: Int, minute: Int): LocalDateTime =
        LocalDateTime.of(2026, 5, 13, hour, minute)

    @Test
    fun `just before the boundary still belongs to the previous day`() {
        assertEquals(
            LocalDate.of(2026, 5, 12),
            LogicalDay.dateFor(at(0, 59), dayEndHour = 1, dayEndMinute = 0)
        )
    }

    @Test
    fun `the boundary itself starts the new day`() {
        assertEquals(
            LocalDate.of(2026, 5, 13),
            LogicalDay.dateFor(at(1, 0), dayEndHour = 1, dayEndMinute = 0)
        )
    }

    @Test
    fun `a midnight boundary is just the calendar date`() {
        assertEquals(
            LocalDate.of(2026, 5, 13),
            LogicalDay.dateFor(at(0, 0), dayEndHour = 0, dayEndMinute = 0)
        )
        assertEquals(
            LocalDate.of(2026, 5, 13),
            LogicalDay.dateFor(at(23, 59), dayEndHour = 0, dayEndMinute = 0)
        )
    }

    @Test
    fun `minutes of the boundary are honored`() {
        val settings = 2 to 30
        assertEquals(
            LocalDate.of(2026, 5, 12),
            LogicalDay.dateFor(at(2, 29), settings.first, settings.second)
        )
        assertEquals(
            LocalDate.of(2026, 5, 13),
            LogicalDay.dateFor(at(2, 30), settings.first, settings.second)
        )
    }

    @Test
    fun `an out-of-range boundary is clamped instead of throwing`() {
        // 25:99 clamps to 23:59, so everything up to the last minute of the day is still yesterday.
        assertEquals(
            LocalDate.of(2026, 5, 12),
            LogicalDay.dateFor(at(23, 58), dayEndHour = 25, dayEndMinute = 99)
        )
        assertEquals(
            LocalDate.of(2026, 5, 13),
            LogicalDay.dateFor(at(23, 59), dayEndHour = 25, dayEndMinute = 99)
        )
    }

    @Test
    fun `the key is the ISO date, which is what the log stores`() {
        assertEquals("2026-05-12", LogicalDay.keyFor(at(0, 30), dayEndHour = 1, dayEndMinute = 0))
    }
}
