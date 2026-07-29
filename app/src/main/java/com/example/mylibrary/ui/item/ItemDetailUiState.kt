package com.example.mylibrary.ui.item

import com.example.mylibrary.domain.model.DynamicFieldValue
import com.example.mylibrary.domain.model.ItemDetail
import com.example.mylibrary.domain.model.LibraryQuote
import com.example.mylibrary.domain.model.LibraryStatus

data class ItemDetailUiState(
    val detail: ItemDetail? = null,
    val quotes: List<LibraryQuote> = emptyList(),
    val visibleFields: List<DynamicFieldValue> = emptyList(),
    val currentStatus: LibraryStatus? = null,
    val isLoading: Boolean = true,
    val isDeleting: Boolean = false,
    val isDeleted: Boolean = false,
    val errorMessage: String? = null
)
