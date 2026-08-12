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
    fun `week buckets start on monday and span seven days`() {
        val buckets = bucketsFor(Aggregation.WEEK, today, page = 0, locale = locale)

        assertEquals(Aggregation.WEEK.bucketCount, buckets.size)
        assertTrue(buckets.all { it.start.dayOfWeek == DayOfWeek.MONDAY })
        assertTrue(buckets.all { it.start.plusDays(6) == it.endInclusive })
        assertTrue(buckets.last().contains(today))
    }

    @Test
    fun `week pages do not overlap`() {
        val current = bucketsFor(Aggregation.WEEK, today, page = 0, locale = locale)
        val older = bucketsFor(Aggregation.WEEK, today, page = 1, locale = locale)

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
        val bucket = bucketsFor(Aggregation.WEEK, today, page = 0, locale = locale).last()

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
    fun `week window is the monday-based week of the paged date`() {
        val window = windowFor(Aggregation.WEEK, today, page = 1, locale = locale)

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
        val buckets = bucketsFor(Aggregation.DAY, today, page = 0, locale = locale)

        assertEquals("07.05 – 13.05", periodLabel(buckets))
    }

    @Test
    fun `period label of nothing is empty`() {
        assertEquals("", periodLabel(emptyList()))
    }
}
