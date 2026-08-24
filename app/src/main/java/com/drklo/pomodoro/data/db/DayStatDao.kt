package com.drklo.pomodoro.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface DayStatDao {

    @Query("SELECT * FROM day_stats WHERE projectId = :projectId AND dayKey = :dayKey")
    suspend fun get(projectId: Long, dayKey: String): DayStatEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(stat: DayStatEntity)

    @Update
    suspend fun update(stat: DayStatEntity)

    /** Atomically add [delta] (default 1) completed pomodoros for the given project/day. */
    @Transaction
    suspend fun addCompleted(projectId: Long, dayKey: String, delta: Int = 1) {
        val current = get(projectId, dayKey)
        if (current == null) {
            insert(DayStatEntity(projectId, dayKey, delta.coerceAtLeast(0)))
        } else {
            update(current.copy(completedPomodoros = (current.completedPomodoros + delta).coerceAtLeast(0)))
        }
    }
}
