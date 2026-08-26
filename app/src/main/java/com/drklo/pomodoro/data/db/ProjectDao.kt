package com.drklo.pomodoro.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {

    /** Projects the user can pick and edit. Archived ones are gone from here by design. */
    @Query("SELECT * FROM projects WHERE archivedAt IS NULL ORDER BY orderIndex ASC, id ASC")
    fun observeActive(): Flow<List<ProjectEntity>>

    /** Everything ever created, archived included — the reports must still name and colour it. */
    @Query("SELECT * FROM projects ORDER BY orderIndex ASC, id ASC")
    fun observeAll(): Flow<List<ProjectEntity>>

    /** A snapshot for export, archived included: a backup is a moment, not a feed. */
    @Query("SELECT * FROM projects ORDER BY orderIndex ASC, id ASC")
    suspend fun getAll(): List<ProjectEntity>

    @Query("DELETE FROM projects")
    suspend fun deleteAll()

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getById(id: Long): ProjectEntity?

    @Query("SELECT COUNT(*) FROM projects")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM projects WHERE archivedAt IS NULL")
    suspend fun countActive(): Int

    @Query("UPDATE projects SET archivedAt = :at WHERE id = :id")
    suspend fun archive(id: Long, at: Long)

    @Query("SELECT COALESCE(MAX(orderIndex), -1) FROM projects")
    suspend fun maxOrderIndex(): Int

    @Insert
    suspend fun insert(project: ProjectEntity): Long

    @Insert
    suspend fun insertAll(projects: List<ProjectEntity>)

    @Update
    suspend fun update(project: ProjectEntity)
}
