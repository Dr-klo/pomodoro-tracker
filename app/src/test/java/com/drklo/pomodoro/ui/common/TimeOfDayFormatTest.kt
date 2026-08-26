package com.drklo.pomodoro.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * The end of the logical day is a clock reading, and a clock reading is one of the few things a user
 * will notice instantly if it is wrong: 23:55 shown as 11:55 with no marker is twelve hours out.
 */
class TimeOfDayFormatTest {

    @Test
    fun aTwentyFourHourPhoneGetsTwentyFourHourTime() {
        assertEquals("23:55", formatTimeOfDay(23, 55, is24Hour = true, locale = Locale.US))
        assertEquals("00:00", formatTimeOfDay(0, 0, is24Hour = true, locale = Locale.US))
        // Padded, so the column does not jitter as the value changes.
        assertEquals("09:05", formatTimeOfDay(9, 5, is24Hour = true, locale = Locale.US))
    }

    @Test
    fun aTwelveHourPhoneGetsAMarkerRatherThanAnAmbiguousNumber() {
        val evening = formatTimeOfDay(23, 55, is24Hour = false, locale = Locale.US)

        assertTrue("no 12-hour marker in \"$evening\"", evening.contains("11:55"))
        assertTrue(
            "\"$evening\" does not say which half of the day it is in",
            evening.uppercase(Locale.ROOT).contains("PM")
        )
    }

    @Test
    fun theTwentyFourHourFormIsIndependentOfLanguage() {
        // Digits and a colon, whatever the locale: Locale.ROOT is used deliberately, because a
        // locale with its own numerals would otherwise produce a string the picker cannot echo.
        assertEquals(
            formatTimeOfDay(7, 30, is24Hour = true, locale = Locale.US),
            formatTimeOfDay(7, 30, is24Hour = true, locale = Locale.forLanguageTag("ru"))
        )
    }
}
