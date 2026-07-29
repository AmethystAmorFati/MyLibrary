package com.example.mylibrary.ui.item

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mylibrary.domain.usecase.TagUseCases
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ItemTagEditorViewModel(
    private val useCases: TagUseCases,
    private val itemId: Long
) : ViewModel() {
    private val error = MutableStateFlow<String?>(null)

    val uiState = combine(
        useCases.observe(),
        useCases.observe.forItem(itemId),
        error
    ) { tags, selected, message ->
        ItemTagEditorUiState(
            tags = tags,
            selectedIds = selected.mapTo(mutableSetOf()) { it.id },
            isLoading = false,
            errorMessage = message
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ItemTagEditorUiState()
    )

    fun setSelected(tagId: Long, selected: Boolean) {
        viewModelScope.launch {
            error.value = null
            runCatching { useCases.setItemTag(itemId, tagId, selected) }
                .onFailure { error.value = it.message ?: "标签更新失败" }
        }
    }
}

class ItemTagEditorViewModelFactory(
    private val useCases: TagUseCases,
    private val itemId: Long
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ItemTagEditorViewModel::class.java))
        return ItemTagEditorViewModel(useCases, itemId) as T
    }
}
