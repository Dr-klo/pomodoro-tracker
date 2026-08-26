package com.drklo.pomodoro.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.drklo.pomodoro.data.backup.ProjectBackup
import com.drklo.pomodoro.data.db.AppDatabase
import com.drklo.pomodoro.data.db.DayStatEntity
import com.drklo.pomodoro.data.db.PomodoroLogEntity
import com.drklo.pomodoro.data.db.ProjectEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.StringReader
import java.io.StringWriter
import java.time.LocalDate

/**
 * The whole point of the feature in one test: what comes out of one database goes into another and
 * the second one holds the same thing. Two real Room databases, in memory — the export queries and
 * the replacing transaction are most of the risk, and neither is exercised by a format test.
 */
@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {

    private lateinit var source: AppDatabase
    private lateinit var target: AppDatabase

    private val today = LocalDate.of(2026, 8, 26)

    @Before
    fun open() {
        source = inMemory()
        target = inMemory()
    }

    @After
    fun close() {
        source.close()
        target.close()
    }

    private fun inMemory() = Room
        .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
        .build()

    private fun project(name: String, order: Int, archivedAt: Long? = null) = ProjectEntity(
        name = name,
        focusMinutes = 25,
        shortBreakMinutes = 5,
        pomodorosPerSession = 4,
        pomodoroColor = 0xFFB74B4B.toInt(),
        breakColor = 0xFF4B7BB7.toInt(),
        dailyGoal = 8,
        longBreakEnabled = true,
        longBreakMinutes = 15,
        longBreakInterval = 4,
        orderIndex = order,
        archivedAt = archivedAt
    )

    private suspend fun seedSource() {
        val work = source.projectDao().insert(project("Work", 0))
        val gone = source.projectDao().insert(project("Test", 1, archivedAt = 1_786_746_079_711))

        source.dayStatDao().insertAll(
            listOf(
                DayStatEntity(work, "2026-08-25", 6),
                DayStatEntity(work, "2026-08-26", 2),
                DayStatEntity(gone, "2026-08-20", 3)
            )
        )
        source.pomodoroLogDao().insertAll(
            listOf(
                PomodoroLogEntity(
                    projectId = work,
                    startEpochMs = 1_000,
                    endEpochMs = 2_500,
                    durationSeconds = 1500,
                    dayKey = "2026-08-25"
                ),
                PomodoroLogEntity(
                    projectId = gone,
                    startEpochMs = 3_000,
                    endEpochMs = 4_500,
                    durationSeconds = 1500,
                    dayKey = "2026-08-20"
                )
            )
        )
    }

    private suspend fun moveAcross(): Int {
        val text = StringWriter()
        BackupRepository(source).export(text, nowMs = 1_787_000_000_000, today = today, appVersion = "1.1")
        return BackupRepository(target).import(StringReader(text.toString()))
    }

    @Test
    fun everythingArrivesOnTheOtherSide() = runTest {
        seedSource()

        assertEquals(2, moveAcross())

        val restored = target.projectDao().getAll()
        assertEquals(listOf("Work", "Test"), restored.map { it.name })
        assertEquals(25, restored.first().focusMinutes)
        assertEquals(0xFFB74B4B.toInt(), restored.first().pomodoroColor)
    }

    @Test
    fun historyStaysWithTheProjectItBelongsTo() = runTest {
        seedSource()
        moveAcross()

        val work = target.projectDao().getAll().first { it.name == "Work" }
        val test = target.projectDao().getAll().first { it.name == "Test" }

        // The ids in the file are not the ids here; this is the re-pointing that has to be right,
        // and getting it wrong would silently attach one project's history to another.
        assertEquals(2, target.dayStatDao().since(work.id, "2000-01-01").size)
        assertEquals(6, target.dayStatDao().since(work.id, "2026-08-25").first().completedPomodoros)
        assertEquals(1, target.pomodoroLogDao().since(work.id, "2000-01-01").size)
        assertEquals(1, target.pomodoroLogDao().since(test.id, "2000-01-01").size)
    }

    @Test
    fun anArchivedProjectKeepsItsHistoryAndStaysArchived() = runTest {
        seedSource()
        moveAcross()

        val test = target.projectDao().getAll().first { it.name == "Test" }

        // Leaving archived projects out of the export would make the reports show smaller numbers
        // after a restore than before it, which is a quiet kind of data loss.
        assertEquals(1_786_746_079_711, test.archivedAt)
        assertEquals(3, target.dayStatDao().since(test.id, "2000-01-01").first().completedPomodoros)
    }

    @Test
    fun historyOlderThanAYearIsLeftBehind() = runTest {
        val work = source.projectDao().insert(project("Work", 0))
        val old = today.minusDays(ProjectBackup.HISTORY_DAYS + 5).toString()
        val recent = today.minusDays(3).toString()
        source.dayStatDao().insertAll(
            listOf(DayStatEntity(work, old, 4), DayStatEntity(work, recent, 5))
        )

        moveAcross()

        val restored = target.projectDao().getAll().single()
        val days = target.dayStatDao().since(restored.id, "2000-01-01")
        assertEquals(listOf(recent), days.map { it.dayKey })
        // The project itself travels regardless of how old it is — it is configuration, not history.
        assertEquals("Work", restored.name)
    }

    @Test
    fun importReplacesRatherThanAddsTo() = runTest {
        seedSource()
        target.projectDao().insert(project("Something else", 0))
        target.pomodoroLogDao().insertAll(
            listOf(
                PomodoroLogEntity(
                    projectId = 1,
                    startEpochMs = 9,
                    endEpochMs = 10,
                    durationSeconds = 60,
                    dayKey = "2026-01-01"
                )
            )
        )

        moveAcross()

        val names = target.projectDao().getAll().map { it.name }
        assertEquals(listOf("Work", "Test"), names)
        assertTrue(
            "a stray log row survived the replace",
            target.pomodoroLogDao().since(1, "2000-01-01").none { it.dayKey == "2026-01-01" }
        )
    }

    @Test
    fun aBrokenFileLeavesTheExistingDataAlone() = runTest {
        seedSource()
        val keep = target.projectDao().insert(project("Keep me", 0))
        target.dayStatDao().insertAll(listOf(DayStatEntity(keep, "2026-08-01", 9)))

        val text = StringWriter()
        BackupRepository(source).export(text, 1_787_000_000_000, today, "1.1")
        // Damaged after the header, so the failure lands part-way through what would have been a
        // successful read — the case where a naive implementation has already emptied the tables.
        val damaged = text.toString().replace("\"focusMinutes\": 25", "\"focusMinutes\": 900")

        val failure = runCatching { BackupRepository(target).import(StringReader(damaged)) }
            .exceptionOrNull() as? ProjectBackup.BackupException
        assertEquals(ProjectBackup.Failure.INVALID_VALUE, failure?.failure)

        val survivors = target.projectDao().getAll()
        assertEquals(listOf("Keep me"), survivors.map { it.name })
        assertEquals(9, target.dayStatDao().since(keep, "2026-08-01").single().completedPomodoros)
    }

    @Test
    fun theFileDoesNotCarryTheIdsItWasWrittenWith() = runTest {
        // Seeded so the source ids start high, and the target's will not match them.
        repeat(5) { source.projectDao().insert(project("filler $it", it)) }
        source.projectDao().deleteAll()
        seedSource()

        moveAcross()

        val sourceIds = source.projectDao().getAll().map { it.id }
        val targetIds = target.projectDao().getAll().map { it.id }
        assertNotEquals(sourceIds, targetIds)
        assertNull(target.projectDao().getById(sourceIds.first())?.takeIf { it.name != "Work" })
    }
}
