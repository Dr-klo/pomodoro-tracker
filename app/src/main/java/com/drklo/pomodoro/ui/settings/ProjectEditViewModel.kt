package com.drklo.pomodoro.ui.settings

import android.app.Application
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.AndroidViewModel
import com.drklo.pomodoro.PomodoroApp
import com.drklo.pomodoro.data.model.Preset
import com.drklo.pomodoro.data.model.Project
import com.drklo.pomodoro.ui.theme.DefaultBreakColor
import com.drklo.pomodoro.ui.theme.DefaultPomodoroColor
import com.drklo.pomodoro.util.launchSafely
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ProjectEditViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as PomodoroApp).container.projectRepository

    private val _project = MutableStateFlow<Project?>(null)
    val project: StateFlow<Project?> = _project.asStateFlow()

    private var loadedFor: Long? = null

    /** Loads an existing project, or seeds a blank one for [projectId] < 0 (new). */
    fun load(projectId: Long) {
        if (loadedFor == projectId && _project.value != null) return
        loadedFor = projectId
        launchSafely {
            _project.value = if (projectId >= 0) repo.getById(projectId) ?: blank() else blank()
        }
    }

    val isNew: Boolean get() = (_project.value?.id ?: 0L) == 0L

    fun edit(transform: (Project) -> Project) {
        _project.value = _project.value?.let(transform)
    }

    fun applyPreset(preset: Preset) = edit {
        it.copy(focusMinutes = preset.focusMinutes, shortBreakMinutes = preset.shortBreakMinutes)
    }

    fun save(onDone: () -> Unit) {
        val current = _project.value ?: return
        val sanitized = current.copy(
            name = current.name.trim().ifBlank { "Pomodoro" }
        )
        launchSafely {
            if (sanitized.id == 0L) repo.add(sanitized) else repo.update(sanitized)
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        val current = _project.value ?: return
        if (current.id == 0L) {
            onDone(); return
        }
        launchSafely {
            repo.delete(current)
            onDone()
        }
    }

    private fun blank() = Project(
        id = 0,
        name = "",
        focusMinutes = 25,
        shortBreakMinutes = 5,
        pomodorosPerSession = 4,
        pomodoroColor = DefaultPomodoroColor.toArgb(),
        breakColor = DefaultBreakColor.toArgb(),
        dailyGoal = 4,
        longBreakEnabled = false,
        longBreakMinutes = 15,
        longBreakInterval = 4,
        orderIndex = 0
    )
}
