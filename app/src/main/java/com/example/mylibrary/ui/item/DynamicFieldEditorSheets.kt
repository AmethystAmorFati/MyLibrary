package com.example.mylibrary.ui.item

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.mylibrary.domain.model.FieldDataType
import com.example.mylibrary.domain.model.FieldValueParser
import com.example.mylibrary.domain.model.decodeFieldSelection
import com.example.mylibrary.domain.model.encodeFieldSelection
import com.example.mylibrary.ui.components.LibraryTextField
import com.example.mylibrary.ui.components.AppModalBottomSheet
import com.example.mylibrary.ui.components.noRippleClickable
import com.example.mylibrary.ui.components.StarRatingBar
import com.example.mylibrary.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FieldValueInputSheet(
    field: DynamicFieldInputState,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember(field.definitionId) { mutableStateOf(field.value) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            FieldSheetHeader(
                title = field.name,
                confirmEnabled = draft.isBlank() ||
                    field.dataType != FieldDataType.NUMBER ||
                    FieldValueParser.parseNumber(draft) != null,
                confirmAsIcon = true,
                onConfirm = {
                    onConfirm(draft.trim())
                    onDismiss()
                },
                onDismiss = onDismiss
            )
            LibraryTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text(field.name) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (field.dataType == FieldDataType.NUMBER) {
                        KeyboardType.Decimal
                    } else {
                        KeyboardType.Text
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FieldRatingBottomSheet(
    field: DynamicFieldInputState,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember(field.definitionId, field.value) {
        mutableStateOf(field.value.toIntOrNull())
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
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            FieldSheetHeader(
                title = field.name,
                confirmEnabled = true,
                onConfirm = {
                    onConfirm(draft?.toString().orEmpty())
                    onDismiss()
                },
                onDismiss = onDismiss
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                StarRatingBar(
                    ratingHalfStars = draft,
                    onRatingChange = { draft = it },
                    starSize = 30.dp
                )
            }
            if (draft != null) {
                Text(
                    text = "清空评分",
                    modifier = Modifier
                        .fillMaxWidth()
                        .noRippleClickable { draft = null }
                        .padding(vertical = 12.dp),
                    style = AppTheme.typography.button,
                    color = AppTheme.colors.textSecondary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FieldSelectionBottomSheet(
    field: DynamicFieldInputState,
    multiple: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val initialSelection = remember(field.definitionId, field.value) {
        if (multiple) {
            decodeFieldSelection(field.value).toSet()
        } else {
            setOfNotNull(field.value.takeIf(String::isNotBlank))
        }
    }
    var temporarySelection by remember(field.definitionId, field.value) {
        mutableStateOf(initialSelection)
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
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FieldSheetHeader(
                title = field.name,
                confirmEnabled = true,
                onConfirm = {
                    onConfirm(
                        confirmedFieldSelection(
                            originalValue = field.value,
                            initialSelection = initialSelection,
                            temporarySelection = temporarySelection,
                            activeOptions = field.options,
                            multiple = multiple
                        )
                    )
                    onDismiss()
                },
                onDismiss = onDismiss
            )
            if (field.options.isEmpty()) {
                Text(
                    text = "暂无可选项，请先在字段管理中添加。",
                    modifier = Modifier.padding(vertical = 18.dp),
                    style = AppTheme.typography.body,
                    color = AppTheme.colors.mutedText
                )
            } else {
                field.options.forEach { option ->
                    FieldSelectionOptionRow(
                        text = option,
                        selected = option in temporarySelection,
                        multiple = multiple,
                        onClick = {
                            temporarySelection = if (multiple) {
                                if (option in temporarySelection) {
                                    temporarySelection - option
                                } else {
                                    temporarySelection + option
                                }
                            } else {
                                if (option in temporarySelection) {
                                    emptySet()
                                } else {
                                    setOf(option)
                                }
                            }
                        }
                    )
                }
            }
            if (temporarySelection.isNotEmpty()) {
                Text(
                    text = "清空选择",
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("clear_field_selection")
                        .noRippleClickable { temporarySelection = emptySet() }
                        .padding(vertical = 12.dp),
                    style = AppTheme.typography.button,
                    color = AppTheme.colors.textSecondary
                )
            }
        }
    }
}

internal fun confirmedFieldSelection(
    originalValue: String,
    initialSelection: Set<String>,
    temporarySelection: Set<String>,
    activeOptions: List<String>,
    multiple: Boolean
): String {
    if (temporarySelection == initialSelection) return originalValue
    val ordered = activeOptions.filter { it in temporarySelection }
    return if (multiple) {
        encodeFieldSelection(ordered)
    } else {
        ordered.firstOrNull().orEmpty()
    }
}

@Composable
private fun FieldSheetHeader(
    title: String,
    confirmEnabled: Boolean,
    confirmAsIcon: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "取消",
            modifier = Modifier
                .noRippleClickable(onClick = onDismiss)
                .padding(horizontal = 6.dp, vertical = 12.dp),
            style = AppTheme.typography.button,
            color = AppTheme.colors.textSecondary
        )
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = AppTheme.typography.sectionTitle,
            color = AppTheme.colors.textPrimary
        )
        if (confirmAsIcon) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .testTag("field_value_confirm")
                    .noRippleClickable(
                        enabled = confirmEnabled,
                        onClick = onConfirm
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = "确认",
                    tint = if (confirmEnabled) {
                        AppTheme.colors.accent
                    } else {
                        AppTheme.colors.mutedText
                    }
                )
            }
        } else {
            Text(
                text = "确定",
                modifier = Modifier
                    .noRippleClickable(enabled = confirmEnabled, onClick = onConfirm)
                    .padding(horizontal = 6.dp, vertical = 12.dp),
                style = AppTheme.typography.button,
                color = if (confirmEnabled) {
                    AppTheme.colors.accent
                } else {
                    AppTheme.colors.mutedText
                }
            )
        }
    }
}

@Composable
private fun FieldSelectionOptionRow(
    text: String,
    selected: Boolean,
    multiple: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .noRippleClickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = AppTheme.typography.body,
            color = AppTheme.colors.textPrimary
        )
        Box(
            modifier = Modifier.size(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when {
                    multiple && selected -> Icons.Outlined.CheckBox
                    multiple -> Icons.Outlined.CheckBoxOutlineBlank
                    selected -> Icons.Outlined.RadioButtonChecked
                    else -> Icons.Outlined.RadioButtonUnchecked
                },
                contentDescription = null,
                tint = if (selected) {
                    AppTheme.colors.accent
                } else {
                    AppTheme.colors.mutedText
                }
            )
        }
    }
}
