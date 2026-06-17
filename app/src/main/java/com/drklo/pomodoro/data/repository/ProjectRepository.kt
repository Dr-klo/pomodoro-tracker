package com.drklo.pomodoro.data.repository

import com.drklo.pomodoro.data.db.ProjectDao
import com.drklo.pomodoro.data.db.ProjectEntity
import com.drklo.pomodoro.data.db.toDomain
import com.drklo.pomodoro.data.db.toEntity
import com.drklo.pomodoro.data.model.Project
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ProjectRepository(private val dao: ProjectDao) {

    val projects: Flow<List<Project>> =
        dao.observeAll().map { list -> list.map(ProjectEntity::toDomain) }

    suspend fun getById(id: Long): Project? = dao.getById(id)?.toDomain()

    /** Inserts a new project at the end of the carousel; returns the generated id. */
    suspend fun add(project: Project): Long {
        val nextOrder = dao.maxOrderIndex() + 1
        return dao.insert(project.copy(id = 0, orderIndex = nextOrder).toEntity())
    }

    suspend fun update(project: Project) = dao.update(project.toEntity())

    suspend fun delete(project: Project) = dao.delete(project.toEntity())

    /** Populates default projects on first launch. */
    suspend fun ensureSeeded() {
        if (dao.count() == 0) {
            dao.insertAll(DefaultProjects.entities())
        }
    }
}

/**
 * Default projects created on first launch, mirroring the PRD example
 * (work / study / reading with their own presets). Names are editable by the user.
 */
private object DefaultProjects {
    fun entities(): List<ProjectEntity> = listOf(
        ProjectEntity(
            name = "Работа",
            focusMinutes = 30, shortBreakMinutes = 10,
            pomodorosPerSession = 8,
            pomodoroColor = 0xFFE53935.toInt(), breakColor = 0xFF43A047.toInt(),
            dailyGoal = 8,
            longBreakEnabled = true, longBreakMinutes = 20, longBreakInterval = 4,
            orderIndex = 0
        ),
        ProjectEntity(
            name = "Учёба",
            focusMinutes = 25, shortBreakMinutes = 5,
            pomodorosPerSession = 3,
            pomodoroColor = 0xFF1E88E5.toInt(), breakColor = 0xFF43A047.toInt(),
            dailyGoal = 3,
            longBreakEnabled = false, longBreakMinutes = 15, longBreakInterval = 4,
            orderIndex = 1
        ),
        ProjectEntity(
            name = "Чтение",
            focusMinutes = 45, shortBreakMinutes = 15,
            pomodorosPerSession = 2,
            pomodoroColor = 0xFF8E24AA.toInt(), breakColor = 0xFFFB8C00.toInt(),
            dailyGoal = 2,
            longBreakEnabled = false, longBreakMinutes = 30, longBreakInterval = 4,
            orderIndex = 2
        )
    )
}
