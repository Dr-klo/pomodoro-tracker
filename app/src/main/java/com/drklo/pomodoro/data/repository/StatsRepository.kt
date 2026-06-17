package com.drklo.pomodoro.data.repository

import com.drklo.pomodoro.data.db.DayStatDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class StatsRepository(private val dao: DayStatDao) {

    /** Observes the completed-pomodoro count for a project on a given logical day. */
    fun observeCompleted(projectId: Long, dayKey: String): Flow<Int> =
        dao.observeCompleted(projectId, dayKey).map { it ?: 0 }

    suspend fun completedCount(projectId: Long, dayKey: String): Int =
        dao.get(projectId, dayKey)?.completedPomodoros ?: 0

    /** Records one finished pomodoro toward the project's daily goal. */
    suspend fun recordCompletedPomodoro(projectId: Long, dayKey: String) =
        dao.addCompleted(projectId, dayKey, 1)
}
