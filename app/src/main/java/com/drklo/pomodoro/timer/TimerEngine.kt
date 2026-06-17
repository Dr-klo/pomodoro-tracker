package com.drklo.pomodoro.timer

import android.os.SystemClock
import com.drklo.pomodoro.data.LogicalDay
import com.drklo.pomodoro.data.model.GlobalSettings
import com.drklo.pomodoro.data.model.Phase
import com.drklo.pomodoro.data.model.Project
import com.drklo.pomodoro.data.model.TimerStatus
import com.drklo.pomodoro.data.repository.SettingsRepository
import com.drklo.pomodoro.data.repository.StatsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.ceil

/**
 * The single global pomodoro timer (PRD F-003). Owns all timing/phase logic; the UI and the
 * foreground service merely observe [state] and forward user actions.
 */
class TimerEngine(
    private val settingsRepository: SettingsRepository,
    private val statsRepository: StatsRepository,
    private val effects: TimerEffects,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val _state = MutableStateFlow(TimerState())
    val state: StateFlow<TimerState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<TimerEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<TimerEvent> = _events.asSharedFlow()

    @Volatile
    private var settings: GlobalSettings = GlobalSettings()

    private var tickJob: Job? = null
    private var idleJob: Job? = null

    /** Wall-clock (elapsedRealtime) deadline at which the running phase reaches zero. */
    private var deadlineElapsed: Long = 0L

    init {
        scope.launch { settingsRepository.settings.collect { settings = it } }
    }

    // --- User actions ---------------------------------------------------------------------

    /** Selects the active project and resets the session. Allowed only while IDLE (carousel F-008). */
    fun setActiveProject(project: Project) {
        if (_state.value.status != TimerStatus.IDLE) return
        stopIdleAlert()
        val total = project.durationSecondsFor(Phase.POMODORO)
        _state.value = TimerState(
            status = TimerStatus.IDLE,
            phase = Phase.POMODORO,
            project = project,
            totalSeconds = total,
            remainingSeconds = total
        )
        refreshCompletedToday(project)
    }

    fun togglePlayPause() {
        when (_state.value.status) {
            TimerStatus.IDLE -> start(userInitiated = true)
            TimerStatus.RUNNING -> pause()
            TimerStatus.PAUSED -> resume()
        }
    }

    /** Resets the current interval back to its full duration (F-020). Keeps session progress. */
    fun reset() {
        val s = _state.value
        val project = s.project ?: return
        cancelTick()
        stopIdleAlert()
        val total = project.durationSecondsFor(s.phase)
        _state.update {
            it.copy(
                status = TimerStatus.IDLE,
                totalSeconds = total,
                remainingSeconds = total,
                awaitingNext = false,
                idleAlertActive = false
            )
        }
    }

    /** Manually changes the phase type (F-021). Allowed while IDLE or PAUSED. */
    fun setPhase(phase: Phase) {
        val s = _state.value
        if (s.status == TimerStatus.RUNNING) return
        val project = s.project ?: return
        cancelTick()
        stopIdleAlert()
        val total = project.durationSecondsFor(phase)
        _state.update {
            it.copy(
                status = TimerStatus.IDLE,
                phase = phase,
                totalSeconds = total,
                remainingSeconds = total,
                awaitingNext = false,
                idleAlertActive = false
            )
        }
    }

    // --- Internal transitions -------------------------------------------------------------

    private fun start(userInitiated: Boolean) {
        val s = _state.value
        if (s.project == null || s.remainingSeconds <= 0) return
        stopIdleAlert()
        deadlineElapsed = SystemClock.elapsedRealtime() + s.remainingSeconds * 1000L
        _state.update { it.copy(status = TimerStatus.RUNNING, awaitingNext = false, idleAlertActive = false) }
        if (userInitiated && settings.soundEnabled) effects.playStart()
        if (userInitiated && settings.vibrateEnabled) effects.vibrate()
        _events.tryEmit(TimerEvent.PhaseStarted(s.phase))
        startTicking()
    }

    private fun pause() {
        cancelTick()
        val remaining = remainingFromDeadline()
        _state.update { it.copy(status = TimerStatus.PAUSED, remainingSeconds = remaining) }
    }

    private fun resume() {
        val s = _state.value
        deadlineElapsed = SystemClock.elapsedRealtime() + s.remainingSeconds * 1000L
        _state.update { it.copy(status = TimerStatus.RUNNING) }
        startTicking()
    }

    private fun onPhaseComplete() {
        cancelTick()
        val s = _state.value
        val project = s.project ?: return
        val finishedPhase = s.phase

        if (settings.soundEnabled) effects.playEnd()
        if (settings.vibrateEnabled) effects.vibrate()
        _events.tryEmit(TimerEvent.PhaseFinished(finishedPhase))

        val next: TimerState = if (finishedPhase == Phase.POMODORO) {
            persistCompletedPomodoro(project)
            val newToday = s.completedToday + 1
            val perSession = project.pomodorosPerSession.coerceAtLeast(1)
            val newSession = (s.completedInSession % perSession) + 1
            val sinceLong = s.pomodorosSinceLongBreak + 1

            if (project.dailyGoal in 1..newToday && s.completedToday < project.dailyGoal) {
                _events.tryEmit(TimerEvent.GoalReached(project.id))
            }

            val takeLong = project.longBreakEnabled && sinceLong >= project.longBreakInterval
            val nextPhase = if (takeLong) Phase.LONG_BREAK else Phase.SHORT_BREAK
            val total = project.durationSecondsFor(nextPhase)
            s.copy(
                status = TimerStatus.IDLE,
                phase = nextPhase,
                totalSeconds = total,
                remainingSeconds = total,
                completedInSession = newSession,
                completedToday = newToday,
                pomodorosSinceLongBreak = if (takeLong) 0 else sinceLong,
                awaitingNext = true,
                idleAlertActive = false
            )
        } else {
            val total = project.durationSecondsFor(Phase.POMODORO)
            s.copy(
                status = TimerStatus.IDLE,
                phase = Phase.POMODORO,
                totalSeconds = total,
                remainingSeconds = total,
                awaitingNext = true,
                idleAlertActive = false
            )
        }

        _state.value = next

        if (settings.autostart) {
            start(userInitiated = false)
        } else {
            startIdleAlert()
        }
    }

    // --- Ticking & idle alert -------------------------------------------------------------

    private fun startTicking() {
        cancelTick()
        tickJob = scope.launch {
            while (true) {
                val remaining = remainingFromDeadline()
                _state.update { it.copy(remainingSeconds = remaining) }
                if (remaining <= 0) {
                    onPhaseComplete()
                    break
                }
                delay(200)
            }
        }
    }

    private fun cancelTick() {
        tickJob?.cancel()
        tickJob = null
    }

    private fun remainingFromDeadline(): Int {
        val remainingMs = deadlineElapsed - SystemClock.elapsedRealtime()
        return ceil(remainingMs / 1000.0).toInt().coerceAtLeast(0)
    }

    private fun startIdleAlert() {
        stopIdleAlert()
        val minutes = settings.idleAlertMinutes
        if (minutes <= 0) return
        idleJob = scope.launch {
            while (_state.value.awaitingNext) {
                delay(minutes * 60_000L)
                if (!_state.value.awaitingNext) break
                _state.update { it.copy(idleAlertActive = !it.idleAlertActive) }
            }
        }
    }

    private fun stopIdleAlert() {
        idleJob?.cancel()
        idleJob = null
    }

    // --- Stats ----------------------------------------------------------------------------

    private fun currentDayKey(): String =
        LogicalDay.keyFor(dayEndHour = settings.dayEndHour, dayEndMinute = settings.dayEndMinute)

    private fun persistCompletedPomodoro(project: Project) {
        val dayKey = currentDayKey()
        scope.launch { statsRepository.recordCompletedPomodoro(project.id, dayKey) }
    }

    private fun refreshCompletedToday(project: Project) {
        scope.launch {
            val today = statsRepository.completedCount(project.id, currentDayKey())
            _state.update { if (it.project?.id == project.id) it.copy(completedToday = today) else it }
        }
    }
}
