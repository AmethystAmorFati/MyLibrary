package com.example.mylibrary.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mylibrary.ui.theme.AppTheme

@Composable
fun ManagementRow(
    title: String,
    supportingText: String,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = AppTheme.typography.body,
                color = AppTheme.colors.textPrimary
            )
            Text(
                supportingText,
                color = AppTheme.colors.mutedText,
                style = AppTheme.typography.metadata
            )
        }
        actions()
    }
    HorizontalDivider(color = AppTheme.colors.subtleBorder)
}
