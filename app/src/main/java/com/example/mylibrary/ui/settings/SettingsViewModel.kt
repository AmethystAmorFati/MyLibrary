package com.example.mylibrary.ui.settings

import android.net.Uri
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
import com.example.mylibrary.export.report.ReportDataResolver
import com.example.mylibrary.export.report.ReportDataSnapshot
import com.example.mylibrary.export.report.ReportPreparationResult
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
    private val reportDataResolver: ReportDataResolver
) : ViewModel() {
    private val backupState = MutableStateFlow(BackupUiState())
    internal var preparedReportSnapshot: ReportDataSnapshot? = null
        private set
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

    val uiState = combine(contentState, backupState) { content, backup ->
        content.copy(
            backupOperation = backup.operation,
            importPreview = backup.importPreview,
            backupMessage = backup.message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SettingsUiState()
    )

    fun exportData(uri: Uri) {
        if (backupState.value.operation != null) return
        viewModelScope.launch {
            backupState.update {
                it.copy(
                    operation = SettingsBackupOperation.EXPORTING,
                    message = null
                )
            }
            val message = when (val result = backupRepository.export(uri)) {
                is BackupResult.Success -> {
                    val missing = result.warnings.filterIsInstance<BackupWarning.MissingCovers>()
                        .sumOf { it.count }
                    if (missing > 0) {
                        "数据已导出，$missing 张缺失封面未包含在备份中"
                    } else {
                        "数据已导出"
                    }
                }
                is BackupResult.Failure -> "导出失败"
                BackupResult.Cancelled -> null
            }
            backupState.update { it.copy(operation = null, message = message) }
        }
    }

    fun prepareImport(uri: Uri) {
        if (backupState.value.operation != null) return
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

    fun prepareReport(config: ReportExportConfig) {
        if (backupState.value.operation != null) return
        viewModelScope.launch {
            backupState.update {
                it.copy(
                    operation = SettingsBackupOperation.PREPARING_REPORT,
                    message = null
                )
            }
            preparedReportSnapshot = null
            val message = runCatching { reportDataResolver.resolve(config) }
                .fold(
                    onSuccess = { result ->
                        when (result) {
                            is ReportPreparationResult.InvalidConfig -> result.message
                            is ReportPreparationResult.Ready -> {
                                preparedReportSnapshot = result.snapshot
                                if (result.snapshot.isEmpty) {
                                    "所选范围内暂无可导出的记录"
                                } else {
                                    "报告数据已准备，正式渲染将在后续接入"
                                }
                            }
                        }
                    },
                    onFailure = {
                        preparedReportSnapshot = null
                        "报告数据准备失败"
                    }
                )
            backupState.update { it.copy(operation = null, message = message) }
        }
    }

    fun confirmImport() {
        if (backupState.value.operation != null ||
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
        if (backupState.value.operation != null) return
        backupState.update { it.copy(importPreview = null) }
        viewModelScope.launch { backupRepository.discardPreparedImport() }
    }

    fun consumeBackupMessage() {
        backupState.update { it.copy(message = null) }
    }
}

internal fun importResultMessage(result: BackupResult): String? = when (result) {
    is BackupResult.Success -> {
        if (BackupWarning.StagingCleanupFailed in result.warnings) {
            "数据导入完成，但临时文件清理失败"
        } else if (BackupWarning.OldCoverCleanupFailed in result.warnings) {
            "数据导入完成，但部分旧封面文件未能清理"
        } else {
            "数据导入完成"
        }
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
    private val reportDataResolver: ReportDataResolver
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(SettingsViewModel::class.java))
        return SettingsViewModel(
            libraryUseCases,
            fieldUseCases,
            backupRepository,
            reportDataResolver
        ) as T
    }
}

private data class BackupUiState(
    val operation: SettingsBackupOperation? = null,
    val importPreview: com.example.mylibrary.backup.model.ImportPreview? = null,
    val message: String? = null
)
