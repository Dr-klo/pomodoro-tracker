package com.drklo.pomodoro.timer

import com.drklo.pomodoro.data.model.GlobalSettings
import com.drklo.pomodoro.data.model.Phase
import com.drklo.pomodoro.data.model.Project
import com.drklo.pomodoro.data.model.TimerStatus
import com.drklo.pomodoro.project
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
    fun `a pomodoro that crosses the end-of-day boundary counts towards the new day`() = runTest {
        // Boundary at 02:00, eight pomodoros already done "yesterday". One more is started at 01:50
        // and lands at 02:15 — the row goes to the new day, so the counters must go with it.
        val h = harness(
            settings = GlobalSettings(dayEndHour = 2),
            startAt = LocalDateTime.of(2026, 5, 13, 1, 50),
            stats = FakeStats(mapOf((work.id to "2026-05-12") to 8))
        )
        h.engine.setActiveProject(work)
        runCurrent()
        assertEquals(8, h.state.completedToday)

        runPomodoro(h)

        assertEquals("2026-05-13", h.stats.records.single().dayKey)
        assertEquals("today is a new day, not yesterday's ninth", 1, h.state.completedToday)
        assertEquals(1, h.state.completedInSession)
        assertEquals(1, h.state.pomodorosSinceLongBreak)
    }

    @Test
    fun `the daily goal fires again after the day rolls over`() = runTest {
        val project = project(id = 3, dailyGoal = 1, focusMinutes = 25)
        val h = harness(
            settings = GlobalSettings(dayEndHour = 2),
            startAt = LocalDateTime.of(2026, 5, 13, 1, 50),
            stats = FakeStats(mapOf((project.id to "2026-05-12") to 8))
        )
        val events = mutableListOf<TimerEvent>()
        backgroundScope.launch { h.engine.events.collect { events += it } }
        runCurrent()

        h.engine.setActiveProject(project)
        runCurrent()
        runPomodoro(h, project)

        // On yesterday's inflated count the goal would look long since met and stay silent forever.
        assertEquals(1, events.filterIsInstance<TimerEvent.GoalReached>().size)
    }

    @Test
    fun `a pomodoro inside the same day keeps counting up`() = runTest {
        val h = harness(stats = FakeStats(mapOf((work.id to "2026-05-13") to 3)))
        h.engine.setActiveProject(work)
        runCurrent()

        runPomodoro(h)

        assertEquals(4, h.state.completedToday)
    }

    @Test
    fun `a database that refuses every query does not stop the timer`() = runTest {
        // The engine must survive on whatever scope it is handed, so this one has no handler.
        val engine = TimerEngine(
            settingsSource = FakeSettings(),
            stats = BrokenStats(),
            effects = RecordingFeedback(),
            time = FakeTimeSource(testScheduler),
            scope = backgroundScope
        )
        runCurrent()

        engine.setActiveProject(work)
        runCurrent()
        assertEquals("a failed read must not blank the screen", work.id, engine.state.value.project?.id)

        engine.togglePlayPause()
        advance(25 * 60_000L)

        // The pomodoro is lost from the history — that is the cost — but the session goes on.
        assertEquals(Phase.SHORT_BREAK, engine.state.value.phase)
        assertEquals(1, engine.state.value.completedToday)

        engine.togglePlayPause()
        advance(5 * 60_000L)
        assertEquals(Phase.POMODORO, engine.state.value.phase)
    }

    @Test
    fun `archiving the active project moves the timer to another one, even mid-run`() = runTest {
        val other = project(id = 9, name = "Study", focusMinutes = 15)
        val h = harness()
        h.engine.setActiveProject(work)
        runCurrent()
        h.engine.togglePlayPause()
        advance(60_000)

        h.engine.onActiveProjectArchived(other)
        runCurrent()

        // Refusing here would leave the screen pointed at a project no carousel page can match.
        assertEquals(other.id, h.state.project?.id)
        assertEquals(TimerStatus.IDLE, h.state.status)
        assertEquals(15 * 60, h.state.remainingSeconds)
        assertEquals("the abandoned interval is not recorded", 0, h.stats.records.size)
    }

    @Test
    fun `the abandoned interval stops ticking instead of finishing in the background`() = runTest {
        val other = project(id = 9, name = "Study", focusMinutes = 15)
        val h = harness()
        h.engine.setActiveProject(work)
        runCurrent()
        h.engine.togglePlayPause()
        advance(60_000)
        h.engine.onActiveProjectArchived(other)
        runCurrent()

        // Long past the archived project's original deadline.
        advance(30 * 60_000L)

        assertEquals(0, h.stats.records.size)
        assertEquals(TimerStatus.IDLE, h.state.status)
    }

    @Test
    fun `an autostarted phase never publishes an idle frame`() = runTest {
        val h = harness(GlobalSettings(autostartBreaks = true))
        val frames = mutableListOf<TimerState>()
        // Unconfined resumes the collector at the point of emission, so it sees every published
        // frame instead of only the latest — which is the whole question here.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            h.engine.state.collect { frames += it }
        }
        h.engine.setActiveProject(work)
        runCurrent()
        frames.clear()

        runPomodoro(h)

        assertEquals(Phase.SHORT_BREAK, h.state.phase)
        assertEquals(TimerStatus.RUNNING, h.state.status)
        val idle = frames.filter { it.status == TimerStatus.IDLE }
        // The foreground service stops itself on IDLE; one such frame in the handover and the
        // autostarted break runs with no notification behind it.
        assertTrue("no IDLE frame may appear between two phases, saw ${idle.size}", idle.isEmpty())
    }

    @Test
    fun `a pause just before the end wins over the tick that was about to finish`() = runTest {
        val h = harness()
        h.engine.setActiveProject(work)
        runCurrent()
        h.engine.togglePlayPause()

        advance(25 * 60_000L - 200)
        h.engine.togglePlayPause()
        assertEquals(TimerStatus.PAUSED, h.state.status)

        // The tick loop that was one pass short of completing must step out, not finish the phase.
        advance(60_000)
        assertEquals(TimerStatus.PAUSED, h.state.status)
        assertEquals(Phase.POMODORO, h.state.phase)
        assertEquals(0, h.stats.records.size)
    }

    @Test
    fun `resetting a running interval stops its countdown for good`() = runTest {
        val h = harness()
        h.engine.setActiveProject(work)
        runCurrent()
        h.engine.togglePlayPause()
        advance(60_000)

        h.engine.reset()
        advance(30 * 60_000L)

        assertEquals(TimerStatus.IDLE, h.state.status)
        assertEquals(25 * 60, h.state.remainingSeconds)
        assertEquals(0, h.stats.records.size)
    }

    @Test
    fun `today's count is re-read only after the finished pomodoro is written`() = runTest {
        // A database that answers slowly: the read is queued while the write is still in flight.
        val h = harness(stats = FakeStats(writeDelayMs = 500))
        h.engine.setActiveProject(work)
        runCurrent()
        runPomodoro(h)

        h.engine.setActiveProject(work)
        advance(1_000)

        assertEquals("the bullet must not disappear until the next recount", 1, h.state.completedToday)
    }

    @Test
    fun `re-emitting an unchanged project leaves a scrubbed interval alone`() = runTest {
        val h = harness()
        h.engine.setActiveProject(work)
        runCurrent()
        h.engine.seek(0.4f)
        assertEquals(600, h.state.remainingSeconds)

        // Editing a different project makes Room re-emit this one, unchanged.
        h.engine.refreshActiveProject(work)

        assertEquals("the scrub must survive a list re-emission", 600, h.state.remainingSeconds)
    }

    @Test
    fun `an actually edited duration does reset the interval`() = runTest {
        val h = harness()
        h.engine.setActiveProject(work)
        runCurrent()
        h.engine.seek(0.4f)

        h.engine.refreshActiveProject(work.copy(focusMinutes = 40))

        assertEquals(40 * 60, h.state.totalSeconds)
        assertEquals(40 * 60, h.state.remainingSeconds)
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
