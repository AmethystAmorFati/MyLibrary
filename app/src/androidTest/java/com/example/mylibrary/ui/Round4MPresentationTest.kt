package com.example.mylibrary.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.example.mylibrary.domain.model.TrashItem
import com.example.mylibrary.ui.settings.TrashScreen
import com.example.mylibrary.ui.settings.TrashUiState
import com.example.mylibrary.ui.theme.MyLibraryTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class Round4MPresentationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun normalModeOnlyShowsCoverTitleAndClearIcon() {
        showTrash(items = listOf(item(7, "极简标题")))

        composeRule.onNodeWithText("回收站").assertExists()
        composeRule.onNodeWithContentDescription("清空回收站").assertExists()
        composeRule.onNodeWithText("清空").assertDoesNotExist()
        composeRule.onNodeWithText("极简标题").assertExists()
        composeRule.onNodeWithText("作者7").assertDoesNotExist()
        composeRule.onNodeWithText("Book").assertDoesNotExist()
        composeRule.onNodeWithText("书").assertDoesNotExist()
        composeRule.onNodeWithTag("trash_item_more_7").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("永久删除所选作品").assertDoesNotExist()
    }

    @Test
    fun emptyTrashHasNoClearAction() {
        showTrash(items = emptyList())

        composeRule.onNodeWithTag("trash_empty_state").assertExists()
        composeRule.onNodeWithContentDescription("清空回收站").assertDoesNotExist()
        composeRule.onNodeWithText("清空").assertDoesNotExist()
    }

    @Test
    fun clickRestoresWhileLongClickStartsSelectionWithoutRestoring() {
        var selectedIds by mutableStateOf(emptySet<Long>())
        var restoredId: Long? = null
        val items = listOf(item(7, "第一部"), item(8, "第二部"))
        composeRule.setContent {
            MyLibraryTheme {
                TrashScreen(
                    state = TrashUiState(
                        items = items,
                        selectedItemIds = selectedIds,
                        isLoading = false
                    ),
                    onBack = {},
                    onRestore = { restoredId = it },
                    onStartSelection = { selectedIds = setOf(it) },
                    onToggleSelection = {
                        selectedIds = if (it in selectedIds) {
                            selectedIds - it
                        } else {
                            selectedIds + it
                        }
                    },
                    onClearSelection = { selectedIds = emptySet() },
                    onPermanentlyDeleteSelected = {},
                    onEmptyTrash = {}
                )
            }
        }

        composeRule.onNodeWithTag("trash_item_7").performClick()
        composeRule.runOnIdle { assertEquals(7L, restoredId) }
        restoredId = null

        composeRule.onNodeWithTag("trash_item_7")
            .performTouchInput { longClick() }
        composeRule.onNodeWithText("已选择 1 项").assertExists()
        composeRule.onNodeWithTag("trash_item_selected_7").assertExists()
        composeRule.onNodeWithContentDescription("清空回收站").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(null, restoredId) }

        composeRule.onNodeWithTag("trash_item_8").performClick()
        composeRule.onNodeWithText("已选择 2 项").assertExists()
        composeRule.onNodeWithTag("trash_item_7").performClick()
        composeRule.onNodeWithText("已选择 1 项").assertExists()
        composeRule.onNodeWithTag("trash_item_8").performClick()
        composeRule.onNodeWithText("回收站").assertExists()
        composeRule.onNodeWithTag("trash_selection_top_bar").assertDoesNotExist()
    }

    @Test
    fun selectionCloseAndScrollDoNotRestoreItems() {
        var selectedIds by mutableStateOf(setOf(7L))
        var restoreCount = 0
        val items = (7L..20L).map { item(it, "作品$it") }
        composeRule.setContent {
            MyLibraryTheme {
                TrashScreen(
                    state = TrashUiState(
                        items = items,
                        selectedItemIds = selectedIds,
                        isLoading = false
                    ),
                    onBack = {},
                    onRestore = { restoreCount += 1 },
                    onStartSelection = { selectedIds = setOf(it) },
                    onToggleSelection = {},
                    onClearSelection = { selectedIds = emptySet() },
                    onPermanentlyDeleteSelected = {},
                    onEmptyTrash = {}
                )
            }
        }

        composeRule.onNodeWithTag("trash_clear_selection").performClick()
        composeRule.onNodeWithText("回收站").assertExists()
        composeRule.onNodeWithTag("trash_list").performTouchInput { swipeUp() }
        composeRule.runOnIdle {
            assertFalse(selectedIds.isNotEmpty())
            assertEquals(0, restoreCount)
        }
    }

    @Test
    fun oneOrManySelectionsUseOneDangerousConfirmation() {
        var selectedIds by mutableStateOf(setOf(7L))
        var deleteCalls = 0
        val items = listOf(item(7, "单项标题"), item(8, "另一项"))
        composeRule.setContent {
            MyLibraryTheme {
                TrashScreen(
                    state = TrashUiState(
                        items = items,
                        selectedItemIds = selectedIds,
                        isLoading = false
                    ),
                    onBack = {},
                    onRestore = {},
                    onStartSelection = { selectedIds = setOf(it) },
                    onToggleSelection = {},
                    onClearSelection = { selectedIds = emptySet() },
                    onPermanentlyDeleteSelected = { deleteCalls += 1 },
                    onEmptyTrash = {}
                )
            }
        }

        composeRule.onNodeWithTag("trash_delete_selected_action").performClick()
        composeRule.onNodeWithText(
            "《单项标题》及其记录、摘录和关联数据将永久删除，无法恢复。"
        ).assertExists()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.runOnIdle { assertEquals(0, deleteCalls) }

        composeRule.runOnIdle { selectedIds = setOf(7L, 8L) }
        composeRule.onNodeWithTag("trash_delete_selected_action").performClick()
        composeRule.onNodeWithText(
            "将永久删除选中的 2 个作品及其记录、摘录和关联数据，无法恢复。"
        ).assertExists()
        composeRule.onNodeWithText("永久删除").performClick()
        composeRule.runOnIdle { assertEquals(1, deleteCalls) }
    }

    @Test
    fun operationInProgressDisablesRepeatedDeleteAction() {
        var deleteCalls = 0
        composeRule.setContent {
            MyLibraryTheme {
                TrashScreen(
                    state = TrashUiState(
                        items = listOf(item(7, "处理中")),
                        selectedItemIds = setOf(7),
                        isLoading = false,
                        isOperationRunning = true
                    ),
                    onBack = {},
                    onRestore = {},
                    onStartSelection = {},
                    onToggleSelection = {},
                    onClearSelection = {},
                    onPermanentlyDeleteSelected = { deleteCalls += 1 },
                    onEmptyTrash = {}
                )
            }
        }

        composeRule.onNodeWithTag("trash_delete_selected_action").performClick()
        composeRule.onNodeWithText("永久删除？").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(0, deleteCalls) }
    }

    @Test
    fun clearTrashConfirmsTheWholeCountOnce() {
        var emptyCalls = 0
        val items = listOf(item(7, "第一部"), item(8, "第二部"))
        composeRule.setContent {
            MyLibraryTheme {
                TrashScreen(
                    state = TrashUiState(items = items, isLoading = false),
                    onBack = {},
                    onRestore = {},
                    onStartSelection = {},
                    onToggleSelection = {},
                    onClearSelection = {},
                    onPermanentlyDeleteSelected = {},
                    onEmptyTrash = { emptyCalls += 1 }
                )
            }
        }

        composeRule.onNodeWithTag("trash_empty_action").performClick()
        composeRule.onNodeWithTag("trash_selection_top_bar").assertDoesNotExist()
        composeRule.onNodeWithText(
            "将永久删除回收站中的 2 个作品及其记录、摘录和关联数据，无法恢复。"
        ).assertExists()
        composeRule.onNodeWithTag("trash_confirm_empty").performClick()
        composeRule.runOnIdle { assertEquals(1, emptyCalls) }
    }

    private fun showTrash(items: List<TrashItem>) {
        composeRule.setContent {
            MyLibraryTheme {
                TrashScreen(
                    state = TrashUiState(items = items, isLoading = false),
                    onBack = {},
                    onRestore = {},
                    onStartSelection = {},
                    onToggleSelection = {},
                    onClearSelection = {},
                    onPermanentlyDeleteSelected = {},
                    onEmptyTrash = {}
                )
            }
        }
    }

    private fun item(id: Long, title: String) = TrashItem(
        id = id,
        typeId = 1,
        typeName = "Book",
        title = title,
        creator = "作者$id",
        coverPath = null,
        thumbnailPath = null,
        deletedAt = id
    )
}
