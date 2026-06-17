package com.drklo.pomodoro.ui.reports

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.drklo.pomodoro.PomodoroApp
import com.drklo.pomodoro.data.LogicalDay
import com.drklo.pomodoro.data.model.GlobalSettings
import com.drklo.pomodoro.data.model.PomodoroLog
import com.drklo.pomodoro.data.model.Project
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.LocalDateTime

/** Aggregated totals shown in the header rows of both report tabs. */
data class ReportsSummary(
    val totalFocusSec: Int = 0,
    val todayFocusSec: Int = 0,
    val weekFocusSec: Int = 0,
    val totalPomodoros: Int = 0,
    val todayPomodoros: Int = 0,
    val weekPomodoros: Int = 0
)

class ReportsViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as PomodoroApp).container

    val projects: StateFlow<List<Project>> = container.projectRepository.projects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val logs: StateFlow<List<PomodoroLog>> = container.statsRepository.observeLog()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val settings: StateFlow<GlobalSettings> = container.settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GlobalSettings())

    /** Logical "today" date, honoring the end-of-day boundary. */
    val today: StateFlow<LocalDate> = settings
        .map { LogicalDay.dateFor(LocalDateTime.now(), it.dayEndHour, it.dayEndMinute) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalDate.now())

    /** Project id -> pomodoro color (ARGB), for coloring chart segments. */
    val projectColors: StateFlow<Map<Long, Int>> = projects
        .map { list -> list.associate { it.id to it.pomodoroColor } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val summary: StateFlow<ReportsSummary> =
        combine(logs, settings) { log, s ->
            val today = LogicalDay.dateFor(LocalDateTime.now(), s.dayEndHour, s.dayEndMinute)
            val todayKey = today.toString()
            val weekKeys = (0..6).map { today.minusDays(it.toLong()).toString() }.toSet()
            ReportsSummary(
                totalFocusSec = log.sumOf { it.durationSeconds },
                todayFocusSec = log.filter { it.dayKey == todayKey }.sumOf { it.durationSeconds },
                weekFocusSec = log.filter { it.dayKey in weekKeys }.sumOf { it.durationSeconds },
                totalPomodoros = log.size,
                todayPomodoros = log.count { it.dayKey == todayKey },
                weekPomodoros = log.count { it.dayKey in weekKeys }
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportsSummary())
}
