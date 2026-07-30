package com.example.mylibrary.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mylibrary.ui.theme.DefaultResolvedTheme
import com.example.mylibrary.ui.theme.ThemeApplyResult
import com.example.mylibrary.ui.theme.ThemeRepository
import com.example.mylibrary.ui.theme.importer.InstalledThemeCatalog
import com.example.mylibrary.ui.theme.importer.InstalledThemeMetadata
import com.example.mylibrary.ui.theme.importer.InstalledThemeStatus
import com.example.mylibrary.ui.theme.importer.ThemeDeleteResult
import com.example.mylibrary.ui.theme.importer.ThemePackageImportResult
import com.example.mylibrary.ui.theme.importer.ThemePackageImportService
import com.example.mylibrary.ui.theme.importer.ThemePackageResult
import com.example.mylibrary.ui.theme.importer.ThemePackageSource
import com.example.mylibrary.ui.theme.importer.ThemePackageSourceFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ThemeManagementViewModel(
    private val importer: ThemePackageImportService,
    private val catalog: InstalledThemeCatalog,
    private val repository: ThemeRepository,
    private val sourceFactory: ThemePackageSourceFactory
) : ViewModel() {
    private val mutableUiState =
        MutableStateFlow(ThemeManagementUiState())
    val uiState = mutableUiState.asStateFlow()
    private var messageSequence = 0L

    init {
        viewModelScope.launch {
            refreshThemes(showLoading = true)
        }
        viewModelScope.launch {
            combine(
                repository.currentThemeId,
                repository.lastRestoreError
            ) { currentThemeId, restoreError ->
                currentThemeId to restoreError
            }.collect { (currentThemeId, restoreError) ->
                mutableUiState.update { state ->
                    state.copy(
                        currentThemeId = currentThemeId,
                        themes = state.themes.withCurrentTheme(
                            currentThemeId
                        )
                    )
                }
                if (restoreError != null) {
                    showMessage(
                        "上次使用的主题无法加载，已恢复默认主题"
                    )
                    repository.acknowledgeRestoreError()
                }
            }
        }
    }

    fun importTheme(uri: Uri) {
        importTheme(sourceFactory.create(uri))
    }

    internal fun importTheme(source: ThemePackageSource) {
        if (mutableUiState.value.isBusy) return
        viewModelScope.launch {
            mutableUiState.update {
                it.copy(isImporting = true, message = null)
            }
            val manifest = when (
                val peeked = importer.peekManifest(source)
            ) {
                is ThemePackageResult.Success -> peeked.value
                is ThemePackageResult.Failure -> {
                    showMessage(
                        ThemeUiErrorMapper.importFailure(peeked.error)
                    )
                    mutableUiState.update {
                        it.copy(isImporting = false)
                    }
                    return@launch
                }
            }
            val existing = catalog.listInstalledThemes()
                .find { it.id == manifest.id }
            if (existing != null) {
                mutableUiState.update {
                    it.copy(
                        isImporting = false,
                        pendingReplace = PendingThemeReplace(
                            themeId = manifest.id,
                            existingName = existing.name,
                            existingVersion = existing.version,
                            importingName = manifest.name,
                            importingVersion = manifest.version,
                            source = source
                        )
                    )
                }
                return@launch
            }
            when (
                val result = importer.import(source)
            ) {
                is ThemePackageImportResult.Installed ->
                    showMessage("主题已导入")

                is ThemePackageImportResult.Failure ->
                    showMessage(
                        ThemeUiErrorMapper.importFailure(result.error)
                    )
            }
            mutableUiState.update { it.copy(isImporting = false) }
            refreshThemes(showLoading = false)
        }
    }

    fun confirmReplaceTheme() {
        val pending = mutableUiState.value.pendingReplace ?: return
        viewModelScope.launch {
            mutableUiState.update {
                it.copy(isImporting = true, pendingReplace = null)
            }
            when (
                val result = importer.import(pending.source)
            ) {
                is ThemePackageImportResult.Installed -> {
                    if (
                        repository.currentThemeId.value == pending.themeId
                    ) {
                        when (
                            val applied = repository.applyInstalledTheme(
                                pending.themeId
                            )
                        ) {
                            is ThemeApplyResult.Applied,
                            is ThemeApplyResult.AlreadyCurrent ->
                                showMessage("主题已更新")
                            is ThemeApplyResult.Failure ->
                                showMessage(
                                    ThemeUiErrorMapper.applyFailure(
                                        applied.error,
                                        installedDuringOperation = true
                                    )
                                )
                        }
                    } else {
                        showMessage("主题已更新")
                    }
                }

                is ThemePackageImportResult.Failure ->
                    showMessage(
                        ThemeUiErrorMapper.importFailure(result.error)
                    )
            }
            mutableUiState.update { it.copy(isImporting = false) }
            refreshThemes(showLoading = false)
        }
    }

    fun cancelReplaceTheme() {
        mutableUiState.update { it.copy(pendingReplace = null) }
    }

    fun applyTheme(themeId: String?) {
        if (mutableUiState.value.isBusy) return
        if (themeId == mutableUiState.value.currentThemeId) return
        viewModelScope.launch {
            mutableUiState.update {
                it.copy(
                    applyingThemeId = themeId ?: DefaultResolvedTheme.id
                )
            }
            val result = if (themeId == null) {
                repository.applyDefaultTheme()
            } else {
                repository.applyInstalledTheme(themeId)
            }
            when (result) {
                is ThemeApplyResult.Applied ->
                    showMessage("主题已应用")
                is ThemeApplyResult.AlreadyCurrent -> Unit
                is ThemeApplyResult.Failure ->
                    showMessage(
                        ThemeUiErrorMapper.applyFailure(result.error)
                    )
            }
            mutableUiState.update { it.copy(applyingThemeId = null) }
            refreshThemes(showLoading = false)
        }
    }

    fun deleteTheme(themeId: String) {
        if (mutableUiState.value.isBusy) return
        viewModelScope.launch {
            mutableUiState.update {
                it.copy(deletingThemeId = themeId)
            }
            if (repository.currentThemeId.value == themeId) {
                when (val switched = repository.applyDefaultTheme()) {
                    is ThemeApplyResult.Failure -> {
                        showMessage(
                            ThemeUiErrorMapper.applyFailure(switched.error)
                        )
                        mutableUiState.update {
                            it.copy(deletingThemeId = null)
                        }
                        return@launch
                    }

                    is ThemeApplyResult.Applied,
                    is ThemeApplyResult.AlreadyCurrent -> Unit
                }
            }

            when (val deleted = catalog.delete(themeId)) {
                ThemeDeleteResult.Success ->
                    showMessage("主题已删除")
                is ThemeDeleteResult.Failure ->
                    showMessage(
                        ThemeUiErrorMapper.deleteFailure(deleted.error)
                    )
            }
            mutableUiState.update { it.copy(deletingThemeId = null) }
            refreshThemes(showLoading = false)
        }
    }

    fun refresh() {
        if (mutableUiState.value.isBusy) return
        viewModelScope.launch {
            refreshThemes(showLoading = true)
        }
    }

    fun consumeMessage(messageId: Long) {
        mutableUiState.update { state ->
            if (state.message?.id == messageId) {
                state.copy(message = null)
            } else {
                state
            }
        }
    }

    private suspend fun refreshThemes(showLoading: Boolean) {
        if (showLoading) {
            mutableUiState.update { it.copy(isLoading = true) }
        }
        val currentThemeId = repository.currentThemeId.value
        val installed = catalog.listInstalledThemes(currentThemeId)
        mutableUiState.update { state ->
            state.copy(
                themes = buildThemeList(installed, currentThemeId),
                currentThemeId = currentThemeId,
                isLoading = false
            )
        }
    }

    private fun showMessage(text: String) {
        messageSequence += 1L
        mutableUiState.update {
            it.copy(message = ThemeUiMessage(messageSequence, text))
        }
    }
}

