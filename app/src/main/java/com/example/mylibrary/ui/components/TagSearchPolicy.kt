package com.example.mylibrary.ui.components

import com.example.mylibrary.domain.model.LibraryTag

internal data class ContextualTagSearchResult(
    val visibleTags: List<LibraryTag>,
    val normalizedQuery: String,
    val canCreate: Boolean
)

internal fun contextualTagSearch(
    tags: List<LibraryTag>,
    activeRootId: Long?,
    query: String,
    allowCreation: Boolean
): ContextualTagSearchResult {
    val normalizedQuery = query.trim()
    val activeRoot = tags.firstOrNull {
        it.id == activeRootId && it.parentId == null
    }
    if (activeRoot == null) {
        return ContextualTagSearchResult(
            visibleTags = emptyList(),
            normalizedQuery = normalizedQuery,
            canCreate = false
        )
    }

    val tagsInContext = buildList {
        add(activeRoot)
        addAll(tags.filter { it.parentId == activeRoot.id })
    }
    val queryMatchesRootExactly = activeRoot.name.equals(
        normalizedQuery,
        ignoreCase = true
    )
    val visibleTags = when {
        normalizedQuery.isEmpty() -> tagsInContext
        queryMatchesRootExactly -> listOf(activeRoot)
        else -> tagsInContext.filter {
            it.name.contains(normalizedQuery, ignoreCase = true)
        }
    }
    val hasExactMatch = tagsInContext.any {
        it.name.equals(normalizedQuery, ignoreCase = true)
    }

    return ContextualTagSearchResult(
        visibleTags = visibleTags,
        normalizedQuery = normalizedQuery,
        canCreate = allowCreation &&
            normalizedQuery.isNotEmpty() &&
            !hasExactMatch
    )
}
