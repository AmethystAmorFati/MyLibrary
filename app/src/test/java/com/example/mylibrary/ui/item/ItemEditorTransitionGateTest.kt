package com.example.mylibrary.ui.item

import androidx.lifecycle.Lifecycle
import com.example.mylibrary.domain.model.ItemType
import com.example.mylibrary.ui.navigation.latchDestinationEnterCompleted
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemEditorTransitionGateTest {
    private val loadedState = ItemEditorUiState(
        types = listOf(ItemType(id = 1L, name = "书籍", sortOrder = 0)),
        selectedTypeId = 1L,
        isLoading = false
    )

    @Test
    fun destinationCompletionWaitsForResumedAndThenStaysLatched() {
        val beforeResume = latchDestinationEnterCompleted(
            wasCompleted = false,
            currentState = Lifecycle.State.STARTED
        )
        val atResume = latchDestinationEnterCompleted(
            wasCompleted = beforeResume,
            currentState = Lifecycle.State.RESUMED
        )
        val duringReturn = latchDestinationEnterCompleted(
            wasCompleted = atResume,
            currentState = Lifecycle.State.STARTED
        )
        val afterDestroy = latchDestinationEnterCompleted(
            wasCompleted = duringReturn,
            currentState = Lifecycle.State.DESTROYED
        )

        assertFalse(beforeResume)
        assertTrue(atResume)
        assertTrue(duringReturn)
        assertTrue(afterDestroy)
    }

    @Test
    fun loadedDataDoesNotMountFormBeforeDestinationResume() {
        assertEquals(
            EditorContentMode.LOADING,
            editorContentMode(
                state = loadedState,
                destinationEnterCompleted = false
            )
        )
    }

    @Test
    fun resumedDestinationStillWaitsForData() {
        assertEquals(
            EditorContentMode.LOADING,
            editorContentMode(
                state = ItemEditorUiState(isLoading = true),
                destinationEnterCompleted = true
            )
        )
    }

    @Test
    fun formMountsOnlyAfterDataAndDestinationAreReady() {
        val contentMode = editorContentMode(
            state = loadedState,
            destinationEnterCompleted = true
        )

        assertEquals(EditorContentMode.FORM, contentMode)
        assertTrue(editorSaveEnabled(loadedState, contentMode))
    }

    @Test
    fun saveRemainsDisabledWhileStableLoadingShellIsShown() {
        val contentMode = editorContentMode(
            state = loadedState,
            destinationEnterCompleted = false
        )

        assertFalse(editorSaveEnabled(loadedState, contentMode))
    }

    @Test
    fun initialLoadFailureAppearsOnlyAfterDestinationResume() {
        val failedState = ItemEditorUiState(
            isLoading = false,
            errorMessage = "作品不存在"
        )

        assertEquals(
            EditorContentMode.LOADING,
            editorContentMode(failedState, destinationEnterCompleted = false)
        )
        assertEquals(
            EditorContentMode.ERROR,
            editorContentMode(failedState, destinationEnterCompleted = true)
        )
    }
}
