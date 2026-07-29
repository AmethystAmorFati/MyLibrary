package com.example.mylibrary.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.mylibrary.ui.theme.AppTheme

@Composable
fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = AppTheme.typography.sectionTitle,
            color = AppTheme.colors.textPrimary
        )
        if (supportingText != null) {
            Text(
                text = supportingText,
                color = AppTheme.colors.mutedText,
                style = AppTheme.typography.metadata
            )
        }
    }
}
