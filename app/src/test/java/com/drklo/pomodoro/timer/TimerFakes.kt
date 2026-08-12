package com.drklo.pomodoro.timer

import com.drklo.pomodoro.data.model.GlobalSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * A clock wired to the test scheduler's virtual time: advancing coroutines advances the clock by
 * exactly the same amount. Without this the engine would read a real clock that never moves while
 * virtual time races ahead, and every countdown would look frozen.
 *
 * [BOOT_OFFSET_MS] keeps the monotonic clock far away from the wall clock, so code that confuses
 * the two fails loudly instead of accidentally agreeing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FakeTimeSource(
    private val scheduler: TestCoroutineScheduler,
    private val start: LocalDateTime = LocalDateTime.of(2026, 5, 13, 9, 0)
) : TimeSource {

    private val startWallMs = start.toInstant(ZoneOffset.UTC).toEpochMilli()

    override fun elapsedRealtimeMs(): Long = BOOT_OFFSET_MS + scheduler.currentTime

    override fun wallClockMs(): Long = startWallMs + scheduler.currentTime

    override fun now(): LocalDateTime = start.plusNanos(scheduler.currentTime * NANOS_PER_MS)

    private companion object {
        const val BOOT_OFFSET_MS = 7_200_000L
        const val NANOS_PER_MS = 1_000_000L
    }
}

/** Settings the test can change mid-run, exactly as DataStore would emit them. */
class FakeSettings(initial: GlobalSettings = GlobalSettings()) : SettingsSource {
    private val state = MutableStateFlow(initial)
    override val settings: Flow<GlobalSettings> = state

    fun emit(value: GlobalSettings) {
        state.value = value
    }
}

/** In-memory stats: what was written, and what a fresh session would read back. */
class FakeStats(initialCounts: Map<Pair<Long, String>, Int> = emptyMap()) : PomodoroStats {

    data class Record(
        val projectId: Long,
        val dayKey: String,
        val startEpochMs: Long,
        val endEpochMs: Long,
        val durationSeconds: Int
    )

    val records = mutableListOf<Record>()
    private val counts = initialCounts.toMutableMap()

    override suspend fun completedCount(projectId: Long, dayKey: String): Int =
        counts[projectId to dayKey] ?: 0

    override suspend fun recordCompletedPomodoro(
        projectId: Long,
        dayKey: String,
        startEpochMs: Long,
        endEpochMs: Long,
        durationSeconds: Int
    ) {
        records += Record(projectId, dayKey, startEpochMs, endEpochMs, durationSeconds)
        counts[projectId to dayKey] = (counts[projectId to dayKey] ?: 0) + 1
    }
}

/** Stats that always fail, standing in for a corrupted or unwritable database. */
class BrokenStats : PomodoroStats {
    override suspend fun completedCount(projectId: Long, dayKey: String): Int =
        error("database unavailable")

    override suspend fun recordCompletedPomodoro(
        projectId: Long,
        dayKey: String,
        startEpochMs: Long,
        endEpochMs: Long,
        durationSeconds: Int
    ): Unit = error("database unavailable")
}

/** Counts the feedback calls so tests can assert a ding happens once, not twice. */
class RecordingFeedback : PhaseFeedback {
    var starts = 0
        private set
    var ends = 0
        private set
    var vibrations = 0
        private set

    override fun playStart() {
        starts++
    }

    override fun playEnd() {
        ends++
    }

    override fun vibrate() {
        vibrations++
    }
}
