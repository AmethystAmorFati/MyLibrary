package com.example.mylibrary.ui.library

import com.example.mylibrary.domain.model.LibraryItem
import com.example.mylibrary.domain.model.LibraryViewMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryPresentationPolicyTest {
    @Test
    fun viewSwitchUsesNoAlphaTransitionAndKeepsNonEmptyContent() {
        assertFalse(LibraryPresentationPolicy.usesAlphaTransition)
        assertFalse(
            LibraryPresentationPolicy.shouldShowEmpty(
                isLoading = false,
                itemCount = 3
            )
        )
    }

    @Test
    fun loadingFilterResultNeverFlashesEmptyState() {
        assertFalse(
            LibraryPresentationPolicy.shouldShowEmpty(
                isLoading = true,
                itemCount = 0
            )
        )
        assertTrue(
            LibraryPresentationPolicy.shouldShowEmpty(
                isLoading = false,
                itemCount = 0
            )
        )
    }

    @Test
    fun lazyItemKeyIsTheStableDatabaseId() {
        val item = LibraryItem(
            id = 42,
            typeId = 1,
            typeName = "Book",
            title = "Stable",
            creator = "",
            coverPath = null,
            thumbnailPath = null,
            createdTime = 1,
            updatedTime = 1,
            currentStatusId = null,
            currentStatusName = null,
            latestRatingHalfStars = null
        )

        assertEquals(42L, libraryItemKey(item))
    }

    @Test
    fun threeViewModesUseExactUserFacingNamesAndStableStorageValues() {
        assertEquals(
            listOf("网格", "列表", "纯图"),
            LibraryViewMode.entries.map(LibraryViewMode::displayName)
        )
        assertEquals(
            listOf("shelf", "list", "cover"),
            LibraryViewMode.entries.map(LibraryViewMode::storageValue)
        )
    }
}
