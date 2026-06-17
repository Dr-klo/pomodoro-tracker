package com.drklo.pomodoro.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PomodoroLogDao {

    @Insert
    suspend fun insert(entry: PomodoroLogEntity)

    @Query("SELECT * FROM pomodoro_log ORDER BY endEpochMs ASC")
    fun observeAll(): Flow<List<PomodoroLogEntity>>
}
