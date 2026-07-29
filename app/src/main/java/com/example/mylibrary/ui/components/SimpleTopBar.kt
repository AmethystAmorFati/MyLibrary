package com.example.mylibrary.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.mylibrary.ui.theme.AppTheme
import com.example.mylibrary.ui.theme.SecondaryHeaderHeight
import com.example.mylibrary.ui.theme.TopBarActionSize
import com.example.mylibrary.ui.theme.TopBarHorizontalPadding

@Composable
fun SimpleTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(SecondaryHeaderHeight)
            .padding(horizontal = TopBarHorizontalPadding)
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(TopBarActionSize)
                .align(androidx.compose.ui.Alignment.CenterStart)
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "返回",
                tint = AppTheme.colors.textPrimary
            )
        }
        Text(
            text = title,
            modifier = Modifier.align(androidx.compose.ui.Alignment.Center),
            style = AppTheme.typography.pageTitle,
            color = AppTheme.colors.textPrimary
        )
        Box(
            modifier = Modifier
                .size(TopBarActionSize)
                .align(androidx.compose.ui.Alignment.CenterEnd),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            action?.invoke()
        }
    }
}
