package com.drklo.pomodoro.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Maps a wall-clock moment to a logical day key, honoring the user's "end of day" time (F-022).
 *
 * Example: if the day ends at 02:00, a pomodoro completed at 01:00 belongs to the previous
 * calendar day. With the default 00:00, this is just the calendar date.
 *
 * Timezone/DST changes are out of scope (PRD Open Question #9).
 */
object LogicalDay {

    fun keyFor(
        now: LocalDateTime = LocalDateTime.now(),
        dayEndHour: Int,
        dayEndMinute: Int
    ): String = dateFor(now, dayEndHour, dayEndMinute).toString() // ISO-8601 yyyy-MM-dd

    /** The logical date for [now], honoring the end-of-day boundary. */
    fun dateFor(
        now: LocalDateTime = LocalDateTime.now(),
        dayEndHour: Int,
        dayEndMinute: Int
    ): LocalDate {
        val boundary = LocalTime.of(dayEndHour.coerceIn(0, 23), dayEndMinute.coerceIn(0, 59))
        return if (now.toLocalTime().isBefore(boundary)) now.toLocalDate().minusDays(1)
        else now.toLocalDate()
    }
}