internal fun buildThemeList(
    installed: List<InstalledThemeMetadata>,
    currentThemeId: String?
): List<ThemeListItem> {
    val defaultItem = ThemeListItem(
        id = null,
        name = DefaultResolvedTheme.name,
        author = null,
        version = null,
        status = ThemeListItemStatus.DEFAULT,
        isCurrent = currentThemeId == null
    )
    return listOf(defaultItem) + installed.map { metadata ->
        ThemeListItem(
            id = metadata.id,
            name = metadata.name ?: "主题已损坏",
            author = metadata.author,
            version = metadata.version,
            status = when (metadata.status) {
                InstalledThemeStatus.VALID -> ThemeListItemStatus.VALID
                InstalledThemeStatus.INVALID -> ThemeListItemStatus.INVALID
            },
            isCurrent = metadata.id == currentThemeId
        )
    }
}

private fun List<ThemeListItem>.withCurrentTheme(
    currentThemeId: String?
): List<ThemeListItem> = map { item ->
    item.copy(isCurrent = item.id == currentThemeId)
}

class ThemeManagementViewModelFactory(
    private val importer: ThemePackageImportService,
    private val catalog: InstalledThemeCatalog,
    private val repository: ThemeRepository,
    private val sourceFactory: ThemePackageSourceFactory
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(
            modelClass.isAssignableFrom(
                ThemeManagementViewModel::class.java
            )
        )
        return ThemeManagementViewModel(
            importer,
            catalog,
            repository,
            sourceFactory
        ) as T
    }
}
