package com.example.mylibrary.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.mylibrary.ui.components.noRippleClickable
import com.example.mylibrary.ui.components.AppThemeSurface
import com.example.mylibrary.ui.theme.AppDanger
import com.example.mylibrary.ui.theme.AppTheme
import com.example.mylibrary.ui.theme.LibraryShapes
import com.example.mylibrary.ui.theme.SurfaceRole

@Composable
internal fun TagNameInputDialog(
    title: String,
    initialValue: String,
    existingNames: List<String>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    placeholder: String = "输入标签名称",
    emptyMessage: String = "标签名称不能为空",
    duplicateMessage: String = "同级标签名称已存在"
) {
    var value by rememberSaveable(
        initialValue,
        stateSaver = TextFieldValue.Saver
    ) {
        mutableStateOf(
            TextFieldValue(
                text = initialValue,
                selection = TextRange(0, initialValue.length)
            )
        )
    }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    fun submit() {
        val normalized = value.text.trim()
        when {
            normalized.isEmpty() -> error = emptyMessage
            existingNames.any { it.equals(normalized, ignoreCase = true) } ->
                error = duplicateMessage
            else -> {
                onConfirm(normalized)
                onDismiss()
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        ManagementDialogSurface {
            Text(
                text = title,
                style = AppTheme.typography.sectionTitle,
                color = AppTheme.colors.textPrimary
            )
            Spacer(Modifier.height(16.dp))
            CompactManagementInput(
                value = value,
                onValueChange = {
                    value = it
                    error = null
                },
                placeholder = placeholder,
                submitEnabled = value.text.isNotBlank(),
                onSubmit = ::submit
            )
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = it,
                    style = AppTheme.typography.metadata,
                    color = AppTheme.colors.mutedText
                )
            }
        }
    }
}

@Composable
internal fun TagBatchAddDialog(
    rootName: String,
    existingNames: List<String>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var value by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    var pendingNames by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current

    fun addPending() {
        val normalized = value.text.trim()
        when {
            normalized.isEmpty() -> error = "标签名称不能为空"
            pendingNames.any { it.equals(normalized, ignoreCase = true) } ->
                error = "本次已添加同名标签"
            existingNames.any { it.equals(normalized, ignoreCase = true) } ->
                error = "当前一级标签下已存在同名标签"
            else -> {
                pendingNames = pendingNames + normalized
                value = TextFieldValue("")
                error = null
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        ManagementDialogSurface {
            Text(
                text = "添加到「$rootName」",
                style = AppTheme.typography.sectionTitle,
                color = AppTheme.colors.textPrimary
            )
            Spacer(Modifier.height(16.dp))
            CompactManagementInput(
                value = value,
                onValueChange = {
                    value = it
                    error = null
                },
                placeholder = "输入二级标签名称",
                submitEnabled = value.text.isNotBlank(),
                onSubmit = ::addPending
            )
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = it,
                    style = AppTheme.typography.metadata,
                    color = AppTheme.colors.mutedText
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = "已添加",
                style = AppTheme.typography.metadata,
                color = AppTheme.colors.mutedText
            )
            Spacer(Modifier.height(8.dp))
            if (pendingNames.isEmpty()) {
                Text(
                    text = "还没有待添加标签",
                    style = AppTheme.typography.body,
                    color = AppTheme.colors.mutedText
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pendingNames.forEach { name ->
                        PendingTagCapsule(
                            name = name,
                            onRemove = { pendingNames = pendingNames - name }
                        )
                    }
                }
            }
            Spacer(Modifier.height(22.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = "完成",
                    modifier = Modifier
                        .noRippleClickable(enabled = pendingNames.isNotEmpty()) {
                            keyboard?.hide()
                            onConfirm(pendingNames.toList())
                            onDismiss()
                        }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    style = AppTheme.typography.button,
                    color = if (pendingNames.isNotEmpty()) {
                        AppTheme.colors.accent
                    } else {
                        AppTheme.colors.mutedText
                    }
                )
            }
        }
    }
}

@Composable
internal fun TagActionDialog(
    tagName: String,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    renameText: String = "重命名",
    deleteText: String = "删除"
) {
    Dialog(onDismissRequest = onDismiss) {
        AppThemeSurface(
            role = SurfaceRole.DIALOG,
            modifier = Modifier.widthIn(max = 280.dp),
            shape = LibraryShapes.medium,
            shadowElevation = 0.dp,
            tonalElevation = 0.dp
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    text = tagName,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    maxLines = 1,
                    style = AppTheme.typography.metadata,
                    color = AppTheme.colors.mutedText
                )
                HorizontalDivider(color = AppTheme.colors.subtleBorder)
                TagActionRow(
                    text = renameText,
                    color = AppTheme.colors.textPrimary,
                    onClick = onRename
                )
                TagActionRow(
                    text = deleteText,
                    color = AppDanger,
                    onClick = onDelete
                )
            }
        }
    }
}

@Composable
private fun TagActionRow(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        style = AppTheme.typography.body,
        color = color
    )
}

@Composable
internal fun ManagementDialogSurface(
    content: @Composable ColumnScope.() -> Unit
) {
    AppThemeSurface(
        role = SurfaceRole.DIALOG,
        modifier = Modifier
            .fillMaxWidth(0.88f)
            .widthIn(max = 360.dp),
        shape = LibraryShapes.large,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            content = content
        )
    }
}

@Composable
internal fun CompactManagementInput(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    submitEnabled: Boolean,
    onSubmit: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val colors = AppTheme.colors

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppThemeSurface(
            role = SurfaceRole.CARD,
            modifier = Modifier
                .weight(1f)
                .height(46.dp),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, colors.border),
            shadowElevation = 0.dp,
            tonalElevation = 0.dp
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .padding(horizontal = 13.dp, vertical = 11.dp),
                singleLine = true,
                textStyle = AppTheme.typography.input.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { if (submitEnabled) onSubmit() }
                ),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.text.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = AppTheme.typography.input,
                                color = colors.mutedText
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            modifier = Modifier
                .size(40.dp)
                .noRippleClickable(enabled = submitEnabled, onClick = onSubmit),
            shape = RoundedCornerShape(12.dp),
            color = if (submitEnabled) colors.accent else colors.subtleCard,
            shadowElevation = 0.dp,
            tonalElevation = 0.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = "确认",
                    modifier = Modifier.size(19.dp),
                    tint = if (submitEnabled) colors.onAccent else colors.mutedText
                )
            }
        }
    }
}

@Composable
private fun PendingTagCapsule(
    name: String,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = AppTheme.colors.subtleCard,
        border = BorderStroke(1.dp, AppTheme.colors.border),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(start = 11.dp, end = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name,
                style = AppTheme.typography.capsule,
                color = AppTheme.colors.textPrimary
            )
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .noRippleClickable(onClick = onRemove),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "移除 $name",
                    modifier = Modifier.size(15.dp),
                    tint = AppTheme.colors.textSecondary
                )
            }
        }
    }
}
