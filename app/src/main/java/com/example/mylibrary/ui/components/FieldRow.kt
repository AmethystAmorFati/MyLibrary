package com.example.mylibrary.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.mylibrary.ui.theme.AppTheme

@Composable
fun FieldRow(
    label: String,
    value: String,
    editable: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .noRippleClickable(enabled = editable, onClick = onClick)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.38f),
            style = AppTheme.typography.body,
            color = AppTheme.colors.textSecondary
        )
        if (trailingContent == null) {
            Text(
                text = if (editable) "$value  ›" else value,
                modifier = Modifier.weight(0.62f),
                textAlign = TextAlign.End,
                style = AppTheme.typography.body,
                color = if (value == "未填写") {
                    AppTheme.colors.mutedText
                } else {
                    AppTheme.colors.textPrimary
                }
            )
        } else {
            Box(
                modifier = Modifier.weight(0.62f),
                contentAlignment = Alignment.CenterEnd
            ) {
                trailingContent()
            }
        }
    }
}
