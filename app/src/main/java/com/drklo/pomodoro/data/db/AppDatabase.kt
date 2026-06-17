package com.drklo.pomodoro.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ProjectEntity::class, DayStatEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun dayStatDao(): DayStatDao

    companion object {
        const val NAME = "pomodoro.db"
    }
}
