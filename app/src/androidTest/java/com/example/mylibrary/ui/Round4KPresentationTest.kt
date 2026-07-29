package com.example.mylibrary.ui

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.mylibrary.domain.model.LibraryTimelineRecord
import com.example.mylibrary.domain.model.LibraryViewMode
import com.example.mylibrary.domain.model.LibraryViewPreferences
import com.example.mylibrary.domain.model.TrashItem
import com.example.mylibrary.ui.home.HomeTimeline
import com.example.mylibrary.ui.home.TimelineListEntry
import com.example.mylibrary.ui.library.LibraryFilterBar
import com.example.mylibrary.ui.settings.LayoutSettingsScreen
import com.example.mylibrary.ui.settings.LayoutSettingsUiState
import com.example.mylibrary.ui.settings.TrashScreen
import com.example.mylibrary.ui.settings.TrashUiState
import com.example.mylibrary.ui.theme.MyLibraryTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class Round4KPresentationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun layoutBooleanRowsUseTheFiveUnifiedInlineSwitches() {
        var changed = false
        composeRule.setContent {
            MyLibraryTheme {
                LayoutSettingsScreen(
                    state = LayoutSettingsUiState(
                        preferences = LibraryViewPreferences(),
                        isLoading = false
                    ),
                    onBack = {},
                    onTimelineCreatorChanged = { changed = it },
                    onTimelineRatingChanged = {},
                    onTimelineStatusChanged = {},
                    onGridColumnsChanged = {},
                    onCoverColumnsChanged = {},
                    onListStatusChanged = {},
                    onListTagsChanged = {},
                    onListFieldsChanged = {}
                )
            }
        }

        listOf(
            "layout_timeline_creator_toggle",
            "layout_timeline_rating_toggle",
            "layout_timeline_status_toggle",
            "layout_list_status_toggle",
            "layout_list_tags_toggle"
        ).forEach { composeRule.onNodeWithTag(it).assertExists() }
        composeRule.onNodeWithText("开").assertDoesNotExist()
        composeRule.onNodeWithText("关").assertDoesNotExist()
        composeRule.onNodeWithTag("layout_timeline_creator_toggle").performClick()
        composeRule.runOnIdle { assertEquals(true, changed) }
    }

    @Test
    fun libraryTagFilterIsOnlyATransparentIconEntry() {
        var clicked = false
        composeRule.setContent {
            MyLibraryTheme {
                LibraryFilterBar(
                    statuses = emptyList(),
                    selectedStatusId = null,
                    viewMode = LibraryViewMode.SHELF,
                    onStatusSelected = {},
                    onTagFilter = { clicked = true },
                    onViewModeCycle = {},
                    onConfigureList = {}
                )
            }
        }

        composeRule.onNodeWithTag("library_tag_filter_icon").performClick()
        composeRule.onNodeWithText("标签").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(true, clicked) }
    }

    @Test
    fun trashRestoresFromAPlainItemClickWithoutConfirmation() {
        var restoredId: Long? = null
        val item = TrashItem(
            id = 7,
            typeId = 1,
            typeName = "Book",
            title = "被删除的书",
            creator = "作者",
            coverPath = null,
            thumbnailPath = null,
            deletedAt = 100
        )
        composeRule.setContent {
            MyLibraryTheme {
                TrashScreen(
                    state = TrashUiState(items = listOf(item), isLoading = false),
                    onBack = {},
                    onRestore = { restoredId = it },
                    onStartSelection = {},
                    onToggleSelection = {},
                    onClearSelection = {},
                    onPermanentlyDeleteSelected = {},
                    onEmptyTrash = {}
                )
            }
        }

        composeRule.onNodeWithTag("trash_item_7").performClick()
        composeRule.runOnIdle { assertEquals(7L, restoredId) }
    }

    @Test
    fun historicalCardsKeepIndependentRecordStatusSnapshots() {
        var secondStatus by mutableStateOf("重读中")
        composeRule.setContent {
            MyLibraryTheme {
                HomeTimeline(
                    entries = listOf(
                        timelineEntry(1, LocalDate.of(2023, 4, 12), 8, "已完成"),
                        timelineEntry(2, LocalDate.of(2026, 7, 1), 9, secondStatus)
                    ),
                    listState = rememberLazyListState(),
                    topContentPadding = 0.dp,
                    visibleRecordSyncEnabled = false,
                    showCreator = false,
                    showRating = true,
                    showTimelineStatus = true,
                    onVisibleRecordChanged = {},
                    onItemSelected = {}
                )
            }
        }

        composeRule.onAllNodesWithText("已完成").assertCountEquals(1)
        composeRule.onAllNodesWithText("重读中").assertCountEquals(1)
        composeRule.runOnIdle { secondStatus = "中止" }
        composeRule.onAllNodesWithText("已完成").assertCountEquals(1)
        composeRule.onAllNodesWithText("中止").assertCountEquals(1)
        composeRule.onAllNodesWithText("重读中").assertCountEquals(0)
    }

    private fun timelineEntry(
        recordId: Long,
        date: LocalDate,
        rating: Int,
        statusSnapshot: String
    ) = TimelineListEntry(
        recordStartDate = date,
        record = LibraryTimelineRecord(
            recordId = recordId,
            recordStartDate = date.toEpochDay(),
            createdAt = recordId,
            itemId = 10,
            typeId = 1,
            title = "同一作品",
            typeName = "Book",
            creator = "作者",
            ratingHalfStars = rating,
            thumbnailPath = null,
            statusSnapshot = statusSnapshot
        ),
        showDateLabel = true,
        isLastInDateGroup = true
    )
}
