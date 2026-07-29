package com.example.mylibrary.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeStartupStateTest {
    @Test
    fun initialStateIsLoadingInsteadOfAReadyEmptyTimeline() {
        val state = HomeUiState()

        assertTrue(state.isInitialLoading)
        assertTrue(state.timelineEntries.isEmpty())
        assertTrue(state.activities.isEmpty())
    }

    @Test
    fun initialTimelinePositionIsCalculatedBeforeListStateCreation() {
        assertEquals(
            0,
            TimelineCalendarCoordinator.initialIndex(
                entries = emptyList(),
                today = java.time.LocalDate.of(2026, 7, 26)
            )
        )
    }
}
