package com.drklo.pomodoro.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ProjectEntity::class, DayStatEntity::class, PomodoroLogEntity::class],
    version = 2,
    // Schemas are exported to app/schemas and committed: without them migrations cannot be tested.
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun dayStatDao(): DayStatDao
    abstract fun pomodoroLogDao(): PomodoroLogDao

    companion object {
        const val NAME = "pomodoro.db"

        /** v2 adds the pomodoro_log table for statistics. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS pomodoro_log (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "projectId INTEGER NOT NULL, " +
                        "startEpochMs INTEGER NOT NULL, " +
                        "endEpochMs INTEGER NOT NULL, " +
                        "durationSeconds INTEGER NOT NULL, " +
                        "dayKey TEXT NOT NULL)"
                )
            }
        }
    }
}
