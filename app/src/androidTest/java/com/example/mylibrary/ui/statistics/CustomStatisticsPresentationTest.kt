package com.example.mylibrary.ui.statistics

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.mylibrary.domain.model.CustomFieldStatistic
import com.example.mylibrary.domain.model.DistributionEntry
import com.example.mylibrary.domain.model.FieldAggregation
import com.example.mylibrary.domain.model.NumericMetric
import com.example.mylibrary.ui.theme.MyLibraryTheme
import org.junit.Rule
import org.junit.Test

class CustomStatisticsPresentationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun customStatisticsSectionIsHiddenWhenThereIsNoEffectiveData() {
        render(StatisticsUiState(isLoading = false))

        composeRule.onNodeWithText("自定义字段统计").assertDoesNotExist()
    }

    @Test
    fun formalStatisticsScreenRendersNumericOptionAndRatingCards() {
        render(
            StatisticsUiState(
                customFieldStatistics = listOf(
                    CustomFieldStatistic.Numeric(
                        fieldId = 10L,
                        fieldName = "页数",
                        sortOrder = 1,
                        metrics = listOf(
                            NumericMetric(
                                FieldAggregation.SUM,
                                "20",
                                "页"
                            ),
                            NumericMetric(
                                FieldAggregation.AVERAGE,
                                "10",
                                "页"
                            )
                        )
                    ),
                    CustomFieldStatistic.OptionDistribution(
                        fieldId = 11L,
                        fieldName = "阅读媒介",
                        sortOrder = 2,
                        entries = listOf(
                            DistributionEntry("电子书", 2),
                            DistributionEntry("纸质书", 1)
                        )
                    ),
                    CustomFieldStatistic.OptionDistribution(
                        fieldId = 12L,
                        fieldName = "内容特点",
                        sortOrder = 3,
                        entries = listOf(
                            DistributionEntry("成长", 2),
                            DistributionEntry("女性", 1)
                        )
                    ),
                    CustomFieldStatistic.Rating(
                        fieldId = 13L,
                        fieldName = "个人评分",
                        sortOrder = 4,
                        average = "4.25",
                        distribution = listOf(
                            DistributionEntry("5 星", 1),
                            DistributionEntry("3.5 星", 1)
                        )
                    )
                ),
                isLoading = false
            )
        )

        composeRule.onNodeWithTag("screen_statistics").assertExists()
        composeRule.onNodeWithTag("custom_statistics_section").assertExists()
        composeRule.onNodeWithText("自定义字段统计").assertExists()
        composeRule.onNodeWithTag("custom_statistic_10").assertExists()
        composeRule.onNodeWithText("20 页").assertExists()
        composeRule.onNodeWithText("10 页").assertExists()
        composeRule.onNodeWithText("平均").assertExists()
        composeRule.onNodeWithTag("custom_statistic_11").assertExists()
        composeRule.onNodeWithText("电子书").assertExists()
        composeRule.onNodeWithTag("custom_statistic_12").assertExists()
        composeRule.onNodeWithText("内容特点").assertExists()
        composeRule.onNodeWithText("成长").assertExists()
        composeRule.onNodeWithTag("custom_statistic_13").assertExists()
        composeRule.onNodeWithText("4.25").assertExists()
        composeRule.onNodeWithText("3.5 星").assertExists()
    }

    private fun render(state: StatisticsUiState) {
        composeRule.setContent {
            MyLibraryTheme {
                StatisticsScreen(
                    state = state,
                    onQuoteSelected = {},
                    onViewAllQuotes = {}
                )
            }
        }
    }
}
