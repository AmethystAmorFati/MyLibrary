package com.example.mylibrary.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mylibrary.domain.model.QuoteListItem
import com.example.mylibrary.domain.model.FixedMediaStatistics
import com.example.mylibrary.domain.model.CustomFieldStatistic
import com.example.mylibrary.domain.usecase.ObserveCustomFieldStatisticsUseCase
import com.example.mylibrary.domain.usecase.QuoteUseCases
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

data class StatisticsUiState(
    val mediaStatistics: FixedMediaStatistics = FixedMediaStatistics(),
    val recentQuotes: List<QuoteListItem> = emptyList(),
    val customFieldStatistics: List<CustomFieldStatistic> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

class StatisticsViewModel internal constructor(
    mediaStatistics: Flow<FixedMediaStatistics>,
    recentQuotes: Flow<List<QuoteListItem>>,
    customFieldStatistics: Flow<List<CustomFieldStatistic>>,
    defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {
    constructor(
        quoteUseCases: QuoteUseCases,
        observeCustomFieldStatistics: ObserveCustomFieldStatisticsUseCase
    ) : this(
        mediaStatistics = quoteUseCases.observeMediaStatistics(),
        recentQuotes = quoteUseCases.observeRecent(),
        customFieldStatistics = observeCustomFieldStatistics()
    )

    val uiState = combine(
        mediaStatistics,
        recentQuotes,
        customFieldStatistics
    ) { statistics, recent, customStatistics ->
        StatisticsUiState(
            mediaStatistics = statistics,
            recentQuotes = recent,
            customFieldStatistics = customStatistics,
            isLoading = false
        )
    }.flowOn(defaultDispatcher)
        .catch { error ->
            emit(
                StatisticsUiState(
                    isLoading = false,
                    errorMessage = error.message ?: "统计读取失败"
                )
            )
        }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            StatisticsUiState()
        )
}

class StatisticsViewModelFactory(
    private val quoteUseCases: QuoteUseCases,
    private val observeCustomFieldStatistics: ObserveCustomFieldStatisticsUseCase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(StatisticsViewModel::class.java))
        return StatisticsViewModel(
            quoteUseCases,
            observeCustomFieldStatistics
        ) as T
    }
}
