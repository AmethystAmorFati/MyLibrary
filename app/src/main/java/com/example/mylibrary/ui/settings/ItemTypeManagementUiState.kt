package com.example.mylibrary.ui.settings

import com.example.mylibrary.domain.model.ItemType

data class ItemTypeManagementUiState(
    val types: List<ItemType> = emptyList(),
    val usageCounts: Map<Long, Int> = emptyMap(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
) {
    fun usageCount(typeId: Long): Int = usageCounts[typeId] ?: 0
}
