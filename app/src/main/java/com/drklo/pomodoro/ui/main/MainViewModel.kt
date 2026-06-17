package com.drklo.pomodoro.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.drklo.pomodoro.PomodoroApp
import com.drklo.pomodoro.data.model.GlobalSettings
import com.drklo.pomodoro.data.model.Phase
import com.drklo.pomodoro.data.model.Project
import com.drklo.pomodoro.data.model.TimerStatus
import com.drklo.pomodoro.timer.TimerEvent
import com.drklo.pomodoro.timer.TimerService
import com.drklo.pomodoro.timer.TimerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as PomodoroApp).container
    private val engine = container.timerEngine

    val timerState: StateFlow<TimerState> = engine.state

    val projects: StateFlow<List<Project>> = container.projectRepository.projects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<GlobalSettings> = container.settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GlobalSettings())

    /** Incremented each time the daily goal is reached, to trigger the fanfare animation. */
    private val _fanfareTrigger = MutableStateFlow(0)
    val fanfareTrigger: StateFlow<Int> = _fanfareTrigger.asStateFlow()

    init {
        // Ensure an active project is selected once projects are available.
        viewModelScope.launch {
            projects.collect { list ->
                if (list.isNotEmpty() && engine.state.value.project == null) {
                    engine.setActiveProject(list.first())
                }
            }
        }
        viewModelScope.launch {
            engine.events.collect { event ->
                if (event is TimerEvent.GoalReached) {
                    _fanfareTrigger.value += 1
                }
            }
        }
    }

    /** Index of the currently active project within [projects], or 0. */
    fun indexOfActiveProject(): Int {
        val id = engine.state.value.project?.id ?: return 0
        return projects.value.indexOfFirst { it.id == id }.coerceAtLeast(0)
    }

    fun onPlayPause() {
        val wasIdle = engine.state.value.status == TimerStatus.IDLE
        engine.togglePlayPause()
        if (wasIdle && engine.state.value.status == TimerStatus.RUNNING) {
            TimerService.start(getApplication())
        }
    }

    fun onReset() {
        engine.reset()
    }

    /** Carousel selection (F-008). No-op while running. */
    fun onSelectProject(index: Int) {
        val list = projects.value
        val project = list.getOrNull(index) ?: return
        if (project.id == engine.state.value.project?.id) return
        engine.setActiveProject(project)
    }

    /** Cycles the phase type manually (F-021): pomodoro -> short break -> long break -> pomodoro. */
    fun onChangePhase() {
        val next = when (engine.state.value.phase) {
            Phase.POMODORO -> Phase.SHORT_BREAK
            Phase.SHORT_BREAK -> Phase.LONG_BREAK
            Phase.LONG_BREAK -> Phase.POMODORO
        }
        engine.setPhase(next)
    }
}
