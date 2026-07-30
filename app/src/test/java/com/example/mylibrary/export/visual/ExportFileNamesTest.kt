package com.example.mylibrary.export.visual

import org.junit.Assert.assertEquals
import org.junit.Test

class ExportFileNamesTest {
    @Test
    fun calendarNameUsesSafeZeroPaddedMonth() {
        assertEquals(
            "MyLibrary_Calendar_2026_07.png",
            ExportFileNames.calendar(2026, 7)
        )
    }

    @Test
    fun annualNamesUseOnlyTheThreeFormalCategories() {
        assertEquals(
            "MyLibrary_Annual_All_2026.png",
            ExportFileNames.annual(2026, AnnualPosterCategory.ALL)
        )
        assertEquals(
            "MyLibrary_Annual_Books_2026.png",
            ExportFileNames.annual(2026, AnnualPosterCategory.BOOK)
        )
        assertEquals(
            "MyLibrary_Annual_Movies_2026.png",
            ExportFileNames.annual(2026, AnnualPosterCategory.MOVIE)
        )
    }

    @Test
    fun duplicateNameGetsStableSequenceWithoutChangingPngExtension() {
        assertEquals("poster.png", ExportFileNames.withSequence("poster.png", 1))
        assertEquals("poster_2.png", ExportFileNames.withSequence("poster.PNG", 2))
    }
}
