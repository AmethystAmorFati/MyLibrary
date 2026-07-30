package com.example.mylibrary.ui.settings

import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mylibrary.backup.BackupRepository
import com.example.mylibrary.backup.model.BackupFailureReason
import com.example.mylibrary.backup.model.BackupPreparationResult
import com.example.mylibrary.backup.model.BackupResult
import com.example.mylibrary.backup.model.BackupWarning
import com.example.mylibrary.domain.usecase.FieldUseCases
import com.example.mylibrary.domain.usecase.LibraryUseCases
import com.example.mylibrary.export.report.ReportExportCoordinator
import com.example.mylibrary.export.visual.VisualExportCoordinator
import com.example.mylibrary.export.visual.VisualExportRequest
import com.example.mylibrary.export.visual.VisualExportThemeSnapshot
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    libraryUseCases: LibraryUseCases,
    fieldUseCases: FieldUseCases,
    private val backupRepository: BackupRepository,
    reportExportCoordinator: ReportExportCoordinator,
    visualExportCoordinator: VisualExportCoordinator,
    directPictureSaveSupported: Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
) : ViewModel() {
    private val backupState = MutableStateFlow(BackupUiState())
    private val visualExportController = SettingsVisualExportController(
        coordinator = visualExportCoordinator,
        scope = viewModelScope,
        directPictureSaveSupported = directPictureSaveSupported
    )
    private val reportExportController = SettingsReportExportController(
        coordinator = reportExportCoordinator,
        scope = viewModelScope,
        directSaveSupported = directPictureSaveSupported
    )
    private val contentState = combine(
        libraryUseCases.observeTypes(),
        libraryUseCases.observeStatuses(),
        fieldUseCases.observe()
    ) { types, statuses, fields ->
        SettingsUiState(
            types = types,
            statuses = statuses,
            dynamicFields = fields.filter { it.enabled && !it.isFixed },
            isLoading = false
        )
    }
        .catch { error ->
            emit(
                SettingsUiState(
                    isLoading = false,
                    errorMessage = error.message ?: "设置读取失败"
                )
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = SettingsUiState()
        )

    val uiState = combine(
        contentState,
        backupState,
        visualExportController.state,
        reportExportController.state
    ) { content, backup, visualExport, reportExport ->
        content.copy(
            backupOperation = backup.operation,
            importPreview = backup.importPreview,
            backupMessage = backup.message,
            visualExportOperation = visualExport.operation,
            visualExportSafRequest = visualExport.safRequest,
            visualExportMessage = visualExport.message,
            reportExportOperation = reportExport.operation,
            reportExportSafRequest = reportExport.safRequest,
            reportExportMessage = reportExport.message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SettingsUiState()
    )

    fun exportData(uri: Uri) {
        if (isOperationBusy()) return
        viewModelScope.launch {
            backupState.update {
                it.copy(
                    operation = SettingsBackupOperation.EXPORTING,
                    message = null
                )
            }
            val message = exportResultMessage(backupRepository.export(uri))
            backupState.update { it.copy(operation = null, message = message) }
        }
    }

    fun prepareImport(uri: Uri) {
        if (isOperationBusy()) return
        viewModelScope.launch {
            backupState.update {
                it.copy(
                    operation = SettingsBackupOperation.VALIDATING,
                    importPreview = null,
                    message = null
                )
            }
            when (val result = backupRepository.prepareImport(uri)) {
                is BackupPreparationResult.Ready -> backupState.update {
                    it.copy(operation = null, importPreview = result.preview)
                }
                is BackupPreparationResult.Failure -> backupState.update {
                    it.copy(
                        operation = null,
                        message = if (
                            result.reason == BackupFailureReason.UNSUPPORTED_NEWER_VERSION
                        ) {
                            "该备份由更高版本的 MyLibrary 创建，请先更新 App"
                        } else {
                            "备份文件无效或已损坏，尚未修改现有数据"
                        }
                    )
                }
            }
        }
    }

    fun startReportExport(
        config: ReportExportConfig,
        theme: VisualExportThemeSnapshot
    ): Boolean {
        if (backupState.value.operation != null || visualExportController.isBusy) {
            return false
        }
        return reportExportController.start(config, theme)
    }

    fun consumeReportExportSafRequest() {
        reportExportController.consumeSafRequest()
    }

    fun onReportExportDestinationSelected(uri: Uri?) {
        reportExportController.onSafResult(uri)
    }

    fun consumeReportExportMessage() {
        reportExportController.consumeMessage()
    }

    fun confirmImport() {
        if (isOperationBusy() ||
            backupState.value.importPreview == null
        ) {
            return
        }
        viewModelScope.launch {
            backupState.update {
                it.copy(
                    operation = SettingsBackupOperation.IMPORTING,
                    importPreview = null,
                    message = null
                )
            }
            val message = importResultMessage(backupRepository.importPrepared())
            backupState.update { it.copy(operation = null, message = message) }
        }
    }

    fun cancelPreparedImport() {
        if (isOperationBusy()) return
        backupState.update { it.copy(importPreview = null) }
        viewModelScope.launch { backupRepository.discardPreparedImport() }
    }

    fun consumeBackupMessage() {
        backupState.update { it.copy(message = null) }
    }

    fun startVisualExport(
        request: VisualExportRequest,
        theme: VisualExportThemeSnapshot
    ): Boolean {
        if (backupState.value.operation != null || reportExportController.isBusy) {
            return false
        }
        return visualExportController.start(request, theme)
    }

    fun consumeVisualExportSafRequest() {
        visualExportController.consumeSafRequest()
    }

    fun onVisualExportDestinationSelected(uri: Uri?) {
        visualExportController.onSafDestinationResult(uri)
    }

    fun consumeVisualExportMessage() {
        visualExportController.consumeMessage()
    }

    override fun onCleared() {
        visualExportController.close()
        reportExportController.close()
        super.onCleared()
    }

    private fun isOperationBusy(): Boolean =
        backupState.value.operation != null ||
            visualExportController.isBusy ||
            reportExportController.isBusy
}

internal fun exportResultMessage(result: BackupResult): String? = when (result) {
    is BackupResult.Success -> {
        val notes = buildList {
            val missing = result.warnings
                .filterIsInstance<BackupWarning.MissingCovers>()
                .sumOf { it.count }
            if (missing > 0) {
                add("$missing 张缺失封面未包含在备份中")
            }
            val skippedThemes = result.warnings
                .filterIsInstance<BackupWarning.SkippedThemes>()
                .sumOf { it.count }
            if (skippedThemes > 0) {
                add("$skippedThemes 个损坏主题已跳过")
            }
        }
        if (notes.isEmpty()) "数据已导出"
        else "数据已导出，${notes.joinToString("，")}"
    }
    is BackupResult.Failure -> "导出失败"
    BackupResult.Cancelled -> null
}

internal fun importResultMessage(result: BackupResult): String? = when (result) {
    is BackupResult.Success -> {
        val notes = buildList {
            val skippedThemes = result.warnings
                .filterIsInstance<BackupWarning.SkippedThemes>()
                .sumOf { it.count }
            if (skippedThemes > 0) {
                add("$skippedThemes 个主题未能恢复")
            }
            if (BackupWarning.CurrentThemeUnavailable in result.warnings) {
                add("当前主题无法恢复，已使用默认主题")
            }
            if (BackupWarning.ThemeRestoreFailed in result.warnings) {
                add("主题恢复失败")
            }
            if (BackupWarning.OldCoverCleanupFailed in result.warnings) {
                add("部分旧封面文件未能清理")
            }
            if (BackupWarning.StagingCleanupFailed in result.warnings) {
                add("临时文件清理失败")
            }
        }
        if (notes.isEmpty()) "数据导入完成"
        else if (notes.size == 1) "数据导入完成，但${notes[0]}"
        else "数据导入完成，但${notes.joinToString("；")}"
    }
    is BackupResult.Failure -> when {
        result.recovery?.fullyRecovered == true ->
            "导入失败，原数据已恢复"
        result.recovery != null ->
            "导入失败，已尝试恢复，但部分内容可能未完全恢复；请重新导入最近备份"
        else ->
            "导入失败；无法确认所有内容均未修改，请检查最近备份"
    }
    BackupResult.Cancelled -> null
}

class SettingsViewModelFactory(
    private val libraryUseCases: LibraryUseCases,
    private val fieldUseCases: FieldUseCases,
    private val backupRepository: BackupRepository,
    private val reportExportCoordinator: ReportExportCoordinator,
    private val visualExportCoordinator: VisualExportCoordinator
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(SettingsViewModel::class.java))
        return SettingsViewModel(
            libraryUseCases,
            fieldUseCases,
            backupRepository,
            reportExportCoordinator,
            visualExportCoordinator
        ) as T
    }
}

private data class BackupUiState(
    val operation: SettingsBackupOperation? = null,
    val importPreview: com.example.mylibrary.backup.model.ImportPreview? = null,
    val message: String? = null
)
