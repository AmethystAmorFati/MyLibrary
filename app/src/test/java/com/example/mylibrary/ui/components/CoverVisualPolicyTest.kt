package com.example.mylibrary.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverVisualPolicyTest {
    @Test
    fun calendarCoverHasNoSurfaceBorderOrFailurePlaceholder() {
        val policy = coverVisualPolicy(CoverDisplayMode.CALENDAR)

        assertFalse(policy.showsSurface)
        assertFalse(policy.showsBorder)
        assertFalse(policy.showsPlaceholder)
        assertFalse(policy.showsPlaceholderText)
    }

    @Test
    fun timelineAndEveryLibraryModeHaveNoCoverBorder() {
        listOf(
            CoverDisplayMode.TIMELINE,
            CoverDisplayMode.LIBRARY_GRID,
            CoverDisplayMode.LIBRARY_LIST,
            CoverDisplayMode.LIBRARY_COVER_ONLY
        ).forEach { mode ->
            val policy = coverVisualPolicy(mode)
            assertTrue(policy.showsSurface)
            assertFalse(policy.showsBorder)
            assertTrue(policy.showsPlaceholder)
        }
        assertFalse(
            coverVisualPolicy(CoverDisplayMode.LIBRARY_COVER_ONLY)
                .showsPlaceholderText
        )
    }

    @Test
    fun startupFacingCoversUseSmallDecodeRequests() {
        assertTrue(
            coverDecodeEdge(CoverDisplayMode.CALENDAR) <
                coverDecodeEdge(CoverDisplayMode.TIMELINE)
        )
        assertTrue(
            coverDecodeEdge(CoverDisplayMode.TIMELINE) <
                coverDecodeEdge(CoverDisplayMode.LIBRARY_LIST)
        )
        assertTrue(
            coverDecodeEdge(CoverDisplayMode.LIBRARY_LIST) <=
                coverDecodeEdge(CoverDisplayMode.LIBRARY_GRID)
        )
        assertEquals(192, coverDecodeEdge(CoverDisplayMode.TIMELINE))
        assertEquals(
            coverDecodeEdge(CoverDisplayMode.LIBRARY_GRID),
            coverDecodeEdge(CoverDisplayMode.LIBRARY_LIST)
        )
        assertEquals(
            coverDecodeEdge(CoverDisplayMode.LIBRARY_GRID),
            coverDecodeEdge(CoverDisplayMode.LIBRARY_COVER_ONLY)
        )
    }
}
