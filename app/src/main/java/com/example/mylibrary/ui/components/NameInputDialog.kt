package com.example.mylibrary.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mylibrary.ui.theme.AppTheme

@Composable
fun NameInputDialog(
    title: String,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    extraContent: (@Composable () -> Unit)? = null
) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                title,
                style = AppTheme.typography.sectionTitle,
                color = AppTheme.colors.textPrimary
            )
        },
        text = {
            androidx.compose.foundation.layout.Column {
                LibraryTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = { Text(label) }
                )
                extraContent?.invoke()
            }
        },
        confirmButton = {
            Text(
                "保存",
                modifier = Modifier
                    .padding(12.dp)
                    .noRippleClickable(
                        enabled = value.isNotBlank(),
                        onClick = onConfirm
                ),
                color = AppTheme.colors.textPrimary,
                style = AppTheme.typography.button
            )
        },
        dismissButton = {
            Text(
                "取消",
                modifier = Modifier
                    .padding(12.dp)
                    .noRippleClickable(onClick = onDismiss),
                color = AppTheme.colors.textPrimary,
                style = AppTheme.typography.button
            )
        }
    )
}
