package com.example.mylibrary.ui.item

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
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
import com.example.mylibrary.ui.components.AppBottomActionBar
import com.example.mylibrary.ui.components.AppConfirmDialog
import com.example.mylibrary.ui.components.AppScreenContainer
import com.example.mylibrary.ui.components.BottomAction
import com.example.mylibrary.ui.components.SecondaryPageHeader
import com.example.mylibrary.ui.theme.AppTheme
import com.example.mylibrary.ui.theme.FloatingActionBarContentPadding
import com.example.mylibrary.ui.theme.ScreenHorizontalPadding
import com.example.mylibrary.ui.theme.TopBarToContentGap

@Composable
fun ItemEditorScaffold(
    screenTitle: String,
    state: ItemEditorUiState,
    destinationEnterCompleted: Boolean,
    showQuoteChapter: Boolean,
    showQuotePage: Boolean,
    allowTypeSelection: Boolean,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    onTypeSelected: (Long) -> Unit,
    onTitleChange: (String) -> Unit,
    onCreatorChange: (String) -> Unit,
    onCoverSelected: (String) -> Unit,
    onRemoveCover: () -> Unit,
    onStatusSelected: (Long) -> Unit,
    onTagSelectionChanged: (Set<Long>) -> Unit,
    onCreateTag: (String, Long?) -> Unit,
    onDynamicValueChange: (Long, String) -> Unit,
    onRecordDraftCompleted: (RecordDraftUiState) -> Unit,
    onRecordDraftDeleted: (String) -> Unit,
    onCreateQuoteDraft: () -> QuoteDraftUiState,
    onQuoteDraftCompleted: (QuoteDraftUiState) -> Unit,
    onQuoteDraftDeleted: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDiscardConfirmation by remember { mutableStateOf(false) }
    val contentMode = editorContentMode(
        state = state,
        destinationEnterCompleted = destinationEnterCompleted
    )

    LaunchedEffect(state.completedItemId) {
        state.completedItemId?.let(onSaved)
    }

    val requestExit = {
        if (state.hasUnsavedChanges) {
            showDiscardConfirmation = true
        } else {
            onBack()
        }
    }
    BackHandler(onBack = requestExit)

    AppScreenContainer(modifier = modifier) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                SecondaryPageHeader(
                    title = screenTitle,
                    onBack = requestExit
                )
                when (contentMode) {
                    EditorContentMode.LOADING -> EditorLoadingState(
                        modifier = Modifier.weight(1f)
                    )
                    EditorContentMode.ERROR -> EditorLoadErrorState(
                        message = requireNotNull(state.errorMessage),
                        modifier = Modifier.weight(1f)
                    )
                    EditorContentMode.FORM -> ItemForm(
                        state = state,
                        showQuoteChapter = showQuoteChapter,
                        showQuotePage = showQuotePage,
                        allowTypeSelection = allowTypeSelection,
                        onTypeSelected = onTypeSelected,
                        onTitleChange = onTitleChange,
                        onCreatorChange = onCreatorChange,
                        onCoverSelected = onCoverSelected,
                        onRemoveCover = onRemoveCover,
                        onStatusSelected = onStatusSelected,
                        onTagSelectionChanged = onTagSelectionChanged,
                        onCreateTag = onCreateTag,
                        onDynamicValueChange = onDynamicValueChange,
                        onRecordDraftCompleted = onRecordDraftCompleted,
                        onRecordDraftDeleted = onRecordDraftDeleted,
                        onCreateQuoteDraft = onCreateQuoteDraft,
                        onQuoteDraftCompleted = onQuoteDraftCompleted,
                        onQuoteDraftDeleted = onQuoteDraftDeleted,
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .imePadding()
                            .padding(
                                start = ScreenHorizontalPadding,
                                end = ScreenHorizontalPadding,
                                top = TopBarToContentGap,
                                bottom = FloatingActionBarContentPadding
                            )
                            .testTag(
                                if (allowTypeSelection) {
                                    "screen_add_item"
                                } else {
                                    "screen_edit_item"
                                }
                            )
                    )
                }
            }
            AppBottomActionBar(
                actions = listOf(
                    BottomAction(
                        text = if (state.isSaving) "正在保存" else "保存",
                        icon = Icons.Outlined.Check,
                        enabled = editorSaveEnabled(
                            state = state,
                            contentMode = contentMode
                        ),
                        onClick = onSave,
                        testTag = "save_item_button"
                    )
                ),
                modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
            )
        }
    }

    if (showDiscardConfirmation) {
        AppConfirmDialog(
            title = "放弃修改？",
            message = "未保存的内容将丢失。",
            confirmText = "放弃",
            dismissText = "继续编辑",
            destructive = true,
            onConfirm = {
                showDiscardConfirmation = false
                onBack()
            },
            onDismiss = { showDiscardConfirmation = false }
        )
    }
}

internal val ItemEditorUiState.isInitialLoadFailed: Boolean
    get() = !isLoading &&
        errorMessage != null &&
        types.isEmpty() &&
        selectedTypeId == null

internal enum class EditorContentMode {
    LOADING,
    ERROR,
    FORM
}

internal fun editorContentMode(
    state: ItemEditorUiState,
    destinationEnterCompleted: Boolean
): EditorContentMode = when {
    state.isLoading || !destinationEnterCompleted -> EditorContentMode.LOADING
    state.isInitialLoadFailed -> EditorContentMode.ERROR
    else -> EditorContentMode.FORM
}

internal fun editorSaveEnabled(
    state: ItemEditorUiState,
    contentMode: EditorContentMode
): Boolean =
    contentMode == EditorContentMode.FORM &&
        !state.isSaving &&
        !state.isProcessingCover

@Composable
private fun EditorLoadingState(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag("item_editor_loading")
    )
}

@Composable
private fun EditorLoadErrorState(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(ScreenHorizontalPadding)
            .testTag("item_editor_load_error"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = AppTheme.colors.textSecondary,
            style = AppTheme.typography.body
        )
    }
}
