package com.example.mylibrary.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.example.mylibrary.MainActivity
import org.junit.Rule
import org.junit.Test

class ItemCrudFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun addEditAndSoftDeleteBook() {
        val originalTitle = "Round 4 Test ${System.currentTimeMillis()}"
        val editedTitle = "$originalTitle Edited"

        composeRule.onNodeWithContentDescription("资料库").performClick()
        composeRule.onNodeWithTag("add_item_fab").performClick()
        composeRule.onNodeWithTag("screen_add_item").assertIsDisplayed()

        composeRule.onNodeWithTag("item_title_input").performTextInput(originalTitle)
        composeRule.onNodeWithTag("item_creator_input").performTextInput("Test Author")
        composeRule.onNodeWithTag("save_item_button").performClick()
        waitForTag("screen_item_detail")
        composeRule.onNodeWithText(originalTitle).assertIsDisplayed()

        composeRule.onNodeWithText("编辑").performClick()
        composeRule.onNodeWithTag("item_title_input").performTextClearance()
        composeRule.onNodeWithTag("item_title_input").performTextInput(editedTitle)
        composeRule.onNodeWithTag("save_item_button").performClick()
        waitForTag("screen_item_detail")
        composeRule.onNodeWithText(editedTitle).assertIsDisplayed()

        composeRule.onNodeWithTag("delete_item_button").performClick()
        composeRule.onNodeWithTag("confirm_delete_button").performClick()
        waitForTag("screen_library")
        composeRule.onAllNodesWithText(editedTitle).assertCountEquals(0)
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 8_000) {
            composeRule.onAllNodesWithTag(tag)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }
}
