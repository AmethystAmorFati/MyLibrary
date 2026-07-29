package com.example.mylibrary.ui.settings

import com.example.mylibrary.domain.model.LibraryTag

data class TagManagementUiState(
    val tags: List<LibraryTag> = emptyList(),
    val usageCounts: Map<Long, Int> = emptyMap(),
    val selectedRootId: Long? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
) {
    val rootTags: List<LibraryTag>
        get() = tags
            .asSequence()
            .filter { it.enabled && it.parentId == null }
            .sortedWith(tagOrder)
            .toList()

    val selectedRoot: LibraryTag?
        get() = rootTags.firstOrNull { it.id == selectedRootId }
            ?: rootTags.firstOrNull()

    val selectedChildren: List<LibraryTag>
        get() = selectedRoot?.let { root ->
            tags
                .asSequence()
                .filter { it.enabled && it.parentId == root.id }
                .sortedWith(tagOrder)
                .toList()
        }.orEmpty()

    fun allChildrenOf(rootId: Long): List<LibraryTag> =
        tags.filter { it.parentId == rootId }

    fun usageCount(tagId: Long): Int = usageCounts[tagId] ?: 0

    private companion object {
        val tagOrder = compareBy<LibraryTag> { it.sortOrder }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            .thenBy { it.id }
    }
}
