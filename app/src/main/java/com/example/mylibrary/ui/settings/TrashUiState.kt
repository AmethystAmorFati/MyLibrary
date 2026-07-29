package com.example.mylibrary.ui.settings

import com.example.mylibrary.domain.model.TrashItem

data class TrashUiState(
    val items: List<TrashItem> = emptyList(),
    val selectedItemIds: Set<Long> = emptySet(),
    val isLoading: Boolean = true,
    val isOperationRunning: Boolean = false,
    val errorMessage: String? = null
) {
    val isSelectionMode: Boolean
        get() = selectedItemIds.isNotEmpty()

    val pendingDeleteCount: Int
        get() = selectedItemIds.size
}

internal fun toggleTrashSelection(
    selectedItemIds: Set<Long>,
    itemId: Long
): Set<Long> =
    if (itemId in selectedItemIds) selectedItemIds - itemId else selectedItemIds + itemId

internal fun retainExistingTrashSelection(
    selectedItemIds: Set<Long>,
    items: List<TrashItem>
): Set<Long> {
    val existingIds = items.mapTo(mutableSetOf(), TrashItem::id)
    return selectedItemIds.intersect(existingIds)
}
