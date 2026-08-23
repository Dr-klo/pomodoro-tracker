package com.drklo.pomodoro.ui.reports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

/**
 * Bucket geometry: how many buckets a chart shows, what each one spans, and how paging moves.
 * These are the boundaries every report number is later attributed to, so an off-by-one here
 * silently moves pomodoros between days, weeks or months.
 */
class BucketsTest {

    private val today: LocalDate = LocalDate.of(2026, 5, 13)
    private val locale: Locale = Locale.US

    /**
     * A language *and* a region, as the app itself always builds (LocaleHelper.localeFor). A bare
     * Locale("ru") carries no calendar convention and the JVM falls back to a Sunday week — the very
     * trap this rule exists to avoid.
     */
    private val russianRegion = Locale("ru", "RU")

    @Test
    fun `day window ends today and covers a full week`() {
        val buckets = bucketsFor(Aggregation.DAY, today, page = 0, locale = locale)

        assertEquals(Aggregation.DAY.bucketCount, buckets.size)
        assertEquals(today, buckets.last().endInclusive)
        assertEquals(today.minusDays(6), buckets.first().start)
        assertTrue(buckets.all { it.start == it.endInclusive })
    }

    @Test
    fun `day pages do not overlap`() {
        val current = bucketsFor(Aggregation.DAY, today, page = 0, locale = locale)
        val older = bucketsFor(Aggregation.DAY, today, page = 1, locale = locale)

        assertEquals(current.first().start.minusDays(1), older.last().endInclusive)
        assertFalse(older.any { current.first().contains(it.start) })
    }

    @Test
    fun `week buckets start on the locale's first day and span seven days`() {
        val russian = russianRegion
        val buckets = bucketsFor(Aggregation.WEEK, today, page = 0, locale = russian)

        assertEquals(Aggregation.WEEK.bucketCount, buckets.size)
        assertTrue(buckets.all { it.start.dayOfWeek == DayOfWeek.MONDAY })
        assertTrue(buckets.all { it.start.plusDays(6) == it.endInclusive })
        assertTrue(buckets.last().contains(today))
    }

    @Test
    fun `week pages do not overlap`() {
        val current = bucketsFor(Aggregation.WEEK, today, page = 0, locale = russianRegion)
        val older = bucketsFor(Aggregation.WEEK, today, page = 1, locale = russianRegion)

        assertEquals(current.first().start.minusDays(1), older.last().endInclusive)
    }

    @Test
    fun `month buckets cover whole calendar months including a leap february`() {
        val buckets = bucketsFor(Aggregation.MONTH, LocalDate.of(2024, 5, 13), page = 0, locale = locale)

        assertEquals(Aggregation.MONTH.bucketCount, buckets.size)
        assertTrue(buckets.all { it.start.dayOfMonth == 1 })
        assertTrue(buckets.all { it.endInclusive == it.start.plusMonths(1).minusDays(1) })

        val february = buckets.single { it.start.monthValue == 2 }
        assertEquals(29, february.endInclusive.dayOfMonth)
    }

    @Test
    fun `bucket boundaries are inclusive on both ends`() {
        val bucket = bucketsFor(Aggregation.WEEK, today, page = 0, locale = russianRegion).last()

        assertTrue(bucket.contains(bucket.start))
        assertTrue(bucket.contains(bucket.endInclusive))
        assertFalse(bucket.contains(bucket.start.minusDays(1)))
        assertFalse(bucket.contains(bucket.endInclusive.plusDays(1)))
    }

    @Test
    fun `single day window is the paged day itself`() {
        val window = windowFor(Aggregation.DAY, today, page = 3, locale = locale)

        assertEquals(today.minusDays(3), window.start)
        assertEquals(window.start, window.endInclusive)
    }

    @Test
    fun `week window is the week of the paged date`() {
        val window = windowFor(Aggregation.WEEK, today, page = 1, locale = russianRegion)

        assertEquals(DayOfWeek.MONDAY, window.start.dayOfWeek)
        assertEquals(window.start.plusDays(6), window.endInclusive)
        assertTrue(window.contains(today.minusWeeks(1)))
    }

    @Test
    fun `month window spans the paged calendar month`() {
        val window = windowFor(Aggregation.MONTH, today, page = 2, locale = locale)

        assertEquals(LocalDate.of(2026, 3, 1), window.start)
        assertEquals(LocalDate.of(2026, 3, 31), window.endInclusive)
    }

    @Test
    fun `period label spans the first and last bucket`() {
        val russian = russianRegion
        val buckets = bucketsFor(Aggregation.DAY, today, page = 0, locale = russian)

        assertEquals("07.05 – 13.05", periodLabel(buckets, russian))
    }

    @Test
    fun `period label of nothing is empty`() {
        assertEquals("", periodLabel(emptyList(), locale))
    }

    @Test
    fun `dates follow the reader's own convention`() {
        val russian = russianRegion
        val american = Locale.US

        val ru = periodLabel(bucketsFor(Aggregation.DAY, today, page = 0, locale = russian), russian)
        val us = periodLabel(bucketsFor(Aggregation.DAY, today, page = 0, locale = american), american)

        assertEquals("07.05 – 13.05", ru)
        // Same week, month-first, and with no year cluttering an axis label.
        assertEquals("5/7 – 5/13", us)
    }

    @Test
    fun `the week starts where the reader's calendar starts`() {
        // 13.05.2026 is a Wednesday.
        val ru = bucketsFor(Aggregation.WEEK, today, page = 0, locale = russianRegion).last()
        val us = bucketsFor(Aggregation.WEEK, today, page = 0, locale = Locale.US).last()

        assertEquals(DayOfWeek.MONDAY, ru.start.dayOfWeek)
        assertEquals(LocalDate.of(2026, 5, 11), ru.start)
        // A US reader's week runs Sunday to Saturday, so the same day sits in a window shifted by one.
        assertEquals(DayOfWeek.SUNDAY, us.start.dayOfWeek)
        assertEquals(LocalDate.of(2026, 5, 10), us.start)
    }

    @Test
    fun `a single window agrees with the buckets about where the week starts`() {
        val american = Locale.US
        val window = windowFor(Aggregation.WEEK, today, page = 0, locale = american)
        val lastBucket = bucketsFor(Aggregation.WEEK, today, page = 0, locale = american).last()

        assertEquals(lastBucket.start, window.start)
        assertEquals(lastBucket.endInclusive, window.endInclusive)
    }

    @Test
    fun `the calendar header starts on the same day the buckets do`() {
        for (l in listOf(russianRegion, Locale.US)) {
            val bucketStart = bucketsFor(Aggregation.WEEK, today, page = 0, locale = l).last().start
            assertEquals(
                "calendar columns must line up with the weekly buckets in $l",
                bucketStart.dayOfWeek,
                firstDayOfWeek(l)
            )
        }
    }
}
