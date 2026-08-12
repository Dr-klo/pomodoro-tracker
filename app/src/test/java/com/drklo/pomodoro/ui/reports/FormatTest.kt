package com.drklo.pomodoro.ui.reports

import com.drklo.pomodoro.timer.formatMmSs
import org.junit.Assert.assertEquals
import org.junit.Test

/** The two number formatters every screen renders; both round down, never up. */
class FormatTest {

    private fun focus(seconds: Int) = formatFocus(seconds, "h", "m")

    @Test
    fun `focus time under an hour shows minutes only`() {
        assertEquals("0m", focus(0))
        assertEquals("0m", focus(59))
        assertEquals("1m", focus(60))
        assertEquals("25m", focus(1500))
    }

    @Test
    fun `focus time over an hour keeps the zero minutes visible`() {
        assertEquals("1h 0m", focus(3600))
        assertEquals("1h 1m", focus(3661))
        assertEquals("2h 5m", focus(7500))
    }

    @Test
    fun `seconds are truncated, not rounded`() {
        // 119 s is 1 m 59 s: showing "2m" would let the reported total exceed the time actually spent.
        assertEquals("1m", focus(119))
    }

    @Test
    fun `timer shows mm ss until an hour, then h mm ss`() {
        assertEquals("00:00", formatMmSs(0))
        assertEquals("00:59", formatMmSs(59))
        assertEquals("01:00", formatMmSs(60))
        assertEquals("59:59", formatMmSs(3599))
        assertEquals("1:00:00", formatMmSs(3600))
        assertEquals("1:01:05", formatMmSs(3665))
    }

    @Test
    fun `a negative remainder clamps to zero rather than printing a minus`() {
        assertEquals("00:00", formatMmSs(-5))
    }
}
