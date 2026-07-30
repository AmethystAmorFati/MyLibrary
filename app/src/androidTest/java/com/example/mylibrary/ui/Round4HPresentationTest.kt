package com.example.mylibrary.ui

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.mylibrary.domain.model.DynamicFieldDefinition
import com.example.mylibrary.domain.model.FieldDataType
import com.example.mylibrary.domain.model.ItemType
import com.example.mylibrary.domain.model.LibraryDisplayFieldKey
import com.example.mylibrary.domain.model.LibraryItem
import com.example.mylibrary.domain.model.LibraryStatus
import com.example.mylibrary.domain.model.LibraryTimelineRecord
import com.example.mylibrary.domain.model.LibraryViewMode
import com.example.mylibrary.domain.model.LibraryViewPreferences
import com.example.mylibrary.ui.home.HomeTimeline
import com.example.mylibrary.ui.home.TimelineListEntry
import com.example.mylibrary.ui.item.RecordDraftSheet
import com.example.mylibrary.ui.item.RecordDraftUiState
import com.example.mylibrary.ui.library.LibraryItemsView
import com.example.mylibrary.ui.settings.LayoutSettingsScreen
import com.example.mylibrary.ui.settings.LayoutSettingsUiState
import com.example.mylibrary.ui.settings.SettingsScreen
import com.example.mylibrary.ui.settings.SettingsUiState
import com.example.mylibrary.ui.theme.MyLibraryTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class Round4HPresentationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingsUsesSemanticGroupsAndAboutStaysInADialog() {
        showSettings()

        listOf(
            "settings_group_customization",
            "settings_group_export",
            "settings_group_data",
            "settings_group_appearance",
            "settings_group_about"
        ).forEach { composeRule.onNodeWithTag(it).assertExists() }
        listOf(
            "数据导入／恢复",
            "数据导出／备份",
            "导出月历页",
            "导出年度海报",
            "导出月度报告",
            "导出年度报告",
            "回收站",
            "状态管理",
            "标签管理",
            "自定义字段",
            "布局",
            "主题"
        ).forEach { composeRule.onNodeWithText(it).assertExists() }

        composeRule.onNodeWithTag("settings_about").performClick()
        composeRule.onNodeWithTag("settings_about_dialog").assertExists()
        composeRule.onNodeWithText("私人文化档案库\n所有数据保存在本地设备中。")
            .assertExists()
        composeRule.onNodeWithText("PeanutPersimmon").assertExists()
        composeRule.onNodeWithText("PeanutPersimmon").assertExists()
        composeRule.onNodeWithText("OpenAI Codex").assertExists()
        composeRule.onNodeWithTag("settings_about_github").assertExists()
        composeRule.onNodeWithTag("settings_about_repository").assertExists()
    }

    @Test
    fun exportDialogsKeepConfigurationInsideTheDialog() {
        showSettings()
        composeRule.onNodeWithTag("settings_export_calendar").performClick()
        composeRule.onNodeWithTag("settings_dialog_export_calendar_page").assertExists()
        composeRule.onNodeWithTag("export_year").assertExists()
        composeRule.onNodeWithTag("export_month").assertExists()

        composeRule.onNodeWithText("取消").performClick()
        composeRule.onNodeWithTag("settings_export_year_poster").performClick()
        composeRule.onNodeWithTag("settings_dialog_export_year_poster").assertExists()
        composeRule.onNodeWithTag("export_annual_categories").assertExists()
        composeRule.onNodeWithText("类别").assertDoesNotExist()
        listOf("全部", "书籍", "电影").forEach {
            composeRule.onNodeWithText(it).assertExists()
        }
        listOf("all", "book", "movie").forEach {
            composeRule.onNodeWithTag("export_annual_category_$it").assertExists()
        }
        composeRule.onNodeWithTag("export_annual_category_all").assertIsSelected()
        composeRule.onNodeWithTag("export_annual_category_book").performClick()
        composeRule.onNodeWithTag("export_annual_category_book").assertIsSelected()
        composeRule.onNodeWithTag("export_month").assertDoesNotExist()

        composeRule.onNodeWithText("取消").performClick()
        composeRule.onNodeWithTag("settings_export_monthly_report").performClick()
        composeRule.onNodeWithTag("settings_dialog_export_monthly_report").assertExists()
        composeRule.onNodeWithTag("export_month").assertExists()
        composeRule.onNodeWithTag("export_group_statistics").assertExists()
        composeRule.onNodeWithTag("export_group_field_statistics").assertExists()
        composeRule.onNodeWithTag("export_group_works").assertExists()
        composeRule.onNodeWithTag("export_group_statuses").assertExists()
        composeRule.onNodeWithTag("export_group_work_fields").assertExists()
        composeRule.onNodeWithText("作品展示").assertExists()
        composeRule.onNodeWithText("作品的自定义信息").assertExists()
        composeRule.onNodeWithTag("export_report_showcase_styles").assertExists()
        composeRule.onNodeWithTag("export_report_showcase_collage").assertIsSelected()

        composeRule.onNodeWithTag("export_category_1").performClick()
        composeRule.onNodeWithTag("export_work_field_11").assertExists()
        composeRule.onNodeWithTag("export_work_field_21").assertDoesNotExist()
    }

    @Test
    fun yearlyReportHasYearCategoryAndContentButNoMonth() {
        showSettings()
        composeRule.onNodeWithTag("settings_export_yearly_report").performClick()

        composeRule.onNodeWithTag("settings_dialog_export_yearly_report").assertExists()
        composeRule.onNodeWithTag("export_year").assertExists()
        composeRule.onNodeWithTag("export_month").assertDoesNotExist()
        composeRule.onNodeWithTag("export_group_category").assertExists()
        composeRule.onNodeWithTag("export_group_statistics").assertExists()
        composeRule.onNodeWithTag("export_group_works").assertDoesNotExist()
        composeRule.onNodeWithTag("export_group_work_fields").assertDoesNotExist()
    }

    @Test
    fun layoutDefaultsHaveNoDefaultViewModeSetting() {
        var creatorEnabled = false
        var statusEnabled = false
        composeRule.setContent {
            MyLibraryTheme {
                LayoutSettingsScreen(
                    state = LayoutSettingsUiState(
                        preferences = LibraryViewPreferences(),
                        isLoading = false
                    ),
                    onBack = {},
                    onTimelineCreatorChanged = { creatorEnabled = it },
                    onTimelineRatingChanged = {},
                    onTimelineStatusChanged = { statusEnabled = it },
                    onGridColumnsChanged = {},
                    onCoverColumnsChanged = {},
                    onListStatusChanged = {},
                    onListTagsChanged = {},
                    onListFieldsChanged = {}
                )
            }
        }

        composeRule.onNodeWithTag("layout_group_timeline").assertExists()
        composeRule.onNodeWithTag("layout_group_library").assertExists()
        composeRule.onNodeWithTag("layout_group_list").assertExists()
        composeRule.onNodeWithText("默认显示模式").assertDoesNotExist()
        composeRule.onNodeWithTag("layout_grid_columns_4").assertExists()
        composeRule.onNodeWithTag("layout_cover_columns_4").assertExists()
        composeRule.onNodeWithTag("layout_timeline_creator_toggle").performClick()
        composeRule.onNodeWithTag("layout_timeline_status_toggle").performClick()
        composeRule.runOnIdle {
            assertEquals(true, creatorEnabled)
            assertEquals(true, statusEnabled)
        }
    }

    @Test
    fun recordSheetOnlyShowsExperienceFieldsAndFiveStarInteraction() {
        var completed: RecordDraftUiState? = null
        composeRule.setContent {
            MyLibraryTheme {
                RecordDraftSheet(
                    initial = RecordDraftUiState(
                        key = "new",
                        id = null,
                        startDate = "2026-07-25",
                        endDate = "",
                        ratingHalfStars = null,
                        review = "",
                        createdAt = 1_753_435_740_000
                    ),
                    onComplete = { completed = it },
                    onDelete = null,
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithText("开始日期").assertExists()
        composeRule.onNodeWithText("结束日期").assertExists()
        composeRule.onNodeWithTag("record_rating_row").assertExists()
        composeRule.onNodeWithText("评价（可选）").assertExists()
        composeRule.onNodeWithText("记录日期").assertDoesNotExist()
        composeRule.onNodeWithText("记录时间").assertDoesNotExist()
        composeRule.onNodeWithTag("record_hour_wheel").assertDoesNotExist()
        composeRule.onNodeWithTag("record_minute_wheel").assertDoesNotExist()
        composeRule.onNodeWithTag("star_rating_bar").assertExists()
        composeRule.onNodeWithText("清除评分").assertDoesNotExist()

        composeRule.onNodeWithTag("star_rating_star_4").performClick()
        composeRule.onNodeWithText("完成").performClick()
        composeRule.runOnIdle { assertEquals(7, completed?.ratingHalfStars) }
    }

    @Test
    fun timelineAuthorRatingAndStatusAppearOnlyWhenEnabled() {
        var showDetails by mutableStateOf(false)
        val entry = TimelineListEntry(
            recordStartDate = LocalDate.of(2026, 7, 25),
            record = LibraryTimelineRecord(
                recordId = 1,
                recordStartDate = 1_753_435_200_000,
                createdAt = 1_753_435_740_000,
                itemId = 2,
                typeId = 1,
                title = "时间轴作品",
                typeName = "Book",
                creator = "独特作者",
                ratingHalfStars = 7,
                thumbnailPath = null,
                statusSnapshot = "进行中"
            ),
            showDateLabel = true,
            isLastInDateGroup = true
        )
        composeRule.setContent {
            MyLibraryTheme {
                HomeTimeline(
                    entries = listOf(entry),
                    listState = rememberLazyListState(),
                    topContentPadding = 0.dp,
                    visibleRecordSyncEnabled = false,
                    showCreator = showDetails,
                    showRating = showDetails,
                    showTimelineStatus = showDetails,
                    onVisibleRecordChanged = {},
                    onItemSelected = {}
                )
            }
        }

        composeRule.onNodeWithText("独特作者").assertDoesNotExist()
        composeRule.onNodeWithText("进行中").assertDoesNotExist()
        composeRule.onNodeWithTag("star_rating_bar").assertDoesNotExist()
        composeRule.runOnIdle { showDetails = true }
        composeRule.onNodeWithText("独特作者").assertExists()
        composeRule.onNodeWithText("进行中").assertExists()
        composeRule.onNodeWithTag("star_rating_bar").assertExists()
    }

    @Test
    fun listShowsStatusAndEveryTagAsSeparateCapsules() {
        val item = LibraryItem(
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
            currentStatusName = "在读",
            latestRatingHalfStars = null,
            tagNames = listOf("文学", "小说", "第三个标签")
        )
        composeRule.setContent {
            MyLibraryTheme {
                LibraryItemsView(
                    items = listOf(item),
                    mode = LibraryViewMode.LIST,
                    gridColumns = 4,
                    coverColumns = 4,
                    displayFields = setOf(
                        LibraryDisplayFieldKey.CURRENT_STATUS,
                        LibraryDisplayFieldKey.TAGS
                    ),
                    dynamicFields = emptyList(),
                    onItemSelected = {},
                    modifier = Modifier
                )
            }
        }

        composeRule.onNodeWithTag("item_card_7_status").assertExists()
        composeRule.onNodeWithTag("item_card_7_tags").assertExists()
        composeRule.onNodeWithText("在读").assertExists()
        composeRule.onNodeWithText("文学").assertExists()
        composeRule.onNodeWithText("小说").assertExists()
        composeRule.onNodeWithText("第三个标签").assertExists()
        composeRule.onNodeWithText("在读  文学  小说").assertDoesNotExist()
        composeRule.onNodeWithText("+1").assertDoesNotExist()
    }

    private fun showSettings() {
        composeRule.setContent {
            MyLibraryTheme {
                SettingsScreen(
                    state = SettingsUiState(
                        types = listOf(
                            ItemType(1, "Book", 0),
                            ItemType(2, "Movie", 1)
                        ),
                        statuses = listOf(
                            LibraryStatus(1, "想看", 0, true),
                            LibraryStatus(2, "在读", 1, true)
                        ),
                        dynamicFields = listOf(
                            field(11, 1, "Book", "出版社"),
                            field(21, 2, "Movie", "片长")
                        ),
                        isLoading = false
                    ),
                    onLayoutSettings = {},
                    onFieldManagement = {},
                    onTagManagement = {},
                    onStatusManagement = {}
                )
            }
        }
    }

    private fun field(
        id: Long,
        typeId: Long,
        typeName: String,
        name: String
    ) = DynamicFieldDefinition(
        id = id,
        typeId = typeId,
        typeName = typeName,
        name = name,
        dataType = FieldDataType.TEXT,
        enabled = true,
        sortOrder = 0,
        isFixed = false
    )
}
