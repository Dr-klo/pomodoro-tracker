package com.drklo.pomodoro.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.drklo.pomodoro.PomodoroApp
import com.drklo.pomodoro.data.model.AppLanguage
import com.drklo.pomodoro.data.model.GlobalSettings
import com.drklo.pomodoro.data.model.Project
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as PomodoroApp).container
    private val settingsRepo = container.settingsRepository
    private val projectRepo = container.projectRepository

    val settings: StateFlow<GlobalSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GlobalSettings())

    val projects: StateFlow<List<Project>> = projectRepo.projects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setSound(value: Boolean) = viewModelScope.launch { settingsRepo.setSoundEnabled(value) }
    fun setVibrate(value: Boolean) = viewModelScope.launch { settingsRepo.setVibrateEnabled(value) }
    fun setAlwaysOn(value: Boolean) = viewModelScope.launch { settingsRepo.setAlwaysOnDisplay(value) }
    fun setAutostart(value: Boolean) = viewModelScope.launch { settingsRepo.setAutostart(value) }
    fun setIdleAlertMinutes(value: Int) = viewModelScope.launch { settingsRepo.setIdleAlertMinutes(value) }
    fun setDayEnd(hour: Int, minute: Int) = viewModelScope.launch { settingsRepo.setDayEnd(hour, minute) }
    fun setLanguage(language: AppLanguage) = viewModelScope.launch { settingsRepo.setLanguage(language) }

    fun deleteProject(project: Project) = viewModelScope.launch { projectRepo.delete(project) }
}
