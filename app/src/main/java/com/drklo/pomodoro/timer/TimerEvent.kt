package com.drklo.pomodoro.timer

import com.drklo.pomodoro.data.model.Phase

/** One-shot timer events, consumed by the UI (fanfare) and effects. */
sealed interface TimerEvent {
    data class PhaseStarted(val phase: Phase) : TimerEvent
    data class PhaseFinished(val phase: Phase) : TimerEvent

    /** Daily goal for [projectId] was just reached — triggers the fanfare animation (F-014). */
    data class GoalReached(val projectId: Long) : TimerEvent
}
