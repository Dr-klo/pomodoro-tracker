package com.drklo.pomodoro.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.drklo.pomodoro.data.model.Project

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val focusMinutes: Int,
    val shortBreakMinutes: Int,
    val pomodorosPerSession: Int,
    val pomodoroColor: Int,
    val breakColor: Int,
    val dailyGoal: Int,
    val longBreakEnabled: Boolean,
    val longBreakMinutes: Int,
    val longBreakInterval: Int,
    val orderIndex: Int
)

fun ProjectEntity.toDomain() = Project(
    id = id,
    name = name,
    focusMinutes = focusMinutes,
    shortBreakMinutes = shortBreakMinutes,
    pomodorosPerSession = pomodorosPerSession,
    pomodoroColor = pomodoroColor,
    breakColor = breakColor,
    dailyGoal = dailyGoal,
    longBreakEnabled = longBreakEnabled,
    longBreakMinutes = longBreakMinutes,
    longBreakInterval = longBreakInterval,
    orderIndex = orderIndex
)

fun Project.toEntity() = ProjectEntity(
    id = id,
    name = name,
    focusMinutes = focusMinutes,
    shortBreakMinutes = shortBreakMinutes,
    pomodorosPerSession = pomodorosPerSession,
    pomodoroColor = pomodoroColor,
    breakColor = breakColor,
    dailyGoal = dailyGoal,
    longBreakEnabled = longBreakEnabled,
    longBreakMinutes = longBreakMinutes,
    longBreakInterval = longBreakInterval,
    orderIndex = orderIndex
)
