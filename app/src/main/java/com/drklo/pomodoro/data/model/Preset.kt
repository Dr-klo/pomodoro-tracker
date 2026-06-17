package com.drklo.pomodoro.data.model

/** Built-in quick presets for focus/short-break durations (F-005). Values stay editable. */
enum class Preset(val focusMinutes: Int, val shortBreakMinutes: Int) {
    CLASSIC(25, 5),
    MEDIUM(30, 10),
    LONG(45, 15)
}
