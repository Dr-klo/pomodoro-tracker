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

    /**
     * A project's counters from [fromDayKey] onwards. Compared as text on purpose: the keys are ISO
     * dates, so lexicographic order is chronological order and SQLite needs no date support.
     */
    @Query("SELECT * FROM day_stats WHERE projectId = :projectId AND dayKey >= :fromDayKey ORDER BY dayKey ASC")
    suspend fun since(projectId: Long, fromDayKey: String): List<DayStatEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(stat: DayStatEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stats: List<DayStatEntity>)

    @Query("DELETE FROM day_stats")
    suspend fun deleteAll()

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
