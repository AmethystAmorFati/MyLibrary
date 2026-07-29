package com.example.mylibrary.ui.item

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun EditItemScreen(
    state: ItemEditorUiState,
    destinationEnterCompleted: Boolean,
    showQuoteChapter: Boolean = true,
    showQuotePage: Boolean = true,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
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
    onCreateQuoteDraft: () -> QuoteDraftUiState = {
        newQuoteDraftUiState("new-quote", 0L)
    },
    onQuoteDraftCompleted: (QuoteDraftUiState) -> Unit = {},
    onQuoteDraftDeleted: (String) -> Unit = {},
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    ItemEditorScaffold(
        screenTitle = "编辑作品",
        state = state,
        destinationEnterCompleted = destinationEnterCompleted,
        showQuoteChapter = showQuoteChapter,
        showQuotePage = showQuotePage,
        allowTypeSelection = false,
        onBack = onBack,
        onSaved = onSaved,
        onTypeSelected = {},
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
        onSave = onSave,
        modifier = modifier
    )
}
