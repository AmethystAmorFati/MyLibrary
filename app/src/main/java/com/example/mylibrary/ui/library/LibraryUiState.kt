package com.example.mylibrary.ui.library

import com.example.mylibrary.domain.model.LibraryItem
import com.example.mylibrary.domain.model.LibraryStatus
import com.example.mylibrary.domain.model.LibraryTag
import com.example.mylibrary.domain.model.DynamicFieldDefinition
import com.example.mylibrary.domain.model.LibraryViewMode
import com.example.mylibrary.domain.model.LibraryViewPreferences

data class LibraryUiState(
    val items: List<LibraryItem> = emptyList(),
    val statuses: List<LibraryStatus> = emptyList(),
    val tags: List<LibraryTag> = emptyList(),
    val query: String = "",
    val selectedStatusId: Long? = null,
    val selectedTagIds: Set<Long> = emptySet(),
    val dynamicFields: List<DynamicFieldDefinition> = emptyList(),
    val viewMode: LibraryViewMode = LibraryViewMode.SHELF,
    val gridColumns: Int = LibraryViewPreferences().gridColumns,
    val coverColumns: Int = LibraryViewPreferences().coverColumns,
    val listDisplayFields: Set<String> = LibraryViewPreferences().listDisplayFields,
    val showTotalDuration: Boolean = true,
    val isSearchActive: Boolean = false,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)
