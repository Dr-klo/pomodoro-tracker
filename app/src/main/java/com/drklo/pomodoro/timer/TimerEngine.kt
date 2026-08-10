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

    /** Logical day for which [TimerState.completedToday]/[TimerState.completedInSession] were computed. */
    @Volatile
    private var sessionDayKey: String? = null

    /** Wall-clock (elapsedRealtime) deadline at which the running phase reaches zero. */
    private var deadlineElapsed: Long = 0L

    init {
        scope.launch { settingsRepository.settings.collect { settings = it } }
    }

    // --- User actions ---------------------------------------------------------------------

    /**
     * Selects the active project and resets the session. Allowed while stopped or paused
     * (carousel F-008); switching away from a paused interval discards it.
     */
    fun setActiveProject(project: Project) {
        if (_state.value.status == TimerStatus.RUNNING) return
        cancelTick()
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

    /**
     * Re-applies edited data for the currently active project (e.g. changed durations/colors)
     * without resetting session progress. Adopts a new duration only when idle; a paused interval
     * keeps its remaining time.
     */
    fun refreshActiveProject(updated: Project) {
        val s = _state.value
        if (s.project?.id != updated.id) return
        when (s.status) {
            TimerStatus.RUNNING, TimerStatus.PAUSED ->
                _state.update { it.copy(project = updated) }
            TimerStatus.IDLE -> {
                val total = updated.durationSecondsFor(s.phase)
                _state.update { it.copy(project = updated, totalSeconds = total, remainingSeconds = total) }
            }
        }
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

    /**
     * Rolls the in-memory session/today counters onto the current logical day (F-022). The counters
     * live only in [TimerState] and are otherwise recomputed solely on project (re)selection, so when
     * the process survives past the day-end boundary (e.g. overnight) yesterday's filled bullets stay
     * on screen. Call this when the app returns to the foreground.
     *
     * Only a stopped/between-phase state rolls over; a running or paused (parked) interval is left
     * untouched. A stale "awaiting next break" prompt from yesterday is cleared back to a pomodoro.
     */
    fun refreshForNewDayIfNeeded() {
        val s = _state.value
        val project = s.project ?: return
        if (s.status != TimerStatus.IDLE) return
        if (sessionDayKey != null && sessionDayKey == currentDayKey()) return
        stopIdleAlert()
        if (s.awaitingNext || s.phase != Phase.POMODORO) {
            val total = project.durationSecondsFor(Phase.POMODORO)
            _state.update {
                it.copy(
                    phase = Phase.POMODORO,
                    totalSeconds = total,
                    remainingSeconds = total,
                    awaitingNext = false,
                    pomodorosSinceLongBreak = 0
                )
            }
        } else {
            _state.update { it.copy(pomodorosSinceLongBreak = 0) }
        }
        refreshCompletedToday(project)
    }

    /**
     * Scrubs the current interval to [fraction] of its total remaining (0 = almost done, 1 = full),
     * letting the user "fast-forward" a phase they started late (e.g. forgot to start the timer for
     * an already-running meeting). Allowed while RUNNING, PAUSED or IDLE. Clamped to at least 1s so a
     * stray drag can't auto-complete the phase. Session/today progress is untouched.
     */
    fun seek(fraction: Float) {
        val s = _state.value
        if (s.project == null || s.totalSeconds <= 0) return
        val target = (fraction.coerceIn(0f, 1f) * s.totalSeconds).toInt().coerceIn(1, s.totalSeconds)
        when (s.status) {
            TimerStatus.RUNNING -> {
                deadlineElapsed = SystemClock.elapsedRealtime() + target * 1000L
                _state.update { it.copy(remainingSeconds = target) }
            }
            TimerStatus.PAUSED, TimerStatus.IDLE ->
                _state.update { it.copy(remainingSeconds = target) }
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
        // Idle alert also applies to a forgotten pause (#2).
        startIdleAlert()
    }

    private fun resume() {
        stopIdleAlert()
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
            persistCompletedPomodoro(project, durationSeconds = s.totalSeconds)
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

        val autostartNext = if (next.phase.isBreak) settings.autostartBreaks else settings.autostartPomodoros
        if (autostartNext) {
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

    /** The timer is stalled when paused or between phases awaiting the next one. */
    private fun isStalled(): Boolean {
        val s = _state.value
        return s.status == TimerStatus.PAUSED || (s.status == TimerStatus.IDLE && s.awaitingNext)
    }

    private fun startIdleAlert() {
        stopIdleAlert()
        val minutes = settings.idleAlertMinutes
        if (minutes <= 0) return
        idleJob = scope.launch {
            while (isStalled()) {
                delay(minutes * 60_000L)
                if (!isStalled()) break
                // Gentle strobe: a few quick color flips over ~2s (not a permanent switch, #5).
                repeat(IDLE_STROBE_BLINKS) {
                    if (!isStalled()) return@launch
                    _state.update { it.copy(idleAlertActive = true) }
                    delay(IDLE_STROBE_HALF_MS)
                    _state.update { it.copy(idleAlertActive = false) }
                    delay(IDLE_STROBE_HALF_MS)
                }
            }
            _state.update { it.copy(idleAlertActive = false) }
        }
    }

    private fun stopIdleAlert() {
        idleJob?.cancel()
        idleJob = null
        if (_state.value.idleAlertActive) {
            _state.update { it.copy(idleAlertActive = false) }
        }
    }

    // --- Stats ----------------------------------------------------------------------------

    private fun currentDayKey(): String =
        LogicalDay.keyFor(dayEndHour = settings.dayEndHour, dayEndMinute = settings.dayEndMinute)

    private fun persistCompletedPomodoro(project: Project, durationSeconds: Int) {
        val dayKey = currentDayKey()
        val end = System.currentTimeMillis()
        val start = end - durationSeconds * 1000L
        scope.launch {
            statsRepository.recordCompletedPomodoro(project.id, dayKey, start, end, durationSeconds)
        }
    }

    private fun refreshCompletedToday(project: Project) {
        val dayKey = currentDayKey()
        sessionDayKey = dayKey
        scope.launch {
            val today = statsRepository.completedCount(project.id, dayKey)
            // Restore session bullets from today's completed count so progress survives a restart
            // (PRD: restore the count of completed pomodoros).
            val perSession = project.pomodorosPerSession.coerceAtLeast(1)
            val sessionBullets = if (today <= 0) 0 else ((today - 1) % perSession) + 1
            _state.update {
                if (it.project?.id == project.id)
                    it.copy(completedToday = today, completedInSession = sessionBullets)
                else it
            }
        }
    }

    private companion object {
        const val IDLE_STROBE_BLINKS = 3
        const val IDLE_STROBE_HALF_MS = 320L
    }
}
