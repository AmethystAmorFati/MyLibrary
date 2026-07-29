package com.example.mylibrary.ui.settings

import com.example.mylibrary.domain.model.TrashItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrashSelectionStateTest {
    @Test
    fun togglingTheLastSelectedItemExitsSelectionMode() {
        val selected = toggleTrashSelection(setOf(7L), 7L)
        val state = TrashUiState(
            items = listOf(item(7)),
            selectedItemIds = selected,
            isLoading = false
        )

        assertTrue(selected.isEmpty())
        assertFalse(state.isSelectionMode)
        assertEquals(0, state.pendingDeleteCount)
    }

    @Test
    fun togglingOtherItemsKeepsOneSharedSelectionSet() {
        val selected = toggleTrashSelection(
            toggleTrashSelection(setOf(7L), 8L),
            7L
        )

        assertEquals(setOf(8L), selected)
    }

    @Test
    fun disappearedItemsAreRemovedFromSelection() {
        val retained = retainExistingTrashSelection(
            selectedItemIds = setOf(7L, 8L, 9L),
            items = listOf(item(8), item(9))
        )

        assertEquals(setOf(8L, 9L), retained)
    }

    private fun item(id: Long) = TrashItem(
        id = id,
        typeId = 1,
        typeName = "Book",
        title = "作品$id",
        creator = "作者$id",
        coverPath = null,
        thumbnailPath = null,
        deletedAt = id
    )
}
