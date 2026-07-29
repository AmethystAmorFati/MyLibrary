package com.example.mylibrary.ui.settings

import com.example.mylibrary.domain.model.DynamicFieldDefinition
import com.example.mylibrary.domain.model.LibraryViewPreferences

data class LayoutSettingsUiState(
    val preferences: LibraryViewPreferences = LibraryViewPreferences(),
    val dynamicFields: List<DynamicFieldDefinition> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)
