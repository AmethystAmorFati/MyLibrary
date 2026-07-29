package com.example.mylibrary.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.mylibrary.domain.model.DynamicFieldDefinition
import com.example.mylibrary.domain.model.FieldDataType
import com.example.mylibrary.domain.model.LibraryDisplayFieldKey
import com.example.mylibrary.domain.model.LibraryItem
import com.example.mylibrary.domain.model.LibraryTimelineRecord
import com.example.mylibrary.domain.model.LibraryViewMode
import com.example.mylibrary.ui.home.HomeTimeline
import com.example.mylibrary.ui.home.TimelineListEntry
import com.example.mylibrary.ui.components.CardMetadataLineHeight
import com.example.mylibrary.ui.components.CardMetadataStarSize
import com.example.mylibrary.ui.library.LibraryItemsView
import com.example.mylibrary.ui.library.LibraryMetadataCapsuleHeight
import com.example.mylibrary.ui.library.LibraryMetadataCapsuleMaxWidth
import com.example.mylibrary.ui.theme.MyLibraryTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class Round4OPresentationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun timelineStatusAndLibraryMetadataShareTheCompactCapsuleHeight() {
        var expectedHeightPx = 0f
        var expectedLineHeightPx = 0f
        var expectedStarSizePx = 0f
        var maxWidthPx = 0f
        var selectedItemId: Long? = null
        composeRule.setContent {
            expectedHeightPx = with(LocalDensity.current) {
                LibraryMetadataCapsuleHeight.toPx()
            }
            expectedLineHeightPx = with(LocalDensity.current) {
                CardMetadataLineHeight.toPx()
            }
            expectedStarSizePx = with(LocalDensity.current) {
                CardMetadataStarSize.toPx()
            }
            maxWidthPx = with(LocalDensity.current) {
                LibraryMetadataCapsuleMaxWidth.toPx()
            }
            MyLibraryTheme {
                CompactMetadataTestContent(
                    onLibraryItemSelected = { selectedItemId = it }
                )
            }
        }

        val timelineStatus = composeRule.onNodeWithTag("timeline_status_1")
            .fetchSemanticsNode().boundsInRoot
        val libraryStatus = composeRule.onNodeWithTag("item_card_7_status")
            .fetchSemanticsNode().boundsInRoot
        val firstTag = composeRule.onNodeWithTag("item_card_7_tag_0")
            .fetchSemanticsNode().boundsInRoot
        val timelineCreator = composeRule.onNodeWithTag("timeline_creator_1")
            .fetchSemanticsNode().boundsInRoot
        val libraryCreator = composeRule.onNodeWithTag("item_card_7_creator")
            .fetchSemanticsNode().boundsInRoot
        val dynamicField = composeRule.onNodeWithTag("item_card_7_dynamic_0")
            .fetchSemanticsNode().boundsInRoot
        val firstStar = composeRule.onNodeWithTag("star_rating_star_1")
            .fetchSemanticsNode().boundsInRoot

        assertEquals(expectedHeightPx, timelineStatus.height, 0.5f)
        assertEquals(expectedHeightPx, libraryStatus.height, 0.5f)
        assertEquals(expectedHeightPx, firstTag.height, 0.5f)
        assertEquals(expectedLineHeightPx, timelineCreator.height, 0.5f)
        assertEquals(expectedLineHeightPx, libraryCreator.height, 0.5f)
        assertEquals(expectedLineHeightPx, dynamicField.height, 0.5f)
        assertEquals(expectedStarSizePx, firstStar.height, 0.5f)
        assertTrue(timelineStatus.width <= maxWidthPx + 0.5f)
        assertTrue(libraryStatus.width <= maxWidthPx + 0.5f)
        assertTrue(libraryStatus.right <= firstTag.left)
        composeRule.onNodeWithText("+2").assertDoesNotExist()
        composeRule.onNodeWithTag("timeline_duration_1").assertExists()
        composeRule.onNodeWithText("1 小时 30 分钟").assertExists()
        composeRule.onNodeWithTag("item_card_7_total_duration").assertExists()
        composeRule.onNodeWithText("累计阅读 2 小时 30 分钟").assertExists()

        composeRule.onNodeWithTag("item_card_7").performClick()
        composeRule.runOnIdle { assertEquals(7L, selectedItemId) }
    }

    @Composable
    private fun CompactMetadataTestContent(
        onLibraryItemSelected: (Long) -> Unit
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            ) {
                HomeTimeline(
                    entries = listOf(
                        TimelineListEntry(
                            recordStartDate = LocalDate.of(2026, 7, 27),
                            record = LibraryTimelineRecord(
                                recordId = 1,
                                recordStartDate = 1,
                                createdAt = 1,
                                itemId = 2,
                                typeId = 1,
                                title = "时间轴作品",
                                typeName = "Book",
                                creator = "作者",
                                ratingHalfStars = 8,
                                thumbnailPath = null,
                                statusSnapshot =
                                    "一个很长但必须保持单行省略的记录状态",
                                durationMinutes = 90
                            ),
                            showDateLabel = true,
                            isLastInDateGroup = true
                        )
                    ),
                    listState = rememberLazyListState(),
                    topContentPadding = 0.dp,
                    visibleRecordSyncEnabled = false,
                    showCreator = true,
                    showRating = true,
                    showTimelineStatus = true,
                    onVisibleRecordChanged = {},
                    onItemSelected = {}
                )
            }
            LibraryItemsView(
                items = listOf(
                    LibraryItem(
                        id = 7,
                        typeId = 1,
                        typeName = "Book",
                        title = "列表作品",
                        creator = "作者",
                        coverPath = null,
                        thumbnailPath = null,
                        createdTime = 1,
                        updatedTime = 1,
                        currentStatusId = 2,
                        currentStatusName = "已完成",
                        latestRatingHalfStars = null,
                        totalDurationMinutes = 150,
                        tagNames = listOf("小说", "成长", "历史", "女性"),
                        dynamicValues = mapOf(11L to "人民文学出版社")
                    )
                ),
                mode = LibraryViewMode.LIST,
                gridColumns = 4,
                coverColumns = 4,
                displayFields = setOf(
                    LibraryDisplayFieldKey.CURRENT_STATUS,
                    LibraryDisplayFieldKey.TAGS,
                    LibraryDisplayFieldKey.dynamic(11)
                ),
                dynamicFields = listOf(
                    DynamicFieldDefinition(
                        id = 11,
                        typeId = 1,
                        typeName = "Book",
                        name = "出版社",
                        dataType = FieldDataType.TEXT,
                        enabled = true,
                        sortOrder = 0,
                        isFixed = false
                    )
                ),
                onItemSelected = onLibraryItemSelected,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            )
        }
    }
}
