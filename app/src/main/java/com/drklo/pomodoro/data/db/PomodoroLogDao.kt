package com.drklo.pomodoro.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PomodoroLogDao {

    @Insert
    suspend fun insert(entry: PomodoroLogEntity)

    @Insert
    suspend fun insertAll(entries: List<PomodoroLogEntity>)

    @Query("SELECT * FROM pomodoro_log ORDER BY endEpochMs ASC")
    fun observeAll(): Flow<List<PomodoroLogEntity>>

    /** A project's pomodoros from [fromDayKey] onwards; see [DayStatDao.since] on the comparison. */
    @Query(
        "SELECT * FROM pomodoro_log WHERE projectId = :projectId AND dayKey >= :fromDayKey " +
            "ORDER BY id ASC"
    )
    suspend fun since(projectId: Long, fromDayKey: String): List<PomodoroLogEntity>

    @Query("DELETE FROM pomodoro_log")
    suspend fun deleteAll()
}
