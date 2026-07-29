package com.example.mylibrary.ui.home

import com.example.mylibrary.domain.model.LibraryActivity
import com.example.mylibrary.ui.theme.CalendarCellAspectRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarDayVisualTest {
    @Test
    fun everyCalendarCellUsesThreeByFourAspectRatio() {
        assertEquals(3f / 4f, CalendarCellAspectRatio, 0f)
    }

    @Test
    fun dateWithoutCoverShowsNumberSurfaceAndLightBorder() {
        val policy = calendarDayVisualPolicy(
            hasCover = false,
            isToday = false,
            isSelected = false
        )

        assertTrue(policy.showsDateNumber)
        assertTrue(policy.showsEmptySurface)
        assertEquals(CalendarDayBorderStyle.STANDARD, policy.borderStyle)
    }

    @Test
    fun todayAndSelectionOnlyDecorateDatesWithoutCovers() {
        assertEquals(
            CalendarDayBorderStyle.TODAY,
            calendarDayVisualPolicy(
                hasCover = false,
                isToday = true,
                isSelected = false
            ).borderStyle
        )
        assertEquals(
            CalendarDayBorderStyle.SELECTED,
            calendarDayVisualPolicy(
                hasCover = false,
                isToday = true,
                isSelected = true
            ).borderStyle
        )
    }

    @Test
    fun dateWithCoverShowsOnlyBorderlessArtwork() {
        val policy = calendarDayVisualPolicy(
            hasCover = true,
            isToday = true,
            isSelected = true
        )

        assertFalse(policy.showsDateNumber)
        assertFalse(policy.showsEmptySurface)
        assertEquals(CalendarDayBorderStyle.NONE, policy.borderStyle)
    }

    @Test
    fun onlyAUsableThumbnailCountsAsCalendarCover() {
        assertFalse(hasCalendarCover(listOf(activity(null), activity("  "))))
        assertTrue(hasCalendarCover(listOf(activity("thumbnail.webp"))))
    }

    private fun activity(thumbnailPath: String?) = LibraryActivity(
        id = 1,
        date = 0,
        itemId = 1,
        typeId = 1,
        recordId = 1,
        recordCreatedAt = 1,
        title = "Item",
        typeName = "Book",
        thumbnailPath = thumbnailPath
    )
}
