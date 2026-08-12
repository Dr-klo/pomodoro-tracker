package com.drklo.pomodoro.data.repository

import androidx.room.withTransaction
import com.drklo.pomodoro.data.db.AppDatabase
import com.drklo.pomodoro.data.db.DayStatDao
import com.drklo.pomodoro.data.db.PomodoroLogDao
import com.drklo.pomodoro.data.db.PomodoroLogEntity
import com.drklo.pomodoro.data.db.toDomain
import com.drklo.pomodoro.data.model.PomodoroLog
import com.drklo.pomodoro.timer.PomodoroStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class StatsRepository(private val db: AppDatabase) : PomodoroStats {

    private val dao: DayStatDao = db.dayStatDao()
    private val logDao: PomodoroLogDao = db.pomodoroLogDao()

    /** Observes the completed-pomodoro count for a project on a given logical day. */
    fun observeCompleted(projectId: Long, dayKey: String): Flow<Int> =
        dao.observeCompleted(projectId, dayKey).map { it ?: 0 }

    override suspend fun completedCount(projectId: Long, dayKey: String): Int =
        dao.get(projectId, dayKey)?.completedPomodoros ?: 0

    /** Records one finished pomodoro: daily goal counter + a detailed log entry for stats. */
    override suspend fun recordCompletedPomodoro(
        projectId: Long,
        dayKey: String,
        startEpochMs: Long,
        endEpochMs: Long,
        durationSeconds: Int
    ) {
        // Both rows or neither. `day_stats` drives the bullets and the daily goal while
        // `pomodoro_log` drives every report, so a half-written pomodoro would leave the two
        // permanently disagreeing about the same day, with nothing to reconcile them.
        db.withTransaction {
            dao.addCompleted(projectId, dayKey, 1)
            logDao.insert(
                PomodoroLogEntity(
                    projectId = projectId,
                    startEpochMs = startEpochMs,
                    endEpochMs = endEpochMs,
                    durationSeconds = durationSeconds,
                    dayKey = dayKey
                )
            )
        }
    }

    /** Full pomodoro history for the statistics screens. */
    fun observeLog(): Flow<List<PomodoroLog>> =
        logDao.observeAll().map { list -> list.map { it.toDomain() } }
}
