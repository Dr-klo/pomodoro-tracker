package com.drklo.pomodoro.data.model

/** One completed pomodoro, recorded for statistics (timestamps + duration + logical day). */
data class PomodoroLog(
    val id: Long = 0,
    val projectId: Long,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val durationSeconds: Int,
    val dayKey: String
)
