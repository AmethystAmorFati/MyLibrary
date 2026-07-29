package com.example.mylibrary.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mylibrary.data.repository.UserPreferencesRepository
import com.example.mylibrary.domain.model.LibraryDisplayFieldKey
import com.example.mylibrary.domain.usecase.FieldUseCases
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LayoutSettingsViewModel(
    private val preferencesRepository: UserPreferencesRepository,
    fieldUseCases: FieldUseCases
) : ViewModel() {
    val uiState = combine(
        preferencesRepository.libraryViewPreferences,
        fieldUseCases.observe()
    ) { preferences, fields ->
        LayoutSettingsUiState(
            preferences = preferences.copy(
                listDisplayFields = preferences.listDisplayFields +
                    LibraryDisplayFieldKey.CREATOR
            ),
            dynamicFields = fields.filter { it.enabled && !it.isFixed },
            isLoading = false
        )
    }
        .catch { error ->
            emit(
                LayoutSettingsUiState(
                    isLoading = false,
                    errorMessage = error.message ?: "布局设置读取失败"
                )
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = LayoutSettingsUiState()
        )

    fun setTimelineShowCreator(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setTimelineShowCreator(enabled)
        }
    }

    fun setTimelineShowRating(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setTimelineShowRating(enabled)
        }
    }

    fun setTimelineShowStatus(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setTimelineShowStatus(enabled)
        }
    }

    fun setTimelineShowDuration(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setTimelineShowDuration(enabled)
        }
    }

    fun setLibraryShowTotalDuration(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setLibraryShowTotalDuration(enabled)
        }
    }

    fun setShowQuoteChapter(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setShowQuoteChapter(enabled)
        }
    }

    fun setShowQuotePage(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setShowQuotePage(enabled)
        }
    }

    fun setGridColumns(columns: Int) {
        viewModelScope.launch {
            preferencesRepository.setGridColumns(columns)
        }
    }

    fun setCoverColumns(columns: Int) {
        viewModelScope.launch {
            preferencesRepository.setCoverColumns(columns)
        }
    }

    fun setListStatusVisible(visible: Boolean) {
        setListField(LibraryDisplayFieldKey.CURRENT_STATUS, visible)
    }

    fun setListTagsVisible(visible: Boolean) {
        setListField(LibraryDisplayFieldKey.TAGS, visible)
    }

    fun setListDisplayFields(fields: Set<String>) {
        viewModelScope.launch {
            preferencesRepository.setListDisplayFields(fields + LibraryDisplayFieldKey.CREATOR)
        }
    }

    private fun setListField(key: String, visible: Boolean) {
        val current = uiState.value.preferences.listDisplayFields
        val next = if (visible) current + key else current - key
        setListDisplayFields(next)
    }
}

class LayoutSettingsViewModelFactory(
    private val preferencesRepository: UserPreferencesRepository,
    private val fieldUseCases: FieldUseCases
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(LayoutSettingsViewModel::class.java))
        return LayoutSettingsViewModel(preferencesRepository, fieldUseCases) as T
    }
}
