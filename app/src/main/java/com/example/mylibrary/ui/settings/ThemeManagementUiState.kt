package com.example.mylibrary.ui.settings

import com.example.mylibrary.ui.theme.importer.ThemePackageSource

enum class ThemeListItemStatus {
    DEFAULT,
    VALID,
    INVALID
}

data class ThemeListItem(
    val id: String?,
    val name: String,
    val author: String?,
    val version: String?,
    val status: ThemeListItemStatus,
    val isCurrent: Boolean
) {
    val isDefault: Boolean
        get() = status == ThemeListItemStatus.DEFAULT

    val canApply: Boolean
        get() = status != ThemeListItemStatus.INVALID && !isCurrent

    val canDelete: Boolean
        get() = !isDefault
}

data class ThemeUiMessage(
    val id: Long,
    val text: String
)

data class PendingThemeReplace(
    val themeId: String,
    val existingName: String?,
    val existingVersion: String?,
    val importingName: String,
    val importingVersion: String,
    val source: ThemePackageSource
)

data class ThemeManagementUiState(
    val themes: List<ThemeListItem> = emptyList(),
    val currentThemeId: String? = null,
    val isLoading: Boolean = true,
    val isImporting: Boolean = false,
    val applyingThemeId: String? = null,
    val deletingThemeId: String? = null,
    val pendingReplace: PendingThemeReplace? = null,
    val message: ThemeUiMessage? = null
) {
    val isBusy: Boolean
        get() = isImporting ||
            applyingThemeId != null ||
            deletingThemeId != null ||
            pendingReplace != null
}
