package com.example.mylibrary.ui.item

import com.example.mylibrary.domain.model.LibraryTag

data class ItemTagEditorUiState(
    val tags: List<LibraryTag> = emptyList(),
    val selectedIds: Set<Long> = emptySet(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)
