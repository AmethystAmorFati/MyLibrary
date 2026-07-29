package com.example.mylibrary.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.mylibrary.domain.model.LibraryItem
import com.example.mylibrary.domain.model.LibraryTag
import com.example.mylibrary.domain.model.LibraryViewMode
import com.example.mylibrary.ui.library.LibraryItemsView
import com.example.mylibrary.ui.settings.TagManagementScreen
import com.example.mylibrary.ui.settings.TagManagementUiState
import com.example.mylibrary.ui.theme.MyLibraryTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class Round4GPresentationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun libraryModesKeepItemsButPureCoverEmitsNoTitleText() {
        val title = "Round 4G unique title"
        var mode by mutableStateOf(LibraryViewMode.SHELF)
        val item = LibraryItem(
            id = 42,
            typeId = 1,
            typeName = "Book",
            title = title,
            creator = "Creator",
            coverPath = null,
            thumbnailPath = null,
            createdTime = 1,
            updatedTime = 1,
            currentStatusId = null,
            currentStatusName = null,
            latestRatingHalfStars = null
        )

        composeRule.setContent {
            MyLibraryTheme {
                LibraryItemsView(
                    items = listOf(item),
                    mode = mode,
                    gridColumns = 4,
                    coverColumns = 4,
                    displayFields = emptySet(),
                    dynamicFields = emptyList(),
                    onItemSelected = {},
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        assertTrue(
            composeRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        )
        composeRule.runOnIdle { mode = LibraryViewMode.LIST }
        assertTrue(
            composeRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        )

        composeRule.runOnIdle { mode = LibraryViewMode.COVER }
        composeRule.onNodeWithTag("library_cover_only_item_42").assertExists()
        composeRule.onNodeWithText(title).assertDoesNotExist()
    }

    @Test
    fun childTagCardsEndWithTheExistingAddAction() {
        val root = LibraryTag(
            id = 1,
            name = "文学",
            parentId = null,
            sortOrder = 0,
            enabled = true
        )
        val child = LibraryTag(
            id = 2,
            name = "小说",
            parentId = root.id,
            sortOrder = 0,
            enabled = true
        )
        composeRule.setContent {
            MyLibraryTheme {
                TagManagementScreen(
                    state = TagManagementUiState(
                        tags = listOf(root, child),
                        selectedRootId = root.id,
                        isLoading = false
                    ),
                    onBack = {},
                    onSelectRoot = {},
                    onCreateRoot = {},
                    onCreateChildren = { _, _ -> },
                    onRename = { _, _ -> },
                    onDelete = {},
                    onReorderRoots = {},
                    onReorderChildren = { _, _ -> }
                )
            }
        }

        val childBounds = composeRule
            .onNodeWithTag("child_tag_row_2")
            .fetchSemanticsNode()
            .boundsInRoot
        val addNode = composeRule.onNodeWithTag("add_child_tag_row")
        val addBounds = addNode.fetchSemanticsNode().boundsInRoot
        assertTrue(addBounds.top >= childBounds.bottom)

        addNode.performClick()
        composeRule.onNodeWithText("添加到「文学」").assertExists()
    }

    @Test
    fun childAddActionExistsWhenTheParentHasNoChildren() {
        val root = LibraryTag(
            id = 1,
            name = "文学",
            parentId = null,
            sortOrder = 0,
            enabled = true
        )
        composeRule.setContent {
            MyLibraryTheme {
                TagManagementScreen(
                    state = TagManagementUiState(
                        tags = listOf(root),
                        selectedRootId = root.id,
                        isLoading = false
                    ),
                    onBack = {},
                    onSelectRoot = {},
                    onCreateRoot = {},
                    onCreateChildren = { _, _ -> },
                    onRename = { _, _ -> },
                    onDelete = {},
                    onReorderRoots = {},
                    onReorderChildren = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithTag("add_child_tag_row").assertExists()
    }
}
