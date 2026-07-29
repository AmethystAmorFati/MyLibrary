package com.example.mylibrary.ui.item

import com.example.mylibrary.domain.model.ItemType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemEditorLoadingStateTest {
    @Test
    fun loadingStateIsNotTreatedAsFailedInitialLoad() {
        val state = ItemEditorUiState(
            isLoading = true,
            errorMessage = "still loading"
        )

        assertFalse(state.isInitialLoadFailed)
    }

    @Test
    fun initialFailureRequiresNoLoadedEditorMetadata() {
        val state = ItemEditorUiState(
            isLoading = false,
            errorMessage = "load failed"
        )

        assertTrue(state.isInitialLoadFailed)
    }

    @Test
    fun laterErrorsKeepTheInitializedFormAvailable() {
        val state = ItemEditorUiState(
            types = listOf(ItemType(id = 1L, name = "Book", sortOrder = 0)),
            selectedTypeId = 1L,
            isLoading = false,
            errorMessage = "save failed"
        )

        assertFalse(state.isInitialLoadFailed)
    }
}
