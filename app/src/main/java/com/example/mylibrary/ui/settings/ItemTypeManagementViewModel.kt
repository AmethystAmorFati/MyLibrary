package com.example.mylibrary.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mylibrary.domain.usecase.ItemTypeUseCases
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ItemTypeManagementViewModel(
    private val useCases: ItemTypeUseCases
) : ViewModel() {
    private val error = MutableStateFlow<String?>(null)

    val uiState = combine(
        useCases.observe(),
        useCases.observe.usageCounts(),
        error
    ) { types, usageCounts, message ->
        ItemTypeManagementUiState(
            types = types,
            usageCounts = usageCounts,
            isLoading = false,
            errorMessage = message
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ItemTypeManagementUiState()
    )

    fun create(name: String) = runOperation { useCases.create(name) }
    fun rename(typeId: Long, name: String) =
        runOperation { useCases.rename(typeId, name) }
    fun delete(typeId: Long) = runOperation { useCases.delete(typeId) }
    fun reorder(orderedIds: List<Long>) =
        runOperation { useCases.reorder(orderedIds) }

    private fun runOperation(block: suspend () -> Unit) {
        viewModelScope.launch {
            error.value = null
            runCatching { block() }
                .onFailure { error.value = it.message ?: "作品类型操作失败" }
        }
    }
}

class ItemTypeManagementViewModelFactory(
    private val useCases: ItemTypeUseCases
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ItemTypeManagementViewModel::class.java))
        return ItemTypeManagementViewModel(useCases) as T
    }
}
