package com.example.mylibrary.ui.item

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.mylibrary.ui.components.AppModalBottomSheet
import com.example.mylibrary.ui.components.LibraryTextField
import com.example.mylibrary.ui.components.noRippleClickable
import com.example.mylibrary.ui.theme.AppTheme
import com.example.mylibrary.ui.theme.AppDanger
import com.example.mylibrary.ui.theme.ScreenHorizontalPadding

internal val QuoteEditorContentHeight = 172.dp
internal val QuoteEditorLabelWidth = 52.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuoteDraftSheet(
    initial: QuoteDraftUiState,
    showChapter: Boolean,
    showPage: Boolean,
    onSave: (QuoteDraftUiState) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var content by rememberSaveable(initial.localKey) {
        mutableStateOf(initial.content)
    }
    var chapter by rememberSaveable(initial.localKey) {
        mutableStateOf(initial.chapter)
    }
    var page by rememberSaveable(initial.localKey) {
        mutableStateOf(initial.page)
    }
    var showContentError by rememberSaveable(initial.localKey) {
        mutableStateOf(false)
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(
                    start = ScreenHorizontalPadding,
                    end = ScreenHorizontalPadding,
                    top = 10.dp,
                    bottom = 18.dp
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (onDelete == null) "新增摘录" else "编辑摘录",
                    modifier = Modifier.weight(1f),
                    style = AppTheme.typography.sectionTitle,
                    color = AppTheme.colors.textPrimary
                )
                onDelete?.let { delete ->
                    SheetIconAction(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "删除摘录草稿",
                        tint = AppDanger,
                        testTag = "quote_delete_action"
                    ) {
                        delete()
                        onDismiss()
                    }
                }
                SheetIconAction(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = "保存摘录草稿",
                    tint = AppTheme.colors.accent,
                    testTag = "quote_save_action"
                ) {
                    if (content.isBlank()) {
                        showContentError = true
                    } else {
                        onSave(
                            initial.copy(
                                content = content,
                                chapter = chapter,
                                page = page
                            )
                        )
                        onDismiss()
                    }
                }
            }

            LibraryTextField(
                value = content,
                onValueChange = {
                    content = it
                    showContentError = false
                },
                label = { Text("请输入摘录内容") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(QuoteEditorContentHeight)
                    .testTag("quote_content_input"),
                singleLine = false,
                minLines = 8,
                maxLines = 8,
                isError = showContentError
            )
            if (showContentError) {
                Text(
                    text = "请填写摘录内容",
                    style = AppTheme.typography.metadata,
                    color = AppDanger
                )
            }
            if (showChapter) {
                QuoteEditorSingleLineRow(
                    label = "章节",
                    value = chapter,
                    placeholder = "第一章",
                    testTag = "quote_chapter_input",
                    keyboardType = KeyboardType.Text,
                    onValueChange = { chapter = it }
                )
            }
            if (showPage) {
                QuoteEditorSingleLineRow(
                    label = "页码",
                    value = page,
                    placeholder = "23-25",
                    testTag = "quote_page_input",
                    keyboardType = KeyboardType.Text,
                    onValueChange = { page = it }
                )
            }
        }
    }
}

@Composable
private fun QuoteEditorSingleLineRow(
    label: String,
    value: String,
    placeholder: String,
    testTag: String,
    keyboardType: KeyboardType,
    onValueChange: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.width(QuoteEditorLabelWidth),
            style = AppTheme.typography.body,
            color = AppTheme.colors.textSecondary
        )
        LibraryTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(placeholder) },
            modifier = Modifier
                .weight(1f)
                .testTag(testTag),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = keyboardType
            )
        )
    }
}

@Composable
private fun SheetIconAction(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: androidx.compose.ui.graphics.Color,
    testTag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .testTag(testTag)
            .noRippleClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}
