package com.drklo.pomodoro.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.drklo.pomodoro.data.model.PomodoroLog

@Entity(tableName = "pomodoro_log")
data class PomodoroLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val durationSeconds: Int,
    val dayKey: String
)

fun PomodoroLogEntity.toDomain() = PomodoroLog(
    id = id,
    projectId = projectId,
    startEpochMs = startEpochMs,
    endEpochMs = endEpochMs,
    durationSeconds = durationSeconds,
    dayKey = dayKey
)
