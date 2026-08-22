package com.drklo.pomodoro.ui.reports

import java.time.format.DateTimeFormatter

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
