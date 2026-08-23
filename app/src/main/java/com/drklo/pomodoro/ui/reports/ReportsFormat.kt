package com.drklo.pomodoro.ui.reports

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Title a tooltip uses for a single day: "Monday, 13.05". Lives here rather than in each chart, so
 * changing the format cannot leave one tooltip on the old one.
 */
val dayTitle: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, dd.MM")

/** Formats focus time as "2h 15m" / "15m" using the given localized unit labels. */
fun formatFocus(totalSeconds: Int, hoursLabel: String, minutesLabel: String): String {
    val totalMinutes = totalSeconds / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "$hours$hoursLabel $minutes$minutesLabel" else "$minutes$minutesLabel"
}

/**
 * Short "day and month" for axis labels and date ranges, in the user's own convention: 12.05 for a
 * Russian reader, 05/12 for an American one. Derived from the locale's short date pattern with the
 * year stripped, rather than hardcoded — the neighbouring month-year formatter already localizes,
 * and a chart that mixes the two conventions reads as a bug.
 */
internal fun dayMonthFormatter(locale: Locale): DateTimeFormatter {
    val full = DateTimeFormatterBuilder.getLocalizedDateTimePattern(
        FormatStyle.SHORT,
        null,
        IsoChronology.INSTANCE,
        locale
    )
    val pattern = full
        .replace(Regex("""[^\p{Alpha}]*[yu]+[^\p{Alpha}]*"""), "")
        .ifBlank { "dd.MM" }
    return DateTimeFormatter.ofPattern(pattern, locale)
}

/**
 * First day of the week for [locale] — Monday in Russia, Sunday in the US. Weekly buckets that
 * always start on Monday would sit a day off from the calendar such a user reads.
 */
fun firstDayOfWeek(locale: Locale): DayOfWeek = WeekFields.of(locale).firstDayOfWeek

/** Days to step back from [date] to reach the start of its week in [locale]. */
fun daysFromWeekStart(date: LocalDate, locale: Locale): Long {
    val first = firstDayOfWeek(locale).value
    return ((date.dayOfWeek.value - first) + DAYS_IN_WEEK) % DAYS_IN_WEEK.toLong()
}

private const val DAYS_IN_WEEK = 7
