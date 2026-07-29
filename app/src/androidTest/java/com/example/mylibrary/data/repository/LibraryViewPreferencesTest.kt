package com.example.mylibrary.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mylibrary.domain.model.LibraryDisplayFieldKey
import com.example.mylibrary.domain.model.LibraryViewMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryViewPreferencesTest {
    @Test
    fun persistsViewModeLayoutAndListFields() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = UserPreferencesRepository(context)
        val fields = setOf(
            LibraryDisplayFieldKey.CREATOR,
            LibraryDisplayFieldKey.CURRENT_STATUS,
            LibraryDisplayFieldKey.TAGS,
            LibraryDisplayFieldKey.dynamic(101),
            LibraryDisplayFieldKey.dynamic(102)
        )
        repository.setLibraryViewMode(LibraryViewMode.LIST)
        repository.setGridColumns(3)
        repository.setCoverColumns(6)
        repository.setTimelineShowCreator(true)
        repository.setTimelineShowRating(true)
        repository.setTimelineShowStatus(true)
        repository.setTimelineShowDuration(false)
        repository.setLibraryShowTotalDuration(false)
        repository.setShowQuoteChapter(false)
        repository.setShowQuotePage(false)
        repository.setListDisplayFields(fields)

        val preferences = repository.libraryViewPreferences.first()
        assertEquals(LibraryViewMode.LIST, preferences.viewMode)
        assertEquals(3, preferences.gridColumns)
        assertEquals(6, preferences.coverColumns)
        assertEquals(true, preferences.timelineShowCreator)
        assertEquals(true, preferences.timelineShowRating)
        assertEquals(true, preferences.timelineShowStatus)
        assertEquals(false, preferences.timelineShowDuration)
        assertEquals(false, preferences.libraryShowTotalDuration)
        assertEquals(false, preferences.showQuoteChapter)
        assertEquals(false, preferences.showQuotePage)
        assertEquals(fields, preferences.listDisplayFields)

        repository.setLibraryViewMode(LibraryViewMode.SHELF)
        repository.setGridColumns(4)
        repository.setCoverColumns(4)
        repository.setTimelineShowCreator(false)
        repository.setTimelineShowRating(false)
        repository.setTimelineShowStatus(false)
        repository.setTimelineShowDuration(true)
        repository.setLibraryShowTotalDuration(true)
        repository.setShowQuoteChapter(true)
        repository.setShowQuotePage(true)
        repository.setListDisplayFields(setOf(LibraryDisplayFieldKey.CREATOR))
    }
}
