package com.example.mylibrary.ui.quote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mylibrary.domain.model.QuoteListItem
import com.example.mylibrary.domain.usecase.QuoteUseCases
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

private const val QUOTE_PAGE_SIZE = 20

data class QuoteListUiState(
    val query: String = "",
    val quotes: List<QuoteListItem> = emptyList(),
    val hasMore: Boolean = false,
    val isLoading: Boolean = true
)

private data class QuotePageResult(
    val query: String = "",
    val quotes: List<QuoteListItem> = emptyList(),
    val hasMore: Boolean = false,
    val isLoading: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
class QuoteListViewModel(
    quoteUseCases: QuoteUseCases
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val requestedPages = MutableStateFlow(1)

    private val pageResult = combine(query, requestedPages, ::Pair)
        .flatMapLatest { (currentQuery, pageCount) ->
            val pageFlows = (0 until pageCount).map { page ->
                quoteUseCases.observePage(
                    query = currentQuery,
                    limit = QUOTE_PAGE_SIZE + 1,
                    offset = page * QUOTE_PAGE_SIZE
                )
            }
            if (pageFlows.isEmpty()) {
                flowOf(QuotePageResult(query = currentQuery, isLoading = false))
            } else {
                combine(pageFlows) { pages ->
                    QuotePageResult(
                        query = currentQuery,
                        quotes = pages
                            .flatMap { it.take(QUOTE_PAGE_SIZE) }
                            .distinctBy { it.quote.id },
                        hasMore = pages.last().size > QUOTE_PAGE_SIZE,
                        isLoading = false
                    )
                }
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            QuotePageResult()
        )

    val uiState = combine(query, pageResult) { currentQuery, result ->
        if (currentQuery == result.query) {
            QuoteListUiState(
                query = currentQuery,
                quotes = result.quotes,
                hasMore = result.hasMore,
                isLoading = result.isLoading
            )
        } else {
            QuoteListUiState(query = currentQuery)
        }
    }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            QuoteListUiState()
        )

    fun onQueryChange(value: String) {
        query.value = value
        requestedPages.value = 1
    }

    fun loadMore() {
        if (!uiState.value.hasMore) return
        requestedPages.update { it + 1 }
    }
}

class QuoteListViewModelFactory(
    private val quoteUseCases: QuoteUseCases
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(QuoteListViewModel::class.java))
        return QuoteListViewModel(quoteUseCases) as T
    }
}
