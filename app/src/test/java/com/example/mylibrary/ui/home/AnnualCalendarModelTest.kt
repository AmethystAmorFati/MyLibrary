package com.example.mylibrary.ui.home

import com.example.mylibrary.domain.model.LibraryActivity
import com.example.mylibrary.util.toStartOfDayMillis
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnualCalendarModelTest {
    @Test
    fun annualModelBuildsTwelveStableMonthItems() {
        val months = buildAnnualCalendarMonths(2026, emptyList())

        assertEquals(12, months.size)
        assertEquals(YearMonth.of(2026, 1), months.first().yearMonth)
        assertEquals(YearMonth.of(2026, 12), months.last().yearMonth)
        assertTrue(months.all { month -> month.weeks.all { it.size == 7 } })
    }

    @Test
    fun dailyCoverActivitiesArePreFilteredSortedAndCapped() {
        val date = LocalDate.of(2026, 7, 27)
        val activities = (1L..6L).map { id ->
            activity(
                id = id,
                itemId = id,
                recordId = id,
                recordCreatedAt = id * 100,
                date = date,
                thumbnailPath = "cover-$id.webp"
            )
        } + activity(
            id = 99L,
            itemId = 99L,
            recordId = 99L,
            recordCreatedAt = 10_000L,
            date = date,
            thumbnailPath = null
        )

        val july = buildAnnualCalendarMonths(2026, activities)
            .single { it.yearMonth == YearMonth.of(2026, 7) }
        val day = july.weeks.flatten().filterNotNull()
            .single { it.date == date }

        assertEquals(4, day.coverActivities.size)
        assertEquals(listOf(6L, 5L, 4L, 3L), day.coverActivities.map { it.itemId })
    }

    private fun activity(
        id: Long,
        itemId: Long,
        recordId: Long,
        recordCreatedAt: Long,
        date: LocalDate,
        thumbnailPath: String?
    ) = LibraryActivity(
        id = id,
        date = date.toStartOfDayMillis(),
        itemId = itemId,
        typeId = 1L,
        recordId = recordId,
        recordCreatedAt = recordCreatedAt,
        title = "Item $itemId",
        typeName = "Book",
        thumbnailPath = thumbnailPath
    )
}
