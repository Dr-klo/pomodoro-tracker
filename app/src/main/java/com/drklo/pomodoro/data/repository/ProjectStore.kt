package com.drklo.pomodoro.data.repository

import com.drklo.pomodoro.data.model.Project
import kotlinx.coroutines.flow.Flow

/**
 * What the screens do with projects — nothing more. The ViewModels depend on this rather than on
 * [ProjectRepository] so a test can supply its own implementation instead of standing up Room
 * (F-R6-06 / step T3), the same way [com.drklo.pomodoro.timer.TimerEngine] depends on its ports.
 */
interface ProjectStore {

    /** Projects the user can pick and edit; archived ones are gone from here by design. */
    val projects: Flow<List<Project>>

    /** Everything ever created, archived included — the reports still need to name and colour it. */
    val allProjects: Flow<List<Project>>

    suspend fun getById(id: Long): Project?

    suspend fun add(project: Project): Long

    suspend fun update(project: Project)

    /** Soft delete. Returns false when refused, which happens for the last remaining project. */
    suspend fun archive(project: Project, now: Long = System.currentTimeMillis()): Boolean
}
