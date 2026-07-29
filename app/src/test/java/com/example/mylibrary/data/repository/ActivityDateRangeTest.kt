package com.example.mylibrary.data.repository

import com.example.mylibrary.util.toStartOfDayMillis
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ActivityDateRangeTest {
    @Test
    fun startOnlyCreatesOneDay() {
        val start = date(2026, 7, 1)
        assertEquals(listOf(start), activityDates(start, null))
    }

    @Test
    fun closedRangeIncludesEveryCalendarDay() {
        assertEquals(
            listOf(
                date(2026, 7, 1),
                date(2026, 7, 2),
                date(2026, 7, 3)
            ),
            activityDates(date(2026, 7, 1), date(2026, 7, 3))
        )
    }

    @Test
    fun sameStartAndEndCreatesOneDay() {
        val day = date(2026, 7, 18)
        assertEquals(listOf(day), activityDates(day, day))
    }

    @Test
    fun rangeUsesCalendarDayArithmetic() {
        assertEquals(
            4,
            activityDates(date(2026, 3, 7), date(2026, 3, 10)).size
        )
    }

    @Test
    fun endBeforeStartIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            activityDates(date(2026, 7, 3), date(2026, 7, 1))
        }
    }

    private fun date(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).toStartOfDayMillis()
}
