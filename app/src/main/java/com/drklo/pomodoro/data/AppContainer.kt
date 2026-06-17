package com.drklo.pomodoro.data

import android.content.Context
import androidx.room.Room
import com.drklo.pomodoro.data.db.AppDatabase
import com.drklo.pomodoro.data.repository.ProjectRepository
import com.drklo.pomodoro.data.repository.SettingsRepository
import com.drklo.pomodoro.data.repository.StatsRepository

/**
 * Manual dependency container (simple service locator). Held by [com.drklo.pomodoro.PomodoroApp]
 * and reused across ViewModels and the timer service.
 */
class AppContainer(context: Context) {

    private val database: AppDatabase = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        AppDatabase.NAME
    ).build()

    val projectRepository: ProjectRepository by lazy { ProjectRepository(database.projectDao()) }
    val statsRepository: StatsRepository by lazy { StatsRepository(database.dayStatDao()) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(context.applicationContext) }
}
