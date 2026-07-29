package com.example.mylibrary.ui.settings

import com.example.mylibrary.domain.model.LibraryStatus
import com.example.mylibrary.domain.model.StatusScope

data class StatusManagementUiState(
    val statuses: List<LibraryStatus> = emptyList(),
    val selectedScope: StatusScope = StatusScope.ITEM,
    val usageCounts: Map<Long, Int> = emptyMap(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
) {
    fun usageCount(statusId: Long): Int = usageCounts[statusId] ?: 0
}
