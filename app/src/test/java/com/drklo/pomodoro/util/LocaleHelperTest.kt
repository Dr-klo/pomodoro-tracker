package com.drklo.pomodoro.util

import com.drklo.pomodoro.ui.reports.firstDayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.util.Locale

/**
 * The app lets the user pick a UI language independently of the system, and that choice must not
 * drag the calendar with it. Region decides where a week starts; language does not.
 */
class LocaleHelperTest {

    @Test
    fun `the chosen language keeps the device's region`() {
        val locale = LocaleHelper.localeFor("en", systemRegion = "RU")

        assertEquals("en", locale.language)
        assertEquals("RU", locale.country)
    }

    @Test
    fun `a russian phone keeps monday weeks in either language`() {
        assertEquals(DayOfWeek.MONDAY, firstDayOfWeek(LocaleHelper.localeFor("ru", "RU")))
        // English UI on the same phone: the text changes, the calendar does not.
        assertEquals(DayOfWeek.MONDAY, firstDayOfWeek(LocaleHelper.localeFor("en", "RU")))
    }

    @Test
    fun `an american phone keeps sunday weeks in either language`() {
        assertEquals(DayOfWeek.SUNDAY, firstDayOfWeek(LocaleHelper.localeFor("en", "US")))
        assertEquals(DayOfWeek.SUNDAY, firstDayOfWeek(LocaleHelper.localeFor("ru", "US")))
    }

    @Test
    fun `a language tag on its own does not carry a calendar`() {
        // Why the region is threaded through at all: the JVM answers "Sunday" for a bare "ru",
        // so building the locale from the language alone would hand Russian users an American week.
        assertEquals(DayOfWeek.SUNDAY, firstDayOfWeek(Locale.forLanguageTag("ru")))
    }
}
