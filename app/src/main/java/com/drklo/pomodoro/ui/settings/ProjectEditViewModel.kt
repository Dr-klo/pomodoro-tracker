package com.drklo.pomodoro.ui.settings

import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import com.drklo.pomodoro.data.model.Preset
import com.drklo.pomodoro.data.model.Project
import com.drklo.pomodoro.data.repository.ProjectStore
import com.drklo.pomodoro.ui.theme.DefaultBreakColor
import com.drklo.pomodoro.ui.theme.DefaultPomodoroColor
import com.drklo.pomodoro.util.launchSafely
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ProjectEditViewModel(private val repo: ProjectStore) : ViewModel() {

    private val _project = MutableStateFlow<Project?>(null)
    val project: StateFlow<Project?> = _project.asStateFlow()

    private var loadedFor: Long? = null

    /** The project as it was loaded, so "did the user change anything" is an answerable question. */
    private var original: Project? = null

    /** True when there are edits the user has not saved. */
    val hasUnsavedChanges: Boolean get() = _project.value != original

    /** Loads an existing project, or seeds a blank one for [projectId] < 0 (new). */
    fun load(projectId: Long) {
        if (loadedFor == projectId && _project.value != null) return
        loadedFor = projectId
        launchSafely {
            val loaded = if (projectId >= 0) repo.getById(projectId) ?: blank() else blank()
            original = loaded
            _project.value = loaded
        }
    }

    val isNew: Boolean get() = (_project.value?.id ?: 0L) == 0L

    fun edit(transform: (Project) -> Project) {
        _project.value = _project.value?.let(transform)
    }

    fun applyPreset(preset: Preset) = edit {
        it.copy(focusMinutes = preset.focusMinutes, shortBreakMinutes = preset.shortBreakMinutes)
    }

    fun save(fallbackName: String, onDone: () -> Unit) {
        val current = _project.value ?: return
        val sanitized = current.copy(
            // From resources: a hardcoded "Pomodoro" would sit in Latin among Работа and Учёба.
            name = current.name.trim().ifBlank { fallbackName }
        )
        launchSafely {
            if (sanitized.id == 0L) repo.add(sanitized) else repo.update(sanitized)
            original = sanitized
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        val current = _project.value ?: return
        if (current.id == 0L) {
            onDone(); return
        }
        launchSafely {
            repo.archive(current)
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
