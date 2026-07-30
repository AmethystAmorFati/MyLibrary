package com.example.mylibrary.ui.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.mylibrary.domain.model.LibraryTag
import com.example.mylibrary.domain.model.LibraryViewMode
import com.example.mylibrary.ui.theme.MyLibraryTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class LibraryTagFilterPresentationTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val tags = listOf(
        tag(id = 1, name = "A"),
        tag(id = 2, name = "a1", parentId = 1),
        tag(id = 3, name = "B"),
        tag(id = 4, name = "b2", parentId = 3)
    )

    @Test
    fun tagEntryIsOnlyAnIconWithoutCountContent() {
        composeRule.setContent {
            MyLibraryTheme {
                LibraryFilterBar(
                    statuses = emptyList(),
                    selectedStatusId = null,
                    viewMode = LibraryViewMode.SHELF,
                    onStatusSelected = {},
                    onTagFilter = {},
                    onViewModeCycle = {},
                    onConfigureList = {}
                )
            }
        }

        composeRule.onNodeWithTag("library_tag_filter_icon").assertExists()
        composeRule.onNodeWithContentDescription("标签筛选").assertExists()
        composeRule.onAllNodesWithText("1").assertCountEquals(0)
        composeRule.onAllNodesWithText("3").assertCountEquals(0)
    }

    @Test
    fun selectedTagRowAppearsRemovesOneTagAndResetsOnlyTags() {
        var selectedIds by mutableStateOf<Set<Long>>(emptySet())
        val statusId = 9L
        val typeId = 2L
        val query = "关键词"
        composeRule.setContent {
            MyLibraryTheme {
                LibrarySelectedTagFilterRow(
                    tags = tags,
                    selectedIds = selectedIds,
                    onSelectionChange = { selectedIds = it }
                )
            }
        }

        composeRule.onNodeWithTag("library_selected_tag_filters")
            .assertDoesNotExist()

        composeRule.runOnIdle { selectedIds = setOf(1, 2) }
        composeRule.onNodeWithTag("library_selected_tag_filters").assertExists()
        composeRule.onNodeWithTag("library_selected_tags_reset").assertExists()
        composeRule.onNodeWithTag("library_selected_tag_1").assertExists()
        composeRule.onNodeWithTag("library_selected_tag_2").assertExists()

        composeRule.onNodeWithTag("library_selected_tag_1").performClick()
        composeRule.runOnIdle {
            assertEquals(setOf(2L), selectedIds)
            assertEquals(9L, statusId)
            assertEquals(2L, typeId)
            assertEquals("关键词", query)
        }
        composeRule.onNodeWithTag("library_selected_tag_1").assertDoesNotExist()
        composeRule.onNodeWithTag("library_selected_tag_2").assertExists()

        composeRule.onNodeWithTag("library_selected_tags_reset").performClick()
        composeRule.runOnIdle { assertEquals(emptySet<Long>(), selectedIds) }
        composeRule.onNodeWithTag("library_selected_tag_filters")
            .assertDoesNotExist()
    }

    @Test
    fun sheetAppliesIndependentSelectionsImmediatelyAndClearKeepsItOpen() {
        var selectedIds by mutableStateOf<Set<Long>>(emptySet())
        var dismissed = false
        composeRule.setContent {
            MyLibraryTheme {
                LibraryTagFilterSheet(
                    tags = tags,
                    selectedIds = selectedIds,
                    onSelectionChange = { selectedIds = it },
                    onDismiss = { dismissed = true }
                )
            }
        }

        composeRule.onNodeWithText("标签筛选").assertExists()
        composeRule.onNodeWithTag("library_tag_filter_clear").assertExists()
        composeRule.onNodeWithText("重置").assertDoesNotExist()
        composeRule.onNodeWithText("确定").assertDoesNotExist()
        composeRule.onNodeWithTag("library_tag_option_1").assertExists()
        composeRule.onNodeWithTag("library_tag_option_2").assertExists()

        composeRule.onNodeWithTag("library_tag_option_1").performClick()
        composeRule.runOnIdle {
            assertEquals(setOf(1L), selectedIds)
            assertFalse(dismissed)
        }

        composeRule.onNodeWithTag("library_tag_option_2").performClick()
        composeRule.runOnIdle { assertEquals(setOf(1L, 2L), selectedIds) }

        composeRule.onNodeWithTag("library_tag_option_1").performClick()
        composeRule.runOnIdle { assertEquals(setOf(2L), selectedIds) }

        composeRule.onNodeWithTag("library_tag_root_3").performClick()
        composeRule.onNodeWithTag("library_tag_option_3").assertExists()
        composeRule.onNodeWithTag("library_tag_option_4").performClick()
        composeRule.runOnIdle { assertEquals(setOf(2L, 4L), selectedIds) }

        composeRule.onNodeWithTag("library_tag_option_3").performClick()
        composeRule.runOnIdle { assertEquals(setOf(2L, 3L, 4L), selectedIds) }

        composeRule.onNodeWithTag("library_tag_filter_clear").performClick()
        composeRule.runOnIdle {
            assertEquals(emptySet<Long>(), selectedIds)
            assertFalse(dismissed)
        }
        composeRule.onNodeWithText("标签筛选").assertExists()
    }

    private fun tag(
        id: Long,
        name: String,
        parentId: Long? = null
    ) = LibraryTag(
        id = id,
        name = name,
        parentId = parentId,
        sortOrder = id.toInt(),
        enabled = true
    )
}
