package com.example.mylibrary.ui.item

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.mylibrary.domain.model.ItemType
import com.example.mylibrary.ui.theme.MyLibraryTheme
import org.junit.Rule
import org.junit.Test

class ItemEditorTransitionGateInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val loadedState = ItemEditorUiState(
        types = listOf(ItemType(id = 1L, name = "书籍", sortOrder = 0)),
        selectedTypeId = 1L,
        isLoading = false
    )

    @Test
    fun addScreenKeepsStableLoadingShellBeforeDestinationResume() {
        setAddContent(loadedState, destinationEnterCompleted = false)

        composeRule.onNodeWithText("新增作品").assertIsDisplayed()
        composeRule.onNodeWithTag("item_editor_loading").assertIsDisplayed()
        composeRule.onNodeWithTag("screen_add_item").assertDoesNotExist()
        composeRule.onNodeWithTag("save_item_button").assertIsNotEnabled()
    }

    @Test
    fun editScreenKeepsStableLoadingShellBeforeDestinationResume() {
        setEditContent(
            loadedState.copy(editingItemId = 42L),
            destinationEnterCompleted = false
        )

        composeRule.onNodeWithText("编辑作品").assertIsDisplayed()
        composeRule.onNodeWithTag("item_editor_loading").assertIsDisplayed()
        composeRule.onNodeWithTag("screen_edit_item").assertDoesNotExist()
        composeRule.onNodeWithTag("save_item_button").assertIsNotEnabled()
    }

    @Test
    fun resumedDestinationStillWaitsForEditorData() {
        setAddContent(
            ItemEditorUiState(isLoading = true),
            destinationEnterCompleted = true
        )

        composeRule.onNodeWithTag("item_editor_loading").assertIsDisplayed()
        composeRule.onNodeWithTag("screen_add_item").assertDoesNotExist()
        composeRule.onNodeWithTag("save_item_button").assertIsNotEnabled()
    }

    @Test
    fun addFormMountsAfterBothConditionsAreReady() {
        setAddContent(loadedState, destinationEnterCompleted = true)
        composeRule.onNodeWithTag("screen_add_item").assertIsDisplayed()
    }

    @Test
    fun editFormMountsAfterBothConditionsAreReady() {
        setEditContent(
            loadedState.copy(editingItemId = 42L),
            destinationEnterCompleted = true
        )
        composeRule.onNodeWithTag("screen_edit_item").assertIsDisplayed()
    }

    private fun setAddContent(
        state: ItemEditorUiState,
        destinationEnterCompleted: Boolean
    ) {
        composeRule.setContent {
            MyLibraryTheme {
                AddItemScreen(
                    state = state,
                    destinationEnterCompleted = destinationEnterCompleted,
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
                    onSave = {}
                )
            }
        }
    }

    private fun setEditContent(
        state: ItemEditorUiState,
        destinationEnterCompleted: Boolean
    ) {
        composeRule.setContent {
            MyLibraryTheme {
                EditItemScreen(
                    state = state,
                    destinationEnterCompleted = destinationEnterCompleted,
                    onBack = {},
                    onSaved = {},
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
                    onQuoteDraftCompleted = {},
                    onQuoteDraftDeleted = {},
                    onSave = {}
                )
            }
        }
    }
}
