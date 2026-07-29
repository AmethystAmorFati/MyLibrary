package com.example.mylibrary.ui

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.mylibrary.domain.model.FixedMediaStatistics
import com.example.mylibrary.domain.model.MediaCategoryStatistics
import com.example.mylibrary.ui.item.ItemEditorScaffold
import com.example.mylibrary.ui.item.ItemEditorUiState
import com.example.mylibrary.ui.item.QuoteDraftUiState
import com.example.mylibrary.ui.item.RecordDraftSheet
import com.example.mylibrary.ui.item.RecordDraftUiState
import com.example.mylibrary.ui.statistics.StatisticsScreen
import com.example.mylibrary.ui.statistics.StatisticsUiState
import com.example.mylibrary.ui.theme.MyLibraryTheme
import org.junit.Rule
import org.junit.Test

class Round4TPresentationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun editorLoadingKeepsTheShellWithoutFakeCardsOrInputs() {
        composeRule.setContent {
            MyLibraryTheme {
                ItemEditorScaffold(
                    screenTitle = "新增作品",
                    state = ItemEditorUiState(isLoading = true),
                    destinationEnterCompleted = false,
                    showQuoteChapter = true,
                    showQuotePage = true,
                    allowTypeSelection = true,
                    onBack = {},
                    onSaved = {},
                    onTypeSelected = {},
                    onTitleChange = {},
                    onCreatorChange = {},
                    onCoverSelected = {},
                    onRemoveCover = {},
                    onStatusSelected = {},
                    onTagSelectionChanged = {},
                    onCreateTag = { _, _ -> },
                    onDynamicValueChange = { _, _ -> },
                    onRecordDraftCompleted = {},
                    onRecordDraftDeleted = {},
                    onCreateQuoteDraft = {
                        QuoteDraftUiState("", null, "", "", "", 1L)
                    },
                    onQuoteDraftCompleted = {},
                    onQuoteDraftDeleted = {},
                    onSave = {}
                )
            }
        }

        composeRule.onNodeWithTag("item_editor_loading").assertExists()
        composeRule.onNodeWithTag("item_editor_loading_card_0").assertDoesNotExist()
        composeRule.onNodeWithTag("screen_add_item").assertDoesNotExist()
        composeRule.onNodeWithTag("save_item_button").assertIsNotEnabled()
    }

    @Test
    fun recordEditorUsesTopActionsAndNoBottomTextButtons() {
        composeRule.setContent {
            MyLibraryTheme {
                RecordDraftSheet(
                    initial = RecordDraftUiState(
                        key = "record-1",
                        id = 1L,
                        startDate = "2026-07-28",
                        endDate = "",
                        ratingHalfStars = null,
                        review = "一段很长的评价",
                        createdAt = 1L
                    ),
                    onComplete = {},
                    onDelete = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithTag("record_save_action").assertExists()
        composeRule.onNodeWithTag("record_delete_action").assertExists()
        composeRule.onNodeWithTag("record_review_input").assertExists()
        composeRule.onNodeWithText("完成").assertDoesNotExist()
        composeRule.onNodeWithText("删除记录").assertDoesNotExist()
    }

    @Test
    fun newRecordDoesNotShowDeleteActionBeforeJoiningTheDraftList() {
        composeRule.setContent {
            MyLibraryTheme {
                RecordDraftSheet(
                    initial = RecordDraftUiState(
                        key = "local-record",
                        id = null,
                        startDate = "2026-07-28",
                        endDate = "",
                        ratingHalfStars = null,
                        review = "",
                        createdAt = 1L
                    ),
                    onComplete = {},
                    onDelete = null,
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithTag("record_save_action").assertExists()
        composeRule.onNodeWithTag("record_delete_action").assertDoesNotExist()
    }

    @Test
    fun mediaCardsHideEmptyCategoryAndHideDurationAreaWithoutValuedRecords() {
        renderStatistics(
            FixedMediaStatistics(
                reading = MediaCategoryStatistics(
                    itemCount = 3,
                    recordCount = 5,
                    quoteCount = 8
                )
            )
        )

        composeRule.onNodeWithTag("reading_statistics_card").assertExists()
        composeRule.onNodeWithTag("watching_statistics_card").assertDoesNotExist()
        composeRule.onNodeWithText("作品").assertExists()
        composeRule.onNodeWithText("记录").assertExists()
        composeRule.onNodeWithText("摘录").assertExists()
        composeRule.onNodeWithTag("reading_statistics_card_duration")
            .assertDoesNotExist()
        composeRule.onNodeWithText("总时长").assertDoesNotExist()
        composeRule.onNodeWithText("标签数量").assertDoesNotExist()
        composeRule.onNodeWithText("最近 5 条摘录").assertExists()
    }

    @Test
    fun mediaCardsShowAllDurationMetricsWithUnifiedFormatting() {
        renderStatistics(
            FixedMediaStatistics(
                reading = MediaCategoryStatistics(
                    itemCount = 2,
                    recordCount = 3,
                    quoteCount = 4,
                    valuedRecordCount = 2,
                    valuedItemCount = 1,
                    totalDurationMinutes = 150,
                    maximumSingleDurationMinutes = 90,
                    longestItemId = 7,
                    longestItemTitle = "一部标题很长的书",
                    longestItemDurationMinutes = 150
                ),
                watching = MediaCategoryStatistics(itemCount = 1)
            )
        )

        composeRule.onNodeWithTag("reading_statistics_card").assertExists()
        composeRule.onNodeWithTag("watching_statistics_card").assertExists()
        composeRule.onNodeWithText("总时长").assertExists()
        composeRule.onNodeWithText("平均每次").assertExists()
        composeRule.onNodeWithText("最长单次").assertExists()
        composeRule.onNodeWithText("平均每本").assertExists()
        composeRule.onNodeWithText("阅读最久作品").assertExists()
        composeRule.onNodeWithText("1 小时 15 分钟").assertExists()
    }

    private fun renderStatistics(statistics: FixedMediaStatistics) {
        composeRule.setContent {
            MyLibraryTheme {
                StatisticsScreen(
                    state = StatisticsUiState(
                        mediaStatistics = statistics,
                        isLoading = false
                    ),
                    onQuoteSelected = {},
                    onViewAllQuotes = {}
                )
            }
        }
    }
}
