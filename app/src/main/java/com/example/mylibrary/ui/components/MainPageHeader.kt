package com.example.mylibrary.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.mylibrary.ui.theme.MainHeaderHeight
import com.example.mylibrary.ui.theme.AppTheme

@Composable
fun MainPageHeader(
    title: String,
    modifier: Modifier = Modifier,
    isBrand: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(MainHeaderHeight),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = if (isBrand) {
                AppTheme.typography.appName
            } else {
                AppTheme.typography.pageTitle
            },
            color = AppTheme.colors.textPrimary
        )
        actions()
    }
}
