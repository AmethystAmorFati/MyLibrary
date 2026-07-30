package com.example.mylibrary.ui.item

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.mylibrary.data.database.DefaultLibraryData
import com.example.mylibrary.domain.model.FieldDataType
import com.example.mylibrary.ui.theme.MyLibraryTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class Round4SPresentationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun persistedQuoteUsesFixedEditorHeightAndTopIconActions() {
        var expectedContentHeightPx = 0f
        composeRule.setContent {
            expectedContentHeightPx = with(LocalDensity.current) {
                QuoteEditorContentHeight.toPx()
            }
            MyLibraryTheme {
                QuoteDraftSheet(
                    initial = QuoteDraftUiState(
                        localKey = "persisted-1",
                        persistedId = 1L,
                        content = "一段足够长的摘录",
                        chapter = "第一章",
                        page = "23",
                        createdTime = 1L
                    ),
                    showChapter = true,
                    showPage = true,
                    onSave = {},
                    onDelete = {},
                    onDismiss = {}
                )
            }
        }

        val contentBounds = composeRule.onNodeWithTag("quote_content_input")
            .fetchSemanticsNode().boundsInRoot
        assertEquals(expectedContentHeightPx, contentBounds.height, 0.5f)
        composeRule.onNodeWithTag("quote_save_action").assertExists()
        composeRule.onNodeWithTag("quote_delete_action").assertExists()
        composeRule.onNodeWithTag("quote_chapter_input").assertExists()
        composeRule.onNodeWithTag("quote_page_input").assertExists()
        composeRule.onNodeWithText("保存").assertDoesNotExist()
        composeRule.onNodeWithText("删除").assertDoesNotExist()
    }

    @Test
    fun newQuoteHidesDeleteAndDisabledLocationRowsWithoutLeavingNodes() {
        composeRule.setContent {
            MyLibraryTheme {
                QuoteDraftSheet(
                    initial = QuoteDraftUiState(
                        localKey = "local-1",
                        persistedId = null,
                        content = "",
                        chapter = "",
                        page = "",
                        createdTime = 1L
                    ),
                    showChapter = false,
                    showPage = false,
                    onSave = {},
                    onDelete = null,
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithTag("quote_save_action").assertExists()
        composeRule.onNodeWithTag("quote_delete_action").assertDoesNotExist()
        composeRule.onNodeWithTag("quote_chapter_input").assertDoesNotExist()
        composeRule.onNodeWithTag("quote_page_input").assertDoesNotExist()
    }

    @Test
    fun recordDurationAndOrdinaryNumberUnitsRemainHorizontal() {
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
                        createdAt = 1L,
                        dynamicFields = listOf(
                            DynamicFieldInputState(
                                definitionId = 7L,
                                name = "重量",
                                dataType = FieldDataType.NUMBER,
                                value = "12",
                                unit = "千克"
                            )
                        ),
                        durationHoursText = "2",
                        durationMinutesText = "75"
                    ),
                    itemTypeId = DefaultLibraryData.BOOK_TYPE_ID,
                    onComplete = {},
                    onDelete = null,
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithText("阅读时长").assertExists()
        composeRule.onNodeWithTag("record_duration_hours").assertExists()
        composeRule.onNodeWithTag("record_duration_minutes").assertExists()
        val hoursUnit = composeRule.onNodeWithText("小时")
            .fetchSemanticsNode().boundsInRoot
        val minutesUnit = composeRule.onNodeWithText("分钟")
            .fetchSemanticsNode().boundsInRoot
        val numberUnit = composeRule.onNodeWithTag("dynamic_field_unit_7")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(hoursUnit.width > hoursUnit.height)
        assertTrue(minutesUnit.width > minutesUnit.height)
        assertTrue(numberUnit.width >= numberUnit.height)
    }

    @Test
    fun itemRatingFieldEditsInlineAndCanClearWithoutOpeningASheet() {
        var value = "7"
        composeRule.setContent {
            MyLibraryTheme {
                DynamicFieldEditorRow(
                    field = DynamicFieldInputState(
                        definitionId = 9L,
                        name = "评分",
                        dataType = FieldDataType.RATING,
                        value = value
                    ),
                    onValueChange = { value = it },
                    onEdit = { error("Item 评分不应打开 BottomSheet") }
                )
            }
        }

        composeRule.onNodeWithTag("item_rating_field_9").assertExists()
        composeRule.onNodeWithTag("star_rating_star_4").performClick()
        composeRule.runOnIdle { assertEquals("8", value) }
        composeRule.onNodeWithTag("star_rating_star_4").performClick()
        composeRule.runOnIdle { assertEquals("", value) }
    }

    @Test
    fun itemHalfRatingKeepsFiveEqualTouchSlotsAndFiveEqualStarGraphics() {
        composeRule.setContent {
            MyLibraryTheme {
                DynamicFieldEditorRow(
                    field = DynamicFieldInputState(
                        definitionId = 10L,
                        name = "评分",
                        dataType = FieldDataType.RATING,
                        value = "9"
                    ),
                    onValueChange = {},
                    onEdit = {}
                )
            }
        }

        val touchBounds = (1..5).map { star ->
            composeRule.onNodeWithTag("star_rating_star_$star")
                .fetchSemanticsNode().boundsInRoot
        }
        val graphicBounds = (1..5).map { star ->
            composeRule.onNodeWithTag("rating_star_graphic_$star")
                .fetchSemanticsNode().boundsInRoot
        }

        touchBounds.drop(1).forEach { bounds ->
            assertEquals(touchBounds.first().width, bounds.width, 0.5f)
            assertEquals(touchBounds.first().height, bounds.height, 0.5f)
        }
        graphicBounds.drop(1).forEach { bounds ->
            assertEquals(graphicBounds.first().width, bounds.width, 0.5f)
            assertEquals(graphicBounds.first().height, bounds.height, 0.5f)
        }
    }
}
