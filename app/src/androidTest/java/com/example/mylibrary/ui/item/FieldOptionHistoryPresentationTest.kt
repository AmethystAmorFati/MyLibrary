package com.example.mylibrary.ui.item

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.mylibrary.domain.model.FieldDataType
import com.example.mylibrary.ui.components.FieldRow
import com.example.mylibrary.ui.theme.MyLibraryTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FieldOptionHistoryPresentationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun editorFieldRowStillShowsDeletedHistoricalName() {
        composeRule.setContent {
            MyLibraryTheme {
                FieldRow(
                    label = "阅读方式",
                    value = "电子书",
                    editable = true,
                    onClick = {}
                )
            }
        }

        composeRule.onNodeWithText("阅读方式").assertExists()
        composeRule.onNodeWithText("电子书  ›").assertExists()
    }

    @Test
    fun selectionSheetHidesDeletedOptionAndUnchangedConfirmKeepsOriginal() {
        var confirmed: String? = null
        composeRule.setContent {
            MyLibraryTheme {
                FieldSelectionBottomSheet(
                    field = DynamicFieldInputState(
                        definitionId = 1,
                        name = "阅读方式",
                        dataType = FieldDataType.SINGLE_SELECT,
                        value = "电子书",
                        options = listOf("纸质书", "听书")
                    ),
                    multiple = false,
                    onConfirm = { confirmed = it },
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithText("电子书").assertDoesNotExist()
        composeRule.onNodeWithText("纸质书").assertExists()
        composeRule.onNodeWithText("听书").assertExists()
        composeRule.onNodeWithText("确定").performClick()
        composeRule.runOnIdle {
            assertEquals("电子书", confirmed)
        }
    }
}
