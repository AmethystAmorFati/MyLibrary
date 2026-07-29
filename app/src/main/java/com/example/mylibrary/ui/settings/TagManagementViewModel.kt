package com.example.mylibrary.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.mylibrary.domain.model.NewTag
import com.example.mylibrary.domain.usecase.TagUseCases
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TagManagementViewModel(
    private val useCases: TagUseCases,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val error = MutableStateFlow<String?>(null)
    private val requestedRootId =
        savedStateHandle.getMutableStateFlow<Long?>(SELECTED_ROOT_KEY, null)

    val uiState = combine(
        useCases.observe(includeDisabled = true),
        useCases.observe.usageCounts(),
        requestedRootId,
        error
    ) { tags, usageCounts, requestedId, message ->
        val roots = tags.filter { it.enabled && it.parentId == null }
        val selectedId = requestedId
            ?.takeIf { id -> roots.any { it.id == id } }
            ?: roots.minWithOrNull(
                compareBy<com.example.mylibrary.domain.model.LibraryTag> { it.sortOrder }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                    .thenBy { it.id }
            )?.id
        TagManagementUiState(
            tags = tags,
            usageCounts = usageCounts,
            selectedRootId = selectedId,
            isLoading = false,
            errorMessage = message
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        TagManagementUiState()
    )

    fun selectRoot(tagId: Long) {
        requestedRootId.value = tagId
    }

    fun createRoot(name: String) {
        runOperation {
            requestedRootId.value = useCases.create(NewTag(name, null))
        }
    }

    fun createChildren(parentId: Long, names: List<String>) {
        runOperation { useCases.createChildren(parentId, names) }
    }

    fun rename(tagId: Long, name: String) {
        runOperation { useCases.rename(tagId, name) }
    }

    fun delete(tagId: Long) {
        val state = uiState.value
        val deletingRootIndex = state.rootTags.indexOfFirst { it.id == tagId }
        if (deletingRootIndex >= 0) {
            requestedRootId.value = state.rootTags
                .getOrNull(deletingRootIndex + 1)
                ?.id
                ?: state.rootTags.getOrNull(deletingRootIndex - 1)?.id
        }
        runOperation { useCases.delete(tagId) }
    }

    fun reorderRoots(orderedIds: List<Long>) {
        runOperation { useCases.reorder(null, orderedIds) }
    }

    fun reorderChildren(parentId: Long, orderedIds: List<Long>) {
        runOperation { useCases.reorder(parentId, orderedIds) }
    }

    private fun runOperation(block: suspend () -> Unit) {
        viewModelScope.launch {
            error.value = null
            runCatching { block() }
                .onFailure { error.value = it.message ?: "标签操作失败" }
        }
    }

    private companion object {
        const val SELECTED_ROOT_KEY = "selected_root_id"
    }
}

class TagManagementViewModelFactory(
    private val useCases: TagUseCases
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: Class<T>,
        extras: androidx.lifecycle.viewmodel.CreationExtras
    ): T {
        require(modelClass.isAssignableFrom(TagManagementViewModel::class.java))
        return TagManagementViewModel(useCases, extras.createSavedStateHandle()) as T
    }
}
