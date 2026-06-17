package com.drklo.pomodoro.data.db

import androidx.room.Entity

/**
 * Per-project, per-logical-day count of completed pomodoros. Drives daily goal progress and
 * survives restarts (PRD Open Question #8: only the completed count is restored).
 */
@Entity(tableName = "day_stats", primaryKeys = ["projectId", "dayKey"])
data class DayStatEntity(
    val projectId: Long,
    /** Logical day key, see [com.drklo.pomodoro.data.LogicalDay]. */
    val dayKey: String,
    val completedPomodoros: Int
)
