package com.drklo.pomodoro.ui.reports

/** Formats focus time as "2h 15m" / "15m" using the given localized unit labels. */
fun formatFocus(totalSeconds: Int, hoursLabel: String, minutesLabel: String): String {
    val totalMinutes = totalSeconds / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "$hours$hoursLabel $minutes$minutesLabel" else "$minutes$minutesLabel"
}
