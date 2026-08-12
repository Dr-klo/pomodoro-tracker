package com.drklo.pomodoro.timer

import com.drklo.pomodoro.data.model.GlobalSettings
import com.drklo.pomodoro.data.model.Phase
import com.drklo.pomodoro.data.model.Project
import com.drklo.pomodoro.data.model.TimerStatus
import com.drklo.pomodoro.project
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * The engine driven by virtual time: no device, no real waiting. A 25-minute pomodoro runs in
 * microseconds, which is the whole point of the [TimeSource] seam.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimerEngineTest {

    private val work = project(id = 1, name = "Work", focusMinutes = 25, shortBreakMinutes = 5)

    /** Everything a test needs to observe: the engine plus the fakes it was built from. */
    private class Harness(
        scope: TestScope,
        settings: GlobalSettings,
        startAt: LocalDateTime,
        val stats: FakeStats
    ) {
        val feedback = RecordingFeedback()
        val time = FakeTimeSource(scope.testScheduler, startAt)
        val engine = TimerEngine(
            settingsSource = FakeSettings(settings),
            stats = stats,
            effects = feedback,
            time = time,
            scope = scope.backgroundScope
        )

        val state get() = engine.state.value
    }

    private fun TestScope.harness(
        settings: GlobalSettings = GlobalSettings(),
        startAt: LocalDateTime = LocalDateTime.of(2026, 5, 13, 9, 0),
        stats: FakeStats = FakeStats()
    ): Harness = Harness(this, settings, startAt, stats).also { runCurrent() }

    /** Advances virtual time and lets the task scheduled exactly at the new instant run. */
    private fun TestScope.advance(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    private fun TestScope.runPomodoro(h: Harness, project: Project = work) {
        h.engine.togglePlayPause()
        advance(project.focusMinutes * 60_000L)
    }

    @Test
    fun `a pomodoro counts down and hands over to the short break`() = runTest {
        val h = harness()
        h.engine.setActiveProject(work)
        runCurrent()

        assertEquals(TimerStatus.IDLE, h.state.status)
        assertEquals(25 * 60, h.state.remainingSeconds)

        h.engine.togglePlayPause()
        assertEquals(TimerStatus.RUNNING, h.state.status)

        advance(60_000)
        assertEquals(24 * 60, h.state.remainingSeconds)

        advance(24 * 60_000L)
        assertEquals(Phase.SHORT_BREAK, h.state.phase)
        assertEquals(TimerStatus.IDLE, h.state.status)
        assertTrue("the user has not started the break yet", h.state.awaitingNext)
        assertEquals(5 * 60, h.state.remainingSeconds)
        assertEquals(1, h.state.completedToday)
        assertEquals(1, h.state.completedInSession)
    }

    @Test
    fun `a finished pomodoro is written exactly once, no matter how long nobody looks`() = runTest {
        val h = harness()
        h.engine.setActiveProject(work)
        runCurrent()
        runPomodoro(h)

        // Idling well past the end must not re-trigger the completed phase.
        advance(30 * 60_000L)

        assertEquals(1, h.stats.records.size)
        assertEquals(1, h.state.completedToday)
        assertEquals(1, h.feedback.ends)
    }

    @Test
    fun `the log entry spans exactly the interval that was worked`() = runTest {
        val h = harness()
        h.engine.setActiveProject(work)
        runCurrent()
        runPomodoro(h)

        val record = h.stats.records.single()
        assertEquals(work.id, record.projectId)
        assertEquals(25 * 60, record.durationSeconds)
        assertEquals(h.time.wallClockMs(), record.endEpochMs)
        assertEquals(25 * 60_000L, record.endEpochMs - record.startEpochMs)
    }

    @Test
    fun `a break is not a pomodoro`() = runTest {
        val h = harness()
        h.engine.setActiveProject(work)
        runCurrent()
        runPomodoro(h)

        h.engine.togglePlayPause()
        advance(5 * 60_000L)

        assertEquals(Phase.POMODORO, h.state.phase)
        assertEquals(1, h.stats.records.size)
        assertEquals(1, h.state.completedToday)
    }

    @Test
    fun `the long break arrives on the configured interval and resets the counter`() = runTest {
        val project = project(
            id = 2,
            focusMinutes = 25,
            longBreakEnabled = true,
            longBreakMinutes = 15,
            longBreakInterval = 2
        )
        val h = harness(GlobalSettings(autostartBreaks = true, autostartPomodoros = true))
        h.engine.setActiveProject(project)
        runCurrent()

        h.engine.togglePlayPause()
        advance(25 * 60_000L)
        assertEquals(Phase.SHORT_BREAK, h.state.phase)
        assertEquals(1, h.state.pomodorosSinceLongBreak)

        // Autostart carries it through the break and straight into the second pomodoro.
        advance(5 * 60_000L + 25 * 60_000L)
        assertEquals(Phase.LONG_BREAK, h.state.phase)
        assertEquals(15 * 60, h.state.remainingSeconds)
        assertEquals(0, h.state.pomodorosSinceLongBreak)
        assertEquals(2, h.stats.records.size)
    }

    @Test
    fun `autostart starts the next phase by itself, a plain finish waits for the user`() = runTest {
        val auto = harness(GlobalSettings(autostartBreaks = true))
        auto.engine.setActiveProject(work)
        runCurrent()
        runPomodoro(auto)
        assertEquals(TimerStatus.RUNNING, auto.state.status)
        assertEquals(Phase.SHORT_BREAK, auto.state.phase)

        val manual = harness()
        manual.engine.setActiveProject(work)
        runCurrent()
        runPomodoro(manual)
        assertEquals(TimerStatus.IDLE, manual.state.status)
    }

    @Test
    fun `pausing freezes the remaining time and resuming continues from it`() = runTest {
        val h = harness()
        h.engine.setActiveProject(work)
        runCurrent()
        h.engine.togglePlayPause()

        advance(10 * 60_000L)
        h.engine.togglePlayPause()
        assertEquals(TimerStatus.PAUSED, h.state.status)
        assertEquals(15 * 60, h.state.remainingSeconds)

        // Time passing while parked must not eat into the interval.
        advance(60 * 60_000L)
        assertEquals(15 * 60, h.state.remainingSeconds)

        h.engine.togglePlayPause()
        advance(15 * 60_000L)
        assertEquals(Phase.SHORT_BREAK, h.state.phase)
        assertEquals(1, h.stats.records.size)
    }

    @Test
    fun `seeking a running interval moves its deadline, not just the label`() = runTest {
        val h = harness()
        h.engine.setActiveProject(work)
        runCurrent()
        h.engine.togglePlayPause()

        h.engine.seek(0.5f)
        assertEquals(750, h.state.remainingSeconds)

        advance(750_000)
        assertEquals(Phase.SHORT_BREAK, h.state.phase)
    }

    @Test
    fun `a running timer refuses to switch projects`() = runTest {
        val other = project(id = 9, name = "Study")
        val h = harness()
        h.engine.setActiveProject(work)
        runCurrent()
        h.engine.togglePlayPause()
        advance(60_000)

        h.engine.setActiveProject(other)
        runCurrent()

        assertEquals(work.id, h.state.project?.id)
        assertEquals(TimerStatus.RUNNING, h.state.status)
    }

    @Test
    fun `a pomodoro finished after midnight still belongs to the previous logical day`() = runTest {
        // Owner's boundary is 01:00, so a pomodoro run from 00:15 to 00:40 is yesterday's.
        val h = harness(
            settings = GlobalSettings(dayEndHour = 1),
            startAt = LocalDateTime.of(2026, 5, 13, 0, 15)
        )
        h.engine.setActiveProject(work)
        runCurrent()
        runPomodoro(h)

        assertEquals("2026-05-12", h.stats.records.single().dayKey)
    }

    @Test
    fun `selecting a project restores today's progress from stats`() = runTest {
        val h = harness(
            stats = FakeStats(mapOf((work.id to "2026-05-13") to 3))
        )
        h.engine.setActiveProject(work)
        runCurrent()

        assertEquals(3, h.state.completedToday)
        // Four per session: the fourth bullet is still pending, so three are filled.
        assertEquals(3, h.state.completedInSession)
    }

    @Test
    fun `starting by hand dings and buzzes once`() = runTest {
        val h = harness()
        h.engine.setActiveProject(work)
        runCurrent()

        h.engine.togglePlayPause()
        assertEquals(1, h.feedback.starts)
        assertEquals(1, h.feedback.vibrations)

        advance(25 * 60_000L)
        assertEquals(1, h.feedback.ends)
        assertEquals(2, h.feedback.vibrations)
    }
}
