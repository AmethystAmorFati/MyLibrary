package com.example.mylibrary.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.mylibrary.domain.model.LibraryViewMode
import com.example.mylibrary.ui.components.MainPageLayout
import com.example.mylibrary.ui.theme.AppTheme

internal object LibraryPresentationPolicy {
    const val usesAlphaTransition = false

    fun shouldShowEmpty(isLoading: Boolean, itemCount: Int): Boolean =
        !isLoading && itemCount == 0
}

@Composable
fun LibraryScreen(
    uiState: LibraryUiState,
    onQueryChange: (String) -> Unit,
    onSearchOpen: () -> Unit,
    onSearchClose: () -> Unit,
    onStatusSelected: (Long?) -> Unit,
    onTagsSelected: (Set<Long>) -> Unit,
    onViewModeSelected: (LibraryViewMode) -> Unit,
    onListFieldsChanged: (Set<String>) -> Unit,
    onItemSelected: (Long) -> Unit,
    isPageVisible: Boolean,
    modifier: Modifier = Modifier
) {
    var showTagFilter by remember { mutableStateOf(false) }
    var showListFields by remember { mutableStateOf(false) }
    LaunchedEffect(
        isPageVisible,
        uiState.isSearchActive,
        uiState.query
    ) {
        if (
            !isPageVisible &&
            uiState.isSearchActive &&
            uiState.query.isBlank()
        ) {
            onSearchClose()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("screen_library")
    ) {
        MainPageLayout {
            LibraryTopBar(
                isSearchActive = uiState.isSearchActive,
                isPageVisible = isPageVisible,
                query = uiState.query,
                onQueryChange = onQueryChange,
                onSearchOpen = onSearchOpen,
                onSearchClose = onSearchClose
            )
            LibraryFilterBar(
                statuses = uiState.statuses,
                selectedStatusId = uiState.selectedStatusId,
                viewMode = uiState.viewMode,
                onStatusSelected = onStatusSelected,
                onTagFilter = { showTagFilter = true },
                onViewModeCycle = {
                    val next = LibraryViewMode.entries[
                        (uiState.viewMode.ordinal + 1) % LibraryViewMode.entries.size
                    ]
                    onViewModeSelected(next)
                },
                onConfigureList = { showListFields = true }
            )
            LibrarySelectedTagFilterRow(
                tags = uiState.tags,
                selectedIds = uiState.selectedTagIds,
                onSelectionChange = onTagsSelected
            )
            if (
                LibraryPresentationPolicy.shouldShowEmpty(
                    isLoading = uiState.isLoading,
                    itemCount = uiState.items.size
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (uiState.query.isBlank()) {
                            "还没有符合条件的作品"
                        } else {
                            "没有匹配的作品"
                        },
                        color = AppTheme.colors.mutedText,
                        style = AppTheme.typography.body
                    )
                }
            } else {
                LibraryItemsView(
                    items = uiState.items,
                    mode = uiState.viewMode,
                    gridColumns = uiState.gridColumns,
                    coverColumns = uiState.coverColumns,
                    displayFields = uiState.listDisplayFields,
                    dynamicFields = uiState.dynamicFields,
                    showTotalDuration = uiState.showTotalDuration,
                    onItemSelected = onItemSelected,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    if (showTagFilter) {
        LibraryTagFilterSheet(
            tags = uiState.tags,
            selectedIds = uiState.selectedTagIds,
            onSelectionChange = onTagsSelected,
            onDismiss = { showTagFilter = false }
        )
    }
    if (showListFields) {
        ListFieldConfigSheet(
            selectedFields = uiState.listDisplayFields,
            dynamicFields = uiState.dynamicFields,
            onSave = onListFieldsChanged,
            onDismiss = { showListFields = false }
        )
    }
}
