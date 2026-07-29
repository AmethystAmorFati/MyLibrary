package com.example.mylibrary.ui.settings

import com.example.mylibrary.domain.model.DynamicFieldDefinition
import com.example.mylibrary.domain.model.ItemType
import com.example.mylibrary.domain.model.LibraryStatus
import com.example.mylibrary.backup.model.ImportPreview

data class SettingsUiState(
    val types: List<ItemType> = emptyList(),
    val statuses: List<LibraryStatus> = emptyList(),
    val dynamicFields: List<DynamicFieldDefinition> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val backupOperation: SettingsBackupOperation? = null,
    val importPreview: ImportPreview? = null,
    val backupMessage: String? = null
) {
    val isBackupBusy: Boolean
        get() = backupOperation != null
}

enum class SettingsBackupOperation {
    VALIDATING,
    EXPORTING,
    IMPORTING,
    PREPARING_REPORT
}
