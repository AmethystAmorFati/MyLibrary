package com.example.mylibrary.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mylibrary.domain.usecase.LibraryUseCases
import com.example.mylibrary.domain.usecase.TagUseCases
import com.example.mylibrary.domain.usecase.FieldUseCases
import com.example.mylibrary.data.repository.UserPreferencesRepository
import com.example.mylibrary.domain.model.LibraryViewMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(
    private val useCases: LibraryUseCases,
    private val tagUseCases: TagUseCases,
    fieldUseCases: FieldUseCases,
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {
    private val filter = MutableStateFlow(LibraryFilter())

    private val filteredItems = filter.flatMapLatest { appliedFilter ->
        useCases.observeItems(
            appliedFilter.query,
            appliedFilter.statusId,
            appliedFilter.tagIds
        ).map { items ->
            FilteredLibraryItems(
                filter = appliedFilter,
                items = items
            )
        }
    }

    private val metadata = combine(
        useCases.observeStatuses(),
        tagUseCases.observe(),
        fieldUseCases.observe(),
        preferencesRepository.libraryViewPreferences
    ) { statuses, tags, fields, preferences ->
        LibraryMetadata(
            statuses = statuses,
            tags = tags,
            fields = fields,
            viewMode = preferences.viewMode,
            gridColumns = preferences.gridColumns,
            coverColumns = preferences.coverColumns,
            listDisplayFields = preferences.listDisplayFields,
            showTotalDuration = preferences.libraryShowTotalDuration
        )
    }

    val uiState = combine(
        filteredItems,
        filter,
        metadata
    ) { result, requestedFilter, metadata ->
        LibraryUiState(
            items = result.items,
            statuses = metadata.statuses,
            tags = metadata.tags,
            query = requestedFilter.query,
            selectedStatusId = result.filter.statusId,
            selectedTagIds = result.filter.tagIds,
            dynamicFields = metadata.fields,
            viewMode = metadata.viewMode,
            gridColumns = metadata.gridColumns,
            coverColumns = metadata.coverColumns,
            listDisplayFields = metadata.listDisplayFields,
            showTotalDuration = metadata.showTotalDuration,
            isSearchActive = requestedFilter.isSearchActive,
            isLoading = false
        )
    }
        .catch { error ->
            emit(
                LibraryUiState(
                    isLoading = false,
                    errorMessage = error.message ?: "资料库读取失败"
                )
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = LibraryUiState()
        )

    fun onQueryChange(query: String) {
        filter.update { it.copy(query = query) }
    }

    fun onStatusSelected(statusId: Long?) {
        filter.update { it.copy(statusId = statusId) }
    }

    fun onTagsSelected(tagIds: Set<Long>) {
        filter.update { it.copy(tagIds = tagIds) }
    }

    fun openSearch() {
        filter.update { it.copy(isSearchActive = true) }
    }

    fun closeSearch() {
        filter.update { current ->
            if (current.query.isBlank()) current.copy(isSearchActive = false) else current
        }
    }

    fun setViewMode(mode: LibraryViewMode) {
        viewModelScope.launch {
            preferencesRepository.setLibraryViewMode(mode)
        }
    }

    fun setListDisplayFields(fields: Set<String>) {
        viewModelScope.launch {
            preferencesRepository.setListDisplayFields(fields)
        }
    }
}

class LibraryViewModelFactory(
    private val useCases: LibraryUseCases,
    private val tagUseCases: TagUseCases,
    private val fieldUseCases: FieldUseCases,
    private val preferencesRepository: UserPreferencesRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(LibraryViewModel::class.java))
        return LibraryViewModel(
            useCases,
            tagUseCases,
            fieldUseCases,
            preferencesRepository
        ) as T
    }
}

private data class LibraryFilter(
    val query: String = "",
    val statusId: Long? = null,
    val tagIds: Set<Long> = emptySet(),
    val isSearchActive: Boolean = false
)

private data class LibraryMetadata(
    val statuses: List<com.example.mylibrary.domain.model.LibraryStatus>,
    val tags: List<com.example.mylibrary.domain.model.LibraryTag>,
    val fields: List<com.example.mylibrary.domain.model.DynamicFieldDefinition>,
    val viewMode: LibraryViewMode,
    val gridColumns: Int,
    val coverColumns: Int,
    val listDisplayFields: Set<String>,
    val showTotalDuration: Boolean
)

private data class FilteredLibraryItems(
    val filter: LibraryFilter,
    val items: List<com.example.mylibrary.domain.model.LibraryItem>
)
