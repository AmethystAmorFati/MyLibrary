package com.example.mylibrary.ui.settings

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.mylibrary.ui.theme.MyLibraryTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ThemeManagementPresentationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun defaultIsFirstAndThemeRowsExposeOnlyManagementMetadata() {
        show(
            ThemeManagementUiState(
                themes = listOf(
                    defaultItem(),
                    ThemeListItem(
                        id = "paper.theme",
                        name = "纸张",
                        author = "作者",
                        version = "1.2",
                        status = ThemeListItemStatus.VALID,
                        isCurrent = false
                    )
                ),
                isLoading = false
            )
        )

        composeRule.onNodeWithTag("screen_theme_management").assertExists()
        composeRule.onNodeWithText("导入主题").assertExists()
        composeRule.onNodeWithText("默认主题").assertExists()
        composeRule.onNodeWithText("MyLibrary 默认主题").assertExists()
        composeRule.onNodeWithText("纸张").assertExists()
        composeRule.onNodeWithText("作者 · 1.2").assertExists()
        composeRule.onNodeWithText("checksum").assertDoesNotExist()
        composeRule.onNodeWithText("manifest.json").assertDoesNotExist()
    }

    @Test
    fun importingDisablesTheImportActionAndShowsNoFakePercentage() {
        show(
            ThemeManagementUiState(
                themes = listOf(defaultItem()),
                isLoading = false,
                isImporting = true
            )
        )

        composeRule.onNodeWithTag("theme_import").assertIsNotEnabled()
        composeRule.onNodeWithText("正在导入").assertExists()
        composeRule.onNodeWithText("0%").assertDoesNotExist()
    }

    @Test
    fun customThemeDeleteUsesTheDialogConfirmationPath() {
        var deletedId: String? = null
        show(
            ThemeManagementUiState(
                themes = listOf(
                    defaultItem(),
                    ThemeListItem(
                        id = "delete.theme",
                        name = "待删除",
                        author = null,
                        version = "1",
                        status = ThemeListItemStatus.VALID,
                        isCurrent = false
                    )
                ),
                isLoading = false
            ),
            onDeleteTheme = { deletedId = it }
        )

        composeRule.onNodeWithTag("theme_menu_delete.theme").performClick()
        composeRule.onNodeWithTag("theme_delete_delete.theme").performClick()
        composeRule.onNodeWithText("删除主题").assertExists()
        composeRule.onNodeWithTag("theme_confirm_delete").performClick()

        composeRule.runOnIdle {
            assertEquals("delete.theme", deletedId)
        }
    }

    private fun show(
        state: ThemeManagementUiState,
        onDeleteTheme: (String) -> Unit = {}
    ) {
        composeRule.setContent {
            MyLibraryTheme {
                ThemeManagementScreen(
                    state = state,
                    onImportSelected = {},
                    onApplyTheme = {},
                    onDeleteTheme = onDeleteTheme,
                    onMessageShown = {}
                )
            }
        }
    }

    private fun defaultItem() = ThemeListItem(
        id = null,
        name = "MyLibrary 默认主题",
        author = null,
        version = null,
        status = ThemeListItemStatus.DEFAULT,
        isCurrent = true
    )
}
