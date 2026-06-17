package com.drklo.pomodoro.data.model

/** Run state of the single global timer. */
enum class TimerStatus {
    /** No active interval; the dial shows the configured duration of the selected phase. */
    IDLE,
    RUNNING,
    PAUSED
}
