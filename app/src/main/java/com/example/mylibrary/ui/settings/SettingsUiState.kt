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
    val backupMessage: String? = null,
    val visualExportOperation: SettingsVisualExportOperation? = null,
    val visualExportSafRequest: VisualExportSafRequest? = null,
    val visualExportMessage: String? = null,
    val reportExportOperation: SettingsReportExportOperation? = null,
    val reportExportSafRequest: ReportExportSafRequest? = null,
    val reportExportMessage: String? = null
) {
    val isBackupBusy: Boolean
        get() = backupOperation != null

    val isVisualExportBusy: Boolean
        get() = visualExportOperation != null

    val isReportExportBusy: Boolean
        get() = reportExportOperation != null

    val isSettingsOperationBusy: Boolean
        get() = isBackupBusy || isVisualExportBusy || isReportExportBusy
}

enum class SettingsBackupOperation {
    VALIDATING,
    EXPORTING,
    IMPORTING
}

enum class SettingsVisualExportOperation {
    GENERATING,
    WAITING_FOR_DESTINATION,
    SAVING
}

enum class SettingsReportExportOperation {
    RESOLVING_AND_RENDERING,
    WAITING_FOR_DESTINATION,
    SAVING
}

data class VisualExportSafRequest(
    val id: Long,
    val displayName: String
)
