package com.drklo.pomodoro.timer

import android.os.SystemClock
import java.time.LocalDateTime

/**
 * Every clock the timer reads, in one place. The three are genuinely different clocks and are not
 * interchangeable:
 *
 * - [elapsedRealtimeMs] is monotonic since boot and keeps counting in sleep. The countdown uses it
 *   so that changing the system time (or a timezone jump) cannot stretch or shorten a pomodoro.
 * - [wallClockMs] is what a finished pomodoro is stamped with in the log — a real point in time,
 *   which the monotonic clock is not.
 * - [now] is local civil time, the only clock that can answer which logical day it is.
 *
 * Injecting them is what lets the engine be driven by virtual time in tests instead of by a device.
 */
interface TimeSource {
    fun elapsedRealtimeMs(): Long
    fun wallClockMs(): Long
    fun now(): LocalDateTime
}

/** The production clock: the device's own. */
object SystemTimeSource : TimeSource {
    override fun elapsedRealtimeMs(): Long = SystemClock.elapsedRealtime()
    override fun wallClockMs(): Long = System.currentTimeMillis()
    override fun now(): LocalDateTime = LocalDateTime.now()
}
