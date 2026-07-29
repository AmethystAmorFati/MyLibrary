package com.example.mylibrary.ui.library

import com.example.mylibrary.domain.model.LibraryDisplayFieldKey
import com.example.mylibrary.domain.model.LibraryViewMode
import com.example.mylibrary.domain.model.LibraryViewPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryViewPreferencesDefaultsTest {
    @Test
    fun layoutDefaultsMatchRoundFourH() {
        val preferences = LibraryViewPreferences()

        assertEquals(4, preferences.gridColumns)
        assertEquals(4, preferences.coverColumns)
        assertFalse(preferences.timelineShowCreator)
        assertFalse(preferences.timelineShowRating)
        assertFalse(preferences.timelineShowStatus)
        assertTrue(preferences.timelineShowDuration)
        assertTrue(preferences.libraryShowTotalDuration)
        assertTrue(preferences.showQuoteChapter)
        assertTrue(preferences.showQuotePage)
        assertEquals(
            setOf(LibraryDisplayFieldKey.CREATOR),
            preferences.listDisplayFields
        )
    }

    @Test
    fun unknownStoredModeFallsBackToGrid() {
        assertEquals(LibraryViewMode.SHELF, LibraryViewMode.fromStorageValue("unknown"))
    }
}
