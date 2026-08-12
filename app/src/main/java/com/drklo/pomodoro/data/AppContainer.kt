package com.drklo.pomodoro.data

import android.content.Context
import androidx.room.Room
import com.drklo.pomodoro.data.db.AppDatabase
import com.drklo.pomodoro.data.repository.ProjectRepository
import com.drklo.pomodoro.data.repository.SettingsRepository
import com.drklo.pomodoro.data.repository.StatsRepository
import com.drklo.pomodoro.timer.SystemTimeSource
import com.drklo.pomodoro.timer.TimerEffects
import com.drklo.pomodoro.timer.TimerEngine

/**
 * Manual dependency container (simple service locator). Held by [com.drklo.pomodoro.PomodoroApp]
 * and reused across ViewModels and the timer service.
 */
class AppContainer(context: Context) {

    private val database: AppDatabase = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        AppDatabase.NAME
    ).addMigrations(AppDatabase.MIGRATION_1_2).build()

    val projectRepository: ProjectRepository by lazy { ProjectRepository(database.projectDao()) }
    val statsRepository: StatsRepository by lazy {
        StatsRepository(database.dayStatDao(), database.pomodoroLogDao())
    }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(context.applicationContext) }

    private val timerEffects: TimerEffects by lazy { TimerEffects(context.applicationContext) }

    /** The single, app-wide timer instance (guarantees one active timer, F-003). */
    val timerEngine: TimerEngine by lazy {
        TimerEngine(settingsRepository, statsRepository, timerEffects, SystemTimeSource)
    }
}
