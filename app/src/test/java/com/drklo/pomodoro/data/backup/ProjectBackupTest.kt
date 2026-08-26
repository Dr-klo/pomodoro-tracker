package com.drklo.pomodoro.data.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.drklo.pomodoro.data.backup.ProjectBackup.Failure
import com.drklo.pomodoro.data.db.DayStatEntity
import com.drklo.pomodoro.data.db.PomodoroLogEntity
import com.drklo.pomodoro.data.db.ProjectEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.StringReader
import java.io.StringWriter
import java.time.LocalDate

/**
 * A backup is only worth having if it reads back exactly as it was written, and only safe if a file
 * that is not one is refused rather than half-applied. Both are checked here, against the format
 * alone — no database involved.
 */
@RunWith(AndroidJUnit4::class)
class ProjectBackupTest {

    private val work = ProjectEntity(
        id = 7,
        name = "Work",
        focusMinutes = 25,
        shortBreakMinutes = 5,
        pomodorosPerSession = 4,
        pomodoroColor = 0xFFB74B4B.toInt(),
        breakColor = 0xFF4B7BB7.toInt(),
        dailyGoal = 8,
        longBreakEnabled = true,
        longBreakMinutes = 15,
        longBreakInterval = 4,
        orderIndex = 0
    )

    private fun record(
        project: ProjectEntity = work,
        dayStats: List<DayStatEntity> = listOf(DayStatEntity(7, "2026-08-25", 6)),
        log: List<PomodoroLogEntity> = listOf(
            PomodoroLogEntity(3, 7, 1_787_000_000_000, 1_787_001_500_000, 1500, "2026-08-25")
        )
    ) = ProjectBackup.Record(project, dayStats, log)

    private fun write(vararg records: ProjectBackup.Record): String {
        val out = StringWriter()
        ProjectBackup.Sink(out).use { sink ->
            sink.begin(
                exportedAtMs = 1_787_000_000_000,
                exportedOn = LocalDate.of(2026, 8, 26),
                historyFrom = LocalDate.of(2025, 8, 26),
                appVersion = "1.1"
            )
            records.forEach(sink::project)
            sink.end()
        }
        return out.toString()
    }

    private suspend fun read(text: String): List<ProjectBackup.Record> {
        val out = mutableListOf<ProjectBackup.Record>()
        ProjectBackup.read(StringReader(text)) { out += it }
        return out
    }

    @Test
    fun aProjectComesBackTheWayItWentIn() = runTest {
        val restored = read(write(record())).single()

        // Ids are deliberately not compared: identity belongs to the database that receives the
        // file, not to the install that wrote it.
        assertEquals(work.copy(id = 0), restored.project)
        assertEquals(listOf(DayStatEntity(0, "2026-08-25", 6)), restored.dayStats)
        assertEquals(1, restored.log.size)
        assertEquals(1500, restored.log.single().durationSeconds)
        assertEquals(0, restored.log.single().projectId)
    }

    @Test
    fun bothTablesTravel() = runTest {
        // The counters are carried rather than counted back out of the log. Here the two disagree
        // on purpose — a day recorded before the log table existed — and the file must not "fix" it.
        val restored = read(
            write(
                record(
                    dayStats = listOf(DayStatEntity(7, "2026-06-17", 4)),
                    log = emptyList()
                )
            )
        ).single()

        assertEquals(4, restored.dayStats.single().completedPomodoros)
        assertTrue(restored.log.isEmpty())
    }

    @Test
    fun anArchivedProjectComesBackArchived() = runTest {
        val restored = read(write(record(project = work.copy(archivedAt = 1_786_746_079_711)))).single()

        // Otherwise it would reappear in the carousel on the new phone, which is not where the user
        // left it — and its pomodoros would lose the name they are reported under.
        assertEquals(1_786_746_079_711, restored.project.archivedAt)
    }

    @Test
    fun projectsKeepTheirOrder() = runTest {
        val second = work.copy(id = 8, name = "Reading", orderIndex = 1)
        val restored = read(write(record(), record(project = second, dayStats = emptyList(), log = emptyList())))

        assertEquals(listOf("Work", "Reading"), restored.map { it.project.name })
    }

    @Test
    fun aFileFromANewerAppIsRefusedByName() = runTest {
        val text = write(record()).replace("\"schemaVersion\": 1", "\"schemaVersion\": 99")

        assertRefused(Failure.UNSUPPORTED_VERSION) { read(text) }
    }

    @Test
    fun somethingThatIsNotABackupIsRefused() = runTest {
        assertRefused(Failure.MALFORMED) { read("""{"unrelated":true}""") }
    }

    @Test
    fun aTruncatedFileIsRefused() = runTest {
        val full = write(record())

        assertRefused(Failure.MALFORMED) { read(full.substring(0, full.length / 2)) }
    }

    @Test
    fun aValueOutsideWhatTheEditorAllowsIsRefused() = runTest {
        // 300 minutes cannot be entered in the app, so it cannot arrive through a file either —
        // otherwise import becomes the way around every bound the editor enforces.
        val text = write(record()).replace("\"focusMinutes\": 25", "\"focusMinutes\": 300")

        assertRefused(Failure.INVALID_VALUE) { read(text) }
    }

    @Test
    fun aPomodoroThatEndedBeforeItStartedIsRefused() = runTest {
        val text = write(record()).replace("\"end\": 1787001500000", "\"end\": 1000")

        assertRefused(Failure.INVALID_VALUE) { read(text) }
    }

    @Test
    fun somethingThatIsNotADateIsRefused() = runTest {
        val text = write(record()).replace("\"2026-08-25\"", "\"yesterday\"")

        assertRefused(Failure.INVALID_VALUE) { read(text) }
    }

    @Test
    fun aFileWithNoProjectsIsRefused() = runTest {
        // Replace would leave the app with no projects at all, and it would quietly seed the
        // defaults back — which looks like the restore invented data.
        assertRefused(Failure.NO_PROJECTS) { read(write()) }
    }

    private suspend inline fun assertRefused(expected: Failure, block: () -> Unit) {
        try {
            block()
            fail("expected $expected, but the file was accepted")
        } catch (e: ProjectBackup.BackupException) {
            assertEquals(e.message, expected, e.failure)
        }
    }
}
