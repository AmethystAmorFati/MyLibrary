package com.example.mylibrary.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.example.mylibrary.domain.model.LibraryDisplayFieldKey
import com.example.mylibrary.domain.model.LibraryItem
import com.example.mylibrary.domain.model.LibraryViewMode
import com.example.mylibrary.ui.library.LibraryItemsView
import com.example.mylibrary.ui.library.LibraryTopBar
import com.example.mylibrary.ui.settings.SettingsScreen
import com.example.mylibrary.ui.settings.SettingsUiState
import com.example.mylibrary.ui.theme.MyLibraryTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class Round4LPresentationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingsGroupsAndEntriesUseTheRequiredOrder() {
        composeRule.setContent {
            MyLibraryTheme {
                SettingsScreen(
                    state = SettingsUiState(isLoading = false),
                    onLayoutSettings = {},
                    onFieldManagement = {},
                    onTagManagement = {},
                    onStatusManagement = {}
                )
            }
        }

        assertTopOrder(
            "settings_group_appearance",
            "settings_group_customization",
            "settings_group_export",
            "settings_group_data",
            "settings_group_about"
        )
        assertTopOrder("settings_layout", "settings_theme")
        assertTopOrder(
            "settings_statuses",
            "settings_tags",
            "settings_fields"
        )
        composeRule.onNodeWithTag("settings_types").assertDoesNotExist()
        assertTopOrder(
            "settings_export_calendar",
            "settings_export_year_poster",
            "settings_export_monthly_report",
            "settings_export_yearly_report"
        )
        assertTopOrder(
            "settings_export_data",
            "settings_import_data",
            "settings_trash"
        )
        composeRule.onNodeWithTag("settings_about").assertExists()
    }

    @Test
    fun libraryTopBarOnlyKeepsSearchAction() {
        composeRule.setContent {
            MyLibraryTheme {
                LibraryTopBar(
                    isSearchActive = false,
                    isPageVisible = true,
                    query = "",
                    onQueryChange = {},
                    onSearchOpen = {},
                    onSearchClose = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription("打开搜索").assertExists()
        composeRule.onNodeWithContentDescription("导出当前封面海报")
            .assertDoesNotExist()
    }

    @Test
    fun metadataRowOnlyExistsForEnabledNonEmptyContent() {
        var displayFields by mutableStateOf(emptySet<String>())
        val item = libraryItem(
            statusName = "在读",
            tags = listOf("小说", "成长")
        )
        composeRule.setContent {
            MyLibraryTheme {
                LibraryItemsView(
                    items = listOf(item),
                    mode = LibraryViewMode.LIST,
                    gridColumns = 4,
                    coverColumns = 4,
                    displayFields = displayFields,
                    dynamicFields = emptyList(),
                    onItemSelected = {}
                )
            }
        }

        composeRule.onNodeWithTag("item_card_7_metadata").assertDoesNotExist()

        composeRule.runOnIdle {
            displayFields = setOf(LibraryDisplayFieldKey.CURRENT_STATUS)
        }
        composeRule.onNodeWithTag("item_card_7_status").assertExists()
        composeRule.onNodeWithTag("item_card_7_tags").assertDoesNotExist()

        composeRule.runOnIdle {
            displayFields = setOf(LibraryDisplayFieldKey.TAGS)
        }
        composeRule.onNodeWithTag("item_card_7_status").assertDoesNotExist()
        composeRule.onNodeWithTag("item_card_7_tags").assertExists()

        composeRule.runOnIdle {
            displayFields = setOf(
                LibraryDisplayFieldKey.CURRENT_STATUS,
                LibraryDisplayFieldKey.TAGS
            )
        }
        composeRule.onNodeWithTag("item_card_7_status").assertExists()
        composeRule.onNodeWithTag("item_card_7_tags").assertExists()
    }

    @Test
    fun everyTagScrollsWhileStatusStaysFixedAndCardIsNotClicked() {
        var selected = false
        val tags = listOf(
            "小说",
            "成长",
            "女性",
            "历史",
            "社会观察",
            "最后一个标签"
        )
        composeRule.setContent {
            MyLibraryTheme {
                LibraryItemsView(
                    items = listOf(
                        libraryItem(
                            statusName = "一个很长但必须固定显示的当前状态",
                            tags = tags
                        )
                    ),
                    mode = LibraryViewMode.LIST,
                    gridColumns = 4,
                    coverColumns = 4,
                    displayFields = setOf(
                        LibraryDisplayFieldKey.CURRENT_STATUS,
                        LibraryDisplayFieldKey.TAGS
                    ),
                    dynamicFields = emptyList(),
                    onItemSelected = { selected = true },
                    modifier = Modifier
                )
            }
        }

        val statusBefore = composeRule.onNodeWithTag("item_card_7_status")
            .fetchSemanticsNode().boundsInRoot
        val firstTag = composeRule.onNodeWithTag("item_card_7_tag_0")
            .fetchSemanticsNode().boundsInRoot
        val metadata = composeRule.onNodeWithTag("item_card_7_metadata")
            .fetchSemanticsNode().boundsInRoot
        assertEquals(statusBefore.height, firstTag.height, 0.5f)
        assertTrue(statusBefore.width <= metadata.width / 2 + 0.5f)

        repeat(2) {
            composeRule.onNodeWithTag("item_card_7_tags")
                .performTouchInput { swipeLeft() }
        }

        val statusAfter = composeRule.onNodeWithTag("item_card_7_status")
            .fetchSemanticsNode().boundsInRoot
        assertEquals(statusBefore.left, statusAfter.left, 0.5f)
        composeRule.onNodeWithText("最后一个标签").assertExists()
        composeRule.onNodeWithText("+4").assertDoesNotExist()
        composeRule.runOnIdle { assertFalse(selected) }
    }

    private fun assertTopOrder(vararg tags: String) {
        val tops = tags.map {
            composeRule.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot.top
        }
        assertEquals(tops.sorted(), tops)
    }

    private fun libraryItem(
        statusName: String?,
        tags: List<String>
    ) = LibraryItem(
        id = 7,
        typeId = 1,
        typeName = "Book",
        title = "列表作品",
        creator = "作者",
        coverPath = null,
        thumbnailPath = null,
        createdTime = 1,
        updatedTime = 1,
        currentStatusId = statusName?.let { 2 },
        currentStatusName = statusName,
        latestRatingHalfStars = null,
        tagNames = tags
    )
}
