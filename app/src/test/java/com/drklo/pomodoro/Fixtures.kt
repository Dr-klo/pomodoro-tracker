package com.drklo.pomodoro

import com.drklo.pomodoro.data.model.PomodoroLog
import com.drklo.pomodoro.data.model.Project

/**
 * Minimal builders so a test states only the fields it actually reasons about. Everything else
 * gets a neutral default — a test that mentions a number is a test that depends on it.
 */
@Suppress("LongParameterList")
fun project(
    id: Long,
    name: String = "P$id",
    color: Int = 0xFF000000.toInt() + id.toInt(),
    dailyGoal: Int = 0,
    orderIndex: Int = id.toInt(),
    focusMinutes: Int = 25,
    shortBreakMinutes: Int = 5,
    pomodorosPerSession: Int = 4,
    longBreakEnabled: Boolean = false,
    longBreakMinutes: Int = 15,
    longBreakInterval: Int = 4
) = Project(
    id = id,
    name = name,
    focusMinutes = focusMinutes,
    shortBreakMinutes = shortBreakMinutes,
    pomodorosPerSession = pomodorosPerSession,
    pomodoroColor = color,
    breakColor = color,
    dailyGoal = dailyGoal,
    longBreakEnabled = longBreakEnabled,
    longBreakMinutes = longBreakMinutes,
    longBreakInterval = longBreakInterval,
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
