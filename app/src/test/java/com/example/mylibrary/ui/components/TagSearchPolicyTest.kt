package com.example.mylibrary.ui.components

import com.example.mylibrary.domain.model.LibraryTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TagSearchPolicyTest {
    private val tags = listOf(
        tag(id = 1, name = "A"),
        tag(id = 2, name = "a1", parentId = 1),
        tag(id = 3, name = "主题a", parentId = 1),
        tag(id = 4, name = "B"),
        tag(id = 5, name = "a1", parentId = 4)
    )

    @Test
    fun blankQueryShowsOnlyActiveRootAndItsChildren() {
        val result = contextualTagSearch(tags, activeRootId = 1, query = "", allowCreation = true)

        assertEquals(listOf("A", "a1", "主题a"), result.visibleTags.map { it.name })
        assertFalse(result.canCreate)
    }

    @Test
    fun partialQuerySearchesCurrentContextAndOffersTrimmedCreation() {
        val result = contextualTagSearch(
            tags,
            activeRootId = 1,
            query = "  1  ",
            allowCreation = true
        )

        assertEquals(listOf("a1"), result.visibleTags.map { it.name })
        assertEquals("1", result.normalizedQuery)
        assertTrue(result.canCreate)
    }

    @Test
    fun exactChildMatchShowsExistingTagWithoutCreation() {
        val result = contextualTagSearch(tags, activeRootId = 1, query = "A1", allowCreation = true)

        assertEquals(listOf(2L), result.visibleTags.map { it.id })
        assertFalse(result.canCreate)
    }

    @Test
    fun exactRootMatchShowsRootWithoutCreatingSameNamedChild() {
        val result = contextualTagSearch(tags, activeRootId = 1, query = "a", allowCreation = true)

        assertEquals(listOf("A"), result.visibleTags.map { it.name })
        assertFalse(result.canCreate)
    }

    @Test
    fun filterModeNeverOffersCreation() {
        val result = contextualTagSearch(tags, activeRootId = 1, query = "新标签", allowCreation = false)

        assertTrue(result.visibleTags.isEmpty())
        assertFalse(result.canCreate)
    }

    @Test
    fun whitespaceOnlyQueryShowsContextWithoutCreation() {
        val result = contextualTagSearch(tags, activeRootId = 1, query = "   ", allowCreation = true)

        assertEquals(listOf(1L, 2L, 3L), result.visibleTags.map { it.id })
        assertFalse(result.canCreate)
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
