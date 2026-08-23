package com.drklo.pomodoro.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.drklo.pomodoro.PomodoroApp
import com.drklo.pomodoro.data.model.AppLanguage
import com.drklo.pomodoro.data.model.GlobalSettings
import com.drklo.pomodoro.data.model.Project
import com.drklo.pomodoro.data.model.ThemeMode
import com.drklo.pomodoro.util.launchSafely
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as PomodoroApp).container
    private val settingsRepo = container.settingsRepository
    private val projectRepo = container.projectRepository

    val settings: StateFlow<GlobalSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GlobalSettings())

    val projects: StateFlow<List<Project>> = projectRepo.projects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setSound(value: Boolean) = launchSafely { settingsRepo.setSoundEnabled(value) }
    fun setVibrate(value: Boolean) = launchSafely { settingsRepo.setVibrateEnabled(value) }
    fun setAlwaysOn(value: Boolean) = launchSafely { settingsRepo.setAlwaysOnDisplay(value) }
    fun setAutostartPomodoros(value: Boolean) = launchSafely { settingsRepo.setAutostartPomodoros(value) }
    fun setAutostartBreaks(value: Boolean) = launchSafely { settingsRepo.setAutostartBreaks(value) }
    fun setIdleAlertMinutes(value: Int) = launchSafely { settingsRepo.setIdleAlertMinutes(value) }
    fun setHoldFinishedPhaseColor(value: Boolean) = launchSafely { settingsRepo.setHoldFinishedPhaseColor(value) }
    fun setDayEnd(hour: Int, minute: Int) = launchSafely { settingsRepo.setDayEnd(hour, minute) }

    /**
     * Persists the language and only then runs [onSaved]. The caller recreates the activity there,
     * and the new language is picked up in `attachBaseContext`, which re-reads the setting: firing
     * both at once let the activity come back up on the old language, and nothing would correct it
     * until the next launch, because rotation is intercepted by configChanges.
     */
    fun setLanguage(language: AppLanguage, onSaved: () -> Unit) = launchSafely {
        settingsRepo.setLanguage(language)
        onSaved()
    }
    fun setThemeMode(mode: ThemeMode) = launchSafely { settingsRepo.setThemeMode(mode) }

    /** Soft delete: the project leaves the list, its pomodoros stay in the reports. */
    fun deleteProject(project: Project) = launchSafely { projectRepo.archive(project) }
}
