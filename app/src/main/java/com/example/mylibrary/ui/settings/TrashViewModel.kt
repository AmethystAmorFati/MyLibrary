package com.example.mylibrary.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mylibrary.domain.model.TrashItem
import com.example.mylibrary.domain.usecase.TrashUseCases
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TrashViewModel(
    private val useCases: TrashUseCases
) : ViewModel() {
    private val items = MutableStateFlow<List<TrashItem>?>(null)
    private val selectedItemIds = MutableStateFlow<Set<Long>>(emptySet())
    private val isOperationRunning = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            useCases.observe().collect { currentItems ->
                items.value = currentItems
                selectedItemIds.update { selected ->
                    retainExistingTrashSelection(selected, currentItems)
                }
            }
        }
    }

    val uiState = combine(
        items,
        selectedItemIds,
        isOperationRunning,
        error
    ) { currentItems, selection, operationRunning, message ->
        TrashUiState(
            items = currentItems.orEmpty(),
            selectedItemIds = selection,
            isLoading = currentItems == null,
            isOperationRunning = operationRunning,
            errorMessage = message
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        TrashUiState()
    )

    fun startSelection(itemId: Long) {
        if (isOperationRunning.value || items.value.orEmpty().none { it.id == itemId }) return
        selectedItemIds.value = setOf(itemId)
    }

    fun toggleSelection(itemId: Long) {
        if (isOperationRunning.value || selectedItemIds.value.isEmpty()) return
        selectedItemIds.update { selected ->
            toggleTrashSelection(selected, itemId)
        }
    }

    fun clearSelection() {
        if (!isOperationRunning.value) {
            selectedItemIds.value = emptySet()
        }
    }

    fun restore(itemId: Long) = runOperation("恢复作品失败") {
        useCases.restore(itemId)
        selectedItemIds.update { it - itemId }
    }

    fun permanentlyDeleteSelected() {
        val itemIds = selectedItemIds.value
        if (itemIds.isEmpty()) return
        runOperation("永久删除作品失败") {
            useCases.permanentlyDelete(itemIds)
            selectedItemIds.value = emptySet()
        }
    }

    fun emptyTrash() = runOperation("清空回收站失败") {
        useCases.empty()
        selectedItemIds.value = emptySet()
    }

    private fun runOperation(
        fallbackMessage: String,
        block: suspend () -> Unit
    ) {
        if (!isOperationRunning.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            error.value = null
            try {
                runCatching { block() }
                    .onFailure { error.value = it.message ?: fallbackMessage }
            } finally {
                isOperationRunning.value = false
            }
        }
    }
}

class TrashViewModelFactory(
    private val useCases: TrashUseCases
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(TrashViewModel::class.java))
        return TrashViewModel(useCases) as T
    }
}
