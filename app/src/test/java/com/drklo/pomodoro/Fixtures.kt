package com.drklo.pomodoro

import com.drklo.pomodoro.data.model.PomodoroLog
import com.drklo.pomodoro.data.model.Project

/**
 * Minimal builders so a test states only the fields it actually reasons about. Everything else
 * gets a neutral default — a test that mentions a number is a test that depends on it.
 */
fun project(
    id: Long,
    name: String = "P$id",
    color: Int = 0xFF000000.toInt() + id.toInt(),
    dailyGoal: Int = 0,
    orderIndex: Int = id.toInt()
) = Project(
    id = id,
    name = name,
    focusMinutes = 25,
    shortBreakMinutes = 5,
    pomodorosPerSession = 4,
    pomodoroColor = color,
    breakColor = color,
    dailyGoal = dailyGoal,
    longBreakEnabled = false,
    longBreakMinutes = 15,
    longBreakInterval = 4,
    orderIndex = orderIndex
)

fun log(
    projectId: Long,
    dayKey: String,
    durationSeconds: Int = 1500,
    id: Long = 0
) = PomodoroLog(
    id = id,
    projectId = projectId,
    startEpochMs = 0,
    endEpochMs = durationSeconds * 1000L,
    durationSeconds = durationSeconds,
    dayKey = dayKey
)
