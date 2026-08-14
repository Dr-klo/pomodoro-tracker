package com.drklo.pomodoro.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The cheapest insurance against corrupting a user's history: it proves that a database which
 * arrived by migration matches the schema Room would create from scratch, and that the rows already
 * in it survive. `runMigrationsAndValidate` does the schema comparison against the exported JSON.
 *
 * Only v2 onwards can be covered — v1's schema was never exported and no longer exists anywhere.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate2to3_keepsProjectsAndTheirHistory() {
        helper.createDatabase(TEST_DB, 2).use { db ->
            db.execSQL(
                "INSERT INTO projects (id, name, focusMinutes, shortBreakMinutes, " +
                    "pomodorosPerSession, pomodoroColor, breakColor, dailyGoal, longBreakEnabled, " +
                    "longBreakMinutes, longBreakInterval, orderIndex) " +
                    "VALUES (1, 'Work', 25, 5, 4, -1, -2, 8, 0, 15, 4, 0)"
            )
            db.execSQL(
                "INSERT INTO pomodoro_log (projectId, startEpochMs, endEpochMs, durationSeconds, dayKey) " +
                    "VALUES (1, 1000, 2000, 1500, '2026-05-13')"
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, AppDatabase.MIGRATION_2_3)

        db.query("SELECT name, archivedAt FROM projects WHERE id = 1").use { cursor ->
            assertTrue("the project must survive the migration", cursor.moveToFirst())
            assertEquals("Work", cursor.getString(0))
            assertTrue("an existing project is active, not archived", cursor.isNull(1))
        }
        db.query("SELECT COUNT(*) FROM pomodoro_log").use { cursor ->
            cursor.moveToFirst()
            assertEquals("history is never dropped by a migration", 1, cursor.getInt(0))
        }
    }

    @Test
    fun migrate2to3_acceptsAnArchiveStamp() {
        helper.createDatabase(TEST_DB, 2).use { db ->
            db.execSQL(
                "INSERT INTO projects (id, name, focusMinutes, shortBreakMinutes, " +
                    "pomodorosPerSession, pomodoroColor, breakColor, dailyGoal, longBreakEnabled, " +
                    "longBreakMinutes, longBreakInterval, orderIndex) " +
                    "VALUES (1, 'Holidays', 25, 5, 4, -1, -2, 8, 0, 15, 4, 0)"
            )
        }
        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, AppDatabase.MIGRATION_2_3)

        db.execSQL("UPDATE projects SET archivedAt = 1700000000000 WHERE id = 1")

        db.query("SELECT archivedAt FROM projects WHERE id = 1").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1_700_000_000_000L, cursor.getLong(0))
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
