package com.example.mylibrary.ui.item

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mylibrary.domain.model.ItemDetail
import com.example.mylibrary.domain.model.LibraryQuote
import com.example.mylibrary.domain.model.LibraryStatus
import com.example.mylibrary.domain.usecase.LibraryUseCases
import com.example.mylibrary.domain.usecase.QuoteUseCases
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ItemDetailViewModel(
    private val useCases: LibraryUseCases,
    private val quoteUseCases: QuoteUseCases,
    private val itemId: Long,
    defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {
    private val operationState = MutableStateFlow(ItemOperationState())

    private val preparedContent = combine(
        useCases.observeItemDetail(itemId),
        useCases.observeStatuses(),
        quoteUseCases.observeForItem(itemId)
    ) { detail, statuses, quotes ->
        prepareItemDetailContent(detail, statuses, quotes)
    }.flowOn(defaultDispatcher)

    val uiState = combine(
        preparedContent,
        operationState
    ) { content, operation ->
        ItemDetailUiState(
            detail = content.detail,
            quotes = content.quotes,
            visibleFields = content.visibleFields,
            currentStatus = content.currentStatus,
            isLoading = false,
            isDeleting = operation.isDeleting,
            isDeleted = operation.isDeleted,
            errorMessage = operation.errorMessage
        )
        }
        .catch { error ->
            emit(
                ItemDetailUiState(
                    isLoading = false,
                    errorMessage = error.message ?: "作品读取失败"
                )
            )
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ItemDetailUiState()
        )

    fun deleteItem() {
        if (operationState.value.isDeleting) return
        viewModelScope.launch {
            operationState.update { it.copy(isDeleting = true, errorMessage = null) }
            runCatching { useCases.deleteItem(itemId) }
                .onSuccess {
                    operationState.value = ItemOperationState(isDeleted = true)
                }
                .onFailure { error ->
                    operationState.value = ItemOperationState(
                        errorMessage = error.message ?: "删除失败"
                    )
                }
        }
    }

}

private data class ItemOperationState(
    val isDeleting: Boolean = false,
    val isDeleted: Boolean = false,
    val errorMessage: String? = null
)

class ItemDetailViewModelFactory(
    private val useCases: LibraryUseCases,
    private val quoteUseCases: QuoteUseCases,
    private val itemId: Long
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ItemDetailViewModel::class.java))
        return ItemDetailViewModel(useCases, quoteUseCases, itemId) as T
    }
}

internal data class PreparedItemDetailContent(
    val detail: ItemDetail?,
    val quotes: List<LibraryQuote>,
    val visibleFields: List<com.example.mylibrary.domain.model.DynamicFieldValue>,
    val currentStatus: LibraryStatus?
)

internal fun prepareItemDetailContent(
    detail: ItemDetail?,
    statuses: List<LibraryStatus>,
    quotes: List<LibraryQuote>
): PreparedItemDetailContent = PreparedItemDetailContent(
    detail = detail,
    quotes = quotes,
    visibleFields = detail?.fields.orEmpty().filter {
        !it.isFixed && it.value.isNotBlank()
    },
    currentStatus = detail?.item?.currentStatusId?.let { statusId ->
        statuses.firstOrNull { it.id == statusId }
    }
)
