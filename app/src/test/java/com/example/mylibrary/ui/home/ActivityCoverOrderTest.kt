package com.example.mylibrary.ui.home

import com.example.mylibrary.domain.model.LibraryActivity
import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityCoverOrderTest {
    @Test
    fun latestFourAreKeptInVisualPriorityOrder() {
        val activities = (1L..5L).map { sequence ->
            activity(
                id = sequence,
                itemId = sequence,
                recordId = sequence,
                createdAt = sequence * 100
            )
        }.shuffled()

        val ordered = orderedActivitiesForCoverStack(activities)

        assertEquals(listOf(500L, 400L, 300L, 200L), ordered.map { it.recordCreatedAt })
    }

    @Test
    fun recordIdAndActivityIdProvideStableTieBreakers() {
        val activities = listOf(
            activity(id = 2, itemId = 2, recordId = 2, createdAt = 100),
            activity(id = 1, itemId = 1, recordId = 1, createdAt = 100)
        )

        val ordered = orderedActivitiesForCoverStack(activities)

        assertEquals(listOf(2L, 1L), ordered.map { it.recordId })
    }

    @Test
    fun oneThroughFourCoversUseStableRecognizableLayouts() {
        assertEquals(CalendarCoverLayout.EMPTY, calendarCoverLayout(0))
        assertEquals(CalendarCoverLayout.SINGLE, calendarCoverLayout(1))
        assertEquals(CalendarCoverLayout.TWO_ROWS, calendarCoverLayout(2))
        assertEquals(CalendarCoverLayout.TWO_OVER_ONE, calendarCoverLayout(3))
        assertEquals(CalendarCoverLayout.TWO_BY_TWO, calendarCoverLayout(4))
        assertEquals(CalendarCoverLayout.TWO_BY_TWO, calendarCoverLayout(12))
    }

    @Test
    fun moreThanFourActivitiesNeverReachTheLayout() {
        val ordered = (1L..8L).map { sequence ->
            activity(
                id = sequence,
                itemId = sequence,
                recordId = sequence,
                createdAt = sequence
            )
        }.let(::orderedActivitiesForCoverStack)

        assertEquals(4, ordered.size)
        assertEquals(listOf(8L, 7L, 6L, 5L), ordered.map { it.recordId })
    }

    private fun activity(
        id: Long,
        itemId: Long,
        recordId: Long,
        createdAt: Long
    ) = LibraryActivity(
        id = id,
        date = 0,
        itemId = itemId,
        typeId = 1,
        recordId = recordId,
        recordCreatedAt = createdAt,
        title = "Item $itemId",
        typeName = "Book",
        thumbnailPath = null
    )
}
