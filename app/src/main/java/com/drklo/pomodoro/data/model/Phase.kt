package com.drklo.pomodoro.data.model

/** The kind of interval the timer is currently counting. */
enum class Phase {
    POMODORO,
    SHORT_BREAK,
    LONG_BREAK;

    val isBreak: Boolean get() = this == SHORT_BREAK || this == LONG_BREAK
}
