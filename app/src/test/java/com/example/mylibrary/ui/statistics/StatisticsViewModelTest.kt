package com.example.mylibrary.ui.statistics

import androidx.lifecycle.viewModelScope
import com.example.mylibrary.domain.model.CustomFieldStatistic
import com.example.mylibrary.domain.model.DistributionEntry
import com.example.mylibrary.domain.model.FieldAggregation
import com.example.mylibrary.domain.model.NumericMetric
import com.example.mylibrary.domain.model.QuoteListItem
import com.example.mylibrary.domain.model.FixedMediaStatistics
import com.example.mylibrary.domain.model.MediaCategoryStatistics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun repositoryResultIsPublishedToInitialUiState() =
        runTest(mainDispatcherRule.testDispatcher) {
        val expected = numericStatistic(fieldId = 10L, value = "20")
        val viewModel = createViewModel(
            customStatistics = MutableStateFlow(listOf(expected))
        )

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(listOf(expected), viewModel.uiState.value.customFieldStatistics)
        assertEquals(1L, viewModel.uiState.value.mediaStatistics.reading.itemCount)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun laterRepositoryEmissionUpdatesUiState() =
        runTest(mainDispatcherRule.testDispatcher) {
        val customStatistics =
            MutableStateFlow<List<CustomFieldStatistic>>(emptyList())
        val viewModel = createViewModel(customStatistics)
        advanceUntilIdle()
        assertEquals(emptyList<CustomFieldStatistic>(), viewModel.uiState.value.customFieldStatistics)

        val updated = CustomFieldStatistic.OptionDistribution(
            fieldId = 11L,
            fieldName = "阅读媒介",
            sortOrder = 1,
            entries = listOf(DistributionEntry("电子书", 2))
        )
        customStatistics.value = listOf(updated)
        advanceUntilIdle()

        assertEquals(listOf(updated), viewModel.uiState.value.customFieldStatistics)
        viewModel.viewModelScope.cancel()
    }

    private fun createViewModel(
        customStatistics: MutableStateFlow<List<CustomFieldStatistic>>
    ) = StatisticsViewModel(
        mediaStatistics = MutableStateFlow(
            FixedMediaStatistics(
                reading = MediaCategoryStatistics(itemCount = 1),
                watching = MediaCategoryStatistics(itemCount = 2)
            )
        ),
        recentQuotes = MutableStateFlow<List<QuoteListItem>>(emptyList()),
        customFieldStatistics = customStatistics,
        defaultDispatcher = mainDispatcherRule.testDispatcher
    )

    private fun numericStatistic(
        fieldId: Long,
        value: String
    ) = CustomFieldStatistic.Numeric(
        fieldId = fieldId,
        fieldName = "页数",
        sortOrder = 0,
        metrics = listOf(
            NumericMetric(
                aggregation = FieldAggregation.SUM,
                value = value,
                unit = "页"
            )
        )
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
