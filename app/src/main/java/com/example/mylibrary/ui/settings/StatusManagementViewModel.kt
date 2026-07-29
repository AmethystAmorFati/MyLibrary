package com.example.mylibrary.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mylibrary.domain.usecase.StatusUseCases
import com.example.mylibrary.domain.model.StatusScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class StatusManagementViewModel(
    private val useCases: StatusUseCases
) : ViewModel() {
    private val error = MutableStateFlow<String?>(null)
    private val selectedScope = MutableStateFlow(StatusScope.ITEM)

    val uiState = selectedScope.flatMapLatest { scope ->
        combine(
            useCases.observe(scope, includeDisabled = true),
            useCases.observe.usageCounts(scope),
            error
        ) { statuses, usageCounts, message ->
            StatusManagementUiState(
                statuses = statuses,
                selectedScope = scope,
                usageCounts = usageCounts,
                isLoading = false,
                errorMessage = message
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        StatusManagementUiState()
    )

    fun selectScope(scope: StatusScope) {
        selectedScope.value = scope
        error.value = null
    }

    fun create(name: String) =
        runOperation { useCases.create(name, selectedScope.value) }
    fun rename(statusId: Long, name: String) =
        runOperation { useCases.rename(statusId, name) }

    fun delete(statusId: Long) = runOperation { useCases.delete(statusId) }
    fun reorder(orderedIds: List<Long>) =
        runOperation { useCases.reorder(selectedScope.value, orderedIds) }

    private fun runOperation(block: suspend () -> Unit) {
        viewModelScope.launch {
            error.value = null
            runCatching { block() }
                .onFailure { error.value = it.message ?: "状态操作失败" }
        }
    }
}

class StatusManagementViewModelFactory(
    private val useCases: StatusUseCases
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(StatusManagementViewModel::class.java))
        return StatusManagementViewModel(useCases) as T
    }
}
