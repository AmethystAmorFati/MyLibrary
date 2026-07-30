package com.example.mylibrary.ui.library

import com.example.mylibrary.domain.model.LibraryTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTagFilterStateTest {
    private val tags = listOf(
        tag(id = 1, name = "A"),
        tag(id = 2, name = "a1", parentId = 1),
        tag(id = 3, name = "B"),
        tag(id = 4, name = "b2", parentId = 3)
    )

    @Test
    fun parentAndChildSelectionsToggleIndependentlyAcrossRootContexts() {
        var selectedIds = emptySet<Long>()

        selectedIds = toggleTagSelection(selectedIds, 1)
        assertEquals(setOf(1L), selectedIds)

        selectedIds = toggleTagSelection(selectedIds, 2)
        assertEquals(setOf(1L, 2L), selectedIds)

        selectedIds = toggleTagSelection(selectedIds, 1)
        assertEquals(setOf(2L), selectedIds)

        selectedIds = toggleTagSelection(selectedIds, 4)
        assertEquals(setOf(2L, 4L), selectedIds)

        selectedIds = toggleTagSelection(selectedIds, 2)
        assertEquals(setOf(4L), selectedIds)
    }

    @Test
    fun selectedTagsUseStableMetadataOrderWithoutDuplicates() {
        val duplicatedMetadata = listOf(tags[2], tags[0], tags[1], tags[0], tags[3])

        assertEquals(
            listOf(3L, 1L, 2L),
            selectedTagsInDisplayOrder(
                tags = duplicatedMetadata,
                selectedIds = setOf(1, 2, 3)
            ).map { it.id }
        )
    }

    @Test
    fun clearingTagsPreservesStatusQueryAndSearchState() {
        val filter = LibraryFilter(
            query = "关键词",
            statusId = 9,
            tagIds = setOf(1, 2),
            isSearchActive = true
        )

        val cleared = filter.withTagIds(emptySet())

        assertTrue(cleared.tagIds.isEmpty())
        assertEquals(filter.query, cleared.query)
        assertEquals(filter.statusId, cleared.statusId)
        assertEquals(filter.isSearchActive, cleared.isSearchActive)
    }

    @Test
    fun emptyResultsDoNotRemoveSelectedTagIds() {
        val state = LibraryUiState(
            items = emptyList(),
            query = "无结果",
            selectedStatusId = 8,
            selectedTagIds = setOf(1, 2)
        )

        assertEquals(setOf(1L, 2L), state.selectedTagIds)
        assertEquals(8L, state.selectedStatusId)
        assertEquals("无结果", state.query)
    }

    private fun tag(
        id: Long,
        name: String,
        parentId: Long? = null
    ) = LibraryTag(
        id = id,
        name = name,
        parentId = parentId,
        sortOrder = id.toInt(),
        enabled = true
    )
}
